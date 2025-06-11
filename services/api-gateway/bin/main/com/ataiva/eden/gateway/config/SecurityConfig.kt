package com.ataiva.eden.gateway.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.httpsredirect.*
import io.ktor.server.plugins.hsts.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.*
import org.slf4j.event.Level
import org.slf4j.LoggerFactory
import com.ataiva.eden.gateway.security.RateLimitingPlugin
import com.ataiva.eden.gateway.security.TokenBucketRateLimiter
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("com.ataiva.eden.gateway.config.SecurityConfig")

/**
 * Security configuration for the API Gateway
 * Implements security best practices for web applications
 */
fun Application.configureSecurityHeaders() {
    // Store environment in a local variable to avoid access issues
    val appEnvironment = environment
    
    // HTTPS Redirect - Redirect all HTTP requests to HTTPS
    // Only enable in production environments
    if (appEnvironment.config.propertyOrNull("ktor.deployment.environment")?.getString() == "production") {
        install(HttpsRedirect) {
            sslPort = 443
            permanentRedirect = true
            excludePrefix("/health")
            excludePrefix("/metrics")
        }
        
        // HSTS - HTTP Strict Transport Security
        install(HSTS) {
            maxAgeInSeconds = 31536000 // 1 year
            includeSubDomains = true
            preload = true
        }
    }
    
    // Default Headers - Add security headers to all responses
    install(DefaultHeaders) {
        // Content Security Policy - Restrict which resources can be loaded
        header("Content-Security-Policy", buildContentSecurityPolicy())
        
        // X-Content-Type-Options - Prevent MIME type sniffing
        header("X-Content-Type-Options", "nosniff")
        
        // X-Frame-Options - Prevent clickjacking
        header("X-Frame-Options", "DENY")
        
        // X-XSS-Protection - Enable XSS filtering in browsers
        header("X-XSS-Protection", "1; mode=block")
        
        // Referrer-Policy - Control how much referrer information is included
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        
        // Permissions-Policy - Control which browser features can be used
        header("Permissions-Policy", buildPermissionsPolicy())
        
        // Cache-Control - Control caching behavior
        header(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0")
        
        // Clear-Site-Data - Clear browsing data when logging out
        // This should be used selectively, not on all responses
        // header("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\"")
        
        // Feature-Policy - Control which features can be used (legacy)
        header("Feature-Policy", "camera 'none'; microphone 'none'; geolocation 'none'")
    }
    
    // Forwarded Headers - Handle proxy headers
    install(ForwardedHeaders)
    
    // Compression - Compress responses
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 0.9
        }
    }
    
    // Note: Call logging is configured in the Monitoring.kt file
}

/**
 * Configure CORS for the API Gateway
 */
fun Application.configureCors() {
    // Store environment in a local variable to avoid access issues
    val appEnvironment = environment
    
    install(CORS) {
        // Allow specific origins in production
        val allowedOrigins = appEnvironment.config.propertyOrNull("security.cors.allowed-origins")
            ?.getList() ?: listOf("http://localhost:3000", "https://eden.ataiva.com")
        
        // In development, allow all origins
        if (appEnvironment.developmentMode) {
            anyHost()
        } else {
            allowedOrigins.forEach { 
                allowHost(it, schemes = listOf("http", "https")) 
            }
        }
        
        // Allow credentials (cookies, authorization headers)
        allowCredentials = true
        
        // Allow specific HTTP methods
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        
        // Allow specific HTTP headers
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader("X-Request-ID")
        allowHeader("X-API-Key")
        allowHeader("X-CSRF-Token")
        
        // Expose specific headers to the client
        exposeHeader(HttpHeaders.ContentType)
        exposeHeader(HttpHeaders.ContentLength)
        exposeHeader(HttpHeaders.ContentDisposition)
        exposeHeader("X-Request-ID")
        
        // Max age for preflight requests (in seconds)
        maxAgeInSeconds = 3600
    }
}

/**
 * Build Content Security Policy header value
 */
