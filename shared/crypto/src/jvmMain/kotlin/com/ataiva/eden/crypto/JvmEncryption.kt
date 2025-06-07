package com.ataiva.eden.crypto

import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom as JvmSecureRandom

/**
 * JVM-specific implementation of encryption functions
 */
class JvmEncryption(
    private val keyDerivation: KeyDerivation
) : DefaultEncryption(keyDerivation) {
    
    companion object {
        private const val AES_GCM_TAG_LENGTH = 128 // bits
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }
    
    /**
     * JVM implementation of AES-GCM encryption
     */
    override fun encryptInternal(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, KEY_ALGORITHM)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(AES_GCM_TAG_LENGTH, nonce)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        
        return cipher.doFinal(data)
    }
    
    /**
     * JVM implementation of AES-GCM decryption
     */
    override fun decryptInternal(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, KEY_ALGORITHM)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(AES_GCM_TAG_LENGTH, nonce)
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        
        return cipher.doFinal(data)
    }
    
    /**
     * JVM implementation of HMAC-SHA256
     */
    override fun computeHmac(data: ByteArray, key: ByteArray): ByteArray {
        val secretKey: SecretKey = SecretKeySpec(key, HMAC_ALGORITHM)
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(secretKey)
        return mac.doFinal(data)
    }
    
    /**
     * JVM implementation of secure random number generation
     */
    override fun generateSecureRandomBytes(length: Int): ByteArray {
        val random = JvmSecureRandom()
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return bytes
    }
}

/**
 * Factory function to create a JVM-specific encryption implementation
 */
/**
 * JVM implementation of the PlatformEncryptionProvider
 */
class JvmEncryptionProvider : PlatformEncryptionProvider {
    override fun createEncryption(keyDerivation: KeyDerivation): Encryption {
        return JvmEncryption(keyDerivation)
    }
    
    override fun createKeyDerivation(): KeyDerivation {
        return JvmKeyDerivation()
    }
}

/**
 * Register the JVM implementation of the PlatformEncryptionProvider
 */
fun registerJvmEncryptionProvider() {
    PlatformEncryptionProvider.register(JvmEncryptionProvider())
}

// Register the JVM provider when the class is loaded
private val registerProvider = run {
    registerJvmEncryptionProvider()
}