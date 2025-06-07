package com.ataiva.eden.database

import java.sql.SQLException

/**
 * Interface for Eden database services
 *
 * This interface defines the contract for database services in the Eden platform.
 * It includes methods for database initialization, migration, validation, and operations.
 *
 * @author Eden Database Team
 * @version 1.0.0
 */
interface EdenDatabaseService {
    // Repositories
    val userRepository: UserRepository
    val secretRepository: SecretRepository
    val secretAccessLogRepository: SecretAccessLogRepository
    val workflowRepository: WorkflowRepository
    val workflowExecutionRepository: WorkflowExecutionRepository
    val workflowStepRepository: WorkflowStepRepository
    val taskRepository: TaskRepository
    val taskExecutionRepository: TaskExecutionRepository
    val systemEventRepository: SystemEventRepository
    val auditLogRepository: AuditLogRepository
    
    /**
     * Initialize the database service
     *
     * @return true if initialization was successful, false otherwise
     */
    suspend fun initialize(): Boolean
    
    /**
     * Run database migrations
     *
     * @return list of migration results
     */
    suspend fun migrate(): List<String>
    
    /**
     * Validate the database schema
     *
     * @return true if the schema is valid, false otherwise
     */
    suspend fun validateSchema(): Boolean
    
    /**
     * Get detailed schema validation results
     *
     * @return map containing validation details
     */
    fun getSchemaValidationDetails(): Map<String, Any>
    
    /**
     * Get the health status of the database
     *
     * @return DatabaseHealthStatus object
     */
    suspend fun getHealthStatus(): DatabaseHealthStatus
    
    /**
     * Close the database service
     */
    suspend fun close()
    
    /**
     * Execute a transaction
     *
     * @param block the transaction block to execute
     * @return the result of the transaction
     */
    suspend fun <T> transaction(block: suspend (EdenDatabaseService) -> T): T
    
    /**
     * Bulk insert entities
     *
     * @param entities the entities to insert
     * @return BulkOperationResult containing operation results
     */
    suspend fun bulkInsert(entities: List<Any>): BulkOperationResult
    
    /**
     * Bulk update entities
     *
     * @param entities the entities to update
     * @return BulkOperationResult containing operation results
     */
    suspend fun bulkUpdate(entities: List<Any>): BulkOperationResult
    
    /**
     * Bulk delete entities
     *
     * @param entityType the type of entities to delete
     * @param ids the IDs of the entities to delete
     * @return BulkOperationResult containing operation results
     */
    suspend fun bulkDelete(entityType: String, ids: List<String>): BulkOperationResult
    
    /**
     * Perform a global search
     *
     * @param query the search query
     * @param userId the ID of the user performing the search
     * @return GlobalSearchResult containing search results
     */
    suspend fun globalSearch(query: String, userId: String): GlobalSearchResult
    
    /**
     * Perform an advanced search
     *
     * @param criteria the search criteria
     * @return SearchResult containing search results
     */
    suspend fun advancedSearch(criteria: SearchCriteria): SearchResult
    
    /**
     * Get dashboard statistics
     *
     * @param userId the ID of the user
     * @return DashboardStats containing dashboard statistics
     */
    suspend fun getDashboardStats(userId: String): DashboardStats
    
    /**
     * Get system overview
     *
     * @return SystemOverview containing system overview
     */
    suspend fun getSystemOverview(): SystemOverview
    
    /**
     * Generate a report
     *
     * @param reportType the type of report to generate
     * @param parameters the report parameters
     * @return Report containing the generated report
     */
    suspend fun generateReport(reportType: ReportType, parameters: Map<String, Any>): Report
}

/**
 * Data class representing database health status
 */
data class DatabaseHealthStatus(
    val isHealthy: Boolean,
    val connectionPoolStats: PoolStats,
    val migrationStatus: List<MigrationStatus>,
    val lastHealthCheck: String,
    val issues: List<String>
)

/**
 * Data class representing connection pool statistics
 */
data class PoolStats(
    val active: Int,
    val idle: Int,
    val waiting: Int,
    val total: Int,
    val maxPoolSize: Int
)

/**
 * Data class representing migration status
 */
data class MigrationStatus(
    val version: String,
    val description: String,
    val type: String,
    val script: String,
    val checksum: Int,
    val installedBy: String,
    val installedOn: String,
    val executionTime: Long,
    val success: Boolean
)

/**
 * Data class representing bulk operation result
 */
data class BulkOperationResult(
    val successful: Int,
    val failed: Int,
    val errors: List<String>,
    val duration: Long
)

/**
 * Data class representing global search result
 */
data class GlobalSearchResult(
    val secrets: List<Any>,
    val workflows: List<Any>,
    val tasks: List<Any>,
    val totalResults: Int,
    val searchDuration: Long
)

