package com.ataiva.eden.database.repositories

import com.ataiva.eden.database.Repository
import kotlinx.datetime.Instant

/**
 * Secret entity for database operations
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

/**
 * Secret access log entity
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

/**
 * Repository interface for secret management operations
 */
interface SecretRepository : Repository<Secret, String> {
    
    /**
     * Find secret by name and user ID (latest version)
     */
    suspend fun findByNameAndUser(name: String, userId: String): Secret?
    
    /**
     * Find all secrets for a user
     */
    suspend fun findByUserId(userId: String): List<Secret>
    
    /**
     * Find all active secrets for a user
     */
    suspend fun findActiveByUserId(userId: String): List<Secret>
    
    /**
     * Find secret by name, user ID, and specific version
     */
    suspend fun findByNameUserAndVersion(name: String, userId: String, version: Int): Secret?
    
    /**
     * Find all versions of a secret
     */
    suspend fun findVersionsByNameAndUser(name: String, userId: String): List<Secret>
    
    /**
     * Search secrets by name pattern
     */
    suspend fun searchByName(userId: String, namePattern: String): List<Secret>
    
    /**
     * Find secrets by type
     */
    suspend fun findByTypeAndUser(secretType: String, userId: String): List<Secret>
    
    /**
     * Update secret status (activate/deactivate)
     */
    suspend fun updateStatus(id: String, isActive: Boolean): Boolean
    
    /**
     * Create new version of existing secret
     */
    suspend fun createNewVersion(secret: Secret): Secret
    
    /**
     * Get secret statistics for user
     */
    suspend fun getSecretStats(userId: String): SecretStats
}

/**
 * Repository interface for secret access logging
 */
interface SecretAccessLogRepository : Repository<SecretAccessLog, String> {
    
    /**
     * Log secret access
     */
    suspend fun logAccess(
        secretId: String,
        userId: String,
        action: String,
        ipAddress: String?,
        userAgent: String?
    ): SecretAccessLog
    
    /**
     * Find access logs for a secret
     */
    suspend fun findBySecretId(secretId: String): List<SecretAccessLog>
    
    /**
     * Find access logs for a user
     */
    suspend fun findByUserId(userId: String): List<SecretAccessLog>
    
    /**
     * Find access logs by action type
     */
    suspend fun findByAction(action: String): List<SecretAccessLog>
    
    /**
     * Find recent access logs
     */
    suspend fun findRecentAccess(limit: Int = 100): List<SecretAccessLog>
    
    /**
     * Get access statistics for a secret
     */
    suspend fun getAccessStats(secretId: String): AccessStats
}

/**
 * Secret statistics data class
 */
data class SecretStats(
    val totalSecrets: Long,
    val activeSecrets: Long,
    val secretsByType: Map<String, Long>,
    val recentlyCreated: Long,
    val recentlyUpdated: Long
)

/**
 * Access statistics data class
 */
data class AccessStats(
    val totalAccesses: Long,
    val readAccesses: Long,
    val writeAccesses: Long,
    val deleteAccesses: Long,
    val uniqueUsers: Long,
    val recentAccesses: Long
)