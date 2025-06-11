package com.ataiva.eden.crypto

/**
 * Secure random number generator interface
 * Provides methods for generating cryptographically secure random values
 */
interface SecureRandom {
    /**
     * Generate random bytes of specified length
     */
    suspend fun nextBytes(length: Int): ByteArray
    
    /**
     * Generate a random string of specified length
     * @param length The length of the string to generate
     * @param charset The character set to use for the string (default: alphanumeric)
     */
    suspend fun nextString(length: Int, charset: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"): String
    
    /**
     * Generate a random UUID
     */
    suspend fun nextUuid(): String
}