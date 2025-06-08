package com.ataiva.eden.monitor.model

import kotlinx.serialization.Serializable

/**
 * Data models for the Monitor Service
 * Comprehensive monitoring and alerting data structures
 */

@Serializable
data class SystemMetrics(
    val timestamp: Long,
    val cpuUsage: Double,
    val memoryUsage: Double,
    val diskUsage: Double,
    val networkIO: NetworkIO,
    val uptime: Long
)

@Serializable
data class NetworkIO(
    val bytesIn: Long,
    val bytesOut: Long
)

@Serializable
data class ServiceHealth(
    val serviceName: String,
    val status: String,
    val responseTime: Long,
    val uptime: Long,
    val timestamp: Long,
    val errorRate: Double,
    val requestCount: Long
)

@Serializable
data class MetricData(
    val timestamp: Long,
    val value: Double
)

@Serializable
data class AlertRule(
    val id: String,
    val name: String,
    val description: String,
    val metricName: String,
    val condition: String, // "greater_than", "less_than", "equals"
    val threshold: Double,
    val severity: String, // "low", "medium", "high", "critical"
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class CreateAlertRequest(
    val name: String,
    val description: String,
    val metricName: String,
    val condition: String,
    val threshold: Double,
    val severity: String,
    val enabled: Boolean = true
)

@Serializable
data class ActiveAlert(
    val id: String,
    val ruleId: String,
    val ruleName: String,
    val severity: String,
    val message: String,
    val triggeredAt: Long,
    val acknowledged: Boolean,
    val acknowledgedBy: String?,
    val acknowledgedAt: Long?
)

@Serializable
data class AcknowledgeAlertRequest(
    val acknowledgedBy: String
)

@Serializable
data class Dashboard(
    val id: String,
    val name: String,
    val description: String,
    val widgets: List<DashboardWidget>,
    val lastUpdated: Long
)

@Serializable
data class DashboardWidget(
    val id: String,
    val title: String,
    val type: String, // "chart", "table", "grid", "status"
    val config: Map<String, String>
)

@Serializable
data class DashboardSummary(
    val id: String,
    val name: String,
    val description: String,
    val widgetCount: Int,
    val lastUpdated: Long
)

@Serializable
data class LogEntry(
    val timestamp: Long,
    val level: String,
    val service: String,
    val message: String,
    val correlationId: String
)

@Serializable
data class LogSearchRequest(
    val query: String = "",
    val service: String? = null,
    val level: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val limit: Int = 100
)

@Serializable
data class MonitoringStats(
    val totalMetricsCollected: Long,
    val totalAlertsTriggered: Long,
    val activeAlertsCount: Int,
    val alertRulesCount: Int,
    val dashboardsCount: Int,
    val uptimeSeconds: Long,
    val lastMetricCollectionTime: Long
)

@Serializable
data class HistoricalMetricsRequest(
    val metricName: String,
    val hours: Int = 24
)

@Serializable
data class HistoricalMetricsResponse(
    val metricName: String,
    val dataPoints: List<MetricData>,
    val summary: MetricSummary
)

@Serializable
data class MetricSummary(
    val min: Double,
    val max: Double,
    val average: Double,
    val count: Int
)

// Response DTOs

@Serializable
data class SystemMetricsResponse(
    val metrics: SystemMetrics,
    val status: String = "success"
)

@Serializable
data class ServiceMetricsResponse(
    val services: List<ServiceHealth>,
    val healthyCount: Int,
    val totalCount: Int,
    val overallStatus: String,
    val timestamp: Long
)

@Serializable
data class AlertRulesResponse(
    val rules: List<AlertRule>,
    val totalCount: Int,
    val enabledCount: Int
)

@Serializable
data class ActiveAlertsResponse(
    val alerts: List<ActiveAlert>,
    val totalCount: Int,
    val acknowledgedCount: Int,
    val criticalCount: Int
)

@Serializable
data class DashboardsResponse(
    val dashboards: List<DashboardSummary>,
    val totalCount: Int
)

@Serializable
data class LogSearchResponse(
    val logs: List<LogEntry>,
    val totalCount: Int,
    val query: String,
    val executionTime: Long
)

@Serializable
data class MonitoringStatsResponse(
    val stats: MonitoringStats,
    val systemHealth: String,
    val recommendations: List<String>
)

// Error response models

@Serializable
data class MonitorErrorResponse(
    val error: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ValidationError(
    val field: String,
    val message: String
)

@Serializable
data class ValidationErrorResponse(
    val error: String = "Validation failed",
    val errors: List<ValidationError>,
    val timestamp: Long = System.currentTimeMillis()
)

// Success response models

@Serializable
data class CreateAlertResponse(
    val alert: AlertRule,
    val message: String = "Alert rule created successfully"
)

@Serializable
data class AcknowledgeAlertResponse(
    val alertId: String,
    val acknowledged: Boolean,
    val acknowledgedBy: String,
    val acknowledgedAt: Long,
    val message: String = "Alert acknowledged successfully"
)

@Serializable
data class GenericSuccessResponse(
    val success: Boolean = true,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)