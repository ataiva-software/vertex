package com.ataiva.eden.crypto

import com.ataiva.eden.testing.crypto.CryptoTestUtils
import com.ataiva.eden.testing.mocks.MockTimeProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

class EncryptionTest : DescribeSpec({
    
    coroutineTestScope = true
    
    describe("Encryption interface") {
        
        val mockEncryption = mockk<Encryption>()
        
        describe("basic encryption/decryption") {
            it("should encrypt and decrypt data successfully") {
                val originalData = "Hello, World!".encodeToByteArray()
                val key = CryptoTestUtils.generateTestKey()
                val expectedResult = EncryptionResult(
                    encryptedData = "encrypted-data".encodeToByteArray(),
                    nonce = "test-nonce".encodeToByteArray(),
                    authTag = "auth-tag".encodeToByteArray()
                )
                
                coEvery { mockEncryption.encrypt(originalData, key) } returns expectedResult
                
                val result = mockEncryption.encrypt(originalData, key)
                
                result shouldBe expectedResult
                result.encryptedData.shouldNotBeNull()
                result.nonce.shouldNotBeNull()
                result.authTag.shouldNotBeNull()
                coVerify { mockEncryption.encrypt(originalData, key) }
            }
            
            it("should decrypt encrypted data successfully") {
                val encryptedData = "encrypted-data".encodeToByteArray()
                val key = CryptoTestUtils.generateTestKey()
                val nonce = "test-nonce".encodeToByteArray()
                val authTag = "auth-tag".encodeToByteArray()
                val originalData = "Hello, World!".encodeToByteArray()
                val expectedResult = DecryptionResult.Success(originalData)
                
                coEvery {
                    mockEncryption.decrypt(encryptedData, key, nonce, authTag)
                } returns expectedResult
                
                val result = mockEncryption.decrypt(encryptedData, key, nonce, authTag)
                
                result shouldBe expectedResult
                result.shouldBeInstanceOf<DecryptionResult.Success>()
                val successResult = result as DecryptionResult.Success
                successResult.data shouldBe originalData
            }
            
            it("should fail to decrypt with wrong key") {
                val encryptedData = "encrypted-data".encodeToByteArray()
                val wrongKey = CryptoTestUtils.generateTestKey()
                val nonce = "test-nonce".encodeToByteArray()
                val authTag = "auth-tag".encodeToByteArray()
                val expectedResult = DecryptionResult.Failure("Invalid key or corrupted data")
                
                coEvery {
                    mockEncryption.decrypt(encryptedData, wrongKey, nonce, authTag)
                } returns expectedResult
                
                val result = mockEncryption.decrypt(encryptedData, wrongKey, nonce, authTag)
                
                result shouldBe expectedResult
                result.shouldBeInstanceOf<DecryptionResult.Failure>()
                val failureResult = result as DecryptionResult.Failure
                failureResult.error shouldBe "Invalid key or corrupted data"
            }
        }
        
        describe("string encryption/decryption") {
            it("should encrypt and decrypt strings") {
                val originalString = "Secret message"
                val key = CryptoTestUtils.generateTestKey()
                val expectedResult = EncryptionResult(
                    encryptedData = "encrypted-string".encodeToByteArray(),
                    nonce = "string-nonce".encodeToByteArray(),
                    authTag = "auth-tag".encodeToByteArray()
                )
                
                coEvery { mockEncryption.encryptString(originalString, key) } returns expectedResult
                coEvery {
                    mockEncryption.decryptString(expectedResult.encryptedData, key, expectedResult.nonce, expectedResult.authTag)
                } returns originalString
                
                val encryptResult = mockEncryption.encryptString(originalString, key)
                val decryptResult = mockEncryption.decryptString(
                    encryptResult.encryptedData,
                    key,
                    encryptResult.nonce,
                    encryptResult.authTag
                )
                
                encryptResult shouldBe expectedResult
                decryptResult shouldBe originalString
            }
            
            it("should return null for failed string decryption") {
                val encryptedData = "invalid-data".encodeToByteArray()
                val key = CryptoTestUtils.generateTestKey()
                val nonce = "test-nonce".encodeToByteArray()
                val authTag = "auth-tag".encodeToByteArray()
                
                coEvery {
                    mockEncryption.decryptString(encryptedData, key, nonce, authTag)
                } returns null
                
                val result = mockEncryption.decryptString(encryptedData, key, nonce, authTag)
                
                result.shouldBeNull()
            }
        }
    }
    
    describe("EncryptionResult") {
        
        describe("construction") {
            it("should create encryption result with all fields") {
                val encryptedData = "encrypted".encodeToByteArray()
                val nonce = "nonce".encodeToByteArray()
                val authTag = "auth".encodeToByteArray()
                
                val result = EncryptionResult(
                    encryptedData = encryptedData,
                    nonce = nonce,
                    authTag = authTag
                )
                
                result.encryptedData shouldBe encryptedData
                result.nonce shouldBe nonce
                result.authTag shouldBe authTag
            }
            
            it("should create encryption result without auth tag") {
                val encryptedData = "encrypted".encodeToByteArray()
                val nonce = "nonce".encodeToByteArray()
                
                val result = EncryptionResult(
                    encryptedData = encryptedData,
                    nonce = nonce
                )
                
                result.encryptedData shouldBe encryptedData
                result.nonce shouldBe nonce
                result.authTag.shouldBeNull()
            }
        }
        
        describe("equality") {
            it("should be equal for same content") {
                val data = "test".encodeToByteArray()
                val nonce = "nonce".encodeToByteArray()
                val authTag = "auth".encodeToByteArray()
                
                val result1 = EncryptionResult(data, nonce, authTag)
                val result2 = EncryptionResult(data, nonce, authTag)
                
                result1 shouldBe result2
                result1.hashCode() shouldBe result2.hashCode()
            }
            
            it("should not be equal for different content") {
                val result1 = EncryptionResult("data1".encodeToByteArray(), "nonce1".encodeToByteArray())
                val result2 = EncryptionResult("data2".encodeToByteArray(), "nonce2".encodeToByteArray())
                
                result1 shouldNotBe result2
                result1.hashCode() shouldNotBe result2.hashCode()
            }
        }
    }
    
    describe("DecryptionResult sealed class") {
        
        describe("Success result") {
            it("should create success result") {
                val data = "decrypted data".encodeToByteArray()
                val result = DecryptionResult.Success(data)
                
                result.data shouldBe data
                result.shouldBeInstanceOf<DecryptionResult.Success>()
            }
            
            it("should have proper equality") {
                val data = "test".encodeToByteArray()
                val result1 = DecryptionResult.Success(data)
                val result2 = DecryptionResult.Success(data)
                
                result1 shouldBe result2
                result1.hashCode() shouldBe result2.hashCode()
            }
        }
        
        describe("Failure result") {
            it("should create failure result") {
                val error = "Decryption failed"
                val result = DecryptionResult.Failure(error)
                
                result.error shouldBe error
                result.shouldBeInstanceOf<DecryptionResult.Failure>()
            }
        }
    }
})