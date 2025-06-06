package com.ataiva.eden.vault

import com.ataiva.eden.vault.service.*
import com.ataiva.eden.vault.controller.VaultController
import com.ataiva.eden.vault.model.*
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
import kotlinx.coroutines.runBlocking

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
        allowHeader("X-User-Password")
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
    val externalSecretsManager = createExternalSecretsManager()
    val vaultService = VaultService(
        databaseService = databaseService,
        encryption = cryptoServices.encryption,
        zeroKnowledgeEncryption = cryptoServices.zeroKnowledgeEncryption,
        secureRandom = cryptoServices.secureRandom,
        keyDerivation = cryptoServices.keyDerivation,
        externalSecretsManager = externalSecretsManager
    )
    val vaultController = VaultController(vaultService)
    
    routing {
        // Service info endpoint
        get("/") {
            call.respond(ServiceInfo(
                name = "Eden Vault Service",
                version = "1.0.0",
                description = "Secure secrets management service with zero-knowledge encryption",
                status = "running"
            ))
        }
        
        // Enhanced health check
        get("/health") {
            val externalSecretsHealth = runBlocking {
                externalSecretsManager?.getHealthStatus() ?: ExternalSecretsManagerHealth(
                    type = "NONE",
                    available = false,
                    lastChecked = Clock.System.now()
                )
            }
            
            val healthResponse = VaultHealthResponse(
                status = "healthy",
                timestamp = Clock.System.now(),
                uptime = System.currentTimeMillis() - startTime,
                service = "vault",
                database = DatabaseHealth(
                    connected = true, // TODO: Add real database health check
                    responseTime = null,
                    activeConnections = null
                ),
                encryption = EncryptionHealth(
                    available = true,
                    algorithm = "AES-256-GCM",
                    keyDerivation = "PBKDF2"
                ),
                externalSecretsManager = ExternalSecretsManagerConfigResponse(
                    type = externalSecretsHealth.type,
                    isConfigured = externalSecretsHealth.available,
                    provider = if (externalSecretsHealth.available) externalSecretsHealth.type else null,
                    lastChecked = externalSecretsHealth.lastChecked
                )
            )
            call.respond(HttpStatusCode.OK, ApiResponse.success(healthResponse))
        }
        
        // Vault API routes
        with(vaultController) {
            vaultRoutes()
            statsRoutes()
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
        zeroKnowledgeEncryption = MockZeroKnowledgeEncryption(),
        secureRandom = MockSecureRandom(),
        keyDerivation = MockKeyDerivation()
    )
}

/**
 * Create external secrets manager based on environment configuration
 */
private fun createExternalSecretsManager(): ExternalSecretsManager? {
    val secretsManagerType = System.getenv("SECRETS_MANAGER_TYPE") ?: return null
    
    return when (secretsManagerType.uppercase()) {
        "HASHICORP_VAULT" -> {
            val vaultUrl = System.getenv("VAULT_URL") ?: return null
            val vaultToken = System.getenv("VAULT_TOKEN") ?: return null
            val vaultNamespace = System.getenv("VAULT_NAMESPACE") ?: "eden"
            
            val config = ExternalSecretsManagerConfig(
                type = SecretsManagerType.HASHICORP_VAULT,
                vaultUrl = vaultUrl,
                vaultToken = vaultToken,
                vaultNamespace = vaultNamespace
            )
            
            ExternalSecretsManager(config)
        }
        "AWS_SECRETS_MANAGER" -> {
            val awsRegion = System.getenv("AWS_REGION") ?: return null
            val awsAccessKey = System.getenv("AWS_ACCESS_KEY") ?: return null
            val awsSecretKey = System.getenv("AWS_SECRET_KEY") ?: return null
            val awsPrefix = System.getenv("AWS_SECRETS_PREFIX") ?: "eden/"
            
            val config = ExternalSecretsManagerConfig(
                type = SecretsManagerType.AWS_SECRETS_MANAGER,
                awsRegion = awsRegion,
                awsAccessKey = awsAccessKey,
                awsSecretKey = awsSecretKey,
                awsPrefix = awsPrefix
            )
            
            ExternalSecretsManager(config)
        }
        else -> null
    }
}

/**
 * Container for crypto services
 */
private data class CryptoServices(
    val encryption: Encryption,
    val zeroKnowledgeEncryption: ZeroKnowledgeEncryption,
    val secureRandom: SecureRandom,
    val keyDerivation: KeyDerivation
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

private class MockZeroKnowledgeEncryption : ZeroKnowledgeEncryption {
    override fun encryptZeroKnowledge(data: String, userPassword: String, salt: ByteArray?): ZeroKnowledgeResult {
        val actualSalt = salt ?: ByteArray(32) { it.toByte() }
        return ZeroKnowledgeResult(
            encryptedData = data.toByteArray(),
            salt = actualSalt,
            nonce = ByteArray(12),
            authTag = ByteArray(16),
            keyDerivationParams = KeyDerivationParams()
        )
    }
    
    override fun decryptZeroKnowledge(encryptedData: ZeroKnowledgeResult, userPassword: String): String? {
        return String(encryptedData.encryptedData)
    }
    
    override fun verifyIntegrity(encryptedData: ZeroKnowledgeResult): Boolean {
        return true
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

private class MockKeyDerivation : KeyDerivation {
    override fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        return ByteArray(keyLength) { (password.hashCode() + salt.contentHashCode()).toByte() }
    }
    
    override fun deriveKeyArgon2(password: String, salt: ByteArray, memory: Int, iterations: Int, parallelism: Int): ByteArray {
        return deriveKey(password, salt, iterations, 32)
    }
    
    override fun generateSalt(length: Int): ByteArray {
        return ByteArray(length) { (Math.random() * 256).toInt().toByte() }
    }
    
    override fun deriveKeys(masterKey: ByteArray, info: String, keyCount: Int, keyLength: Int): List<ByteArray> {
        return (1..keyCount).map { ByteArray(keyLength) { (masterKey.contentHashCode() + it).toByte() } }
    }
}

@Serializable
data class ServiceInfo(
    val name: String,
    val version: String,
    val description: String,
    val status: String
)

private val startTime = System.currentTimeMillis()