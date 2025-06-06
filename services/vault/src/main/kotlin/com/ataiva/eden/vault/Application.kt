package com.ataiva.eden.vault

import com.ataiva.eden.vault.service.*
import com.ataiva.eden.vault.controller.VaultController
import com.ataiva.eden.vault.model.*
import com.ataiva.eden.database.EdenDatabaseService
import com.ataiva.eden.database.PostgreSQLDatabaseService
import com.ataiva.eden.crypto.*
import com.ataiva.eden.crypto.BouncyCastleEncryption
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
/**
 * Creates production-ready cryptographic services for the Vault service
 *
 * This function initializes all the cryptographic components needed by the Vault service:
 * - Encryption: For symmetric encryption/decryption using AES-GCM
 * - ZeroKnowledgeEncryption: For zero-knowledge encryption schemes
 * - SecureRandom: For cryptographically secure random number generation
 * - KeyDerivation: For password-based key derivation using Argon2
 *
 * Security considerations:
 * - Uses BouncyCastle for cryptographic operations (FIPS 140-2 compliant)
 * - Implements Argon2id for password hashing (memory-hard and resistant to side-channel attacks)
 * - Uses Java's SecureRandom for random number generation
 * - Provides authenticated encryption with AES-GCM
 *
 * @return A CryptoServices instance containing all required cryptographic components
 */
private fun createCryptoServices(): CryptoServices {
    // Use BouncyCastleEncryption which implements all required interfaces
    val bouncyCastleEncryption = BouncyCastleEncryption()
    
    return CryptoServices(
        encryption = bouncyCastleEncryption,
        zeroKnowledgeEncryption = bouncyCastleEncryption,
        secureRandom = SecureRandomAdapter(), // Use our adapter for SecureRandom
        keyDerivation = bouncyCastleEncryption
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
 * Container for cryptographic services used by the Vault service
 *
 * This class encapsulates all the cryptographic components needed by the Vault service,
 * providing a clean way to inject these dependencies into the VaultService.
 *
 * The components are:
 * - encryption: For symmetric encryption/decryption operations
 * - zeroKnowledgeEncryption: For zero-knowledge encryption schemes where the server never has access to plaintext
 * - secureRandom: For generating cryptographically secure random numbers, tokens, and identifiers
 * - keyDerivation: For deriving cryptographic keys from passwords and other inputs
 *
 * In production, all these interfaces are implemented by the BouncyCastleEncryption class,
 * except for secureRandom which uses the SecureRandomAdapter to wrap Java's SecureRandom.
 */
private data class CryptoServices(
    val encryption: Encryption,
    val zeroKnowledgeEncryption: ZeroKnowledgeEncryption,
    val secureRandom: SecureRandom,
    val keyDerivation: KeyDerivation
)

// Production-ready crypto implementations are now used from shared/crypto module
// The mock implementations have been removed and replaced with BouncyCastleEncryption

/**
 * Adapter class to bridge the Java SecureRandom with our SecureRandom interface
 *
 * This class provides a cryptographically secure random number generator implementation
 * by wrapping Java's SecureRandom class. Java's SecureRandom uses the platform's
 * strongest available random number generator (e.g., /dev/urandom on Linux,
 * CryptGenRandom on Windows).
 *
 * Security considerations:
 * - Uses the default SecureRandom implementation which is suitable for most security needs
 * - Automatically seeds from the system entropy pool
 * - Provides methods for generating random bytes, strings, and UUIDs
 *
 * This implementation is suitable for:
 * - Generating cryptographic keys
 * - Creating random nonces and initialization vectors
 * - Generating secure tokens and identifiers
 */
private class SecureRandomAdapter : com.ataiva.eden.crypto.SecureRandom {
    // Use Java's SecureRandom implementation which is cryptographically secure
    private val secureRandom = java.security.SecureRandom()
    
    /**
     * Generates a random byte array of the specified size
     *
     * @param size The number of random bytes to generate
     * @return A byte array filled with random values
     */
    override fun nextBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        return bytes
    }
    
    /**
     * Generates a random string of the specified length using the given charset
     *
     * @param length The length of the string to generate
     * @param charset The set of characters to use (defaults to alphanumeric)
     * @return A random string
     */
    override fun nextString(length: Int, charset: String): String {
        return (1..length)
            .map { charset[secureRandom.nextInt(charset.length)] }
            .joinToString("")
    }
    
    /**
     * Generates a random UUID using Java's UUID.randomUUID()
     *
     * @return A random UUID string
     */
    override fun nextUuid(): String {
        return java.util.UUID.randomUUID().toString()
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