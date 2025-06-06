package com.ataiva.eden.vault.service

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import com.bettercloud.vault.VaultException
import com.bettercloud.vault.response.LogicalResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * HashiCorp Vault connector for Eden Vault Service
 * Provides integration with HashiCorp Vault for secure secrets management
 */
class HashiCorpVaultConnector(
    private val vaultUrl: String,
    private val vaultToken: String,
    private val vaultNamespace: String = "eden",
    private val openTimeout: Int = 5,
    private val readTimeout: Int = 30
) {
    private val vault: Vault by lazy {
        val config = VaultConfig()
            .address(vaultUrl)
            .token(vaultToken)
            .openTimeout(openTimeout)
            .readTimeout(readTimeout)
            .engineVersion(2)
            .build()

        if (vaultNamespace.isNotEmpty()) {
            config.nameSpace(vaultNamespace)
        }

        Vault(config)
    }

    /**
     * Read a secret from HashiCorp Vault
     */
    suspend fun readSecret(path: String, key: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val response = vault.logical().read(path)
            return@withContext if (key != null) {
                response.data[key]
            } else {
                Json.encodeToString(JsonObject.serializer(), response.data.mapValues { 
                    kotlinx.serialization.json.JsonPrimitive(it.value) 
                }.toMap())
            }
        } catch (e: VaultException) {
            println("Failed to read secret from HashiCorp Vault: ${e.message}")
            null
        }
    }

    /**
     * Write a secret to HashiCorp Vault
     */
    suspend fun writeSecret(path: String, key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            vault.logical().write(path, mapOf(key to value))
            true
        } catch (e: VaultException) {
            println("Failed to write secret to HashiCorp Vault: ${e.message}")
            false
        }
    }

    /**
     * Delete a secret from HashiCorp Vault
     */
    suspend fun deleteSecret(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            vault.logical().delete(path)
            true
        } catch (e: VaultException) {
            println("Failed to delete secret from HashiCorp Vault: ${e.message}")
            false
        }
    }

    /**
     * List secrets at a path in HashiCorp Vault
     */
    suspend fun listSecrets(path: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = vault.logical().list(path)
            response.data["keys"]?.split(",") ?: emptyList()
        } catch (e: VaultException) {
            println("Failed to list secrets in HashiCorp Vault: ${e.message}")
            emptyList()
        }
    }

    /**
     * Check if HashiCorp Vault is available
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            vault.logical().read("sys/health")
            true
        } catch (e: VaultException) {
            false
        }
    }

    /**
     * Get a dynamic database credential from HashiCorp Vault
     */
    suspend fun getDatabaseCredential(dbRole: String): DatabaseCredential? = withContext(Dispatchers.IO) {
        try {
            val response = vault.logical().read("database/creds/$dbRole")
            DatabaseCredential(
                username = response.data["username"] ?: return@withContext null,
                password = response.data["password"] ?: return@withContext null,
                leaseDuration = response.leaseDuration
            )
        } catch (e: VaultException) {
            println("Failed to get database credential from HashiCorp Vault: ${e.message}")
            null
        }
    }

    /**
     * Generate a one-time token with specific policies
     */
    suspend fun generateToken(policies: List<String>, ttl: String = "1h"): String? = withContext(Dispatchers.IO) {
        try {
            val response = vault.auth().createToken(
                policies.toTypedArray(),
                null,
                null,
                null,
                ttl,
                0,
                null,
                null
            )
            response.authClientToken
        } catch (e: VaultException) {
            println("Failed to generate token from HashiCorp Vault: ${e.message}")
            null
        }
    }
}

/**
 * Database credential from HashiCorp Vault
 */
data class DatabaseCredential(
    val username: String,
    val password: String,
    val leaseDuration: Long
)