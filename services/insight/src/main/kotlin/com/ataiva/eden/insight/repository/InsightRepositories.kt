package com.ataiva.eden.insight.repository

import com.ataiva.eden.database.Repository
import com.ataiva.eden.insight.model.*
import java.time.LocalDateTime

/**
 * Repository interface for analytics queries
 */
interface AnalyticsQueryRepository : Repository<AnalyticsQuery, String> {
    suspend fun findByName(name: String): AnalyticsQuery?
    suspend fun findByCreatedBy(createdBy: String): List<AnalyticsQuery>
    suspend fun findByQueryType(queryType: QueryType): List<AnalyticsQuery>
    suspend fun findByTags(tags: List<String>): List<AnalyticsQuery>
    suspend fun findActive(): List<AnalyticsQuery>
    suspend fun findByCreatedByAndActive(createdBy: String, isActive: Boolean): List<AnalyticsQuery>
    suspend fun updateStatus(id: String, isActive: Boolean): Boolean
    suspend fun search(namePattern: String): List<AnalyticsQuery>
}

/**
 * Repository interface for query executions
 */
interface QueryExecutionRepository : Repository<QueryExecution, String> {
    suspend fun findByQueryId(queryId: String): List<QueryExecution>
    suspend fun findByStatus(status: ExecutionStatus): List<QueryExecution>
    suspend fun findByExecutedBy(executedBy: String): List<QueryExecution>
    suspend fun findByTimeRange(startTime: Long, endTime: Long): List<QueryExecution>
    suspend fun findByQueryIdAndTimeRange(queryId: String, startTime: Long, endTime: Long): List<QueryExecution>
    suspend fun findLatestByQueryId(queryId: String): QueryExecution?
    suspend fun updateStatus(id: String, status: ExecutionStatus): Boolean
}

/**
 * Repository interface for reports
 */
interface ReportRepository : Repository<Report, String> {
    suspend fun findByName(name: String): Report?
    suspend fun findByCreatedBy(createdBy: String): List<Report>
    suspend fun findByTemplateId(templateId: String): List<Report>
    suspend fun findActive(): List<Report>
    suspend fun findByCreatedByAndActive(createdBy: String, isActive: Boolean): List<Report>
    suspend fun findScheduled(): List<Report>
    suspend fun findScheduledBefore(time: Long): List<Report>
    suspend fun updateStatus(id: String, isActive: Boolean): Boolean
    suspend fun updateLastGenerated(id: String, timestamp: Long): Boolean
    suspend fun search(namePattern: String): List<Report>
}

/**
 * Repository interface for report templates
 */
interface ReportTemplateRepository : Repository<ReportTemplate, String> {
    suspend fun findByName(name: String): ReportTemplate?
    suspend fun findByCategory(category: String): List<ReportTemplate>
    suspend fun findByCreatedBy(createdBy: String): List<ReportTemplate>
    suspend fun search(namePattern: String): List<ReportTemplate>
}

/**
 * Repository interface for report executions
 */
interface ReportExecutionRepository : Repository<ReportExecution, String> {
    suspend fun findByReportId(reportId: String): List<ReportExecution>
    suspend fun findByStatus(status: ExecutionStatus): List<ReportExecution>
    suspend fun findByExecutedBy(executedBy: String): List<ReportExecution>
    suspend fun findByTimeRange(startTime: Long, endTime: Long): List<ReportExecution>
    suspend fun findByReportIdAndTimeRange(reportId: String, startTime: Long, endTime: Long): List<ReportExecution>
    suspend fun findLatestByReportId(reportId: String): ReportExecution?
    suspend fun updateStatus(id: String, status: ExecutionStatus): Boolean
    suspend fun updateOutput(id: String, outputPath: String, fileSize: Long): Boolean
    suspend fun updateError(id: String, errorMessage: String): Boolean
}

/**
 * Repository interface for dashboards
 */
interface DashboardRepository : Repository<Dashboard, String> {
    suspend fun findByName(name: String): Dashboard?
    suspend fun findByCreatedBy(createdBy: String): List<Dashboard>
    suspend fun findPublic(): List<Dashboard>
    suspend fun findByTags(tags: List<String>): List<Dashboard>
    suspend fun findByCreatedByOrPublic(createdBy: String): List<Dashboard>
    suspend fun search(namePattern: String): List<Dashboard>
}

/**
 * Repository interface for metrics
 */
interface MetricRepository : Repository<Metric, String> {
    suspend fun findByName(name: String): Metric?
    suspend fun findByCategory(category: String): List<Metric>
    suspend fun findActive(): List<Metric>
    suspend fun findByQueryId(queryId: String): List<Metric>
    suspend fun updateStatus(id: String, isActive: Boolean): Boolean
    suspend fun search(namePattern: String): List<Metric>
}

/**
 * Repository interface for metric values
 */
interface MetricValueRepository : Repository<MetricValue, String> {
    suspend fun findByMetricId(metricId: String): List<MetricValue>
    suspend fun findByMetricIdAndTimeRange(metricId: String, startTime: Long, endTime: Long): List<MetricValue>
    suspend fun findByDimension(dimension: String, value: String): List<MetricValue>
    suspend fun findLatestByMetricId(metricId: String, limit: Int = 1): List<MetricValue>
    suspend fun deleteOlderThan(timestamp: Long): Int
    suspend fun bulkInsert(values: List<MetricValue>): Int
}

/**
 * Repository interface for KPIs
 */
interface KPIRepository : Repository<KPI, String> {
    suspend fun findByName(name: String): KPI?
    suspend fun findByCategory(category: String): List<KPI>
    suspend fun updateValue(id: String, currentValue: Double, trend: TrendDirection): Boolean
    suspend fun updateHistoricalData(id: String, dataPoint: KPIDataPoint): Boolean
    suspend fun search(namePattern: String): List<KPI>
}