/**
 * Data class representing search criteria
 */
data class SearchCriteria(
    val entityType: String,
    val filters: Map<String, Any>,
    val sortBy: String? = null,
    val sortDirection: String? = null,
    val page: Int = 1,
    val pageSize: Int = 20
)

/**
 * Data class representing search result
 */
data class SearchResult(
    val results: List<Any>,
    val totalCount: Int,
    val searchDuration: Long,
    val facets: Map<String, List<String>>
)

/**
 * Data class representing dashboard statistics
 */
data class DashboardStats(
    val activeUsers: Int,
    val activeWorkflows: Int,
    val activeTasks: Int,
    val recentEvents: List<Any>,
    val systemHealth: Map<String, Any>
)

/**
 * Data class representing system overview
 */
data class SystemOverview(
    val services: Map<String, ServiceStatus>,
    val resources: Map<String, ResourceUsage>,
    val alerts: List<Alert>
)

/**
 * Data class representing service status
 */
data class ServiceStatus(
    val name: String,
    val status: String,
    val uptime: Long,
    val version: String
)

/**
 * Data class representing resource usage
 */
data class ResourceUsage(
    val name: String,
    val usage: Double,
    val limit: Double,
    val unit: String
)

/**
 * Data class representing an alert
 */
data class Alert(
    val level: String,
    val message: String,
    val timestamp: String,
    val source: String
)

/**
 * Enum representing report type
 */
enum class ReportType {
    USAGE,
    AUDIT,
    PERFORMANCE,
    SECURITY
}

/**
 * Data class representing a report
 */
data class Report(
    val type: ReportType,
    val title: String,
    val generatedAt: String,
    val data: Map<String, Any>
)

/**
 * Interface for user repository
 */
