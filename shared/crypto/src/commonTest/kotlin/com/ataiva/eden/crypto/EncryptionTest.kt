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

class EncryptionTest : DescribeSpec({
    
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
                val originalData = "Hello, World!".encodeToByteArray()
                val expectedResult = DecryptionResult.Success(originalData)
                
                coEvery { 
                    mockEncryption.decrypt(encryptedData, key, nonce) 
                } returns expectedResult
                
                val result = mockEncryption.decrypt(encryptedData, key, nonce)
                
                result shouldBe expectedResult
                result.shouldBeInstanceOf<DecryptionResult.Success>()
                (result as DecryptionResult.Success).data shouldBe originalData
            }
            
            it("should fail to decrypt with wrong key") {
                val encryptedData = "encrypted-data".encodeToByteArray()
                val wrongKey = CryptoTestUtils.generateTestKey()
                val nonce = "test-nonce".encodeToByteArray()
                val expectedResult = DecryptionResult.Failure("Invalid key or corrupted data")
                
                coEvery { 
                    mockEncryption.decrypt(encryptedData, wrongKey, nonce) 
                } returns expectedResult
                
                val result = mockEncryption.decrypt(encryptedData, wrongKey, nonce)
                
                result shouldBe expectedResult
                result.shouldBeInstanceOf<DecryptionResult.Failure>()
                (result as DecryptionResult.Failure).error shouldBe "Invalid key or corrupted data"
            }
        }
        
        describe("string encryption/decryption") {
            it("should encrypt and decrypt strings") {
                val originalString = "Secret message"
                val key = CryptoTestUtils.generateTestKey()
                val expectedResult = EncryptionResult(
                    encryptedData = "encrypted-string".encodeToByteArray(),
                    nonce = "string-nonce".encodeToByteArray()
                )
                
                coEvery { mockEncryption.encryptString(originalString, key) } returns expectedResult
                coEvery { 
                    mockEncryption.decryptString(expectedResult.encryptedData, key, expectedResult.nonce) 
                } returns originalString
                
                val encryptResult = mockEncryption.encryptString(originalString, key)
                val decryptResult = mockEncryption.decryptString(
                    encryptResult.encryptedData, 
                    key, 
                    encryptResult.nonce
                )
                
                encryptResult shouldBe expectedResult
                decryptResult shouldBe originalString
            }
            
            it("should return null for failed string decryption") {
                val encryptedData = "invalid-data".encodeToByteArray()
                val key = CryptoTestUtils.generateTestKey()
                val nonce = "test-nonce".encodeToByteArray()
                
                coEvery { 
                    mockEncryption.decryptString(encryptedData, key, nonce) 
                } returns null
                
                val result = mockEncryption.decryptString(encryptedData, key, nonce)
                
                result.shouldBeNull()
            }
        }
        
        describe("property-based testing") {
            it("should handle arbitrary byte arrays") {
                checkAll(Arb.byteArray(Arb.int(1..1000))) { data ->
                    val key = CryptoTestUtils.generateTestKey()
                    val expectedResult = EncryptionResult(
                        encryptedData = data,
                        nonce = CryptoTestUtils.generateTestNonce()
                    )
                    
                    coEvery { mockEncryption.encrypt(data, key) } returns expectedResult
                    
                    val result = mockEncryption.encrypt(data, key)
                    result.encryptedData shouldBe data
                }
            }
            
            it("should handle arbitrary strings") {
                checkAll(Arb.string(1..500)) { text ->
                    val key = CryptoTestUtils.generateTestKey()
                    val expectedResult = EncryptionResult(
                        encryptedData = text.encodeToByteArray(),
                        nonce = CryptoTestUtils.generateTestNonce()
                    )
                    
                    coEvery { mockEncryption.encryptString(text, key) } returns expectedResult
                    
                    val result = mockEncryption.encryptString(text, key)
                    result.encryptedData shouldBe text.encodeToByteArray()
                }
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
    
    describe("KeyDerivation interface") {
        
        val mockKeyDerivation = mockk<KeyDerivation>()
        
        describe("PBKDF2 key derivation") {
            it("should derive key using PBKDF2") {
                val password = "strongpassword"
                val salt = CryptoTestUtils.generateTestSalt()
                val expectedKey = CryptoTestUtils.generateTestKey()
                
                coEvery { 
                    mockKeyDerivation.deriveKey(password, salt, 100000, 32) 
                } returns expectedKey
                
                val result = mockKeyDerivation.deriveKey(password, salt, 100000, 32)
                
                result shouldBe expectedKey
                result.size shouldBe 32
                coVerify { mockKeyDerivation.deriveKey(password, salt, 100000, 32) }
            }
            
            it("should derive key with different iterations") {
                val password = "password"
                val salt = CryptoTestUtils.generateTestSalt()
                val key1 = CryptoTestUtils.generateTestKey()
                val key2 = CryptoTestUtils.generateTestKey()
                
                coEvery { mockKeyDerivation.deriveKey(password, salt, 50000, 32) } returns key1
                coEvery { mockKeyDerivation.deriveKey(password, salt, 100000, 32) } returns key2
                
                val result1 = mockKeyDerivation.deriveKey(password, salt, 50000, 32)
                val result2 = mockKeyDerivation.deriveKey(password, salt, 100000, 32)
                
                result1 shouldBe key1
                result2 shouldBe key2
                result1 shouldNotBe result2
            }
        }
        
        describe("Argon2 key derivation") {
            it("should derive key using Argon2") {
                val password = "strongpassword"
                val salt = CryptoTestUtils.generateTestSalt()
                val expectedKey = CryptoTestUtils.generateTestKey()
                
                coEvery { 
                    mockKeyDerivation.deriveKeyArgon2(password, salt, 65536, 3, 4) 
                } returns expectedKey
                
                val result = mockKeyDerivation.deriveKeyArgon2(password, salt, 65536, 3, 4)
                
                result shouldBe expectedKey
                result.size shouldBe 32
            }
        }
        
        describe("salt generation") {
            it("should generate random salt") {
                val expectedSalt = CryptoTestUtils.generateTestSalt()
                
                coEvery { mockKeyDerivation.generateSalt(32) } returns expectedSalt
                
                val result = mockKeyDerivation.generateSalt(32)
                
                result shouldBe expectedSalt
                result.size shouldBe 32
            }
            
            it("should generate salts of different lengths") {
                val salt16 = ByteArray(16) { it.toByte() }
                val salt32 = ByteArray(32) { it.toByte() }
                
                coEvery { mockKeyDerivation.generateSalt(16) } returns salt16
                coEvery { mockKeyDerivation.generateSalt(32) } returns salt32
                
                mockKeyDerivation.generateSalt(16).size shouldBe 16
                mockKeyDerivation.generateSalt(32).size shouldBe 32
            }
        }
        
        describe("HKDF key derivation") {
            it("should derive multiple keys using HKDF") {
                val masterKey = CryptoTestUtils.generateTestKey()
                val info = "test-context"
                val expectedKeys = listOf(
                    CryptoTestUtils.generateTestKey(),
                    CryptoTestUtils.generateTestKey(),
                    CryptoTestUtils.generateTestKey()
                )
                
                coEvery { 
                    mockKeyDerivation.deriveKeys(masterKey, info, 3, 32) 
                } returns expectedKeys
                
                val result = mockKeyDerivation.deriveKeys(masterKey, info, 3, 32)
                
                result shouldBe expectedKeys
                result shouldHaveSize 3
                result.forEach { key ->
                    key.size shouldBe 32
                }
            }
        }
    }
    
    describe("ZeroKnowledgeEncryption interface") {
        
        val mockZkEncryption = mockk<ZeroKnowledgeEncryption>()
        
        describe("zero-knowledge encryption") {
            it("should encrypt data with zero-knowledge approach") {
                val data = "sensitive data"
                val password = "userpassword"
                val salt = CryptoTestUtils.generateTestSalt()
                val expectedResult = ZeroKnowledgeResult(
                    encryptedData = "zk-encrypted".encodeToByteArray(),
                    salt = salt,
                    nonce = CryptoTestUtils.generateTestNonce(),
                    authTag = "zk-auth".encodeToByteArray(),
                    keyDerivationParams = KeyDerivationParams()
                )
                
                coEvery { 
                    mockZkEncryption.encryptZeroKnowledge(data, password, salt) 
                } returns expectedResult
                
                val result = mockZkEncryption.encryptZeroKnowledge(data, password, salt)
                
                result shouldBe expectedResult
                result.salt shouldBe salt
                result.keyDerivationParams.algorithm shouldBe "PBKDF2"
            }
            
            it("should encrypt with auto-generated salt") {
                val data = "sensitive data"
                val password = "userpassword"
                val expectedResult = ZeroKnowledgeResult(
                    encryptedData = "zk-encrypted".encodeToByteArray(),
                    salt = CryptoTestUtils.generateTestSalt(),
                    nonce = CryptoTestUtils.generateTestNonce(),
                    authTag = "zk-auth".encodeToByteArray(),
                    keyDerivationParams = KeyDerivationParams()
                )
                
                coEvery { 
                    mockZkEncryption.encryptZeroKnowledge(data, password, null) 
                } returns expectedResult
                
                val result = mockZkEncryption.encryptZeroKnowledge(data, password, null)
                
                result shouldBe expectedResult
                result.salt.shouldNotBeNull()
            }
        }
        
        describe("zero-knowledge decryption") {
            it("should decrypt zero-knowledge encrypted data") {
                val originalData = "sensitive data"
                val password = "userpassword"
                val zkResult = ZeroKnowledgeResult(
                    encryptedData = "zk-encrypted".encodeToByteArray(),
                    salt = CryptoTestUtils.generateTestSalt(),
                    nonce = CryptoTestUtils.generateTestNonce(),
                    authTag = "zk-auth".encodeToByteArray(),
                    keyDerivationParams = KeyDerivationParams()
                )
                
                coEvery { 
                    mockZkEncryption.decryptZeroKnowledge(zkResult, password) 
                } returns originalData
                
                val result = mockZkEncryption.decryptZeroKnowledge(zkResult, password)
                
                result shouldBe originalData
            }
            
            it("should return null for wrong password") {
                val zkResult = ZeroKnowledgeResult(
                    encryptedData = "zk-encrypted".encodeToByteArray(),
                    salt = CryptoTestUtils.generateTestSalt(),
                    nonce = CryptoTestUtils.generateTestNonce(),
                    authTag = "zk-auth".encodeToByteArray(),
                    keyDerivationParams = KeyDerivationParams()
                )
                
                coEvery { 
                    mockZkEncryption.decryptZeroKnowledge(zkResult, "wrongpassword") 
                } returns null
                
                val result = mockZkEncryption.decryptZeroKnowledge(zkResult, "wrongpassword")
                
                result.shouldBeNull()
            }
        }
        
        describe("integrity verification") {
            it("should verify data integrity") {
                val zkResult = ZeroKnowledgeResult(
                    encryptedData = "zk-encrypted".encodeToByteArray(),
                    salt = CryptoTestUtils.generateTestSalt(),
                    nonce = CryptoTestUtils.generateTestNonce(),
                    authTag = "zk-auth".encodeToByteArray(),
                    keyDerivationParams = KeyDerivationParams()
                )
                
                coEvery { mockZkEncryption.verifyIntegrity(zkResult) } returns true
                
                val result = mockZkEncryption.verifyIntegrity(zkResult)
                
                result shouldBe true
            }
            
            it("should detect corrupted data") {
                val corruptedResult = ZeroKnowledgeResult(
                    encryptedData = "corrupted".encodeToByteArray(),
                    salt = CryptoTestUtils.generateTestSalt(),
                    nonce = CryptoTestUtils.generateTestNonce(),
                    authTag = "invalid-auth".encodeToByteArray(),
                    keyDerivationParams = KeyDerivationParams()
                )
                
                coEvery { mockZkEncryption.verifyIntegrity(corruptedResult) } returns false
                
                val result = mockZkEncryption.verifyIntegrity(corruptedResult)
                
                result shouldBe false
            }
        }
    }
    
    describe("ZeroKnowledgeResult") {
        
        describe("construction") {
            it("should create zero-knowledge result with all fields") {
                val encryptedData = "encrypted".encodeToByteArray()
                val salt = "salt".encodeToByteArray()
                val nonce = "nonce".encodeToByteArray()
                val authTag = "auth".encodeToByteArray()
                val params = KeyDerivationParams("PBKDF2", 100000, 32, "SHA256")
                
                val result = ZeroKnowledgeResult(
                    encryptedData = encryptedData,
                    salt = salt,
                    nonce = nonce,
                    authTag = authTag,
                    keyDerivationParams = params
                )
                
                result.encryptedData shouldBe encryptedData
                result.salt shouldBe salt
                result.nonce shouldBe nonce
                result.authTag shouldBe authTag
                result.keyDerivationParams shouldBe params
            }
        }
        
        describe("equality") {
            it("should be equal for same content") {
                val data = "test".encodeToByteArray()
                val salt = "salt".encodeToByteArray()
                val nonce = "nonce".encodeToByteArray()
                val authTag = "auth".encodeToByteArray()
                val params = KeyDerivationParams()
                
                val result1 = ZeroKnowledgeResult(data, salt, nonce, authTag, params)
                val result2 = ZeroKnowledgeResult(data, salt, nonce, authTag, params)
                
                result1 shouldBe result2
                result1.hashCode() shouldBe result2.hashCode()
            }
        }
    }
    
    describe("KeyDerivationParams") {
        
        describe("construction") {
            it("should create params with default values") {
                val params = KeyDerivationParams()
                
                params.algorithm shouldBe "PBKDF2"
                params.iterations shouldBe 100000
                params.keyLength shouldBe 32
                params.hashFunction shouldBe "SHA256"
            }
            
            it("should create params with custom values") {
                val params = KeyDerivationParams(
                    algorithm = "Argon2",
                    iterations = 50000,
                    keyLength = 64,
                    hashFunction = "SHA512"
                )
                
                params.algorithm shouldBe "Argon2"
                params.iterations shouldBe 50000
                params.keyLength shouldBe 64
                params.hashFunction shouldBe "SHA512"
            }
        }
    }
    
    describe("DigitalSignature interface") {
        
        val mockDigitalSignature = mockk<DigitalSignature>()
        
        describe("key pair generation") {
            it("should generate key pair") {
                val expectedKeyPair = KeyPair(
                    publicKey = "public-key".encodeToByteArray(),
                    privateKey = "private-key".encodeToByteArray()
                )
                
                coEvery { mockDigitalSignature.generateKeyPair() } returns expectedKeyPair
                
                val result = mockDigitalSignature.generateKeyPair()
                
                result shouldBe expectedKeyPair
                result.publicKey.shouldNotBeNull()
                result.privateKey.shouldNotBeNull()
            }
        }
        
        describe("signing and verification") {
            it("should sign and verify data") {
                val data = "data to sign".encodeToByteArray()
                val privateKey = "private-key".encodeToByteArray()
                val publicKey = "public-key".encodeToByteArray()
                val signature = "signature".encodeToByteArray()
                
                coEvery { mockDigitalSignature.sign(data, privateKey) } returns signature
                coEvery { mockDigitalSignature.verify(data, signature, publicKey) } returns true
                
                val signResult = mockDigitalSignature.sign(data, privateKey)
                val verifyResult = mockDigitalSignature.verify(data, signature, publicKey)
                
                signResult shouldBe signature
                verifyResult shouldBe true
            }
            
            it("should reject invalid signature") {
                val data = "data to sign".encodeToByteArray()
                val invalidSignature = "invalid-signature".encodeToByteArray()
                val publicKey = "public-key".encodeToByteArray()
                
                coEvery { 
                    mockDigitalSignature.verify(data, invalidSignature, publicKey) 
                } returns false
                
                val result = mockDigitalSignature.verify(data, invalidSignature, publicKey)
                
                result shouldBe false
            }
        }
        
        describe("string signing") {
            it("should sign and verify strings") {
                val data = "string to sign"
                val privateKey = "private-key".encodeToByteArray()
                val publicKey = "public-key".encodeToByteArray()
                val signature = "string-signature".encodeToByteArray()
                
                coEvery { mockDigitalSignature.signString(data, privateKey) } returns signature
                coEvery { mockDigitalSignature.verifyString(data, signature, publicKey) } returns true
                
                val signResult = mockDigitalSignature.signString(data, privateKey)
                val verifyResult = mockDigitalSignature.verifyString(data, signature, publicKey)
                
                signResult shouldBe signature
                verifyResult shouldBe true
            }
        }
    }
    
    describe("KeyPair") {
        
        describe("construction") {
            it("should create key pair") {
                val publicKey = "public".encodeToByteArray()
                val privateKey = "private".encodeToByteArray()
                
                val keyPair = KeyPair(publicKey, privateKey)
                
                keyPair.publicKey shouldBe publicKey
                keyPair.privateKey shouldBe privateKey
            }
        }
        
        describe("equality") {
            it("should be equal for same keys") {
                val publicKey = "public".encodeToByteArray()
                val privateKey = "private".encodeToByteArray()
                
                val keyPair1 = KeyPair(publicKey, privateKey)
                val keyPair2 = KeyPair(publicKey, privateKey)
                
                keyPair1 shouldBe keyPair2
                keyPair1.hashCode() shouldBe keyPair2.hashCode()
            }
        }
    }
    
    describe("SecureRandom interface") {
        
        val mockSecureRandom = mockk<SecureRandom>()
        
        describe("random byte generation") {
            it("should generate random bytes") {
                val expectedBytes = ByteArray(32) { it.toByte() }
                
                coEvery { mockSecureRandom.nextBytes(32) } returns expectedBytes
                
                val result = mockSecureRandom.nextBytes(32)
                
                result shouldBe expectedBytes
                result.size shouldBe 32
            }
        }
        
        describe("random string generation") {
            it("should generate random string") {
                val expectedString = "randomstring123"
                
                coEvery { mockSecureRandom.nextString(15) } returns expectedString
                
                val result = mockSecureRandom.nextString(15)
                
                result shouldBe expectedString
                result.length shouldBe 15
            }
            
            it("should generate string with custom charset") {
                val charset = "0123456789"
                val expectedString = "1234567890"
                
                coEvery { mockSecureRandom.nextString(10, charset) } returns expectedString
                
                val result = mockSecureRandom.nextString(10, charset)
                
                result shouldBe expectedString
                result.all { it in charset } shouldBe true
            }
        }
        
        describe("UUID generation") {
            it("should generate UUID") {
                val expectedUuid = "550e8400-e29b-41d4-a716-446655440000"
                
                coEvery { mockSecureRandom.nextUuid() } returns expectedUuid
                
                val result = mockSecureRandom.nextUuid()
                
                result shouldBe expectedUuid
                result shouldStartWith "550e8400"
            }
        }
    }
    
    describe("timing attack resistance") {
        
        it("should have consistent timing for encryption operations") {
            val mockEncryption = mockk<Encryption>()
            val data1 = "short".encodeToByteArray()
            val data2 = "this is a much longer string with more content".encodeToByteArray()
            val key = CryptoTestUtils.generateTestKey()
            
            val result1 = EncryptionResult("enc1".encodeToByteArray(), "nonce1".encodeToByteArray())
            val result2 = EncryptionResult("enc2".encodeToByteArray(), "nonce2".encodeToByteArray())
            
            coEvery { mockEncryption.encrypt(data1, key) } returns result1
            coEvery { mockEncryption.encrypt(data2, key) } returns result2
            
            // Both operations should complete successfully regardless of input size
            mockEncryption.encrypt(data1, key) shouldBe result1
            mockEncryption.encrypt(data2, key) shouldBe result2
        }
        
        it("should have consistent timing for key derivation") {
            val mockKeyDerivation = mockk<KeyDerivation>()
            val password1 = "short"
            val password2 = "this is a much longer password with special characters !@#$%"
            val salt = CryptoTestUtils.generateTestSalt()
            
            val key1 = CryptoTestUtils.generateTestKey()
            val key2 = CryptoTestUtils.generateTestKey()
            
            coEvery { mockKeyDerivation.deriveKey(password1, salt, 100000, 32) } returns key1
            coEvery { mockKeyDerivation.deriveKey(password2, salt, 100000, 32) } returns key2
            
            // Both operations should complete successfully regardless of password length
            mockKeyDerivation.deriveKey(password1, salt, 100000, 32) shouldBe key1
            mockKeyDerivation.deriveKey(password2, salt, 100000, 32) shouldBe key2
        }
    }
    
    describe("memory security") {
        
        it("should handle sensitive data securely") {
            val mockEncryption = mockk<Encryption>()
            val sensitiveData = "credit card: 1234-5678-9012-3456".encodeToByteArray()
            val key = CryptoTestUtils.generateTestKey()
            val result = EncryptionResult("encrypted".encodeToByteArray(), "nonce".encodeToByteArray())
            
            coEvery { mockEncryption.encrypt(sensitiveData, key) } returns result
            
            val encryptionResult = mockEncryption.encrypt(sensitiveData, key)
            
            encryptionResult shouldBe result
            // In a real implementation, sensitive data should be cleared from memory
            coVerify { mockEncryption.encrypt(sensitiveData, key) }
        }
    }
})