private fun buildContentSecurityPolicy(): String {
    return buildString {
        append("default-src 'self'; ")
        append("script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; ")
        append("style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.jsdelivr.net; ")
        append("img-src 'self' data: https:; ")
        append("font-src 'self' https://fonts.gstatic.com; ")
        append("connect-src 'self' https://api.ataiva.com; ")
        append("frame-src 'none'; ")
        append("object-src 'none'; ")
        append("base-uri 'self'; ")
        append("form-action 'self'; ")
        append("frame-ancestors 'none'; ")
        append("upgrade-insecure-requests; ")
        append("block-all-mixed-content; ")
    }
}

/**
 * Build Permissions Policy header value
 */
private fun buildPermissionsPolicy(): String {
    return buildString {
        append("accelerometer=(), ")
        append("camera=(), ")
        append("geolocation=(), ")
        append("gyroscope=(), ")
        append("magnetometer=(), ")
        append("microphone=(), ")
        append("payment=(), ")
        append("usb=()")
    }
}

/**
 * Configure rate limiting for the API Gateway
 */
fun Application.configureRateLimiting() {
    // Store environment in a local variable to avoid access issues
    val appEnvironment = environment
    
    // Install the rate limiting plugin with configurable settings
    install(RateLimitingPlugin) {
        // Configure the rate limiter with settings from application configuration
        rateLimiter = TokenBucketRateLimiter(
            // Default limit: 60 requests per minute
            defaultLimit = appEnvironment.config.propertyOrNull("security.ratelimit.default-limit")?.getString()?.toInt() ?: 60,
            defaultWindow = appEnvironment.config.propertyOrNull("security.ratelimit.default-window-seconds")?.getString()?.toInt()?.seconds ?: 60.seconds,
            
            // Path-specific limits
            pathLimits = mapOf(
                // Authentication endpoints: 20 requests per minute
                "/api/v1/auth" to TokenBucketRateLimiter.PathLimit(
                    appEnvironment.config.propertyOrNull("security.ratelimit.auth-limit")?.getString()?.toInt() ?: 20,
                    appEnvironment.config.propertyOrNull("security.ratelimit.auth-window-seconds")?.getString()?.toInt()?.seconds ?: 60.seconds
                ),
                
                // User management endpoints: 30 requests per minute
                "/api/v1/users" to TokenBucketRateLimiter.PathLimit(
                    appEnvironment.config.propertyOrNull("security.ratelimit.users-limit")?.getString()?.toInt() ?: 30,
                    appEnvironment.config.propertyOrNull("security.ratelimit.users-window-seconds")?.getString()?.toInt()?.seconds ?: 60.seconds
                ),
                
                // Vault endpoints: 50 requests per minute
                "/api/v1/vault" to TokenBucketRateLimiter.PathLimit(
                    appEnvironment.config.propertyOrNull("security.ratelimit.vault-limit")?.getString()?.toInt() ?: 50,
                    appEnvironment.config.propertyOrNull("security.ratelimit.vault-window-seconds")?.getString()?.toInt()?.seconds ?: 60.seconds
                ),
                
                // Workflow endpoints: 40 requests per minute
                "/api/v1/workflows" to TokenBucketRateLimiter.PathLimit(
                    appEnvironment.config.propertyOrNull("security.ratelimit.workflows-limit")?.getString()?.toInt() ?: 40,
                    appEnvironment.config.propertyOrNull("security.ratelimit.workflows-window-seconds")?.getString()?.toInt()?.seconds ?: 60.seconds
                )
            ),
            
            // IP blacklist and whitelist from configuration
            ipBlacklist = appEnvironment.config.propertyOrNull("security.ratelimit.ip-blacklist")?.getList()?.toSet() ?: emptySet(),
            ipWhitelist = appEnvironment.config.propertyOrNull("security.ratelimit.ip-whitelist")?.getList()?.toSet() ?: emptySet()
        )
        
        // Paths to exclude from rate limiting
        excludedPaths = appEnvironment.config.propertyOrNull("security.ratelimit.excluded-paths")?.getList()
            ?: listOf("/health", "/metrics", "/favicon.ico")
        
        // Header to use for client IP identification (for proxied requests)
        ipHeaderName = appEnvironment.config.propertyOrNull("security.ratelimit.ip-header")?.getString() ?: "X-Forwarded-For"
    }
    
    // Log that rate limiting has been configured
    log.info("Rate limiting configured with default limit of ${appEnvironment.config.propertyOrNull("security.ratelimit.default-limit")?.getString()?.toInt() ?: 60} requests per minute")
}