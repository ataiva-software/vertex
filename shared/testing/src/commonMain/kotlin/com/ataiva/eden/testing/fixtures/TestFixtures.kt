package com.ataiva.eden.testing.fixtures

import com.ataiva.eden.core.models.*

/**
 * Test fixtures providing consistent test data across all test suites
 * Centralizes test data creation and management
 */
object TestFixtures {
    
    // Common test identifiers
    const val TEST_USER_ID = "test-user-12345"
    const val TEST_ORG_ID = "test-org-67890"
    const val TEST_SESSION_ID = "test-session-abcde"
    const val TEST_PERMISSION_ID = "test-permission-fghij"
    const val TEST_MEMBERSHIP_ID = "test-membership-klmno"
    
    // Common test emails and names
    const val TEST_EMAIL = "test@example.com"
    const val TEST_USERNAME = "testuser"
    const val TEST_DISPLAY_NAME = "Test User"
    const val TEST_ORG_NAME = "Test Organization"
    const val TEST_ORG_SLUG = "test-org"
    
    // Test passwords and tokens
    const val TEST_PASSWORD = "TestPassword123!"
    const val TEST_PASSWORD_HASH = "\$2a\$10\$test.hash.value.for.testing"
    const val TEST_JWT_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.test.token"
    const val TEST_REFRESH_TOKEN = "refresh-token-test-value"
    const val TEST_MFA_SECRET = "JBSWY3DPEHPK3PXP"
    
    // Test IP addresses and user agents
    const val TEST_IP_ADDRESS = "192.168.1.100"
    const val TEST_USER_AGENT = "Mozilla/5.0 (Test Browser) Eden/1.0"
    
    /**
     * Creates a standard test user profile
     */
    fun createTestUserProfile(
        firstName: String = "Test",
        lastName: String = "User",
        displayName: String = TEST_DISPLAY_NAME
    ): UserProfile {
        return UserProfile(
            firstName = firstName,
            lastName = lastName,
            displayName = displayName,
            avatarUrl = "https://example.com/avatar.jpg",
            timezone = "UTC",
            locale = "en",
            preferences = mapOf(
                "theme" to "dark",
                "notifications" to "enabled"
            )
        )
    }
    
    /**
     * Creates a standard test user
     */
    fun createTestUser(
        id: String = TEST_USER_ID,
        email: String = TEST_EMAIL,
        profile: UserProfile = createTestUserProfile(),
        isActive: Boolean = true,
        emailVerified: Boolean = true
    ): com.ataiva.eden.core.models.User {
        return com.ataiva.eden.core.models.User(
            id = id,
            email = email,
            passwordHash = TEST_PASSWORD_HASH,
            mfaSecret = if (emailVerified) TEST_MFA_SECRET else null,
            profile = profile,
            isActive = isActive,
            emailVerified = emailVerified,
            lastLoginAt = null,
            createdAt = TestTimeFixtures.FIXED_INSTANT,
            updatedAt = TestTimeFixtures.FIXED_INSTANT
        )
    }
    
    /**
     * Creates a standard test organization settings
     */
    fun createTestOrganizationSettings(
        requireMfa: Boolean = false,
        sessionTimeoutMinutes: Int = 480
    ): OrganizationSettings {
        return OrganizationSettings(
            allowedDomains = listOf("example.com", "test.com"),
            requireMfa = requireMfa,
            sessionTimeoutMinutes = sessionTimeoutMinutes,
            auditRetentionDays = 90,
            features = mapOf(
                "vault" to true,
                "flow" to true,
                "monitoring" to true
            ),
            integrations = mapOf(
                "slack" to "enabled",
                "github" to "enabled"
            ),
            customFields = mapOf(
                "department" to "Engineering",
                "cost_center" to "R&D"
            )
        )
    }
    
