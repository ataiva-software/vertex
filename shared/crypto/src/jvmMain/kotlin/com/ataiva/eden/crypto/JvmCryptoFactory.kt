package com.ataiva.eden.crypto

import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * JVM implementation of the CryptoFactory
 */
// Override the platform-specific implementations for JVM
internal fun CryptoFactory.createPlatformEncryption(): Encryption {
    // Register BouncyCastle provider if not already registered
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
        Security.addProvider(BouncyCastleProvider())
    }
    return BouncyCastleEncryption()
}

internal fun CryptoFactory.createPlatformDigitalSignature(): DigitalSignature {
    // Register BouncyCastle provider if not already registered
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
        Security.addProvider(BouncyCastleProvider())
    }
    return BouncyCastleDigitalSignature()
}

internal fun CryptoFactory.createPlatformSecureRandom(): SecureRandom {
    return BouncyCastleSecureRandom()
}

/**
 * JVM implementation of the DigitalSignature interface using BouncyCastle
 */
class BouncyCastleDigitalSignature : DigitalSignature {
    private val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
    private val cryptoDispatcher = executor.asCoroutineDispatcher()
    
    override suspend fun generateKeyPair(): KeyPair = kotlinx.coroutines.withContext(cryptoDispatcher) {
        val keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA", "BC")
        keyPairGenerator.initialize(2048)
        val javaKeyPair = keyPairGenerator.generateKeyPair()
        
        KeyPair(
            publicKey = javaKeyPair.public.encoded,
            privateKey = javaKeyPair.private.encoded
        )
    }
    
    override suspend fun sign(data: ByteArray, privateKey: ByteArray): ByteArray = kotlinx.coroutines.withContext(cryptoDispatcher) {
        val keyFactory = java.security.KeyFactory.getInstance("RSA", "BC")
        val privateKeySpec = java.security.spec.PKCS8EncodedKeySpec(privateKey)
        val privateKeyObj = keyFactory.generatePrivate(privateKeySpec)
        
        val signature = java.security.Signature.getInstance("SHA256withRSA", "BC")
        signature.initSign(privateKeyObj)
        signature.update(data)
        signature.sign()
    }
    
    override suspend fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean = kotlinx.coroutines.withContext(cryptoDispatcher) {
        val keyFactory = java.security.KeyFactory.getInstance("RSA", "BC")
        val publicKeySpec = java.security.spec.X509EncodedKeySpec(publicKey)
        val publicKeyObj = keyFactory.generatePublic(publicKeySpec)
        
        val sig = java.security.Signature.getInstance("SHA256withRSA", "BC")
        sig.initVerify(publicKeyObj)
        sig.update(data)
        sig.verify(signature)
    }
    
    // Clean up resources
    fun close() {
        cryptoDispatcher.close()
    }
}

/**
 * JVM implementation of the SecureRandom interface using BouncyCastle
 */
class BouncyCastleSecureRandom : SecureRandom {
    private val secureRandom = java.security.SecureRandom()
    private val executor = java.util.concurrent.Executors.newFixedThreadPool(1)
    private val cryptoDispatcher = executor.asCoroutineDispatcher()
    
    override suspend fun nextBytes(length: Int): ByteArray = kotlinx.coroutines.withContext(cryptoDispatcher) {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        bytes
    }
    
    override suspend fun nextString(length: Int, charset: String): String = kotlinx.coroutines.withContext(cryptoDispatcher) {
        val random = secureRandom
        val chars = charset.toCharArray()
        val result = StringBuilder(length)
        
        repeat(length) {
            result.append(chars[random.nextInt(chars.size)])
        }
        
        result.toString()
    }
    
    override suspend fun nextUuid(): String = kotlinx.coroutines.withContext(cryptoDispatcher) {
        java.util.UUID.randomUUID().toString()
    }
    
    // Clean up resources
    fun close() {
        cryptoDispatcher.close()
    }
}