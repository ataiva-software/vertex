package com.ataiva.eden.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Permission(
    val id: String,
    val name: String,
    val description: String,
    val resource: String,
    val action: String,
    val scope: PermissionScope = PermissionScope.ORGANIZATION,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
enum class PermissionScope {
    GLOBAL,
    ORGANIZATION,
    PROJECT,
    RESOURCE
}

@Serializable
data class Role(
    val id: String,
    val name: String,
    val description: String,
    val permissions: Set<String>,
    val isBuiltIn: Boolean = false,
    val organizationId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

// Built-in role definitions
object BuiltInRoles {
    val OWNER_PERMISSIONS = setOf(
        "org:admin", "vault:admin", "flow:admin", "task:admin",
        "monitor:admin", "sync:admin", "insight:admin", "hub:admin"
    )
    
    val ADMIN_PERMISSIONS = setOf(
        "org:read", "org:write", "vault:admin", "flow:admin",
        "task:admin", "monitor:admin", "sync:admin", "insight:admin", "hub:admin"
    )
    
    val DEVELOPER_PERMISSIONS = setOf(
        "org:read", "vault:read", "vault:write", "flow:read", "flow:write",
        "flow:execute", "task:read", "task:write", "task:execute",
        "monitor:read", "sync:read", "insight:read", "hub:read"
    )
    
    val VIEWER_PERMISSIONS = setOf(
        "org:read", "vault:read", "flow:read", "task:read",
        "monitor:read", "sync:read", "insight:read", "hub:read"
    )
}