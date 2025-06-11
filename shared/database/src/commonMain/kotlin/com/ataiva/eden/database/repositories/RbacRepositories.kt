package com.ataiva.eden.database.repositories

import com.ataiva.eden.core.models.Role
import com.ataiva.eden.database.Permission

/**
 * Repository for user roles
 */
interface UserRoleRepository {
    suspend fun findRolesByUserId(userId: String): List<Role>
    suspend fun findRolesByUserIdAndOrganizationId(userId: String, organizationId: String): List<Role>
    suspend fun assignRole(userId: String, roleId: String): Boolean
    suspend fun assignRole(userId: String, roleId: String, organizationId: String): Boolean
    suspend fun removeRole(userId: String, roleId: String): Boolean
    suspend fun removeRole(userId: String, roleId: String, organizationId: String): Boolean
}

/**
 * Repository for roles
 */
interface RoleRepository {
    suspend fun findById(id: String): Role?
    suspend fun findAll(): List<Role>
    suspend fun create(role: Role): Role
    suspend fun update(role: Role): Role
    suspend fun delete(id: String): Boolean
    suspend fun findByOrganizationId(organizationId: String): List<Role>
}

/**
 * Repository for permissions
 */
interface PermissionRepository {
    suspend fun findAll(): List<Permission>
    suspend fun findById(id: String): Permission?
    suspend fun create(permission: Permission): Permission
    suspend fun update(permission: Permission): Permission
    suspend fun delete(id: String): Boolean
}