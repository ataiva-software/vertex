package com.ataiva.eden.auth.ktor

import com.ataiva.eden.auth.rbac.JvmRbacService
import com.ataiva.eden.auth.rbac.UnauthorizedException
import com.ataiva.eden.auth.models.DummyUser
import com.ataiva.eden.auth.models.DummyUserSession
import com.ataiva.eden.auth.models.DummyPermission
import com.ataiva.eden.auth.models.DummyPermissionScope
import com.ataiva.eden.auth.models.DummyOrganizationMembership
import com.ataiva.eden.auth.models.DummyUserContext
import com.ataiva.eden.auth.util.DateTimeUtil

/**
 * Dummy RBAC authentication plugin for Ktor
 * This is a simplified implementation that avoids direct Ktor dependencies
 * and doesn't attempt to convert to/from core models
 */
class DummyRbacAuthPlugin(configuration: Configuration) {
    private val rbacService = configuration.rbacService
    
    /**
     * Configuration for the RBAC authentication plugin
     */
    class Configuration {
        var rbacService: JvmRbacService? = null
    }
    
    /**
     * Check if a user has a specific permission
     */
    fun checkPermission(userContext: DummyUserContext, permission: String): Boolean {
        return userContext.hasPermission(permission)
    }
    
    /**
     * Check if a user has a specific permission for a resource
     */
    fun checkPermission(userContext: DummyUserContext, permission: String, resourceType: String, resourceId: String): Boolean {
        // Check for direct resource permission
        val resourcePermission = "$permission:$resourceType:$resourceId"
        if (userContext.hasPermission(resourcePermission)) {
            return true
        }
        
        // Check for resource type permission
        val resourceTypePermission = "$permission:$resourceType:*"
        if (userContext.hasPermission(resourceTypePermission)) {
            return true
        }
        
        // Check for general permission
        return userContext.hasPermission(permission)
    }
    
    /**
     * Create a UserContext from JWT claims
     */
    fun createUserContext(
        userId: String,
        email: String,
        sessionId: String? = null,
        permissionStrings: List<String> = emptyList(),
        organizationId: String? = null,
        role: String? = null
    ): DummyUserContext {
        // Create a minimal User object
        val user = DummyUser(
            id = userId,
            email = email,
            passwordHash = null,
            createdAt = DateTimeUtil.now(),
            updatedAt = DateTimeUtil.now()
        )
        
        // Create a minimal UserSession object
        val session = DummyUserSession(
            id = sessionId ?: userId,
            userId = userId,
            token = "token",
            expiresAt = DateTimeUtil.now(),
            createdAt = DateTimeUtil.now()
        )
        
        // Convert string permissions to Permission objects
        val permissions = permissionStrings.mapIndexed { index, name ->
            DummyPermission(
                id = "perm-$index",
                name = name,
                description = "Permission",
                resource = name.split(":").getOrElse(0) { "*" },
                action = name.split(":").getOrElse(1) { "*" },
                createdAt = DateTimeUtil.now(),
                updatedAt = DateTimeUtil.now()
            )
        }.toSet()
        
        // Create organization memberships
        val organizationMemberships = if (organizationId != null && role != null) {
            listOf(
                DummyOrganizationMembership(
                    id = "org-1",
                    userId = userId,
                    organizationId = organizationId,
                    role = role,
                    createdAt = DateTimeUtil.now(),
                    updatedAt = DateTimeUtil.now()
                )
            )
        } else {
            emptyList()
        }
        
        // Create the UserContext
        return DummyUserContext(
            user = user,
            session = session,
            permissions = permissions,
            organizationMemberships = organizationMemberships
        )
    }
}

/**
 * Helper class for authentication
 */
data class DummyUserContextPrincipal(val userContext: DummyUserContext)