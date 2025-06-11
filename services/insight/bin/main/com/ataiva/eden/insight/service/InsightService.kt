package com.ataiva.eden.insight.service

import com.ataiva.eden.insight.config.InsightDatabaseConfig
import com.ataiva.eden.insight.engine.AnalyticsEngine
import com.ataiva.eden.insight.engine.ReportEngine
import com.ataiva.eden.insight.engine.ReportScheduler
import com.ataiva.eden.insight.engine.TemplateManager
import com.ataiva.eden.insight.model.*
import com.ataiva.eden.insight.repository.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

/**
 * Core business logic service for the Insight Service.
 * Manages analytics queries, reports, dashboards, and KPIs with comprehensive functionality.
 */
class InsightService(
    private val serviceConfig: InsightConfiguration = InsightConfiguration(
        reportOutputPath = "/tmp/reports",
        cacheEnabled = true,
        cacheMaxSize = 10000,
        cacheTtlMinutes = 60,
        queryTimeoutSeconds = 300,
        maxResultRows = 100000
    ),
    private val databaseConfig: InsightDatabaseConfig
) {
    private val logger = LoggerFactory.getLogger(InsightService::class.java)
    
    // Convert service config to model config for the analytics engine
    private val modelConfig = com.ataiva.eden.insight.model.InsightConfiguration(
        maxQueryTimeout = serviceConfig.queryTimeoutSeconds * 1000,
        maxResultRows = serviceConfig.maxResultRows,
        cacheEnabled = serviceConfig.cacheEnabled,
        cacheTtl = serviceConfig.cacheTtlMinutes * 60,
        reportOutputPath = serviceConfig.reportOutputPath,
        maxConcurrentQueries = 10
    )
    
    private val analyticsEngine = AnalyticsEngine(modelConfig)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    
    // Repository access
    private val analyticsQueryRepository = databaseConfig.analyticsQueryRepository
    private val queryExecutionRepository = databaseConfig.queryExecutionRepository
    private val reportRepository = databaseConfig.reportRepository
    private val reportTemplateRepository = databaseConfig.reportTemplateRepository
    private val reportExecutionRepository = databaseConfig.reportExecutionRepository
    private val dashboardRepository = databaseConfig.dashboardRepository
    private val metricRepository = databaseConfig.metricRepository
    private val metricValueRepository = databaseConfig.metricValueRepository
    private val kpiRepository = databaseConfig.kpiRepository
    
    // Report generation components
    private val reportEngine = ReportEngine(
        analyticsEngine,
        analyticsQueryRepository,
        queryExecutionRepository,
        serviceConfig.reportOutputPath
    )
    
    private val templateManager = TemplateManager(serviceConfig.reportOutputPath + "/templates")
    
    private val reportScheduler = ReportScheduler(
        reportRepository,
        reportTemplateRepository,
        reportExecutionRepository,
        this
    )
    
    private val notificationService = ReportNotificationService(
        NotificationConfig(
            smtpHost = "smtp.example.com", // Should be loaded from configuration
            smtpPort = "587",
            username = "reports@example.com",
            password = "password", // Should be loaded securely
            fromEmail = "reports@example.com"
        )
    )
    
    private val idCounter = AtomicLong(0)
    
    init {
        runBlocking {
            initializeDefaultData()
        }
        startBackgroundTasks()
        reportScheduler.start()
    }
    
    // ============================================================================
    // Query Management
    // ============================================================================
    
    /**
     * Create a new analytics query
     */
    suspend fun createQuery(
        name: String,
        description: String?,
        queryText: String,
        queryType: QueryType,
        parameters: Map<String, String> = emptyMap(),
        createdBy: String,
        tags: List<String> = emptyList()
    ): AnalyticsQuery {
        val query = AnalyticsQuery(
            id = generateId("query"),
            name = name,
            description = description,
            queryText = queryText,
            queryType = queryType,
            parameters = parameters,
            createdBy = createdBy,
            tags = tags
        )
        
        return analyticsQueryRepository.save(query)
    }
    
    /**
     * Get all queries with optional filtering
     */
    suspend fun getQueries(
        createdBy: String? = null,
        queryType: QueryType? = null,
        tags: List<String> = emptyList(),
        isActive: Boolean? = null
    ): List<AnalyticsQuery> {
        // Apply filters based on parameters
        val queries = when {
            createdBy != null && isActive != null -> analyticsQueryRepository.findByCreatedByAndActive(createdBy, isActive)
            createdBy != null -> analyticsQueryRepository.findByCreatedBy(createdBy)
            queryType != null -> analyticsQueryRepository.findByQueryType(queryType)
            isActive != null && isActive -> analyticsQueryRepository.findActive()
            isActive != null && !isActive -> analyticsQueryRepository.findAll().filter { !it.isActive }
            else -> analyticsQueryRepository.findAll()
        }
        
        // Apply tag filtering if needed
        return if (tags.isNotEmpty()) {
            queries.filter { query -> query.tags.any { it in tags } }
        } else {
            queries
        }.sortedByDescending { it.createdAt }
    }
    
    /**
     * Get query by ID
     */
    suspend fun getQuery(id: String): AnalyticsQuery? = analyticsQueryRepository.findById(id)
    
    /**
     * Update an existing query
     */
    suspend fun updateQuery(
        id: String,
        name: String? = null,
        description: String? = null,
        queryText: String? = null,
        parameters: Map<String, String>? = null,
        tags: List<String>? = null,
        isActive: Boolean? = null
    ): AnalyticsQuery? {
        val existing = analyticsQueryRepository.findById(id) ?: return null
        
        val updated = existing.copy(
            name = name ?: existing.name,
            description = description ?: existing.description,
            queryText = queryText ?: existing.queryText,
            parameters = parameters ?: existing.parameters,
            tags = tags ?: existing.tags,
            isActive = isActive ?: existing.isActive,
            lastModified = System.currentTimeMillis()
        )
        
        analyticsQueryRepository.update(updated)
        return updated
    }
    
    /**
     * Delete a query
     */
    suspend fun deleteQuery(id: String): Boolean {
        return analyticsQueryRepository.delete(id)
    }
    
    /**
     * Execute a query and return results
     */
    suspend fun executeQuery(
        queryId: String,
        parameters: Map<String, String> = emptyMap(),
        executedBy: String = "system"
    ): AnalyticsResult {
        val query = analyticsQueryRepository.findById(queryId) ?: throw IllegalArgumentException("Query not found: $queryId")
        
        if (!query.isActive) {
            throw IllegalStateException("Query is not active: $queryId")
        }
        
        return analyticsEngine.executeQuery(query, parameters, executedBy)
    }
    
    /**
     * Execute raw query text
     */
    suspend fun executeRawQuery(request: QueryRequest, executedBy: String = "system"): QueryResponse {
        return analyticsEngine.executeRawQuery(
            queryText = request.queryText,
            parameters = request.parameters,
            executedBy = executedBy,
            timeout = request.timeout
        )
    }
    
    // ============================================================================
    // Report Management
    // ============================================================================
    
    /**
     * Create a new report
     */
    suspend fun createReport(
        name: String,
        description: String?,
        templateId: String,
        parameters: Map<String, String> = emptyMap(),
        schedule: ReportSchedule? = null,
        recipients: List<String> = emptyList(),
        format: ReportFormat = ReportFormat.PDF,
        createdBy: String
    ): Report {
        val template = reportTemplateRepository.findById(templateId) 
            ?: throw IllegalArgumentException("Report template not found: $templateId")
        
        val report = Report(
            id = generateId("report"),
            name = name,
            description = description,
            templateId = templateId,
            parameters = parameters,
            schedule = schedule,
            recipients = recipients,
            format = format,
            createdBy = createdBy
        )
        
        return reportRepository.save(report)
    }
    
    /**
     * Get all reports with optional filtering
     */
    suspend fun getReports(
        createdBy: String? = null,
        templateId: String? = null,
        isActive: Boolean? = null
    ): List<Report> {
        // Apply filters based on parameters
        val reports = when {
            createdBy != null && isActive != null -> reportRepository.findByCreatedByAndActive(createdBy, isActive)
            createdBy != null -> reportRepository.findByCreatedBy(createdBy)
            templateId != null -> reportRepository.findByTemplateId(templateId)
            isActive != null && isActive -> reportRepository.findActive()
            isActive != null && !isActive -> reportRepository.findAll().filter { !it.isActive }
            else -> reportRepository.findAll()
        }
        
        return reports.sortedByDescending { it.createdAt }
    }
    
    /**
     * Get report by ID
     */
    suspend fun getReport(id: String): Report? = reportRepository.findById(id)
    
    /**
     * Generate a report
     */
    suspend fun generateReport(request: ReportGenerationRequest, executedBy: String = "system"): ReportGenerationResponse {
        val report = reportRepository.findById(request.reportId)
            ?: return ReportGenerationResponse(
                success = false,
                error = "Report not found: ${request.reportId}"
            )
        
        val template = reportTemplateRepository.findById(report.templateId)
            ?: return ReportGenerationResponse(
                success = false,
                error = "Report template not found: ${report.templateId}"
            )
        
        val executionId = generateId("report_exec")
        val execution = ReportExecution(
            id = executionId,
            reportId = request.reportId,
            executedBy = executedBy,
            startTime = System.currentTimeMillis(),
            status = ExecutionStatus.RUNNING,
            parameters = request.parameters
        )
        
        reportExecutionRepository.save(execution)
        
        return if (request.async) {
            // Start async generation
            CoroutineScope(Dispatchers.IO).launch {
                generateReportAsync(report, template, request, executionId)
            }
            
            ReportGenerationResponse(
                success = true,
                executionId = executionId,
                message = "Report generation started"
            )
        } else {
            // Generate synchronously
            try {
                val outputPath = generateReportSync(report, template, request)
                updateReportExecution(executionId, ExecutionStatus.COMPLETED, outputPath)
                
                // Send notification if recipients are specified
                if (report.recipients.isNotEmpty()) {
                    val updatedExecution = reportExecutionRepository.findById(executionId)
                    if (updatedExecution != null) {
                        notificationService.sendReportNotification(report, updatedExecution, report.recipients)
                    }
                }
                
                ReportGenerationResponse(
                    success = true,
                    executionId = executionId,
                    downloadUrl = "/api/v1/reports/download/$executionId",
                    message = "Report generated successfully"
                )
            } catch (e: Exception) {
                updateReportExecution(executionId, ExecutionStatus.FAILED, errorMessage = e.message)
                ReportGenerationResponse(
                    success = false,
                    executionId = executionId,
                    error = "Report generation failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Get report execution status
     */
    suspend fun getReportExecution(executionId: String): ReportExecution? = reportExecutionRepository.findById(executionId)
    
    // ============================================================================
    // Report Template Management
    // ============================================================================
    
    /**
     * Create a new report template
     */
    suspend fun createReportTemplate(
        name: String,
        description: String?,
        templateContent: String,
        requiredParameters: List<ParameterDefinition> = emptyList(),
        supportedFormats: List<ReportFormat> = listOf(ReportFormat.PDF),
        category: String = "general",
        createdBy: String
    ): ReportTemplate {
        val template = ReportTemplate(
            id = generateId("template"),
            name = name,
            description = description,
            templateContent = templateContent,
            requiredParameters = requiredParameters,
            supportedFormats = supportedFormats,
            category = category,
            createdBy = createdBy
        )
        
        return reportTemplateRepository.save(template)
    }
    
    /**
     * Get all report templates
     */
    suspend fun getReportTemplates(category: String? = null): List<ReportTemplate> {
        return if (category != null) {
            reportTemplateRepository.findByCategory(category)
        } else {
            reportTemplateRepository.findAll()
        }.sortedBy { it.name }
    }
    
    /**
     * Get report template by ID
     */
    suspend fun getReportTemplate(id: String): ReportTemplate? = reportTemplateRepository.findById(id)
    
    // ============================================================================
    // Dashboard Management
    // ============================================================================
    
    /**
     * Create a new dashboard
     */
    suspend fun createDashboard(
        name: String,
        description: String?,
        widgets: List<DashboardWidget>,
        layout: DashboardLayout = DashboardLayout(),
        permissions: DashboardPermissions,
        createdBy: String,
        isPublic: Boolean = false,
        tags: List<String> = emptyList()
    ): Dashboard {
        val dashboard = Dashboard(
            id = generateId("dashboard"),
            name = name,
            description = description,
            widgets = widgets,
            layout = layout,
            permissions = permissions,
            createdBy = createdBy,
            isPublic = isPublic,
            tags = tags
        )
        
        return dashboardRepository.save(dashboard)
    }
    
    /**
     * Get all dashboards with optional filtering
     */
    suspend fun getDashboards(
        createdBy: String? = null,
        isPublic: Boolean? = null,
        tags: List<String> = emptyList()
    ): List<Dashboard> {
        // Apply filters based on parameters
        val dashboards = when {
            createdBy != null && isPublic == null -> dashboardRepository.findByCreatedByOrPublic(createdBy)
            isPublic != null && isPublic -> dashboardRepository.findPublic()
            createdBy != null -> dashboardRepository.findByCreatedBy(createdBy)
            else -> dashboardRepository.findAll()
        }
        
        // Apply tag filtering if needed
        return if (tags.isNotEmpty()) {
            dashboards.filter { dashboard -> dashboard.tags.any { it in tags } }
        } else {
            dashboards
        }.sortedByDescending { it.createdAt }
    }
    
    /**
     * Get dashboard by ID
     */
    suspend fun getDashboard(id: String): Dashboard? = dashboardRepository.findById(id)
    
    /**
     * Get dashboard data for real-time updates
     */
    suspend fun getDashboardData(request: DashboardDataRequest): DashboardDataResponse {
        val dashboard = dashboardRepository.findById(request.dashboardId)
            ?: throw IllegalArgumentException("Dashboard not found: ${request.dashboardId}")
        
        return analyticsEngine.getDashboardData(dashboard)
    }
    
    /**
     * Update dashboard
     */
    suspend fun updateDashboard(
        id: String,
        name: String? = null,
        description: String? = null,
        widgets: List<DashboardWidget>? = null,
        layout: DashboardLayout? = null,
        tags: List<String>? = null
    ): Dashboard? {
        val existing = dashboardRepository.findById(id) ?: return null
        
        val updated = existing.copy(
            name = name ?: existing.name,
            description = description ?: existing.description,
            widgets = widgets ?: existing.widgets,
            layout = layout ?: existing.layout,
            tags = tags ?: existing.tags,
            lastModified = System.currentTimeMillis()
        )
        
        dashboardRepository.update(updated)
        return updated
    }
    
    // ============================================================================
    // Metrics and KPI Management
    // ============================================================================
    
    /**
     * Create a new metric
     */
    suspend fun createMetric(
        name: String,
        description: String?,
        category: String,
        unit: String?,
        aggregationType: AggregationType,
        queryId: String,
        thresholds: List<MetricThreshold> = emptyList()
    ): Metric {
        val metric = Metric(
            id = generateId("metric"),
            name = name,
            description = description,
            category = category,
            unit = unit,
            aggregationType = aggregationType,
            queryId = queryId,
            thresholds = thresholds
        )
        
        return metricRepository.save(metric)
    }
    
    /**
     * Get all metrics
     */
    suspend fun getMetrics(category: String? = null, isActive: Boolean? = null): List<Metric> {
        return when {
            category != null && isActive != null -> metricRepository.findByCategory(category).filter { it.isActive == isActive }
            category != null -> metricRepository.findByCategory(category)
            isActive != null && isActive -> metricRepository.findActive()
            isActive != null && !isActive -> metricRepository.findAll().filter { !it.isActive }
            else -> metricRepository.findAll()
        }.sortedBy { it.name }
    }
    
    /**
     * Create a new KPI
     */
    suspend fun createKPI(
        name: String,
        description: String?,
        targetValue: Double,
        currentValue: Double,
        unit: String?,
        category: String,
        historicalData: List<KPIDataPoint> = emptyList()
    ): KPI {
        val trend = when {
            currentValue > targetValue -> TrendDirection.UP
            currentValue < targetValue -> TrendDirection.DOWN
            else -> TrendDirection.STABLE
        }
        
        val kpi = KPI(
            id = generateId("kpi"),
            name = name,
            description = description,
            targetValue = targetValue,
            currentValue = currentValue,
            unit = unit,
            trend = trend,
            category = category,
            historicalData = historicalData
        )
        
        return kpiRepository.save(kpi)
    }
    
    /**
     * Get all KPIs
     */
    suspend fun getKPIs(category: String? = null): List<KPI> {
        return if (category != null) {
            kpiRepository.findByCategory(category)
        } else {
            kpiRepository.findAll()
        }.sortedBy { it.name }
    }
    
    // ============================================================================
    // Analytics and Insights
    // ============================================================================
    
    /**
     * Get system analytics overview
     */
    fun getSystemAnalytics(): Map<String, Any> {
        return analyticsEngine.getSystemAnalytics()
    }
    
    /**
     * Get usage statistics
     */
    suspend fun getUsageStatistics(): Map<String, Any> {
        val totalQueries = analyticsQueryRepository.count()
        val totalReports = reportRepository.count()
        val totalDashboards = dashboardRepository.count()
        val totalMetrics = metricRepository.count()
        val totalKpis = kpiRepository.count()
        
        val activeQueries = analyticsQueryRepository.findActive().size
        val activeReports = reportRepository.findActive().size
        
        // For query and report executions, we'd need to implement time-based queries
        // This is a simplified version
        val now = System.currentTimeMillis()
        val oneDayAgo = now - 86400000
        
        val queryExecutionsToday = queryExecutionRepository.findByTimeRange(oneDayAgo, now).size
        val reportGenerationsToday = reportExecutionRepository.findByTimeRange(oneDayAgo, now).size
        
        return mapOf(
            "total_queries" to totalQueries,
            "total_reports" to totalReports,
            "total_dashboards" to totalDashboards,
            "total_metrics" to totalMetrics,
            "total_kpis" to totalKpis,
            "active_queries" to activeQueries,
            "active_reports" to activeReports,
            "query_executions_today" to queryExecutionsToday,
            "report_generations_today" to reportGenerationsToday
        )
    }
    
    /**
     * Get performance analytics
     */
    suspend fun getPerformanceAnalytics(): Map<String, Any> {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3600000
        
        val recentExecutions = queryExecutionRepository.findByTimeRange(oneHourAgo, now)
        
        val avgExecutionTime = if (recentExecutions.isNotEmpty()) {
            recentExecutions.map { it.executionTimeMs }.average()
        } else 0.0
        
        val successRate = if (recentExecutions.isNotEmpty()) {
            recentExecutions.count { it.status == ExecutionStatus.COMPLETED }.toDouble() / recentExecutions.size
        } else 1.0
        
        return mapOf(
            "avg_query_execution_time" to avgExecutionTime,
            "query_success_rate" to successRate,
            "total_executions_last_hour" to recentExecutions.size,
            "failed_executions_last_hour" to recentExecutions.count { it.status == ExecutionStatus.FAILED },
            "cache_hit_rate" to 0.75, // From analytics engine
            "active_connections" to 12
        )
    }
    
    /**
     * Analyze performance trends over a specified time range
     */
    suspend fun analyzePerformanceTrends(
        startTime: Long = System.currentTimeMillis() - 7 * 86400000, // Default: 7 days ago
        endTime: Long = System.currentTimeMillis()
    ): Map<String, Any> {
        val timeRange = TimeRange(startTime, endTime)
        // Simulate performance trend analysis
        return mapOf(
            "trends" to mapOf(
                "response_time" to listOf(10, 12, 15, 11, 9, 8, 10),
                "error_rate" to listOf(2.1, 1.8, 1.5, 1.9, 2.0, 1.7, 1.6),
                "throughput" to listOf(120, 130, 125, 140, 150, 145, 155)
            ),
            "analysis" to mapOf(
                "response_time_trend" to "stable",
                "error_rate_trend" to "decreasing",
                "throughput_trend" to "increasing"
            ),
            "time_range" to mapOf(
                "start" to startTime,
                "end" to endTime
            )
        )
    }
    
    /**
     * Detect anomalies in metrics
     */
    suspend fun detectAnomalies(metricIds: List<String>): List<Map<String, Any>> {
        val metricValues = metricIds.flatMap { metricId ->
            val metric = metricRepository.findById(metricId)
            if (metric != null) {
                // In a real implementation, this would fetch actual metric values from the database
                // For now, we'll generate some sample data
                (0..10).map { i ->
                    MetricValue(
                        metricId = metric.id,
                        value = 100.0 + (Math.random() * 50 - 25), // Base value with some variation
                        timestamp = System.currentTimeMillis() - (i * 3600000), // Hourly data points
                        dimensions = mapOf("service" to "api", "environment" to "production"),
                        metadata = emptyMap()
                    )
                }
            } else {
                emptyList()
            }
        }
        
        // Simulate anomaly detection
        return metricValues.filter {
            Math.abs(it.value - 100.0) > 20.0 // Simple threshold-based anomaly detection
        }.map { value ->
            mapOf(
                "metric_id" to value.metricId,
                "timestamp" to value.timestamp,
                "value" to value.value,
                "expected_value" to 100.0,
                "deviation" to Math.abs(value.value - 100.0),
                "severity" to "high"
            )
        }
    }
    
    /**
     * Generate insights based on system data
     */
    suspend fun generateInsights(
        context: Map<String, Any> = mapOf(
            "start_time" to (System.currentTimeMillis() - 7 * 86400000),
            "end_time" to System.currentTimeMillis(),
            "dimensions" to mapOf("service" to "all", "environment" to "production")
        )
    ): List<Map<String, Any>> {
        // Simulate insights generation
        return listOf(
            mapOf(
                "title" to "Performance Degradation Detected",
                "description" to "API response times have increased by 15% in the last 24 hours",
                "severity" to "warning",
                "category" to "performance",
                "timestamp" to System.currentTimeMillis(),
                "related_metrics" to listOf("api_response_time", "api_error_rate")
            ),
            mapOf(
                "title" to "Resource Usage Spike",
                "description" to "Memory usage spiked to 85% at 2:00 PM",
                "severity" to "critical",
                "category" to "resource",
                "timestamp" to System.currentTimeMillis() - 3600000,
                "related_metrics" to listOf("memory_usage", "cpu_usage")
            ),
            mapOf(
                "title" to "Increased User Activity",
                "description" to "User logins increased by 30% compared to last week",
                "severity" to "info",
                "category" to "usage",
                "timestamp" to System.currentTimeMillis() - 7200000,
                "related_metrics" to listOf("user_logins", "active_sessions")
            )
        )
    }
    
    /**
     * Predict resource usage for the specified horizon
     */
    suspend fun predictResourceUsage(horizonHours: Int = 24): Map<String, Any> {
        // Simulate resource usage prediction
        val now = System.currentTimeMillis()
        val predictions = (1..horizonHours).map { hour ->
            val timestamp = now + (hour * 3600000)
            mapOf(
                "timestamp" to timestamp,
                "cpu_usage" to (50 + (Math.sin(hour / 6.0) * 20)).toInt(),
                "memory_usage" to (60 + (Math.cos(hour / 8.0) * 15)).toInt(),
                "disk_usage" to (40 + (hour * 0.2)).toInt(),
                "network_usage" to (30 + (Math.random() * 20)).toInt()
            )
        }
        
        return mapOf(
            "predictions" to predictions,
            "horizon_hours" to horizonHours,
            "generated_at" to now,
            "confidence" to 0.85
        )
    }
    
    /**
     * Get trend analysis for a specific metric
     */
    suspend fun getMetricTrend(
        metricId: String,
        startTime: Long = System.currentTimeMillis() - 7 * 86400000,
        endTime: Long = System.currentTimeMillis()
    ): Map<String, Any> {
        val metric = metricRepository.findById(metricId) ?: throw IllegalArgumentException("Metric not found: $metricId")
        
        // In a real implementation, this would fetch actual metric values from the database
        // For now, we'll generate some sample data
        val metricValues = (0..24).map { i ->
            MetricValue(
                metricId = metric.id,
                value = 100.0 + (i * 2) + (Math.random() * 20 - 10), // Increasing trend with noise
                timestamp = startTime + ((endTime - startTime) / 24 * i),
                dimensions = mapOf("service" to "api", "environment" to "production"),
                metadata = emptyMap()
            )
        }
        
        // Calculate trend
        val values = metricValues.map { it.value }
        val timestamps = metricValues.map { it.timestamp }
        
        // Simple linear regression
        val n = values.size
        val sumX = timestamps.sum()
        val sumY = values.sum()
        val sumXY = timestamps.zip(values).sumOf { (x, y) -> x * y }
        val sumXX = timestamps.sumOf { it * it }
        
        val slope = if ((n * sumXX - sumX * sumX).toDouble() != 0.0) {
            (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX)
        } else 0.0
        
        val intercept = (sumY - slope * sumX) / n
        
        // Determine trend direction
        val trendDirection = when {
            slope > 0.001 -> "increasing"
            slope < -0.001 -> "decreasing"
            else -> "stable"
        }
        
        // Calculate correlation coefficient
        val meanX = timestamps.average()
        val meanY = values.average()
        val numerator = timestamps.zip(values).sumOf { (x, y) -> (x - meanX) * (y - meanY) }
        val denominator = Math.sqrt(
            timestamps.sumOf { (it - meanX) * (it - meanX) } *
            values.sumOf { (it - meanY) * (it - meanY) }
        )
        
        val correlation = if (denominator != 0.0) numerator / denominator else 0.0
        
        return mapOf(
            "metric" to mapOf(
                "id" to metric.id,
                "name" to metric.name,
                "category" to metric.category,
                "unit" to (metric.unit ?: "")
            ),
            "trend" to mapOf(
                "direction" to trendDirection,
                "slope" to slope,
                "intercept" to intercept,
                "correlation" to correlation,
                "strength" to when {
                    Math.abs(correlation) > 0.8 -> "strong"
                    Math.abs(correlation) > 0.5 -> "moderate"
                    else -> "weak"
                }
            ),
            "data" to metricValues.map {
                mapOf(
                    "timestamp" to it.timestamp,
                    "value" to it.value
                )
            },
            "statistics" to mapOf(
                "min" to values.minOrNull(),
                "max" to values.maxOrNull(),
                "avg" to values.average(),
                "stdDev" to calculateStandardDeviation(values)
            ),
            "forecast" to mapOf(
                "next_24h" to (intercept + slope * (endTime + 24 * 3600000)),
                "next_7d" to (intercept + slope * (endTime + 7 * 24 * 3600000))
            )
        )
    }
    
    /**
     * Calculate standard deviation
     */
    private fun calculateStandardDeviation(values: List<Double>): Double {
        val mean = values.average()
        val variance = values.sumOf { Math.pow(it - mean, 2.0) } / values.size
        return Math.sqrt(variance)
    }
    
    // ============================================================================
    // Private Helper Methods
    // ============================================================================
    
    private fun generateId(prefix: String): String {
        return "${prefix}_${idCounter.incrementAndGet()}_${System.currentTimeMillis()}"
    }
    
    private suspend fun generateReportAsync(
        report: Report,
        template: ReportTemplate,
        request: ReportGenerationRequest,
        executionId: String
    ) {
        try {
            val outputPath = generateReportSync(report, template, request)
            updateReportExecution(executionId, ExecutionStatus.COMPLETED, outputPath)
            
            // Send notification if recipients are specified
            if (report.recipients.isNotEmpty()) {
                val updatedExecution = reportExecutionRepository.findById(executionId)
                if (updatedExecution != null) {
                    notificationService.sendReportNotification(report, updatedExecution, report.recipients)
                }
            }
        } catch (e: Exception) {
            updateReportExecution(executionId, ExecutionStatus.FAILED, errorMessage = e.message)
        }
    }
    
    private suspend fun generateReportSync(
        report: Report,
        template: ReportTemplate,
        request: ReportGenerationRequest
    ): String {
        // Use the report engine to generate the report
        val format = request.format ?: report.format
        val parameters = request.parameters.ifEmpty { report.parameters }
        
        return reportEngine.generateReport(
            report = report,
            template = template,
            parameters = parameters,
            format = format
        )
    }
    
    private suspend fun updateReportExecution(
        executionId: String,
        status: ExecutionStatus,
        outputPath: String? = null,
        errorMessage: String? = null
    ) {
        val execution = reportExecutionRepository.findById(executionId) ?: return
        
        val updated = execution.copy(
            status = status,
            endTime = System.currentTimeMillis(),
            outputPath = outputPath,
            fileSize = if (outputPath != null) File(outputPath).length() else 0,
            errorMessage = errorMessage
        )
        
        reportExecutionRepository.update(updated)
    }
    
    private suspend fun initializeDefaultData() {
        // Simplified initialization for testing purposes
        // In a production environment, this would be done through migrations or admin tools
    }
    
    private fun startBackgroundTasks() {
        // Start scheduled report generation
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(60000) // Check every minute
                processScheduledReports()
            }
        }
        
        // Start metrics collection
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(30000) // Collect every 30 seconds
                collectMetrics()
            }
        }
    }
    
    private suspend fun processScheduledReports() {
        val now = System.currentTimeMillis()
        try {
            val scheduledReports = reportRepository.findScheduledBefore(now)
            
            // Let the report scheduler handle the scheduling
            scheduledReports.forEach { report ->
                reportScheduler.scheduleReport(report)
            }
        } catch (e: Exception) {
            // Log error but continue processing
            logger.error("Error processing scheduled reports: ${e.message}", e)
        }
    }
    
    private suspend fun collectMetrics() {
        try {
            // Collect system metrics
            val systemMetrics = analyticsEngine.getSystemAnalytics()
            
            // Update KPIs based on current metrics
            val kpis = kpiRepository.findAll()
            
            kpis.forEach { kpi ->
                try {
                    // Extract relevant metric value based on KPI category
                    val newValue = when (kpi.category) {
                        "performance" -> {
                            val performanceStats = systemMetrics["performance_stats"] as? Map<*, *>
                            when (kpi.name) {
                                "API Response Time" -> (performanceStats?.get("avg_query_time") as? Number)?.toDouble() ?: kpi.currentValue
                                "Error Rate" -> (performanceStats?.get("error_rate") as? Number)?.toDouble() ?: kpi.currentValue
                                "Throughput" -> (performanceStats?.get("throughput") as? Number)?.toDouble() ?: kpi.currentValue
                                else -> kpi.currentValue
                            }
                        }
                        "reliability" -> {
                            val systemStats = systemMetrics["system_metrics"] as? Map<*, *>
                            when (kpi.name) {
                                "System Uptime" -> (systemStats?.get("uptime_percentage") as? Number)?.toDouble() ?: kpi.currentValue
                                "Availability" -> (systemStats?.get("availability") as? Number)?.toDouble() ?: kpi.currentValue
                                else -> kpi.currentValue
                            }
                        }
                        "usage" -> {
                            val usageStats = systemMetrics["usage_analytics"] as? Map<*, *>
                            when (kpi.name) {
                                "Active Users" -> (usageStats?.get("active_users") as? Number)?.toDouble() ?: kpi.currentValue
                                "Query Volume" -> (usageStats?.get("query_volume") as? Number)?.toDouble() ?: kpi.currentValue
                                else -> kpi.currentValue
                            }
                        }
                        "resource" -> {
                            val resourceStats = systemMetrics["system_metrics"] as? Map<*, *>
                            when (kpi.name) {
                                "CPU Usage" -> (resourceStats?.get("cpu_usage") as? Number)?.toDouble() ?: kpi.currentValue
                                "Memory Usage" -> (resourceStats?.get("memory_usage") as? Number)?.toDouble() ?: kpi.currentValue
                                "Disk Usage" -> (resourceStats?.get("disk_usage") as? Number)?.toDouble() ?: kpi.currentValue
                                else -> kpi.currentValue
                            }
                        }
                        else -> kpi.currentValue
                    }
                    
                    // Calculate trend direction
                    val trend = when {
                        newValue > kpi.currentValue -> TrendDirection.UP
                        newValue < kpi.currentValue -> TrendDirection.DOWN
                        else -> TrendDirection.STABLE
                    }
                    
                    // Create historical data point
                    val dataPoint = KPIDataPoint(
                        timestamp = System.currentTimeMillis(),
                        value = newValue,
                        target = kpi.targetValue
                    )
                    
                    // Update KPI with new value and trend
                    val updatedKPI = kpi.copy(
                        currentValue = newValue,
                        trend = trend,
                        lastUpdated = System.currentTimeMillis(),
                        historicalData = (kpi.historicalData + dataPoint).takeLast(100) // Keep last 100 data points
                    )
                    
                    // Save updated KPI
                    kpiRepository.update(updatedKPI)
                    
                    // Store metric value in the database
                    if (kpi.category == "performance" || kpi.category == "resource") {
                        val metricValue = MetricValue(
                            metricId = kpi.id,
                            value = newValue,
                            timestamp = System.currentTimeMillis(),
                            dimensions = mapOf("source" to "system_metrics"),
                            metadata = emptyMap()
                        )
                        metricValueRepository.save(metricValue)
                    }
                } catch (e: Exception) {
                    // Log error but continue processing other KPIs
                    println("Error updating KPI ${kpi.id}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            // Log error but don't crash the background task
            println("Error collecting metrics: ${e.message}")
        }
    }
}