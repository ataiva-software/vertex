package com.ataiva.eden.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AuditLog(
    val id: String,
    val organizationId: String,
    val userId: String?,
    val action: String,
    val resource: String,
    val resourceId: String?,
    val details: Map<String, String> = emptyMap(),
    val ipAddress: String?,
    val userAgent: String?,
    val timestamp: Instant,
    val severity: AuditSeverity = AuditSeverity.INFO
)

@Serializable
enum class AuditSeverity {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

@Serializable
data class AuditEvent(
    val action: String,
    val resource: String,
    val resourceId: String?,
    val details: Map<String, String> = emptyMap(),
    val severity: AuditSeverity = AuditSeverity.INFO
)

// Common audit actions
object AuditActions {
    // Authentication actions
    const val LOGIN = "auth.login"
    const val LOGOUT = "auth.logout"
    const val LOGIN_FAILED = "auth.login_failed"
    const val PASSWORD_CHANGED = "auth.password_changed"
    const val MFA_ENABLED = "auth.mfa_enabled"
    const val MFA_DISABLED = "auth.mfa_disabled"
    
    // User management actions
    const val USER_CREATED = "user.created"
    const val USER_UPDATED = "user.updated"
    const val USER_DELETED = "user.deleted"
    const val USER_INVITED = "user.invited"
    const val USER_ACTIVATED = "user.activated"
    const val USER_DEACTIVATED = "user.deactivated"
    
    // Organization actions
    const val ORG_CREATED = "org.created"
    const val ORG_UPDATED = "org.updated"
    const val ORG_DELETED = "org.deleted"
    const val ORG_MEMBER_ADDED = "org.member_added"
    const val ORG_MEMBER_REMOVED = "org.member_removed"
    const val ORG_ROLE_CHANGED = "org.role_changed"
    
    // Vault actions
    const val SECRET_CREATED = "vault.secret_created"
    const val SECRET_ACCESSED = "vault.secret_accessed"
    const val SECRET_UPDATED = "vault.secret_updated"
    const val SECRET_DELETED = "vault.secret_deleted"
    const val SECRET_SHARED = "vault.secret_shared"
    
    // Flow actions
    const val WORKFLOW_CREATED = "flow.workflow_created"
    const val WORKFLOW_UPDATED = "flow.workflow_updated"
    const val WORKFLOW_DELETED = "flow.workflow_deleted"
    const val WORKFLOW_EXECUTED = "flow.workflow_executed"
    const val WORKFLOW_FAILED = "flow.workflow_failed"
    
    // Task actions
    const val TASK_CREATED = "task.created"
    const val TASK_STARTED = "task.started"
    const val TASK_COMPLETED = "task.completed"
    const val TASK_FAILED = "task.failed"
    const val TASK_CANCELLED = "task.cancelled"
    
    // Monitor actions
    const val CHECK_CREATED = "monitor.check_created"
    const val CHECK_UPDATED = "monitor.check_updated"
    const val CHECK_DELETED = "monitor.check_deleted"
    const val ALERT_TRIGGERED = "monitor.alert_triggered"
    const val ALERT_RESOLVED = "monitor.alert_resolved"
    
    // System actions
    const val SYSTEM_STARTED = "system.started"
    const val SYSTEM_STOPPED = "system.stopped"
    const val CONFIG_CHANGED = "system.config_changed"
    const val BACKUP_CREATED = "system.backup_created"
    const val BACKUP_RESTORED = "system.backup_restored"
}

// Common audit resources
object AuditResources {
    const val USER = "user"
    const val ORGANIZATION = "organization"
    const val SECRET = "secret"
    const val WORKFLOW = "workflow"
    const val TASK = "task"
    const val MONITOR_CHECK = "monitor_check"
    const val SYSTEM = "system"
    const val SESSION = "session"
    const val API_KEY = "api_key"
    const val INTEGRATION = "integration"
}