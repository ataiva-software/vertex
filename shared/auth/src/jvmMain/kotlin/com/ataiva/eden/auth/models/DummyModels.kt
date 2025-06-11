package com.ataiva.eden.auth.models

import com.ataiva.eden.auth.util.CustomInstant
import com.ataiva.eden.auth.util.DateTimeUtil

/**
 * Dummy models that don't attempt to convert to/from core models
 * This is a workaround for kotlinx.datetime.Instant compatibility issues
 */

/**
 * Dummy implementation of User model
 */
data class DummyUser(
    val id: String,
    val email: String,
    val passwordHash: String? = null,
    val mfaSecret: String? = null,
    val profile: DummyUserProfile = DummyUserProfile(),
    val isActive: Boolean = true,
    val emailVerified: Boolean = false,
    val lastLoginAt: CustomInstant? = null,
    val createdAt: CustomInstant = DateTimeUtil.now(),
    val updatedAt: CustomInstant = DateTimeUtil.now()
)

/**
 * Dummy implementation of UserProfile model
 */
data class DummyUserProfile(
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val timezone: String = "UTC",
    val locale: String = "en",
    val preferences: Map<String, String> = emptyMap()
)

/**
 * Dummy implementation of UserSession model
 */
data class DummyUserSession(
    val id: String,
    val userId: String,
    val token: String,
    val refreshToken: String? = null,
    val expiresAt: CustomInstant = DateTimeUtil.now(),
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val isActive: Boolean = true,
    val createdAt: CustomInstant = DateTimeUtil.now()
)

/**
 * Dummy implementation of Permission model
 */
data class DummyPermission(
    val id: String,
    val name: String,
    val description: String,
    val resource: String,
    val action: String,
    val scope: DummyPermissionScope = DummyPermissionScope.ORGANIZATION,
    val isActive: Boolean = true,
    val createdAt: CustomInstant = DateTimeUtil.now(),
    val updatedAt: CustomInstant = DateTimeUtil.now()
)

/**
 * Dummy implementation of PermissionScope enum
 */
enum class DummyPermissionScope {
    GLOBAL,
    ORGANIZATION,
    PROJECT,
    RESOURCE
}

/**
 * Dummy implementation of Role model
 */
data class DummyRole(
    val id: String,
    val name: String,
    val description: String,
    val permissions: Set<String>,
    val isBuiltIn: Boolean = false,
    val organizationId: String? = null,
    val createdAt: CustomInstant = DateTimeUtil.now(),
    val updatedAt: CustomInstant = DateTimeUtil.now()
)

/**
 * Dummy implementation of OrganizationMembership model
 */
data class DummyOrganizationMembership(
    val id: String,
    val userId: String,
    val organizationId: String,
    val role: String,
    val permissions: Set<String> = emptySet(),
    val isActive: Boolean = true,
    val invitedBy: String? = null,
    val invitedAt: CustomInstant? = null,
    val joinedAt: CustomInstant? = null,
    val createdAt: CustomInstant = DateTimeUtil.now(),
    val updatedAt: CustomInstant = DateTimeUtil.now()
)

/**
 * Dummy implementation of UserContext model
 */
data class DummyUserContext(
    val user: DummyUser,
    val session: DummyUserSession,
    val permissions: Set<DummyPermission>,
    val organizationMemberships: List<DummyOrganizationMembership>
) {
    fun hasPermission(permission: String): Boolean {
        return permissions.any { it.name == permission } ||
               permissions.any { it.name == "*:*" } ||
               permissions.any { it.name == "${permission.split(":")[0]}:*" }
    }
}