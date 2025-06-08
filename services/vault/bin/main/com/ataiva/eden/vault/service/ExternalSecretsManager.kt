package com.ataiva.eden.vault.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.datetime.Clock

/**
 * External secrets manager provider type
 */
enum class SecretsManagerType {
    HASHICORP_VAULT,
    AWS_SECRETS_MANAGER,
    NONE
}

/**
 * External secrets manager configuration
 */
data class ExternalSecretsManagerConfig(
    val type: SecretsManagerType,
    // HashiCorp Vault config
    val vaultUrl: String? = null,
    val vaultToken: String? = null,
    val vaultNamespace: String? = null,
    // AWS Secrets Manager config
    val awsRegion: String? = null,
    val awsAccessKey: String? = null,
    val awsSecretKey: String? = null,
    val awsPrefix: String? = null
)

/**
 * External secrets manager result
 */
sealed class ExternalSecretsResult<out T> {
    data class Success<T>(val data: T) : ExternalSecretsResult<T>()
    data class Error(val message: String) : ExternalSecretsResult<Nothing>()
}

/**
 * External secrets manager interface
 * Provides a unified interface for external secrets management systems
 */
class ExternalSecretsManager(private val config: ExternalSecretsManagerConfig) {
    
    private val hashiCorpVaultConnector: HashiCorpVaultConnector? by lazy {
        if (config.type == SecretsManagerType.HASHICORP_VAULT && 
            !config.vaultUrl.isNullOrBlank() && 
            !config.vaultToken.isNullOrBlank()) {
            HashiCorpVaultConnector(
                vaultUrl = config.vaultUrl,
                vaultToken = config.vaultToken,
                vaultNamespace = config.vaultNamespace ?: "eden"
            )
        } else {
            null
        }
    }
    
    private val awsSecretsManagerConnector: AwsSecretsManagerConnector? by lazy {
        if (config.type == SecretsManagerType.AWS_SECRETS_MANAGER && 
            !config.awsRegion.isNullOrBlank() && 
            !config.awsAccessKey.isNullOrBlank() && 
            !config.awsSecretKey.isNullOrBlank()) {
            AwsSecretsManagerConnector(
                region = config.awsRegion,
                accessKey = config.awsAccessKey,
                secretKey = config.awsSecretKey,
                prefix = config.awsPrefix ?: "eden/"
            )
        } else {
            null
        }
    }
    
    /**
     * Read a secret from the external secrets manager
     */
    suspend fun readSecret(path: String, key: String? = null): ExternalSecretsResult<String> {
        return when (config.type) {
            SecretsManagerType.HASHICORP_VAULT -> {
                val connector = hashiCorpVaultConnector ?: return ExternalSecretsResult.Error("HashiCorp Vault connector not configured")
                val result = connector.readSecret(path, key)
                if (result != null) {
                    ExternalSecretsResult.Success(result)
                } else {
                    ExternalSecretsResult.Error("Failed to read secret from HashiCorp Vault")
                }
            }
            SecretsManagerType.AWS_SECRETS_MANAGER -> {
                val connector = awsSecretsManagerConnector ?: return ExternalSecretsResult.Error("AWS Secrets Manager connector not configured")
                val result = connector.readSecret(path, key)
                if (result != null) {
                    ExternalSecretsResult.Success(result)
                } else {
                    ExternalSecretsResult.Error("Failed to read secret from AWS Secrets Manager")
                }
            }
            SecretsManagerType.NONE -> {
                ExternalSecretsResult.Error("No external secrets manager configured")
            }
        }
    }
    