interface UserRepository {
    suspend fun findById(id: String): User?
    suspend fun findAll(): List<User>
    suspend fun save(entity: User): User
    suspend fun update(entity: User): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun count(): Long
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
 * Interface for secret repository
 */
interface SecretRepository {
    suspend fun findById(id: String): Secret?
    suspend fun findAll(): List<Secret>
    suspend fun save(entity: Secret): Secret
    suspend fun update(entity: Secret): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun count(): Long
    suspend fun findByName(name: String): Secret?
    suspend fun findByType(type: String): List<Secret>
    suspend fun findByUserId(userId: String): List<Secret>
    suspend fun findByOrganizationId(organizationId: String): List<Secret>
    suspend fun findAccessibleByUser(userId: String): List<Secret>
    suspend fun findUpdatedSince(since: String): List<Secret>
}

/**
 * Interface for secret access log repository
 */
interface SecretAccessLogRepository {
    suspend fun findById(id: String): SecretAccessLog?
    suspend fun findAll(): List<SecretAccessLog>
    suspend fun save(entity: SecretAccessLog): SecretAccessLog
    suspend fun update(entity: SecretAccessLog): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun count(): Long
    suspend fun findBySecretId(secretId: String): List<SecretAccessLog>
    suspend fun findByUserId(userId: String): List<SecretAccessLog>
    suspend fun findByTimeRange(start: String, end: String): List<SecretAccessLog>
    suspend fun findBySecretIdAndTimeRange(secretId: String, start: String, end: String): List<SecretAccessLog>
    suspend fun findByUserIdAndTimeRange(userId: String, start: String, end: String): List<SecretAccessLog>
}

/**
 * Interface for workflow repository
 */
interface WorkflowRepository {
    suspend fun findById(id: String): Workflow?
    suspend fun findAll(): List<Workflow>
    suspend fun save(entity: Workflow): Workflow
    suspend fun update(entity: Workflow): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun count(): Long
    suspend fun findByName(name: String): Workflow?
    suspend fun findByStatus(status: String): List<Workflow>
    suspend fun findByUserId(userId: String): List<Workflow>
    suspend fun findByOrganizationId(organizationId: String): List<Workflow>
    suspend fun findUpdatedSince(since: String): List<Workflow>
    suspend fun updateStatus(workflowId: String, status: String): Boolean
}

/**
 * Interface for workflow execution repository
 */
interface WorkflowExecutionRepository {
    suspend fun findById(id: String): WorkflowExecution?
    suspend fun findAll(): List<WorkflowExecution>
    suspend fun save(entity: WorkflowExecution): WorkflowExecution
    suspend fun update(entity: WorkflowExecution): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun count(): Long
    suspend fun findByWorkflowId(workflowId: String): List<WorkflowExecution>
    suspend fun findByStatus(status: String): List<WorkflowExecution>
    suspend fun findByTimeRange(start: String, end: String): List<WorkflowExecution>
    suspend fun findByWorkflowIdAndTimeRange(workflowId: String, start: String, end: String): List<WorkflowExecution>
    suspend fun findLatestByWorkflowId(workflowId: String): WorkflowExecution?
    suspend fun updateStatus(executionId: String, status: String): Boolean
}

/**
 * Interface for workflow step repository
 */
interface WorkflowStepRepository {
    suspend fun findById(id: String): WorkflowStep?
    suspend fun findAll(): List<WorkflowStep>
    suspend fun save(entity: WorkflowStep): WorkflowStep
    suspend fun update(entity: WorkflowStep): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun count(): Long
    suspend fun findByWorkflowId(workflowId: String): List<WorkflowStep>
    suspend fun findByExecutionId(executionId: String): List<WorkflowStep>
    suspend fun findByStatus(status: String): List<WorkflowStep>
    suspend fun updateStatus(stepId: String, status: String): Boolean
    suspend fun findByWorkflowIdAndOrder(workflowId: String, order: Int): WorkflowStep?
}

/**
 * Interface for task repository
 */
interface TaskRepository {
    suspend fun findById(id: String): Task?
    suspend fun findAll(): List<Task>
    suspend fun save(entity: Task): Task
    suspend fun update(entity: Task): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun count(): Long
    suspend fun findByName(name: String): Task?
    suspend fun findByStatus(status: String): List<Task>
    suspend fun findByType(type: String): List<Task>
    suspend fun findByUserId(userId: String): List<Task>
    suspend fun findByPriority(priority: Int): List<Task>
    suspend fun findScheduledBefore(time: String): List<Task>
    suspend fun updateStatus(taskId: String, status: String): Boolean
}

/**
 * Interface for task execution repository
 */
interface TaskExecutionRepository {
    suspend fun findById(id: String): TaskExecution?
    suspend fun findAll(): List<TaskExecution>
    suspend fun save(entity: TaskExecution): TaskExecution
    suspend fun update(entity: TaskExecution): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun count(): Long
    suspend fun findByTaskId(taskId: String): List<TaskExecution>
    suspend fun findByStatus(status: String): List<TaskExecution>
    suspend fun findByTimeRange(start: String, end: String): List<TaskExecution>
    suspend fun findByTaskIdAndTimeRange(taskId: String, start: String, end: String): List<TaskExecution>
    suspend fun findLatestByTaskId(taskId: String): TaskExecution?
    suspend fun updateStatus(executionId: String, status: String): Boolean
}

/**
 * Interface for system event repository
 */
interface SystemEventRepository {
    suspend fun findById(id: String): SystemEvent?
    suspend fun findAll(): List<SystemEvent>
    suspend fun save(entity: SystemEvent): SystemEvent
    suspend fun update(entity: SystemEvent): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun count(): Long
    suspend fun findByType(type: String): List<SystemEvent>
    suspend fun findBySource(source: String): List<SystemEvent>
    suspend fun findByTimeRange(start: String, end: String): List<SystemEvent>
    suspend fun findByTypeAndTimeRange(type: String, start: String, end: String): List<SystemEvent>
    suspend fun findBySourceAndTimeRange(source: String, start: String, end: String): List<SystemEvent>
}

/**
 * Interface for audit log repository
 */
interface AuditLogRepository {
    suspend fun findById(id: String): AuditLog?
    suspend fun findAll(): List<AuditLog>
    suspend fun save(entity: AuditLog): AuditLog
    suspend fun update(entity: AuditLog): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun count(): Long
    suspend fun findByUserId(userId: String): List<AuditLog>
    suspend fun findByAction(action: String): List<AuditLog>
    suspend fun findByResource(resource: String): List<AuditLog>
    suspend fun findByTimeRange(start: String, end: String): List<AuditLog>
    suspend fun findByUserIdAndTimeRange(userId: String, start: String, end: String): List<AuditLog>
    suspend fun findByActionAndTimeRange(action: String, start: String, end: String): List<AuditLog>
    suspend fun findSecurityRelatedLogs(start: String, end: String): List<AuditLog>
}

// Placeholder data classes for entities
data class User(val id: String, val email: String)
data class Permission(val id: String, val name: String)
data class OrganizationMembership(val userId: String, val organizationId: String, val role: String)
data class Secret(val id: String, val name: String)
data class SecretAccessLog(val id: String, val secretId: String, val userId: String)
data class Workflow(val id: String, val name: String)
data class WorkflowExecution(val id: String, val workflowId: String)
data class WorkflowStep(val id: String, val executionId: String)
data class Task(val id: String, val name: String)
data class TaskExecution(val id: String, val taskId: String)
data class SystemEvent(val id: String, val type: String)
data class AuditLog(val id: String, val userId: String)