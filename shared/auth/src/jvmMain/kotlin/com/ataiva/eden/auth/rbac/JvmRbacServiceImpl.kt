package com.ataiva.eden.auth.rbac

import com.ataiva.eden.auth.models.Permission
import com.ataiva.eden.auth.models.UserContext
import com.ataiva.eden.core.models.Role
import com.ataiva.eden.database.EdenDatabaseService

/**
 * JVM-specific implementation of the RBAC service
 * This is a workaround for kotlinx.datetime.Instant compatibility issues
 */
class JvmRbacServiceImpl(
    private val rbacService: RbacService
) : JvmRbacService {
    
    override suspend fun hasPermission(userId: String, permission: String): Boolean {
        return rbacService.hasPermission(userId, permission)
    }
    
    override suspend fun hasPermission(userId: String, permission: String, organizationId: String): Boolean {
        return rbacService.hasPermission(userId, permission, organizationId)
    }
    
    override suspend fun hasPermission(userId: String, permission: String, resourceType: String, resourceId: String): Boolean {
        return rbacService.hasPermission(userId, permission, resourceType, resourceId)
    }
    
    override suspend fun getUserPermissions(userId: String): Set<String> {
        return rbacService.getUserPermissions(userId)
    }
    
    override suspend fun getUserPermissions(userId: String, organizationId: String): Set<String> {
        return rbacService.getUserPermissions(userId, organizationId)
    }
    
    override suspend fun getUserRoles(userId: String): List<Role> {
        return rbacService.getUserRoles(userId)
    }
    
    override suspend fun getUserRoles(userId: String, organizationId: String): List<Role> {
        return rbacService.getUserRoles(userId, organizationId)
    }
    
    override suspend fun assignRole(userId: String, roleId: String): Boolean {
        return rbacService.assignRole(userId, roleId)
    }
    
    override suspend fun assignRole(userId: String, roleId: String, organizationId: String): Boolean {
        return rbacService.assignRole(userId, roleId, organizationId)
    }
    
    override suspend fun removeRole(userId: String, roleId: String): Boolean {
        return rbacService.removeRole(userId, roleId)
    }
    
    override suspend fun removeRole(userId: String, roleId: String, organizationId: String): Boolean {
        return rbacService.removeRole(userId, roleId, organizationId)
    }
    
    override suspend fun createRole(name: String, description: String, permissions: Set<String>, organizationId: String?): Role {
        return rbacService.createRole(name, description, permissions, organizationId)
    }
    
    override suspend fun updateRole(roleId: String, name: String, description: String, permissions: Set<String>): Role {
        return rbacService.updateRole(roleId, name, description, permissions)
    }
    
    override suspend fun deleteRole(roleId: String): Boolean {
        return rbacService.deleteRole(roleId)
    }
    
    override suspend fun getRole(roleId: String): Role? {
        return rbacService.getRole(roleId)
    }
    
    override suspend fun getAllRoles(): List<Role> {
        return rbacService.getAllRoles()
    }
    
    override suspend fun getAllRoles(organizationId: String): List<Role> {
        return rbacService.getAllRoles(organizationId)
    }
    
    override suspend fun getAllPermissions(): List<Permission> {
        // Convert core Permission to auth Permission
        return rbacService.getAllPermissions().map { corePermission ->
            Permission(
                id = corePermission.id,
                name = corePermission.name,
                description = corePermission.description,
                resource = corePermission.resource,
                action = corePermission.action,
                scope = com.ataiva.eden.auth.models.AuthPermissionScope.valueOf(corePermission.scope.name),
                isActive = corePermission.isActive,
                createdAt = com.ataiva.eden.auth.util.DateTimeUtil.now(),
                updatedAt = com.ataiva.eden.auth.util.DateTimeUtil.now()
            )
        }
    }
    
    override fun checkPermission(userContext: UserContext, permission: String): Boolean {
        // Convert auth UserContext to core UserContext
        val coreUserContext = userContext.toCoreUserContext()
        return rbacService.checkPermission(coreUserContext, permission)
    }
    
    override fun checkPermission(userContext: UserContext, permission: String, resourceType: String, resourceId: String): Boolean {
        // Convert auth UserContext to core UserContext
        val coreUserContext = userContext.toCoreUserContext()
        return rbacService.checkPermission(coreUserContext, permission, resourceType, resourceId)
    }
}