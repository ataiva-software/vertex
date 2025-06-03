package com.ataiva.eden.integration.database

import com.ataiva.eden.database.*
import com.ataiva.eden.testing.fixtures.DatabaseTestFixtures
import com.ataiva.eden.testing.fixtures.DatabaseTestUtils
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration tests for the new database schema implementation
 * Tests real database operations against the new schema defined in V2__core_business_schema.sql
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NewSchemaIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("eden_test")
            .withUsername("eden_test")
            .withPassword("test_password")
            .withInitScript("init-test-db.sql")

        private lateinit var database: DatabaseConnection
        private lateinit var migrationManager: MigrationManager

        @BeforeAll
        @JvmStatic
        fun setupDatabase() {
            postgres.start()
            
            val config = DatabaseConfig(
                url = postgres.jdbcUrl,
                username = postgres.username,
                password = postgres.password,
                driverClassName = "org.postgresql.Driver",
                maxPoolSize = 5,
                minIdleConnections = 1
            )
            
            database = PostgreSQLDatabaseImpl(config)
            migrationManager = FlywayMigrationManager(config)
        }

        @AfterAll
        @JvmStatic
        fun tearDownDatabase() = runBlocking {
            database.close()
            postgres.stop()
        }
    }

    @BeforeEach
    fun setupTestData() = runBlocking {
        // Clean up any existing test data
        DatabaseTestUtils.CLEANUP_STATEMENTS.forEach { statement ->
            try {
                database.execute(statement)
            } catch (e: Exception) {
                // Ignore cleanup errors for non-existent data
            }
        }
    }

    @Test
    @Order(1)
    fun `should run database migrations successfully`() = runBlocking {
        // When
        val appliedMigrations = migrationManager.migrate()
        
        // Then
        assertTrue(appliedMigrations.isNotEmpty(), "Should have applied migrations")
        assertTrue(migrationManager.validate(), "Migrations should be valid")
        
        val status = migrationManager.getStatus()
        val successfulMigrations = status.filter { it.applied }
        assertTrue(successfulMigrations.isNotEmpty(), "Should have successful migrations")
        
        println("Applied migrations: ${appliedMigrations.joinToString(", ")}")
    }

    @Test
    @Order(2)
    fun `should verify all required tables exist`() = runBlocking {
        // Given
        val expectedTables = listOf(
            "users", "user_sessions", "organizations", "secrets", "secret_access_logs",
            "workflows", "workflow_executions", "workflow_steps",
            "tasks", "task_executions", "system_events", "audit_logs"
        )
        
        // When & Then
        expectedTables.forEach { tableName ->
            val exists = database.queryOne(
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
            }
            
            assertTrue(exists == true, "Table '$tableName' should exist")
        }
    }

    @Test
    @Order(3)
    fun `should insert and retrieve users successfully`() = runBlocking {
        // Given
        val testUsers = DatabaseTestFixtures.createTestUsers()
        
        // When - Insert users
        testUsers.forEach { user ->
            database.execute(
                """
                INSERT INTO users (id, email, password_hash, full_name, is_active, is_verified, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                mapOf(
                    "id" to user.id,
                    "email" to user.email,
                    "password_hash" to user.passwordHash,
                    "full_name" to user.fullName,
                    "is_active" to user.isActive,
                    "is_verified" to user.isVerified,
                    "created_at" to user.createdAt.toString(),
                    "updated_at" to user.updatedAt.toString()
                )
            )
        }
        
        // Then - Retrieve and verify users
        val retrievedUsers = database.query(
            "SELECT id, email, full_name, is_active, is_verified FROM users WHERE id LIKE '0%' ORDER BY email"
        ) { row ->
            mapOf(
                "id" to row.getString("id"),
                "email" to row.getString("email"),
                "full_name" to row.getString("full_name"),
                "is_active" to row.getBoolean("is_active"),
                "is_verified" to row.getBoolean("is_verified")
            )
        }
        
        assertEquals(testUsers.size, retrievedUsers.size, "Should retrieve all inserted users")
        
        val adminUser = retrievedUsers.find { it["email"] == "admin@eden.local" }
        assertNotNull(adminUser, "Admin user should exist")
        assertEquals(true, adminUser!!["is_active"], "Admin user should be active")
        assertEquals(true, adminUser["is_verified"], "Admin user should be verified")
    }

    @Test
    @Order(4)
    fun `should insert and retrieve secrets with proper relationships`() = runBlocking {
        // Given
        val testSecrets = DatabaseTestFixtures.createTestSecrets()
        
        // When - Insert secrets
        testSecrets.forEach { secret ->
            database.execute(
                """
                INSERT INTO secrets (id, name, encrypted_value, encryption_key_id, secret_type, description, user_id, organization_id, version, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                mapOf(
                    "id" to secret.id,
                    "name" to secret.name,
                    "encrypted_value" to secret.encryptedValue,
                    "encryption_key_id" to secret.encryptionKeyId,
                    "secret_type" to secret.secretType,
                    "description" to secret.description,
                    "user_id" to secret.userId,
                    "organization_id" to secret.organizationId,
                    "version" to secret.version,
                    "is_active" to secret.isActive,
                    "created_at" to secret.createdAt.toString(),
                    "updated_at" to secret.updatedAt.toString()
                )
            )
        }
        
        // Then - Retrieve secrets with user information
        val secretsWithUsers = database.query(
            """
            SELECT s.id, s.name, s.secret_type, s.is_active, u.email as user_email
            FROM secrets s
            JOIN users u ON s.user_id = u.id
            WHERE s.id LIKE '1%'
            ORDER BY s.name
            """.trimIndent()
        ) { row ->
            mapOf(
                "id" to row.getString("id"),
                "name" to row.getString("name"),
                "secret_type" to row.getString("secret_type"),
                "is_active" to row.getBoolean("is_active"),
                "user_email" to row.getString("user_email")
            )
        }
        
        assertTrue(secretsWithUsers.isNotEmpty(), "Should retrieve secrets with user information")
        
        val databaseSecret = secretsWithUsers.find { it["name"] == "database-password" }
        assertNotNull(databaseSecret, "Database secret should exist")
        assertEquals("database", databaseSecret!!["secret_type"], "Should have correct secret type")
        assertEquals("admin@eden.local", databaseSecret["user_email"], "Should be owned by admin user")
    }

    @Test
    @Order(5)
    fun `should insert and retrieve workflows with complex JSON definitions`() = runBlocking {
        // Given
        val testWorkflows = DatabaseTestFixtures.createTestWorkflows()
        
        // When - Insert workflows
        testWorkflows.forEach { workflow ->
            database.execute(
                """
                INSERT INTO workflows (id, name, description, definition, user_id, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                """.trimIndent(),
                mapOf(
                    "id" to workflow.id,
                    "name" to workflow.name,
                    "description" to workflow.description,
                    "definition" to workflow.definition.toString(), // Convert to JSON string
                    "user_id" to workflow.userId,
                    "status" to workflow.status,
                    "version" to workflow.version,
                    "created_at" to workflow.createdAt.toString(),
                    "updated_at" to workflow.updatedAt.toString()
                )
            )
        }
        
        // Then - Retrieve workflows and verify JSON structure
        val retrievedWorkflows = database.query(
            """
            SELECT id, name, definition, status, 
                   definition->>'name' as def_name,
                   jsonb_array_length(definition->'steps') as step_count
            FROM workflows 
            WHERE id LIKE '2%'
            ORDER BY name
            """.trimIndent()
        ) { row ->
            mapOf(
                "id" to row.getString("id"),
                "name" to row.getString("name"),
                "status" to row.getString("status"),
                "def_name" to row.getString("def_name"),
                "step_count" to row.getInt("step_count")
            )
        }
        
        assertTrue(retrievedWorkflows.isNotEmpty(), "Should retrieve workflows")
        
        val deployWorkflow = retrievedWorkflows.find { it["name"] == "deploy-to-staging" }
        assertNotNull(deployWorkflow, "Deploy workflow should exist")
        assertEquals("deploy-to-staging", deployWorkflow!!["def_name"], "JSON definition should be accessible")
        assertTrue((deployWorkflow["step_count"] as Int?) ?: 0 > 0, "Should have workflow steps")
    }

    @Test
    @Order(6)
    fun `should handle task executions with different statuses`() = runBlocking {
        // Given
        val testTasks = DatabaseTestFixtures.createTestTasks()
        val testExecutions = DatabaseTestFixtures.createTestTaskExecutions()
        
        // When - Insert tasks first
        testTasks.forEach { task ->
            database.execute(
                """
                INSERT INTO tasks (id, name, description, task_type, configuration, schedule_cron, user_id, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                """.trimIndent(),
                mapOf(
                    "id" to task.id,
                    "name" to task.name,
                    "description" to task.description,
                    "task_type" to task.taskType,
                    "configuration" to task.configuration.toString(),
                    "schedule_cron" to task.scheduleCron,
                    "user_id" to task.userId,
                    "is_active" to task.isActive,
                    "created_at" to task.createdAt.toString(),
                    "updated_at" to task.updatedAt.toString()
                )
            )
        }
        
        // Then insert task executions
        testExecutions.forEach { execution ->
            database.execute(
                """
                INSERT INTO task_executions (id, task_id, status, priority, input_data, output_data, error_message, progress_percentage, queued_at, started_at, completed_at, duration_ms)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                mapOf(
                    "id" to execution.id,
                    "task_id" to execution.taskId,
                    "status" to execution.status,
                    "priority" to execution.priority,
                    "input_data" to execution.inputData?.toString(),
                    "output_data" to execution.outputData?.toString(),
                    "error_message" to execution.errorMessage,
                    "progress_percentage" to execution.progressPercentage,
                    "queued_at" to execution.queuedAt.toString(),
                    "started_at" to execution.startedAt?.toString(),
                    "completed_at" to execution.completedAt?.toString(),
                    "duration_ms" to execution.durationMs
                )
            )
        }
        
        // Then - Verify task execution statistics
        val executionStats = database.query(
            """
            SELECT status, COUNT(*) as count, AVG(priority) as avg_priority
            FROM task_executions 
            WHERE id LIKE '5%'
            GROUP BY status
            ORDER BY status
            """.trimIndent()
        ) { row ->
            mapOf(
                "status" to row.getString("status"),
                "count" to row.getLong("count"),
                "avg_priority" to row.getDouble("avg_priority")
            )
        }
        
        assertTrue(executionStats.isNotEmpty(), "Should have execution statistics")
        
        val completedStats = executionStats.find { it["status"] == "completed" }
        assertNotNull(completedStats, "Should have completed executions")
        assertTrue((completedStats!!["count"] as Long?) ?: 0 > 0, "Should have at least one completed execution")
    }

    @Test
    @Order(7)
    fun `should create audit trail for database operations`() = runBlocking {
        // Given
        val testAuditLogs = DatabaseTestFixtures.createTestAuditLogs()
        
        // When - Insert audit logs
        testAuditLogs.forEach { auditLog ->
            database.execute(
                """
                INSERT INTO audit_logs (id, user_id, organization_id, action, resource, resource_id, details, ip_address, user_agent, timestamp, severity)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::inet, ?, ?, ?)
                """.trimIndent(),
                mapOf(
                    "id" to auditLog.id,
                    "user_id" to auditLog.userId,
                    "organization_id" to auditLog.organizationId,
                    "action" to auditLog.action,
                    "resource" to auditLog.resource,
                    "resource_id" to auditLog.resourceId,
                    "details" to auditLog.details.toString(),
                    "ip_address" to auditLog.ipAddress,
                    "user_agent" to auditLog.userAgent,
                    "timestamp" to auditLog.timestamp.toString(),
                    "severity" to auditLog.severity
                )
            )
        }
        
        // Then - Query audit trail by user and action
        val auditTrail = database.query(
            """
            SELECT a.action, a.resource, a.severity, u.email as user_email, a.timestamp
            FROM audit_logs a
            JOIN users u ON a.user_id = u.id
            WHERE a.id LIKE '7%'
            ORDER BY a.timestamp DESC
            """.trimIndent()
        ) { row ->
            mapOf(
                "action" to row.getString("action"),
                "resource" to row.getString("resource"),
                "severity" to row.getString("severity"),
                "user_email" to row.getString("user_email"),
                "timestamp" to row.getTimestamp("timestamp")
            )
        }
        
        assertTrue(auditTrail.isNotEmpty(), "Should have audit trail entries")
        
        val secretActions = auditTrail.filter { it["resource"] == "secret" }
        assertTrue(secretActions.isNotEmpty(), "Should have secret-related audit entries")
        
        val createActions = auditTrail.filter { it["action"] == "CREATE" }
        assertTrue(createActions.isNotEmpty(), "Should have CREATE audit entries")
    }

    @Test
    @Order(8)
    fun `should verify database indexes are working efficiently`() = runBlocking {
        // When - Query index usage for performance-critical tables
        val indexUsage = database.query(
            """
            SELECT schemaname, tablename, indexname, idx_tup_read, idx_tup_fetch
            FROM pg_stat_user_indexes 
            WHERE schemaname = 'public' 
            AND tablename IN ('users', 'secrets', 'workflows', 'tasks')
            ORDER BY tablename, indexname
            """.trimIndent()
        ) { row ->
            mapOf(
                "table" to row.getString("tablename"),
                "index" to row.getString("indexname"),
                "reads" to row.getLong("idx_tup_read"),
                "fetches" to row.getLong("idx_tup_fetch")
            )
        }
        
        // Then - Verify key indexes exist
        val indexNames = indexUsage.map { it["index"] as String }
        
        assertTrue(indexNames.any { it.contains("users_email") }, "Should have users email index")
        assertTrue(indexNames.any { it.contains("secrets_user_id") }, "Should have secrets user_id index")
        assertTrue(indexNames.any { it.contains("workflows_user_id") }, "Should have workflows user_id index")
        assertTrue(indexNames.any { it.contains("tasks_user_id") }, "Should have tasks user_id index")
        
        println("Found indexes: ${indexNames.joinToString(", ")}")
    }

    @Test
    @Order(9)
    fun `should handle database transactions correctly`() = runBlocking {
        // Given
        val testUserId = "test-transaction-user"
        val testSecretId = "test-transaction-secret"
        
        // When - Perform transaction that should succeed
        database.transaction { conn ->
            // Insert user
            conn.execute(
                "INSERT INTO users (id, email, password_hash, full_name, is_active, is_verified) VALUES (?, ?, ?, ?, ?, ?)",
                mapOf(
                    "id" to testUserId,
                    "email" to "transaction@test.local",
                    "password_hash" to "test-hash",
                    "full_name" to "Transaction Test User",
                    "is_active" to true,
                    "is_verified" to true
                )
            )
            
            // Insert secret for that user
            conn.execute(
                "INSERT INTO secrets (id, name, encrypted_value, encryption_key_id, user_id) VALUES (?, ?, ?, ?, ?)",
                mapOf(
                    "id" to testSecretId,
                    "name" to "transaction-secret",
                    "encrypted_value" to "encrypted-value",
                    "encryption_key_id" to "key-001",
                    "user_id" to testUserId
                )
            )
        }
        
        // Then - Verify both records exist
        val userExists = database.queryOne(
            "SELECT COUNT(*) as count FROM users WHERE id = ?",
            mapOf("id" to testUserId)
        ) { row -> row.getLong("count") }
        
        val secretExists = database.queryOne(
            "SELECT COUNT(*) as count FROM secrets WHERE id = ?",
            mapOf("id" to testSecretId)
        ) { row -> row.getLong("count") }
        
        assertEquals(1L, userExists, "User should exist after transaction")
        assertEquals(1L, secretExists, "Secret should exist after transaction")
        
        // Cleanup
        database.execute("DELETE FROM secrets WHERE id = ?", mapOf("id" to testSecretId))
        database.execute("DELETE FROM users WHERE id = ?", mapOf("id" to testUserId))
    }

    @Test
    @Order(10)
    fun `should validate foreign key constraints`() = runBlocking {
        // Given - Try to insert secret with non-existent user
        val invalidUserId = "non-existent-user"
        val secretId = "test-fk-secret"
        
        // When & Then - Should fail due to foreign key constraint
        assertThrows<Exception>("Should throw exception for invalid foreign key") {
            runBlocking {
                database.execute(
                    "INSERT INTO secrets (id, name, encrypted_value, encryption_key_id, user_id) VALUES (?, ?, ?, ?, ?)",
                    mapOf(
                        "id" to secretId,
                        "name" to "fk-test-secret",
                        "encrypted_value" to "encrypted-value",
                        "encryption_key_id" to "key-001",
                        "user_id" to invalidUserId
                    )
                )
            }
        }
        
        // Verify secret was not inserted
        val secretCount = database.queryOne(
            "SELECT COUNT(*) as count FROM secrets WHERE id = ?",
            mapOf("id" to secretId)
        ) { row -> row.getLong("count") }
        
        assertEquals(0L, secretCount, "Secret should not exist due to foreign key constraint")
    }
}