package com.ataiva.eden.gateway.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.httpsredirect.*
import io.ktor.server.plugins.hsts.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.callloging.*
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
    // HTTPS Redirect - Redirect all HTTP requests to HTTPS
    // Only enable in production environments
    if (environment.config.propertyOrNull("ktor.deployment.environment")?.getString() == "production") {
        install(HttpsRedirect) {
            sslPort = 443
            permanentRedirect = true
            excludePrefix("/health", "/metrics")
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
        header(HttpHeaders.ContentSecurityPolicy, buildContentSecurityPolicy())
        
        // X-Content-Type-Options - Prevent MIME type sniffing
        header(HttpHeaders.XContentTypeOptions, "nosniff")
        
        // X-Frame-Options - Prevent clickjacking
        header(HttpHeaders.XFrameOptions, "DENY")
        
        // X-XSS-Protection - Enable XSS filtering in browsers
        header(HttpHeaders.XssProtection, "1; mode=block")
        
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
    
    // Call Logging - Log all requests
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val path = call.request.path()
            val userAgent = call.request.headers["User-Agent"]
            val clientIp = call.request.origin.remoteHost
            
            "$clientIp [$httpMethod] $path - $status - $userAgent"
        }
    }
}

/**
 * Configure CORS for the API Gateway
 */
fun Application.configureCors() {
    install(CORS) {
        // Allow specific origins in production
        val allowedOrigins = environment.config.propertyOrNull("security.cors.allowed-origins")
            ?.getList() ?: listOf("http://localhost:3000", "https://eden.ataiva.com")
        
        // In development, allow all origins
        if (environment.developmentMode) {
            anyHost()
        } else {
            allowedOrigins.forEach { allowHost(it, schemes = listOf("http", "https")) }
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
    // Install the rate limiting plugin with configurable settings
    install(RateLimitingPlugin) {
        // Configure the rate limiter with settings from application configuration
        rateLimiter = TokenBucketRateLimiter(
            // Default limit: 60 requests per minute
            defaultLimit = environment.config.propertyOrNull("security.ratelimit.default-limit")?.getString()?.toInt() ?: 60,
            defaultWindow = environment.config.propertyOrNull("security.ratelimit.default-window-seconds")?.getString()?.toInt()?.seconds ?: 60.seconds,
            
            // Path-specific limits
            pathLimits = mapOf(
                // Authentication endpoints: 20 requests per minute
                "/api/v1/auth" to TokenBucketRateLimiter.PathLimit(
                    environment.config.propertyOrNull("security.ratelimit.auth-limit")?.getString()?.toInt() ?: 20,
                    environment.config.propertyOrNull("security.ratelimit.auth-window-seconds")?.getString()?.toInt()?.seconds ?: 60.seconds
                ),
                
                // User management endpoints: 30 requests per minute
                "/api/v1/users" to TokenBucketRateLimiter.PathLimit(
                    environment.config.propertyOrNull("security.ratelimit.users-limit")?.getString()?.toInt() ?: 30,
                    environment.config.propertyOrNull("security.ratelimit.users-window-seconds")?.getString()?.toInt()?.seconds ?: 60.seconds
                ),
                
                // Vault endpoints: 50 requests per minute
                "/api/v1/vault" to TokenBucketRateLimiter.PathLimit(
                    environment.config.propertyOrNull("security.ratelimit.vault-limit")?.getString()?.toInt() ?: 50,
                    environment.config.propertyOrNull("security.ratelimit.vault-window-seconds")?.getString()?.toInt()?.seconds ?: 60.seconds
                ),
                
                // Workflow endpoints: 40 requests per minute
                "/api/v1/workflows" to TokenBucketRateLimiter.PathLimit(
                    environment.config.propertyOrNull("security.ratelimit.workflows-limit")?.getString()?.toInt() ?: 40,
                    environment.config.propertyOrNull("security.ratelimit.workflows-window-seconds")?.getString()?.toInt()?.seconds ?: 60.seconds
                )
            ),
            
            // IP blacklist and whitelist from configuration
            ipBlacklist = environment.config.propertyOrNull("security.ratelimit.ip-blacklist")?.getList()?.toSet() ?: emptySet(),
            ipWhitelist = environment.config.propertyOrNull("security.ratelimit.ip-whitelist")?.getList()?.toSet() ?: emptySet()
        )
        
        // Paths to exclude from rate limiting
        excludedPaths = environment.config.propertyOrNull("security.ratelimit.excluded-paths")?.getList()
            ?: listOf("/health", "/metrics", "/favicon.ico")
        
        // Header to use for client IP identification (for proxied requests)
        ipHeaderName = environment.config.propertyOrNull("security.ratelimit.ip-header")?.getString() ?: "X-Forwarded-For"
    }
    
    // Log that rate limiting has been configured
    log.info("Rate limiting configured with default limit of ${environment.config.propertyOrNull("security.ratelimit.default-limit")?.getString()?.toInt() ?: 60} requests per minute")
}