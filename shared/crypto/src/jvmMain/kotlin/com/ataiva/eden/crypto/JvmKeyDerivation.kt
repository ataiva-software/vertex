package com.ataiva.eden.crypto

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.SecureRandom as JvmSecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * JVM implementation of key derivation functions
 */
class JvmKeyDerivation : KeyDerivation {
    private val secureRandom = JvmSecureRandom()
    
    override suspend fun deriveKey(
        password: String, 
        salt: ByteArray, 
        iterations: Int, 
        keyLength: Int
    ): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLength * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
    
    override suspend fun deriveKeyArgon2(
        password: String, 
        salt: ByteArray, 
        memory: Int, 
        iterations: Int, 
        parallelism: Int
    ): ByteArray {
        val generator = Argon2BytesGenerator()
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withIterations(iterations)
            .withMemoryAsKB(memory)
            .withParallelism(parallelism)
            .build()
        
        generator.init(params)
        
        val result = ByteArray(32) // 256 bits
        generator.generateBytes(password.toByteArray(), result, 0, result.size)
        return result
    }
    
    override suspend fun generateSalt(length: Int): ByteArray {
        val salt = ByteArray(length)
        secureRandom.nextBytes(salt)
        return salt
    }
    
    override suspend fun deriveKeys(
        masterKey: ByteArray, 
        info: String, 
        count: Int, 
        keyLength: Int
    ): List<ByteArray> {
        // HKDF implementation
        val keys = mutableListOf<ByteArray>()
        val prk = extractHkdf(masterKey, info.toByteArray())
        
        for (i in 0 until count) {
            val infoBytes = info.toByteArray() + byteArrayOf(i.toByte())
            keys.add(expandHkdf(prk, infoBytes, keyLength))
        }
        
        return keys
    }
    
    // HKDF extract function
    private fun extractHkdf(ikm: ByteArray, salt: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(salt, "HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(ikm)
    }
    
    // HKDF expand function
    private fun expandHkdf(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(prk, "HmacSHA256")
        mac.init(keySpec)
        
        val result = ByteArray(length)
        var filledBytes = 0
        var counter = 1
        var previousBlock = ByteArray(0)
        
        while (filledBytes < length) {
            mac.reset()
            mac.update(previousBlock)
            mac.update(info)
            mac.update(counter.toByte())
            
            val stepResult = mac.doFinal()
            previousBlock = stepResult
            
            val bytesToCopy = minOf(stepResult.size, length - filledBytes)
            System.arraycopy(stepResult, 0, result, filledBytes, bytesToCopy)
            filledBytes += bytesToCopy
            counter++
        }
        
        return result
    }
}

// The factory function is now handled by JvmEncryptionProvider