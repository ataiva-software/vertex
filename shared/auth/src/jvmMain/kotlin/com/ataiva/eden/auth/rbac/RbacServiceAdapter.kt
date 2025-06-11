package com.ataiva.eden.auth.rbac

import com.ataiva.eden.core.models.Role

/**
 * Simplified adapter class that implements a subset of RbacService
 * This is a workaround for kotlinx.datetime.Instant compatibility issues
 */
class RbacServiceAdapter(private val delegate: RbacServiceImpl) {
    
    // Delegate basic permission checking methods
    suspend fun hasPermission(userId: String, permission: String): Boolean {
        return delegate.hasPermission(userId, permission)
    }
    
    suspend fun hasPermission(userId: String, permission: String, organizationId: String): Boolean {
        return delegate.hasPermission(userId, permission, organizationId)
    }
    
    suspend fun hasPermission(userId: String, permission: String, resourceType: String, resourceId: String): Boolean {
        return delegate.hasPermission(userId, permission, resourceType, resourceId)
    }
    
    // Delegate role management methods
    suspend fun getUserRoles(userId: String): List<Role> {
        return delegate.getUserRoles(userId)
    }
    
    suspend fun getUserRoles(userId: String, organizationId: String): List<Role> {
        return delegate.getUserRoles(userId, organizationId)
    }
    
    suspend fun assignRole(userId: String, roleId: String): Boolean {
        return delegate.assignRole(userId, roleId)
    }
    
    suspend fun assignRole(userId: String, roleId: String, organizationId: String): Boolean {
        return delegate.assignRole(userId, roleId, organizationId)
    }
    
    suspend fun removeRole(userId: String, roleId: String): Boolean {
        return delegate.removeRole(userId, roleId)
    }
    
    suspend fun removeRole(userId: String, roleId: String, organizationId: String): Boolean {
        return delegate.removeRole(userId, roleId, organizationId)
    }
}