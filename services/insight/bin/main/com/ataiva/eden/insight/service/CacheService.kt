package com.ataiva.eden.insight.service

import com.ataiva.eden.insight.config.RedisCacheConfig
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Cache
import com.typesafe.config.ConfigFactory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Cache type enum
 */
enum class CacheType {
    IN_MEMORY,
    REDIS,
    NONE
}

/**
 * Cache service for the Insight Service.
 * Provides caching functionality using both in-memory (Caffeine) and Redis caches.
 */
class CacheService(
    val redisCacheConfig: RedisCacheConfig? = null
) {
    val logger = LoggerFactory.getLogger(CacheService::class.java)
    val config = ConfigFactory.load().getConfig("insight.cache")
    val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    
    // In-memory cache using Caffeine
    val inMemoryCache: Cache<String, String>?
    
    // Cache settings
    val inMemoryEnabled: Boolean
    val inMemoryTtlMinutes: Long
    val redisEnabled: Boolean
    
    // Cache policies
    private val cachePolicies: Map<String, CacheType>
    
    init {
        // Initialize in-memory cache
        inMemoryEnabled = config.getBoolean("in-memory.enabled")
        inMemoryTtlMinutes = config.getLong("in-memory.ttl-minutes")
        
        inMemoryCache = if (inMemoryEnabled) {
            Caffeine.newBuilder()
                .maximumSize(config.getLong("in-memory.max-size"))
                .expireAfterWrite(inMemoryTtlMinutes, TimeUnit.MINUTES)
                .recordStats()
                .build<String, String>()
        } else {
            null
        }
        
        // Check if Redis is enabled
        redisEnabled = redisCacheConfig?.enabled ?: false
        
        // Initialize cache policies
        val policies = config.getConfig("policies")
        cachePolicies = mapOf(
            "queries" to getCacheTypeFromString(policies.getString("queries")),
            "reports" to getCacheTypeFromString(policies.getString("reports")),
            "dashboards" to getCacheTypeFromString(policies.getString("dashboards")),
            "metrics" to getCacheTypeFromString(policies.getString("metrics")),
            "kpis" to getCacheTypeFromString(policies.getString("kpis")),
            "analytics" to getCacheTypeFromString(policies.getString("analytics"))
        )
        
        logger.info("Cache service initialized with policies: $cachePolicies")
    }
    
    /**
     * Get cache type from string
     */
    private fun getCacheTypeFromString(type: String): CacheType {
        return when (type.lowercase()) {
            "redis" -> if (redisEnabled) CacheType.REDIS else CacheType.IN_MEMORY
            "in-memory" -> if (inMemoryEnabled) CacheType.IN_MEMORY else CacheType.NONE
            else -> CacheType.NONE
        }
    }
    
    /**
     * Get cache type for a specific data type
     */
    fun getCacheTypeForDataType(dataType: String): CacheType {
        return cachePolicies[dataType] ?: CacheType.NONE
    }
    
    /**
     * Put an object in the cache
     */
    inline fun <reified T> put(key: String, value: T, dataType: String) {
        val cacheType = getCacheTypeForDataType(dataType)
        val serializedValue = json.encodeToString(value)
        
        when (cacheType) {
            CacheType.IN_MEMORY -> {
                inMemoryCache?.put(key, serializedValue)
                logger.debug("Stored in in-memory cache: $key")
            }
            CacheType.REDIS -> {
                redisCacheConfig?.syncCommands?.setex(key, redisCacheConfig.ttlMinutes * 60, serializedValue)
                logger.debug("Stored in Redis cache: $key")
            }
            CacheType.NONE -> {
                // Do nothing
            }
        }
    }
    
    /**
     * Get an object from the cache
     */
    inline fun <reified T> get(key: String, dataType: String): T? {
        val cacheType = getCacheTypeForDataType(dataType)
        
        val serializedValue = when (cacheType) {
            CacheType.IN_MEMORY -> {
                inMemoryCache?.getIfPresent(key)
            }
            CacheType.REDIS -> {
                redisCacheConfig?.syncCommands?.get(key)
            }
            CacheType.NONE -> {
                null
            }
        }
        
        return if (serializedValue != null) {
            try {
                val result = json.decodeFromString<T>(serializedValue)
                logger.debug("Cache hit for key: $key")
                result
            } catch (e: Exception) {
                logger.warn("Failed to deserialize cached value for key: $key", e)
                null
            }
        } else {
            logger.debug("Cache miss for key: $key")
            null
        }
    }
    
    /**
     * Remove an object from the cache
     */
    fun remove(key: String, dataType: String) {
        val cacheType = getCacheTypeForDataType(dataType)
        
        when (cacheType) {
            CacheType.IN_MEMORY -> {
                inMemoryCache?.invalidate(key)
                logger.debug("Removed from in-memory cache: $key")
            }
            CacheType.REDIS -> {
                redisCacheConfig?.syncCommands?.del(key)
                logger.debug("Removed from Redis cache: $key")
            }
            CacheType.NONE -> {
                // Do nothing
            }
        }
    }
    
    /**
     * Clear all caches
     */
    fun clearAll() {
        if (inMemoryEnabled) {
            inMemoryCache?.invalidateAll()
            logger.info("In-memory cache cleared")
        }
        
        if (redisEnabled) {
            redisCacheConfig?.syncCommands?.flushdb()
            logger.info("Redis cache cleared")
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getStats(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        
        if (inMemoryEnabled && inMemoryCache != null) {
            val caffeineStats = inMemoryCache.stats()
            stats["in_memory"] = mapOf(
                "size" to inMemoryCache.estimatedSize(),
                "hit_count" to caffeineStats.hitCount(),
                "miss_count" to caffeineStats.missCount(),
                "hit_rate" to caffeineStats.hitRate(),
                "eviction_count" to caffeineStats.evictionCount()
            )
        }
        
        if (redisEnabled) {
            val redisInfo = redisCacheConfig?.syncCommands?.info() ?: ""
            val keyCount = redisCacheConfig?.syncCommands?.dbsize() ?: 0
            
            stats["redis"] = mapOf(
                "key_count" to keyCount,
                "memory_used" to parseRedisMemoryUsed(redisInfo)
            )
        }
        
        return stats
    }
    
    /**
     * Parse memory used from Redis INFO command output
     */
    fun parseRedisMemoryUsed(info: String): Long {
        val memoryLine = info.lines().find { it.startsWith("used_memory:") }
        return memoryLine?.split(":")?.getOrNull(1)?.toLongOrNull() ?: 0
    }
    
    // Duplicate getStats method removed
    
    /**
     * Close cache connections
     */
    fun close() {
        // No need to close in-memory cache
        // Redis connections are closed by RedisCacheConfig
    }
}