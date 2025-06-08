package com.ataiva.eden.insight.controller

import com.ataiva.eden.insight.model.*
import com.ataiva.eden.insight.service.InsightService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * REST API Controller for the Insight Service.
 * Provides comprehensive endpoints for analytics, reporting, and dashboard functionality.
 */
class InsightController(private val insightService: InsightService) {
    
    fun configureRoutes(routing: Routing) {
        routing {
            route("/api/v1") {
                // Query Management Endpoints
                queryRoutes()
                
                // Report Management Endpoints
                reportRoutes()
                
                // Dashboard Management Endpoints
                dashboardRoutes()
                
                // Analytics Endpoints
                analyticsRoutes()
                
                // Metrics and KPI Endpoints
                metricsRoutes()
                
                // System Information Endpoints
                systemRoutes()
            }
        }
    }
    
    // ============================================================================
    // Query Management Routes
    // ============================================================================
    
    private fun Route.queryRoutes() {
        route("/queries") {
            // Create new query
            post {
                try {
                    val request = call.receive<CreateQueryRequest>()
                    val query = insightService.createQuery(
                        name = request.name,
                        description = request.description,
                        queryText = request.queryText,
                        queryType = request.queryType,
                        parameters = request.parameters,
                        createdBy = request.createdBy,
                        tags = request.tags
                    )
                    call.respond(HttpStatusCode.Created, ApiResponse.success(query))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<AnalyticsQuery>(e.message ?: "Failed to create query"))
                }
            }
            
            // Get all queries with filtering
            get {
                try {
                    val createdBy = call.request.queryParameters["createdBy"]
                    val queryType = call.request.queryParameters["queryType"]?.let { QueryType.valueOf(it) }
                    val tags = call.request.queryParameters.getAll("tags") ?: emptyList()
                    val isActive = call.request.queryParameters["isActive"]?.toBoolean()
                    
                    val queries = insightService.getQueries(createdBy, queryType, tags, isActive)
                    call.respond(HttpStatusCode.OK, ApiResponse.success(queries))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<AnalyticsQuery>>(e.message ?: "Failed to get queries"))
                }
            }
            
            // Get specific query
            get("/{id}") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("Query ID is required")
                    val query = insightService.getQuery(id)
                    
