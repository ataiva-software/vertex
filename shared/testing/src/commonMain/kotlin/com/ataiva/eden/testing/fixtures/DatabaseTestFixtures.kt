package com.ataiva.eden.testing.fixtures

import com.ataiva.eden.testing.builders.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Database test fixtures providing comprehensive test data for the new schema
 */
object DatabaseTestFixtures {
    
    // Test user IDs for consistent references
    const val ADMIN_USER_ID = "00000000-0000-0000-0000-000000000001"
    const val DEVELOPER_USER_ID = "00000000-0000-0000-0000-000000000002"
    const val REGULAR_USER_ID = "00000000-0000-0000-0000-000000000003"
    
    // Test organization ID
    const val DEFAULT_ORG_ID = "00000000-0000-0000-0000-000000000001"
    
    // Test resource IDs
    const val TEST_SECRET_ID = "10000000-0000-0000-0000-000000000001"
    const val TEST_WORKFLOW_ID = "20000000-0000-0000-0000-000000000001"
    const val TEST_TASK_ID = "30000000-0000-0000-0000-000000000001"
    
    /**
     * Creates a complete test database state with all entities
     */
    fun createCompleteTestData(): DatabaseTestData {
        val users = createTestUsers()
        val secrets = createTestSecrets()
        val workflows = createTestWorkflows()
        val tasks = createTestTasks()
        val workflowExecutions = createTestWorkflowExecutions()
        val taskExecutions = createTestTaskExecutions()
        val systemEvents = createTestSystemEvents()
        val auditLogs = createTestAuditLogs()
        val secretAccessLogs = createTestSecretAccessLogs()
        
        return DatabaseTestData(
            users = users,
            secrets = secrets,
            workflows = workflows,
            tasks = tasks,
            workflowExecutions = workflowExecutions,
            taskExecutions = taskExecutions,
            systemEvents = systemEvents,
            auditLogs = auditLogs,
            secretAccessLogs = secretAccessLogs
        )
    }
    
    /**
     * Creates test users with different roles and states
     */
    fun createTestUsers(): List<User> {
        return listOf(
            User(
                id = ADMIN_USER_ID,
                email = "admin@eden.local",
                passwordHash = "\$2a\$10\$test.hash.for.admin.user",
                fullName = "Eden Administrator",
                isActive = true,
                isVerified = true,
                createdAt = DatabaseTestTimeFixtures.PAST_INSTANT,
                updatedAt = DatabaseTestTimeFixtures.FIXED_INSTANT
            ),
            User(
                id = DEVELOPER_USER_ID,
                email = "developer@eden.local",
                passwordHash = "\$2a\$10\$test.hash.for.developer.user",
                fullName = "Eden Developer",
                isActive = true,
                isVerified = true,
                createdAt = DatabaseTestTimeFixtures.PAST_INSTANT,
                updatedAt = DatabaseTestTimeFixtures.FIXED_INSTANT
            ),
            User(
                id = REGULAR_USER_ID,
                email = "user@eden.local",
                passwordHash = "\$2a\$10\$test.hash.for.regular.user",
                fullName = "Eden User",
                isActive = true,
                isVerified = false,
                createdAt = DatabaseTestTimeFixtures.FIXED_INSTANT,
                updatedAt = DatabaseTestTimeFixtures.FIXED_INSTANT
            )
        )
    }
    
    /**
     * Creates test secrets for different scenarios
     */
    fun createTestSecrets(): List<Secret> {
        return listOf(
            SecretTestDataBuilder.databaseSecret()
                .withId(TEST_SECRET_ID)
                .withUserId(ADMIN_USER_ID)
                .withOrganizationId(DEFAULT_ORG_ID)
                .build(),
            SecretTestDataBuilder.apiKeySecret()
                .withUserId(DEVELOPER_USER_ID)
                .withOrganizationId(DEFAULT_ORG_ID)
                .build(),
            SecretTestDataBuilder.certificateSecret()
                .withUserId(ADMIN_USER_ID)
                .withOrganizationId(DEFAULT_ORG_ID)
                .build(),
            SecretTestDataBuilder.aSecret()
                .withName("test-secret-inactive")
                .withUserId(REGULAR_USER_ID)
                .withIsActive(false)
                .build()
        )
    }
    
    /**
     * Creates test workflows for different use cases
     */
    fun createTestWorkflows(): List<Workflow> {
        return listOf(
            WorkflowTestDataBuilder.deploymentWorkflow()
                .withId(TEST_WORKFLOW_ID)
                .withUserId(ADMIN_USER_ID)
                .build(),
            WorkflowTestDataBuilder.backupWorkflow()
                .withUserId(DEVELOPER_USER_ID)
                .build(),
            WorkflowTestDataBuilder.testWorkflow()
                .withUserId(DEVELOPER_USER_ID)
                .build(),
            WorkflowTestDataBuilder.aWorkflow()
                .withName("paused-workflow")
                .withStatus("paused")
                .withUserId(REGULAR_USER_ID)
                .build()
        )
    }
    
