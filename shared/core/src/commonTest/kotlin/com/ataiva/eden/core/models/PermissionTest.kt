package com.ataiva.eden.core.models

import com.ataiva.eden.testing.mocks.MockFactory
import com.ataiva.eden.testing.mocks.MockTimeProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class PermissionTest : DescribeSpec({
    
    describe("Permission model") {
        
        describe("construction") {
            it("should create permission with all required fields") {
                val now = MockTimeProvider.fixedInstant()
                val permission = Permission(
                    id = "perm-123",
                    name = "vault:read",
                    description = "Read access to vault",
                    resource = "vault",
                    action = "read",
                    scope = PermissionScope.ORGANIZATION,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now
                )
                
                permission.id shouldBe "perm-123"
                permission.name shouldBe "vault:read"
                permission.description shouldBe "Read access to vault"
                permission.resource shouldBe "vault"
                permission.action shouldBe "read"
                permission.scope shouldBe PermissionScope.ORGANIZATION
                permission.isActive shouldBe true
                permission.createdAt shouldBe now
                permission.updatedAt shouldBe now
            }
            
            it("should create permission with default scope") {
                val now = MockTimeProvider.fixedInstant()
                val permission = Permission(
                    id = "perm-123",
                    name = "org:read",
                    description = "Read organization",
                    resource = "org",
                    action = "read",
                    createdAt = now,
                    updatedAt = now
                )
                
                permission.scope shouldBe PermissionScope.ORGANIZATION
                permission.isActive shouldBe true
            }
            
            it("should create permission with different scopes") {
                val now = MockTimeProvider.fixedInstant()
                
                PermissionScope.values().forEach { scope ->
                    val permission = Permission(
                        id = "perm-${scope.name}",
                        name = "test:${scope.name.lowercase()}",
                        description = "Test permission for ${scope.name}",
                        resource = "test",
                        action = scope.name.lowercase(),
                        scope = scope,
                        createdAt = now,
                        updatedAt = now
                    )
                    
                    permission.scope shouldBe scope
                }
            }
        }
        
        describe("factory methods") {
            it("should create permission using mock factory") {
                val permission = MockFactory.createMockPermission()
                
                permission.id.shouldNotBeNull()
                permission.name shouldBe "test:read"
                permission.resource shouldBe "test"
                permission.action shouldBe "read"
                permission.scope shouldBe PermissionScope.ORGANIZATION
                permission.isActive shouldBe true
            }
            
            it("should create permission with custom values") {
                val permission = MockFactory.createMockPermission(
                    id = "custom-perm",
                    name = "vault:write",
                    resource = "vault",
                    action = "write"
                )
                
                permission.id shouldBe "custom-perm"
                permission.name shouldBe "vault:write"
                permission.resource shouldBe "vault"
                permission.action shouldBe "write"
            }
        }
        
        describe("property-based testing") {
            it("should handle arbitrary permission names") {
                checkAll(Arb.string(1..50)) { name ->
                    val now = MockTimeProvider.fixedInstant()
                    val permission = Permission(
                        id = "perm-test",
                        name = name,
                        description = "Test permission",
                        resource = "test",
                        action = "test",
                        createdAt = now,
                        updatedAt = now
                    )
                    
                    permission.name shouldBe name
                }
            }
            
            it("should handle arbitrary resources and actions") {
                checkAll(Arb.string(1..20), Arb.string(1..20)) { resource, action ->
                    val now = MockTimeProvider.fixedInstant()
                    val permission = Permission(
                        id = "perm-test",
                        name = "$resource:$action",
                        description = "Test permission",
                        resource = resource,
                        action = action,
                        createdAt = now,
                        updatedAt = now
                    )
                    
                    permission.resource shouldBe resource
                    permission.action shouldBe action
                }
            }
        }
        
        describe("edge cases") {
            it("should handle inactive permission") {
                val now = MockTimeProvider.fixedInstant()
                val permission = Permission(
                    id = "perm-123",
                    name = "test:read",
                    description = "Test permission",
                    resource = "test",
                    action = "read",
                    isActive = false,
                    createdAt = now,
                    updatedAt = now
                )
                
                permission.isActive shouldBe false
            }
            
            it("should handle empty description") {
                val now = MockTimeProvider.fixedInstant()
                val permission = Permission(
                    id = "perm-123",
                    name = "test:read",
                    description = "",
                    resource = "test",
                    action = "read",
                    createdAt = now,
                    updatedAt = now
                )
                
                permission.description shouldBe ""
            }
        }
    }
    
    describe("PermissionScope enum") {
        
        it("should have all expected scope types") {
            val scopes = PermissionScope.values()
            
            scopes shouldContain PermissionScope.GLOBAL
            scopes shouldContain PermissionScope.ORGANIZATION
            scopes shouldContain PermissionScope.PROJECT
            scopes shouldContain PermissionScope.RESOURCE
        }
        
        it("should maintain scope ordering") {
            val scopes = PermissionScope.values()
            
            scopes[0] shouldBe PermissionScope.GLOBAL
            scopes[1] shouldBe PermissionScope.ORGANIZATION
            scopes[2] shouldBe PermissionScope.PROJECT
            scopes[3] shouldBe PermissionScope.RESOURCE
        }
    }
    
    describe("Role model") {
        
        describe("construction") {
            it("should create role with all required fields") {
                val now = MockTimeProvider.fixedInstant()
                val permissions = setOf("org:read", "vault:read", "flow:read")
                val role = Role(
                    id = "role-123",
                    name = "Developer",
                    description = "Developer role",
                    permissions = permissions,
                    isBuiltIn = false,
                    organizationId = "org-123",
                    createdAt = now,
                    updatedAt = now
                )
                
                role.id shouldBe "role-123"
                role.name shouldBe "Developer"
                role.description shouldBe "Developer role"
                role.permissions shouldBe permissions
                role.isBuiltIn shouldBe false
                role.organizationId shouldBe "org-123"
                role.createdAt shouldBe now
                role.updatedAt shouldBe now
            }
            
            it("should create built-in role") {
                val now = MockTimeProvider.fixedInstant()
                val role = Role(
                    id = "role-builtin",
                    name = "Admin",
                    description = "Built-in admin role",
                    permissions = setOf("admin:all"),
                    isBuiltIn = true,
                    organizationId = null,
                    createdAt = now,
                    updatedAt = now
                )
                
                role.isBuiltIn shouldBe true
                role.organizationId.shouldBeNull()
            }
            
            it("should create role with default values") {
                val now = MockTimeProvider.fixedInstant()
                val role = Role(
                    id = "role-123",
                    name = "Custom Role",
                    description = "Custom role",
                    permissions = emptySet(),
                    createdAt = now,
                    updatedAt = now
                )
                
                role.isBuiltIn shouldBe false
                role.organizationId.shouldBeNull()
            }
        }
        
        describe("edge cases") {
            it("should handle empty permissions") {
                val now = MockTimeProvider.fixedInstant()
                val role = Role(
                    id = "role-123",
                    name = "Empty Role",
                    description = "Role with no permissions",
                    permissions = emptySet(),
                    createdAt = now,
                    updatedAt = now
                )
                
                role.permissions shouldHaveSize 0
            }
            
            it("should handle many permissions") {
                val now = MockTimeProvider.fixedInstant()
                val manyPermissions = (1..100).map { "perm:$it" }.toSet()
                val role = Role(
                    id = "role-123",
                    name = "Super Role",
                    description = "Role with many permissions",
                    permissions = manyPermissions,
                    createdAt = now,
                    updatedAt = now
                )
                
                role.permissions shouldHaveSize 100
                role.permissions shouldContainAll manyPermissions
            }
        }
    }
    
    describe("BuiltInRoles object") {
        
        describe("OWNER permissions") {
            it("should have comprehensive admin permissions") {
                val ownerPerms = BuiltInRoles.OWNER_PERMISSIONS
                
                ownerPerms shouldContain "org:admin"
                ownerPerms shouldContain "vault:admin"
                ownerPerms shouldContain "flow:admin"
                ownerPerms shouldContain "task:admin"
                ownerPerms shouldContain "monitor:admin"
                ownerPerms shouldContain "sync:admin"
                ownerPerms shouldContain "insight:admin"
                ownerPerms shouldContain "hub:admin"
            }
            
            it("should have expected number of permissions") {
                val ownerPerms = BuiltInRoles.OWNER_PERMISSIONS
                ownerPerms shouldHaveSize 8
            }
        }
        
        describe("ADMIN permissions") {
            it("should have admin permissions without org:admin") {
                val adminPerms = BuiltInRoles.ADMIN_PERMISSIONS
                
                adminPerms shouldContain "org:read"
                adminPerms shouldContain "org:write"
                adminPerms shouldContain "vault:admin"
                adminPerms shouldContain "flow:admin"
                adminPerms shouldContain "task:admin"
                adminPerms shouldContain "monitor:admin"
                adminPerms shouldContain "sync:admin"
                adminPerms shouldContain "insight:admin"
                adminPerms shouldContain "hub:admin"
            }
            
            it("should have expected number of permissions") {
                val adminPerms = BuiltInRoles.ADMIN_PERMISSIONS
                adminPerms shouldHaveSize 9
            }
        }
        
        describe("DEVELOPER permissions") {
            it("should have read/write permissions without admin") {
                val devPerms = BuiltInRoles.DEVELOPER_PERMISSIONS
                
                devPerms shouldContain "org:read"
                devPerms shouldContain "vault:read"
                devPerms shouldContain "vault:write"
                devPerms shouldContain "flow:read"
                devPerms shouldContain "flow:write"
                devPerms shouldContain "flow:execute"
                devPerms shouldContain "task:read"
                devPerms shouldContain "task:write"
                devPerms shouldContain "task:execute"
                devPerms shouldContain "monitor:read"
                devPerms shouldContain "sync:read"
                devPerms shouldContain "insight:read"
                devPerms shouldContain "hub:read"
            }
            
            it("should not have admin permissions") {
                val devPerms = BuiltInRoles.DEVELOPER_PERMISSIONS
                
                devPerms.none { it.contains("admin") } shouldBe true
            }
            
            it("should have expected number of permissions") {
                val devPerms = BuiltInRoles.DEVELOPER_PERMISSIONS
                devPerms shouldHaveSize 13
            }
        }
        
        describe("VIEWER permissions") {
            it("should have only read permissions") {
                val viewerPerms = BuiltInRoles.VIEWER_PERMISSIONS
                
                viewerPerms shouldContain "org:read"
                viewerPerms shouldContain "vault:read"
                viewerPerms shouldContain "flow:read"
                viewerPerms shouldContain "task:read"
                viewerPerms shouldContain "monitor:read"
                viewerPerms shouldContain "sync:read"
                viewerPerms shouldContain "insight:read"
                viewerPerms shouldContain "hub:read"
            }
            
            it("should not have write or admin permissions") {
                val viewerPerms = BuiltInRoles.VIEWER_PERMISSIONS
                
                viewerPerms.none { it.contains("write") || it.contains("admin") || it.contains("execute") } shouldBe true
            }
            
            it("should have expected number of permissions") {
                val viewerPerms = BuiltInRoles.VIEWER_PERMISSIONS
                viewerPerms shouldHaveSize 8
            }
        }
        
        describe("permission hierarchy") {
            it("should have proper permission count hierarchy") {
                val ownerCount = BuiltInRoles.OWNER_PERMISSIONS.size
                val adminCount = BuiltInRoles.ADMIN_PERMISSIONS.size
                val devCount = BuiltInRoles.DEVELOPER_PERMISSIONS.size
                val viewerCount = BuiltInRoles.VIEWER_PERMISSIONS.size
                
                // DEVELOPER has most permissions due to execute permissions
                devCount shouldBe 13
                adminCount shouldBe 9
                ownerCount shouldBe 8
                viewerCount shouldBe 8
            }
            
            it("should have unique permission sets") {
                val owner = BuiltInRoles.OWNER_PERMISSIONS
                val admin = BuiltInRoles.ADMIN_PERMISSIONS
                val dev = BuiltInRoles.DEVELOPER_PERMISSIONS
                val viewer = BuiltInRoles.VIEWER_PERMISSIONS
                
                owner shouldNotBe admin
                admin shouldNotBe dev
                dev shouldNotBe viewer
                owner shouldNotBe viewer
            }
        }
    }
})