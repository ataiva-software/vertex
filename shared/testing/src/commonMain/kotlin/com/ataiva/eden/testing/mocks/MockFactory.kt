package com.ataiva.eden.testing.mocks

import com.ataiva.eden.core.models.*

/**
 * Factory for creating mock objects for testing
 * Provides consistent mock data across all tests
 */
object MockFactory {
    
    /**
     * Creates a mock User with default test values
     */
    fun createMockUser(
        id: String = "mock-user-123",
        email: String = "test@example.com",
        isActive: Boolean = true,
        emailVerified: Boolean = true
    ): User {
        return User(
            id = id,
            email = email,
            passwordHash = "mock-hash",
            mfaSecret = null,
            profile = UserProfile(
                firstName = "Test",
                lastName = "User",
                displayName = "Test User"
            ),
            isActive = isActive,
            emailVerified = emailVerified,
            lastLoginAt = null,
            createdAt = MockTimeProvider.fixedInstant(),
            updatedAt = MockTimeProvider.fixedInstant()
        )
    }
    
    /**
     * Creates a mock Organization with default test values
     */
    fun createMockOrganization(
        id: String = "mock-org-123",
        name: String = "Test Organization",
        slug: String = "test-org",
        plan: OrganizationPlan = OrganizationPlan.FREE
    ): Organization {
        return Organization(
            id = id,
            name = name,
            slug = slug,
            description = "A test organization",
            settings = OrganizationSettings(),
            plan = plan,
            isActive = true,
            createdAt = MockTimeProvider.fixedInstant(),
            updatedAt = MockTimeProvider.fixedInstant()
        )
    }
    
    /**
     * Creates a mock Permission with default test values
     */
    fun createMockPermission(
        id: String = "mock-permission-123",
        name: String = "test:read",
        resource: String = "test",
        action: String = "read"
    ): Permission {
        return Permission(
            id = id,
            name = name,
            description = "Test permission",
            resource = resource,
            action = action,
            scope = PermissionScope.ORGANIZATION,
            isActive = true,
            createdAt = MockTimeProvider.fixedInstant(),
            updatedAt = MockTimeProvider.fixedInstant()
        )
    }
    
    /**
     * Creates a mock OrganizationMembership with default test values
     */
    fun createMockMembership(
        id: String = "mock-membership-123",
        userId: String = "mock-user-123",
        organizationId: String = "mock-org-123",
        role: String = "developer"
    ): OrganizationMembership {
        return OrganizationMembership(
            id = id,
            userId = userId,
            organizationId = organizationId,
            role = role,
            permissions = setOf("org:read", "vault:read"),
            isActive = true,
            invitedBy = null,
            invitedAt = null,
            joinedAt = MockTimeProvider.fixedInstant(),
            createdAt = MockTimeProvider.fixedInstant(),
            updatedAt = MockTimeProvider.fixedInstant()
        )
    }
    
    /**
     * Creates a mock UserContext with default test values
     */
    fun createMockUserContext(
        user: User = createMockUser(),
        organizationId: String = "mock-org-123"
    ): UserContext {
        val session = UserSession(
            id = "mock-session-123",
            userId = user.id,
            token = "mock-token",
            refreshToken = "mock-refresh-token",
            expiresAt = MockTimeProvider.futureInstant(),
            ipAddress = "127.0.0.1",
            userAgent = "Test Agent",
            isActive = true,
            createdAt = MockTimeProvider.fixedInstant()
        )
        
        val permissions = setOf(
            createMockPermission("perm-1", "org:read", "org", "read"),
            createMockPermission("perm-2", "vault:read", "vault", "read")
        )
        
        val memberships = listOf(
            createMockMembership(
                userId = user.id,
                organizationId = organizationId
            )
        )
        
        return UserContext(
            user = user,
            session = session,
            permissions = permissions,
            organizationMemberships = memberships
        )
    }
    
    /**
     * Creates multiple mock users for testing collections
     */
    fun createMockUsers(count: Int = 3): List<User> {
        return (1..count).map { index ->
            createMockUser(
                id = "mock-user-$index",
                email = "user$index@example.com"
            )
        }
    }
    
    /**
     * Creates multiple mock organizations for testing collections
     */
    fun createMockOrganizations(count: Int = 3): List<Organization> {
        return (1..count).map { index ->
            createMockOrganization(
                id = "mock-org-$index",
                name = "Organization $index",
                slug = "org-$index"
            )
        }
    }
}

/**
 * Provides consistent mock timestamps for testing
 */
object MockTimeProvider {
    private val baseTimestamp = 1640995200000L // 2022-01-01T00:00:00Z
    
    fun fixedInstant(): kotlinx.datetime.Instant {
        return kotlinx.datetime.Instant.fromEpochMilliseconds(baseTimestamp)
    }
    
    fun futureInstant(hoursFromNow: Long = 24): kotlinx.datetime.Instant {
        return kotlinx.datetime.Instant.fromEpochMilliseconds(
            baseTimestamp + (hoursFromNow * 60 * 60 * 1000)
        )
    }
    
    fun pastInstant(hoursAgo: Long = 24): kotlinx.datetime.Instant {
        return kotlinx.datetime.Instant.fromEpochMilliseconds(
            baseTimestamp - (hoursAgo * 60 * 60 * 1000)
        )
    }
}