    /**
     * Creates test tasks for different scenarios
     */
    fun createTestTasks(): List<Task> {
        return listOf(
            TaskTestDataBuilder.httpCheckTask()
                .withId(TEST_TASK_ID)
                .withUserId(ADMIN_USER_ID)
                .build(),
            TaskTestDataBuilder.fileCleanupTask()
                .withUserId(DEVELOPER_USER_ID)
                .build(),
            TaskTestDataBuilder.dataSyncTask()
                .withUserId(ADMIN_USER_ID)
                .build(),
            TaskTestDataBuilder.backupTask()
                .withUserId(ADMIN_USER_ID)
                .build(),
            TaskTestDataBuilder.aTask()
                .withName("inactive-task")
                .withIsActive(false)
                .withUserId(REGULAR_USER_ID)
                .build()
        )
    }
    
    /**
     * Creates test workflow executions in various states
     */
    fun createTestWorkflowExecutions(): List<WorkflowExecution> {
        return listOf(
            WorkflowExecutionTestDataBuilder.completedExecution()
                .withWorkflowId(TEST_WORKFLOW_ID)
                .withTriggeredBy(ADMIN_USER_ID)
                .build(),
            WorkflowExecutionTestDataBuilder.runningExecution()
                .withWorkflowId(TEST_WORKFLOW_ID)
                .withTriggeredBy(DEVELOPER_USER_ID)
                .build(),
            WorkflowExecutionTestDataBuilder.failedExecution()
                .withWorkflowId(TEST_WORKFLOW_ID)
                .withTriggeredBy(ADMIN_USER_ID)
                .build()
        )
    }
    
    /**
     * Creates test task executions in various states
     */
    fun createTestTaskExecutions(): List<TaskExecution> {
        return listOf(
            TaskExecutionTestDataBuilder.completedExecution()
                .withTaskId(TEST_TASK_ID)
                .build(),
            TaskExecutionTestDataBuilder.runningExecution()
                .withTaskId(TEST_TASK_ID)
                .build(),
            TaskExecutionTestDataBuilder.queuedExecution()
                .withTaskId(TEST_TASK_ID)
                .build(),
            TaskExecutionTestDataBuilder.failedExecution()
                .withTaskId(TEST_TASK_ID)
                .build(),
            TaskExecutionTestDataBuilder.highPriorityExecution()
                .withTaskId(TEST_TASK_ID)
                .build()
        )
    }
    
    /**
     * Creates test system events for monitoring
     */
    fun createTestSystemEvents(): List<SystemEvent> {
        return listOf(
            SystemEventTestDataBuilder.userLoginEvent()
                .withUserId(ADMIN_USER_ID)
                .build(),
            SystemEventTestDataBuilder.secretAccessedEvent()
                .withUserId(DEVELOPER_USER_ID)
                .build(),
            SystemEventTestDataBuilder.workflowFailedEvent()
                .withUserId(ADMIN_USER_ID)
                .build(),
            SystemEventTestDataBuilder.taskCompletedEvent()
                .withUserId(ADMIN_USER_ID)
                .build(),
            SystemEventTestDataBuilder.criticalErrorEvent()
                .build()
        )
    }
    
    /**
     * Creates test audit logs for compliance
     */
    fun createTestAuditLogs(): List<AuditLog> {
        return listOf(
            AuditLogTestDataBuilder.secretCreatedLog()
                .withUserId(ADMIN_USER_ID)
                .withOrganizationId(DEFAULT_ORG_ID)
                .withResourceId(TEST_SECRET_ID)
                .build(),
            AuditLogTestDataBuilder.workflowExecutedLog()
                .withUserId(DEVELOPER_USER_ID)
                .withOrganizationId(DEFAULT_ORG_ID)
                .withResourceId(TEST_WORKFLOW_ID)
                .build(),
            AuditLogTestDataBuilder.secretAccessedLog()
                .withUserId(ADMIN_USER_ID)
                .withOrganizationId(DEFAULT_ORG_ID)
                .withResourceId(TEST_SECRET_ID)
                .build()
        )
    }
    
    /**
     * Creates test secret access logs
     */
    fun createTestSecretAccessLogs(): List<SecretAccessLog> {
        return listOf(
            SecretAccessLogTestDataBuilder.readAccessLog()
                .withSecretId(TEST_SECRET_ID)
                .withUserId(ADMIN_USER_ID)
                .build(),
            SecretAccessLogTestDataBuilder.writeAccessLog()
                .withSecretId(TEST_SECRET_ID)
                .withUserId(ADMIN_USER_ID)
                .build(),
            SecretAccessLogTestDataBuilder.readAccessLog()
                .withSecretId(TEST_SECRET_ID)
                .withUserId(DEVELOPER_USER_ID)
                .build()
        )
    }
    
