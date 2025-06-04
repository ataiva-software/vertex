package com.ataiva.eden.database

import com.ataiva.eden.database.repositories.*

/**
 * Comprehensive database service for Eden DevOps Suite
 * Provides centralized access to all repository operations
 */
interface EdenDatabaseService {
    
    // Repository access
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
    
    // Database management
    suspend fun initialize(): Boolean
    suspend fun migrate(): List<String>
    suspend fun validateSchema(): Boolean
    suspend fun getHealthStatus(): DatabaseHealthStatus
    suspend fun close()
    
    // Transaction support
    suspend fun <T> transaction(block: suspend (EdenDatabaseService) -> T): T
    
    // Bulk operations
    suspend fun bulkInsert(entities: List<Any>): BulkOperationResult
    suspend fun bulkUpdate(entities: List<Any>): BulkOperationResult
    suspend fun bulkDelete(entityType: String, ids: List<String>): BulkOperationResult
    
    // Search operations
    suspend fun globalSearch(query: String, userId: String): GlobalSearchResult
    suspend fun advancedSearch(criteria: SearchCriteria): SearchResult
    
    // Analytics and reporting
    suspend fun getDashboardStats(userId: String): DashboardStats
    suspend fun getSystemOverview(): SystemOverview
    suspend fun generateReport(reportType: ReportType, parameters: Map<String, Any>): Report
}

/**
 * Database health status
 */
data class DatabaseHealthStatus(
    val isHealthy: Boolean,
    val connectionPoolStats: PoolStats,
    val migrationStatus: List<MigrationStatus>,
    val lastHealthCheck: String,
    val issues: List<String> = emptyList()
)

/**
 * Bulk operation result
 */
data class BulkOperationResult(
    val successful: Int,
    val failed: Int,
    val errors: List<String> = emptyList(),
    val duration: Long
)

/**
 * Global search result
 */
data class GlobalSearchResult(
    val secrets: List<Secret>,
    val workflows: List<Workflow>,
    val tasks: List<Task>,
    val totalResults: Int,
    val searchDuration: Long
)

/**
 * Search criteria for advanced search
 */
data class SearchCriteria(
    val query: String,
    val entityTypes: List<String> = emptyList(),
    val userId: String? = null,
    val dateRange: DateRange? = null,
    val filters: Map<String, Any> = emptyMap(),
    val sortBy: String? = null,
    val sortOrder: SortOrder = SortOrder.DESC,
    val limit: Int = 50,
    val offset: Int = 0
)

/**
 * Search result
 */
data class SearchResult(
    val results: List<SearchResultItem>,
    val totalCount: Long,
    val searchDuration: Long,
    val facets: Map<String, List<FacetValue>> = emptyMap()
)

/**
 * Search result item
 */
data class SearchResultItem(
    val id: String,
    val type: String,
    val title: String,
    val description: String?,
    val relevanceScore: Double,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Facet value for search filtering
 */
data class FacetValue(
    val value: String,
    val count: Long
)

/**
 * Date range for filtering
 */
data class DateRange(
    val startDate: String,
    val endDate: String
)

/**
 * Sort order enum
 */
enum class SortOrder {
    ASC, DESC
}

/**
 * Dashboard statistics
 */
data class DashboardStats(
    val userStats: UserStats,
    val secretStats: SecretStats,
    val workflowStats: WorkflowStats,
    val taskStats: TaskStats,
    val recentActivity: List<ActivityItem>,
    val systemHealth: SystemHealthSummary
)

/**
 * Activity item for dashboard
 */
data class ActivityItem(
    val id: String,
    val type: String,
    val description: String,
    val timestamp: String,
    val userId: String?,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * System health summary
 */
data class SystemHealthSummary(
    val overallStatus: String,
    val activeServices: Int,
    val totalServices: Int,
    val criticalIssues: Int,
    val lastUpdated: String
)

/**
 * System overview
 */
data class SystemOverview(
    val totalUsers: Long,
    val totalSecrets: Long,
    val totalWorkflows: Long,
    val totalTasks: Long,
    val activeExecutions: Long,
    val systemEvents: SystemEventStats,
    val auditLogs: AuditStats,
    val performance: PerformanceMetrics
)

/**
 * Performance metrics
 */
data class PerformanceMetrics(
    val averageResponseTime: Double,
    val throughputPerSecond: Double,
    val errorRate: Double,
    val databaseConnections: Int,
    val memoryUsage: Long,
    val cpuUsage: Double
)

/**
 * Report types
 */
enum class ReportType {
    USER_ACTIVITY,
    SECRET_ACCESS,
    WORKFLOW_EXECUTION,
    TASK_PERFORMANCE,
    SYSTEM_AUDIT,
    SECURITY_SUMMARY,
    PERFORMANCE_ANALYSIS
}

/**
 * Report data
 */
data class Report(
    val type: ReportType,
    val title: String,
    val generatedAt: String,
    val parameters: Map<String, Any>,
    val data: Map<String, Any>,
    val summary: String,
    val charts: List<ChartData> = emptyList()
)

/**
 * Chart data for reports
 */
data class ChartData(
    val type: String,
    val title: String,
    val data: List<DataPoint>,
    val labels: List<String> = emptyList()
)

/**
 * Data point for charts
 */
data class DataPoint(
    val label: String,
    val value: Double,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Database service factory
 */
interface EdenDatabaseServiceFactory {
    suspend fun create(config: DatabaseConfig): EdenDatabaseService
    suspend fun createWithMigration(config: DatabaseConfig): EdenDatabaseService
    suspend fun createForTesting(config: DatabaseConfig): EdenDatabaseService
}