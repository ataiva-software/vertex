package com.ataiva.eden.vault.model

import com.ataiva.eden.database.repositories.Secret
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Request to create a new secret
 */
@Serializable
data class CreateSecretRequest(
    val name: String,
    val value: String,
    val type: String? = null,
    val description: String? = null,
    val userId: String,
    val organizationId: String? = null,
    val userPassword: String,
    val ipAddress: String? = null,
    val userAgent: String? = null
)

/**
 * Request to get a secret
 */
@Serializable
data class GetSecretRequest(
    val name: String,
    val userId: String,
    val version: Int? = null,
    val userPassword: String,
    val ipAddress: String? = null,
    val userAgent: String? = null
)

/**
 * Request to update a secret
 */
@Serializable
data class UpdateSecretRequest(
    val name: String,
    val newValue: String,
    val description: String? = null,
    val userId: String,
    val userPassword: String,
    val ipAddress: String? = null,
    val userAgent: String? = null
)

/**
 * Request to delete a secret
 */
@Serializable
data class DeleteSecretRequest(
    val name: String,
    val userId: String,
    val ipAddress: String? = null,
    val userAgent: String? = null
)

/**
 * Request to list secrets
 */
@Serializable
data class ListSecretsRequest(
    val userId: String,
    val type: String? = null,
    val namePattern: String? = null,
    val includeInactive: Boolean = false
)

/**
 * Request to get secret versions
 */
@Serializable
data class GetSecretVersionsRequest(
    val name: String,
    val userId: String
)

/**
 * Request to get access logs
 */
@Serializable
data class GetAccessLogsRequest(
    val secretId: String? = null,
    val userId: String? = null,
    val limit: Int? = null
)

/**
 * Secret response (without sensitive data)
 */
@Serializable
data class SecretResponse(
    val id: String,
    val name: String,
    val type: String,
    val description: String?,
    val version: Int,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun fromSecret(secret: Secret): SecretResponse {
            return SecretResponse(
                id = secret.id,
                name = secret.name,
                type = secret.secretType,
                description = secret.description,
                version = secret.version,
                isActive = secret.isActive,
                createdAt = secret.createdAt,
                updatedAt = secret.updatedAt
            )
        }
    }
}

/**
 * Secret value response (includes decrypted value)
 */
@Serializable
data class SecretValueResponse(
    val id: String,
    val name: String,
    val value: String,
    val type: String,
    val description: String?,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * API error response
 */
@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: Instant = kotlinx.datetime.Clock.System.now()
)

/**
 * API success response wrapper
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val timestamp: Instant = kotlinx.datetime.Clock.System.now()
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> {
            return ApiResponse(success = true, data = data)
        }
        
        fun <T> error(message: String): ApiResponse<T> {
            return ApiResponse(success = false, error = message)
        }
    }
}

/**
 * Bulk operations request
 */
@Serializable
data class BulkSecretsRequest(
    val secrets: List<CreateSecretRequest>,
    val userId: String,
    val userPassword: String,
    val ipAddress: String? = null,
    val userAgent: String? = null
)

/**
 * Bulk operations response
 */
@Serializable
data class BulkSecretsResponse(
    val successful: List<SecretResponse>,
    val failed: List<BulkOperationError>,
    val totalProcessed: Int,
    val successCount: Int,
    val failureCount: Int
)

/**
 * Bulk operation error
 */
@Serializable
data class BulkOperationError(
    val secretName: String,
    val error: String
)

/**
 * Secret search request
 */
@Serializable
data class SearchSecretsRequest(
    val query: String,
    val userId: String,
    val type: String? = null,
    val limit: Int = 50,
    val offset: Int = 0
)

/**
 * Secret search response
 */
@Serializable
data class SearchSecretsResponse(
    val secrets: List<SecretResponse>,
    val totalCount: Int,
    val hasMore: Boolean
)

/**
 * Secret policy request
 */
@Serializable
data class CreateSecretPolicyRequest(
    val name: String,
    val description: String?,
    val rules: List<PolicyRule>,
    val userId: String
)

/**
 * Policy rule
 */
@Serializable
data class PolicyRule(
    val type: String, // "access", "retention", "encryption"
    val condition: String,
    val action: String,
    val parameters: Map<String, String> = emptyMap()
)

/**
 * Secret policy response
 */
@Serializable
data class SecretPolicyResponse(
    val id: String,
    val name: String,
    val description: String?,
    val rules: List<PolicyRule>,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Secret export request
 */
@Serializable
data class ExportSecretsRequest(
    val userId: String,
    val userPassword: String,
    val secretNames: List<String>? = null,
    val format: String = "json", // "json", "yaml", "env"
    val includeMetadata: Boolean = true
)

/**
 * Secret import request
 */
@Serializable
data class ImportSecretsRequest(
    val userId: String,
    val userPassword: String,
    val data: String,
    val format: String = "json",
    val overwriteExisting: Boolean = false,
    val ipAddress: String? = null,
    val userAgent: String? = null
)

/**
 * Import result
 */
@Serializable
data class ImportSecretsResponse(
    val imported: List<SecretResponse>,
    val skipped: List<String>,
    val errors: List<BulkOperationError>,
    val totalProcessed: Int,
    val importedCount: Int,
    val skippedCount: Int,
    val errorCount: Int
)

/**
 * Health check response
 */
@Serializable
data class VaultHealthResponse(
    val status: String,
    val timestamp: Instant,
    val uptime: Long,
    val service: String,
    val database: DatabaseHealth,
    val encryption: EncryptionHealth,
    val externalSecretsManager: ExternalSecretsManagerConfigResponse? = null
)

/**
 * Database health status
 */
@Serializable
data class DatabaseHealth(
    val connected: Boolean,
    val responseTime: Long? = null,
    val activeConnections: Int? = null
)

/**
 * Encryption health status
 */
@Serializable
data class EncryptionHealth(
    val available: Boolean,
    val algorithm: String = "AES-256-GCM",
    val keyDerivation: String = "PBKDF2"
)