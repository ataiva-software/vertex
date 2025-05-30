package com.ataiva.eden.crypto

/**
 * Core encryption interface for Eden services
 */
interface Encryption {
    /**
     * Encrypt data with AES-256-GCM
     */
    fun encrypt(data: ByteArray, key: ByteArray): EncryptionResult
    
    /**
     * Decrypt data with AES-256-GCM
     */
    fun decrypt(encryptedData: ByteArray, key: ByteArray, nonce: ByteArray): DecryptionResult
    
    /**
     * Encrypt string data
     */
    fun encryptString(data: String, key: ByteArray): EncryptionResult
    
    /**
     * Decrypt to string
     */
    fun decryptString(encryptedData: ByteArray, key: ByteArray, nonce: ByteArray): String?
}

/**
 * Encryption result containing encrypted data and metadata
 */
data class EncryptionResult(
    val encryptedData: ByteArray,
    val nonce: ByteArray,
    val authTag: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as EncryptionResult
        
        if (!encryptedData.contentEquals(other.encryptedData)) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (authTag != null) {
            if (other.authTag == null) return false
            if (!authTag.contentEquals(other.authTag)) return false
        } else if (other.authTag != null) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = encryptedData.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + (authTag?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Decryption result
 */
sealed class DecryptionResult {
    data class Success(val data: ByteArray) : DecryptionResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            
            other as Success
            
            return data.contentEquals(other.data)
        }
        
        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }
    
    data class Failure(val error: String) : DecryptionResult()
}

/**
 * Key derivation interface
 */
interface KeyDerivation {
    /**
     * Derive key from password using PBKDF2
     */
    fun deriveKey(password: String, salt: ByteArray, iterations: Int = 100000, keyLength: Int = 32): ByteArray
    
    /**
     * Derive key from password using Argon2
     */
    fun deriveKeyArgon2(password: String, salt: ByteArray, memory: Int = 65536, iterations: Int = 3, parallelism: Int = 4): ByteArray
    
    /**
     * Generate random salt
     */
    fun generateSalt(length: Int = 32): ByteArray
    
    /**
     * Derive multiple keys using HKDF
     */
    fun deriveKeys(masterKey: ByteArray, info: String, keyCount: Int, keyLength: Int = 32): List<ByteArray>
}

/**
 * Zero-knowledge encryption for secrets
 */
interface ZeroKnowledgeEncryption {
    /**
     * Encrypt data with zero-knowledge approach
     * Client-side key derivation and encryption
     */
    fun encryptZeroKnowledge(
        data: String,
        userPassword: String,
        salt: ByteArray? = null
    ): ZeroKnowledgeResult
    
    /**
     * Decrypt zero-knowledge encrypted data
     */
    fun decryptZeroKnowledge(
        encryptedData: ZeroKnowledgeResult,
        userPassword: String
    ): String?
    
    /**
     * Verify zero-knowledge encrypted data integrity
     */
    fun verifyIntegrity(encryptedData: ZeroKnowledgeResult): Boolean
}

/**
 * Zero-knowledge encryption result
 */
data class ZeroKnowledgeResult(
    val encryptedData: ByteArray,
    val salt: ByteArray,
    val nonce: ByteArray,
    val authTag: ByteArray,
    val keyDerivationParams: KeyDerivationParams
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as ZeroKnowledgeResult
        
        if (!encryptedData.contentEquals(other.encryptedData)) return false
        if (!salt.contentEquals(other.salt)) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (!authTag.contentEquals(other.authTag)) return false
        if (keyDerivationParams != other.keyDerivationParams) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = encryptedData.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        result = 31 * result + keyDerivationParams.hashCode()
        return result
    }
}

/**
 * Key derivation parameters
 */
data class KeyDerivationParams(
    val algorithm: String = "PBKDF2",
    val iterations: Int = 100000,
    val keyLength: Int = 32,
    val hashFunction: String = "SHA256"
)

/**
 * Digital signature interface
 */
interface DigitalSignature {
    /**
     * Generate key pair for signing
     */
    fun generateKeyPair(): KeyPair
    
    /**
     * Sign data with private key
     */
    fun sign(data: ByteArray, privateKey: ByteArray): ByteArray
    
    /**
     * Verify signature with public key
     */
    fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
    
    /**
     * Sign string data
     */
    fun signString(data: String, privateKey: ByteArray): ByteArray
    
    /**
     * Verify string signature
     */
    fun verifyString(data: String, signature: ByteArray, publicKey: ByteArray): Boolean
}

/**
 * Key pair for digital signatures
 */
data class KeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as KeyPair
        
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (!privateKey.contentEquals(other.privateKey)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}

/**
 * Secure random number generator
 */
interface SecureRandom {
    /**
     * Generate random bytes
     */
    fun nextBytes(size: Int): ByteArray
    
    /**
     * Generate random string
     */
    fun nextString(length: Int, charset: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"): String
    
    /**
     * Generate UUID
     */
    fun nextUuid(): String
}