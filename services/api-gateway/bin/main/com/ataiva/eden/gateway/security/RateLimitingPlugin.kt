package com.ataiva.eden.gateway.security

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Rate limiting plugin for Ktor
 * Provides protection against DDoS attacks and API abuse
 */
class RateLimitingPlugin(configuration: Configuration) {
    private val rateLimiter = configuration.rateLimiter
    private val excludedPaths = configuration.excludedPaths
    private val ipHeaderName = configuration.ipHeaderName
    
    /**
     * Configuration for the rate limiting plugin
     */
    class Configuration {
        var rateLimiter: RateLimiter = TokenBucketRateLimiter()
        var excludedPaths: List<String> = listOf("/health", "/metrics")
        var ipHeaderName: String = "X-Forwarded-For"
    }
    
    /**
     * Companion object for the rate limiting plugin
     */
    companion object Plugin : BaseApplicationPlugin<Application, Configuration, RateLimitingPlugin> {
        override val key = AttributeKey<RateLimitingPlugin>("RateLimiting")
        
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): RateLimitingPlugin {
            val configuration = Configuration().apply(configure)
            val plugin = RateLimitingPlugin(configuration)
            
            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                val path = call.request.path()
                
                // Skip rate limiting for excluded paths
                if (configuration.excludedPaths.any { path.startsWith(it) }) {
                    return@intercept
                }
                
                // Get client IP address
                val clientIp = call.request.header(configuration.ipHeaderName) ?: call.request.origin.remoteHost
                
                // Check if request is allowed
                val result = configuration.rateLimiter.checkLimit(clientIp, path)
                
                if (!result.allowed) {
                    // Add rate limit headers
                    call.response.header("X-RateLimit-Limit", result.limit.toString())
                    call.response.header("X-RateLimit-Remaining", result.remaining.toString())
                    call.response.header("X-RateLimit-Reset", result.resetTime.toString())
                    
                    // Respond with 429 Too Many Requests
                    call.respond(HttpStatusCode.TooManyRequests, mapOf(
                        "error" to "Rate limit exceeded",
                        "message" to "Too many requests, please try again later",
                        "retryAfter" to result.retryAfter
                    ))
                    
                    finish()
                } else {
                    // Add rate limit headers
                    call.response.header("X-RateLimit-Limit", result.limit.toString())
                    call.response.header("X-RateLimit-Remaining", result.remaining.toString())
                    call.response.header("X-RateLimit-Reset", result.resetTime.toString())
                }
            }
            
            return plugin
        }
    }
}

/**
 * Rate limiter interface
 */
interface RateLimiter {
    /**
     * Check if a request is allowed
     */
    fun checkLimit(clientIp: String, path: String): RateLimitResult
}

/**
 * Rate limit result
 */
data class RateLimitResult(
    val allowed: Boolean,
    val limit: Int,
    val remaining: Int,
    val resetTime: Long,
    val retryAfter: Int = 0
)

/**
 * Token bucket rate limiter implementation
 */
