package com.ataiva.eden.hub

import com.ataiva.eden.hub.service.HubService
import com.ataiva.eden.hub.controller.HubController
import com.ataiva.eden.hub.model.*
import com.ataiva.eden.database.EdenDatabaseService
import com.ataiva.eden.database.PostgreSQLDatabaseService
import com.ataiva.eden.database.DatabaseConfig
import com.ataiva.eden.crypto.*

fun main() {
    println("Starting Eden Hub Service...")
    
    // Initialize dependencies
    val databaseService = createDatabaseService()
    val cryptoServices = createCryptoServices()
    val hubService = HubService(
        databaseService = databaseService,
        encryption = cryptoServices.encryption,
        secureRandom = cryptoServices.secureRandom
    )
    
    println("Eden Hub Service initialized successfully")
}

/**
 * Create database service with proper configuration
 */
private fun createDatabaseService(): EdenDatabaseService {
    val config = DatabaseConfig(
        url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/eden_dev",
        username = System.getenv("DATABASE_USERNAME") ?: "eden_user",
        password = System.getenv("DATABASE_PASSWORD") ?: "eden_password",
        driverClassName = "org.postgresql.Driver"
    )
    
    return PostgreSQLDatabaseService(config)
}

/**
 * Create crypto services with proper implementations
 */
private fun createCryptoServices(): CryptoServices {
    return CryptoServices(
        encryption = MockEncryption(),
        secureRandom = SecureRandom()
    )
}

/**
 * Container for crypto services
 */
private data class CryptoServices(
    val encryption: Encryption,
    val secureRandom: SecureRandom
)

// Simplified mock implementations that match the interfaces
private class MockEncryption : Encryption {
    override suspend fun encrypt(data: ByteArray, key: ByteArray): EncryptionResult {
        return EncryptionResult(data, ByteArray(12), ByteArray(16))
    }
    
    override suspend fun decrypt(data: ByteArray, key: ByteArray, nonce: ByteArray, authTag: ByteArray?): DecryptionResult {
        return DecryptionResult.Success(data)
    }
    
    override suspend fun encryptString(text: String, key: ByteArray): EncryptionResult {
        return encrypt(text.encodeToByteArray(), key)
    }
    
    override suspend fun decryptString(data: ByteArray, key: ByteArray, nonce: ByteArray, authTag: ByteArray?): String? {
        return when (val result = decrypt(data, key, nonce, authTag)) {
            is DecryptionResult.Success -> result.data.decodeToString()
            is DecryptionResult.Failure -> null
        }
    }
}

data class ServiceInfo(
    val name: String,
    val version: String,
    val description: String,
    val status: String,
    val features: List<String> = emptyList()
)