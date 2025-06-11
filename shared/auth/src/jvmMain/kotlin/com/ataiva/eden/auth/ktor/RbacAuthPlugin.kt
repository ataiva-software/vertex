package com.ataiva.eden.auth.ktor

import com.ataiva.eden.auth.rbac.RbacService
import com.ataiva.eden.auth.rbac.UnauthorizedException
import com.ataiva.eden.auth.models.UserContext
import com.ataiva.eden.auth.models.AuthUser
import com.ataiva.eden.auth.models.AuthUserSession
import com.ataiva.eden.auth.models.Permission
import com.ataiva.eden.auth.models.AuthOrganizationMembership
import com.ataiva.eden.auth.util.DateTimeUtil

/**
 * RBAC authentication plugin for Ktor
 * Provides role-based access control for API endpoints
 * 
 * This is a simplified implementation that avoids direct Ktor dependencies
 * to fix compilation issues. The actual implementation would use Ktor components.
 */
class RbacAuthPlugin(configuration: Configuration) {
    private val rbacService = configuration.rbacService
    
    /**
     * Configuration for the RBAC authentication plugin
     */
    class Configuration {
        var rbacService: RbacService? = null
    }
    
    /**
     * Check if a user has a specific permission
     */
    fun checkPermission(userContext: UserContext, permission: String): Boolean {
        // Convert AuthUserContext to core UserContext for the RbacService
        val coreUserContext = userContext.toCoreUserContext()
        return rbacService?.checkPermission(coreUserContext, permission) ?: false
    }
    
    /**
     * Check if a user has a specific permission for a resource
     */
    fun checkPermission(userContext: UserContext, permission: String, resourceType: String, resourceId: String): Boolean {
        // Convert AuthUserContext to core UserContext for the RbacService
        val coreUserContext = userContext.toCoreUserContext()
        return rbacService?.checkPermission(coreUserContext, permission, resourceType, resourceId) ?: false
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
    ): UserContext {
        // Create a minimal AuthUser object
        val user = AuthUser(
            id = userId,
            email = email,
            passwordHash = null,
            createdAt = DateTimeUtil.now(),
            updatedAt = DateTimeUtil.now()
        )
        
        // Create a minimal AuthUserSession object
        val session = AuthUserSession(
            id = sessionId ?: userId,
            userId = userId,
            token = "token",
            expiresAt = DateTimeUtil.now(),
            createdAt = DateTimeUtil.now()
        )
        
        // Convert string permissions to Permission objects
        val permissions = permissionStrings.mapIndexed { index, name ->
            Permission(
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
                AuthOrganizationMembership(
                    id = "org-1",
                    userId = userId,
                    organizationId = organizationId,
                    role = role,
                    createdAt = DateTimeUtil.now(),
                    updatedAt = DateTimeUtil.now()
                )
            )
        } else {
            emptyList<AuthOrganizationMembership>()
        }
        
        // Create the UserContext
        return UserContext(
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
data class UserContextPrincipal(val userContext: UserContext)