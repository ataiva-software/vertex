package com.ataiva.eden.events

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Base domain event interface
 */
@Serializable
abstract class DomainEvent {
    abstract val eventId: String
    abstract val eventType: String
    abstract val aggregateId: String
    abstract val aggregateType: String
    abstract val organizationId: String?
    abstract val userId: String?
    abstract val timestamp: Instant
    abstract val version: Int
    abstract val metadata: Map<String, String>
}

/**
 * Event bus interface for publishing and subscribing to events
 */
interface EventBus {
    /**
     * Publish event
     */
    suspend fun publish(event: DomainEvent)
    
    /**
     * Publish multiple events
     */
    suspend fun publishAll(events: List<DomainEvent>)
    
    /**
     * Subscribe to events by type
     */
    suspend fun subscribe(eventType: String, handler: EventHandler)
    
    /**
     * Subscribe to events by pattern
     */
    suspend fun subscribePattern(pattern: String, handler: EventHandler)
    
    /**
     * Unsubscribe from events
     */
    suspend fun unsubscribe(eventType: String, handler: EventHandler)
    
    /**
     * Start event bus
     */
    suspend fun start()
    
    /**
     * Stop event bus
     */
    suspend fun stop()
}

/**
 * Event handler interface
 */
interface EventHandler {
    /**
     * Handle event
     */
    suspend fun handle(event: DomainEvent)
    
    /**
     * Get supported event types
     */
    fun getSupportedEventTypes(): Set<String>
    
    /**
     * Handler name for identification
     */
    val handlerName: String
}

/**
 * Event store interface for persisting events
 */
interface EventStore {
    /**
     * Save event
     */
    suspend fun saveEvent(event: DomainEvent)
    
    /**
     * Save multiple events
     */
    suspend fun saveEvents(events: List<DomainEvent>)
    
    /**
     * Get events for aggregate
     */
    suspend fun getEvents(aggregateId: String, fromVersion: Int = 0): List<DomainEvent>
    
    /**
     * Get events by type
     */
    suspend fun getEventsByType(eventType: String, limit: Int = 100, offset: Int = 0): List<DomainEvent>
    
    /**
     * Get events in time range
     */
    suspend fun getEventsInRange(from: Instant, to: Instant, limit: Int = 100): List<DomainEvent>
    
    /**
     * Get latest events
     */
    suspend fun getLatestEvents(limit: Int = 100): List<DomainEvent>
}

// User Events
@Serializable
data class UserCreatedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val organizationId: String?,
    override val userId: String?,
    override val timestamp: Instant,
    override val version: Int = 1,
    override val metadata: Map<String, String> = emptyMap(),
    val email: String,
    val profile: Map<String, String> = emptyMap()
) : DomainEvent() {
    override val eventType: String = "user.created"
    override val aggregateType: String = "user"
}

@Serializable
data class UserUpdatedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val organizationId: String?,
    override val userId: String?,
    override val timestamp: Instant,
    override val version: Int = 1,
    override val metadata: Map<String, String> = emptyMap(),
    val changes: Map<String, String> = emptyMap()
) : DomainEvent() {
    override val eventType: String = "user.updated"
    override val aggregateType: String = "user"
}

@Serializable
data class UserDeletedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val organizationId: String?,
    override val userId: String?,
    override val timestamp: Instant,
    override val version: Int = 1,
    override val metadata: Map<String, String> = emptyMap()
) : DomainEvent() {
    override val eventType: String = "user.deleted"
    override val aggregateType: String = "user"
}

// Organization Events
@Serializable
data class OrganizationCreatedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val organizationId: String?,
    override val userId: String?,
    override val timestamp: Instant,
    override val version: Int = 1,
    override val metadata: Map<String, String> = emptyMap(),
    val name: String,
    val slug: String
) : DomainEvent() {
    override val eventType: String = "organization.created"
    override val aggregateType: String = "organization"
}

@Serializable
data class OrganizationMemberAddedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val organizationId: String?,
    override val userId: String?,
    override val timestamp: Instant,
    override val version: Int = 1,
    override val metadata: Map<String, String> = emptyMap(),
    val memberId: String,
    val role: String
) : DomainEvent() {
    override val eventType: String = "organization.member_added"
    override val aggregateType: String = "organization"
}

// Vault Events
@Serializable
data class SecretCreatedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val organizationId: String?,
    override val userId: String?,
    override val timestamp: Instant,
    override val version: Int = 1,
    override val metadata: Map<String, String> = emptyMap(),
    val secretName: String,
    val secretType: String = "generic"
) : DomainEvent() {
    override val eventType: String = "vault.secret_created"
    override val aggregateType: String = "secret"
}

@Serializable
data class SecretAccessedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val organizationId: String?,
    override val userId: String?,
    override val timestamp: Instant,
    override val version: Int = 1,
    override val metadata: Map<String, String> = emptyMap(),
    val secretName: String,
    val accessType: String = "read"
) : DomainEvent() {
    override val eventType: String = "vault.secret_accessed"
    override val aggregateType: String = "secret"
}

// Flow Events
@Serializable
data class WorkflowCreatedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val organizationId: String?,
    override val userId: String?,
    override val timestamp: Instant,
    override val version: Int = 1,
    override val metadata: Map<String, String> = emptyMap(),
    val workflowName: String,
    val workflowType: String = "generic"
) : DomainEvent() {
    override val eventType: String = "flow.workflow_created"
    override val aggregateType: String = "workflow"
}

@Serializable
data class WorkflowExecutedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val organizationId: String?,
    override val userId: String?,
    override val timestamp: Instant,
    override val version: Int = 1,
    override val metadata: Map<String, String> = emptyMap(),
    val workflowName: String,
    val executionId: String,
    val status: String,
    val duration: Long? = null
) : DomainEvent() {
    override val eventType: String = "flow.workflow_executed"
    override val aggregateType: String = "workflow"
}

// Task Events
@Serializable
data class TaskCreatedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val organizationId: String?,
    override val userId: String?,
    override val timestamp: Instant,
    override val version: Int = 1,
    override val metadata: Map<String, String> = emptyMap(),
    val taskName: String,
    val taskType: String = "generic"
) : DomainEvent() {
    override val eventType: String = "task.created"
    override val aggregateType: String = "task"
}

@Serializable
data class TaskExecutedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val organizationId: String?,
    override val userId: String?,
    override val timestamp: Instant,
    override val version: Int = 1,
    override val metadata: Map<String, String> = emptyMap(),
    val taskName: String,
    val executionId: String,
    val status: String,
    val exitCode: Int? = null,
    val duration: Long? = null
) : DomainEvent() {
    override val eventType: String = "task.executed"
    override val aggregateType: String = "task"
}

/**
 * Event serializer interface
 */
interface EventSerializer {
    /**
     * Serialize event to string
     */
    fun serialize(event: DomainEvent): String
    
    /**
     * Deserialize event from string
     */
    fun deserialize(data: String, eventType: String): DomainEvent?
    
    /**
     * Get supported event types
     */
    fun getSupportedTypes(): Set<String>
}