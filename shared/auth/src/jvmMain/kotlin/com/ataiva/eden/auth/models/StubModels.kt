package com.ataiva.eden.auth.models

import com.ataiva.eden.auth.util.CustomInstant
import com.ataiva.eden.auth.util.DateTimeUtil
import com.ataiva.eden.auth.util.InstantUtil

/**
 * Stub implementations of core models that use CustomInstant instead of kotlinx.datetime.Instant
 * These are used as a workaround for kotlinx.datetime.Instant compatibility issues
 */

/**
 * Stub implementation of User model
 */
class StubUser(
    val id: String,
    val email: String,
    val passwordHash: String? = null,
    val mfaSecret: String? = null,
    val profile: StubUserProfile = StubUserProfile(),
    val isActive: Boolean = true,
    val emailVerified: Boolean = false,
    val lastLoginAt: CustomInstant? = null,
    val createdAt: CustomInstant = DateTimeUtil.now(),
    val updatedAt: CustomInstant = DateTimeUtil.now()
) {
    fun toAuthUser(): AuthUser {
        return AuthUser(
            id = id,
            email = email,
            passwordHash = passwordHash,
            mfaSecret = mfaSecret,
            profile = AuthUserProfile.fromCoreUserProfile(profile.toCoreUserProfile()),
            isActive = isActive,
            emailVerified = emailVerified,
            lastLoginAt = lastLoginAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

/**
 * Stub implementation of UserProfile model
 */
class StubUserProfile(
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val timezone: String = "UTC",
    val locale: String = "en",
    val preferences: Map<String, String> = emptyMap()
) {
    fun toCoreUserProfile(): com.ataiva.eden.core.models.UserProfile {
        return com.ataiva.eden.core.models.UserProfile(
            firstName = firstName,
            lastName = lastName,
            displayName = displayName,
            avatarUrl = avatarUrl,
            timezone = timezone,
            locale = locale,
            preferences = preferences
        )
    }
}

/**
 * Stub implementation of Permission model
 */
class StubPermission(
    val id: String,
    val name: String,
    val description: String,
    val resource: String,
    val action: String,
    val scope: StubPermissionScope = StubPermissionScope.ORGANIZATION,
    val isActive: Boolean = true,
    val createdAt: CustomInstant = DateTimeUtil.now(),
    val updatedAt: CustomInstant = DateTimeUtil.now()
) {
    fun toAuthPermission(): AuthPermission {
        return AuthPermission(
            id = id,
            name = name,
            description = description,
            resource = resource,
            action = action,
            scope = AuthPermissionScope.valueOf(scope.name),
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

/**
 * Stub implementation of PermissionScope enum
 */
enum class StubPermissionScope {
    GLOBAL,
    ORGANIZATION,
    PROJECT,
    RESOURCE;
    
    fun toCorePermissionScope(): com.ataiva.eden.core.models.PermissionScope {
        return com.ataiva.eden.core.models.PermissionScope.valueOf(this.name)
    }
}

/**
 * Stub implementation of Role model
 */
class StubRole(
    val id: String,
    val name: String,
    val description: String,
    val permissions: Set<String>,
    val isBuiltIn: Boolean = false,
    val organizationId: String? = null,
    val createdAt: CustomInstant = DateTimeUtil.now(),
    val updatedAt: CustomInstant = DateTimeUtil.now()
) {
    fun toCoreRole(): com.ataiva.eden.core.models.Role {
        return com.ataiva.eden.core.models.Role(
            id = id,
            name = name,
            description = description,
            permissions = permissions,
            isBuiltIn = isBuiltIn,
            organizationId = organizationId,
            createdAt = InstantUtil.dummyInstant(),
            updatedAt = InstantUtil.dummyInstant()
        )
    }
}

/**
 * Stub implementation of OrganizationMembership model
 */
class StubOrganizationMembership(
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
) {
    fun toAuthOrganizationMembership(): AuthOrganizationMembership {
        return AuthOrganizationMembership(
            id = id,
            userId = userId,
            organizationId = organizationId,
            role = role,
            permissions = permissions,
            isActive = isActive,
            invitedBy = invitedBy,
            invitedAt = invitedAt,
            joinedAt = joinedAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

/**
 * Stub implementation of UserContext model
 */
class StubUserContext(
    val user: StubUser,
    val session: StubUserSession,
    val permissions: Set<StubPermission>,
    val organizationMemberships: List<StubOrganizationMembership>
) {
    fun toAuthUserContext(): AuthUserContext {
        return AuthUserContext(
            user = user.toAuthUser(),
            session = session.toAuthUserSession(),
            permissions = permissions.map { it.toAuthPermission() }.toSet(),
            organizationMemberships = organizationMemberships.map { it.toAuthOrganizationMembership() }
        )
    }
    
    fun toCoreUserContext(): com.ataiva.eden.core.models.UserContext {
        return toAuthUserContext().toCoreUserContext()
    }
}

/**
 * Stub implementation of UserSession model
 */
class StubUserSession(
    val id: String,
    val userId: String,
    val token: String,
    val refreshToken: String? = null,
    val expiresAt: CustomInstant = DateTimeUtil.now(),
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val isActive: Boolean = true,
    val createdAt: CustomInstant = DateTimeUtil.now()
) {
    fun toAuthUserSession(): AuthUserSession {
        return AuthUserSession(
            id = id,
            userId = userId,
            token = token,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
            ipAddress = ipAddress,
            userAgent = userAgent,
            isActive = isActive,
            createdAt = createdAt
        )
    }
}