class TokenBucketRateLimiter(
    private val defaultLimit: Int = 60,
    private val defaultWindow: kotlin.time.Duration = 1.minutes,
    private val pathLimits: Map<String, PathLimit> = mapOf(
        "/api/v1/auth" to PathLimit(20, 1.minutes),
        "/api/v1/users" to PathLimit(30, 1.minutes)
    ),
    private val ipBlacklist: Set<String> = emptySet(),
    private val ipWhitelist: Set<String> = emptySet()
) : RateLimiter {
    
    private val buckets = ConcurrentHashMap<String, TokenBucket>()
    private val mutex = Mutex()
    
    override fun checkLimit(clientIp: String, path: String): RateLimitResult {
        // Check whitelist
        if (ipWhitelist.contains(clientIp)) {
            return RateLimitResult(
                allowed = true,
                limit = Int.MAX_VALUE,
                remaining = Int.MAX_VALUE,
                resetTime = System.currentTimeMillis() + defaultWindow.inWholeMilliseconds
            )
        }
        
        // Check blacklist
        if (ipBlacklist.contains(clientIp)) {
            return RateLimitResult(
                allowed = false,
                limit = 0,
                remaining = 0,
                resetTime = System.currentTimeMillis() + defaultWindow.inWholeMilliseconds,
                retryAfter = defaultWindow.inWholeSeconds.toInt()
            )
        }
        
        // Get path-specific limit or default
        val pathLimit = pathLimits.entries.find { path.startsWith(it.key) }?.value
            ?: PathLimit(defaultLimit, defaultWindow)
        
        // Get or create bucket for this IP and path
        val bucketKey = "$clientIp:$path"
        val bucket = buckets.computeIfAbsent(bucketKey) {
            TokenBucket(pathLimit.limit, pathLimit.window.toJavaDuration())
        }
        
        // Try to consume a token
        val allowed = bucket.tryConsume()
        val remaining = bucket.availableTokens()
        val resetTime = bucket.getResetTimeMillis()
        val retryAfter = if (allowed) 0 else ((resetTime - System.currentTimeMillis()) / 1000).toInt()
        
        // Clean up old buckets periodically
        if (Math.random() < 0.01) { // 1% chance to clean up
            cleanupOldBuckets()
        }
        
        return RateLimitResult(
            allowed = allowed,
            limit = pathLimit.limit,
            remaining = remaining,
            resetTime = resetTime,
            retryAfter = retryAfter
        )
    }
    
    /**
     * Clean up old buckets
     */
    private fun cleanupOldBuckets() {
        val now = System.currentTimeMillis()
        buckets.entries.removeIf { (_, bucket) ->
            bucket.getResetTimeMillis() < now && bucket.availableTokens() == bucket.capacity
        }
    }
    
    /**
     * Path-specific rate limit
     */
    data class PathLimit(
        val limit: Int,
        val window: kotlin.time.Duration
    )
    
    /**
     * Token bucket implementation
     */
    private inner class TokenBucket(
        val capacity: Int,
        val refillTime: Duration
    ) {
        private val tokens = AtomicInteger(capacity)
        private var lastRefillTime = System.currentTimeMillis()
        
        /**
         * Try to consume a token
         */
        fun tryConsume(): Boolean {
            refillTokens()
            
            while (true) {
                val currentTokens = tokens.get()
                if (currentTokens <= 0) {
                    return false
                }
                
                if (tokens.compareAndSet(currentTokens, currentTokens - 1)) {
                    return true
                }
            }
        }
        
        /**
         * Get available tokens
         */
        fun availableTokens(): Int {
            refillTokens()
            return tokens.get()
        }
        
        /**
         * Get reset time in milliseconds
         */
        fun getResetTimeMillis(): Long {
            return lastRefillTime + refillTime.toMillis()
        }
        
        /**
         * Refill tokens based on elapsed time
         */
        private fun refillTokens() {
            val now = System.currentTimeMillis()
            val elapsedTime = now - lastRefillTime
            
            if (elapsedTime >= refillTime.toMillis()) {
                tokens.set(capacity)
                lastRefillTime = now
            }
        }
    }
}

/**
 * Extension function to install rate limiting
 */
fun Application.configureRateLimiting() {
    install(RateLimitingPlugin) {
        rateLimiter = TokenBucketRateLimiter(
            defaultLimit = environment.config.propertyOrNull("security.ratelimit.default-limit")?.getString()?.toInt() ?: 60,
            defaultWindow = environment.config.propertyOrNull("security.ratelimit.default-window")?.getString()?.toInt()?.seconds ?: 1.minutes,
            pathLimits = mapOf(
                "/api/v1/auth" to TokenBucketRateLimiter.PathLimit(20, 1.minutes),
                "/api/v1/users" to TokenBucketRateLimiter.PathLimit(30, 1.minutes),
                "/api/v1/vault" to TokenBucketRateLimiter.PathLimit(50, 1.minutes)
            ),
            ipBlacklist = environment.config.propertyOrNull("security.ratelimit.ip-blacklist")?.getList()?.toSet() ?: emptySet(),
            ipWhitelist = environment.config.propertyOrNull("security.ratelimit.ip-whitelist")?.getList()?.toSet() ?: emptySet()
        )
        excludedPaths = listOf("/health", "/metrics", "/favicon.ico")
        ipHeaderName = environment.config.propertyOrNull("security.ratelimit.ip-header")?.getString() ?: "X-Forwarded-For"
    }
}