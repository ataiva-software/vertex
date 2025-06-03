package com.ataiva.eden.vault.service

import com.ataiva.eden.crypto.*
import com.ataiva.eden.database.EdenDatabaseService
import com.ataiva.eden.database.repositories.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Core business logic for Eden Vault - Secure secrets management
 */
class VaultService(
    private val databaseService: EdenDatabaseService,
    private val encryption: Encryption,
    private val zeroKnowledgeEncryption: ZeroKnowledgeEncryption,
    private val secureRandom: SecureRandom,
    private val keyDerivation: KeyDerivation
) {
    
    /**
     * Create a new secret with client-side encryption
     */
    suspend fun createSecret(request: CreateSecretRequest): VaultResult<SecretResponse> {
        return try {
            // Validate input
            if (request.name.isBlank()) {
                return VaultResult.Error("Secret name cannot be empty")
            }
            if (request.value.isBlank()) {
                return VaultResult.Error("Secret value cannot be empty")
            }
            
            // Check if secret already exists
            val existing = databaseService.secretRepository.findByNameAndUser(request.name, request.userId)
            if (existing != null) {
                return VaultResult.Error("Secret with name '${request.name}' already exists")
            }
            
            // Generate encryption key ID and encrypt the secret
            val encryptionKeyId = secureRandom.nextUuid()
            val encryptedValue = encryptSecretValue(request.value, request.userPassword, encryptionKeyId)
            
            // Create secret entity
            val secret = Secret(
                id = secureRandom.nextUuid(),
                name = request.name,
                encryptedValue = encryptedValue,
                encryptionKeyId = encryptionKeyId,
                secretType = request.type ?: "generic",
                description = request.description,
                userId = request.userId,
                organizationId = request.organizationId,
                version = 1,
                isActive = true,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            
            // Save to database
            val savedSecret = databaseService.secretRepository.create(secret)
            
            // Log access
            logSecretAccess(savedSecret.id, request.userId, "CREATE", request.ipAddress, request.userAgent)
            
            VaultResult.Success(SecretResponse.fromSecret(savedSecret))
            
        } catch (e: Exception) {
            VaultResult.Error("Failed to create secret: ${e.message}")
        }
    }
    
    /**
     * Retrieve and decrypt a secret
     */
    suspend fun getSecret(request: GetSecretRequest): VaultResult<SecretValueResponse> {
        return try {
            // Find the secret
            val secret = if (request.version != null) {
                databaseService.secretRepository.findByNameUserAndVersion(request.name, request.userId, request.version)
            } else {
                databaseService.secretRepository.findByNameAndUser(request.name, request.userId)
            }
            
            if (secret == null) {
                return VaultResult.Error("Secret '${request.name}' not found")
            }
            
            if (!secret.isActive) {
                return VaultResult.Error("Secret '${request.name}' is inactive")
            }
            
            // Decrypt the secret value
            val decryptedValue = decryptSecretValue(secret.encryptedValue, request.userPassword, secret.encryptionKeyId)
                ?: return VaultResult.Error("Failed to decrypt secret - invalid password or corrupted data")
            
            // Log access
            logSecretAccess(secret.id, request.userId, "READ", request.ipAddress, request.userAgent)
            
            VaultResult.Success(SecretValueResponse(
                id = secret.id,
                name = secret.name,
                value = decryptedValue,
                type = secret.secretType,
                description = secret.description,
                version = secret.version,
                createdAt = secret.createdAt,
                updatedAt = secret.updatedAt
            ))
            
        } catch (e: Exception) {
            VaultResult.Error("Failed to retrieve secret: ${e.message}")
        }
    }
    
    /**
     * Update an existing secret (creates new version)
     */
    suspend fun updateSecret(request: UpdateSecretRequest): VaultResult<SecretResponse> {
        return try {
            // Find existing secret
            val existingSecret = databaseService.secretRepository.findByNameAndUser(request.name, request.userId)
                ?: return VaultResult.Error("Secret '${request.name}' not found")
            
            // Encrypt new value
            val encryptionKeyId = secureRandom.nextUuid()
            val encryptedValue = encryptSecretValue(request.newValue, request.userPassword, encryptionKeyId)
            
            // Create new version
            val newSecret = existingSecret.copy(
                id = secureRandom.nextUuid(),
                encryptedValue = encryptedValue,
                encryptionKeyId = encryptionKeyId,
                description = request.description ?: existingSecret.description,
                version = existingSecret.version + 1,
                updatedAt = Clock.System.now()
            )
            
            // Save new version
            val savedSecret = databaseService.secretRepository.createNewVersion(newSecret)
            
            // Log access
            logSecretAccess(savedSecret.id, request.userId, "UPDATE", request.ipAddress, request.userAgent)
            
            VaultResult.Success(SecretResponse.fromSecret(savedSecret))
            
        } catch (e: Exception) {
            VaultResult.Error("Failed to update secret: ${e.message}")
        }
    }
    
    /**
     * Delete a secret (deactivate)
     */
    suspend fun deleteSecret(request: DeleteSecretRequest): VaultResult<Unit> {
        return try {
            val secret = databaseService.secretRepository.findByNameAndUser(request.name, request.userId)
                ?: return VaultResult.Error("Secret '${request.name}' not found")
            
            // Deactivate the secret
            val success = databaseService.secretRepository.updateStatus(secret.id, false)
            if (!success) {
                return VaultResult.Error("Failed to delete secret")
            }
            
            // Log access
            logSecretAccess(secret.id, request.userId, "DELETE", request.ipAddress, request.userAgent)
            
            VaultResult.Success(Unit)
            
        } catch (e: Exception) {
            VaultResult.Error("Failed to delete secret: ${e.message}")
        }
    }
    
    /**
     * List all secrets for a user
     */
    suspend fun listSecrets(request: ListSecretsRequest): VaultResult<List<SecretResponse>> {
        return try {
            val secrets = if (request.includeInactive) {
                databaseService.secretRepository.findByUserId(request.userId)
            } else {
                databaseService.secretRepository.findActiveByUserId(request.userId)
            }
            
            val filteredSecrets = secrets.filter { secret ->
                when {
                    request.type != null && secret.secretType != request.type -> false
                    request.namePattern != null && !secret.name.contains(request.namePattern, ignoreCase = true) -> false
                    else -> true
                }
            }
            
            VaultResult.Success(filteredSecrets.map { SecretResponse.fromSecret(it) })
            
        } catch (e: Exception) {
            VaultResult.Error("Failed to list secrets: ${e.message}")
        }
    }
    
    /**
     * Get secret versions
     */
    suspend fun getSecretVersions(request: GetSecretVersionsRequest): VaultResult<List<SecretResponse>> {
        return try {
            val versions = databaseService.secretRepository.findVersionsByNameAndUser(request.name, request.userId)
            VaultResult.Success(versions.map { SecretResponse.fromSecret(it) })
            
        } catch (e: Exception) {
            VaultResult.Error("Failed to get secret versions: ${e.message}")
        }
    }
    
    /**
     * Get secret statistics
     */
    suspend fun getSecretStats(userId: String): VaultResult<SecretStats> {
        return try {
            val stats = databaseService.secretRepository.getSecretStats(userId)
            VaultResult.Success(stats)
            
        } catch (e: Exception) {
            VaultResult.Error("Failed to get secret statistics: ${e.message}")
        }
    }
    
    /**
     * Get access logs for a secret
     */
    suspend fun getAccessLogs(request: GetAccessLogsRequest): VaultResult<List<SecretAccessLog>> {
        return try {
            val logs = when {
                request.secretId != null -> databaseService.secretAccessLogRepository.findBySecretId(request.secretId)
                request.userId != null -> databaseService.secretAccessLogRepository.findByUserId(request.userId)
                else -> databaseService.secretAccessLogRepository.findRecentAccess(request.limit ?: 100)
            }
            
            VaultResult.Success(logs)
            
        } catch (e: Exception) {
            VaultResult.Error("Failed to get access logs: ${e.message}")
        }
    }
    
    /**
     * Encrypt secret value using zero-knowledge encryption
     */
    private fun encryptSecretValue(value: String, userPassword: String, keyId: String): String {
        val zkResult = zeroKnowledgeEncryption.encryptZeroKnowledge(value, userPassword)
        
        // Create encrypted secret metadata
        val metadata = EncryptedSecretMetadata(
            keyId = keyId,
            encryptedData = Base64.getEncoder().encodeToString(zkResult.encryptedData),
            salt = Base64.getEncoder().encodeToString(zkResult.salt),
            nonce = Base64.getEncoder().encodeToString(zkResult.nonce),
            authTag = Base64.getEncoder().encodeToString(zkResult.authTag),
            keyDerivationParams = zkResult.keyDerivationParams
        )
        
        return Json.encodeToString(metadata)
    }
    
    /**
     * Decrypt secret value using zero-knowledge encryption
     */
    private fun decryptSecretValue(encryptedValue: String, userPassword: String, keyId: String): String? {
        return try {
            val metadata = Json.decodeFromString<EncryptedSecretMetadata>(encryptedValue)
            
            val zkResult = ZeroKnowledgeResult(
                encryptedData = Base64.getDecoder().decode(metadata.encryptedData),
                salt = Base64.getDecoder().decode(metadata.salt),
                nonce = Base64.getDecoder().decode(metadata.nonce),
                authTag = Base64.getDecoder().decode(metadata.authTag),
                keyDerivationParams = metadata.keyDerivationParams
            )
            
            zeroKnowledgeEncryption.decryptZeroKnowledge(zkResult, userPassword)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Log secret access for audit trail
     */
    private suspend fun logSecretAccess(
        secretId: String,
        userId: String,
        action: String,
        ipAddress: String?,
        userAgent: String?
    ) {
        try {
            databaseService.secretAccessLogRepository.logAccess(
                secretId = secretId,
                userId = userId,
                action = action,
                ipAddress = ipAddress,
                userAgent = userAgent
            )
        } catch (e: Exception) {
            // Log access failure but don't fail the main operation
            println("Failed to log secret access: ${e.message}")
        }
    }
}

/**
 * Encrypted secret metadata for storage
 */
@kotlinx.serialization.Serializable
private data class EncryptedSecretMetadata(
    val keyId: String,
    val encryptedData: String,
    val salt: String,
    val nonce: String,
    val authTag: String,
    val keyDerivationParams: KeyDerivationParams
)

/**
 * Vault operation result
 */
sealed class VaultResult<out T> {
    data class Success<T>(val data: T) : VaultResult<T>()
    data class Error(val message: String) : VaultResult<Nothing>()
    
    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }
    
    fun getErrorOrNull(): String? = when (this) {
        is Success -> null
        is Error -> message
    }
}