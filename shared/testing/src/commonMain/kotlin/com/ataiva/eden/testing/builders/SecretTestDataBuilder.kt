package com.ataiva.eden.testing.builders

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Test data builder for Secret entities
 */
data class Secret(
    val id: String,
    val name: String,
    val encryptedValue: String,
    val encryptionKeyId: String,
    val secretType: String,
    val description: String?,
    val userId: String,
    val organizationId: String?,
    val version: Int,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

class SecretTestDataBuilder {
    private var id: String = "secret-${generateRandomId()}"
    private var name: String = "test-secret"
    private var encryptedValue: String = "encrypted_test_value_placeholder"
    private var encryptionKeyId: String = "test-key-001"
    private var secretType: String = "generic"
    private var description: String? = "Test secret for development"
    private var userId: String = "user-${generateRandomId()}"
    private var organizationId: String? = null
    private var version: Int = 1
    private var isActive: Boolean = true
    private var createdAt: Instant = Clock.System.now()
    private var updatedAt: Instant = Clock.System.now()

    fun withId(id: String) = apply { this.id = id }
    fun withName(name: String) = apply { this.name = name }
    fun withEncryptedValue(encryptedValue: String) = apply { this.encryptedValue = encryptedValue }
    fun withEncryptionKeyId(keyId: String) = apply { this.encryptionKeyId = keyId }
    fun withSecretType(type: String) = apply { this.secretType = type }
    fun withDescription(description: String?) = apply { this.description = description }
    fun withUserId(userId: String) = apply { this.userId = userId }
    fun withOrganizationId(organizationId: String?) = apply { this.organizationId = organizationId }
    fun withVersion(version: Int) = apply { this.version = version }
    fun withIsActive(isActive: Boolean) = apply { this.isActive = isActive }
    fun withCreatedAt(createdAt: Instant) = apply { this.createdAt = createdAt }
    fun withUpdatedAt(updatedAt: Instant) = apply { this.updatedAt = updatedAt }

    fun build(): Secret {
        return Secret(
            id = id,
            name = name,
            encryptedValue = encryptedValue,
            encryptionKeyId = encryptionKeyId,
            secretType = secretType,
            description = description,
            userId = userId,
            organizationId = organizationId,
            version = version,
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun aSecret() = SecretTestDataBuilder()
        
        fun databaseSecret() = SecretTestDataBuilder()
            .withName("database-password")
            .withSecretType("database")
            .withDescription("Database connection password")
            
        fun apiKeySecret() = SecretTestDataBuilder()
            .withName("api-key-github")
            .withSecretType("api_token")
            .withDescription("GitHub API access token")
            
        fun certificateSecret() = SecretTestDataBuilder()
            .withName("ssl-certificate")
            .withSecretType("certificate")
            .withDescription("SSL certificate for production")

        private fun generateRandomId(): String {
            return (1..8).map { ('a'..'z').random() }.joinToString("")
        }
    }
}

/**
 * Test data builder for SecretAccessLog entities
 */
data class SecretAccessLog(
    val id: String,
    val secretId: String,
    val userId: String,
    val action: String,
    val ipAddress: String?,
    val userAgent: String?,
    val createdAt: Instant
)

class SecretAccessLogTestDataBuilder {
    private var id: String = "log-${generateRandomId()}"
    private var secretId: String = "secret-${generateRandomId()}"
    private var userId: String = "user-${generateRandomId()}"
    private var action: String = "read"
    private var ipAddress: String? = "192.168.1.100"
    private var userAgent: String? = "Eden CLI/1.0"
    private var createdAt: Instant = Clock.System.now()

    fun withId(id: String) = apply { this.id = id }
    fun withSecretId(secretId: String) = apply { this.secretId = secretId }
    fun withUserId(userId: String) = apply { this.userId = userId }
    fun withAction(action: String) = apply { this.action = action }
    fun withIpAddress(ipAddress: String?) = apply { this.ipAddress = ipAddress }
    fun withUserAgent(userAgent: String?) = apply { this.userAgent = userAgent }
    fun withCreatedAt(createdAt: Instant) = apply { this.createdAt = createdAt }

    fun build(): SecretAccessLog {
        return SecretAccessLog(
            id = id,
            secretId = secretId,
            userId = userId,
            action = action,
            ipAddress = ipAddress,
            userAgent = userAgent,
            createdAt = createdAt
        )
    }

    companion object {
        fun aSecretAccessLog() = SecretAccessLogTestDataBuilder()
        
        fun readAccessLog() = SecretAccessLogTestDataBuilder()
            .withAction("read")
            
        fun writeAccessLog() = SecretAccessLogTestDataBuilder()
            .withAction("write")
            
        fun deleteAccessLog() = SecretAccessLogTestDataBuilder()
            .withAction("delete")

        private fun generateRandomId(): String {
            return (1..8).map { ('a'..'z').random() }.joinToString("")
        }
    }
}