    /**
     * Creates a standard test organization
     */
    fun createTestOrganization(
        id: String = TEST_ORG_ID,
        name: String = TEST_ORG_NAME,
        slug: String = TEST_ORG_SLUG,
        plan: OrganizationPlan = OrganizationPlan.PROFESSIONAL
    ): Organization {
        return Organization(
            id = id,
            name = name,
            slug = slug,
            description = "A test organization for development and testing",
            settings = createTestOrganizationSettings(),
            plan = plan,
            isActive = true,
            createdAt = TestTimeFixtures.FIXED_INSTANT,
            updatedAt = TestTimeFixtures.FIXED_INSTANT
        )
    }
    
    /**
     * Creates a standard test permission
     */
    fun createTestPermission(
        id: String = TEST_PERMISSION_ID,
        name: String = "test:read",
        resource: String = "test",
        action: String = "read"
    ): Permission {
        return Permission(
            id = id,
            name = name,
            description = "Test permission for reading test resources",
            resource = resource,
            action = action,
            scope = PermissionScope.ORGANIZATION,
            isActive = true,
            createdAt = TestTimeFixtures.FIXED_INSTANT,
            updatedAt = TestTimeFixtures.FIXED_INSTANT
        )
    }
    
    /**
     * Creates a standard test user session
     */
    fun createTestUserSession(
        id: String = TEST_SESSION_ID,
        userId: String = TEST_USER_ID,
        token: String = TEST_JWT_TOKEN
    ): UserSession {
        return UserSession(
            id = id,
            userId = userId,
            token = token,
            refreshToken = TEST_REFRESH_TOKEN,
            expiresAt = TestTimeFixtures.FUTURE_INSTANT,
            ipAddress = TEST_IP_ADDRESS,
            userAgent = TEST_USER_AGENT,
            isActive = true,
            createdAt = TestTimeFixtures.FIXED_INSTANT
        )
    }
    
    /**
     * Creates a standard test organization membership
     */
    fun createTestMembership(
        id: String = TEST_MEMBERSHIP_ID,
        userId: String = TEST_USER_ID,
        organizationId: String = TEST_ORG_ID,
        role: String = "developer"
    ): OrganizationMembership {
        return OrganizationMembership(
            id = id,
            userId = userId,
            organizationId = organizationId,
            role = role,
            permissions = OrganizationRole.DEVELOPER.permissions,
            isActive = true,
            invitedBy = "admin-user-id",
            invitedAt = TestTimeFixtures.PAST_INSTANT,
            joinedAt = TestTimeFixtures.FIXED_INSTANT,
            createdAt = TestTimeFixtures.PAST_INSTANT,
            updatedAt = TestTimeFixtures.FIXED_INSTANT
        )
    }
    
    /**
     * Creates a complete test user context
     */
    fun createTestUserContext(
        user: com.ataiva.eden.core.models.User = createTestUser(),
        organizationId: String = TEST_ORG_ID
    ): com.ataiva.eden.core.models.UserContext {
        val session = createTestUserSession(userId = user.id)
        val permissions = setOf(
            createTestPermission("perm-1", "org:read", "org", "read"),
            createTestPermission("perm-2", "vault:read", "vault", "read"),
            createTestPermission("perm-3", "flow:execute", "flow", "execute")
        )
        val memberships = listOf(
            createTestMembership(userId = user.id, organizationId = organizationId)
        )
        
        return com.ataiva.eden.core.models.UserContext(
            user = user,
            session = session,
            permissions = permissions,
            organizationMemberships = memberships
        )
    }
    
    /**
     * Creates test data sets for bulk operations
     */
    object BulkData {
        
        fun createMultipleUsers(count: Int = 5): List<com.ataiva.eden.core.models.User> {
            return (1..count).map { index ->
                createTestUser(
                    id = "test-user-$index",
                    email = "user$index@example.com",
                    profile = createTestUserProfile(
                        firstName = "User",
                        lastName = "$index",
                        displayName = "User $index"
                    )
                )
            }
        }
        
        fun createMultipleOrganizations(count: Int = 3): List<Organization> {
            return (1..count).map { index ->
                createTestOrganization(
                    id = "test-org-$index",
                    name = "Organization $index",
                    slug = "org-$index"
                )
            }
        }
        