    /**
     * Creates minimal test data for basic testing
     */
    fun createMinimalTestData(): DatabaseTestData {
        return DatabaseTestData(
            users = listOf(
                User(
                    id = ADMIN_USER_ID,
                    email = "admin@test.local",
                    passwordHash = "\$2a\$10\$test.hash",
                    fullName = "Test Admin",
                    isActive = true,
                    isVerified = true,
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now()
                )
            ),
            secrets = listOf(
                SecretTestDataBuilder.aSecret()
                    .withUserId(ADMIN_USER_ID)
                    .build()
            ),
            workflows = listOf(
                WorkflowTestDataBuilder.aWorkflow()
                    .withUserId(ADMIN_USER_ID)
                    .build()
            ),
            tasks = listOf(
                TaskTestDataBuilder.aTask()
                    .withUserId(ADMIN_USER_ID)
                    .build()
            ),
            workflowExecutions = emptyList(),
            taskExecutions = emptyList(),
            systemEvents = emptyList(),
            auditLogs = emptyList(),
            secretAccessLogs = emptyList()
        )
    }
}

/**
 * Container for all test data entities
 */
data class DatabaseTestData(
    val users: List<User>,
    val secrets: List<Secret>,
    val workflows: List<Workflow>,
    val tasks: List<Task>,
    val workflowExecutions: List<WorkflowExecution>,
    val taskExecutions: List<TaskExecution>,
    val systemEvents: List<SystemEvent>,
    val auditLogs: List<AuditLog>,
    val secretAccessLogs: List<SecretAccessLog>
)

/**
 * User entity for testing
 */
data class User(
    val id: String,
    val email: String,
    val passwordHash: String,
    val fullName: String?,
    val isActive: Boolean,
    val isVerified: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Time fixtures for consistent testing
 */
object DatabaseTestTimeFixtures {
    private const val BASE_TIMESTAMP = 1640995200000L // 2022-01-01T00:00:00Z
    
    val FIXED_INSTANT = Instant.fromEpochMilliseconds(BASE_TIMESTAMP)
    val PAST_INSTANT = Instant.fromEpochMilliseconds(BASE_TIMESTAMP - 86400000L) // 1 day ago
    val FUTURE_INSTANT = Instant.fromEpochMilliseconds(BASE_TIMESTAMP + 86400000L) // 1 day from now
}

/**
 * Database test utilities for integration tests
 */
object DatabaseTestUtils {
    
    /**
     * SQL statements for cleaning up test data
     */
    val CLEANUP_STATEMENTS = listOf(
        "DELETE FROM secret_access_logs WHERE secret_id LIKE '1%'",
        "DELETE FROM audit_logs WHERE id LIKE '7%'",
        "DELETE FROM system_events WHERE id LIKE '6%'",
        "DELETE FROM workflow_steps WHERE execution_id LIKE '4%'",
        "DELETE FROM workflow_executions WHERE id LIKE '4%'",
        "DELETE FROM task_executions WHERE id LIKE '5%'",
        "DELETE FROM workflows WHERE id LIKE '2%'",
        "DELETE FROM tasks WHERE id LIKE '3%'",
        "DELETE FROM secrets WHERE id LIKE '1%'",
        "DELETE FROM user_sessions WHERE user_id LIKE '0%'",
        "DELETE FROM users WHERE id LIKE '0%'",
        "DELETE FROM organizations WHERE id LIKE '0%'"
    )
    
    /**
     * SQL statements for setting up test data
     */
    fun getInsertStatements(testData: DatabaseTestData): List<String> {
        val statements = mutableListOf<String>()
        
        // Insert users
        testData.users.forEach { user ->
            statements.add("""
                INSERT INTO users (id, email, password_hash, full_name, is_active, is_verified, created_at, updated_at)
                VALUES ('${user.id}', '${user.email}', '${user.passwordHash}', '${user.fullName}', ${user.isActive}, ${user.isVerified}, '${user.createdAt}', '${user.updatedAt}')
                ON CONFLICT (id) DO NOTHING
            """.trimIndent())
        }
        
        // Insert secrets
        testData.secrets.forEach { secret ->
            statements.add("""
                INSERT INTO secrets (id, name, encrypted_value, encryption_key_id, secret_type, description, user_id, organization_id, version, is_active, created_at, updated_at)
                VALUES ('${secret.id}', '${secret.name}', '${secret.encryptedValue}', '${secret.encryptionKeyId}', '${secret.secretType}', '${secret.description}', '${secret.userId}', ${secret.organizationId?.let { "'$it'" } ?: "NULL"}, ${secret.version}, ${secret.isActive}, '${secret.createdAt}', '${secret.updatedAt}')
                ON CONFLICT (name, user_id, version) DO NOTHING
            """.trimIndent())
        }
        
        // Add more insert statements for other entities as needed...
        
        return statements
    }
    
    /**
     * Validates that test data was inserted correctly
     */
    fun getValidationQueries(): List<String> {
        return listOf(
            "SELECT COUNT(*) as user_count FROM users WHERE id LIKE '0%'",
            "SELECT COUNT(*) as secret_count FROM secrets WHERE id LIKE '1%'",
            "SELECT COUNT(*) as workflow_count FROM workflows WHERE id LIKE '2%'",
            "SELECT COUNT(*) as task_count FROM tasks WHERE id LIKE '3%'"
        )
    }
}