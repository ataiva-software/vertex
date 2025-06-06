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
    
    // Configure security headers (HTTPS redirect, HSTS, CSP, etc.)
    configureSecurityHeaders()
    
    // Configure CORS
    configureCors()
    
    // Configure rate limiting
    configureRateLimiting()
    
    // JWT Authentication configuration
    val jwtIssuer = environment.config.propertyOrNull("jwt.issuer")?.getString() ?: "eden.ataiva.com"
    val jwtAudience = environment.config.propertyOrNull("jwt.audience")?.getString() ?: "eden-api"
    val jwtRealm = environment.config.propertyOrNull("jwt.realm")?.getString() ?: "Eden API"
    val jwtSecret = environment.config.propertyOrNull("jwt.secret")?.getString() 
        ?: System.getenv("JWT_SECRET") 
        ?: "defaultSecretForDevEnvironmentOnly"
    
    if (jwtSecret == "defaultSecretForDevEnvironmentOnly" && !environment.developmentMode) {
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
        apiKey("auth-api-key") {
            validate { apiKey ->
                val validApiKeys = environment.config.propertyOrNull("security.api-keys")?.getList() 
                    ?: listOf(System.getenv("API_KEY") ?: "dev-api-key")
                
                if (validApiKeys.contains(apiKey) && apiKey != "dev-api-key") {
                    ApiKeyPrincipal(apiKey)
                } else {
                    if (apiKey == "dev-api-key" && !environment.developmentMode) {
                        logger.warn("Attempt to use development API key in production environment")
                        null
                    } else if (environment.developmentMode) {
                        ApiKeyPrincipal(apiKey)
                    } else {
                        null
                    }
                }
            }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, mapOf(
                    "error" to "Invalid API key",
                    "message" to "Valid API key required"
                ))
            }
        }
    }
    
    // Log security configuration
    logger.info("Security configured with JWT authentication, CORS, and rate limiting")
}

/**
 * API Key authentication provider
 */
private fun AuthenticationConfig.apiKey(
    name: String,
    configure: ApiKeyAuthenticationProvider.Config.() -> Unit
) {
    val provider = ApiKeyAuthenticationProvider.Config(name).apply(configure)
    register(provider)
}

/**
 * API Key authentication provider implementation
 */
private class ApiKeyAuthenticationProvider(config: Config) : AuthenticationProvider(config) {
    private val apiKeyName = config.apiKeyName
    private val authHeaderName = config.authHeaderName
    private val validate = config.validate
    private val challenge = config.challenge
    
    class Config(name: String) : AuthenticationProvider.Config(name) {
        var apiKeyName = "api_key"
        var authHeaderName = HttpHeaders.Authorization
        var validate: suspend (String) -> ApiKeyPrincipal? = { null }
        var challenge: suspend AuthenticationContext.() -> Unit = {}
    }
    
    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        
        // Try to get API key from header
        val apiKey = call.request.headers[authHeaderName]?.removePrefix("ApiKey ")
            // If not in header, try query parameter
            ?: call.request.queryParameters[apiKeyName]
        
        if (apiKey == null) {
            context.challenge(apiKeyName, AuthenticationFailedCause.NoCredentials) { challenge(this) }
            return
        }
        
        val principal = validate(apiKey)
        if (principal == null) {
            context.challenge(apiKeyName, AuthenticationFailedCause.InvalidCredentials) { challenge(this) }
            return
        }
        
        context.principal(principal)
    }
}

/**
 * API Key principal
 */
data class ApiKeyPrincipal(val apiKey: String) : Principal