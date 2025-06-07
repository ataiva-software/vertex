package com.ataiva.eden.crypto

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for the encryption implementation
 */
class EncryptionTest {
    
    // Register the JVM provider before running tests
    init {
        registerJvmEncryptionProvider()
    }
    
    @Test
    fun `test symmetric encryption and decryption`() = runBlocking {
        // Create encryption instance
        val encryption = EncryptionFactory.createEncryption()
        
        // Test data
        val originalData = "This is a test message for encryption".encodeToByteArray()
        val key = ByteArray(32) { it.toByte() } // Test key
        
        // Encrypt data
        val encryptionResult = encryption.encrypt(originalData, key)
        
        // Verify encryption result
        assertNotNull(encryptionResult.encryptedData)
        assertNotNull(encryptionResult.nonce)
        assertNotNull(encryptionResult.authTag)
        
        // Decrypt data
        val decryptionResult = encryption.decrypt(
            encryptionResult.encryptedData,
            key,
            encryptionResult.nonce,
            encryptionResult.authTag
        )
        
        // Verify decryption result
        assertTrue(decryptionResult is DecryptionResult.Success)
        if (decryptionResult is DecryptionResult.Success) {
            assertEquals(
                originalData.decodeToString(),
                decryptionResult.data.decodeToString()
            )
        }
    }
    
    @Test
    fun `test zero-knowledge encryption and decryption`() = runBlocking {
        // Create encryption instance
        val encryption = EncryptionFactory.createEncryption()
        
        // Test data
        val originalData = "This is a test message for zero-knowledge encryption"
        val password = "test-password-123"
        
        // Encrypt data with zero-knowledge approach
        val zkEncryption = encryption as ZeroKnowledgeEncryption
        val encryptionResult = zkEncryption.encryptZeroKnowledge(originalData, password)
        
        // Verify encryption result
        assertNotNull(encryptionResult.encryptedData)
        assertNotNull(encryptionResult.salt)
        assertNotNull(encryptionResult.nonce)
        assertNotNull(encryptionResult.authTag)
        
        // Decrypt data
        val decryptedData = zkEncryption.decryptZeroKnowledge(encryptionResult, password)
        
        // Verify decryption result
        assertNotNull(decryptedData)
        assertEquals(originalData, decryptedData)
        
        // Verify integrity
        val integrityResult = zkEncryption.verifyIntegrity(encryptionResult)
        assertTrue(integrityResult)
        
        // Test with wrong password
        val wrongDecryptedData = zkEncryption.decryptZeroKnowledge(encryptionResult, "wrong-password")
        assertEquals(null, wrongDecryptedData)
    }
    
    @Test
    fun `test integrity verification`() = runBlocking {
        // Create encryption instance
        val encryption = EncryptionFactory.createEncryption()
        
        // Test data
        val originalData = "This is a test message for integrity verification"
        val password = "test-password-123"
        
        // Encrypt data with zero-knowledge approach
        val zkEncryption = encryption as ZeroKnowledgeEncryption
        val encryptionResult = zkEncryption.encryptZeroKnowledge(originalData, password)
        
        // Verify integrity with correct data
        val integrityResult = zkEncryption.verifyIntegrity(encryptionResult)
        assertTrue(integrityResult)
        
        // Create tampered data
        val tamperedData = encryptionResult.encryptedData.copyOf()
        tamperedData[0] = (tamperedData[0] + 1).toByte() // Modify one byte
        
        val tamperedResult = encryptionResult.copy(encryptedData = tamperedData)
        
        // Verify integrity with tampered data
        val tamperedIntegrityResult = zkEncryption.verifyIntegrity(tamperedResult)
        assertFalse(tamperedIntegrityResult)
    }
    
    @Test
    fun `test key derivation`() = runBlocking {
        // Create key derivation instance
        val keyDerivation = EncryptionFactory.createKeyDerivation()
        
        // Test data
        val password = "test-password-123"
        val salt = ByteArray(16) { it.toByte() }
        
        // Derive key with PBKDF2
        val key1 = keyDerivation.deriveKey(password, salt)
        val key2 = keyDerivation.deriveKey(password, salt)
        
        // Verify keys are consistent
        assertTrue(key1.contentEquals(key2))
        assertEquals(32, key1.size)
        
        // Derive key with Argon2
        val argonKey1 = keyDerivation.deriveKeyArgon2(password, salt)
        val argonKey2 = keyDerivation.deriveKeyArgon2(password, salt)
        
        // Verify keys are consistent
        assertTrue(argonKey1.contentEquals(argonKey2))
        assertEquals(32, argonKey1.size)
        
        // Verify different passwords produce different keys
        val differentKey = keyDerivation.deriveKey("different-password", salt)
        assertFalse(key1.contentEquals(differentKey))
    }
}