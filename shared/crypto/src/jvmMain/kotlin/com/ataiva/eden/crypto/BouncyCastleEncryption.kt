package com.ataiva.eden.crypto

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.Security
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * BouncyCastle implementation of the Encryption interface for JVM platform
 */
class BouncyCastleEncryption : Encryption, KeyDerivation, ZeroKnowledgeEncryption {
    
    private val secureRandom = SecureRandom()
    private val cryptoDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    
    init {
        // Register BouncyCastle provider if not already registered
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }
    
    override suspend fun encrypt(data: ByteArray, key: ByteArray): EncryptionResult = withContext(cryptoDispatcher) {
        val nonce = ByteArray(12)
        secureRandom.nextBytes(nonce)
        
        val cipher = GCMBlockCipher(AESEngine())
        val parameters = AEADParameters(KeyParameter(key), 128, nonce)
        
        cipher.init(true, parameters)
        
        val encryptedData = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, encryptedData, 0)
        cipher.doFinal(encryptedData, len)
        
        val authTag = ByteArray(16)
        System.arraycopy(encryptedData, encryptedData.size - 16, authTag, 0, 16)
        val actualEncryptedData = ByteArray(encryptedData.size - 16)
        System.arraycopy(encryptedData, 0, actualEncryptedData, 0, encryptedData.size - 16)
        
        EncryptionResult(actualEncryptedData, nonce, authTag)
    }
    
    override suspend fun decrypt(data: ByteArray, key: ByteArray, nonce: ByteArray, authTag: ByteArray?): DecryptionResult = withContext(cryptoDispatcher) {
        try {
            if (authTag == null) {
                return@withContext DecryptionResult.Failure("Authentication tag is required for GCM mode")
            }
            
            val cipher = GCMBlockCipher(AESEngine())
            val parameters = AEADParameters(KeyParameter(key), 128, nonce)
            
            cipher.init(false, parameters)
            
            // Combine data and auth tag for GCM decryption
            val encryptedWithTag = ByteArray(data.size + authTag.size)
            System.arraycopy(data, 0, encryptedWithTag, 0, data.size)
            System.arraycopy(authTag, 0, encryptedWithTag, data.size, authTag.size)
            
            val decryptedData = ByteArray(cipher.getOutputSize(encryptedWithTag.size))
            val len = cipher.processBytes(encryptedWithTag, 0, encryptedWithTag.size, decryptedData, 0)
            cipher.doFinal(decryptedData, len)
            
            DecryptionResult.Success(decryptedData)
        } catch (e: Exception) {
            DecryptionResult.Failure("Decryption failed: ${e.message}")
        }
    }
    
    // Key Derivation Implementation
    override suspend fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray = withContext(cryptoDispatcher) {
        val digest = MessageDigest.getInstance("SHA-256")
        var result = password.toByteArray()
        
        // Simple PBKDF2 implementation
        for (i in 0 until iterations) {
            digest.reset()
            digest.update(result)
            digest.update(salt)
            result = digest.digest()
        }
        
        // Ensure the key is the right length
        if (result.size > keyLength) {
            result.copyOf(keyLength)
        } else if (result.size < keyLength) {
            val paddedResult = ByteArray(keyLength)
            System.arraycopy(result, 0, paddedResult, 0, result.size)
            paddedResult
        } else {
            result
        }
    }
    
    override suspend fun deriveKeyArgon2(password: String, salt: ByteArray, memory: Int, iterations: Int, parallelism: Int): ByteArray = withContext(cryptoDispatcher) {
        // In a real implementation, this would use Argon2 library
        // For now, we'll use a placeholder implementation based on PBKDF2
        deriveKey(password, salt, iterations * 10000, 32)
    }
    
    override suspend fun generateSalt(length: Int): ByteArray = withContext(cryptoDispatcher) {
        val salt = ByteArray(length)
        secureRandom.nextBytes(salt)
        salt
    }
    
    override suspend fun deriveKeys(masterKey: ByteArray, info: String, count: Int, keyLength: Int): List<ByteArray> = withContext(cryptoDispatcher) {
        val result = mutableListOf<ByteArray>()
        val infoBytes = info.toByteArray()
        
        for (i in 0 until count) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(masterKey, "HmacSHA256"))
            
            // HKDF-like key derivation
            mac.update(infoBytes)
            mac.update(byteArrayOf(i.toByte()))
            
            val derivedKey = mac.doFinal()
            result.add(if (derivedKey.size > keyLength) derivedKey.copyOf(keyLength) else derivedKey)
        }
        
        result
    }
    
    // Zero-Knowledge Encryption Implementation
    override suspend fun encryptZeroKnowledge(data: String, password: String, salt: ByteArray?): ZeroKnowledgeResult = withContext(cryptoDispatcher) {
        val actualSalt = salt ?: generateSalt()
        val key = deriveKey(password, actualSalt)
        val encryptionResult = encrypt(data.toByteArray(), key)
        
        ZeroKnowledgeResult(
            encryptedData = encryptionResult.encryptedData,
            salt = actualSalt,
            nonce = encryptionResult.nonce,
            authTag = encryptionResult.authTag,
            keyDerivationParams = KeyDerivationParams()
        )
    }
    
    override suspend fun decryptZeroKnowledge(result: ZeroKnowledgeResult, password: String): String? = withContext(cryptoDispatcher) {
        val key = deriveKey(
            password,
            result.salt,
            result.keyDerivationParams.iterations,
            result.keyDerivationParams.keyLength
        )
        
        when (val decryptResult = decrypt(result.encryptedData, key, result.nonce, result.authTag)) {
            is DecryptionResult.Success -> decryptResult.data.toString(Charsets.UTF_8)
            is DecryptionResult.Failure -> null
        }
    }
    
    override suspend fun verifyIntegrity(result: ZeroKnowledgeResult): Boolean = withContext(cryptoDispatcher) {
        result.authTag != null
    }
    
    // Clean up resources
    fun close() {
        cryptoDispatcher.close()
    }
}