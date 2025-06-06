package com.ataiva.eden.hub

import com.ataiva.eden.hub.service.HubService
import com.ataiva.eden.hub.controller.HubController
import com.ataiva.eden.hub.model.*
import com.ataiva.eden.database.EdenDatabaseService
import com.ataiva.eden.database.PostgreSQLDatabaseServiceImpl
import com.ataiva.eden.database.DatabaseConfig
import com.ataiva.eden.crypto.*
import com.ataiva.eden.hub.crypto.KeyManagementSystem
import com.ataiva.eden.config.DatabaseConfigLoader

fun main() {
    println("Starting Eden Hub Service...")
    
    // Initialize dependencies
    val databaseService = createDatabaseService()
    val cryptoServices = createCryptoServices(databaseService)
    val hubService = HubService(
        databaseService = databaseService,
        encryption = cryptoServices.encryption,
        secureRandom = cryptoServices.secureRandom,
        keyManagementSystem = cryptoServices.keyManagementSystem
    )
    
    println("Eden Hub Service initialized successfully")
}

/**
 * Create database service with proper configuration
 */
private fun createDatabaseService(): EdenDatabaseService {
    // Load database configuration from file or environment variables
    val environment = System.getenv("EDEN_ENVIRONMENT") ?: "dev"
    val configPath = System.getenv("EDEN_CONFIG_PATH") ?: "application.properties"
    
    val config = DatabaseConfigLoader().loadFromFile(configPath, environment)
    
    return PostgreSQLDatabaseServiceImpl(config)
}

/**
 * Create crypto services with proper implementations
 */
private fun createCryptoServices(databaseService: EdenDatabaseService): CryptoServices {
    val encryption = BouncyCastleEncryption()
    val secureRandom = SecureRandom()
    val keyManagementSystem = KeyManagementSystem(encryption, secureRandom, databaseService)
    
    return CryptoServices(
        encryption = encryption,
        secureRandom = secureRandom,
        keyManagementSystem = keyManagementSystem
    )
}

/**
 * Container for crypto services
 */
private data class CryptoServices(
    val encryption: Encryption,
    val secureRandom: SecureRandom,
    val keyManagementSystem: KeyManagementSystem
)

// Removed MockEncryption implementation as we're now using BouncyCastleEncryption

data class ServiceInfo(
    val name: String,
    val version: String,
    val description: String,
    val status: String,
    val features: List<String> = emptyList()
)