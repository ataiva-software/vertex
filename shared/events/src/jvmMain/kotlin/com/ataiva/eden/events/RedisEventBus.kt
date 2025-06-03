package com.ataiva.eden.events

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * Redis-based event bus implementation
 */
class RedisEventBus(
    private val redisHost: String = "localhost",
    private val redisPort: Int = 6379,
    private val redisPassword: String? = null,
    private val redisDatabase: Int = 0
) : EventBus {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val jedisPool: JedisPool by lazy {
        val config = JedisPoolConfig().apply {
            maxTotal = 20
            maxIdle = 10
            minIdle = 2
            testOnBorrow = true
            testOnReturn = true
            testWhileIdle = true
            blockWhenExhausted = true
        }
        
        if (redisPassword != null) {
            JedisPool(config, redisHost, redisPort, 2000, redisPassword, redisDatabase)
        } else {
            JedisPool(config, redisHost, redisPort, 2000, null, redisDatabase)
        }
    }
    
    private val eventHandlers = ConcurrentHashMap<String, MutableSet<EventHandler>>()
    private val patternHandlers = ConcurrentHashMap<String, MutableSet<EventHandler>>()
    private val subscriberScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val publisherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val eventQueue = Channel<DomainEvent>(Channel.UNLIMITED)
    
    private var isStarted = false
    private var subscriber: JedisPubSub? = null
    private var patternSubscriber: JedisPubSub? = null
    
    override suspend fun publish(event: DomainEvent) {
        if (!isStarted) {
            throw IllegalStateException("EventBus is not started")
        }
        
        eventQueue.send(event)
    }
    
    override suspend fun publishAll(events: List<DomainEvent>) {
        if (!isStarted) {
            throw IllegalStateException("EventBus is not started")
        }
        
        events.forEach { event ->
            eventQueue.send(event)
        }
    }
    
    override suspend fun subscribe(eventType: String, handler: EventHandler) {
        eventHandlers.computeIfAbsent(eventType) { mutableSetOf() }.add(handler)
        
        if (isStarted) {
            // Re-subscribe to include new handler
            restartSubscription()
        }
    }
    
    override suspend fun subscribePattern(pattern: String, handler: EventHandler) {
        patternHandlers.computeIfAbsent(pattern) { mutableSetOf() }.add(handler)
        
        if (isStarted) {
            // Re-subscribe to include new pattern handler
            restartPatternSubscription()
        }
    }
    
    override suspend fun unsubscribe(eventType: String, handler: EventHandler) {
        eventHandlers[eventType]?.remove(handler)
        if (eventHandlers[eventType]?.isEmpty() == true) {
            eventHandlers.remove(eventType)
        }
        
        if (isStarted) {
            restartSubscription()
        }
    }
    
    override suspend fun start() {
        if (isStarted) return
        
        isStarted = true
        
        // Start event publisher coroutine
        publisherScope.launch {
            eventQueue.consumeEach { event ->
                try {
                    publishEventToRedis(event)
                } catch (e: Exception) {
                    // Log error but continue processing
                    println("Error publishing event: ${e.message}")
                }
            }
        }
        
        // Start event subscribers
        startSubscription()
        startPatternSubscription()
    }
    
    override suspend fun stop() {
        if (!isStarted) return
        
        isStarted = false
        
        // Cancel coroutines
        publisherScope.cancel()
        subscriberScope.cancel()
        
        // Unsubscribe from Redis
        subscriber?.unsubscribe()
        patternSubscriber?.punsubscribe()
        
        // Close event queue
        eventQueue.close()
        
        // Close Redis pool
        jedisPool.close()
    }
    
    private suspend fun publishEventToRedis(event: DomainEvent) = withContext(Dispatchers.IO) {
        jedisPool.resource.use { jedis ->
            val eventData = json.encodeToString(event)
            val channel = "events:${event.eventType}"
            jedis.publish(channel, eventData)
            
            // Also publish to a general events channel
            jedis.publish("events:all", eventData)
        }
    }
    
    private fun startSubscription() {
        if (eventHandlers.isEmpty()) return
        
        subscriberScope.launch {
            try {
                jedisPool.resource.use { jedis ->
                    subscriber = object : JedisPubSub() {
                        override fun onMessage(channel: String, message: String) {
                            handleIncomingEvent(channel, message)
                        }
                    }
                    
                    val channels = eventHandlers.keys.map { "events:$it" }.toTypedArray()
                    jedis.subscribe(subscriber, *channels)
                }
            } catch (e: Exception) {
                if (isStarted) {
                    println("Redis subscription error: ${e.message}")
                    // Retry after delay
                    delay(5000)
                    startSubscription()
                }
            }
        }
    }
    
    private fun startPatternSubscription() {
        if (patternHandlers.isEmpty()) return
        
        subscriberScope.launch {
            try {
                jedisPool.resource.use { jedis ->
                    patternSubscriber = object : JedisPubSub() {
                        override fun onPMessage(pattern: String, channel: String, message: String) {
                            handleIncomingPatternEvent(pattern, channel, message)
                        }
                    }
                    
                    val patterns = patternHandlers.keys.map { "events:$it" }.toTypedArray()
                    jedis.psubscribe(patternSubscriber, *patterns)
                }
            } catch (e: Exception) {
                if (isStarted) {
                    println("Redis pattern subscription error: ${e.message}")
                    // Retry after delay
                    delay(5000)
                    startPatternSubscription()
                }
            }
        }
    }
    
    private fun restartSubscription() {
        subscriber?.unsubscribe()
        startSubscription()
    }
    
    private fun restartPatternSubscription() {
        patternSubscriber?.punsubscribe()
        startPatternSubscription()
    }
    
    private fun handleIncomingEvent(channel: String, message: String) {
        subscriberScope.launch {
            try {
                val eventType = channel.removePrefix("events:")
                val event = deserializeEvent(message, eventType)
                
                if (event != null) {
                    val handlers = eventHandlers[eventType] ?: emptySet()
                    handlers.forEach { handler ->
                        try {
                            handler.handle(event)
                        } catch (e: Exception) {
                            println("Error handling event in ${handler.handlerName}: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error processing incoming event: ${e.message}")
            }
        }
    }
    
    private fun handleIncomingPatternEvent(pattern: String, channel: String, message: String) {
        subscriberScope.launch {
            try {
                val eventType = channel.removePrefix("events:")
                val event = deserializeEvent(message, eventType)
                
                if (event != null) {
                    val patternKey = pattern.removePrefix("events:")
                    val handlers = patternHandlers[patternKey] ?: emptySet()
                    
                    handlers.forEach { handler ->
                        try {
                            if (handler.getSupportedEventTypes().contains(eventType) ||
                                handler.getSupportedEventTypes().any { supportedType ->
                                    Pattern.matches(supportedType, eventType)
                                }) {
                                handler.handle(event)
                            }
                        } catch (e: Exception) {
                            println("Error handling pattern event in ${handler.handlerName}: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error processing incoming pattern event: ${e.message}")
            }
        }
    }
    
    private fun deserializeEvent(message: String, eventType: String): DomainEvent? {
        return try {
            when (eventType) {
                "user.created" -> json.decodeFromString<UserCreatedEvent>(message)
                "user.updated" -> json.decodeFromString<UserUpdatedEvent>(message)
                "user.deleted" -> json.decodeFromString<UserDeletedEvent>(message)
                "organization.created" -> json.decodeFromString<OrganizationCreatedEvent>(message)
                "organization.member_added" -> json.decodeFromString<OrganizationMemberAddedEvent>(message)
                "vault.secret_created" -> json.decodeFromString<SecretCreatedEvent>(message)
                "vault.secret_accessed" -> json.decodeFromString<SecretAccessedEvent>(message)
                "flow.workflow_created" -> json.decodeFromString<WorkflowCreatedEvent>(message)
                "flow.workflow_executed" -> json.decodeFromString<WorkflowExecutedEvent>(message)
                "task.created" -> json.decodeFromString<TaskCreatedEvent>(message)
                "task.executed" -> json.decodeFromString<TaskExecutedEvent>(message)
                else -> {
                    println("Unknown event type: $eventType")
                    null
                }
            }
        } catch (e: Exception) {
            println("Error deserializing event: ${e.message}")
            null
        }
    }
}

/**
 * In-memory event bus implementation for testing
 */
class InMemoryEventBus : EventBus {
    
    private val eventHandlers = ConcurrentHashMap<String, MutableSet<EventHandler>>()
    private val patternHandlers = ConcurrentHashMap<String, MutableSet<EventHandler>>()
    private val eventScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isStarted = false
    
    override suspend fun publish(event: DomainEvent) {
        if (!isStarted) {
            throw IllegalStateException("EventBus is not started")
        }
        
        eventScope.launch {
            handleEvent(event)
        }
    }
    
    override suspend fun publishAll(events: List<DomainEvent>) {
        if (!isStarted) {
            throw IllegalStateException("EventBus is not started")
        }
        
        events.forEach { event ->
            eventScope.launch {
                handleEvent(event)
            }
        }
    }
    
    override suspend fun subscribe(eventType: String, handler: EventHandler) {
        eventHandlers.computeIfAbsent(eventType) { mutableSetOf() }.add(handler)
    }
    
    override suspend fun subscribePattern(pattern: String, handler: EventHandler) {
        patternHandlers.computeIfAbsent(pattern) { mutableSetOf() }.add(handler)
    }
    
    override suspend fun unsubscribe(eventType: String, handler: EventHandler) {
        eventHandlers[eventType]?.remove(handler)
        if (eventHandlers[eventType]?.isEmpty() == true) {
            eventHandlers.remove(eventType)
        }
    }
    
    override suspend fun start() {
        isStarted = true
    }
    
    override suspend fun stop() {
        isStarted = false
        eventScope.cancel()
    }
    
    private suspend fun handleEvent(event: DomainEvent) {
        // Handle direct subscriptions
        val handlers = eventHandlers[event.eventType] ?: emptySet()
        handlers.forEach { handler ->
            try {
                handler.handle(event)
            } catch (e: Exception) {
                println("Error handling event in ${handler.handlerName}: ${e.message}")
            }
        }
        
        // Handle pattern subscriptions
        patternHandlers.forEach { (pattern, handlers) ->
            if (Pattern.matches(pattern, event.eventType)) {
                handlers.forEach { handler ->
                    try {
                        handler.handle(event)
                    } catch (e: Exception) {
                        println("Error handling pattern event in ${handler.handlerName}: ${e.message}")
                    }
                }
            }
        }
    }
    
    // Utility methods for testing
    fun getHandlerCount(eventType: String): Int = eventHandlers[eventType]?.size ?: 0
    fun getPatternHandlerCount(pattern: String): Int = patternHandlers[pattern]?.size ?: 0
    fun clearAllHandlers() {
        eventHandlers.clear()
        patternHandlers.clear()
    }
}

/**
 * Event serializer implementation using Kotlinx Serialization
 */
class JsonEventSerializer : EventSerializer {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    override fun serialize(event: DomainEvent): String {
        return json.encodeToString(event)
    }
    
    override fun deserialize(data: String, eventType: String): DomainEvent? {
        return try {
            when (eventType) {
                "user.created" -> json.decodeFromString<UserCreatedEvent>(data)
                "user.updated" -> json.decodeFromString<UserUpdatedEvent>(data)
                "user.deleted" -> json.decodeFromString<UserDeletedEvent>(data)
                "organization.created" -> json.decodeFromString<OrganizationCreatedEvent>(data)
                "organization.member_added" -> json.decodeFromString<OrganizationMemberAddedEvent>(data)
                "vault.secret_created" -> json.decodeFromString<SecretCreatedEvent>(data)
                "vault.secret_accessed" -> json.decodeFromString<SecretAccessedEvent>(data)
                "flow.workflow_created" -> json.decodeFromString<WorkflowCreatedEvent>(data)
                "flow.workflow_executed" -> json.decodeFromString<WorkflowExecutedEvent>(data)
                "task.created" -> json.decodeFromString<TaskCreatedEvent>(data)
                "task.executed" -> json.decodeFromString<TaskExecutedEvent>(data)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun getSupportedTypes(): Set<String> {
        return setOf(
            "user.created", "user.updated", "user.deleted",
            "organization.created", "organization.member_added",
            "vault.secret_created", "vault.secret_accessed",
            "flow.workflow_created", "flow.workflow_executed",
            "task.created", "task.executed"
        )
    }
}

/**
 * Factory for creating event bus instances
 */
object EventBusFactory {
    
    fun createRedisEventBus(
        host: String = "localhost",
        port: Int = 6379,
        password: String? = null,
        database: Int = 0
    ): EventBus {
        return RedisEventBus(host, port, password, database)
    }
    
    fun createInMemoryEventBus(): EventBus {
        return InMemoryEventBus()
    }
    
    fun createEventBusFromConfig(): EventBus {
        val redisUrl = System.getenv("REDIS_URL")
        return if (redisUrl != null) {
            // Parse Redis URL and create Redis event bus
            val host = System.getenv("REDIS_HOST") ?: "localhost"
            val port = System.getenv("REDIS_PORT")?.toIntOrNull() ?: 6379
            val password = System.getenv("REDIS_PASSWORD")
            val database = System.getenv("REDIS_DATABASE")?.toIntOrNull() ?: 0
            
            createRedisEventBus(host, port, password, database)
        } else {
            createInMemoryEventBus()
        }
    }
}