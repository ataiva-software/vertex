package com.ataiva.eden.insight.service

import com.ataiva.eden.insight.engine.AnalyticsEngine
import com.ataiva.eden.insight.model.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf

/**
 * Core business logic service for the Insight Service.
 * Manages analytics queries, reports, dashboards, and KPIs with comprehensive functionality.
 */
class InsightService(
    private val configuration: InsightConfiguration = InsightConfiguration()
) {
    private val analyticsEngine = AnalyticsEngine(configuration)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    
    // In-memory storage (in production, these would be database repositories)
    private val queries = ConcurrentHashMap<String, AnalyticsQuery>()
    private val reports = ConcurrentHashMap<String, Report>()
    private val reportTemplates = ConcurrentHashMap<String, ReportTemplate>()
    private val dashboards = ConcurrentHashMap<String, Dashboard>()
    private val metrics = ConcurrentHashMap<String, Metric>()
    private val kpis = ConcurrentHashMap<String, KPI>()
    private val executions = ConcurrentHashMap<String, QueryExecution>()
    private val reportExecutions = ConcurrentHashMap<String, ReportExecution>()
    
    private val idCounter = AtomicLong(0)
    
    init {
        initializeDefaultData()
        startBackgroundTasks()
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
        
        queries[query.id] = query
        return query
    }
    
    /**
     * Get all queries with optional filtering
     */
    fun getQueries(
        createdBy: String? = null,
        queryType: QueryType? = null,
        tags: List<String> = emptyList(),
        isActive: Boolean? = null
    ): List<AnalyticsQuery> {
        return queries.values.filter { query ->
            (createdBy == null || query.createdBy == createdBy) &&
            (queryType == null || query.queryType == queryType) &&
            (tags.isEmpty() || query.tags.any { it in tags }) &&
            (isActive == null || query.isActive == isActive)
        }.sortedByDescending { it.createdAt }
    }
    
    /**
     * Get query by ID
     */
    fun getQuery(id: String): AnalyticsQuery? = queries[id]
    
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
        val existing = queries[id] ?: return null
        
        val updated = existing.copy(
            name = name ?: existing.name,
            description = description ?: existing.description,
            queryText = queryText ?: existing.queryText,
            parameters = parameters ?: existing.parameters,
            tags = tags ?: existing.tags,
            isActive = isActive ?: existing.isActive,
            lastModified = System.currentTimeMillis()
        )
        
        queries[id] = updated
        return updated
    }
    
    /**
     * Delete a query
     */
    suspend fun deleteQuery(id: String): Boolean {
        return queries.remove(id) != null
    }
    
    /**
     * Execute a query and return results
     */
    suspend fun executeQuery(
        queryId: String,
        parameters: Map<String, String> = emptyMap(),
        executedBy: String = "system"
    ): AnalyticsResult {
        val query = queries[queryId] ?: throw IllegalArgumentException("Query not found: $queryId")
        
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
        val template = reportTemplates[templateId] 
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
        
        reports[report.id] = report
        return report
    }
    
    /**
     * Get all reports with optional filtering
     */
    fun getReports(
        createdBy: String? = null,
        templateId: String? = null,
        isActive: Boolean? = null
    ): List<Report> {
        return reports.values.filter { report ->
            (createdBy == null || report.createdBy == createdBy) &&
            (templateId == null || report.templateId == templateId) &&
            (isActive == null || report.isActive == isActive)
        }.sortedByDescending { it.createdAt }
    }
    
    /**
     * Get report by ID
     */
    fun getReport(id: String): Report? = reports[id]
    
    /**
     * Generate a report
     */
    suspend fun generateReport(request: ReportGenerationRequest, executedBy: String = "system"): ReportGenerationResponse {
        val report = reports[request.reportId] 
            ?: return ReportGenerationResponse(
                success = false,
                error = "Report not found: ${request.reportId}"
            )
        
        val template = reportTemplates[report.templateId]
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
        
        reportExecutions[executionId] = execution
        
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
    fun getReportExecution(executionId: String): ReportExecution? = reportExecutions[executionId]
    
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
        
        reportTemplates[template.id] = template
        return template
    }
    
    /**
     * Get all report templates
     */
    fun getReportTemplates(category: String? = null): List<ReportTemplate> {
        return reportTemplates.values.filter { template ->
            category == null || template.category == category
        }.sortedBy { it.name }
    }
    
    /**
     * Get report template by ID
     */
    fun getReportTemplate(id: String): ReportTemplate? = reportTemplates[id]
    
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
        
        dashboards[dashboard.id] = dashboard
        return dashboard
    }
    
    /**
     * Get all dashboards with optional filtering
     */
    fun getDashboards(
        createdBy: String? = null,
        isPublic: Boolean? = null,
        tags: List<String> = emptyList()
    ): List<Dashboard> {
        return dashboards.values.filter { dashboard ->
            (createdBy == null || dashboard.createdBy == createdBy || dashboard.isPublic) &&
            (isPublic == null || dashboard.isPublic == isPublic) &&
            (tags.isEmpty() || dashboard.tags.any { it in tags })
        }.sortedByDescending { it.createdAt }
    }
    
    /**
     * Get dashboard by ID
     */
    fun getDashboard(id: String): Dashboard? = dashboards[id]
    
    /**
     * Get dashboard data for real-time updates
     */
    suspend fun getDashboardData(request: DashboardDataRequest): DashboardDataResponse {
        val dashboard = dashboards[request.dashboardId]
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
        val existing = dashboards[id] ?: return null
        
        val updated = existing.copy(
            name = name ?: existing.name,
            description = description ?: existing.description,
            widgets = widgets ?: existing.widgets,
            layout = layout ?: existing.layout,
            tags = tags ?: existing.tags,
            lastModified = System.currentTimeMillis()
        )
        
        dashboards[id] = updated
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
        
        metrics[metric.id] = metric
        return metric
    }
    
    /**
     * Get all metrics
     */
    fun getMetrics(category: String? = null, isActive: Boolean? = null): List<Metric> {
        return metrics.values.filter { metric ->
            (category == null || metric.category == category) &&
            (isActive == null || metric.isActive == isActive)
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
        
        kpis[kpi.id] = kpi
        return kpi
    }
    
    /**
     * Get all KPIs
     */
    fun getKPIs(category: String? = null): List<KPI> {
        return kpis.values.filter { kpi ->
            category == null || kpi.category == category
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
    fun getUsageStatistics(): Map<String, Any> {
        return mapOf(
            "total_queries" to queries.size,
            "total_reports" to reports.size,
            "total_dashboards" to dashboards.size,
            "total_metrics" to metrics.size,
            "total_kpis" to kpis.size,
            "active_queries" to queries.values.count { it.isActive },
            "active_reports" to reports.values.count { it.isActive },
            "query_executions_today" to executions.values.count { 
                it.startTime > System.currentTimeMillis() - 86400000 
            },
            "report_generations_today" to reportExecutions.values.count { 
                it.startTime > System.currentTimeMillis() - 86400000 
            }
        )
    }
    
    /**
     * Get performance analytics
     */
    fun getPerformanceAnalytics(): Map<String, Any> {
        val recentExecutions = executions.values.filter { 
            it.endTime != null && it.endTime!! > System.currentTimeMillis() - 3600000 
        }
        
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
            delay(2000) // Simulate report generation time
            val outputPath = generateReportSync(report, template, request)
            updateReportExecution(executionId, ExecutionStatus.COMPLETED, outputPath)
        } catch (e: Exception) {
            updateReportExecution(executionId, ExecutionStatus.FAILED, errorMessage = e.message)
        }
    }
    
    private suspend fun generateReportSync(
        report: Report,
        template: ReportTemplate,
        request: ReportGenerationRequest
    ): String {
        // Simulate report generation
        val outputDir = File(configuration.reportOutputPath)
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        
        val filename = "report_${report.id}_${System.currentTimeMillis()}.${request.format.name.lowercase()}"
        val outputPath = File(outputDir, filename).absolutePath
        
        // Generate report content based on template and parameters
        val reportContent = processReportTemplate(template, request.parameters)
        
        // Write to file (simplified for demo)
        File(outputPath).writeText(reportContent)
        
        return outputPath
    }
    
    private fun processReportTemplate(template: ReportTemplate, parameters: Map<String, String>): String {
        var content = template.templateContent
        
        // Replace parameters in template
        parameters.forEach { (key, value) ->
            content = content.replace("{{$key}}", value)
            content = content.replace("\${$key}", value)
        }
        
        // Add timestamp
        content = content.replace("{{timestamp}}", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        
        return content
    }
    
    private fun updateReportExecution(
        executionId: String,
        status: ExecutionStatus,
        outputPath: String? = null,
        errorMessage: String? = null
    ) {
        reportExecutions[executionId]?.let { execution ->
            reportExecutions[executionId] = execution.copy(
                status = status,
                endTime = System.currentTimeMillis(),
                outputPath = outputPath,
                fileSize = if (outputPath != null) File(outputPath).length() else 0,
                errorMessage = errorMessage
            )
        }
    }
    
    private fun initializeDefaultData() {
        // Create default report templates
        createDefaultReportTemplates()
        
        // Create sample queries
        createSampleQueries()
        
        // Create sample dashboards
        createSampleDashboards()
        
        // Create sample metrics and KPIs
        createSampleMetricsAndKPIs()
    }
    
    private fun createDefaultReportTemplates() {
        val systemOverviewTemplate = ReportTemplate(
            id = "system_overview",
            name = "System Overview Report",
            description = "Comprehensive system overview with key metrics",
            templateContent = """
                # System Overview Report
                Generated: {{timestamp}}
                
                ## Key Metrics
                - Total Users: {{total_users}}
                - Active Workflows: {{active_workflows}}
                - Completed Tasks: {{completed_tasks}}
                - System Uptime: {{system_uptime}}%
                
                ## Performance Summary
                - Average Response Time: {{avg_response_time}}ms
                - Error Rate: {{error_rate}}%
                - Throughput: {{throughput}} req/min
            """.trimIndent(),
            supportedFormats = listOf(ReportFormat.PDF, ReportFormat.HTML),
            category = "system",
            createdBy = "system"
        )
        
        reportTemplates[systemOverviewTemplate.id] = systemOverviewTemplate
    }
    
    private fun createSampleQueries() {
        val userActivityQuery = AnalyticsQuery(
            id = "user_activity",
            name = "User Activity Analysis",
            description = "Analyze user activity patterns",
            queryText = "SELECT * FROM user_activity WHERE created_at > {{start_date}}",
            queryType = QueryType.SELECT,
            createdBy = "system",
            tags = listOf("users", "activity")
        )
        
        queries[userActivityQuery.id] = userActivityQuery
    }
    
    private fun createSampleDashboards() {
        val systemDashboard = Dashboard(
            id = "system_dashboard",
            name = "System Overview Dashboard",
            description = "Real-time system metrics and performance",
            widgets = listOf(
                DashboardWidget(
                    id = "cpu_usage",
                    type = WidgetType.GAUGE,
                    title = "CPU Usage",
                    configuration = WidgetConfiguration(),
                    position = WidgetPosition(0, 0, 6, 4)
                ),
                DashboardWidget(
                    id = "memory_usage",
                    type = WidgetType.GAUGE,
                    title = "Memory Usage",
                    configuration = WidgetConfiguration(),
                    position = WidgetPosition(6, 0, 6, 4)
                )
            ),
            layout = DashboardLayout(),
            permissions = DashboardPermissions(owner = "system", isPublic = true),
            createdBy = "system",
            isPublic = true
        )
        
        dashboards[systemDashboard.id] = systemDashboard
    }
    
    private fun createSampleMetricsAndKPIs() {
        val responseTimeMetric = Metric(
            id = "response_time",
            name = "Average Response Time",
            description = "Average API response time",
            category = "performance",
            unit = "ms",
            aggregationType = AggregationType.AVG,
            queryId = "response_time_query",
            thresholds = listOf(
                MetricThreshold(ThresholdLevel.WARNING, ComparisonOperator.GREATER_THAN, 200.0),
                MetricThreshold(ThresholdLevel.CRITICAL, ComparisonOperator.GREATER_THAN, 500.0)
            )
        )
        
        metrics[responseTimeMetric.id] = responseTimeMetric
        
        val uptimeKPI = KPI(
            id = "system_uptime",
            name = "System Uptime",
            description = "Overall system availability",
            targetValue = 99.9,
            currentValue = 99.8,
            unit = "%",
            trend = TrendDirection.STABLE,
            category = "reliability"
        )
        
        kpis[uptimeKPI.id] = uptimeKPI
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
        reports.values.filter { report ->
            report.isActive && 
            report.schedule != null && 
            report.schedule.enabled &&
            (report.schedule.nextExecution ?: 0) <= now
        }.forEach { report ->
            try {
                generateReport(
                    ReportGenerationRequest(
                        reportId = report.id,
                        parameters = report.parameters,
                        format = report.format,
                        async = true
                    ),
                    "scheduler"
                )
            } catch (e: Exception) {
                // Log error but continue processing other reports
            }
        }
    }
    
    private suspend fun collectMetrics() {
        // Collect system metrics and update KPIs
        val systemMetrics = analyticsEngine.getSystemAnalytics()
        
        // Update KPIs based on current metrics
        kpis.values.forEach { kpi ->
            // Update KPI values based on real metrics
            // This is a simplified example
        }
    }
}