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
    ): User {
        return User(
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
        user: User = createTestUser(),
        organizationId: String = TEST_ORG_ID
    ): UserContext {
        val session = createTestUserSession(userId = user.id)
        val permissions = setOf(
            createTestPermission("perm-1", "org:read", "org", "read"),
            createTestPermission("perm-2", "vault:read", "vault", "read"),
            createTestPermission("perm-3", "flow:execute", "flow", "execute")
        )
        val memberships = listOf(
            createTestMembership(userId = user.id, organizationId = organizationId)
        )
        
        return UserContext(
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
        
        fun createMultipleUsers(count: Int = 5): List<User> {
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