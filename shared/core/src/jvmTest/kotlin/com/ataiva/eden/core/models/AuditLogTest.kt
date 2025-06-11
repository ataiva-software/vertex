package com.ataiva.eden.core.models

import com.ataiva.eden.testing.extensions.shouldHaveSize
import com.ataiva.eden.testing.mocks.MockTimeProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class AuditLogTest : DescribeSpec({
    
    describe("AuditLog model") {
        
        describe("construction") {
            it("should create audit log with all required fields") {
                val now = MockTimeProvider.fixedInstant()
                val details = mapOf("action" to "create", "resource_id" to "123")
                val auditLog = AuditLog(
                    id = "audit-123",
                    organizationId = "org-123",
                    userId = "user-123",
                    action = AuditActions.USER_CREATED,
                    resource = AuditResources.USER,
                    resourceId = "user-123",
                    details = details,
                    ipAddress = "192.168.1.1",
                    userAgent = "Mozilla/5.0",
                    timestamp = now,
                    severity = AuditSeverity.INFO
                )
                
                auditLog.id shouldBe "audit-123"
                auditLog.organizationId shouldBe "org-123"
                auditLog.userId shouldBe "user-123"
                auditLog.action shouldBe AuditActions.USER_CREATED
                auditLog.resource shouldBe AuditResources.USER
                auditLog.resourceId shouldBe "user-123"
                auditLog.details shouldBe details
                auditLog.ipAddress shouldBe "192.168.1.1"
                auditLog.userAgent shouldBe "Mozilla/5.0"
                auditLog.timestamp shouldBe now
                auditLog.severity shouldBe AuditSeverity.INFO
            }
            
            it("should create audit log with optional fields as null") {
                val now = MockTimeProvider.fixedInstant()
                val auditLog = AuditLog(
                    id = "audit-123",
                    organizationId = "org-123",
                    userId = null,
                    action = AuditActions.SYSTEM_STARTED,
                    resource = AuditResources.SYSTEM,
                    resourceId = null,
                    ipAddress = null,
                    userAgent = null,
                    timestamp = now
                )
                
                auditLog.userId.shouldBeNull()
                auditLog.resourceId.shouldBeNull()
                auditLog.details shouldBe emptyMap()
                auditLog.ipAddress.shouldBeNull()
                auditLog.userAgent.shouldBeNull()
                auditLog.severity shouldBe AuditSeverity.INFO
            }
            
            it("should create audit log with different severities") {
                val now = MockTimeProvider.fixedInstant()
                
                AuditSeverity.values().forEach { severity ->
                    val auditLog = AuditLog(
                        id = "audit-${severity.name}",
                        organizationId = "org-123",
                        userId = "user-123",
                        action = "test.action",
                        resource = "test",
                        resourceId = "test-123",
                        ipAddress = "127.0.0.1",
                        userAgent = "Test Agent",
                        timestamp = now,
                        severity = severity
                    )
                    
                    auditLog.severity shouldBe severity
                }
            }
        }
        
        describe("property-based testing") {
            it("should handle arbitrary action names") {
                checkAll(Arb.string(1..100)) { action ->
                    val now = MockTimeProvider.fixedInstant()
                    val auditLog = AuditLog(
                        id = "audit-test",
                        organizationId = "org-123",
                        userId = "user-123",
                        action = action,
                        resource = "test",
                        resourceId = "test-123",
                        ipAddress = "127.0.0.1",
                        userAgent = "Test Agent",
                        timestamp = now
                    )
                    
                    auditLog.action shouldBe action
                }
            }
            
            it("should handle arbitrary resource names") {
                checkAll(Arb.string(1..50)) { resource ->
                    val now = MockTimeProvider.fixedInstant()
                    val auditLog = AuditLog(
                        id = "audit-test",
                        organizationId = "org-123",
                        userId = "user-123",
                        action = "test.action",
                        resource = resource,
                        resourceId = "test-123",
                        ipAddress = "127.0.0.1",
                        userAgent = "Test Agent",
                        timestamp = now
                    )
                    
                    auditLog.resource shouldBe resource
                }
            }
        }
        
        describe("edge cases") {
            it("should handle empty details map") {
                val now = MockTimeProvider.fixedInstant()
                val auditLog = AuditLog(
                    id = "audit-123",
                    organizationId = "org-123",
                    userId = "user-123",
                    action = "test.action",
                    resource = "test",
                    resourceId = "test-123",
                    details = emptyMap(),
                    ipAddress = "127.0.0.1",
                    userAgent = "Test Agent",
                    timestamp = now
                )
                
                auditLog.details shouldBe emptyMap()
            }
            
            it("should handle large details map") {
                val now = MockTimeProvider.fixedInstant()
                val largeDetails = (1..100).associate { "key$it" to "value$it" }
                val auditLog = AuditLog(
                    id = "audit-123",
                    organizationId = "org-123",
                    userId = "user-123",
                    action = "test.action",
                    resource = "test",
                    resourceId = "test-123",
                    details = largeDetails,
                    ipAddress = "127.0.0.1",
                    userAgent = "Test Agent",
                    timestamp = now
                )
                
                auditLog.details shouldHaveSize 100
                auditLog.details["key1"] shouldBe "value1"
                auditLog.details["key100"] shouldBe "value100"
            }
        }
    }
    
    describe("AuditSeverity enum") {
        
        it("should have all expected severity levels") {
            val severities = AuditSeverity.values()
            
            severities shouldContain AuditSeverity.DEBUG
            severities shouldContain AuditSeverity.INFO
            severities shouldContain AuditSeverity.WARNING
            severities shouldContain AuditSeverity.ERROR
            severities shouldContain AuditSeverity.CRITICAL
        }
        
        it("should maintain severity ordering") {
            val severities = AuditSeverity.values()
            
            severities[0] shouldBe AuditSeverity.DEBUG
            severities[1] shouldBe AuditSeverity.INFO
            severities[2] shouldBe AuditSeverity.WARNING
            severities[3] shouldBe AuditSeverity.ERROR
            severities[4] shouldBe AuditSeverity.CRITICAL
        }
        
        it("should have expected number of severity levels") {
            val severities = AuditSeverity.values()
            severities shouldHaveSize 5
        }
    }
    
    describe("AuditEvent model") {
        
        describe("construction") {
            it("should create audit event with all required fields") {
                val details = mapOf("field" to "value")
                val event = AuditEvent(
                    action = AuditActions.USER_UPDATED,
                    resource = AuditResources.USER,
                    resourceId = "user-123",
                    details = details,
                    severity = AuditSeverity.WARNING
                )
                
                event.action shouldBe AuditActions.USER_UPDATED
                event.resource shouldBe AuditResources.USER
                event.resourceId shouldBe "user-123"
                event.details shouldBe details
                event.severity shouldBe AuditSeverity.WARNING
            }
            
            it("should create audit event with default values") {
                val event = AuditEvent(
                    action = "test.action",
                    resource = "test",
                    resourceId = "test-123"
                )
                
                event.details shouldBe emptyMap()
                event.severity shouldBe AuditSeverity.INFO
            }
            
            it("should create audit event with null resource ID") {
                val event = AuditEvent(
                    action = AuditActions.SYSTEM_STARTED,
                    resource = AuditResources.SYSTEM,
                    resourceId = null
                )
                
                event.resourceId.shouldBeNull()
            }
        }
    }
    
    describe("AuditActions object") {
        
        describe("authentication actions") {
            it("should have all authentication action constants") {
                AuditActions.LOGIN shouldBe "auth.login"
                AuditActions.LOGOUT shouldBe "auth.logout"
                AuditActions.LOGIN_FAILED shouldBe "auth.login_failed"
                AuditActions.PASSWORD_CHANGED shouldBe "auth.password_changed"
                AuditActions.MFA_ENABLED shouldBe "auth.mfa_enabled"
                AuditActions.MFA_DISABLED shouldBe "auth.mfa_disabled"
            }
        }
        
        describe("user management actions") {
            it("should have all user management action constants") {
                AuditActions.USER_CREATED shouldBe "user.created"
                AuditActions.USER_UPDATED shouldBe "user.updated"
                AuditActions.USER_DELETED shouldBe "user.deleted"
                AuditActions.USER_INVITED shouldBe "user.invited"
                AuditActions.USER_ACTIVATED shouldBe "user.activated"
                AuditActions.USER_DEACTIVATED shouldBe "user.deactivated"
            }
        }
        
        describe("organization actions") {
            it("should have all organization action constants") {
                AuditActions.ORG_CREATED shouldBe "org.created"
                AuditActions.ORG_UPDATED shouldBe "org.updated"
                AuditActions.ORG_DELETED shouldBe "org.deleted"
                AuditActions.ORG_MEMBER_ADDED shouldBe "org.member_added"
                AuditActions.ORG_MEMBER_REMOVED shouldBe "org.member_removed"
                AuditActions.ORG_ROLE_CHANGED shouldBe "org.role_changed"
            }
        }
        
        describe("vault actions") {
            it("should have all vault action constants") {
                AuditActions.SECRET_CREATED shouldBe "vault.secret_created"
                AuditActions.SECRET_ACCESSED shouldBe "vault.secret_accessed"
                AuditActions.SECRET_UPDATED shouldBe "vault.secret_updated"
                AuditActions.SECRET_DELETED shouldBe "vault.secret_deleted"
                AuditActions.SECRET_SHARED shouldBe "vault.secret_shared"
            }
        }
        
        describe("flow actions") {
            it("should have all flow action constants") {
                AuditActions.WORKFLOW_CREATED shouldBe "flow.workflow_created"
                AuditActions.WORKFLOW_UPDATED shouldBe "flow.workflow_updated"
                AuditActions.WORKFLOW_DELETED shouldBe "flow.workflow_deleted"
                AuditActions.WORKFLOW_EXECUTED shouldBe "flow.workflow_executed"
                AuditActions.WORKFLOW_FAILED shouldBe "flow.workflow_failed"
            }
        }
        
        describe("task actions") {
            it("should have all task action constants") {
                AuditActions.TASK_CREATED shouldBe "task.created"
                AuditActions.TASK_STARTED shouldBe "task.started"
                AuditActions.TASK_COMPLETED shouldBe "task.completed"
                AuditActions.TASK_FAILED shouldBe "task.failed"
                AuditActions.TASK_CANCELLED shouldBe "task.cancelled"
            }
        }
        
        describe("monitor actions") {
            it("should have all monitor action constants") {
                AuditActions.CHECK_CREATED shouldBe "monitor.check_created"
                AuditActions.CHECK_UPDATED shouldBe "monitor.check_updated"
                AuditActions.CHECK_DELETED shouldBe "monitor.check_deleted"
                AuditActions.ALERT_TRIGGERED shouldBe "monitor.alert_triggered"
                AuditActions.ALERT_RESOLVED shouldBe "monitor.alert_resolved"
            }
        }
        
        describe("system actions") {
            it("should have all system action constants") {
                AuditActions.SYSTEM_STARTED shouldBe "system.started"
                AuditActions.SYSTEM_STOPPED shouldBe "system.stopped"
                AuditActions.CONFIG_CHANGED shouldBe "system.config_changed"
                AuditActions.BACKUP_CREATED shouldBe "system.backup_created"
                AuditActions.BACKUP_RESTORED shouldBe "system.backup_restored"
            }
        }
        
        describe("action naming consistency") {
            it("should follow consistent naming patterns") {
                // Auth actions should start with "auth."
                listOf(
                    AuditActions.LOGIN,
                    AuditActions.LOGOUT,
                    AuditActions.LOGIN_FAILED,
                    AuditActions.PASSWORD_CHANGED,
                    AuditActions.MFA_ENABLED,
                    AuditActions.MFA_DISABLED
                ).forEach { action ->
                    action.startsWith("auth.") shouldBe true
                }
                
                // User actions should start with "user."
                listOf(
                    AuditActions.USER_CREATED,
                    AuditActions.USER_UPDATED,
                    AuditActions.USER_DELETED,
                    AuditActions.USER_INVITED,
                    AuditActions.USER_ACTIVATED,
                    AuditActions.USER_DEACTIVATED
                ).forEach { action ->
                    action.startsWith("user.") shouldBe true
                }
                
                // System actions should start with "system."
                listOf(
                    AuditActions.SYSTEM_STARTED,
                    AuditActions.SYSTEM_STOPPED,
                    AuditActions.CONFIG_CHANGED,
                    AuditActions.BACKUP_CREATED,
                    AuditActions.BACKUP_RESTORED
                ).forEach { action ->
                    action.startsWith("system.") shouldBe true
                }
            }
        }
    }
    
    describe("AuditResources object") {
        
        it("should have all expected resource constants") {
            AuditResources.USER shouldBe "user"
            AuditResources.ORGANIZATION shouldBe "organization"
            AuditResources.SECRET shouldBe "secret"
            AuditResources.WORKFLOW shouldBe "workflow"
            AuditResources.TASK shouldBe "task"
            AuditResources.MONITOR_CHECK shouldBe "monitor_check"
            AuditResources.SYSTEM shouldBe "system"
            AuditResources.SESSION shouldBe "session"
            AuditResources.API_KEY shouldBe "api_key"
            AuditResources.INTEGRATION shouldBe "integration"
        }
        
        it("should use consistent naming convention") {
            val resources = listOf(
                AuditResources.USER,
                AuditResources.ORGANIZATION,
                AuditResources.SECRET,
                AuditResources.WORKFLOW,
                AuditResources.TASK,
                AuditResources.SYSTEM,
                AuditResources.SESSION,
                AuditResources.INTEGRATION
            )
            
            // Single word resources should be lowercase
            resources.forEach { resource ->
                resource shouldBe resource.lowercase()
                resource.contains(" ") shouldBe false
            }
        }
        
        it("should handle compound resource names") {
            // Compound names should use underscore
            AuditResources.MONITOR_CHECK shouldBe "monitor_check"
            AuditResources.API_KEY shouldBe "api_key"
            
            AuditResources.MONITOR_CHECK.contains("_") shouldBe true
            AuditResources.API_KEY.contains("_") shouldBe true
        }
    }
    
    describe("audit log integration") {
        
        it("should create audit log with predefined actions and resources") {
            val now = MockTimeProvider.fixedInstant()
            val auditLog = AuditLog(
                id = "audit-123",
                organizationId = "org-123",
                userId = "user-123",
                action = AuditActions.SECRET_ACCESSED,
                resource = AuditResources.SECRET,
                resourceId = "secret-123",
                details = mapOf("access_type" to "read"),
                ipAddress = "10.0.0.1",
                userAgent = "Eden CLI v1.0",
                timestamp = now,
                severity = AuditSeverity.INFO
            )
            
            auditLog.action shouldBe "vault.secret_accessed"
            auditLog.resource shouldBe "secret"
            auditLog.severity shouldBe AuditSeverity.INFO
        }
        
        it("should create system audit log without user") {
            val now = MockTimeProvider.fixedInstant()
            val auditLog = AuditLog(
                id = "audit-system",
                organizationId = "system",
                userId = null,
                action = AuditActions.SYSTEM_STARTED,
                resource = AuditResources.SYSTEM,
                resourceId = null,
                details = mapOf("version" to "1.0.0"),
                ipAddress = null,
                userAgent = null,
                timestamp = now,
                severity = AuditSeverity.INFO
            )
            
            auditLog.userId.shouldBeNull()
            auditLog.resourceId.shouldBeNull()
            auditLog.ipAddress.shouldBeNull()
            auditLog.userAgent.shouldBeNull()
            auditLog.action shouldBe "system.started"
            auditLog.resource shouldBe "system"
        }
        
        it("should create critical severity audit log") {
            val now = MockTimeProvider.fixedInstant()
            val auditLog = AuditLog(
                id = "audit-critical",
                organizationId = "org-123",
                userId = "user-123",
                action = AuditActions.SECRET_DELETED,
                resource = AuditResources.SECRET,
                resourceId = "secret-123",
                details = mapOf("reason" to "security_breach"),
                ipAddress = "127.0.0.1",
                userAgent = "Test Agent",
                timestamp = now,
                severity = AuditSeverity.CRITICAL
            )
            
            auditLog.severity shouldBe AuditSeverity.CRITICAL
            auditLog.details["reason"] shouldBe "security_breach"
        }
    }
})