    /**
     * Write a secret to the external secrets manager
     */
    suspend fun writeSecret(path: String, value: String): ExternalSecretsResult<Boolean> {
        return when (config.type) {
            SecretsManagerType.HASHICORP_VAULT -> {
                val connector = hashiCorpVaultConnector ?: return ExternalSecretsResult.Error("HashiCorp Vault connector not configured")
                val key = path.substringAfterLast("/", "value")
                val secretPath = path.substringBeforeLast("/", path)
                val result = connector.writeSecret(secretPath, key, value)
                if (result) {
                    ExternalSecretsResult.Success(true)
                } else {
                    ExternalSecretsResult.Error("Failed to write secret to HashiCorp Vault")
                }
            }
            SecretsManagerType.AWS_SECRETS_MANAGER -> {
                val connector = awsSecretsManagerConnector ?: return ExternalSecretsResult.Error("AWS Secrets Manager connector not configured")
                val result = connector.writeSecret(path, value)
                if (result) {
                    ExternalSecretsResult.Success(true)
                } else {
                    ExternalSecretsResult.Error("Failed to write secret to AWS Secrets Manager")
                }
            }
            SecretsManagerType.NONE -> {
                ExternalSecretsResult.Error("No external secrets manager configured")
            }
        }
    }
    
    /**
     * Delete a secret from the external secrets manager
     */
    suspend fun deleteSecret(path: String): ExternalSecretsResult<Boolean> {
        return when (config.type) {
            SecretsManagerType.HASHICORP_VAULT -> {
                val connector = hashiCorpVaultConnector ?: return ExternalSecretsResult.Error("HashiCorp Vault connector not configured")
                val result = connector.deleteSecret(path)
                if (result) {
                    ExternalSecretsResult.Success(true)
                } else {
                    ExternalSecretsResult.Error("Failed to delete secret from HashiCorp Vault")
                }
            }
            SecretsManagerType.AWS_SECRETS_MANAGER -> {
                val connector = awsSecretsManagerConnector ?: return ExternalSecretsResult.Error("AWS Secrets Manager connector not configured")
                val result = connector.deleteSecret(path)
                if (result) {
                    ExternalSecretsResult.Success(true)
                } else {
                    ExternalSecretsResult.Error("Failed to delete secret from AWS Secrets Manager")
                }
            }
            SecretsManagerType.NONE -> {
                ExternalSecretsResult.Error("No external secrets manager configured")
            }
        }
    }
    
    /**
     * List secrets in the external secrets manager
     */
    suspend fun listSecrets(path: String = ""): ExternalSecretsResult<List<String>> {
        return when (config.type) {
            SecretsManagerType.HASHICORP_VAULT -> {
                val connector = hashiCorpVaultConnector ?: return ExternalSecretsResult.Error("HashiCorp Vault connector not configured")
                val result = connector.listSecrets(path)
                ExternalSecretsResult.Success(result)
            }
            SecretsManagerType.AWS_SECRETS_MANAGER -> {
                val connector = awsSecretsManagerConnector ?: return ExternalSecretsResult.Error("AWS Secrets Manager connector not configured")
                val result = connector.listSecrets()
                ExternalSecretsResult.Success(result)
            }
            SecretsManagerType.NONE -> {
                ExternalSecretsResult.Error("No external secrets manager configured")
            }
        }
    }
    
    /**
     * Check if the external secrets manager is available
     */
    suspend fun isAvailable(): Boolean {
        return when (config.type) {
            SecretsManagerType.HASHICORP_VAULT -> {
                hashiCorpVaultConnector?.isAvailable() ?: false
            }
            SecretsManagerType.AWS_SECRETS_MANAGER -> {
                awsSecretsManagerConnector?.isAvailable() ?: false
            }
            SecretsManagerType.NONE -> {
                false
            }
        }
    }
    
    /**
     * Get the type of external secrets manager
     */
    fun getType(): SecretsManagerType {
        return config.type
    }
    
    /**
     * Get health status of the external secrets manager
     */
    suspend fun getHealthStatus(): ExternalSecretsManagerHealth {
        val available = isAvailable()
        return ExternalSecretsManagerHealth(
            type = config.type.name,
            available = available,
            lastChecked = Clock.System.now()
        )
    }
    
    /**
     * Close connections
     */
    fun close() {
        if (config.type == SecretsManagerType.AWS_SECRETS_MANAGER) {
            awsSecretsManagerConnector?.close()
        }
    }
}

/**
 * External secrets manager health status
 */
data class ExternalSecretsManagerHealth(
    val type: String,
    val available: Boolean,
    val lastChecked: kotlinx.datetime.Instant
)