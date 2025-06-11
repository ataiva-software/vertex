package com.ataiva.eden.gateway.plugins

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.text.DateFormat
import java.util.*

/**
 * Configures serialization features for the API Gateway
 * - JSON serialization with kotlinx.serialization
 * - Content negotiation
 * - Error handling for serialization issues
 */
fun Application.configureSerialization() {
    val logger = LoggerFactory.getLogger("SerializationPlugin")
    
    // Store environment in a local variable to avoid access issues
    val appEnvironment = environment
    
    // Install Content Negotiation
    @OptIn(ExperimentalSerializationApi::class)
    install(ContentNegotiation) {
        // Configure JSON serialization
        json(Json {
            // Pretty print JSON in development mode
            prettyPrint = appEnvironment.developmentMode
            
            // Be lenient when parsing JSON
            isLenient = true
            
            // Allow serializing Kotlin objects as nulls
            explicitNulls = false
            
            // Ignore unknown keys when deserializing
            ignoreUnknownKeys = true
            
            // Use ISO-8601 date format
            encodeDefaults = true
            
            // Allow serializing special floating point values
            allowSpecialFloatingPointValues = true
            
            // Allow structured map keys
            allowStructuredMapKeys = true
            
            // Use names as defined in Kotlin code
            useAlternativeNames = false
            
            // Encode default values
            encodeDefaults = true
            
            // Class discriminator for polymorphic serialization
            classDiscriminator = "type"
        })
    }
    
    // Install Status Pages for error handling
    install(StatusPages) {
        // Handle serialization exceptions
        exception<SerializationException> { call, cause ->
            logger.warn("Serialization error: ${cause.message}", cause)
            
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to "Invalid request format",
                    "message" to (cause.message ?: "Serialization error"),
                    "timestamp" to Date().time
                )
            )
        }
        
        // Handle content transformation exceptions
        exception<ContentTransformationException> { call, cause ->
            logger.warn("Content transformation error: ${cause.message}", cause)
            
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to "Invalid content format",
                    "message" to (cause.message ?: "Content transformation error"),
                    "timestamp" to Date().time
                )
            )
        }
        
        // Handle general exceptions
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception: ${cause.message}", cause)
            
            // Don't expose internal error details in production
            val errorMessage = if (appEnvironment.developmentMode) {
                cause.message ?: "Internal server error"
            } else {
                "Internal server error"
            }
            
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "error" to "Server error",
                    "message" to errorMessage,
                    "timestamp" to Date().time
                )
            )
        }
        
        // Handle 404 Not Found
        status(HttpStatusCode.NotFound) { call, status ->
            val path = call.request.path()
            call.respond(
                status,
                mapOf(
                    "error" to "Not found",
                    "message" to "The requested resource was not found",
                    "path" to path,
                    "timestamp" to Date().time
                )
            )
        }
        
        // Handle 405 Method Not Allowed
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            val method = call.request.httpMethod.value
            val path = call.request.path()
            call.respond(
                status,
                mapOf(
                    "error" to "Method not allowed",
                    "message" to "The HTTP method is not supported for this resource",
                    "method" to method,
                    "path" to path,
                    "timestamp" to Date().time
                )
            )
        }
        
        // Handle 415 Unsupported Media Type
        status(HttpStatusCode.UnsupportedMediaType) { call, status ->
            val contentType = call.request.contentType().toString()
            call.respond(
                status,
                mapOf(
                    "error" to "Unsupported media type",
                    "message" to "The content type is not supported",
                    "contentType" to contentType,
                    "timestamp" to Date().time
                )
            )
        }
    }
    
    // Add response transformation interceptor for consistent response format
    intercept(ApplicationCallPipeline.Plugins) {
        // Add correlation ID to all responses if available
        val correlationId = call.request.header("X-Correlation-ID") ?: UUID.randomUUID().toString()
        call.response.header("X-Correlation-ID", correlationId)
    }
    
    logger.info("Serialization configured with JSON content negotiation and error handling")
}