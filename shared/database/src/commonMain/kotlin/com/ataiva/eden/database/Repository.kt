package com.ataiva.eden.database

/**
 * Base repository interface
 */
interface Repository<T, ID> {
    suspend fun findById(id: ID): T?
    suspend fun findAll(): List<T>
    suspend fun save(entity: T): T
    suspend fun update(entity: T): Boolean
    suspend fun delete(id: ID): Boolean
    suspend fun count(): Long
}

/**
 * User repository interface
 */
interface UserRepository : Repository<User, String> {
    suspend fun findByEmail(email: String): User?
    suspend fun findByUsername(username: String): User?
    suspend fun updatePassword(userId: String, passwordHash: String): Boolean
    suspend fun getUserPermissions(userId: String): Set<Permission>
    suspend fun getUserOrganizationMemberships(userId: String): List<OrganizationMembership>
    suspend fun findByRole(role: String): List<User>
    suspend fun findActiveUsers(): List<User>
    suspend fun findNewUsersSince(since: String): List<User>
}

/**
 * Secret repository interface
 */
interface SecretRepository : Repository<Secret, String> {
    suspend fun findByName(name: String): Secret?
    suspend fun findByType(type: String): List<Secret>
    suspend fun findByUserId(userId: String): List<Secret>
    suspend fun findByOrganizationId(organizationId: String): List<Secret>
    suspend fun findAccessibleByUser(userId: String): List<Secret>
    suspend fun findUpdatedSince(since: String): List<Secret>
}

/**
 * Secret access log repository interface
 */
interface SecretAccessLogRepository : Repository<SecretAccessLog, String> {
    suspend fun findBySecretId(secretId: String): List<SecretAccessLog>
    suspend fun findByUserId(userId: String): List<SecretAccessLog>
    suspend fun findByTimeRange(start: String, end: String): List<SecretAccessLog>
    suspend fun findBySecretIdAndTimeRange(secretId: String, start: String, end: String): List<SecretAccessLog>
    suspend fun findByUserIdAndTimeRange(userId: String, start: String, end: String): List<SecretAccessLog>
}

/**
 * Workflow repository interface
 */
interface WorkflowRepository : Repository<Workflow, String> {
    suspend fun findByName(name: String): Workflow?
    suspend fun findByStatus(status: String): List<Workflow>
    suspend fun findByUserId(userId: String): List<Workflow>
    suspend fun findByOrganizationId(organizationId: String): List<Workflow>
    suspend fun findUpdatedSince(since: String): List<Workflow>
    suspend fun updateStatus(workflowId: String, status: String): Boolean
}

/**
 * Workflow execution repository interface
 */
interface WorkflowExecutionRepository : Repository<WorkflowExecution, String> {
    suspend fun findByWorkflowId(workflowId: String): List<WorkflowExecution>
    suspend fun findByStatus(status: String): List<WorkflowExecution>
    suspend fun findByTimeRange(start: String, end: String): List<WorkflowExecution>
    suspend fun findByWorkflowIdAndTimeRange(workflowId: String, start: String, end: String): List<WorkflowExecution>
    suspend fun findLatestByWorkflowId(workflowId: String): WorkflowExecution?
    suspend fun updateStatus(executionId: String, status: String): Boolean
}

/**
 * Workflow step repository interface
 */
interface WorkflowStepRepository : Repository<WorkflowStep, String> {
    suspend fun findByWorkflowId(workflowId: String): List<WorkflowStep>
    suspend fun findByExecutionId(executionId: String): List<WorkflowStep>
    suspend fun findByStatus(status: String): List<WorkflowStep>
    suspend fun updateStatus(stepId: String, status: String): Boolean
    suspend fun findByWorkflowIdAndOrder(workflowId: String, order: Int): WorkflowStep?
}

/**
 * Task repository interface
 */
interface TaskRepository : Repository<Task, String> {
    suspend fun findByName(name: String): Task?
    suspend fun findByStatus(status: String): List<Task>
    suspend fun findByType(type: String): List<Task>
    suspend fun findByUserId(userId: String): List<Task>
    suspend fun findByPriority(priority: Int): List<Task>
    suspend fun findScheduledBefore(time: String): List<Task>
    suspend fun updateStatus(taskId: String, status: String): Boolean
}

