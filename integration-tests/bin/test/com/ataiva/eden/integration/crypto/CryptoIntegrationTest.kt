package com.ataiva.eden.integration.crypto

import com.ataiva.eden.crypto.BouncyCastleEncryption
import com.ataiva.eden.crypto.CryptoFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Integration tests for crypto functionality across different scenarios
 */
class CryptoIntegrationTest {
    
    private val encryption = BouncyCastleEncryption()
    
    @Test
    fun `end-to-end encryption workflow should work correctly`() = runTest {
        // Simulate a complete encryption workflow
        val originalData = "Sensitive production data that needs encryption"
        val userPassword = "UserSecurePassword123!"
        
        // Step 1: Generate encryption key from user password
        val salt = encryption.generateSalt(32)
        val encryptionKey = encryption.deriveKey(userPassword, salt, 100000, 32)
        
        // Step 2: Encrypt the data
        val encryptionResult = encryption.encrypt(originalData.toByteArray(), encryptionKey)
        
        // Step 3: Store encrypted data (simulate database storage)
        val storedData = mapOf(
            "encryptedData" to encryptionResult.encryptedData,
            "nonce" to encryptionResult.nonce,
            "authTag" to encryptionResult.authTag,
            "salt" to salt
        )
        
        // Step 4: Retrieve and decrypt (simulate retrieval from database)
        val retrievedKey = encryption.deriveKey(userPassword, storedData["salt"]!!, 100000, 32)
        val combinedData = storedData["encryptedData"]!! + storedData["authTag"]!!
        val decryptedData = encryption.decryptString(
            combinedData,
            retrievedKey,
            storedData["nonce"]!!
        )
        
        assertEquals(originalData, decryptedData)
    }
    
    @Test
    fun `zero-knowledge encryption integration should work`() = runTest {
        val sensitiveData = "API keys and secrets"
        val userPassword = "ComplexUserPassword456!"
        
        // Client-side encryption
        val zkResult = encryption.encryptZeroKnowledge(sensitiveData, userPassword)
        
        // Verify integrity
        assertTrue(encryption.verifyIntegrity(zkResult))
        
        // Server cannot decrypt without user password
        val wrongPassword = "WrongPassword"
        val failedDecryption = encryption.decryptZeroKnowledge(zkResult, wrongPassword)
        assertNull(failedDecryption)
        
        // User can decrypt with correct password
        val successfulDecryption = encryption.decryptZeroKnowledge(zkResult, userPassword)
        assertEquals(sensitiveData, successfulDecryption)
    }
    
    @Test
    fun `digital signature workflow should work correctly`() = runTest {
        // Generate key pair for signing
        val keyPair = encryption.generateKeyPair()
        
        // Sign important data
        val importantData = "Critical system configuration"
        val signature = encryption.signString(importantData, keyPair.privateKey)
        
        // Verify signature
        assertTrue(encryption.verifyString(importantData, signature, keyPair.publicKey))
        
        // Tampered data should fail verification
        val tamperedData = "Critical system configuration - MODIFIED"
        assertFalse(encryption.verifyString(tamperedData, signature, keyPair.publicKey))
    }
    
    @Test
    fun `crypto factory should provide consistent instances`() {
        val encryption1 = CryptoFactory.createEncryption()
        val encryption2 = CryptoFactory.createEncryption()
        
        // Should be different instances but same functionality
        assertNotSame(encryption1, encryption2)
        
        // Test that both work the same way
        val testData = "Test data for consistency"
        val key = ByteArray(32) { it.toByte() }
        
        val result1 = encryption1.encryptString(testData, key)
        val result2 = encryption2.encryptString(testData, key)
        
        // Results should be different (due to random nonces) but both should decrypt correctly
        assertFalse(result1.encryptedData.contentEquals(result2.encryptedData))
        
        val decrypted1 = encryption1.decryptString(
            result1.encryptedData + result1.authTag!!,
            key,
            result1.nonce
        )
        val decrypted2 = encryption2.decryptString(
            result2.encryptedData + result2.authTag!!,
            key,
            result2.nonce
        )
        
        assertEquals(testData, decrypted1)
        assertEquals(testData, decrypted2)
    }
    
    @Test
    fun `performance test for encryption operations`() = runTest {
        val testData = "Performance test data that simulates real-world usage scenarios"
        val key = encryption.generateSalt(32)
        
        val startTime = System.currentTimeMillis()
        
        // Perform multiple encryption/decryption cycles
        repeat(100) {
            val encrypted = encryption.encryptString(testData, key)
            val decrypted = encryption.decryptString(
                encrypted.encryptedData + encrypted.authTag!!,
                key,
                encrypted.nonce
            )
            assertEquals(testData, decrypted)
        }
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        
        // Should complete 100 cycles in reasonable time (less than 5 seconds)
        assertTrue(totalTime < 5000, "Encryption performance test took too long: ${totalTime}ms")
        println("100 encryption/decryption cycles completed in ${totalTime}ms")
    }
    
    @Test
    fun `key derivation should be consistent and secure`() = runTest {
        val password = "TestPassword123!"
        val salt = encryption.generateSalt(16)
        
        // Same inputs should produce same outputs
        val key1 = encryption.deriveKey(password, salt, 10000, 32)
        val key2 = encryption.deriveKey(password, salt, 10000, 32)
        
        assertContentEquals(key1, key2)
        
        // Different salts should produce different keys
        val differentSalt = encryption.generateSalt(16)
        val key3 = encryption.deriveKey(password, differentSalt, 10000, 32)
        
        assertFalse(key1.contentEquals(key3))
        
        // Different passwords should produce different keys
        val differentPassword = "DifferentPassword123!"
        val key4 = encryption.deriveKey(differentPassword, salt, 10000, 32)
        
        assertFalse(key1.contentEquals(key4))
    }
    
    @Test
    fun `multiple key derivation should work correctly`() = runTest {
        val masterKey = encryption.generateSalt(32)
        val info = "Eden DevOps Key Derivation"
        
        val keys = encryption.deriveKeys(masterKey, info, 5, 32)
        
        assertEquals(5, keys.size)
        
        // All keys should be different
        for (i in keys.indices) {
            for (j in i + 1 until keys.size) {
                assertFalse(keys[i].contentEquals(keys[j]), "Keys $i and $j should be different")
            }
        }
        
        // All keys should be the correct length
        keys.forEach { key ->
            assertEquals(32, key.size)
        }
    }
    
    @Test
    fun `password strength validation should work correctly`() = runTest {
        val testCases = mapOf(
            "123" to false,                    // Too short
            "password" to false,               // No uppercase, numbers, or symbols
            "Password" to false,               // No numbers or symbols
            "Password123" to false,            // No symbols
            "Password123!" to true,            // Strong password
            "VeryStrongP@ssw0rd2024!" to true  // Very strong password
        )
        
        testCases.forEach { (password, expectedValid) ->
            val result = encryption.validatePasswordStrength(password)
            assertEquals(expectedValid, result.isValid, "Password '$password' validation failed")
            
            if (expectedValid) {
                assertTrue(result.errors.isEmpty(), "Strong password should have no errors")
                assertTrue(result.score >= 80, "Strong password should have high score")
            } else {
                assertTrue(result.errors.isNotEmpty(), "Weak password should have errors")
                assertTrue(result.score < 80, "Weak password should have low score")
            }
        }
    }
}