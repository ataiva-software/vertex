package com.ataiva.eden.auth.models

import com.ataiva.eden.auth.util.CustomInstant
import com.ataiva.eden.auth.util.DateTimeUtil
import com.ataiva.eden.auth.util.InstantUtil

/**
 * Auth-specific model classes that use CustomInstant instead of kotlinx.datetime.Instant
 * These are used as a workaround for kotlinx.datetime.Instant compatibility issues
 */

/**
 * Auth-specific User model
 */
data class AuthUser(
    val id: String,
    val email: String,
    val passwordHash: String? = null,
    val mfaSecret: String? = null,
    val profile: AuthUserProfile = AuthUserProfile(),
    val isActive: Boolean = true,
    val emailVerified: Boolean = false,
    val lastLoginAt: CustomInstant? = null,
    val createdAt: CustomInstant,
    val updatedAt: CustomInstant
) {
    companion object {
        fun fromCoreUser(user: com.ataiva.eden.core.models.User): AuthUser {
            return AuthUser(
                id = user.id,
                email = user.email,
                passwordHash = user.passwordHash,
                mfaSecret = user.mfaSecret,
                profile = AuthUserProfile.fromCoreUserProfile(user.profile),
                isActive = user.isActive,
                emailVerified = user.emailVerified,
                lastLoginAt = null, // We can't convert the Instant type
                createdAt = DateTimeUtil.now(),
                updatedAt = DateTimeUtil.now()
            )
        }
    }
    
    fun toCoreUser(): com.ataiva.eden.core.models.User {
        return com.ataiva.eden.core.models.User(
            id = id,
            email = email,
            passwordHash = passwordHash,
            mfaSecret = mfaSecret,
            profile = profile.toCoreUserProfile(),
            isActive = isActive,
            emailVerified = emailVerified,
            lastLoginAt = null, // We can't convert the Instant type
            // Use a dummy Instant value for non-nullable fields
            createdAt = InstantUtil.dummyInstant(),
            updatedAt = InstantUtil.dummyInstant()
        )
    }
    
    /**
     * Convert to StubUser
     */
    fun toStubUser(): StubUser {
        return StubUser(
            id = id,
            email = email,
            passwordHash = passwordHash,
            mfaSecret = mfaSecret,
            profile = profile.toStubUserProfile(),
            isActive = isActive,
            emailVerified = emailVerified,
            lastLoginAt = lastLoginAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

/**
 * Auth-specific UserProfile model
 */
data class AuthUserProfile(
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val timezone: String = "UTC",
    val locale: String = "en",
    val preferences: Map<String, String> = emptyMap()
) {
    companion object {
        fun fromCoreUserProfile(profile: com.ataiva.eden.core.models.UserProfile): AuthUserProfile {
            return AuthUserProfile(
                firstName = profile.firstName,
                lastName = profile.lastName,
                displayName = profile.displayName,
                avatarUrl = profile.avatarUrl,
                timezone = profile.timezone,
                locale = profile.locale,
                preferences = profile.preferences
            )
        }
    }
    
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
    
    /**
     * Convert to StubUserProfile
     */
    fun toStubUserProfile(): StubUserProfile {
        return StubUserProfile(
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
 * Auth-specific UserSession model
 */
data class AuthUserSession(
    val id: String,
    val userId: String,
    val token: String,
    val refreshToken: String? = null,
    val expiresAt: CustomInstant,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val isActive: Boolean = true,
    val createdAt: CustomInstant
) {
    companion object {
        fun fromCoreUserSession(session: com.ataiva.eden.core.models.UserSession): AuthUserSession {
            return AuthUserSession(
                id = session.id,
                userId = session.userId,
                token = session.token,
                refreshToken = session.refreshToken,
                expiresAt = DateTimeUtil.now(), // Use current time as we can't convert
                ipAddress = session.ipAddress,
                userAgent = session.userAgent,
                isActive = session.isActive,
                createdAt = DateTimeUtil.now() // Use current time as we can't convert
            )
        }
    }
    
    fun toCoreUserSession(): com.ataiva.eden.core.models.UserSession {
        return com.ataiva.eden.core.models.UserSession(
            id = id,
            userId = userId,
            token = token,
            refreshToken = refreshToken,
            expiresAt = InstantUtil.dummyInstant(),
            ipAddress = ipAddress,
            userAgent = userAgent,
            isActive = isActive,
            createdAt = InstantUtil.dummyInstant()
        )
    }
    
    /**
     * Convert to StubUserSession
     */
    fun toStubUserSession(): StubUserSession {
        return StubUserSession(
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

/**
 * Auth-specific Permission model
 */
data class AuthPermission(
    val id: String,
    val name: String,
    val description: String,
    val resource: String,
    val action: String,
    val scope: AuthPermissionScope = AuthPermissionScope.ORGANIZATION,
    val isActive: Boolean = true,
    val createdAt: CustomInstant,
    val updatedAt: CustomInstant
) {
    companion object {
        fun fromCorePermission(permission: com.ataiva.eden.core.models.Permission): AuthPermission {
            return AuthPermission(
                id = permission.id,
                name = permission.name,
                description = permission.description,
                resource = permission.resource,
                action = permission.action,
                scope = AuthPermissionScope.valueOf(permission.scope.name),
                isActive = permission.isActive,
                createdAt = DateTimeUtil.now(), // Use current time as we can't convert
                updatedAt = DateTimeUtil.now() // Use current time as we can't convert
            )
        }
    }
    
    fun toCorePermission(): com.ataiva.eden.core.models.Permission {
        return com.ataiva.eden.core.models.Permission(
            id = id,
            name = name,
            description = description,
            resource = resource,
            action = action,
            scope = com.ataiva.eden.core.models.PermissionScope.valueOf(scope.name),
            isActive = isActive,
            createdAt = InstantUtil.dummyInstant(),
            updatedAt = InstantUtil.dummyInstant()
        )
    }
    
    /**
     * Convert to StubPermission
     */
    fun toStubPermission(): StubPermission {
        return StubPermission(
            id = id,
            name = name,
            description = description,
            resource = resource,
            action = action,
            scope = StubPermissionScope.valueOf(scope.name),
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

/**
 * Auth-specific PermissionScope enum
 */
enum class AuthPermissionScope {
    GLOBAL,
    ORGANIZATION,
    PROJECT,
    RESOURCE
}

/**
 * Auth-specific OrganizationMembership model
 */
data class AuthOrganizationMembership(
    val id: String,
    val userId: String,
    val organizationId: String,
    val role: String,
    val permissions: Set<String> = emptySet(),
    val isActive: Boolean = true,
    val invitedBy: String? = null,
    val invitedAt: CustomInstant? = null,
    val joinedAt: CustomInstant? = null,
    val createdAt: CustomInstant,
    val updatedAt: CustomInstant
) {
    companion object {
        fun fromCoreOrganizationMembership(membership: com.ataiva.eden.core.models.OrganizationMembership): AuthOrganizationMembership {
            return AuthOrganizationMembership(
                id = membership.id,
                userId = membership.userId,
                organizationId = membership.organizationId,
                role = membership.role,
                permissions = membership.permissions,
                isActive = membership.isActive,
                invitedBy = membership.invitedBy,
                invitedAt = null, // We can't convert the Instant type
                joinedAt = null, // We can't convert the Instant type
                createdAt = DateTimeUtil.now(), // Use current time as we can't convert
                updatedAt = DateTimeUtil.now() // Use current time as we can't convert
            )
        }
    }
    
    fun toCoreOrganizationMembership(): com.ataiva.eden.core.models.OrganizationMembership {
        return com.ataiva.eden.core.models.OrganizationMembership(
            id = id,
            userId = userId,
            organizationId = organizationId,
            role = role,
            permissions = permissions,
            isActive = isActive,
            invitedBy = invitedBy,
            invitedAt = null, // We can't convert the Instant type
            joinedAt = null, // We can't convert the Instant type
            createdAt = InstantUtil.dummyInstant(),
            updatedAt = InstantUtil.dummyInstant()
        )
    }
    
    /**
     * Convert to StubOrganizationMembership
     */
    fun toStubOrganizationMembership(): StubOrganizationMembership {
        return StubOrganizationMembership(
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
 * Auth-specific UserContext model
 */
data class AuthUserContext(
    val user: AuthUser,
    val session: AuthUserSession,
    val permissions: Set<AuthPermission>,
    val organizationMemberships: List<AuthOrganizationMembership>
) {
    companion object {
        fun fromCoreUserContext(userContext: com.ataiva.eden.core.models.UserContext): AuthUserContext {
            return AuthUserContext(
                user = AuthUser.fromCoreUser(userContext.user),
                session = AuthUserSession.fromCoreUserSession(userContext.session),
                permissions = userContext.permissions.map { AuthPermission.fromCorePermission(it) }.toSet(),
                organizationMemberships = userContext.organizationMemberships.map { AuthOrganizationMembership.fromCoreOrganizationMembership(it) }
            )
        }
    }
    
    fun toCoreUserContext(): com.ataiva.eden.core.models.UserContext {
        return com.ataiva.eden.core.models.UserContext(
            user = user.toCoreUser(),
            session = session.toCoreUserSession(),
            permissions = permissions.map { it.toCorePermission() }.toSet(),
            organizationMemberships = organizationMemberships.map { it.toCoreOrganizationMembership() }
        )
    }
    
    fun hasPermission(permission: String): Boolean {
        return permissions.any { it.name == permission } ||
               permissions.any { it.name == "*:*" } ||
               permissions.any { it.name == "${permission.split(":")[0]}:*" }
    }
    
    /**
     * Convert to StubUserContext
     */
    fun toStubUserContext(): StubUserContext {
        return StubUserContext(
            user = user.toStubUser(),
            session = session.toStubUserSession(),
            permissions = permissions.map { it.toStubPermission() }.toSet(),
            organizationMemberships = organizationMemberships.map { it.toStubOrganizationMembership() }
        )
    }
}

// Type aliases for compatibility with existing code
typealias Permission = AuthPermission
typealias UserContext = AuthUserContext