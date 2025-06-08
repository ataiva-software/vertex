package com.ataiva.eden.vault.service

import com.ataiva.eden.vault.model.*
import com.ataiva.eden.database.EdenDatabaseService
import com.ataiva.eden.database.repositories.*
import com.ataiva.eden.crypto.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VaultServiceTest {
    
    private lateinit var databaseService: EdenDatabaseService
    private lateinit var secretRepository: SecretRepository
    private lateinit var secretAccessLogRepository: SecretAccessLogRepository
    private lateinit var encryption: Encryption
    private lateinit var zeroKnowledgeEncryption: ZeroKnowledgeEncryption
    private lateinit var secureRandom: SecureRandom
    private lateinit var keyDerivation: KeyDerivation
    private lateinit var vaultService: VaultService
    
    private val testUserId = "test-user-123"
    private val testPassword = "test-password"
    private val testSecretName = "test-secret"
    private val testSecretValue = "secret-value-123"
    
    @BeforeEach
    fun setUp() {
        // Mock dependencies
        databaseService = mock()
        secretRepository = mock()
        secretAccessLogRepository = mock()
        encryption = mock()
        zeroKnowledgeEncryption = mock()
        secureRandom = mock()
        keyDerivation = mock()
        
        // Configure database service mocks
        whenever(databaseService.secretRepository).thenReturn(secretRepository)
        whenever(databaseService.secretAccessLogRepository).thenReturn(secretAccessLogRepository)
        
        // Configure secure random mock
        whenever(secureRandom.nextUuid()).thenReturn("mock-uuid-123")
        
        // Configure zero-knowledge encryption mock
        val mockZkResult = ZeroKnowledgeResult(
            encryptedData = "encrypted-data".toByteArray(),
            salt = "salt".toByteArray(),
            nonce = "nonce".toByteArray(),
            authTag = "auth-tag".toByteArray(),
            keyDerivationParams = KeyDerivationParams()
        )
        whenever(zeroKnowledgeEncryption.encryptZeroKnowledge(any(), any(), any())).thenReturn(mockZkResult)
        whenever(zeroKnowledgeEncryption.decryptZeroKnowledge(any(), any())).thenReturn(testSecretValue)
        
        // Create service instance
        vaultService = VaultService(
            databaseService = databaseService,
            encryption = encryption,
            zeroKnowledgeEncryption = zeroKnowledgeEncryption,
            secureRandom = secureRandom,
            keyDerivation = keyDerivation
        )
    }
    
    @Test
    fun `createSecret should create new secret successfully`() = runTest {
        // Given
        val request = CreateSecretRequest(
            name = testSecretName,
            value = testSecretValue,
            userId = testUserId,
            userPassword = testPassword
        )
        
        val expectedSecret = createTestSecret()
        
        whenever(secretRepository.findByNameAndUser(testSecretName, testUserId)).thenReturn(null)
        whenever(secretRepository.create(any())).thenReturn(expectedSecret)
        whenever(secretAccessLogRepository.logAccess(any(), any(), any(), any(), any())).thenReturn(mock())
        
        // When
        val result = vaultService.createSecret(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(testSecretName, response.name)
        assertEquals("generic", response.type)
        assertTrue(response.isActive)
        
        verify(secretRepository).findByNameAndUser(testSecretName, testUserId)
        verify(secretRepository).create(any())
        verify(secretAccessLogRepository).logAccess(any(), eq(testUserId), eq("CREATE"), any(), any())
    }
    
    @Test
    fun `createSecret should fail when secret already exists`() = runTest {
        // Given
        val request = CreateSecretRequest(
            name = testSecretName,
            value = testSecretValue,
            userId = testUserId,
            userPassword = testPassword
        )
        
        val existingSecret = createTestSecret()
        whenever(secretRepository.findByNameAndUser(testSecretName, testUserId)).thenReturn(existingSecret)
        
        // When
        val result = vaultService.createSecret(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Secret with name '$testSecretName' already exists", result.getErrorOrNull())
        
        verify(secretRepository).findByNameAndUser(testSecretName, testUserId)
        verify(secretRepository, never()).create(any())
    }
    
    @Test
    fun `createSecret should fail with empty name`() = runTest {
        // Given
        val request = CreateSecretRequest(
            name = "",
            value = testSecretValue,
            userId = testUserId,
            userPassword = testPassword
        )
        
        // When
        val result = vaultService.createSecret(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Secret name cannot be empty", result.getErrorOrNull())
        
        verify(secretRepository, never()).findByNameAndUser(any(), any())
    }
    
    @Test
    fun `createSecret should fail with empty value`() = runTest {
        // Given
        val request = CreateSecretRequest(
            name = testSecretName,
            value = "",
            userId = testUserId,
            userPassword = testPassword
        )
        
        // When
        val result = vaultService.createSecret(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Secret value cannot be empty", result.getErrorOrNull())
        
        verify(secretRepository, never()).findByNameAndUser(any(), any())
    }
    
    @Test
    fun `getSecret should retrieve and decrypt secret successfully`() = runTest {
        // Given
        val request = GetSecretRequest(
            name = testSecretName,
            userId = testUserId,
            userPassword = testPassword
        )
        
        val existingSecret = createTestSecret()
        whenever(secretRepository.findByNameAndUser(testSecretName, testUserId)).thenReturn(existingSecret)
        whenever(secretAccessLogRepository.logAccess(any(), any(), any(), any(), any())).thenReturn(mock())
        
        // When
        val result = vaultService.getSecret(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(testSecretName, response.name)
        assertEquals(testSecretValue, response.value)
        assertEquals(1, response.version)
        
        verify(secretRepository).findByNameAndUser(testSecretName, testUserId)
        verify(secretAccessLogRepository).logAccess(any(), eq(testUserId), eq("READ"), any(), any())
    }
    
    @Test
    fun `getSecret should fail when secret not found`() = runTest {
        // Given
        val request = GetSecretRequest(
            name = testSecretName,
            userId = testUserId,
            userPassword = testPassword
        )
        
        whenever(secretRepository.findByNameAndUser(testSecretName, testUserId)).thenReturn(null)
        
        // When
        val result = vaultService.getSecret(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Secret '$testSecretName' not found", result.getErrorOrNull())
        
        verify(secretRepository).findByNameAndUser(testSecretName, testUserId)
        verify(secretAccessLogRepository, never()).logAccess(any(), any(), any(), any(), any())
    }
    
    @Test
    fun `getSecret should fail when secret is inactive`() = runTest {
        // Given
        val request = GetSecretRequest(
            name = testSecretName,
            userId = testUserId,
            userPassword = testPassword
        )
        
        val inactiveSecret = createTestSecret().copy(isActive = false)
        whenever(secretRepository.findByNameAndUser(testSecretName, testUserId)).thenReturn(inactiveSecret)
        
        // When
        val result = vaultService.getSecret(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Secret '$testSecretName' is inactive", result.getErrorOrNull())
        
        verify(secretRepository).findByNameAndUser(testSecretName, testUserId)
        verify(secretAccessLogRepository, never()).logAccess(any(), any(), any(), any(), any())
    }
    
    @Test
    fun `getSecret should fail when decryption fails`() = runTest {
        // Given
        val request = GetSecretRequest(
            name = testSecretName,
            userId = testUserId,
            userPassword = "wrong-password"
        )
        
        val existingSecret = createTestSecret()
        whenever(secretRepository.findByNameAndUser(testSecretName, testUserId)).thenReturn(existingSecret)
        whenever(zeroKnowledgeEncryption.decryptZeroKnowledge(any(), eq("wrong-password"))).thenReturn(null)
        
        // When
        val result = vaultService.getSecret(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Failed to decrypt secret - invalid password or corrupted data", result.getErrorOrNull())
        
        verify(secretRepository).findByNameAndUser(testSecretName, testUserId)
        verify(secretAccessLogRepository, never()).logAccess(any(), any(), any(), any(), any())
    }
    
    @Test
    fun `updateSecret should create new version successfully`() = runTest {
        // Given
        val newValue = "new-secret-value"
        val request = UpdateSecretRequest(
            name = testSecretName,
            newValue = newValue,
            userId = testUserId,
            userPassword = testPassword
        )
        
        val existingSecret = createTestSecret()
        val newSecret = existingSecret.copy(
            id = "new-uuid",
            version = 2,
            updatedAt = Clock.System.now()
        )
        
        whenever(secretRepository.findByNameAndUser(testSecretName, testUserId)).thenReturn(existingSecret)
        whenever(secretRepository.createNewVersion(any())).thenReturn(newSecret)
        whenever(secretAccessLogRepository.logAccess(any(), any(), any(), any(), any())).thenReturn(mock())
        
        // When
        val result = vaultService.updateSecret(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(testSecretName, response.name)
        assertEquals(2, response.version)
        
        verify(secretRepository).findByNameAndUser(testSecretName, testUserId)
        verify(secretRepository).createNewVersion(any())
        verify(secretAccessLogRepository).logAccess(any(), eq(testUserId), eq("UPDATE"), any(), any())
    }
    
    @Test
    fun `updateSecret should fail when secret not found`() = runTest {
        // Given
        val request = UpdateSecretRequest(
            name = testSecretName,
            newValue = "new-value",
            userId = testUserId,
            userPassword = testPassword
        )
        
        whenever(secretRepository.findByNameAndUser(testSecretName, testUserId)).thenReturn(null)
        
        // When
        val result = vaultService.updateSecret(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Secret '$testSecretName' not found", result.getErrorOrNull())
        
        verify(secretRepository).findByNameAndUser(testSecretName, testUserId)
        verify(secretRepository, never()).createNewVersion(any())
    }
    
    @Test
    fun `deleteSecret should deactivate secret successfully`() = runTest {
        // Given
        val request = DeleteSecretRequest(
            name = testSecretName,
            userId = testUserId
        )
        
        val existingSecret = createTestSecret()
        whenever(secretRepository.findByNameAndUser(testSecretName, testUserId)).thenReturn(existingSecret)
        whenever(secretRepository.updateStatus(existingSecret.id, false)).thenReturn(true)
        whenever(secretAccessLogRepository.logAccess(any(), any(), any(), any(), any())).thenReturn(mock())
        
        // When
        val result = vaultService.deleteSecret(request)
        
        // Then
        assertTrue(result.isSuccess())
        
        verify(secretRepository).findByNameAndUser(testSecretName, testUserId)
        verify(secretRepository).updateStatus(existingSecret.id, false)
        verify(secretAccessLogRepository).logAccess(any(), eq(testUserId), eq("DELETE"), any(), any())
    }
    
    @Test
    fun `deleteSecret should fail when secret not found`() = runTest {
        // Given
        val request = DeleteSecretRequest(
            name = testSecretName,
            userId = testUserId
        )
        
        whenever(secretRepository.findByNameAndUser(testSecretName, testUserId)).thenReturn(null)
        
        // When
        val result = vaultService.deleteSecret(request)
        
        // Then
        assertTrue(result.isError())
        assertEquals("Secret '$testSecretName' not found", result.getErrorOrNull())
        
        verify(secretRepository).findByNameAndUser(testSecretName, testUserId)
        verify(secretRepository, never()).updateStatus(any(), any())
    }
    
    @Test
    fun `listSecrets should return active secrets by default`() = runTest {
        // Given
        val request = ListSecretsRequest(userId = testUserId)
        
        val secrets = listOf(
            createTestSecret(),
            createTestSecret().copy(id = "secret-2", name = "secret-2")
        )
        whenever(secretRepository.findActiveByUserId(testUserId)).thenReturn(secrets)
        
        // When
        val result = vaultService.listSecrets(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(2, response.size)
        
        verify(secretRepository).findActiveByUserId(testUserId)
        verify(secretRepository, never()).findByUserId(any())
    }
    
    @Test
    fun `listSecrets should return all secrets when includeInactive is true`() = runTest {
        // Given
        val request = ListSecretsRequest(userId = testUserId, includeInactive = true)
        
        val secrets = listOf(
            createTestSecret(),
            createTestSecret().copy(id = "secret-2", name = "secret-2", isActive = false)
        )
        whenever(secretRepository.findByUserId(testUserId)).thenReturn(secrets)
        
        // When
        val result = vaultService.listSecrets(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(2, response.size)
        
        verify(secretRepository).findByUserId(testUserId)
        verify(secretRepository, never()).findActiveByUserId(any())
    }
    
    @Test
    fun `listSecrets should filter by type when specified`() = runTest {
        // Given
        val request = ListSecretsRequest(userId = testUserId, type = "api-key")
        
        val secrets = listOf(
            createTestSecret().copy(secretType = "api-key"),
            createTestSecret().copy(id = "secret-2", name = "secret-2", secretType = "password")
        )
        whenever(secretRepository.findActiveByUserId(testUserId)).thenReturn(secrets)
        
        // When
        val result = vaultService.listSecrets(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(1, response.size)
        assertEquals("api-key", response[0].type)
    }
    
    @Test
    fun `getSecretVersions should return all versions of a secret`() = runTest {
        // Given
        val request = GetSecretVersionsRequest(name = testSecretName, userId = testUserId)
        
        val versions = listOf(
            createTestSecret().copy(version = 1),
            createTestSecret().copy(id = "secret-v2", version = 2)
        )
        whenever(secretRepository.findVersionsByNameAndUser(testSecretName, testUserId)).thenReturn(versions)
        
        // When
        val result = vaultService.getSecretVersions(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(2, response.size)
        assertEquals(1, response[0].version)
        assertEquals(2, response[1].version)
        
        verify(secretRepository).findVersionsByNameAndUser(testSecretName, testUserId)
    }
    
    @Test
    fun `getSecretStats should return statistics for user`() = runTest {
        // Given
        val expectedStats = SecretStats(
            totalSecrets = 10,
            activeSecrets = 8,
            secretsByType = mapOf("api-key" to 5L, "password" to 3L),
            recentlyCreated = 2,
            recentlyUpdated = 1
        )
        whenever(secretRepository.getSecretStats(testUserId)).thenReturn(expectedStats)
        
        // When
        val result = vaultService.getSecretStats(testUserId)
        
        // Then
        assertTrue(result.isSuccess())
        val stats = result.getOrNull()
        assertNotNull(stats)
        assertEquals(10, stats.totalSecrets)
        assertEquals(8, stats.activeSecrets)
        assertEquals(2, stats.secretsByType.size)
        
        verify(secretRepository).getSecretStats(testUserId)
    }
    
    @Test
    fun `getAccessLogs should return logs for specific secret`() = runTest {
        // Given
        val secretId = "secret-123"
        val request = GetAccessLogsRequest(secretId = secretId)
        
        val logs = listOf(
            createTestAccessLog().copy(secretId = secretId, action = "CREATE"),
            createTestAccessLog().copy(secretId = secretId, action = "READ")
        )
        whenever(secretAccessLogRepository.findBySecretId(secretId)).thenReturn(logs)
        
        // When
        val result = vaultService.getAccessLogs(request)
        
        // Then
        assertTrue(result.isSuccess())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(2, response.size)
        assertEquals("CREATE", response[0].action)
        assertEquals("READ", response[1].action)
        
        verify(secretAccessLogRepository).findBySecretId(secretId)
    }
    
    private fun createTestSecret(): Secret {
        return Secret(
            id = "test-secret-id",
            name = testSecretName,
            encryptedValue = """{"keyId":"test-key","encryptedData":"dGVzdA==","salt":"c2FsdA==","nonce":"bm9uY2U=","authTag":"YXV0aA==","keyDerivationParams":{"algorithm":"PBKDF2","iterations":100000,"keyLength":32,"hashFunction":"SHA256"}}""",
            encryptionKeyId = "test-key-id",
            secretType = "generic",
            description = "Test secret",
            userId = testUserId,
            organizationId = null,
            version = 1,
            isActive = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
    }
    
    private fun createTestAccessLog(): SecretAccessLog {
        return SecretAccessLog(
            id = "log-id",
            secretId = "secret-id",
            userId = testUserId,
            action = "READ",
            ipAddress = "127.0.0.1",
            userAgent = "test-agent",
            createdAt = Clock.System.now()
        )
    }
}