                    if (query != null) {
                        call.respond(HttpStatusCode.OK, ApiResponse.success(query))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ApiResponse.error<AnalyticsQuery>("Query not found"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<AnalyticsQuery>(e.message ?: "Failed to get query"))
                }
            }
            
            // Update query
            put("/{id}") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("Query ID is required")
                    val request = call.receive<UpdateQueryRequest>()
                    
                    val updatedQuery = insightService.updateQuery(
                        id = id,
                        name = request.name,
                        description = request.description,
                        queryText = request.queryText,
                        parameters = request.parameters,
                        tags = request.tags,
                        isActive = request.isActive
                    )
                    
                    if (updatedQuery != null) {
                        call.respond(HttpStatusCode.OK, ApiResponse.success(updatedQuery))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ApiResponse.error<AnalyticsQuery>("Query not found"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<AnalyticsQuery>(e.message ?: "Failed to update query"))
                }
            }
            
            // Delete query
            delete("/{id}") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("Query ID is required")
                    val deleted = insightService.deleteQuery(id)
                    
                    if (deleted) {
                        call.respond(HttpStatusCode.OK, ApiResponse.success(mapOf("deleted" to true)))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ApiResponse.error<Map<String, Boolean>>("Query not found"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Map<String, Boolean>>(e.message ?: "Failed to delete query"))
                }
            }
            
            // Execute query
            post("/{id}/execute") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("Query ID is required")
                    val request = call.receive<ExecuteQueryRequest>()
                    
                    val result = insightService.executeQuery(
                        queryId = id,
                        parameters = request.parameters,
                        executedBy = request.executedBy
                    )
                    
                    call.respond(HttpStatusCode.OK, ApiResponse.success(result))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<AnalyticsResult>(e.message ?: "Failed to execute query"))
                }
            }
            
            // Execute raw query
            post("/execute") {
                try {
                    val request = call.receive<QueryRequest>()
                    val executedBy = call.request.headers["X-User-ID"] ?: "anonymous"
                    
                    val response = insightService.executeRawQuery(request, executedBy)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, QueryResponse(
                        success = false,
                        error = e.message ?: "Failed to execute query"
                    ))
                }
            }
        }
    }
    
    // ============================================================================
    // Report Management Routes
    // ============================================================================
    
    private fun Route.reportRoutes() {
        route("/reports") {
            // Create new report
            post {
                try {
                    val request = call.receive<CreateReportRequest>()
                    val report = insightService.createReport(
                        name = request.name,
                        description = request.description,
                        templateId = request.templateId,
                        parameters = request.parameters,
                        schedule = request.schedule,
                        recipients = request.recipients,
                        format = request.format,
                        createdBy = request.createdBy
                    )
                    call.respond(HttpStatusCode.Created, ApiResponse.success(report))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Report>(e.message ?: "Failed to create report"))
                }
            }
            
            // Get all reports
            get {
                try {
                    val createdBy = call.request.queryParameters["createdBy"]
                    val templateId = call.request.queryParameters["templateId"]
                    val isActive = call.request.queryParameters["isActive"]?.toBoolean()
                    
                    val reports = insightService.getReports(createdBy, templateId, isActive)
                    call.respond(HttpStatusCode.OK, ApiResponse.success(reports))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<Report>>(e.message ?: "Failed to get reports"))
                }
            }
            
            // Get specific report
            get("/{id}") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("Report ID is required")
                    val report = insightService.getReport(id)
                    
                    if (report != null) {
                        call.respond(HttpStatusCode.OK, ApiResponse.success(report))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ApiResponse.error<Report>("Report not found"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Report>(e.message ?: "Failed to get report"))
                }
            }
            
            // Generate report
            post("/{id}/generate") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("Report ID is required")
                    val request = call.receiveNullable<ReportGenerationRequest>() ?: ReportGenerationRequest(reportId = id)
                    val executedBy = call.request.headers["X-User-ID"] ?: "anonymous"
                    
                    val response = insightService.generateReport(request.copy(reportId = id), executedBy)
                    
                    val statusCode = if (response.success) {
                        if (request.async) HttpStatusCode.Accepted else HttpStatusCode.OK
                    } else {
                        HttpStatusCode.BadRequest
                    }
                    
                    call.respond(statusCode, response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ReportGenerationResponse(
                        success = false,
                        error = e.message ?: "Failed to generate report"
                    ))
                }
            }
            
            // Get report execution status
            get("/executions/{executionId}") {
                try {
                    val executionId = call.parameters["executionId"] ?: throw IllegalArgumentException("Execution ID is required")
                    val execution = insightService.getReportExecution(executionId)
                    
                    if (execution != null) {
                        call.respond(HttpStatusCode.OK, ApiResponse.success(execution))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ApiResponse.error<ReportExecution>("Report execution not found"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ReportExecution>(e.message ?: "Failed to get report execution"))
                }
            }
        }
        
        // Report Templates
        route("/report-templates") {
            // Create new template
            post {
                try {
                    val request = call.receive<CreateReportTemplateRequest>()
                    val template = insightService.createReportTemplate(
                        name = request.name,
                        description = request.description,
                        templateContent = request.templateContent,
                        requiredParameters = request.requiredParameters,
                        supportedFormats = request.supportedFormats,
                        category = request.category,
                        createdBy = request.createdBy
                    )
                    call.respond(HttpStatusCode.Created, ApiResponse.success(template))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ReportTemplate>(e.message ?: "Failed to create template"))
                }
            }
            
            // Get all templates
            get {
                try {
                    val category = call.request.queryParameters["category"]
                    val templates = insightService.getReportTemplates(category)
                    call.respond(HttpStatusCode.OK, ApiResponse.success(templates))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<ReportTemplate>>(e.message ?: "Failed to get templates"))
                }
            }
            
            // Get specific template
            get("/{id}") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("Template ID is required")
                    val template = insightService.getReportTemplate(id)
                    
                    if (template != null) {
                        call.respond(HttpStatusCode.OK, ApiResponse.success(template))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ApiResponse.error<ReportTemplate>("Template not found"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ReportTemplate>(e.message ?: "Failed to get template"))
                }
            }
        }
    }
    
    // ============================================================================
    // Dashboard Management Routes
    // ============================================================================
    
    private fun Route.dashboardRoutes() {
        route("/dashboards") {
            // Create new dashboard
            post {
                try {
                    val request = call.receive<CreateDashboardRequest>()
                    val dashboard = insightService.createDashboard(
                        name = request.name,
                        description = request.description,
                        widgets = request.widgets,
                        layout = request.layout,
                        permissions = request.permissions,
                        createdBy = request.createdBy,
                        isPublic = request.isPublic,
                        tags = request.tags
                    )
                    call.respond(HttpStatusCode.Created, ApiResponse.success(dashboard))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Dashboard>(e.message ?: "Failed to create dashboard"))
                }
            }
            
            // Get all dashboards
            get {
                try {
                    val createdBy = call.request.queryParameters["createdBy"]
                    val isPublic = call.request.queryParameters["isPublic"]?.toBoolean()
                    val tags = call.request.queryParameters.getAll("tags") ?: emptyList()
                    
                    val dashboards = insightService.getDashboards(createdBy, isPublic, tags)
                    call.respond(HttpStatusCode.OK, ApiResponse.success(dashboards))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<Dashboard>>(e.message ?: "Failed to get dashboards"))
                }
            }
            
            // Get specific dashboard
            get("/{id}") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("Dashboard ID is required")
                    val dashboard = insightService.getDashboard(id)
                    
                    if (dashboard != null) {
                        call.respond(HttpStatusCode.OK, ApiResponse.success(dashboard))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ApiResponse.error<Dashboard>("Dashboard not found"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Dashboard>(e.message ?: "Failed to get dashboard"))
                }
            }
            
            // Get dashboard data
            get("/{id}/data") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("Dashboard ID is required")
                    val timeRange = call.request.queryParameters["timeRange"]?.let { 
                        // Parse time range if provided
                        null // Simplified for now
                    }
                    val filters = call.request.queryParameters.toMap().filterKeys { it.startsWith("filter_") }
                    
                    val request = DashboardDataRequest(
                        dashboardId = id,
                        timeRange = timeRange,
                        filters = filters
                    )
                    
                    val response = insightService.getDashboardData(request)
                    call.respond(HttpStatusCode.OK, ApiResponse.success(response))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<DashboardDataResponse>(e.message ?: "Failed to get dashboard data"))
                }
            }
            
            // Update dashboard
            put("/{id}") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("Dashboard ID is required")
                    val request = call.receive<UpdateDashboardRequest>()
                    
                    val updatedDashboard = insightService.updateDashboard(
                        id = id,
                        name = request.name,
                        description = request.description,
                        widgets = request.widgets,
                        layout = request.layout,
                        tags = request.tags
                    )
                    
                    if (updatedDashboard != null) {
                        call.respond(HttpStatusCode.OK, ApiResponse.success(updatedDashboard))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ApiResponse.error<Dashboard>("Dashboard not found"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Dashboard>(e.message ?: "Failed to update dashboard"))
                }
            }
        }
    }
    
    // ============================================================================
    // Analytics Routes
    // ============================================================================
    
    private fun Route.analyticsRoutes() {
        route("/analytics") {
            // System analytics overview
            get("/overview") {
                try {
                    val analytics = insightService.getSystemAnalytics()
                    call.respond(HttpStatusCode.OK, ApiResponse.success(analytics))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Map<String, Any>>(e.message ?: "Failed to get system analytics"))
                }
            }
            
            // Usage statistics
            get("/usage") {
                try {
                    val usage = insightService.getUsageStatistics()
                    call.respond(HttpStatusCode.OK, ApiResponse.success(usage))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Map<String, Any>>(e.message ?: "Failed to get usage statistics"))
                }
            }
            
            // Performance analytics
            get("/performance") {
                try {
                    val performance = insightService.getPerformanceAnalytics()
                    call.respond(HttpStatusCode.OK, ApiResponse.success(performance))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Map<String, Any>>(e.message ?: "Failed to get performance analytics"))
                }
            }
            
            // Performance trends analysis
            get("/trends") {
                try {
                    val startTime = call.request.queryParameters["startTime"]?.toLongOrNull()
                    val endTime = call.request.queryParameters["endTime"]?.toLongOrNull()
                    
                    val trends = insightService.analyzePerformanceTrends(
                        startTime ?: (System.currentTimeMillis() - 7 * 86400000),
                        endTime ?: System.currentTimeMillis()
                    )
                    
                    call.respond(HttpStatusCode.OK, ApiResponse.success(trends))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Map<String, Any>>(e.message ?: "Failed to analyze performance trends"))
                }
            }
            
            // Anomaly detection
            post("/anomalies") {
                try {
                    val request = call.receive<AnomalyDetectionRequest>()
                    val anomalies = insightService.detectAnomalies(request.metricIds)
                    call.respond(HttpStatusCode.OK, ApiResponse.success(anomalies))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<Map<String, Any>>>(e.message ?: "Failed to detect anomalies"))
                }
            }
            
            // Insights generation
            get("/insights") {
                try {
                    val startTime = call.request.queryParameters["startTime"]?.toLongOrNull()
                    val endTime = call.request.queryParameters["endTime"]?.toLongOrNull()
                    val service = call.request.queryParameters["service"] ?: "all"
                    val environment = call.request.queryParameters["environment"] ?: "production"
                    
                    val context = mapOf(
                        "start_time" to (startTime ?: (System.currentTimeMillis() - 7 * 86400000)),
                        "end_time" to (endTime ?: System.currentTimeMillis()),
                        "dimensions" to mapOf("service" to service, "environment" to environment)
                    )
                    
                    val insights = insightService.generateInsights(context)
                    call.respond(HttpStatusCode.OK, ApiResponse.success(insights))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<Map<String, Any>>>(e.message ?: "Failed to generate insights"))
                }
            }
            
            // Resource usage prediction
            get("/predict/resources") {
                try {
                    val horizonHours = call.request.queryParameters["horizonHours"]?.toIntOrNull() ?: 24
                    val prediction = insightService.predictResourceUsage(horizonHours)
                    call.respond(HttpStatusCode.OK, ApiResponse.success(prediction))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Map<String, Any>>(e.message ?: "Failed to predict resource usage"))
                }
            }
            
            // Metric trend analysis
            get("/metrics/{id}/trend") {
                try {
                    val metricId = call.parameters["id"] ?: throw IllegalArgumentException("Metric ID is required")
                    val startTime = call.request.queryParameters["startTime"]?.toLongOrNull()
                    val endTime = call.request.queryParameters["endTime"]?.toLongOrNull()
                    
                    val trend = insightService.getMetricTrend(
                        metricId,
                        startTime ?: (System.currentTimeMillis() - 7 * 86400000),
                        endTime ?: System.currentTimeMillis()
                    )
                    
                    call.respond(HttpStatusCode.OK, ApiResponse.success(trend))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Map<String, Any>>(e.message ?: "Failed to get metric trend"))
                }
            }
        }
    }
    
    // ============================================================================
    // Metrics and KPI Routes
    // ============================================================================
    
    private fun Route.metricsRoutes() {
        route("/metrics") {
            // Create new metric
            post {
                try {
                    val request = call.receive<CreateMetricRequest>()
                    val metric = insightService.createMetric(
                        name = request.name,
                        description = request.description,
                        category = request.category,
                        unit = request.unit,
                        aggregationType = request.aggregationType,
                        queryId = request.queryId,
                        thresholds = request.thresholds
                    )
                    call.respond(HttpStatusCode.Created, ApiResponse.success(metric))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Metric>(e.message ?: "Failed to create metric"))
                }
            }
            
            // Get all metrics
            get {
                try {
                    val category = call.request.queryParameters["category"]
                    val isActive = call.request.queryParameters["isActive"]?.toBoolean()
                    
                    val metrics = insightService.getMetrics(category, isActive)
                    call.respond(HttpStatusCode.OK, ApiResponse.success(metrics))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<Metric>>(e.message ?: "Failed to get metrics"))
                }
            }
        }
        
        route("/kpis") {
            // Create new KPI
            post {
                try {
                    val request = call.receive<CreateKPIRequest>()
                    val kpi = insightService.createKPI(
                        name = request.name,
                        description = request.description,
                        targetValue = request.targetValue,
                        currentValue = request.currentValue,
                        unit = request.unit,
                        category = request.category,
                        historicalData = request.historicalData
                    )
                    call.respond(HttpStatusCode.Created, ApiResponse.success(kpi))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse.error<KPI>(e.message ?: "Failed to create KPI"))
                }
            }
            
            // Get all KPIs
            get {
                try {
                    val category = call.request.queryParameters["category"]
                    val kpis = insightService.getKPIs(category)
                    call.respond(HttpStatusCode.OK, ApiResponse.success(kpis))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<KPI>>(e.message ?: "Failed to get KPIs"))
                }
            }
        }
    }
    
    // ============================================================================
    // System Information Routes
    // ============================================================================
    
    private fun Route.systemRoutes() {
        // Service information
        get("/info") {
            call.respond(HttpStatusCode.OK, ServiceInfo(
                name = "Eden Insight Service",
                version = "1.0.0",
                description = "Analytics and business intelligence service with real functionality",
                status = "running"
            ))
        }
        
        // Health check
        get("/health") {
            call.respond(HttpStatusCode.OK, HealthCheck(
                status = "healthy",
                timestamp = System.currentTimeMillis(),
                uptime = System.currentTimeMillis() - startTime,
                service = "insight"
            ))
        }
    }
}