        fun createMultiplePermissions(count: Int = 10): List<Permission> {
            val resources = listOf("vault", "flow", "task", "monitor", "sync")
            val actions = listOf("read", "write", "execute", "admin")
            
            return (1..count).map { index ->
                val resource = resources[index % resources.size]
                val action = actions[index % actions.size]
                createTestPermission(
                    id = "test-permission-$index",
                    name = "$resource:$action",
                    resource = resource,
                    action = action
                )
            }
        }
    }
}

/**
 * Time-related test fixtures
 */
object TestTimeFixtures {
    // Fixed timestamp for consistent testing: 2022-01-01T00:00:00Z
    private const val BASE_TIMESTAMP = 1640995200000L
    
    val FIXED_INSTANT = kotlinx.datetime.Instant.fromEpochMilliseconds(BASE_TIMESTAMP)
    val PAST_INSTANT = kotlinx.datetime.Instant.fromEpochMilliseconds(BASE_TIMESTAMP - 86400000L) // 1 day ago
    val FUTURE_INSTANT = kotlinx.datetime.Instant.fromEpochMilliseconds(BASE_TIMESTAMP + 86400000L) // 1 day from now
    val FAR_FUTURE_INSTANT = kotlinx.datetime.Instant.fromEpochMilliseconds(BASE_TIMESTAMP + 31536000000L) // 1 year from now
}

/**
 * Environment-specific test fixtures
 */
object TestEnvironmentFixtures {
    
    const val TEST_DATABASE_URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
    const val TEST_REDIS_URL = "redis://localhost:6379/1"
    const val TEST_API_BASE_URL = "http://localhost:8080"
    
    val TEST_ENVIRONMENT_VARIABLES = mapOf(
        "ENVIRONMENT" to "test",
        "LOG_LEVEL" to "DEBUG",
        "DATABASE_URL" to TEST_DATABASE_URL,
        "REDIS_URL" to TEST_REDIS_URL,
        "JWT_SECRET" to "test-jwt-secret-key",
        "ENCRYPTION_KEY" to "test-encryption-key-32-bytes-long"
    )
    
    fun createTestConfiguration(): Map<String, String> {
        return TEST_ENVIRONMENT_VARIABLES + mapOf(
            "TEST_MODE" to "true",
            "MOCK_EXTERNAL_SERVICES" to "true",
            "DISABLE_RATE_LIMITING" to "true"
        )
    }
}

/**
 * Extended test fixtures for new database schema entities
 */
object NewSchemaTestFixtures {
    
    // Test IDs for new schema entities
    const val TEST_SECRET_ID = "secret-test-12345"
    const val TEST_WORKFLOW_ID = "workflow-test-67890"
    const val TEST_TASK_ID = "task-test-abcde"
    const val TEST_EXECUTION_ID = "execution-test-fghij"
    
