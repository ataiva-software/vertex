package com.ataiva.eden.insight.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

// ============================================================================
// Core Domain Models
// ============================================================================

@Serializable
data class AnalyticsQuery(
    val id: String,
    val name: String,
    val description: String? = null,
    val queryText: String,
    val queryType: QueryType,
    val parameters: Map<String, String> = emptyMap(),
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val tags: List<String> = emptyList()
)

@Serializable
data class QueryExecution(
    val id: String,
    val queryId: String,
    val executedBy: String,
    val startTime: Long,
    val endTime: Long? = null,
    val status: ExecutionStatus,
    val resultCount: Int = 0,
    val executionTimeMs: Long = 0,
    val errorMessage: String? = null,
    val parameters: Map<String, String> = emptyMap()
)

@Serializable
data class AnalyticsResult(
    val queryId: String,
    val executionId: String,
    val data: List<Map<String, @Contextual Any>>,
    val metadata: ResultMetadata,
    val generatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ResultMetadata(
    val totalRows: Int,
    val columns: List<ColumnInfo>,
    val executionTimeMs: Long,
    val dataSource: String,
    val queryHash: String
)

@Serializable
data class ColumnInfo(
    val name: String,
    val type: DataType,
    val nullable: Boolean = true,
    val description: String? = null
)

// ============================================================================
// Report Models
// ============================================================================

@Serializable
data class Report(
    val id: String,
    val name: String,
    val description: String? = null,
    val templateId: String,
    val parameters: Map<String, String> = emptyMap(),
    val schedule: ReportSchedule? = null,
    val recipients: List<String> = emptyList(),
    val format: ReportFormat = ReportFormat.PDF,
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastGenerated: Long? = null,
    val isActive: Boolean = true
)

@Serializable
data class ReportTemplate(
    val id: String,
    val name: String,
    val description: String? = null,
    val templateContent: String,
    val requiredParameters: List<ParameterDefinition> = emptyList(),
    val supportedFormats: List<ReportFormat> = listOf(ReportFormat.PDF),
    val category: String = "general",
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val version: String = "1.0"
)

@Serializable
data class ReportExecution(
    val id: String,
    val reportId: String,
    val executedBy: String,
    val startTime: Long,
    val endTime: Long? = null,
    val status: ExecutionStatus,
    val outputPath: String? = null,
    val fileSize: Long = 0,
    val errorMessage: String? = null,
    val parameters: Map<String, String> = emptyMap()
)

@Serializable
data class ReportSchedule(
    val cronExpression: String,
    val timezone: String = "UTC",
    val enabled: Boolean = true,
    val nextExecution: Long? = null,
    val lastExecution: Long? = null
)

@Serializable
data class ParameterDefinition(
    val name: String,
    val type: DataType,
    val required: Boolean = true,
    val defaultValue: String? = null,
    val description: String? = null,
    val validValues: List<String> = emptyList()
)

// ============================================================================
// Dashboard Models
// ============================================================================

@Serializable
data class Dashboard(
    val id: String,
    val name: String,
    val description: String? = null,
    val widgets: List<DashboardWidget>,
    val layout: DashboardLayout,
    val permissions: DashboardPermissions,
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val isPublic: Boolean = false,
    val tags: List<String> = emptyList()
)

@Serializable
data class DashboardWidget(
    val id: String,
    val type: WidgetType,
    val title: String,
    val queryId: String? = null,
    val configuration: WidgetConfiguration,
    val position: WidgetPosition,
    val refreshInterval: Int = 300, // seconds
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class WidgetConfiguration(
    val chartType: ChartType? = null,
    val aggregation: AggregationType? = null,
    val groupBy: List<String> = emptyList(),
    val filters: Map<String, String> = emptyMap(),
    val colorScheme: String = "default",
    val showLegend: Boolean = true,
    val customOptions: Map<String, @Contextual Any> = emptyMap()
)

@Serializable
data class WidgetPosition(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

@Serializable
data class DashboardLayout(
    val columns: Int = 12,
    val rowHeight: Int = 100,
    val margin: Int = 10,
    val responsive: Boolean = true
)

@Serializable
data class DashboardPermissions(
    val owner: String,
    val viewers: List<String> = emptyList(),
    val editors: List<String> = emptyList(),
    val isPublic: Boolean = false
)

// ============================================================================
// Metrics and KPI Models
// ============================================================================

@Serializable
data class Metric(
    val id: String,
    val name: String,
    val description: String? = null,
    val category: String,
    val unit: String? = null,
    val aggregationType: AggregationType,
    val queryId: String,
    val thresholds: List<MetricThreshold> = emptyList(),
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class MetricValue(
    val metricId: String,
    val value: Double,
    val timestamp: Long,
    val dimensions: Map<String, String> = emptyMap(),
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class MetricThreshold(
    val level: ThresholdLevel,
    val operator: ComparisonOperator,
    val value: Double,
    val message: String? = null
)

@Serializable
data class KPI(
    val id: String,
    val name: String,
    val description: String? = null,
    val targetValue: Double,
    val currentValue: Double,
    val unit: String? = null,
    val trend: TrendDirection,
    val category: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    val historicalData: List<KPIDataPoint> = emptyList()
)

@Serializable
data class KPIDataPoint(
    val timestamp: Long,
    val value: Double,
    val target: Double? = null
)

// ============================================================================
// API Request/Response Models
// ============================================================================

@Serializable
data class QueryRequest(
    val queryText: String,
    val parameters: Map<String, String> = emptyMap(),
    val limit: Int = 1000,
    val timeout: Int = 30000 // milliseconds
)

@Serializable
data class QueryResponse(
    val success: Boolean,
    val data: List<Map<String, @Contextual Any>> = emptyList(),
    val metadata: ResultMetadata? = null,
    val executionId: String? = null,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class ReportGenerationRequest(
    val reportId: String,
    val parameters: Map<String, String> = emptyMap(),
    val format: ReportFormat = ReportFormat.PDF,
    val async: Boolean = true
)

@Serializable
data class ReportGenerationResponse(
    val success: Boolean,
    val executionId: String? = null,
    val downloadUrl: String? = null,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class DashboardDataRequest(
    val dashboardId: String,
    val timeRange: TimeRange? = null,
    val filters: Map<String, String> = emptyMap()
)

@Serializable
data class DashboardDataResponse(
    val dashboardId: String,
    val widgets: List<WidgetData>,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class WidgetData(
    val widgetId: String,
    val data: List<Map<String, @Contextual Any>>,
    val metadata: ResultMetadata? = null,
    val error: String? = null
)

@Serializable
data class TimeRange(
    val start: Long,
    val end: Long,
    val granularity: TimeGranularity = TimeGranularity.HOUR
)

// ============================================================================
// Configuration Models
// ============================================================================

@Serializable
data class DataSource(
    val id: String,
    val name: String,
    val type: DataSourceType,
    val connectionString: String,
    val credentials: Map<String, String> = emptyMap(),
    val isActive: Boolean = true,
    val lastTested: Long? = null,
    val testResult: String? = null
)

@Serializable
data class InsightConfiguration(
    val maxQueryTimeout: Int = 300000, // 5 minutes
    val maxResultRows: Int = 100000,
    val cacheEnabled: Boolean = true,
    val cacheTtl: Int = 3600, // 1 hour
    val allowedDataSources: List<String> = emptyList(),
    val reportOutputPath: String = "/tmp/reports",
    val maxConcurrentQueries: Int = 10
)

// ============================================================================
// Enums
// ============================================================================

@Serializable
enum class QueryType {
    SELECT, AGGREGATE, TIME_SERIES, CUSTOM
}

@Serializable
enum class ExecutionStatus {
    PENDING, RUNNING, COMPLETED, FAILED, CANCELLED, TIMEOUT
}

@Serializable
enum class DataType {
    STRING, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, TIMESTAMP, JSON
}

@Serializable
enum class ReportFormat {
    PDF, EXCEL, CSV, JSON, HTML
}

@Serializable
enum class WidgetType {
    CHART, TABLE, METRIC, GAUGE, TEXT, MAP
}

@Serializable
enum class ChartType {
    LINE, BAR, PIE, AREA, SCATTER, HISTOGRAM, HEATMAP
}

@Serializable
enum class AggregationType {
    SUM, COUNT, AVG, MIN, MAX, DISTINCT_COUNT, PERCENTILE
}

@Serializable
enum class ThresholdLevel {
    INFO, WARNING, CRITICAL
}

@Serializable
enum class ComparisonOperator {
    GREATER_THAN, LESS_THAN, EQUALS, NOT_EQUALS, GREATER_EQUAL, LESS_EQUAL
}

@Serializable
enum class TrendDirection {
    UP, DOWN, STABLE, UNKNOWN
}

@Serializable
enum class TimeGranularity {
    MINUTE, HOUR, DAY, WEEK, MONTH, YEAR
}

@Serializable
enum class DataSourceType {
    POSTGRESQL, MYSQL, MONGODB, ELASTICSEARCH, REST_API, FILE
}

// ============================================================================
// Service Info Models (for compatibility)
// ============================================================================

@Serializable
data class ServiceInfo(
    val name: String,
    val version: String,
    val description: String,
    val status: String
)

@Serializable
data class HealthCheck(
    val status: String,
    val timestamp: Long,
    val uptime: Long,
    val service: String
)