package com.ataiva.eden.vault.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * AWS Secrets Manager connector for Eden Vault Service
 * Provides integration with AWS Secrets Manager for secure secrets management
 */
class AwsSecretsManagerConnector(
    private val region: String,
    private val accessKey: String,
    private val secretKey: String,
    private val prefix: String = "eden/"
) {
    private val secretsManagerClient: SecretsManagerClient by lazy {
        val credentials = AwsBasicCredentials.create(accessKey, secretKey)
        
        SecretsManagerClient.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build()
    }

    /**
     * Read a secret from AWS Secrets Manager
     */
    suspend fun readSecret(secretName: String, key: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val getSecretValueRequest = GetSecretValueRequest.builder()
                .secretId(formatSecretName(secretName))
                .build()

            val response = secretsManagerClient.getSecretValue(getSecretValueRequest)
            val secretString = response.secretString()

            if (key != null) {
                // Parse JSON and extract specific key
                val jsonObject = Json.parseToJsonElement(secretString).jsonObject
                jsonObject[key]?.jsonPrimitive?.content
            } else {
                secretString
            }
        } catch (e: Exception) {
            println("Failed to read secret from AWS Secrets Manager: ${e.message}")
            null
        }
    }

    /**
     * Write a secret to AWS Secrets Manager
     */
    suspend fun writeSecret(secretName: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val formattedSecretName = formatSecretName(secretName)
            
            // Check if secret exists
            try {
                val describeSecretRequest = DescribeSecretRequest.builder()
                    .secretId(formattedSecretName)
                    .build()
                secretsManagerClient.describeSecret(describeSecretRequest)
                
                // Secret exists, update it
                val updateSecretRequest = UpdateSecretRequest.builder()
                    .secretId(formattedSecretName)
                    .secretString(value)
                    .build()
                secretsManagerClient.updateSecret(updateSecretRequest)
            } catch (e: ResourceNotFoundException) {
                // Secret doesn't exist, create it
                val createSecretRequest = CreateSecretRequest.builder()
                    .name(formattedSecretName)
                    .secretString(value)
                    .description("Eden secret: $secretName")
                    .tags(
                        Tag.builder().key("Application").value("Eden").build(),
                        Tag.builder().key("Environment").value("Production").build()
                    )
                    .build()
                secretsManagerClient.createSecret(createSecretRequest)
            }
            
            true
        } catch (e: Exception) {
            println("Failed to write secret to AWS Secrets Manager: ${e.message}")
            false
        }
    }

    /**
     * Delete a secret from AWS Secrets Manager
     */
    suspend fun deleteSecret(secretName: String, forceDelete: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val deleteSecretRequest = DeleteSecretRequest.builder()
                .secretId(formatSecretName(secretName))
                .forceDeleteWithoutRecovery(forceDelete)
                .build()
                
            secretsManagerClient.deleteSecret(deleteSecretRequest)
            true
        } catch (e: Exception) {
            println("Failed to delete secret from AWS Secrets Manager: ${e.message}")
            false
        }
    }

    /**
     * List secrets in AWS Secrets Manager
     */
    suspend fun listSecrets(): List<String> = withContext(Dispatchers.IO) {
        try {
            val listSecretsRequest = ListSecretsRequest.builder()
                .filters(
                    Filter.builder()
                        .key(FilterNameStringType.NAME)
                        .values(prefix)
                        .build()
                )
                .build()
                
            val response = secretsManagerClient.listSecrets(listSecretsRequest)
            response.secretList().map { it.name().removePrefix(prefix) }
        } catch (e: Exception) {
            println("Failed to list secrets in AWS Secrets Manager: ${e.message}")
            emptyList()
        }
    }

    /**
     * Check if AWS Secrets Manager is available
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            secretsManagerClient.listSecrets(ListSecretsRequest.builder().maxResults(1).build())
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Format secret name with prefix
     */
    private fun formatSecretName(secretName: String): String {
        return if (secretName.startsWith(prefix)) secretName else "$prefix$secretName"
    }

    /**
     * Close the client
     */
    fun close() {
        secretsManagerClient.close()
    }
}