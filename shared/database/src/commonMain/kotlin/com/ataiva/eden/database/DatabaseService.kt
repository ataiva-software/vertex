package com.ataiva.eden.database

/**
 * Database configuration
 */
data class DatabaseConfig(
    val url: String,
    val username: String,
    val password: String,
    val driverClassName: String,
    val maxPoolSize: Int = 10,
    val minIdle: Int = 5,
    val idleTimeout: Long = 600000, // 10 minutes
    val connectionTimeout: Long = 30000, // 30 seconds
    val validationTimeout: Long = 5000, // 5 seconds
    val maxLifetime: Long = 1800000, // 30 minutes
    val autoCommit: Boolean = false,
    val schema: String? = null,
    val properties: Map<String, String> = emptyMap()
)

/**
 * Connection pool statistics
 */
data class PoolStats(
    val active: Int,
    val idle: Int,
    val waiting: Int,
    val total: Int,
    val maxPoolSize: Int
)

/**
 * Migration status
 */
data class MigrationStatus(
    val version: String,
    val description: String,
    val type: String,
    val script: String,
    val checksum: Int,
    val installedBy: String,
    val installedOn: String,
    val executionTime: Int,
    val success: Boolean
)

/**
 * User statistics
 */
data class UserStats(
    val totalUsers: Long,
    val activeUsers: Long,
    val newUsersLast30Days: Long,
    val usersByRole: Map<String, Long>
)

/**
 * Secret statistics
 */
data class SecretStats(
    val totalSecrets: Long,
    val secretsByType: Map<String, Long>,
    val secretsAccessedLast24Hours: Long,
    val secretsCreatedLast30Days: Long
)

/**
 * Workflow statistics
 */
data class WorkflowStats(
    val totalWorkflows: Long,
    val activeWorkflows: Long,
    val completedWorkflowsLast30Days: Long,
    val failedWorkflowsLast30Days: Long,
    val averageExecutionTime: Long
)

/**
 * Task statistics
 */
data class TaskStats(
    val totalTasks: Long,
    val pendingTasks: Long,
    val completedTasksLast30Days: Long,
    val failedTasksLast30Days: Long,
    val averageExecutionTime: Long
)

/**
 * System event statistics
 */
data class SystemEventStats(
    val totalEvents: Long,
    val eventsByType: Map<String, Long>,
    val eventsLast24Hours: Long,
    val errorEventsLast24Hours: Long
)

/**
 * Audit statistics
 */
data class AuditStats(
    val totalAuditLogs: Long,
    val auditLogsByType: Map<String, Long>,
    val auditLogsLast24Hours: Long,
    val securityRelatedLogsLast24Hours: Long
)

/**
 * Secret entity
 */
data class Secret(
    val id: String,
    val name: String,
    val description: String?,
    val type: String,
    val value: String,
    val metadata: Map<String, String>,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: String,
    val updatedBy: String
)

/**
 * Workflow entity
 */
data class Workflow(
    val id: String,
    val name: String,
    val description: String?,
    val status: String,
    val steps: List<WorkflowStep>,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: String,
    val updatedBy: String
)

/**
 * Workflow step entity
 */
data class WorkflowStep(
    val id: String,
    val workflowId: String,
    val name: String,
    val type: String,
    val status: String,
    val order: Int,
    val config: Map<String, Any>,
    val dependsOn: List<String>
)

/**
 * Task entity
 */
data class Task(
    val id: String,
    val name: String,
    val description: String?,
    val type: String,
    val status: String,
    val priority: Int,
    val config: Map<String, Any>,
    val scheduledAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: String,
    val updatedBy: String
)