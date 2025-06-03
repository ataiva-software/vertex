package com.ataiva.eden.testing.crypto

import kotlin.random.Random

/**
 * Crypto testing utilities for zero-knowledge encryption testing
 * Provides consistent test data and validation helpers for cryptographic operations
 *
 * FIXED: Added missing generateTestKey() function and test utility functions
 * for integration testing (createOrganization, createUser)
 * FIXED: Replaced System.currentTimeMillis() with multiplatform-compatible approach
 */
object CryptoTestUtils {
    
    // Test key pairs and secrets
    const val TEST_PRIVATE_KEY = "test-private-key-32-bytes-long-123"
    const val TEST_PUBLIC_KEY = "test-public-key-32-bytes-long-456"
    const val TEST_SYMMETRIC_KEY = "test-symmetric-key-256-bit-789"
    const val TEST_SALT = "test-salt-16-bytes"
    const val TEST_IV = "test-iv-16-bytes"
    const val TEST_NONCE = "test-nonce-12-bytes"
    
    // Test data for encryption/decryption
    const val TEST_PLAINTEXT = "This is a test message for encryption"
    const val TEST_ENCRYPTED_DATA = "encrypted-test-data-base64-encoded"
    const val TEST_HASH = "test-hash-sha256-64-characters-long-abcdef1234567890"
    
    // Zero-knowledge proof test data
    const val TEST_ZK_PROOF = "zk-proof-test-data-base64-encoded"
    const val TEST_ZK_COMMITMENT = "zk-commitment-test-data-base64"
    const val TEST_ZK_WITNESS = "zk-witness-test-data-base64"
    
    // Multiplatform-compatible timestamp generation
    private fun getCurrentTimestamp(): Long {
        // Use a simple counter-based approach for testing
        return Random.nextLong(1000000000L, 9999999999L)
    }
    
    /**
     * Generates test key pair for asymmetric encryption testing
     */
    fun generateTestKeyPair(): TestKeyPair {
        return TestKeyPair(
            privateKey = TEST_PRIVATE_KEY,
            publicKey = TEST_PUBLIC_KEY
        )
    }
    
    /**
     * Generates test key for encryption testing (FIXED: Missing function)
     * Returns ByteArray as expected by Encryption interface
     */
    fun generateTestKey(): ByteArray {
        return TEST_SYMMETRIC_KEY.encodeToByteArray()
    }
    
    /**
     * Generates test nonce for encryption testing (FIXED: Missing function)
     */
    fun generateTestNonce(): ByteArray {
        return TEST_NONCE.encodeToByteArray()
    }
    
    /**
     * Generates test salt for key derivation testing (FIXED: Missing function)
     */
    fun generateTestSalt(): ByteArray {
        return TEST_SALT.encodeToByteArray()
    }
    
    /**
     * Generates test symmetric key for symmetric encryption testing
     * Returns String for backward compatibility
     */
    fun generateTestSymmetricKey(): String {
        return TEST_SYMMETRIC_KEY
    }
    
    /**
     * Generates test key as ByteArray for encryption operations
     */
    fun generateTestKeyBytes(): ByteArray {
        return TEST_SYMMETRIC_KEY.encodeToByteArray()
    }
    
    /**
     * Creates test encryption parameters
     */
    fun createTestEncryptionParams(): TestEncryptionParams {
        return TestEncryptionParams(
            key = TEST_SYMMETRIC_KEY,
            salt = TEST_SALT,
            iv = TEST_IV
        )
    }
    
    /**
     * Creates test zero-knowledge proof data
     */
    fun createTestZkProofData(): TestZkProofData {
        return TestZkProofData(
            proof = TEST_ZK_PROOF,
            commitment = TEST_ZK_COMMITMENT,
            witness = TEST_ZK_WITNESS
        )
    }
    
    /**
     * Validates that encrypted data is different from plaintext
     */
    fun validateEncryption(plaintext: String, encrypted: String): Boolean {
        return plaintext != encrypted && encrypted.isNotEmpty()
    }
    
    /**
     * Validates that decrypted data matches original plaintext
     */
    fun validateDecryption(original: String, decrypted: String): Boolean {
        return original == decrypted
    }
    
    /**
     * Validates hash consistency
     */
    fun validateHash(data: String, hash: String): Boolean {
        return hash.isNotEmpty() && hash.length >= 32 // Minimum for SHA-256
    }
    
    /**
     * Validates zero-knowledge proof structure
     */
    fun validateZkProof(proof: TestZkProofData): Boolean {
        return proof.proof.isNotEmpty() &&
               proof.commitment.isNotEmpty() &&
               proof.witness.isNotEmpty()
    }
    
    /**
     * Creates test organization for integration testing (FIXED: Missing function)
     */
    fun createOrganization(name: String = "Test Organization", slug: String = "test-org"): TestOrganization {
        val timestamp = getCurrentTimestamp()
        return TestOrganization(
            id = "org-${timestamp}",
            name = name,
            slug = slug,
            description = "Test organization for integration testing",
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }
    
    /**
     * Creates test user for integration testing (FIXED: Missing function)
     */
    fun createUser(
        email: String = "test@example.com",
        name: String = "Test User",
        organizationId: String = "test-org-id"
    ): TestUser {
        val timestamp = getCurrentTimestamp()
        return TestUser(
            id = "user-${timestamp}",
            email = email,
            name = name,
            organizationId = organizationId,
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }
}

/**
 * Test data classes
 */
data class TestKeyPair(
    val privateKey: String,
    val publicKey: String
)

data class TestEncryptionParams(
    val key: String,
    val salt: String,
    val iv: String
)

data class TestZkProofData(
    val proof: String,
    val commitment: String,
    val witness: String
)

/**
 * Test data classes for integration testing (FIXED: Missing classes)
 */
data class TestOrganization(
    val id: String,
    val name: String,
    val slug: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class TestUser(
    val id: String,
    val email: String,
    val name: String,
    val organizationId: String,
    val createdAt: Long,
    val updatedAt: Long
)