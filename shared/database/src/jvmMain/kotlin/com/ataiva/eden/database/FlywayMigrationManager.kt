package com.ataiva.eden.database

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationInfo
import org.flywaydb.core.api.MigrationState
import org.flywaydb.core.api.configuration.FluentConfiguration

/**
 * Flyway-based migration manager implementation
 */
class FlywayMigrationManager(
    private val config: DatabaseConfig
) : MigrationManager {
    
    private val flyway: Flyway by lazy {
        createFlyway()
    }

    override suspend fun migrate(): List<String> {
        return try {
            val result = flyway.migrate()
            result.migrations.map { migration ->
                "Applied migration: ${migration.version} - ${migration.description}"
            }
        } catch (e: Exception) {
            throw RuntimeException("Migration failed: ${e.message}", e)
        }
    }

    override suspend fun rollback(version: String): List<String> {
        return try {
            // Flyway Community Edition doesn't support rollback
            // This would require Flyway Teams/Enterprise
            throw UnsupportedOperationException(
                "Rollback is not supported in Flyway Community Edition. " +
                "Consider using Flyway Teams/Enterprise or implement custom rollback logic."
            )
        } catch (e: Exception) {
            throw RuntimeException("Rollback failed: ${e.message}", e)
        }
    }

    override suspend fun getStatus(): List<MigrationStatus> {
        return try {
            val info = flyway.info()
            info.all().map { migrationInfo ->
                MigrationStatus(
                    version = migrationInfo.version?.version ?: "unknown",
                    description = migrationInfo.description ?: "",
                    applied = migrationInfo.state == MigrationState.SUCCESS,
                    appliedAt = migrationInfo.installedOn?.toInstant()?.toKotlinInstant()
                )
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to get migration status: ${e.message}", e)
        }
    }

    override suspend fun validate(): Boolean {
        return try {
            flyway.validate()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun createFlyway(): Flyway {
        val configuration = FluentConfiguration()
            .dataSource(config.url, config.username, config.password)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .baselineDescription("Initial baseline")
            .validateOnMigrate(true)
            .cleanDisabled(false) // Enable for development, disable in production
            .group(true)
            .mixed(true)
            .outOfOrder(false)
            .ignoreMissingMigrations(false)
            .ignoreIgnoredMigrations(false)
            .ignorePendingMigrations(false)
            .ignoreFutureMigrations(false)
        
        // Set schema if specified
        config.schema?.let { schema ->
            configuration.schemas(schema)
        }
        
        return configuration.load()
    }
    
    /**
     * Get detailed migration information
     */
    fun getDetailedInfo(): List<MigrationInfo> {
        return flyway.info().all().toList()
    }
    
    /**
     * Clean database (removes all objects)
     * WARNING: This will delete all data!
     */
    suspend fun clean(): Boolean {
        return try {
            flyway.clean()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Repair migration schema history table
     */
    suspend fun repair(): Boolean {
        return try {
            flyway.repair()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get current database version
     */
    fun getCurrentVersion(): String? {
        return flyway.info().current()?.version?.version
    }
    
    /**
     * Check if database is up to date
     */
    fun isUpToDate(): Boolean {
        val info = flyway.info()
        return info.pending().isEmpty()
    }
    
    /**
     * Get pending migrations
     */
    fun getPendingMigrations(): List<MigrationInfo> {
        return flyway.info().pending().toList()
    }
    
    /**
     * Get applied migrations
     */
    fun getAppliedMigrations(): List<MigrationInfo> {
        return flyway.info().applied().toList()
    }
}

/**
 * Custom migration implementation for complex migrations
 */
abstract class CustomMigration : Migration {
    
    /**
     * Execute SQL script from resources
     */
    protected suspend fun executeSqlScript(
        connection: DatabaseConnection,
        scriptPath: String
    ) {
        val sqlScript = this::class.java.classLoader
            .getResourceAsStream(scriptPath)
            ?.bufferedReader()
            ?.readText()
            ?: throw IllegalArgumentException("SQL script not found: $scriptPath")
        
        // Split by semicolon and execute each statement
        sqlScript.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { statement ->
                connection.execute(statement)
            }
    }
    
    /**
     * Check if table exists
     */
    protected suspend fun tableExists(
        connection: DatabaseConnection,
        tableName: String
    ): Boolean {
        return connection.queryOne(
            """
            SELECT EXISTS (
                SELECT FROM information_schema.tables 
                WHERE table_schema = 'public' 
                AND table_name = ?
            ) as exists
            """.trimIndent(),
            mapOf("tableName" to tableName)
        ) { row ->
            row.getBoolean("exists") ?: false
        } ?: false
    }
    
    /**
     * Check if column exists in table
     */
    protected suspend fun columnExists(
        connection: DatabaseConnection,
        tableName: String,
        columnName: String
    ): Boolean {
        return connection.queryOne(
            """
            SELECT EXISTS (
                SELECT FROM information_schema.columns 
                WHERE table_schema = 'public' 
                AND table_name = ? 
                AND column_name = ?
            ) as exists
            """.trimIndent(),
            mapOf("tableName" to tableName, "columnName" to columnName)
        ) { row ->
            row.getBoolean("exists") ?: false
        } ?: false
    }
    
    /**
     * Check if index exists
     */
    protected suspend fun indexExists(
        connection: DatabaseConnection,
        indexName: String
    ): Boolean {
        return connection.queryOne(
            """
            SELECT EXISTS (
                SELECT FROM pg_indexes 
                WHERE schemaname = 'public' 
                AND indexname = ?
            ) as exists
            """.trimIndent(),
            mapOf("indexName" to indexName)
        ) { row ->
            row.getBoolean("exists") ?: false
        } ?: false
    }
}

/**
 * Example migration for creating initial tables
 */
class InitialMigration : CustomMigration() {
    override val version: String = "1.0.0"
    override val description: String = "Create initial tables"
    
    override suspend fun up(connection: DatabaseConnection) {
        // Create users table
        connection.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                email VARCHAR(255) UNIQUE NOT NULL,
                password_hash VARCHAR(255),
                mfa_secret VARCHAR(255),
                first_name VARCHAR(100) DEFAULT '',
                last_name VARCHAR(100) DEFAULT '',
                display_name VARCHAR(200) DEFAULT '',
                avatar_url TEXT,
                timezone VARCHAR(50) DEFAULT 'UTC',
                locale VARCHAR(10) DEFAULT 'en',
                preferences JSONB DEFAULT '{}',
                is_active BOOLEAN DEFAULT true,
                email_verified BOOLEAN DEFAULT false,
                last_login_at TIMESTAMPTZ,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                updated_at TIMESTAMPTZ DEFAULT NOW()
            )
        """.trimIndent())
        
        // Create organizations table
        connection.execute("""
            CREATE TABLE IF NOT EXISTS organizations (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                name VARCHAR(255) NOT NULL,
                slug VARCHAR(100) UNIQUE NOT NULL,
                description TEXT DEFAULT '',
                settings JSONB DEFAULT '{}',
                plan VARCHAR(50) DEFAULT 'FREE',
                is_active BOOLEAN DEFAULT true,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                updated_at TIMESTAMPTZ DEFAULT NOW()
            )
        """.trimIndent())
        
        // Create organization_memberships table
        connection.execute("""
            CREATE TABLE IF NOT EXISTS organization_memberships (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
                role VARCHAR(50) NOT NULL,
                permissions JSONB DEFAULT '[]',
                is_active BOOLEAN DEFAULT true,
                invited_by UUID REFERENCES users(id),
                invited_at TIMESTAMPTZ,
                joined_at TIMESTAMPTZ,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                updated_at TIMESTAMPTZ DEFAULT NOW(),
                UNIQUE(user_id, organization_id)
            )
        """.trimIndent())
        
        // Create user_sessions table
        connection.execute("""
            CREATE TABLE IF NOT EXISTS user_sessions (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                token VARCHAR(500) NOT NULL,
                refresh_token VARCHAR(500),
                expires_at TIMESTAMPTZ NOT NULL,
                ip_address INET,
                user_agent TEXT,
                is_active BOOLEAN DEFAULT true,
                created_at TIMESTAMPTZ DEFAULT NOW()
            )
        """.trimIndent())
        
        // Create indexes
        connection.execute("CREATE INDEX IF NOT EXISTS idx_users_email ON users(email)")
        connection.execute("CREATE INDEX IF NOT EXISTS idx_users_active ON users(is_active)")
        connection.execute("CREATE INDEX IF NOT EXISTS idx_organizations_slug ON organizations(slug)")
        connection.execute("CREATE INDEX IF NOT EXISTS idx_org_memberships_user ON organization_memberships(user_id)")
        connection.execute("CREATE INDEX IF NOT EXISTS idx_org_memberships_org ON organization_memberships(organization_id)")
        connection.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user ON user_sessions(user_id)")
        connection.execute("CREATE INDEX IF NOT EXISTS idx_sessions_token ON user_sessions(token)")
        connection.execute("CREATE INDEX IF NOT EXISTS idx_sessions_expires ON user_sessions(expires_at)")
    }
    
    override suspend fun down(connection: DatabaseConnection) {
        connection.execute("DROP TABLE IF EXISTS user_sessions CASCADE")
        connection.execute("DROP TABLE IF EXISTS organization_memberships CASCADE")
        connection.execute("DROP TABLE IF EXISTS organizations CASCADE")
        connection.execute("DROP TABLE IF EXISTS users CASCADE")
    }
}