// ============================================================================
// Request/Response DTOs
// ============================================================================

@Serializable
data class CreateQueryRequest(
    val name: String,
    val description: String? = null,
    val queryText: String,
    val queryType: QueryType,
    val parameters: Map<String, String> = emptyMap(),
    val createdBy: String,
    val tags: List<String> = emptyList()
)

@Serializable
data class UpdateQueryRequest(
    val name: String? = null,
    val description: String? = null,
    val queryText: String? = null,
    val parameters: Map<String, String>? = null,
    val tags: List<String>? = null,
    val isActive: Boolean? = null
)

@Serializable
data class ExecuteQueryRequest(
    val parameters: Map<String, String> = emptyMap(),
    val executedBy: String = "system"
)

@Serializable
data class CreateReportRequest(
    val name: String,
    val description: String? = null,
    val templateId: String,
    val parameters: Map<String, String> = emptyMap(),
    val schedule: ReportSchedule? = null,
    val recipients: List<String> = emptyList(),
    val format: ReportFormat = ReportFormat.PDF,
    val createdBy: String
)

@Serializable
data class CreateReportTemplateRequest(
    val name: String,
    val description: String? = null,
    val templateContent: String,
    val requiredParameters: List<ParameterDefinition> = emptyList(),
    val supportedFormats: List<ReportFormat> = listOf(ReportFormat.PDF),
    val category: String = "general",
    val createdBy: String
)

