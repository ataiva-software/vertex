package com.ataiva.eden.database

/**
 * Migration manager interface
 */
interface MigrationManager {
    suspend fun migrate(): List<MigrationResult>
    suspend fun getMigrationStatus(): List<MigrationInfo>
    suspend fun validateMigrations(): Boolean
    suspend fun rollback(targetVersion: String): Boolean
    suspend fun repair(): Boolean
}

/**
 * Migration result
 */
data class MigrationResult(
    val version: String,
    val description: String,
    val success: Boolean,
    val executionTime: Long,
    val error: String? = null
)

/**
 * Migration information
 */
data class MigrationInfo(
    val version: String,
    val description: String,
    val type: MigrationType,
    val state: MigrationState,
    val installedOn: String?,
    val executionTime: Long?,
    val checksum: String?
)

/**
 * Migration type
 */
enum class MigrationType {
    SQL,
    KOTLIN,
    BASELINE
}

/**
 * Migration state
 */
enum class MigrationState {
    PENDING,
    SUCCESS,
    FAILED,
    IGNORED,
    DELETED,
    SUPERSEDED
}

/**
 * Migration script interface
 */
interface Migration {
    val version: String
    val description: String
    val type: MigrationType
    suspend fun migrate(connection: DatabaseConnection): Boolean
    suspend fun rollback(connection: DatabaseConnection): Boolean
}

/**
 * SQL migration implementation
 */
class SqlMigration(
    override val version: String,
    override val description: String,
    private val upScript: String,
    private val downScript: String? = null
) : Migration {
    
    override val type: MigrationType = MigrationType.SQL
    
    override suspend fun migrate(connection: DatabaseConnection): Boolean {
        return try {
            connection.execute(upScript)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun rollback(connection: DatabaseConnection): Boolean {
        return if (downScript != null) {
            try {
                connection.execute(downScript)
                true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }
}

/**
 * Kotlin migration implementation
 */
abstract class KotlinMigration(
    override val version: String,
    override val description: String
) : Migration {
    
    override val type: MigrationType = MigrationType.KOTLIN
    
    abstract override suspend fun migrate(connection: DatabaseConnection): Boolean
    
    override suspend fun rollback(connection: DatabaseConnection): Boolean {
        // Default implementation - override if rollback is supported
        return false
    }
}

/**
 * Default migration manager implementation
 */
class DefaultMigrationManager(
    private val connection: DatabaseConnection,
    private val migrations: List<Migration> = emptyList()
) : MigrationManager {
    
    override suspend fun migrate(): List<MigrationResult> {
        val results = mutableListOf<MigrationResult>()
        
        // Ensure migration tracking table exists
        createMigrationTable()
        
        // Get applied migrations
        val appliedMigrations = getAppliedMigrations()
        
        // Execute pending migrations
        for (migration in migrations.sortedBy { it.version }) {
            if (!appliedMigrations.contains(migration.version)) {
                val success = try {
                    migration.migrate(connection)
                } catch (e: Exception) {
                    false
                }
                val executionTime = 0L // Simplified for cross-platform compatibility
                
                if (success) {
                    recordMigration(migration, executionTime)
                }
                
                results.add(
                    MigrationResult(
                        version = migration.version,
                        description = migration.description,
                        success = success,
                        executionTime = executionTime,
                        error = if (!success) "Migration failed" else null
                    )
                )
            }
        }
        
        return results
    }
    
    override suspend fun getMigrationStatus(): List<MigrationInfo> {
        val appliedMigrations = getAppliedMigrationInfo()
        val allMigrations = migrations.map { migration ->
            val applied = appliedMigrations.find { it.version == migration.version }
            MigrationInfo(
                version = migration.version,
                description = migration.description,
                type = migration.type,
                state = if (applied != null) MigrationState.SUCCESS else MigrationState.PENDING,
                installedOn = applied?.installedOn,
                executionTime = applied?.executionTime,
                checksum = applied?.checksum
            )
        }
        
        return allMigrations.sortedBy { it.version }
    }
    
    override suspend fun validateMigrations(): Boolean {
        return try {
            // Validate that all migrations are consistent
            val status = getMigrationStatus()
            status.none { it.state == MigrationState.FAILED }
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun rollback(targetVersion: String): Boolean {
        return try {
            val appliedMigrations = getAppliedMigrations().sorted().reversed()
            
            for (version in appliedMigrations) {
                if (version <= targetVersion) break
                
                val migration = migrations.find { it.version == version }
                if (migration != null) {
                    val success = migration.rollback(connection)
                    if (success) {
                        removeMigrationRecord(version)
                    } else {
                        return false
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun repair(): Boolean {
        return try {
            // Repair migration tracking table
            createMigrationTable()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun createMigrationTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS migration_history (
                version VARCHAR(50) PRIMARY KEY,
                description VARCHAR(200),
                type VARCHAR(20),
                installed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                execution_time BIGINT,
                checksum VARCHAR(32)
            )
        """.trimIndent()
        
        connection.execute(sql)
    }
    
    private suspend fun getAppliedMigrations(): Set<String> {
        val sql = "SELECT version FROM migration_history ORDER BY version"
        return try {
            connection.queryForList(sql) { row ->
                row.getString("version") ?: ""
            }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    private suspend fun getAppliedMigrationInfo(): List<AppliedMigrationInfo> {
        val sql = "SELECT version, description, type, installed_on, execution_time, checksum FROM migration_history ORDER BY version"
        return try {
            connection.queryForList(sql) { row ->
                AppliedMigrationInfo(
                    version = row.getString("version") ?: "",
                    description = row.getString("description") ?: "",
                    type = row.getString("type") ?: "",
                    installedOn = null, // Would parse timestamp in real implementation
                    executionTime = row.getLong("execution_time"),
                    checksum = row.getString("checksum")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private suspend fun recordMigration(migration: Migration, executionTime: Long) {
        val sql = """
            INSERT INTO migration_history (version, description, type, execution_time, checksum)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        
        connection.execute(
            sql,
            mapOf(
                "version" to migration.version,
                "description" to migration.description,
                "type" to migration.type.name,
                "execution_time" to executionTime,
                "checksum" to calculateChecksum(migration)
            )
        )
    }
    
    private suspend fun removeMigrationRecord(version: String) {
        val sql = "DELETE FROM migration_history WHERE version = ?"
        connection.execute(sql, mapOf("version" to version))
    }
    
    private fun calculateChecksum(migration: Migration): String {
        // Simple checksum calculation - would use proper hashing in real implementation
        return "${migration.version}-${migration.description}".hashCode().toString()
    }
}

/**
 * Applied migration information
 */
private data class AppliedMigrationInfo(
    val version: String,
    val description: String,
    val type: String,
    val installedOn: String?,
    val executionTime: Long?,
    val checksum: String?
)

/**
 * Migration builder for creating migrations programmatically
 */
class MigrationBuilder {
    private val migrations = mutableListOf<Migration>()
    
    fun sql(version: String, description: String, upScript: String, downScript: String? = null): MigrationBuilder {
        migrations.add(SqlMigration(version, description, upScript, downScript))
        return this
    }
    
    fun kotlin(migration: KotlinMigration): MigrationBuilder {
        migrations.add(migration)
        return this
    }
    
    fun build(): List<Migration> {
        return migrations.toList()
    }
}

/**
 * Migration factory
 */
object MigrationFactory {
    fun createManager(connection: DatabaseConnection, migrations: List<Migration> = emptyList()): MigrationManager {
        return DefaultMigrationManager(connection, migrations)
    }
    
    fun builder(): MigrationBuilder {
        return MigrationBuilder()
    }
}