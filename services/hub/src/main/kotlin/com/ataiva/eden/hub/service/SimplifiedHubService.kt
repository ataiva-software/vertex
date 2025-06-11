package com.ataiva.eden.hub.service

import com.ataiva.eden.hub.crypto.MockKeyManagementSystem
import java.security.SecureRandom
import java.util.logging.Logger

/**
 * Simplified Hub Service for testing purposes
 */
class MockHubService(
    private val secureRandom: SecureRandom,
    private val keyManagementSystem: MockKeyManagementSystem
) {
    private val logger = Logger.getLogger(HubService::class.java.name)
    
    init {
        // Initialize key management system
        keyManagementSystem.initialize()
        logger.info("Hub Service initialized with simplified dependencies")
    }
    
    /**
     * Get service status
     */
    fun getStatus(): String {
        return "running"
    }
    
    /**
     * Generate a random ID
     */
    fun generateId(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Get current timestamp from key management system
     */
    fun getCurrentTimestamp(): String {
        return keyManagementSystem.getCurrentTimestamp()
    }
}