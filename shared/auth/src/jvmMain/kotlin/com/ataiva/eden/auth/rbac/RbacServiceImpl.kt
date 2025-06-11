package com.ataiva.eden.auth.rbac

import com.ataiva.eden.core.models.Permission
import com.ataiva.eden.core.models.PermissionScope
import com.ataiva.eden.core.models.Role
import com.ataiva.eden.core.models.UserContext
import com.ataiva.eden.database.EdenDatabaseService
import com.ataiva.eden.auth.util.DateTimeUtil
import com.ataiva.eden.auth.util.Instant
import com.ataiva.eden.auth.util.InstantUtil
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

/**
 * Implementation of the RBAC service
 */
class RbacServiceImpl(
    private val databaseService: EdenDatabaseService
) : RbacService {
    
    // Cache for permissions to reduce database load
    private val permissionCache = ConcurrentHashMap<String, CachedPermissions>()
    
    // Cache expiration time
    private val cacheExpiration = 15.minutes
    
    override suspend fun hasPermission(userId: String, permission: String): Boolean {
        val permissions = getUserPermissions(userId)
        return permissions.contains(permission) || permissions.contains("*:*")
    }
    
    override suspend fun hasPermission(userId: String, permission: String, organizationId: String): Boolean {
        val permissions = getUserPermissions(userId, organizationId)
        return permissions.contains(permission) || 
               permissions.contains("*:*") || 
               permissions.contains("${permission.split(":")[0]}:*")
    }
    
    override suspend fun hasPermission(userId: String, permission: String, resourceType: String, resourceId: String): Boolean {
        val permissions = getUserPermissions(userId)
        
        // Check for direct resource permission
        val resourcePermission = "$permission:$resourceType:$resourceId"
        if (permissions.contains(resourcePermission)) {
            return true
        }
        
        // Check for resource type permission
        val resourceTypePermission = "$permission:$resourceType:*"
        if (permissions.contains(resourceTypePermission)) {
            return true
        }
        
        // Check for general permission
        return permissions.contains(permission) || 
               permissions.contains("*:*") || 
               permissions.contains("${permission.split(":")[0]}:*")
    }
    
    override suspend fun getUserPermissions(userId: String): Set<String> {
        val cacheKey = "user:$userId"
        val cached = permissionCache[cacheKey]
        
        if (cached != null && !cached.isExpired()) {
            return cached.permissions
        }
        
        // Get user roles from database
        val roles = getUserRoles(userId)
        
        // Collect all permissions from roles
        val permissions = roles.flatMap { it.permissions }.toSet()
        
        // Cache the permissions
        permissionCache[cacheKey] = CachedPermissions(permissions)
        
        return permissions
    }
    
    override suspend fun getUserPermissions(userId: String, organizationId: String): Set<String> {
        val cacheKey = "user:$userId:org:$organizationId"
        val cached = permissionCache[cacheKey]
        
        if (cached != null && !cached.isExpired()) {
            return cached.permissions
        }
        
        // Get user roles for the organization from database
        val roles = getUserRoles(userId, organizationId)
        
        // Collect all permissions from roles
        val permissions = roles.flatMap { it.permissions }.toSet()
        
        // Cache the permissions
        permissionCache[cacheKey] = CachedPermissions(permissions)
        
        return permissions
    }
    
    override suspend fun getUserRoles(userId: String): List<Role> {
        return databaseService.userRoleRepository.findRolesByUserId(userId)
    }
    
    override suspend fun getUserRoles(userId: String, organizationId: String): List<Role> {
        return databaseService.userRoleRepository.findRolesByUserIdAndOrganizationId(userId, organizationId)
    }
    
    override suspend fun assignRole(userId: String, roleId: String): Boolean {
        val result = databaseService.userRoleRepository.assignRole(userId, roleId)
        
        // Invalidate cache
        invalidateUserCache(userId)
        
        return result
    }
    
    override suspend fun assignRole(userId: String, roleId: String, organizationId: String): Boolean {
        val result = databaseService.userRoleRepository.assignRole(userId, roleId, organizationId)
        
        // Invalidate cache
        invalidateUserCache(userId)
        invalidateUserOrgCache(userId, organizationId)
        
        return result
    }
    
    override suspend fun removeRole(userId: String, roleId: String): Boolean {
        val result = databaseService.userRoleRepository.removeRole(userId, roleId)
        
        // Invalidate cache
        invalidateUserCache(userId)
        
        return result
    }
    
    override suspend fun removeRole(userId: String, roleId: String, organizationId: String): Boolean {
        val result = databaseService.userRoleRepository.removeRole(userId, roleId, organizationId)
        
        // Invalidate cache
        invalidateUserCache(userId)
        invalidateUserOrgCache(userId, organizationId)
        
        return result
    }
    
    override suspend fun createRole(name: String, description: String, permissions: Set<String>, organizationId: String?): Role {
        val roleId = java.util.UUID.randomUUID().toString()
        val now = DateTimeUtil.now()
        
        val role = Role(
            id = roleId,
            name = name,
            description = description,
            permissions = permissions,
            isBuiltIn = false,
            organizationId = organizationId,
            createdAt = InstantUtil.dummyInstant(),
            updatedAt = InstantUtil.dummyInstant()
        )
        
        return databaseService.roleRepository.create(role)
    }
    
    override suspend fun updateRole(roleId: String, name: String, description: String, permissions: Set<String>): Role {
        val existingRole = databaseService.roleRepository.findById(roleId)
            ?: throw IllegalArgumentException("Role not found: $roleId")
        
        val updatedRole = existingRole.copy(
            name = name,
            description = description,
            permissions = permissions,
            updatedAt = InstantUtil.dummyInstant()
        )
        
        val result = databaseService.roleRepository.update(updatedRole)
        
        // Invalidate all caches since we don't know which users have this role
        permissionCache.clear()
        
        return result
    }
    
    override suspend fun deleteRole(roleId: String): Boolean {
        val result = databaseService.roleRepository.delete(roleId)
        
        // Invalidate all caches since we don't know which users have this role
        permissionCache.clear()
        
        return result
    }
    
    override suspend fun getRole(roleId: String): Role? {
        return databaseService.roleRepository.findById(roleId)
    }
    
    override suspend fun getAllRoles(): List<Role> {
        return databaseService.roleRepository.findAll()
    }
    
    override suspend fun getAllRoles(organizationId: String): List<Role> {
        return databaseService.roleRepository.findByOrganizationId(organizationId)
    }
    
    override suspend fun getAllPermissions(): List<Permission> {
        return databaseService.permissionRepository.findAll().map { dbPermission ->
            Permission(
                id = dbPermission.id,
                name = dbPermission.name,
                description = dbPermission.description ?: "",
                resource = dbPermission.resource,
                action = dbPermission.action,
                scope = PermissionScope.ORGANIZATION,
                isActive = true,
                createdAt = InstantUtil.dummyInstant(),
                updatedAt = InstantUtil.dummyInstant()
            )
        }
    }
    
    override fun checkPermission(userContext: UserContext, permission: String): Boolean {
        return userContext.permissions.any { it.name == permission } ||
               userContext.permissions.any { it.name == "*:*" } ||
               userContext.permissions.any { it.name == "${permission.split(":")[0]}:*" }
    }
    
    override fun checkPermission(userContext: UserContext, permission: String, resourceType: String, resourceId: String): Boolean {
        // Check for direct resource permission
        val resourcePermission = "$permission:$resourceType:$resourceId"
        if (userContext.permissions.any { it.name == resourcePermission }) {
            return true
        }
        
        // Check for resource type permission
        val resourceTypePermission = "$permission:$resourceType:*"
        if (userContext.permissions.any { it.name == resourceTypePermission }) {
            return true
        }
        
        // Check for general permission
        return userContext.permissions.any { it.name == permission } ||
               userContext.permissions.any { it.name == "*:*" } ||
               userContext.permissions.any { it.name == "${permission.split(":")[0]}:*" }
    }
    
    /**
     * Invalidate user cache
     */
    private fun invalidateUserCache(userId: String) {
        permissionCache.remove("user:$userId")
    }
    
    /**
     * Invalidate user organization cache
     */
    private fun invalidateUserOrgCache(userId: String, organizationId: String) {
        permissionCache.remove("user:$userId:org:$organizationId")
    }
    
    /**
     * Cached permissions with expiration time
     */
    private inner class CachedPermissions(
        val permissions: Set<String>,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - timestamp > cacheExpiration.inWholeMilliseconds
        }
    }
}