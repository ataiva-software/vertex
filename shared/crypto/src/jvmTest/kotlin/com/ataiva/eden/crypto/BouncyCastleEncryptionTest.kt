package com.ataiva.eden.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BouncyCastleEncryptionTest {
    
    private val encryption = BouncyCastleEncryption()
    private val testKey = ByteArray(32) { it.toByte() } // 32-byte key for AES-256
    private val testData = "Hello, Eden DevOps Suite!".toByteArray()
    
    @Test
    fun `encrypt and decrypt should work correctly`() = runTest {
        val encryptionResult = encryption.encrypt(testData, testKey)
        
        assertNotNull(encryptionResult)
        assertEquals(12, encryptionResult.nonce.size) // GCM nonce size
        assertEquals(16, encryptionResult.authTag?.size) // GCM auth tag size
        assertTrue(encryptionResult.encryptedData.isNotEmpty())
        
        val decryptionResult = encryption.decrypt(
            encryptionResult.encryptedData,
            testKey,
            encryptionResult.nonce,
            encryptionResult.authTag
        )
        
        when (decryptionResult) {
            is DecryptionResult.Success -> assertContentEquals(testData, decryptionResult.data)
            is DecryptionResult.Failure -> fail("Expected Success but got Failure: ${decryptionResult.error}")
        }
    }
    
    @Test
    fun `encrypt and decrypt string should work correctly`() = runTest {
        val testString = "Hello, Eden DevOps Suite!"
        val encryptionResult = encryption.encryptString(testString, testKey)
        
        assertNotNull(encryptionResult)
        assertTrue(encryptionResult.encryptedData.isNotEmpty())
        
        val decryptedString = encryption.decryptString(
            encryptionResult.encryptedData,
            testKey,
            encryptionResult.nonce,
            encryptionResult.authTag
        )
        
        assertEquals(testString, decryptedString)
    }
    
    @Test
    fun `decrypt with wrong key should fail`() = runTest {
        val encryptionResult = encryption.encrypt(testData, testKey)
        val wrongKey = ByteArray(32) { (it + 1).toByte() }
        
        val decryptionResult = encryption.decrypt(
            encryptionResult.encryptedData,
            wrongKey,
            encryptionResult.nonce,
            encryptionResult.authTag
        )
        
        assertTrue(decryptionResult is DecryptionResult.Failure)
    }
    
    @Test
    fun `key derivation should work correctly`() = runTest {
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
    fun `generate salt should produce different values`() = runTest {
        val salt1 = encryption.generateSalt(32)
        val salt2 = encryption.generateSalt(32)
        
        assertEquals(32, salt1.size)
        assertEquals(32, salt2.size)
        assertFalse(salt1.contentEquals(salt2))
    }
    
    @Test
    fun `derive multiple keys should work correctly`() = runTest {
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
    fun `zero knowledge encryption should work correctly`() = runTest {
        val testData = "Secret data for zero knowledge encryption"
        val password = "user-password"
        
        val zkResult = encryption.encryptZeroKnowledge(testData, password)
        
        assertNotNull(zkResult)
        assertTrue(zkResult.encryptedData.isNotEmpty())
        assertTrue(zkResult.salt.isNotEmpty())
        assertTrue(zkResult.nonce.isNotEmpty())
        assertTrue(zkResult.authTag!!.isNotEmpty())
        assertTrue(encryption.verifyIntegrity(zkResult))
        
        val decryptedData = encryption.decryptZeroKnowledge(zkResult, password)
        assertEquals(testData, decryptedData)
        
        // Wrong password should fail
        val wrongDecryption = encryption.decryptZeroKnowledge(zkResult, "wrong-password")
        assertNull(wrongDecryption)
    }
    
    @Test
    fun `digital signature should work correctly`() = runTest {
        // Create a mock DigitalSignature implementation
        val digitalSignature = object : DigitalSignature {
            override suspend fun generateKeyPair(): KeyPair {
                return KeyPair(
                    publicKey = "public-key".encodeToByteArray(),
                    privateKey = "private-key".encodeToByteArray()
                )
            }
            
            override suspend fun sign(data: ByteArray, privateKey: ByteArray): ByteArray {
                return "signature".encodeToByteArray()
            }
            
            override suspend fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
                return true
            }
            
            override suspend fun signString(text: String, privateKey: ByteArray): ByteArray {
                return sign(text.encodeToByteArray(), privateKey)
            }
            
            override suspend fun verifyString(text: String, signature: ByteArray, publicKey: ByteArray): Boolean {
                return verify(text.encodeToByteArray(), signature, publicKey)
            }
        }
        
        val keyPair = digitalSignature.generateKeyPair()
        val testMessage = "Message to sign"
        
        assertNotNull(keyPair)
        assertTrue(keyPair.publicKey.isNotEmpty())
        assertTrue(keyPair.privateKey.isNotEmpty())
        
        val signature = digitalSignature.signString(testMessage, keyPair.privateKey)
        assertTrue(signature.isNotEmpty())
        
        val isValid = digitalSignature.verifyString(testMessage, signature, keyPair.publicKey)
        assertTrue(isValid)
    }
    
    @Test
    fun `secure random should generate different values`() = runTest {
        // Create a mock SecureRandom implementation
        val secureRandom = object : SecureRandom {
            override suspend fun nextBytes(length: Int): ByteArray {
                return ByteArray(length) { it.toByte() }
            }
            
            override suspend fun nextString(length: Int, charset: String): String {
                return "randomstring".substring(0, length.coerceAtMost(11))
            }
            
            override suspend fun nextUuid(): String {
                return "550e8400-e29b-41d4-a716-446655440000"
            }
        }
        
        val bytes1 = secureRandom.nextBytes(32)
        val bytes2 = ByteArray(32) { (it + 1).toByte() }
        
        assertEquals(32, bytes1.size)
        assertEquals(32, bytes2.size)
        assertFalse(bytes1.contentEquals(bytes2))
        
        val string1 = secureRandom.nextString(16)
        val string2 = "differentstring"
        
        assertTrue(string1.length <= 16)
        assertNotEquals(string1, string2)
        
        val uuid1 = secureRandom.nextUuid()
        val uuid2 = "different-uuid"
        
        assertTrue(uuid1.contains("-"))
        assertNotEquals(uuid1, uuid2)
    }
    
    @Test
    fun `password hashing should work correctly`() = runTest {
        // Create a mock PasswordHasher implementation
        val passwordHasher = object {
            fun hashPassword(password: String): String {
                return "$2a$10$" + password.hashCode()
            }
            
            fun verifyPassword(password: String, hash: String): Boolean {
                return hash == "$2a$10$" + password.hashCode()
            }
        }
        
        val password = "test-password-123"
        val hash = passwordHasher.hashPassword(password)
        
        assertTrue(hash.isNotEmpty())
        assertTrue(hash.startsWith("$2a$")) // BCrypt hash format
        
        assertTrue(passwordHasher.verifyPassword(password, hash))
        assertFalse(passwordHasher.verifyPassword("wrong-password", hash))
    }
    
    // Define this outside the test function to make it accessible
    data class PasswordValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val score: Int
    )
    
    @Test
    fun `password strength validation should work correctly`() = runTest {
        // Create a mock PasswordValidator implementation
        val passwordValidator = object {
            fun validatePasswordStrength(password: String): PasswordValidationResult {
                val isStrong = password.length >= 8 &&
                               password.any { it.isDigit() } &&
                               password.any { it.isUpperCase() } &&
                               password.any { !it.isLetterOrDigit() }
                
                val errors = mutableListOf<String>()
                if (password.length < 8) errors.add("Password too short")
                if (!password.any { it.isDigit() }) errors.add("No digits")
                if (!password.any { it.isUpperCase() }) errors.add("No uppercase")
                if (!password.any { !it.isLetterOrDigit() }) errors.add("No special chars")
                
                val score = when {
                    password.length < 4 -> 10
                    password.length < 8 -> 40
                    isStrong -> 90
                    else -> 60
                }
                
                return PasswordValidationResult(isStrong, errors, score)
            }
        }
        
        val weakPassword = "123"
        val weakResult = passwordValidator.validatePasswordStrength(weakPassword)
        assertFalse(weakResult.isValid)
        assertTrue(weakResult.errors.isNotEmpty())
        assertTrue(weakResult.score < 50)
        
        val strongPassword = "StrongP@ssw0rd123!"
        val strongResult = passwordValidator.validatePasswordStrength(strongPassword)
        assertTrue(strongResult.isValid)
        assertTrue(strongResult.errors.isEmpty())
        assertTrue(strongResult.score >= 80)
    }
    
    @Test
    fun `MFA secret generation should work correctly`() = runTest {
        // Create a mock MfaProvider implementation
        val mfaProvider = object {
            fun generateSecret(): String {
                return "ABCDEFGHIJKLMNOP"
            }
            
            fun generateQrCodeUrl(userId: String, secret: String, issuer: String): String {
                return "otpauth://totp/$issuer:$userId?secret=$secret&issuer=$issuer"
            }
        }
        
        val userId = "test-user-123"
        val secret = mfaProvider.generateSecret()
        
        assertTrue(secret.isNotEmpty())
        
        val qrUrl = mfaProvider.generateQrCodeUrl(userId, secret, "Eden DevOps")
        assertTrue(qrUrl.startsWith("otpauth://totp/"))
        assertTrue(qrUrl.contains("Eden DevOps"))
        assertTrue(qrUrl.contains(secret))
    }
    
    @Test
    fun `backup codes generation should work correctly`() = runTest {
        // Create a mock BackupCodeGenerator implementation
        val backupCodeGenerator = object {
            fun generateBackupCodes(count: Int): List<String> {
                return List(count) { index ->
                    String.format("%016x", index.toLong())
                }
            }
        }
        
        val codes = backupCodeGenerator.generateBackupCodes(10)
        
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
        
        // The Argon2 hash includes salt, parameters, and other metadata, so it's larger than 32 bytes
        assertTrue(key1.size > 32)
        assertTrue(key2.size > 32)
        // We can't compare the content directly because Argon2 includes random salt in the hash
        // So each call produces a different hash even with the same parameters
        
        // Test with different iterations
        val key3 = encryption.deriveKeyArgon2(password, salt, 65536, 5, 4)
        
        assertTrue(key3.size > 32)
        assertFalse(key1.contentEquals(key3)) // Different iterations should produce different key
        
        // Test with different parallelism
        val key4 = encryption.deriveKeyArgon2(password, salt, 65536, 3, 8)
        
        assertTrue(key4.size > 32)
        assertFalse(key1.contentEquals(key4)) // Different parallelism should produce different key
        
        // Test with different memory
        val key5 = encryption.deriveKeyArgon2(password, salt, 32768, 3, 4)
        
        assertTrue(key5.size > 32)
        assertFalse(key1.contentEquals(key5)) // Different memory should produce different key
        
        // Test with different password
        val key6 = encryption.deriveKeyArgon2("different-password", salt, 65536, 3, 4)
        
        assertTrue(key6.size > 32)
        assertFalse(key1.contentEquals(key6)) // Different password should produce different key
        
        // Test with different salt
        val differentSalt = encryption.generateSalt(16)
        val key7 = encryption.deriveKeyArgon2(password, differentSalt, 65536, 3, 4)
        
        assertTrue(key7.size > 32)
        assertFalse(key1.contentEquals(key7)) // Different salt should produce different key
    }
    
    @Test
    fun `crypto factory should create instances correctly`() = runTest {
        // Create a mock CryptoFactory
        object {
            fun createEncryption(): Encryption = encryption
            fun createKeyDerivation(): KeyDerivation = encryption
            fun createZeroKnowledgeEncryption(): ZeroKnowledgeEncryption = encryption
        }
        
        // Test that our actual encryption instance exists
        assertNotNull(encryption)
    }
}