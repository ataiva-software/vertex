package com.ataiva.eden.vault.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Request to store a secret in the external secrets manager
 */
@Serializable
data class StoreExternalSecretRequest(
    val name: String,
    val path: String,
    val value: String,
    val description: String? = null,
    val userId: String,
    val organizationId: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null
)

/**
 * Request to get a secret from the external secrets manager
 */
@Serializable
data class GetExternalSecretRequest(
    val name: String,
    val userId: String,
    val key: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null
)

/**
 * Request to delete a secret from the external secrets manager
 */
@Serializable
data class DeleteExternalSecretRequest(
    val name: String,
    val userId: String,
    val deleteFromProvider: Boolean = false,
    val ipAddress: String? = null,
    val userAgent: String? = null
)

/**
 * Request to list secrets in the external secrets manager
 */
@Serializable
data class ListExternalSecretsRequest(
    val userId: String,
    val path: String = ""
)

/**
 * External secret response (without sensitive data)
 */
@Serializable
data class ExternalSecretResponse(
    val id: String,
    val name: String,
    val path: String,
    val type: String,
    val description: String?,
    val provider: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * External secret value response (includes decrypted value)
 */
@Serializable
data class ExternalSecretValueResponse(
    val id: String,
    val name: String,
    val path: String,
    val value: String,
    val type: String,
    val description: String?,
    val provider: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * External secrets manager configuration request
 */
@Serializable
data class ConfigureExternalSecretsManagerRequest(
    val type: String, // "HASHICORP_VAULT", "AWS_SECRETS_MANAGER", "NONE"
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
 * External secrets manager configuration response
 */
@Serializable
data class ExternalSecretsManagerConfigResponse(
    val type: String,
    val isConfigured: Boolean,
    val provider: String? = null,
    val lastChecked: Instant? = null
)