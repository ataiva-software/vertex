package com.ataiva.eden.crypto

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import java.util.*

/**
 * BouncyCastle-based encryption implementation for JVM
 */
class BouncyCastleEncryption : Encryption, KeyDerivation, ZeroKnowledgeEncryption, DigitalSignature, SecureRandom {
    
    private val secureRandom = SecureRandom()
    
    override fun encrypt(data: ByteArray, key: ByteArray): EncryptionResult {
        require(key.size == 32) { "Key must be 32 bytes for AES-256" }
        
        val nonce = ByteArray(12) // 96-bit nonce for GCM
        secureRandom.nextBytes(nonce)
        
        val cipher = GCMBlockCipher(AESEngine())
        val keyParam = KeyParameter(key)
        val aeadParams = AEADParameters(keyParam, 128, nonce) // 128-bit auth tag
        
        cipher.init(true, aeadParams)
        
        val outputSize = cipher.getOutputSize(data.size)
        val output = ByteArray(outputSize)
        
        var len = cipher.processBytes(data, 0, data.size, output, 0)
        len += cipher.doFinal(output, len)
        
        // Split encrypted data and auth tag
        val encryptedData = output.copyOfRange(0, len - 16)
        val authTag = output.copyOfRange(len - 16, len)
        
        return EncryptionResult(encryptedData, nonce, authTag)
    }
    
    override fun decrypt(encryptedData: ByteArray, key: ByteArray, nonce: ByteArray): DecryptionResult {
        return try {
            require(key.size == 32) { "Key must be 32 bytes for AES-256" }
            require(nonce.size == 12) { "Nonce must be 12 bytes for GCM" }
            
            val cipher = GCMBlockCipher(AESEngine())
            val keyParam = KeyParameter(key)
            val aeadParams = AEADParameters(keyParam, 128, nonce)
            
            cipher.init(false, aeadParams)
            
            val outputSize = cipher.getOutputSize(encryptedData.size)
            val output = ByteArray(outputSize)
            
            var len = cipher.processBytes(encryptedData, 0, encryptedData.size, output, 0)
            len += cipher.doFinal(output, len)
            
            DecryptionResult.Success(output.copyOfRange(0, len))
        } catch (e: Exception) {
            DecryptionResult.Failure("Decryption failed: ${e.message}")
        }
    }
    
    override fun encryptString(data: String, key: ByteArray): EncryptionResult {
        return encrypt(data.toByteArray(Charsets.UTF_8), key)
    }
    
    override fun decryptString(encryptedData: ByteArray, key: ByteArray, nonce: ByteArray): String? {
        return when (val result = decrypt(encryptedData, key, nonce)) {
            is DecryptionResult.Success -> String(result.data, Charsets.UTF_8)
            is DecryptionResult.Failure -> null
        }
    }
    
