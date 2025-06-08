package com.ataiva.eden.insight.repository

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Database table definitions for Insight Service
 */
object AnalyticsQueries : Table("analytics_queries") {
    val id = varchar("id", 255)
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val queryText = text("query_text")
    val queryType = varchar("query_type", 50)
    val parameters = text("parameters") // JSON serialized
    val createdBy = varchar("created_by", 255)
    val createdAt = timestamp("created_at")
    val lastModified = timestamp("last_modified")
    val isActive = bool("is_active")
    val tags = text("tags") // JSON serialized

    override val primaryKey = PrimaryKey(id)
}

object QueryExecutions : Table("query_executions") {
    val id = varchar("id", 255)
    val queryId = varchar("query_id", 255).references(AnalyticsQueries.id)
    val executedBy = varchar("executed_by", 255)
    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time").nullable()
    val status = varchar("status", 50)
    val resultCount = integer("result_count")
    val executionTimeMs = long("execution_time_ms")
    val errorMessage = text("error_message").nullable()
    val parameters = text("parameters") // JSON serialized

    override val primaryKey = PrimaryKey(id)
}

object Reports : Table("reports") {
    val id = varchar("id", 255)
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val templateId = varchar("template_id", 255)
    val parameters = text("parameters") // JSON serialized
    val schedule = text("schedule").nullable() // JSON serialized
    val recipients = text("recipients") // JSON serialized
    val format = varchar("format", 50)
    val createdBy = varchar("created_by", 255)
    val createdAt = timestamp("created_at")
    val lastGenerated = timestamp("last_generated").nullable()
    val isActive = bool("is_active")

    override val primaryKey = PrimaryKey(id)
}

object ReportTemplates : Table("report_templates") {
    val id = varchar("id", 255)
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val templateContent = text("template_content")
    val requiredParameters = text("required_parameters") // JSON serialized
    val supportedFormats = text("supported_formats") // JSON serialized
    val category = varchar("category", 100)
    val createdBy = varchar("created_by", 255)
    val createdAt = timestamp("created_at")
    val version = varchar("version", 50)

    override val primaryKey = PrimaryKey(id)
}

object ReportExecutions : Table("report_executions") {
    val id = varchar("id", 255)
    val reportId = varchar("report_id", 255).references(Reports.id)
    val executedBy = varchar("executed_by", 255)
    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time").nullable()
    val status = varchar("status", 50)
    val outputPath = varchar("output_path", 500).nullable()
    val fileSize = long("file_size")
    val errorMessage = text("error_message").nullable()
    val parameters = text("parameters") // JSON serialized

    override val primaryKey = PrimaryKey(id)
}

object Dashboards : Table("dashboards") {
    val id = varchar("id", 255)
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val widgets = text("widgets") // JSON serialized
    val layout = text("layout") // JSON serialized
    val permissions = text("permissions") // JSON serialized
    val createdBy = varchar("created_by", 255)
    val createdAt = timestamp("created_at")
    val lastModified = timestamp("last_modified")
    val isPublic = bool("is_public")
    val tags = text("tags") // JSON serialized

    override val primaryKey = PrimaryKey(id)
}

object Metrics : Table("metrics") {
    val id = varchar("id", 255)
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val category = varchar("category", 100)
    val unit = varchar("unit", 50).nullable()
    val aggregationType = varchar("aggregation_type", 50)
    val queryId = varchar("query_id", 255)
    val thresholds = text("thresholds") // JSON serialized
    val isActive = bool("is_active")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object MetricValues : Table("metric_values") {
    val id = varchar("id", 255)
    val metricId = varchar("metric_id", 255).references(Metrics.id)
    val value = double("value")
    val timestamp = timestamp("timestamp")
    val dimensions = text("dimensions") // JSON serialized
    val metadata = text("metadata") // JSON serialized

    override val primaryKey = PrimaryKey(id)
}

object KPIs : Table("kpis") {
    val id = varchar("id", 255)
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val targetValue = double("target_value")
    val currentValue = double("current_value")
    val unit = varchar("unit", 50).nullable()
    val trend = varchar("trend", 50)
    val category = varchar("category", 100)
    val lastUpdated = timestamp("last_updated")
    val historicalData = text("historical_data") // JSON serialized

    override val primaryKey = PrimaryKey(id)
}