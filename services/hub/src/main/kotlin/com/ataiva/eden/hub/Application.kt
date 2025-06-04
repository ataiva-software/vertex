package com.ataiva.eden.hub

import com.ataiva.eden.hub.service.HubService
import com.ataiva.eden.hub.controller.HubController
import com.ataiva.eden.hub.model.*
import com.ataiva.eden.database.EdenDatabaseService
import com.ataiva.eden.database.PostgreSQLDatabaseService
import com.ataiva.eden.crypto.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.cors.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Configure JSON serialization
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    
    // Configure CORS
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-User-ID")
        allowHeader("X-Integration-Key")
        anyHost() // For development - restrict in production
    }
    
    // Configure error handling
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val errorResponse = ApiResponse.error<Unit>("Internal server error: ${cause.localizedMessage}")
            call.respond(HttpStatusCode.InternalServerError, errorResponse)
        }
        
        status(HttpStatusCode.NotFound) { call, status ->
            val errorResponse = ApiResponse.error<Unit>("Endpoint not found")
            call.respond(status, errorResponse)
        }
        
        status(HttpStatusCode.Unauthorized) { call, status ->
            val errorResponse = ApiResponse.error<Unit>("Authentication required")
            call.respond(status, errorResponse)
        }
    }
    
    // Initialize dependencies
    val databaseService = createDatabaseService()
    val cryptoServices = createCryptoServices()
    val hubService = HubService(
        databaseService = databaseService,
        encryption = cryptoServices.encryption,
        secureRandom = cryptoServices.secureRandom
    )
    val hubController = HubController(hubService)
    
    routing {
        // Service info endpoint
        get("/") {
            call.respond(ServiceInfo(
                name = "Eden Hub Service",
                version = "1.0.0",
                description = "Central integration and communication hub with webhooks and notifications",
                status = "running",
                features = listOf(
                    "External Service Integrations (GitHub, Slack, JIRA, AWS)",
                    "Webhook Management with Reliable Delivery",
                    "Multi-Channel Notifications (Email, SMS, Slack, Push)",
                    "Event Publishing and Subscription System",
                    "Template-Based Notification System",
                    "OAuth 2.0 and API Key Authentication"
                )
            ))
        }
        
        // Enhanced health check
        get("/health") {
            try {
                val healthResponse = hubService.getHealthStatus()
                call.respond(HttpStatusCode.OK, ApiResponse.success(healthResponse))
            } catch (e: Exception) {
                val errorHealth = HubHealthResponse(
                    status = "unhealthy",
                    timestamp = Clock.System.now(),
                    uptime = System.currentTimeMillis() - startTime,
                    service = "hub",
                    database = DatabaseHealth(connected = false),
                    integrations = IntegrationsHealth(0, 0, 0, null),
                    webhooks = WebhooksHealth(0, 0, 0, 0.0),
                    notifications = NotificationsHealth(0, 0, 0, 0.0)
                )
                call.respond(HttpStatusCode.ServiceUnavailable, ApiResponse.success(errorHealth))
            }
        }
        
        // Service statistics
        get("/stats") {
            try {
                val stats = hubService.getServiceStatistics()
                call.respond(HttpStatusCode.OK, ApiResponse.success(stats))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Map<String, Any>>("Failed to get statistics"))
            }
        }
        
        // Hub API routes
        with(hubController) {
            hubRoutes()
        }
    }
}

/**
 * Create database service with proper configuration
 */
private fun createDatabaseService(): EdenDatabaseService {
    // TODO: Load from configuration
    val config = mapOf(
        "url" to (System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/eden_dev"),
        "username" to (System.getenv("DATABASE_USERNAME") ?: "eden_user"),
        "password" to (System.getenv("DATABASE_PASSWORD") ?: "eden_password"),
        "driver" to "org.postgresql.Driver"
    )
    
    return PostgreSQLDatabaseService(config)
}

/**
 * Create crypto services with proper implementations
 */
private fun createCryptoServices(): CryptoServices {
    // TODO: Load actual implementations from shared/crypto module
    return CryptoServices(
        encryption = MockEncryption(),
        secureRandom = MockSecureRandom()
    )
}

/**
 * Container for crypto services
 */
private data class CryptoServices(
    val encryption: Encryption,
    val secureRandom: SecureRandom
)

// TODO: Replace with actual implementations from shared/crypto
private class MockEncryption : Encryption {
    override fun encrypt(data: ByteArray, key: ByteArray): EncryptionResult {
        // Mock implementation - replace with BouncyCastleEncryption
        return EncryptionResult(data, ByteArray(12), ByteArray(16))
    }
    
    override fun decrypt(encryptedData: ByteArray, key: ByteArray, nonce: ByteArray): DecryptionResult {
        return DecryptionResult.Success(encryptedData)
    }
    
    override fun encryptString(data: String, key: ByteArray): EncryptionResult {
        return encrypt(data.toByteArray(), key)
    }
    
    override fun decryptString(encryptedData: ByteArray, key: ByteArray, nonce: ByteArray): String? {
        return String(encryptedData)
    }
}

private class MockSecureRandom : SecureRandom {
    override fun nextBytes(size: Int): ByteArray {
        return ByteArray(size) { (Math.random() * 256).toInt().toByte() }
    }
    
    override fun nextString(length: Int, charset: String): String {
        return (1..length).map { charset.random() }.joinToString("")
    }
    
    override fun nextUuid(): String {
        return java.util.UUID.randomUUID().toString()
    }
}

@Serializable
data class ServiceInfo(
    val name: String,
    val version: String,
    val description: String,
    val status: String,
    val features: List<String> = emptyList()
)

private val startTime = System.currentTimeMillis()