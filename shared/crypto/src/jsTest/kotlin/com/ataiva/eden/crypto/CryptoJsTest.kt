package com.ataiva.eden.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Simple JavaScript test for the crypto module.
 * This test only verifies basic functionality that can run in JS environment.
 */
class CryptoJsTest {
    @Test
    fun testBasicEncryptionResult() {
        val encryptedData = "encrypted".encodeToByteArray()
        val nonce = "nonce".encodeToByteArray()
        
        val result = EncryptionResult(
            encryptedData = encryptedData,
            nonce = nonce
        )
        
        assertEquals(encryptedData, result.encryptedData)
        assertEquals(nonce, result.nonce)
    }
    
    @Test
    fun testDecryptionResultSuccess() {
        val data = "decrypted".encodeToByteArray()
        val result = DecryptionResult.Success(data)
        
        assertEquals(data, result.data)
    }
    
    @Test
    fun testDecryptionResultFailure() {
        val errorMessage = "Failed to decrypt"
        val result = DecryptionResult.Failure(errorMessage)
        
        assertEquals(errorMessage, result.error)
    }
}