    // Key Derivation Implementation
    override fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        val generator = PKCS5S2ParametersGenerator(SHA256Digest())
        generator.init(password.toByteArray(Charsets.UTF_8), salt, iterations)
        val key = generator.generateDerivedParameters(keyLength * 8)
        return (key as KeyParameter).key
    }
    
    override fun deriveKeyArgon2(password: String, salt: ByteArray, memory: Int, iterations: Int, parallelism: Int): ByteArray {
        // For now, fallback to PBKDF2 as BouncyCastle Argon2 requires additional setup
        return deriveKey(password, salt, iterations * 1000, 32)
    }
    
    override fun generateSalt(length: Int): ByteArray {
        val salt = ByteArray(length)
        secureRandom.nextBytes(salt)
        return salt
    }
    
    override fun deriveKeys(masterKey: ByteArray, info: String, keyCount: Int, keyLength: Int): List<ByteArray> {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        val params = HKDFParameters(masterKey, null, info.toByteArray(Charsets.UTF_8))
        hkdf.init(params)
        
        val keys = mutableListOf<ByteArray>()
        repeat(keyCount) {
            val key = ByteArray(keyLength)
            hkdf.generateBytes(key, 0, keyLength)
            keys.add(key)
        }
        return keys
    }
    
    // Zero-Knowledge Encryption Implementation
    override fun encryptZeroKnowledge(data: String, userPassword: String, salt: ByteArray?): ZeroKnowledgeResult {
        val actualSalt = salt ?: generateSalt(32)
        val derivedKey = deriveKey(userPassword, actualSalt, 100000, 32)
        val encryptionResult = encryptString(data, derivedKey)
        
        return ZeroKnowledgeResult(
            encryptedData = encryptionResult.encryptedData,
            salt = actualSalt,
            nonce = encryptionResult.nonce,
            authTag = encryptionResult.authTag ?: ByteArray(0),
            keyDerivationParams = KeyDerivationParams()
        )
    }
    
    override fun decryptZeroKnowledge(encryptedData: ZeroKnowledgeResult, userPassword: String): String? {
        val derivedKey = deriveKey(
            userPassword, 
            encryptedData.salt, 
            encryptedData.keyDerivationParams.iterations,
            encryptedData.keyDerivationParams.keyLength
        )
        
        // Combine encrypted data and auth tag for GCM decryption
        val combinedData = encryptedData.encryptedData + encryptedData.authTag
        return decryptString(combinedData, derivedKey, encryptedData.nonce)
    }
    
    override fun verifyIntegrity(encryptedData: ZeroKnowledgeResult): Boolean {
        return encryptedData.encryptedData.isNotEmpty() && 
               encryptedData.salt.isNotEmpty() && 
               encryptedData.nonce.isNotEmpty() &&
               encryptedData.authTag.isNotEmpty()
    }
    
    // Digital Signature Implementation
    override fun generateKeyPair(): KeyPair {
        val keyPairGenerator = Ed25519KeyPairGenerator()
        keyPairGenerator.init(Ed25519KeyGenerationParameters(secureRandom))
        val keyPair = keyPairGenerator.generateKeyPair()
        
        val privateKey = (keyPair.private as Ed25519PrivateKeyParameters).encoded
        val publicKey = (keyPair.public as Ed25519PublicKeyParameters).encoded
        
        return KeyPair(publicKey, privateKey)
    }
    
    override fun sign(data: ByteArray, privateKey: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        val privateKeyParams = Ed25519PrivateKeyParameters(privateKey, 0)
        signer.init(true, privateKeyParams)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }
    
    override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return try {
            val verifier = Ed25519Signer()
            val publicKeyParams = Ed25519PublicKeyParameters(publicKey, 0)
            verifier.init(false, publicKeyParams)
            verifier.update(data, 0, data.size)
            verifier.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }
    
    override fun signString(data: String, privateKey: ByteArray): ByteArray {
        return sign(data.toByteArray(Charsets.UTF_8), privateKey)
    }
    
    override fun verifyString(data: String, signature: ByteArray, publicKey: ByteArray): Boolean {
        return verify(data.toByteArray(Charsets.UTF_8), signature, publicKey)
    }
    
    // Secure Random Implementation
    override fun nextBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        return bytes
    }
    
    override fun nextString(length: Int, charset: String): String {
        val chars = charset.toCharArray()
        return (1..length)
            .map { chars[secureRandom.nextInt(chars.size)] }
            .joinToString("")
    }
    
    override fun nextUuid(): String {
        return UUID.randomUUID().toString()
    }
}

/**
 * Factory for creating encryption instances
 */
object CryptoFactory {
    fun createEncryption(): Encryption = BouncyCastleEncryption()
    fun createKeyDerivation(): KeyDerivation = BouncyCastleEncryption()
    fun createZeroKnowledgeEncryption(): ZeroKnowledgeEncryption = BouncyCastleEncryption()
    fun createDigitalSignature(): DigitalSignature = BouncyCastleEncryption()
    fun createSecureRandom(): SecureRandom = BouncyCastleEncryption()
}