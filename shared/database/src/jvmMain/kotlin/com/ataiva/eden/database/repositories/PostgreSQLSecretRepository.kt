package com.ataiva.eden.database.repositories

import com.ataiva.eden.database.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

/**
 * PostgreSQL implementation of SecretRepository
 */
class PostgreSQLSecretRepository(
    private val database: DatabaseConnection
) : SecretRepository {

    override suspend fun findById(id: String): Secret? {
        return database.queryOne(
            """
            SELECT id, name, encrypted_value, encryption_key_id, secret_type, description, 
                   user_id, organization_id, version, is_active, created_at, updated_at
            FROM secrets 
            WHERE id = ? AND is_active = true
            """.trimIndent(),
            mapOf("id" to id)
        ) { row -> mapRowToSecret(row) }
    }

    override suspend fun findAll(): List<Secret> {
        return database.query(
            """
            SELECT id, name, encrypted_value, encryption_key_id, secret_type, description, 
                   user_id, organization_id, version, is_active, created_at, updated_at
            FROM secrets 
            WHERE is_active = true
            ORDER BY created_at DESC
            """.trimIndent()
        ) { row -> mapRowToSecret(row) }
    }

    override suspend fun findAll(offset: Int, limit: Int): Page<Secret> {
        val secrets = database.query(
            """
            SELECT id, name, encrypted_value, encryption_key_id, secret_type, description, 
                   user_id, organization_id, version, is_active, created_at, updated_at
            FROM secrets 
            WHERE is_active = true
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            mapOf("limit" to limit, "offset" to offset)
        ) { row -> mapRowToSecret(row) }

        val totalCount = count()
        val totalPages = ((totalCount + limit - 1) / limit).toInt()
        val currentPage = (offset / limit) + 1

        return Page(
            content = secrets,
            totalElements = totalCount,
            totalPages = totalPages,
            page = currentPage,
            size = limit,
            hasNext = currentPage < totalPages,
            hasPrevious = currentPage > 1
        )
    }

    override suspend fun save(entity: Secret): Secret {
        val now = Clock.System.now()
        val secretToSave = entity.copy(updatedAt = now)

        val exists = existsById(entity.id)
        
        if (exists) {
            database.execute(
                """
                UPDATE secrets 
                SET name = ?, encrypted_value = ?, encryption_key_id = ?, secret_type = ?, 
                    description = ?, organization_id = ?, version = ?, is_active = ?, updated_at = ?
                WHERE id = ?
                """.trimIndent(),
                mapOf(
                    "name" to secretToSave.name,
                    "encrypted_value" to secretToSave.encryptedValue,
                    "encryption_key_id" to secretToSave.encryptionKeyId,
                    "secret_type" to secretToSave.secretType,
                    "description" to secretToSave.description,
                    "organization_id" to secretToSave.organizationId,
                    "version" to secretToSave.version,
                    "is_active" to secretToSave.isActive,
                    "updated_at" to secretToSave.updatedAt.toString(),
                    "id" to secretToSave.id
                )
            )
        } else {
            database.execute(
                """
                INSERT INTO secrets (id, name, encrypted_value, encryption_key_id, secret_type, 
                                   description, user_id, organization_id, version, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                mapOf(
                    "id" to secretToSave.id,
                    "name" to secretToSave.name,
                    "encrypted_value" to secretToSave.encryptedValue,
                    "encryption_key_id" to secretToSave.encryptionKeyId,
                    "secret_type" to secretToSave.secretType,
                    "description" to secretToSave.description,
                    "user_id" to secretToSave.userId,
                    "organization_id" to secretToSave.organizationId,
                    "version" to secretToSave.version,
                    "is_active" to secretToSave.isActive,
                    "created_at" to secretToSave.createdAt.toString(),
                    "updated_at" to secretToSave.updatedAt.toString()
                )
            )
        }

        return secretToSave
    }

    override suspend fun saveAll(entities: List<Secret>): List<Secret> {
        return entities.map { save(it) }
    }

    override suspend fun deleteById(id: String): Boolean {
        val rowsAffected = database.execute(
            "UPDATE secrets SET is_active = false WHERE id = ?",
            mapOf("id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun delete(entity: Secret): Boolean {
        return deleteById(entity.id)
    }

    override suspend fun existsById(id: String): Boolean {
        return database.queryOne(
            "SELECT COUNT(*) as count FROM secrets WHERE id = ? AND is_active = true",
            mapOf("id" to id)
        ) { row -> (row.getLong("count") ?: 0) > 0 } ?: false
    }

    override suspend fun count(): Long {
        return database.queryOne(
            "SELECT COUNT(*) as count FROM secrets WHERE is_active = true"
        ) { row -> row.getLong("count") ?: 0 } ?: 0
    }

    // SecretRepository specific methods

    override suspend fun findByNameAndUser(name: String, userId: String): Secret? {
        return database.queryOne(
            """
            SELECT id, name, encrypted_value, encryption_key_id, secret_type, description, 
                   user_id, organization_id, version, is_active, created_at, updated_at
            FROM secrets 
            WHERE name = ? AND user_id = ? AND is_active = true
            ORDER BY version DESC
            LIMIT 1
            """.trimIndent(),
            mapOf("name" to name, "user_id" to userId)
        ) { row -> mapRowToSecret(row) }
    }

    override suspend fun findByUserId(userId: String): List<Secret> {
        return database.query(
            """
            SELECT id, name, encrypted_value, encryption_key_id, secret_type, description, 
                   user_id, organization_id, version, is_active, created_at, updated_at
            FROM secrets 
            WHERE user_id = ? AND is_active = true
            ORDER BY name, version DESC
            """.trimIndent(),
            mapOf("user_id" to userId)
        ) { row -> mapRowToSecret(row) }
    }

    override suspend fun findActiveByUserId(userId: String): List<Secret> {
        return findByUserId(userId) // Already filtered by is_active = true
    }

    override suspend fun findByNameUserAndVersion(name: String, userId: String, version: Int): Secret? {
        return database.queryOne(
            """
            SELECT id, name, encrypted_value, encryption_key_id, secret_type, description, 
                   user_id, organization_id, version, is_active, created_at, updated_at
            FROM secrets 
            WHERE name = ? AND user_id = ? AND version = ? AND is_active = true
            """.trimIndent(),
            mapOf("name" to name, "user_id" to userId, "version" to version)
        ) { row -> mapRowToSecret(row) }
    }

    override suspend fun findVersionsByNameAndUser(name: String, userId: String): List<Secret> {
        return database.query(
            """
            SELECT id, name, encrypted_value, encryption_key_id, secret_type, description, 
                   user_id, organization_id, version, is_active, created_at, updated_at
            FROM secrets 
            WHERE name = ? AND user_id = ? AND is_active = true
            ORDER BY version DESC
            """.trimIndent(),
            mapOf("name" to name, "user_id" to userId)
        ) { row -> mapRowToSecret(row) }
    }

    override suspend fun searchByName(userId: String, namePattern: String): List<Secret> {
        return database.query(
            """
            SELECT id, name, encrypted_value, encryption_key_id, secret_type, description, 
                   user_id, organization_id, version, is_active, created_at, updated_at
            FROM secrets 
            WHERE user_id = ? AND name ILIKE ? AND is_active = true
            ORDER BY name, version DESC
            """.trimIndent(),
            mapOf("user_id" to userId, "name_pattern" to "%$namePattern%")
        ) { row -> mapRowToSecret(row) }
    }

    override suspend fun findByTypeAndUser(secretType: String, userId: String): List<Secret> {
        return database.query(
            """
            SELECT id, name, encrypted_value, encryption_key_id, secret_type, description, 
                   user_id, organization_id, version, is_active, created_at, updated_at
            FROM secrets 
            WHERE secret_type = ? AND user_id = ? AND is_active = true
            ORDER BY name, version DESC
            """.trimIndent(),
            mapOf("secret_type" to secretType, "user_id" to userId)
        ) { row -> mapRowToSecret(row) }
    }

    override suspend fun updateStatus(id: String, isActive: Boolean): Boolean {
        val rowsAffected = database.execute(
            "UPDATE secrets SET is_active = ?, updated_at = ? WHERE id = ?",
            mapOf("is_active" to isActive, "updated_at" to Clock.System.now().toString(), "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun createNewVersion(secret: Secret): Secret {
        val maxVersion = database.queryOne(
            "SELECT COALESCE(MAX(version), 0) as max_version FROM secrets WHERE name = ? AND user_id = ?",
            mapOf("name" to secret.name, "user_id" to secret.userId)
        ) { row -> row.getInt("max_version") ?: 0 } ?: 0

        val newSecret = secret.copy(
            version = maxVersion + 1,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )

        return save(newSecret)
    }

    override suspend fun getSecretStats(userId: String): SecretStats {
        val totalSecrets = database.queryOne(
            "SELECT COUNT(*) as count FROM secrets WHERE user_id = ?",
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val activeSecrets = database.queryOne(
            "SELECT COUNT(*) as count FROM secrets WHERE user_id = ? AND is_active = true",
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val secretsByType = database.query(
            """
            SELECT secret_type, COUNT(*) as count 
            FROM secrets 
            WHERE user_id = ? AND is_active = true 
            GROUP BY secret_type
            """.trimIndent(),
            mapOf("user_id" to userId)
        ) { row ->
            (row.getString("secret_type") ?: "unknown") to (row.getLong("count") ?: 0)
        }.toMap()

        val recentlyCreated = database.queryOne(
            """
            SELECT COUNT(*) as count 
            FROM secrets 
            WHERE user_id = ? AND created_at > NOW() - INTERVAL '7 days'
            """.trimIndent(),
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val recentlyUpdated = database.queryOne(
            """
            SELECT COUNT(*) as count 
            FROM secrets 
            WHERE user_id = ? AND updated_at > NOW() - INTERVAL '7 days' AND updated_at != created_at
            """.trimIndent(),
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        return SecretStats(
            totalSecrets = totalSecrets,
            activeSecrets = activeSecrets,
            secretsByType = secretsByType,
            recentlyCreated = recentlyCreated,
            recentlyUpdated = recentlyUpdated
        )
    }

    private fun mapRowToSecret(row: ResultRow): Secret {
        return Secret(
            id = row.getString("id") ?: "",
            name = row.getString("name") ?: "",
            encryptedValue = row.getString("encrypted_value") ?: "",
            encryptionKeyId = row.getString("encryption_key_id") ?: "",
            secretType = row.getString("secret_type") ?: "generic",
            description = row.getString("description"),
            userId = row.getString("user_id") ?: "",
            organizationId = row.getString("organization_id"),
            version = row.getInt("version") ?: 1,
            isActive = row.getBoolean("is_active") ?: true,
            createdAt = row.getTimestamp("created_at") ?: Clock.System.now(),
            updatedAt = row.getTimestamp("updated_at") ?: Clock.System.now()
        )
    }
}

/**
 * PostgreSQL implementation of SecretAccessLogRepository
 */
class PostgreSQLSecretAccessLogRepository(
    private val database: DatabaseConnection
) : SecretAccessLogRepository {

    override suspend fun findById(id: String): SecretAccessLog? {
        return database.queryOne(
            """
            SELECT id, secret_id, user_id, action, ip_address, user_agent, created_at
            FROM secret_access_logs 
            WHERE id = ?
            """.trimIndent(),
            mapOf("id" to id)
        ) { row -> mapRowToSecretAccessLog(row) }
    }

    override suspend fun findAll(): List<SecretAccessLog> {
        return database.query(
            """
            SELECT id, secret_id, user_id, action, ip_address, user_agent, created_at
            FROM secret_access_logs 
            ORDER BY created_at DESC
            """.trimIndent()
        ) { row -> mapRowToSecretAccessLog(row) }
    }

    override suspend fun findAll(offset: Int, limit: Int): Page<SecretAccessLog> {
        val logs = database.query(
            """
            SELECT id, secret_id, user_id, action, ip_address, user_agent, created_at
            FROM secret_access_logs 
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            mapOf("limit" to limit, "offset" to offset)
        ) { row -> mapRowToSecretAccessLog(row) }

        val totalCount = count()
        val totalPages = ((totalCount + limit - 1) / limit).toInt()
        val currentPage = (offset / limit) + 1

        return Page(
            content = logs,
            totalElements = totalCount,
            totalPages = totalPages,
            page = currentPage,
            size = limit,
            hasNext = currentPage < totalPages,
            hasPrevious = currentPage > 1
        )
    }

    override suspend fun save(entity: SecretAccessLog): SecretAccessLog {
        database.execute(
            """
            INSERT INTO secret_access_logs (id, secret_id, user_id, action, ip_address, user_agent, created_at)
            VALUES (?, ?, ?, ?, ?::inet, ?, ?)
            """.trimIndent(),
            mapOf(
                "id" to entity.id,
                "secret_id" to entity.secretId,
                "user_id" to entity.userId,
                "action" to entity.action,
                "ip_address" to entity.ipAddress,
                "user_agent" to entity.userAgent,
                "created_at" to entity.createdAt.toString()
            )
        )
        return entity
    }

    override suspend fun saveAll(entities: List<SecretAccessLog>): List<SecretAccessLog> {
        return entities.map { save(it) }
    }

    override suspend fun deleteById(id: String): Boolean {
        val rowsAffected = database.execute(
            "DELETE FROM secret_access_logs WHERE id = ?",
            mapOf("id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun delete(entity: SecretAccessLog): Boolean {
        return deleteById(entity.id)
    }

    override suspend fun existsById(id: String): Boolean {
        return database.queryOne(
            "SELECT COUNT(*) as count FROM secret_access_logs WHERE id = ?",
            mapOf("id" to id)
        ) { row -> (row.getLong("count") ?: 0) > 0 } ?: false
    }

    override suspend fun count(): Long {
        return database.queryOne(
            "SELECT COUNT(*) as count FROM secret_access_logs"
        ) { row -> row.getLong("count") ?: 0 } ?: 0
    }

    // SecretAccessLogRepository specific methods

    override suspend fun logAccess(
        secretId: String,
        userId: String,
        action: String,
        ipAddress: String?,
        userAgent: String?
    ): SecretAccessLog {
        val log = SecretAccessLog(
            id = java.util.UUID.randomUUID().toString(),
            secretId = secretId,
            userId = userId,
            action = action,
            ipAddress = ipAddress,
            userAgent = userAgent,
            createdAt = Clock.System.now()
        )
        return save(log)
    }

    override suspend fun findBySecretId(secretId: String): List<SecretAccessLog> {
        return database.query(
            """
            SELECT id, secret_id, user_id, action, ip_address, user_agent, created_at
            FROM secret_access_logs 
            WHERE secret_id = ?
            ORDER BY created_at DESC
            """.trimIndent(),
            mapOf("secret_id" to secretId)
        ) { row -> mapRowToSecretAccessLog(row) }
    }

    override suspend fun findByUserId(userId: String): List<SecretAccessLog> {
        return database.query(
            """
            SELECT id, secret_id, user_id, action, ip_address, user_agent, created_at
            FROM secret_access_logs 
            WHERE user_id = ?
            ORDER BY created_at DESC
            """.trimIndent(),
            mapOf("user_id" to userId)
        ) { row -> mapRowToSecretAccessLog(row) }
    }

    override suspend fun findByAction(action: String): List<SecretAccessLog> {
        return database.query(
            """
            SELECT id, secret_id, user_id, action, ip_address, user_agent, created_at
            FROM secret_access_logs 
            WHERE action = ?
            ORDER BY created_at DESC
            """.trimIndent(),
            mapOf("action" to action)
        ) { row -> mapRowToSecretAccessLog(row) }
    }

    override suspend fun findRecentAccess(limit: Int): List<SecretAccessLog> {
        return database.query(
            """
            SELECT id, secret_id, user_id, action, ip_address, user_agent, created_at
            FROM secret_access_logs 
            ORDER BY created_at DESC
            LIMIT ?
            """.trimIndent(),
            mapOf("limit" to limit)
        ) { row -> mapRowToSecretAccessLog(row) }
    }

    override suspend fun getAccessStats(secretId: String): AccessStats {
        val totalAccesses = database.queryOne(
            "SELECT COUNT(*) as count FROM secret_access_logs WHERE secret_id = ?",
            mapOf("secret_id" to secretId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val readAccesses = database.queryOne(
            "SELECT COUNT(*) as count FROM secret_access_logs WHERE secret_id = ? AND action = 'read'",
            mapOf("secret_id" to secretId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val writeAccesses = database.queryOne(
            "SELECT COUNT(*) as count FROM secret_access_logs WHERE secret_id = ? AND action = 'write'",
            mapOf("secret_id" to secretId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val deleteAccesses = database.queryOne(
            "SELECT COUNT(*) as count FROM secret_access_logs WHERE secret_id = ? AND action = 'delete'",
            mapOf("secret_id" to secretId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val uniqueUsers = database.queryOne(
            "SELECT COUNT(DISTINCT user_id) as count FROM secret_access_logs WHERE secret_id = ?",
            mapOf("secret_id" to secretId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val recentAccesses = database.queryOne(
            """
            SELECT COUNT(*) as count 
            FROM secret_access_logs 
            WHERE secret_id = ? AND created_at > NOW() - INTERVAL '24 hours'
            """.trimIndent(),
            mapOf("secret_id" to secretId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        return AccessStats(
            totalAccesses = totalAccesses,
            readAccesses = readAccesses,
            writeAccesses = writeAccesses,
            deleteAccesses = deleteAccesses,
            uniqueUsers = uniqueUsers,
            recentAccesses = recentAccesses
        )
    }

    private fun mapRowToSecretAccessLog(row: ResultRow): SecretAccessLog {
        return SecretAccessLog(
            id = row.getString("id") ?: "",
            secretId = row.getString("secret_id") ?: "",
            userId = row.getString("user_id") ?: "",
            action = row.getString("action") ?: "",
            ipAddress = row.getString("ip_address"),
            userAgent = row.getString("user_agent"),
            createdAt = row.getTimestamp("created_at") ?: Clock.System.now()
        )
    }
}