package com.ataiva.eden.auth.rbac

import com.ataiva.eden.core.models.Permission
import com.ataiva.eden.core.models.Role
import com.ataiva.eden.core.models.User
import com.ataiva.eden.core.models.UserContext

/**
 * RBAC (Role-Based Access Control) service for Eden
 * Provides centralized authorization and permission checking
 */
interface RbacService {
    /**
     * Check if a user has a specific permission
     */
    suspend fun hasPermission(userId: String, permission: String): Boolean
    
    /**
     * Check if a user has a specific permission in an organization
     */
    suspend fun hasPermission(userId: String, permission: String, organizationId: String): Boolean
    
    /**
     * Check if a user has a specific permission for a resource
     */
    suspend fun hasPermission(userId: String, permission: String, resourceType: String, resourceId: String): Boolean
    
    /**
     * Get all permissions for a user
     */
    suspend fun getUserPermissions(userId: String): Set<String>
    
    /**
     * Get all permissions for a user in an organization
     */
    suspend fun getUserPermissions(userId: String, organizationId: String): Set<String>
    
    /**
     * Get all roles for a user
     */
    suspend fun getUserRoles(userId: String): List<Role>
    
    /**
     * Get all roles for a user in an organization
     */
    suspend fun getUserRoles(userId: String, organizationId: String): List<Role>
    
    /**
     * Assign a role to a user
     */
    suspend fun assignRole(userId: String, roleId: String): Boolean
    
    /**
     * Assign a role to a user in an organization
     */
    suspend fun assignRole(userId: String, roleId: String, organizationId: String): Boolean
    
    /**
     * Remove a role from a user
     */
    suspend fun removeRole(userId: String, roleId: String): Boolean
    
    /**
     * Remove a role from a user in an organization
     */
    suspend fun removeRole(userId: String, roleId: String, organizationId: String): Boolean
    
    /**
     * Create a new role with permissions
     */
    suspend fun createRole(name: String, description: String, permissions: Set<String>, organizationId: String? = null): Role
    
    /**
     * Update an existing role
     */
    suspend fun updateRole(roleId: String, name: String, description: String, permissions: Set<String>): Role
    
    /**
     * Delete a role
     */
    suspend fun deleteRole(roleId: String): Boolean
    
    /**
     * Get a role by ID
     */
    suspend fun getRole(roleId: String): Role?
    
    /**
     * Get all roles
     */
    suspend fun getAllRoles(): List<Role>
    
    /**
     * Get all roles in an organization
     */
    suspend fun getAllRoles(organizationId: String): List<Role>
    
    /**
     * Get all permissions
     */
    suspend fun getAllPermissions(): List<Permission>
    
    /**
     * Check if a user context has a specific permission
     * This is a synchronous method that can be used in API controllers
     */
    fun checkPermission(userContext: UserContext, permission: String): Boolean
    
    /**
     * Check if a user context has a specific permission for a resource
     * This is a synchronous method that can be used in API controllers
     */
    fun checkPermission(userContext: UserContext, permission: String, resourceType: String, resourceId: String): Boolean
}

/**
 * RBAC authorization result
 */
sealed class AuthorizationResult {
    object Authorized : AuthorizationResult()
    data class Unauthorized(val reason: String) : AuthorizationResult()
}

/**
 * RBAC exception thrown when a user doesn't have the required permission
 */
class UnauthorizedException(message: String) : RuntimeException(message)