/**
 * Task execution repository interface
 */
interface TaskExecutionRepository : Repository<TaskExecution, String> {
    suspend fun findByTaskId(taskId: String): List<TaskExecution>
    suspend fun findByStatus(status: String): List<TaskExecution>
    suspend fun findByTimeRange(start: String, end: String): List<TaskExecution>
    suspend fun findByTaskIdAndTimeRange(taskId: String, start: String, end: String): List<TaskExecution>
    suspend fun findLatestByTaskId(taskId: String): TaskExecution?
    suspend fun updateStatus(executionId: String, status: String): Boolean
}

/**
 * System event repository interface
 */
interface SystemEventRepository : Repository<SystemEvent, String> {
    suspend fun findByType(type: String): List<SystemEvent>
    suspend fun findBySource(source: String): List<SystemEvent>
    suspend fun findByTimeRange(start: String, end: String): List<SystemEvent>
    suspend fun findByTypeAndTimeRange(type: String, start: String, end: String): List<SystemEvent>
    suspend fun findBySourceAndTimeRange(source: String, start: String, end: String): List<SystemEvent>
}

/**
 * Audit log repository interface
 */
interface AuditLogRepository : Repository<AuditLog, String> {
    suspend fun findByUserId(userId: String): List<AuditLog>
    suspend fun findByAction(action: String): List<AuditLog>
    suspend fun findByResource(resource: String): List<AuditLog>
    suspend fun findByTimeRange(start: String, end: String): List<AuditLog>
    suspend fun findByUserIdAndTimeRange(userId: String, start: String, end: String): List<AuditLog>
    suspend fun findByActionAndTimeRange(action: String, start: String, end: String): List<AuditLog>
    suspend fun findSecurityRelatedLogs(start: String, end: String): List<AuditLog>
}

/**
 * User entity
 */
data class User(
    val id: String,
    val email: String,
    val username: String,
    val passwordHash: String?,
    val isActive: Boolean,
    val emailVerified: Boolean,
    val mfaSecret: String?,
    val profile: UserProfile,
    val createdAt: String,
    val updatedAt: String
)

/**
 * User profile
 */
data class UserProfile(
    val firstName: String,
    val lastName: String,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String?,
    val preferences: Map<String, String>
)

/**
 * Permission entity
 */
data class Permission(
    val id: String,
    val name: String,
    val description: String?,
    val resource: String,
    val action: String
)

/**
 * Organization membership
 */
data class OrganizationMembership(
    val userId: String,
    val organizationId: String,
    val role: String,
    val joinedAt: String
)

/**
 * Secret access log
 */
data class SecretAccessLog(
    val id: String,
    val secretId: String,
    val userId: String,
    val accessType: String,
    val timestamp: String,
    val ipAddress: String?,
    val userAgent: String?
)

/**
 * Workflow execution
 */
data class WorkflowExecution(
    val id: String,
    val workflowId: String,
    val status: String,
    val startedAt: String,
    val completedAt: String?,
    val executedBy: String,
    val parameters: Map<String, Any>,
    val result: Map<String, Any>?,
    val error: String?
)

/**
 * Task execution
 */
data class TaskExecution(
    val id: String,
    val taskId: String,
    val status: String,
    val startedAt: String,
    val completedAt: String?,
    val executedBy: String?,
    val parameters: Map<String, Any>,
    val result: Map<String, Any>?,
    val error: String?
)

/**
 * System event
 */
data class SystemEvent(
    val id: String,
    val type: String,
    val source: String,
    val timestamp: String,
    val message: String,
    val details: Map<String, Any>?,
    val severity: String
)

/**
 * Audit log
 */
data class AuditLog(
    val id: String,
    val userId: String?,
    val action: String,
    val resource: String,
    val resourceId: String?,
    val timestamp: String,
    val ipAddress: String?,
    val userAgent: String?,
    val details: Map<String, Any>?
)