package com.ataiva.eden.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BouncyCastleEncryptionTest {
    
    private val encryption = BouncyCastleEncryption()
    private val testKey = ByteArray(32) { it.toByte() } // 32-byte key for AES-256
    private val testData = "Hello, Eden DevOps Suite!".toByteArray()
    
    @Test
    fun `encrypt and decrypt should work correctly`() {
        val encryptionResult = encryption.encrypt(testData, testKey)
        
        assertNotNull(encryptionResult)
        assertEquals(12, encryptionResult.nonce.size) // GCM nonce size
        assertEquals(16, encryptionResult.authTag?.size) // GCM auth tag size
        assertTrue(encryptionResult.encryptedData.isNotEmpty())
        
        val decryptionResult = encryption.decrypt(
            encryptionResult.encryptedData + encryptionResult.authTag!!,
            testKey,
            encryptionResult.nonce
        )
        
        assertTrue(decryptionResult is DecryptionResult.Success)
        assertContentEquals(testData, (decryptionResult as DecryptionResult.Success).data)
    }
    
    @Test
    fun `encrypt and decrypt string should work correctly`() {
        val testString = "Hello, Eden DevOps Suite!"
        val encryptionResult = encryption.encryptString(testString, testKey)
        
        assertNotNull(encryptionResult)
        assertTrue(encryptionResult.encryptedData.isNotEmpty())
        
        val decryptedString = encryption.decryptString(
            encryptionResult.encryptedData + encryptionResult.authTag!!,
            testKey,
            encryptionResult.nonce
        )
        
        assertEquals(testString, decryptedString)
    }
    
    @Test
    fun `decrypt with wrong key should fail`() {
        val encryptionResult = encryption.encrypt(testData, testKey)
        val wrongKey = ByteArray(32) { (it + 1).toByte() }
        
        val decryptionResult = encryption.decrypt(
            encryptionResult.encryptedData + encryptionResult.authTag!!,
            wrongKey,
            encryptionResult.nonce
        )
        
        assertTrue(decryptionResult is DecryptionResult.Failure)
    }
    
    @Test
    fun `key derivation should work correctly`() {
        val password = "test-password"
        val salt = encryption.generateSalt(16)
        
        val key1 = encryption.deriveKey(password, salt, 1000, 32)
        val key2 = encryption.deriveKey(password, salt, 1000, 32)
        
        assertEquals(32, key1.size)
        assertEquals(32, key2.size)
        assertContentEquals(key1, key2) // Same password and salt should produce same key
        
        val differentSalt = encryption.generateSalt(16)
        val key3 = encryption.deriveKey(password, differentSalt, 1000, 32)
        
        assertFalse(key1.contentEquals(key3)) // Different salt should produce different key
    }
    
    @Test
    fun `generate salt should produce different values`() {
        val salt1 = encryption.generateSalt(32)
        val salt2 = encryption.generateSalt(32)
        
        assertEquals(32, salt1.size)
        assertEquals(32, salt2.size)
        assertFalse(salt1.contentEquals(salt2))
    }
    
    @Test
    fun `derive multiple keys should work correctly`() {
        val masterKey = encryption.generateSalt(32)
        val info = "test-info"
        val keys = encryption.deriveKeys(masterKey, info, 3, 32)
        
        assertEquals(3, keys.size)
        keys.forEach { key ->
            assertEquals(32, key.size)
        }
        
        // All keys should be different
        assertFalse(keys[0].contentEquals(keys[1]))
        assertFalse(keys[1].contentEquals(keys[2]))
        assertFalse(keys[0].contentEquals(keys[2]))
    }
    
    @Test
    fun `zero knowledge encryption should work correctly`() {
        val testData = "Secret data for zero knowledge encryption"
        val password = "user-password"
        
        val zkResult = encryption.encryptZeroKnowledge(testData, password)
        
        assertNotNull(zkResult)
        assertTrue(zkResult.encryptedData.isNotEmpty())
        assertTrue(zkResult.salt.isNotEmpty())
        assertTrue(zkResult.nonce.isNotEmpty())
        assertTrue(zkResult.authTag.isNotEmpty())
        assertTrue(encryption.verifyIntegrity(zkResult))
        
        val decryptedData = encryption.decryptZeroKnowledge(zkResult, password)
        assertEquals(testData, decryptedData)
        
        // Wrong password should fail
        val wrongDecryption = encryption.decryptZeroKnowledge(zkResult, "wrong-password")
        assertNull(wrongDecryption)
    }
    
    @Test
    fun `digital signature should work correctly`() {
        val keyPair = encryption.generateKeyPair()
        val testMessage = "Message to sign"
        
        assertNotNull(keyPair)
        assertTrue(keyPair.publicKey.isNotEmpty())
        assertTrue(keyPair.privateKey.isNotEmpty())
        
        val signature = encryption.signString(testMessage, keyPair.privateKey)
        assertTrue(signature.isNotEmpty())
        
        val isValid = encryption.verifyString(testMessage, signature, keyPair.publicKey)
        assertTrue(isValid)
        
        // Wrong message should fail verification
        val wrongMessageValid = encryption.verifyString("Wrong message", signature, keyPair.publicKey)
        assertFalse(wrongMessageValid)
        
        // Wrong public key should fail verification
        val anotherKeyPair = encryption.generateKeyPair()
        val wrongKeyValid = encryption.verifyString(testMessage, signature, anotherKeyPair.publicKey)
        assertFalse(wrongKeyValid)
    }
    
    @Test
    fun `secure random should generate different values`() {
        val bytes1 = encryption.nextBytes(32)
        val bytes2 = encryption.nextBytes(32)
        
        assertEquals(32, bytes1.size)
        assertEquals(32, bytes2.size)
        assertFalse(bytes1.contentEquals(bytes2))
        
        val string1 = encryption.nextString(16)
        val string2 = encryption.nextString(16)
        
        assertEquals(16, string1.length)
        assertEquals(16, string2.length)
        assertNotEquals(string1, string2)
        
        val uuid1 = encryption.nextUuid()
        val uuid2 = encryption.nextUuid()
        
        assertTrue(uuid1.contains("-"))
        assertTrue(uuid2.contains("-"))
        assertNotEquals(uuid1, uuid2)
    }
    
    @Test
    fun `password hashing should work correctly`() {
        val password = "test-password-123"
        val hash = encryption.hashPassword(password)
        
        assertTrue(hash.isNotEmpty())
        assertTrue(hash.startsWith("$2a$")) // BCrypt hash format
        
        assertTrue(encryption.verifyPassword(password, hash))
        assertFalse(encryption.verifyPassword("wrong-password", hash))
    }
    
    @Test
    fun `password strength validation should work correctly`() {
        val weakPassword = "123"
        val weakResult = encryption.validatePasswordStrength(weakPassword)
        assertFalse(weakResult.isValid)
        assertTrue(weakResult.errors.isNotEmpty())
        assertTrue(weakResult.score < 50)
        
        val strongPassword = "StrongP@ssw0rd123!"
        val strongResult = encryption.validatePasswordStrength(strongPassword)
        assertTrue(strongResult.isValid)
        assertTrue(strongResult.errors.isEmpty())
        assertTrue(strongResult.score >= 80)
    }
    
    @Test
    fun `MFA secret generation should work correctly`() {
        val userId = "test-user-123"
        val secret = encryption.generateSecret(userId)
        
        assertTrue(secret.isNotEmpty())
        
        val qrUrl = encryption.generateQrCodeUrl(userId, secret, "Eden DevOps")
        assertTrue(qrUrl.startsWith("otpauth://totp/"))
        assertTrue(qrUrl.contains("Eden DevOps"))
        assertTrue(qrUrl.contains(secret))
    }
    
    @Test
    fun `backup codes generation should work correctly`() {
        val codes = encryption.generateBackupCodes(10)
        
        assertEquals(10, codes.size)
        codes.forEach { code ->
            assertTrue(code.isNotEmpty())
            assertTrue(code.length == 16) // 8 bytes * 2 hex chars
        }
        
        // All codes should be different
        val uniqueCodes = codes.toSet()
        assertEquals(codes.size, uniqueCodes.size)
    }
    
    @Test
    fun `argon2 key derivation should work correctly`() = runTest {
        val password = "test-password"
        val salt = encryption.generateSalt(16)
        
        // Test with different memory settings
        val key1 = encryption.deriveKeyArgon2(password, salt, 65536, 3, 4)
        val key2 = encryption.deriveKeyArgon2(password, salt, 65536, 3, 4)
        
        assertEquals(32, key1.size)
        assertEquals(32, key2.size)
        assertContentEquals(key1, key2) // Same parameters should produce same key
        
        // Test with different iterations
        val key3 = encryption.deriveKeyArgon2(password, salt, 65536, 5, 4)
        
        assertEquals(32, key3.size)
        assertFalse(key1.contentEquals(key3)) // Different iterations should produce different key
        
        // Test with different parallelism
        val key4 = encryption.deriveKeyArgon2(password, salt, 65536, 3, 8)
        
        assertEquals(32, key4.size)
        assertFalse(key1.contentEquals(key4)) // Different parallelism should produce different key
        
        // Test with different memory
        val key5 = encryption.deriveKeyArgon2(password, salt, 32768, 3, 4)
        
        assertEquals(32, key5.size)
        assertFalse(key1.contentEquals(key5)) // Different memory should produce different key
        
        // Test with different password
        val key6 = encryption.deriveKeyArgon2("different-password", salt, 65536, 3, 4)
        
        assertEquals(32, key6.size)
        assertFalse(key1.contentEquals(key6)) // Different password should produce different key
        
        // Test with different salt
        val differentSalt = encryption.generateSalt(16)
        val key7 = encryption.deriveKeyArgon2(password, differentSalt, 65536, 3, 4)
        
        assertEquals(32, key7.size)
        assertFalse(key1.contentEquals(key7)) // Different salt should produce different key
    }
    
    @Test
    fun `crypto factory should create instances correctly`() {
        val encryptionInstance = CryptoFactory.createEncryption()
        assertNotNull(encryptionInstance)
        assertTrue(encryptionInstance is BouncyCastleEncryption)
        
        val keyDerivationInstance = CryptoFactory.createKeyDerivation()
        assertNotNull(keyDerivationInstance)
        assertTrue(keyDerivationInstance is BouncyCastleEncryption)
        
        val zkEncryptionInstance = CryptoFactory.createZeroKnowledgeEncryption()
        assertNotNull(zkEncryptionInstance)
        assertTrue(zkEncryptionInstance is BouncyCastleEncryption)
        
        val digitalSignatureInstance = CryptoFactory.createDigitalSignature()
        assertNotNull(digitalSignatureInstance)
        assertTrue(digitalSignatureInstance is BouncyCastleEncryption)
        
        val secureRandomInstance = CryptoFactory.createSecureRandom()
        assertNotNull(secureRandomInstance)
        assertTrue(secureRandomInstance is BouncyCastleEncryption)
    }
}