package com.ataiva.eden.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,
    val passwordHash: String? = null,
    val mfaSecret: String? = null,
    val profile: UserProfile = UserProfile(),
    val isActive: Boolean = true,
    val emailVerified: Boolean = false,
    val lastLoginAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
data class UserProfile(
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val timezone: String = "UTC",
    val locale: String = "en",
    val preferences: Map<String, String> = emptyMap()
)

@Serializable
data class UserSession(
    val id: String,
    val userId: String,
    val token: String,
    val refreshToken: String? = null,
    val expiresAt: Instant,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val isActive: Boolean = true,
    val createdAt: Instant
)

@Serializable
data class UserContext(
    val user: User,
    val session: UserSession,
    val permissions: Set<Permission>,
    val organizationMemberships: List<OrganizationMembership>
) {
    fun hasPermission(permission: String): Boolean {
        return permissions.any { it.name == permission }
    }
    
    fun hasRole(organizationId: String, role: String): Boolean {
        return organizationMemberships.any { 
            it.organizationId == organizationId && it.role == role 
        }
    }
    
    fun getOrganizationIds(): Set<String> {
        return organizationMemberships.map { it.organizationId }.toSet()
    }
}