@Serializable
data class CreateDashboardRequest(
    val name: String,
    val description: String? = null,
    val widgets: List<DashboardWidget>,
    val layout: DashboardLayout = DashboardLayout(),
    val permissions: DashboardPermissions,
    val createdBy: String,
    val isPublic: Boolean = false,
    val tags: List<String> = emptyList()
)

@Serializable
data class UpdateDashboardRequest(
    val name: String? = null,
    val description: String? = null,
    val widgets: List<DashboardWidget>? = null,
    val layout: DashboardLayout? = null,
    val tags: List<String>? = null
)

@Serializable
data class CreateMetricRequest(
    val name: String,
    val description: String? = null,
    val category: String,
    val unit: String? = null,
    val aggregationType: AggregationType,
    val queryId: String,
    val thresholds: List<MetricThreshold> = emptyList()
)

@Serializable
data class CreateKPIRequest(
    val name: String,
    val description: String? = null,
    val targetValue: Double,
    val currentValue: Double,
    val unit: String? = null,
    val category: String,
    val historicalData: List<KPIDataPoint> = emptyList()
)

@Serializable
data class AnomalyDetectionRequest(
    val metricIds: List<String>,
    val sensitivity: Double = 1.0,
    val startTime: Long? = null,
    val endTime: Long? = null
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun <T> success(data: T, message: String? = null) = ApiResponse(
            success = true,
            data = data,
            message = message
        )
        
        fun <T> error(error: String, data: T? = null) = ApiResponse(
            success = false,
            data = data,
            error = error
        )
    }
}

private val startTime = System.currentTimeMillis()