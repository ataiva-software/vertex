package com.ataiva.eden.gateway.plugins

import com.ataiva.eden.gateway.config.configureCors
import com.ataiva.eden.gateway.config.configureRateLimiting
import com.ataiva.eden.gateway.config.configureSecurityHeaders
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

/**
 * Configures security features for the API Gateway
 * - JWT Authentication
 * - Authorization
 * - Security Headers
 * - CORS
 * - Rate Limiting
 */
fun Application.configureSecurity() {
    val logger = LoggerFactory.getLogger("SecurityPlugin")
    
    // Store environment in a local variable to avoid access issues
    val appEnvironment = environment
    
    // Configure security headers (HTTPS redirect, HSTS, CSP, etc.)
    configureSecurityHeaders()
    
    // Configure CORS
    configureCors()
    
    // Configure rate limiting
    configureRateLimiting()
    
    // JWT Authentication configuration
    val jwtIssuer = appEnvironment.config.propertyOrNull("jwt.issuer")?.getString() ?: "eden.ataiva.com"
    val jwtAudience = appEnvironment.config.propertyOrNull("jwt.audience")?.getString() ?: "eden-api"
    val jwtRealm = appEnvironment.config.propertyOrNull("jwt.realm")?.getString() ?: "Eden API"
    val jwtSecret = appEnvironment.config.propertyOrNull("jwt.secret")?.getString()
        ?: System.getenv("JWT_SECRET")
        ?: "defaultSecretForDevEnvironmentOnly"
    
    if (jwtSecret == "defaultSecretForDevEnvironmentOnly" && !appEnvironment.developmentMode) {
        logger.warn("WARNING: Using default JWT secret in production environment. This is insecure!")
    }
    
    // Install authentication
    install(Authentication) {
        // JWT authentication
        jwt("auth-jwt") {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                // Validate JWT claims
                if (credential.payload.audience.contains(jwtAudience) && 
                    credential.payload.expiresAt.time > System.currentTimeMillis()) {
                    // Extract user ID and roles from JWT
                    val userId = credential.payload.getClaim("userId")?.asString()
                    val roles = credential.payload.getClaim("roles")?.asList(String::class.java) ?: emptyList()
                    
                    if (!userId.isNullOrBlank()) {
                        // Create JWTPrincipal with additional custom claims
                        JWTPrincipal(credential.payload).also {
                            logger.debug("Authenticated user: $userId with roles: $roles")
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf(
                    "error" to "Invalid or expired JWT token",
                    "message" to "Authentication required"
                ))
            }
        }
        
        // API Key authentication for service-to-service communication
        // Use bearer authentication with custom validation for API keys
        bearer("auth-api-key") {
            realm = "Eden API"
            authenticate { tokenCredential ->
                val apiKey = tokenCredential.token
                val validApiKeys = appEnvironment.config.propertyOrNull("security.api-keys")?.getList()
                    ?: listOf(System.getenv("API_KEY") ?: "dev-api-key")
                
                if (validApiKeys.contains(apiKey) && apiKey != "dev-api-key") {
                    ApiKeyPrincipal(apiKey)
                } else {
                    if (apiKey == "dev-api-key" && !appEnvironment.developmentMode) {
                        logger.warn("Attempt to use development API key in production environment")
                        null
                    } else if (appEnvironment.developmentMode) {
                        ApiKeyPrincipal(apiKey)
                    } else {
                        null
                    }
                }
            }
        }
    }
    
    // Log security configuration
    logger.info("Security configured with JWT authentication, CORS, and rate limiting")
}

// ApiKeyPrincipal is already defined at line 172

/**
 * API Key principal
 */
data class ApiKeyPrincipal(val apiKey: String) : Principal