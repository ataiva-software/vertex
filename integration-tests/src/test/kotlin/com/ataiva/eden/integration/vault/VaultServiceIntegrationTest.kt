package com.ataiva.eden.integration.vault

import com.ataiva.eden.vault.service.VaultService
import com.ataiva.eden.vault.service.VaultResult
import com.ataiva.eden.vault.model.*
import com.ataiva.eden.database.EdenDatabaseService
import com.ataiva.eden.database.PostgreSQLDatabaseService
import com.ataiva.eden.database.repositories.*
import com.ataiva.eden.crypto.*
import com.ataiva.eden.testing.fixtures.DatabaseTestFixtures
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Testcontainers
class VaultServiceIntegrationTest {
    
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("eden_test")
            .withUsername("test_user")
            .withPassword("test_password")
    }
    
    private lateinit var databaseService: EdenDatabaseService
    private lateinit var vaultService: VaultService
    private lateinit var testFixtures: DatabaseTestFixtures
    
    private val testUserId = "integration-test-user"
    private val testPassword = "integration-test-password"
    private val testOrganizationId = "test-org-123"
    
    @BeforeEach
    fun setUp() = runTest {
        // Create database service with test container
        val config = mapOf(
            "url" to postgres.jdbcUrl,
            "username" to postgres.username,
            "password" to postgres.password,
            "driver" to "org.postgresql.Driver"
        )
        
        databaseService = PostgreSQLDatabaseService(config)
        
        // Initialize database and run migrations
        databaseService.initialize()
        databaseService.migrate()
        
        // Create test fixtures
        testFixtures = DatabaseTestFixtures(databaseService)
        
        // Create crypto services (using mock implementations for integration tests)
        val cryptoServices = createTestCryptoServices()
        
        // Create vault service
        vaultService = VaultService(
            databaseService = databaseService,
            encryption = cryptoServices.encryption,
            zeroKnowledgeEncryption = cryptoServices.zeroKnowledgeEncryption,
            secureRandom = cryptoServices.secureRandom,
            keyDerivation = cryptoServices.keyDerivation
        )
        
        // Set up test data
        setupTestData()
    }
    
    @AfterEach
    fun tearDown() = runTest {
        databaseService.close()
    }
    
    @Test
    fun `complete secret lifecycle - create, read, update, delete`() = runTest {
        val secretName = "lifecycle-test-secret"
        val originalValue = "original-secret-value"
        val updatedValue = "updated-secret-value"
        
        // 1. Create secret
        val createRequest = CreateSecretRequest(
            name = secretName,
            value = originalValue,
            type = "api-key",
            description = "Integration test secret",
            userId = testUserId,
            organizationId = testOrganizationId,
            userPassword = testPassword,
            ipAddress = "127.0.0.1",
            userAgent = "integration-test"
        )
        
        val createResult = vaultService.createSecret(createRequest)
        assertTrue(createResult.isSuccess())
        val createdSecret = createResult.getOrNull()!!
        
        assertEquals(secretName, createdSecret.name)
        assertEquals("api-key", createdSecret.type)
        assertEquals("Integration test secret", createdSecret.description)
        assertEquals(1, createdSecret.version)
        assertTrue(createdSecret.isActive)
        
        // 2. Read secret
        val getRequest = GetSecretRequest(
            name = secretName,
            userId = testUserId,
            userPassword = testPassword,
            ipAddress = "127.0.0.1",
            userAgent = "integration-test"
        )
        
        val getResult = vaultService.getSecret(getRequest)
        assertTrue(getResult.isSuccess())
        val retrievedSecret = getResult.getOrNull()!!
        
        assertEquals(secretName, retrievedSecret.name)
        assertEquals(originalValue, retrievedSecret.value)
        assertEquals("api-key", retrievedSecret.type)
        assertEquals(1, retrievedSecret.version)
        
        // 3. Update secret
        val updateRequest = UpdateSecretRequest(
            name = secretName,
            newValue = updatedValue,
            description = "Updated integration test secret",
            userId = testUserId,
            userPassword = testPassword,
            ipAddress = "127.0.0.1",
            userAgent = "integration-test"
        )
        
        val updateResult = vaultService.updateSecret(updateRequest)
        assertTrue(updateResult.isSuccess())
        val updatedSecret = updateResult.getOrNull()!!
        
        assertEquals(secretName, updatedSecret.name)
        assertEquals(2, updatedSecret.version)
        assertEquals("Updated integration test secret", updatedSecret.description)
        
        // 4. Read updated secret
        val getUpdatedResult = vaultService.getSecret(getRequest)
        assertTrue(getUpdatedResult.isSuccess())
        val retrievedUpdatedSecret = getUpdatedResult.getOrNull()!!
        
        assertEquals(updatedValue, retrievedUpdatedSecret.value)
        assertEquals(2, retrievedUpdatedSecret.version)
        
        // 5. Get secret versions
        val versionsRequest = GetSecretVersionsRequest(name = secretName, userId = testUserId)
        val versionsResult = vaultService.getSecretVersions(versionsRequest)
        assertTrue(versionsResult.isSuccess())
        val versions = versionsResult.getOrNull()!!
        
        assertEquals(2, versions.size)
        assertTrue(versions.any { it.version == 1 })
        assertTrue(versions.any { it.version == 2 })
        
        // 6. Delete secret
        val deleteRequest = DeleteSecretRequest(
            name = secretName,
            userId = testUserId,
            ipAddress = "127.0.0.1",
            userAgent = "integration-test"
        )
        
        val deleteResult = vaultService.deleteSecret(deleteRequest)
        assertTrue(deleteResult.isSuccess())
        
        // 7. Verify secret is inactive
        val getDeletedResult = vaultService.getSecret(getRequest)
        assertTrue(getDeletedResult.isError())
        assertEquals("Secret '$secretName' is inactive", getDeletedResult.getErrorOrNull())
    }
    
    @Test
    fun `bulk secret operations`() = runTest {
        val secretCount = 5
        val secrets = (1..secretCount).map { i ->
            CreateSecretRequest(
                name = "bulk-secret-$i",
                value = "bulk-value-$i",
                type = "bulk-test",
                description = "Bulk test secret $i",
                userId = testUserId,
                organizationId = testOrganizationId,
                userPassword = testPassword
            )
        }
        
        // Create secrets individually (bulk endpoint would be tested separately)
        val createdSecrets = mutableListOf<SecretResponse>()
        for (secret in secrets) {
            val result = vaultService.createSecret(secret)
            assertTrue(result.isSuccess())
            createdSecrets.add(result.getOrNull()!!)
        }
        
        assertEquals(secretCount, createdSecrets.size)
        
        // List all secrets
        val listRequest = ListSecretsRequest(userId = testUserId, type = "bulk-test")
        val listResult = vaultService.listSecrets(listRequest)
        assertTrue(listResult.isSuccess())
        val listedSecrets = listResult.getOrNull()!!
        
        assertEquals(secretCount, listedSecrets.size)
        assertTrue(listedSecrets.all { it.type == "bulk-test" })
        assertTrue(listedSecrets.all { it.isActive })
    }
    
    @Test
    fun `secret search and filtering`() = runTest {
        // Create test secrets with different types and names
        val testSecrets = listOf(
            CreateSecretRequest("api-key-github", "github-token", "api-key", "GitHub API key", testUserId, testOrganizationId, testPassword),
            CreateSecretRequest("api-key-slack", "slack-token", "api-key", "Slack API key", testUserId, testOrganizationId, testPassword),
            CreateSecretRequest("db-password-prod", "prod-db-pass", "password", "Production DB password", testUserId, testOrganizationId, testPassword),
            CreateSecretRequest("db-password-staging", "staging-db-pass", "password", "Staging DB password", testUserId, testOrganizationId, testPassword),
            CreateSecretRequest("certificate-ssl", "ssl-cert-data", "certificate", "SSL certificate", testUserId, testOrganizationId, testPassword)
        )
        
        // Create all secrets
        for (secret in testSecrets) {
            val result = vaultService.createSecret(secret)
            assertTrue(result.isSuccess())
        }
        
        // Test filtering by type
        val apiKeyRequest = ListSecretsRequest(userId = testUserId, type = "api-key")
        val apiKeyResult = vaultService.listSecrets(apiKeyRequest)
        assertTrue(apiKeyResult.isSuccess())
        val apiKeys = apiKeyResult.getOrNull()!!
        
        assertEquals(2, apiKeys.size)
        assertTrue(apiKeys.all { it.type == "api-key" })
        assertTrue(apiKeys.any { it.name == "api-key-github" })
        assertTrue(apiKeys.any { it.name == "api-key-slack" })
        
        // Test filtering by name pattern
        val dbPasswordRequest = ListSecretsRequest(userId = testUserId, namePattern = "db-password")
        val dbPasswordResult = vaultService.listSecrets(dbPasswordRequest)
        assertTrue(dbPasswordResult.isSuccess())
        val dbPasswords = dbPasswordResult.getOrNull()!!
        
        assertEquals(2, dbPasswords.size)
        assertTrue(dbPasswords.all { it.name.contains("db-password") })
        
        // Test getting all secrets
        val allSecretsRequest = ListSecretsRequest(userId = testUserId)
        val allSecretsResult = vaultService.listSecrets(allSecretsRequest)
        assertTrue(allSecretsResult.isSuccess())
        val allSecrets = allSecretsResult.getOrNull()!!
        
        assertTrue(allSecrets.size >= 5) // At least the 5 we created
    }
    
    @Test
    fun `secret access logging and audit trail`() = runTest {
        val secretName = "audit-test-secret"
        val secretValue = "audit-secret-value"
        
        // Create secret
        val createRequest = CreateSecretRequest(
            name = secretName,
            value = secretValue,
            userId = testUserId,
            userPassword = testPassword,
            ipAddress = "192.168.1.100",
            userAgent = "audit-test-client"
        )
        
        val createResult = vaultService.createSecret(createRequest)
        assertTrue(createResult.isSuccess())
        val createdSecret = createResult.getOrNull()!!
        
        // Read secret multiple times
        val getRequest = GetSecretRequest(
            name = secretName,
            userId = testUserId,
            userPassword = testPassword,
            ipAddress = "192.168.1.101",
            userAgent = "audit-read-client"
        )
        
        repeat(3) {
            val getResult = vaultService.getSecret(getRequest)
            assertTrue(getResult.isSuccess())
        }
        
        // Update secret
        val updateRequest = UpdateSecretRequest(
            name = secretName,
            newValue = "updated-audit-value",
            userId = testUserId,
            userPassword = testPassword,
            ipAddress = "192.168.1.102",
            userAgent = "audit-update-client"
        )
        
        val updateResult = vaultService.updateSecret(updateRequest)
        assertTrue(updateResult.isSuccess())
        
        // Get access logs for the secret
        val logsRequest = GetAccessLogsRequest(secretId = createdSecret.id)
        val logsResult = vaultService.getAccessLogs(logsRequest)
        assertTrue(logsResult.isSuccess())
        val logs = logsResult.getOrNull()!!
        
        // Should have at least CREATE, READ (x3), UPDATE operations
        assertTrue(logs.size >= 5)
        
        val actions = logs.map { it.action }.toSet()
        assertTrue(actions.contains("CREATE"))
        assertTrue(actions.contains("READ"))
        assertTrue(actions.contains("UPDATE"))
        
        // Verify IP addresses and user agents are logged
        val createLog = logs.find { it.action == "CREATE" }
        assertNotNull(createLog)
        assertEquals("192.168.1.100", createLog.ipAddress)
        assertEquals("audit-test-client", createLog.userAgent)
        
        val readLogs = logs.filter { it.action == "READ" }
        assertTrue(readLogs.isNotEmpty())
        assertTrue(readLogs.all { it.ipAddress == "192.168.1.101" })
        assertTrue(readLogs.all { it.userAgent == "audit-read-client" })
    }
    
    @Test
    fun `secret statistics and reporting`() = runTest {
        // Create secrets of different types
        val secretsToCreate = listOf(
            Triple("stats-api-1", "api-key", true),
            Triple("stats-api-2", "api-key", true),
            Triple("stats-api-3", "api-key", false), // inactive
            Triple("stats-pwd-1", "password", true),
            Triple("stats-pwd-2", "password", true),
            Triple("stats-cert-1", "certificate", true)
        )
        
        for ((name, type, active) in secretsToCreate) {
            val createRequest = CreateSecretRequest(
                name = name,
                value = "test-value",
                type = type,
                userId = testUserId,
                userPassword = testPassword
            )
            
            val result = vaultService.createSecret(createRequest)
            assertTrue(result.isSuccess())
            
            // Deactivate if needed
            if (!active) {
                val deleteRequest = DeleteSecretRequest(name = name, userId = testUserId)
                vaultService.deleteSecret(deleteRequest)
            }
        }
        
        // Get statistics
        val statsResult = vaultService.getSecretStats(testUserId)
        assertTrue(statsResult.isSuccess())
        val stats = statsResult.getOrNull()!!
        
        assertEquals(6, stats.totalSecrets)
        assertEquals(5, stats.activeSecrets) // One is inactive
        
        val secretsByType = stats.secretsByType
        assertEquals(3, secretsByType["api-key"]) // 3 api-key secrets created
        assertEquals(2, secretsByType["password"]) // 2 password secrets created
        assertEquals(1, secretsByType["certificate"]) // 1 certificate secret created
    }
    
    @Test
    fun `error handling and edge cases`() = runTest {
        // Test duplicate secret creation
        val secretName = "duplicate-test"
        val createRequest = CreateSecretRequest(
            name = secretName,
            value = "test-value",
            userId = testUserId,
            userPassword = testPassword
        )
        
        val firstResult = vaultService.createSecret(createRequest)
        assertTrue(firstResult.isSuccess())
        
        val duplicateResult = vaultService.createSecret(createRequest)
        assertTrue(duplicateResult.isError())
        assertTrue(duplicateResult.getErrorOrNull()!!.contains("already exists"))
        
        // Test accessing non-existent secret
        val nonExistentRequest = GetSecretRequest(
            name = "non-existent-secret",
            userId = testUserId,
            userPassword = testPassword
        )
        
        val nonExistentResult = vaultService.getSecret(nonExistentRequest)
        assertTrue(nonExistentResult.isError())
        assertTrue(nonExistentResult.getErrorOrNull()!!.contains("not found"))
        
        // Test wrong password
        val wrongPasswordRequest = GetSecretRequest(
            name = secretName,
            userId = testUserId,
            userPassword = "wrong-password"
        )
        
        val wrongPasswordResult = vaultService.getSecret(wrongPasswordRequest)
        assertTrue(wrongPasswordResult.isError())
        assertTrue(wrongPasswordResult.getErrorOrNull()!!.contains("invalid password"))
        
        // Test empty secret name
        val emptyNameRequest = CreateSecretRequest(
            name = "",
            value = "test-value",
            userId = testUserId,
            userPassword = testPassword
        )
        
        val emptyNameResult = vaultService.createSecret(emptyNameRequest)
        assertTrue(emptyNameResult.isError())
        assertEquals("Secret name cannot be empty", emptyNameResult.getErrorOrNull())
        
        // Test empty secret value
        val emptyValueRequest = CreateSecretRequest(
            name = "test-empty-value",
            value = "",
            userId = testUserId,
            userPassword = testPassword
        )
        
        val emptyValueResult = vaultService.createSecret(emptyValueRequest)
        assertTrue(emptyValueResult.isError())
        assertEquals("Secret value cannot be empty", emptyValueResult.getErrorOrNull())
    }
    
    private suspend fun setupTestData() {
        // Create test user if needed
        testFixtures.createTestUser(
            id = testUserId,
            username = "integration-test-user",
            email = "integration-test@example.com",
            organizationId = testOrganizationId
        )
    }
    
    private fun createTestCryptoServices(): TestCryptoServices {
        return TestCryptoServices(
            encryption = TestEncryption(),
            zeroKnowledgeEncryption = TestZeroKnowledgeEncryption(),
            secureRandom = TestSecureRandom(),
            keyDerivation = TestKeyDerivation()
        )
    }
    
    private data class TestCryptoServices(
        val encryption: Encryption,
        val zeroKnowledgeEncryption: ZeroKnowledgeEncryption,
        val secureRandom: SecureRandom,
        val keyDerivation: KeyDerivation
    )
    
    // Test implementations of crypto interfaces
    private class TestEncryption : Encryption {
        override fun encrypt(data: ByteArray, key: ByteArray): EncryptionResult {
            return EncryptionResult(data, ByteArray(12), ByteArray(16))
        }
        
        override fun decrypt(encryptedData: ByteArray, key: ByteArray, nonce: ByteArray): DecryptionResult {
            return DecryptionResult.Success(encryptedData)
        }
        
        override fun encryptString(data: String, key: ByteArray): EncryptionResult {
            return encrypt(data.toByteArray(), key)
        }
        
        override fun decryptString(encryptedData: ByteArray, key: ByteArray, nonce: ByteArray): String? {
            return String(encryptedData)
        }
    }
    
    private class TestZeroKnowledgeEncryption : ZeroKnowledgeEncryption {
        override fun encryptZeroKnowledge(data: String, userPassword: String, salt: ByteArray?): ZeroKnowledgeResult {
            val actualSalt = salt ?: ByteArray(32) { it.toByte() }
            return ZeroKnowledgeResult(
                encryptedData = data.toByteArray(),
                salt = actualSalt,
                nonce = ByteArray(12),
                authTag = ByteArray(16),
                keyDerivationParams = KeyDerivationParams()
            )
        }
        
        override fun decryptZeroKnowledge(encryptedData: ZeroKnowledgeResult, userPassword: String): String? {
            return String(encryptedData.encryptedData)
        }
        
        override fun verifyIntegrity(encryptedData: ZeroKnowledgeResult): Boolean {
            return true
        }
    }
    
    private class TestSecureRandom : SecureRandom {
        private var counter = 0
        
        override fun nextBytes(size: Int): ByteArray {
            return ByteArray(size) { (counter++ % 256).toByte() }
        }
        
        override fun nextString(length: Int, charset: String): String {
            return (1..length).map { charset[counter++ % charset.length] }.joinToString("")
        }
        
        override fun nextUuid(): String {
            return "test-uuid-${counter++}"
        }
    }
    
    private class TestKeyDerivation : KeyDerivation {
        override fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
            return ByteArray(keyLength) { (password.hashCode() + salt.contentHashCode()).toByte() }
        }
        
        override fun deriveKeyArgon2(password: String, salt: ByteArray, memory: Int, iterations: Int, parallelism: Int): ByteArray {
            return deriveKey(password, salt, iterations, 32)
        }
        
        override fun generateSalt(length: Int): ByteArray {
            return ByteArray(length) { (Math.random() * 256).toInt().toByte() }
        }
        
        override fun deriveKeys(masterKey: ByteArray, info: String, keyCount: Int, keyLength: Int): List<ByteArray> {
            return (1..keyCount).map { ByteArray(keyLength) { (masterKey.contentHashCode() + it).toByte() } }
        }
    }
}