    /**
     * Creates a test secret for vault testing
     */
    fun createTestSecret(
        id: String = TEST_SECRET_ID,
        name: String = "test-secret",
        userId: String = TestFixtures.TEST_USER_ID,
        secretType: String = "generic"
    ): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to name,
            "encrypted_value" to "encrypted_test_value_placeholder",
            "encryption_key_id" to "test-key-001",
            "secret_type" to secretType,
            "description" to "Test secret for development",
            "user_id" to userId,
            "organization_id" to TestFixtures.TEST_ORG_ID,
            "version" to 1,
            "is_active" to true,
            "created_at" to TestTimeFixtures.FIXED_INSTANT,
            "updated_at" to TestTimeFixtures.FIXED_INSTANT
        )
    }
    
    /**
     * Creates a test workflow for flow testing
     */
    fun createTestWorkflow(
        id: String = TEST_WORKFLOW_ID,
        name: String = "test-workflow",
        userId: String = TestFixtures.TEST_USER_ID
    ): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to name,
            "description" to "Test workflow for development",
            "definition" to mapOf(
                "name" to name,
                "description" to "Test workflow",
                "version" to "1.0",
                "steps" to listOf(
                    mapOf(
                        "name" to "test-step",
                        "type" to "shell",
                        "configuration" to mapOf(
                            "command" to "echo 'Hello World'"
                        )
                    )
                )
            ),
            "user_id" to userId,
            "status" to "active",
            "version" to 1,
            "created_at" to TestTimeFixtures.FIXED_INSTANT,
            "updated_at" to TestTimeFixtures.FIXED_INSTANT
        )
    }
    
    /**
     * Creates a test task for task testing
     */
    fun createTestTask(
        id: String = TEST_TASK_ID,
        name: String = "test-task",
        userId: String = TestFixtures.TEST_USER_ID,
        taskType: String = "shell"
    ): Map<String, Any> {
        return mapOf<String, Any>(
            "id" to id,
            "name" to name,
            "description" to "Test task for development",
            "task_type" to taskType,
            "configuration" to mapOf<String, Any>(
                "command" to "echo 'Test task execution'"
            ),
            "schedule_cron" to "" as Any,
            "user_id" to userId,
            "is_active" to true,
            "created_at" to TestTimeFixtures.FIXED_INSTANT,
            "updated_at" to TestTimeFixtures.FIXED_INSTANT
        )
    }
    
    /**
     * Creates a test workflow execution
     */
    fun createTestWorkflowExecution(
        id: String = TEST_EXECUTION_ID,
        workflowId: String = TEST_WORKFLOW_ID,
        triggeredBy: String = TestFixtures.TEST_USER_ID,
        status: String = "completed"
    ): Map<String, Any> {
        return mapOf<String, Any>(
            "id" to id,
            "workflow_id" to workflowId,
            "triggered_by" to triggeredBy,
            "status" to status,
            "input_data" to mapOf<String, Any>("test" to "input"),
            "output_data" to (if (status == "completed") mapOf<String, Any>("result" to "success") else mapOf<String, Any>()) as Any,
            "error_message" to (if (status == "failed") "Test error message" else "") as Any,
            "started_at" to TestTimeFixtures.FIXED_INSTANT,
            "completed_at" to (if (status in listOf("completed", "failed")) TestTimeFixtures.FUTURE_INSTANT else TestTimeFixtures.FIXED_INSTANT) as Any,
            "duration_ms" to (if (status in listOf("completed", "failed")) 30000 else 0) as Any
        )
    }
    
    /**
     * Creates a test task execution
     */
    fun createTestTaskExecution(
        id: String = "task-execution-${generateRandomId()}",
        taskId: String = TEST_TASK_ID,
        status: String = "completed",
        priority: Int = 0
    ): Map<String, Any> {
        return mapOf<String, Any>(
            "id" to id,
            "task_id" to taskId,
            "status" to status,
            "priority" to priority,
            "input_data" to mapOf<String, Any>("test" to "input"),
            "output_data" to (if (status == "completed") mapOf<String, Any>("result" to "success") else mapOf<String, Any>()) as Any,
            "error_message" to (if (status == "failed") "Test error message" else "") as Any,
            "progress_percentage" to if (status == "completed") 100 else 0,
            "queued_at" to TestTimeFixtures.FIXED_INSTANT,
            "started_at" to (if (status != "queued") TestTimeFixtures.FIXED_INSTANT else TestTimeFixtures.FIXED_INSTANT) as Any,
            "completed_at" to (if (status in listOf("completed", "failed")) TestTimeFixtures.FUTURE_INSTANT else TestTimeFixtures.FIXED_INSTANT) as Any,
            "duration_ms" to (if (status in listOf("completed", "failed")) 15000 else 0) as Any
        )
    }
    
    /**
     * Creates a test system event
     */
    fun createTestSystemEvent(
        id: String = "event-${generateRandomId()}",
        eventType: String = "test_event",
        sourceService: String = "test-service",
        severity: String = "info",
        userId: String? = TestFixtures.TEST_USER_ID
    ): Map<String, Any> {
        return mapOf<String, Any>(
            "id" to id,
            "event_type" to eventType,
            "source_service" to sourceService,
            "event_data" to mapOf<String, Any>(
                "message" to "Test event",
                "details" to "Additional test details"
            ),
            "severity" to severity,
            "user_id" to (userId ?: "") as Any,
            "created_at" to TestTimeFixtures.FIXED_INSTANT
        )
    }
    
    /**
     * Creates a test audit log entry
     */
    fun createTestAuditLog(
        id: String = "audit-${generateRandomId()}",
        userId: String = TestFixtures.TEST_USER_ID,
        action: String = "READ",
        resource: String = "test_resource",
        resourceId: String? = null
    ): Map<String, Any> {
        return mapOf<String, Any>(
            "id" to id,
            "user_id" to userId,
            "organization_id" to TestFixtures.TEST_ORG_ID,
            "action" to action,
            "resource" to resource,
            "resource_id" to (resourceId ?: "") as Any,
            "details" to mapOf<String, Any>(
                "action" to action.lowercase(),
                "resource_type" to resource
            ),
            "ip_address" to TestFixtures.TEST_IP_ADDRESS,
            "user_agent" to TestFixtures.TEST_USER_AGENT,
            "timestamp" to TestTimeFixtures.FIXED_INSTANT,
            "severity" to "INFO"
        )
    }
    
    /**
     * Creates a test secret access log
     */
    fun createTestSecretAccessLog(
        id: String = "access-${generateRandomId()}",
        secretId: String = TEST_SECRET_ID,
        userId: String = TestFixtures.TEST_USER_ID,
        action: String = "read"
    ): Map<String, Any> {
        return mapOf(
            "id" to id,
            "secret_id" to secretId,
            "user_id" to userId,
            "action" to action,
            "ip_address" to TestFixtures.TEST_IP_ADDRESS,
            "user_agent" to TestFixtures.TEST_USER_AGENT,
            "created_at" to TestTimeFixtures.FIXED_INSTANT
        )
    }
    
    /**
     * Creates a complete test dataset for new schema
     */
    fun createCompleteTestDataset(): Map<String, List<Map<String, Any>>> {
        return mapOf(
            "secrets" to listOf(
                createTestSecret(name = "database-password", secretType = "database"),
                createTestSecret(name = "api-key-github", secretType = "api_token"),
                createTestSecret(name = "ssl-certificate", secretType = "certificate")
            ),
            "workflows" to listOf(
                createTestWorkflow(name = "deploy-to-staging"),
                createTestWorkflow(name = "backup-database"),
                createTestWorkflow(name = "run-tests")
            ),
            "tasks" to listOf(
                createTestTask(name = "health-check", taskType = "http_check"),
                createTestTask(name = "file-cleanup", taskType = "file_cleanup"),
                createTestTask(name = "data-sync", taskType = "data_sync")
            ),
            "workflow_executions" to listOf(
                createTestWorkflowExecution(status = "completed"),
                createTestWorkflowExecution(status = "running"),
                createTestWorkflowExecution(status = "failed")
            ),
            "task_executions" to listOf(
                createTestTaskExecution(status = "completed"),
                createTestTaskExecution(status = "running"),
                createTestTaskExecution(status = "queued")
            ),
            "system_events" to listOf(
                createTestSystemEvent(eventType = "user_login", sourceService = "api-gateway"),
                createTestSystemEvent(eventType = "secret_accessed", sourceService = "vault"),
                createTestSystemEvent(eventType = "workflow_failed", sourceService = "flow", severity = "error")
            ),
            "audit_logs" to listOf(
                createTestAuditLog(action = "CREATE", resource = "secret"),
                createTestAuditLog(action = "EXECUTE", resource = "workflow"),
                createTestAuditLog(action = "READ", resource = "secret")
            ),
            "secret_access_logs" to listOf(
                createTestSecretAccessLog(action = "read"),
                createTestSecretAccessLog(action = "write"),
                createTestSecretAccessLog(action = "delete")
            )
        )
    }
    
    private fun generateRandomId(): String {
        return (1..8).map { ('a'..'z').random() }.joinToString("")
    }
}