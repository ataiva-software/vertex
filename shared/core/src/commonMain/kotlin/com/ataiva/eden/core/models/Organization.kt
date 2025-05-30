package com.ataiva.eden.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Organization(
    val id: String,
    val name: String,
    val slug: String,
    val description: String = "",
    val settings: OrganizationSettings = OrganizationSettings(),
    val plan: OrganizationPlan = OrganizationPlan.FREE,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
data class OrganizationSettings(
    val allowedDomains: List<String> = emptyList(),
    val requireMfa: Boolean = false,
    val sessionTimeoutMinutes: Int = 480, // 8 hours
    val auditRetentionDays: Int = 90,
    val features: Map<String, Boolean> = emptyMap(),
    val integrations: Map<String, String> = emptyMap(),
    val customFields: Map<String, String> = emptyMap()
)

@Serializable
enum class OrganizationPlan {
    FREE,
    STARTER,
    PROFESSIONAL,
    ENTERPRISE
}

@Serializable
data class OrganizationMembership(
    val id: String,
    val userId: String,
    val organizationId: String,
    val role: String,
    val permissions: Set<String> = emptySet(),
    val isActive: Boolean = true,
    val invitedBy: String? = null,
    val invitedAt: Instant? = null,
    val joinedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
data class OrganizationInvitation(
    val id: String,
    val organizationId: String,
    val email: String,
    val role: String,
    val permissions: Set<String> = emptySet(),
    val invitedBy: String,
    val token: String,
    val expiresAt: Instant,
    val acceptedAt: Instant? = null,
    val createdAt: Instant
) {
    val isExpired: Boolean
        get() = kotlinx.datetime.Clock.System.now() > expiresAt
        
    val isAccepted: Boolean
        get() = acceptedAt != null
}

@Serializable
enum class OrganizationRole(val permissions: Set<String>) {
    OWNER(setOf(
        "org:admin", "org:read", "org:write", "org:delete",
        "user:admin", "user:invite", "user:remove",
        "vault:admin", "flow:admin", "task:admin", "monitor:admin",
        "sync:admin", "insight:admin", "hub:admin"
    )),
    ADMIN(setOf(
        "org:read", "org:write",
        "user:invite", "user:remove",
        "vault:admin", "flow:admin", "task:admin", "monitor:admin",
        "sync:admin", "insight:admin", "hub:admin"
    )),
    DEVELOPER(setOf(
        "org:read",
        "vault:read", "vault:write",
        "flow:read", "flow:write", "flow:execute",
        "task:read", "task:write", "task:execute",
        "monitor:read", "monitor:write",
        "sync:read", "insight:read", "hub:read"
    )),
    VIEWER(setOf(
        "org:read",
        "vault:read", "flow:read", "task:read",
        "monitor:read", "sync:read", "insight:read", "hub:read"
    ))
}