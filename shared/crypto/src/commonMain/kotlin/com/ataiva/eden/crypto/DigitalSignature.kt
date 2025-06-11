package com.ataiva.eden.crypto

/**
 * Digital signature interface for asymmetric cryptography operations
 */
interface DigitalSignature {
    /**
     * Generate a new key pair for digital signatures
     */
    suspend fun generateKeyPair(): KeyPair
    
    /**
     * Sign data with a private key
     */
    suspend fun sign(data: ByteArray, privateKey: ByteArray): ByteArray
    
    /**
     * Verify a signature with a public key
     */
    suspend fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
    
    /**
     * Sign a string with a private key
     */
    suspend fun signString(text: String, privateKey: ByteArray): ByteArray {
        return sign(text.encodeToByteArray(), privateKey)
    }
    
    /**
     * Verify a signature of a string with a public key
     */
    suspend fun verifyString(text: String, signature: ByteArray, publicKey: ByteArray): Boolean {
        return verify(text.encodeToByteArray(), signature, publicKey)
    }
}