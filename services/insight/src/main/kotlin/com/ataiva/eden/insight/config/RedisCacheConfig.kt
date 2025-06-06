package com.ataiva.eden.insight.config

import com.typesafe.config.ConfigFactory
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DefaultClientResources
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Redis cache configuration for the Insight Service.
 * Manages Redis connections and provides access to Redis commands.
 */
class RedisCacheConfig(
    private val configPath: String = "insight.cache.redis"
) {
    private val logger = LoggerFactory.getLogger(RedisCacheConfig::class.java)
    private val config = ConfigFactory.load().getConfig(configPath)
    
    private val clientResources: ClientResources
    private val redisClient: RedisClient
    private val connection: StatefulRedisConnection<String, String>
    
    val syncCommands: RedisCommands<String, String>
    val enabled: Boolean
    val ttlMinutes: Long
    
    init {
        enabled = config.getBoolean("enabled")
        ttlMinutes = config.getLong("ttl-minutes")
        
        if (enabled) {
            logger.info("Initializing Redis cache...")
            
            // Create client resources with optimized thread pools
            clientResources = DefaultClientResources.builder()
                .ioThreadPoolSize(config.getInt("pool-size"))
                .computationThreadPoolSize(4)
                .build()
            
            // Create Redis URI
            val redisUri = RedisURI.builder()
                .withHost(config.getString("host"))
                .withPort(config.getInt("port"))
                .withDatabase(config.getInt("database"))
                .withTimeout(Duration.ofSeconds(5))
                
            // Add password if configured
            val password = config.getString("password")
            if (password.isNotEmpty()) {
                redisUri.withPassword(password.toCharArray())
            }
            
            // Create Redis client with optimized options
            redisClient = RedisClient.create(clientResources, redisUri.build())
            redisClient.options = ClientOptions.builder()
                .autoReconnect(true)
                .pingBeforeActivateConnection(true)
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(5)))
                .build()
            
            // Create connection
            connection = redisClient.connect()
            syncCommands = connection.sync()
            
            logger.info("Redis cache initialized successfully")
        } else {
            logger.info("Redis cache is disabled")
            clientResources = DefaultClientResources.create()
            redisClient = RedisClient.create(clientResources)
            connection = redisClient.connect(RedisURI.create("localhost", 6379))
            syncCommands = connection.sync()
        }
    }
    
    /**
     * Close Redis connections and resources
     */
    fun close() {
        if (enabled) {
            logger.info("Closing Redis connections...")
            connection.close()
            redisClient.shutdown(0, 0, TimeUnit.SECONDS)
            clientResources.shutdown(0, 0, TimeUnit.SECONDS)
            logger.info("Redis connections closed")
        }
    }
}