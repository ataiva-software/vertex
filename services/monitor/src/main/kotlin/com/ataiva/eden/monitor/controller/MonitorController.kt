package com.ataiva.eden.monitor.controller

import com.ataiva.eden.monitor.model.*
import com.ataiva.eden.monitor.service.MonitorService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Monitor Controller - REST API endpoints for monitoring and alerting
 * Provides comprehensive monitoring capabilities through HTTP endpoints
 */
fun Application.configureMonitorRouting() {
    val monitorService = MonitorService()
    
    routing {
        route("/api/v1") {
            
            // System metrics endpoints
            route("/metrics") {
                get {
                    try {
                        val metrics = withTimeout(10000) {
                            monitorService.getSystemMetrics()
                        }
                        call.respond(HttpStatusCode.OK, SystemMetricsResponse(metrics))
                    } catch (e: TimeoutCancellationException) {
                        call.respond(HttpStatusCode.RequestTimeout, MonitorErrorResponse(
                            error = "Timeout",
                            message = "Metrics collection timed out"
                        ))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, MonitorErrorResponse(
                            error = "Internal Server Error",
                            message = e.message ?: "Unknown error occurred"
                        ))
                    }
                }
                
                get("/system") {
                    try {
                        val metrics = monitorService.getSystemMetrics()
                        call.respond(HttpStatusCode.OK, SystemMetricsResponse(metrics))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, MonitorErrorResponse(
                            error = "Internal Server Error",
                            message = e.message ?: "Failed to collect system metrics"
                        ))
                    }
                }
                
                get("/services") {
                    try {
                        val serviceMetrics = monitorService.getServiceMetrics()
                        val healthyCount = serviceMetrics.count { it.status == "healthy" }
                        val overallStatus = if (healthyCount == serviceMetrics.size) "healthy" else "degraded"
                        
                        call.respond(HttpStatusCode.OK, ServiceMetricsResponse(
                            services = serviceMetrics,
                            healthyCount = healthyCount,
                            totalCount = serviceMetrics.size,
                            overallStatus = overallStatus,
                            timestamp = System.currentTimeMillis()
                        ))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, MonitorErrorResponse(
                            error = "Internal Server Error",
                            message = e.message ?: "Failed to collect service metrics"
                        ))
                    }
                }
                
                get("/historical/{metricName}") {
                    try {
                        val metricName = call.parameters["metricName"] ?: run {
                            call.respond(HttpStatusCode.BadRequest, MonitorErrorResponse(
                                error = "Bad Request",
                                message = "Metric name is required"
                            ))
                            return@get
                        }
                        
                        val hours = call.request.queryParameters["hours"]?.toIntOrNull() ?: 24
                        
                        if (hours < 1 || hours > 168) { // Max 1 week
                            call.respond(HttpStatusCode.BadRequest, MonitorErrorResponse(
                                error = "Bad Request",
                                message = "Hours must be between 1 and 168"
                            ))
                            return@get
                        }
                        
                        val historicalData = monitorService.getHistoricalMetrics(metricName, hours)
                        
                        val summary = if (historicalData.isNotEmpty()) {
                            val values = historicalData.map { it.value }
                            MetricSummary(
                                min = values.minOrNull() ?: 0.0,
                                max = values.maxOrNull() ?: 0.0,
                                average = values.average(),
                                count = values.size
                            )
                        } else {
                            MetricSummary(0.0, 0.0, 0.0, 0)
                        }
                        
                        call.respond(HttpStatusCode.OK, HistoricalMetricsResponse(
                            metricName = metricName,
                            dataPoints = historicalData,
                            summary = summary
                        ))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, MonitorErrorResponse(
                            error = "Internal Server Error",
                            message = e.message ?: "Failed to retrieve historical metrics"
                        ))
                    }
                }
            }
            
            // Alert management endpoints
            route("/alerts") {
                get {
                    try {
                        val alertRules = monitorService.getAlertRules()
                        val enabledCount = alertRules.count { it.enabled }
                        
                        call.respond(HttpStatusCode.OK, AlertRulesResponse(
                            rules = alertRules,
                            totalCount = alertRules.size,
                            enabledCount = enabledCount
                        ))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, MonitorErrorResponse(
                            error = "Internal Server Error",
                            message = e.message ?: "Failed to retrieve alert rules"
                        ))
                    }
                }
                
                post {
                    try {
                        val request = call.receive<CreateAlertRequest>()
                        
                        // Validate request
                        val validationErrors = validateCreateAlertRequest(request)
                        if (validationErrors.isNotEmpty()) {
                            call.respond(HttpStatusCode.BadRequest, ValidationErrorResponse(
                                errors = validationErrors
                            ))
                            return@post
                        }
                        
                        val alertRule = monitorService.createAlertRule(request)
                        call.respond(HttpStatusCode.Created, CreateAlertResponse(alertRule))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, MonitorErrorResponse(
                            error = "Internal Server Error",
                            message = e.message ?: "Failed to create alert rule"
                        ))
                    }
                }
                
                get("/active") {
                    try {
                        val activeAlerts = monitorService.getActiveAlerts()
                        val acknowledgedCount = activeAlerts.count { it.acknowledged }
                        val criticalCount = activeAlerts.count { it.severity == "critical" }
                        
                        call.respond(HttpStatusCode.OK, ActiveAlertsResponse(
                            alerts = activeAlerts,
                            totalCount = activeAlerts.size,
                            acknowledgedCount = acknowledgedCount,
                            criticalCount = criticalCount
                        ))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, MonitorErrorResponse(
                            error = "Internal Server Error",
                            message = e.message ?: "Failed to retrieve active alerts"
                        ))
                    }
                }
                
                post("/{alertId}/acknowledge") {
                    try {
                        val alertId = call.parameters["alertId"] ?: run {
                            call.respond(HttpStatusCode.BadRequest, MonitorErrorResponse(
                                error = "Bad Request",
                                message = "Alert ID is required"
                            ))
                            return@post
                        }
                        
                        val request = call.receive<AcknowledgeAlertRequest>()
                        
                        if (request.acknowledgedBy.isBlank()) {
                            call.respond(HttpStatusCode.BadRequest, MonitorErrorResponse(
                                error = "Bad Request",
                                message = "acknowledgedBy field is required"
                            ))
                            return@post
                        }
                        
                        val success = monitorService.acknowledgeAlert(alertId, request.acknowledgedBy)
                        
                        if (success) {
                            call.respond(HttpStatusCode.OK, AcknowledgeAlertResponse(
                                alertId = alertId,
                                acknowledged = true,
                                acknowledgedBy = request.acknowledgedBy,
                                acknowledgedAt = System.currentTimeMillis()
                            ))
                        } else {
                            call.respond(HttpStatusCode.NotFound, MonitorErrorResponse(
                                error = "Not Found",
                                message = "Alert not found or already resolved"
                            ))
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, MonitorErrorResponse(
                            error = "Internal Server Error",
                            message = e.message ?: "Failed to acknowledge alert"
                        ))
                    }
                }
            }
            
            // Dashboard endpoints
            route("/dashboards") {
                get {
                    try {
                        val dashboards = monitorService.getAllDashboards()
                        call.respond(HttpStatusCode.OK, DashboardsResponse(
                            dashboards = dashboards,
                            totalCount = dashboards.size
                        ))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, MonitorErrorResponse(
                            error = "Internal Server Error",
                            message = e.message ?: "Failed to retrieve dashboards"
                        ))
                    }
                }
                
                get("/{dashboardId}") {
                    try {
                        val dashboardId = call.parameters["dashboardId"] ?: run {
                            call.respond(HttpStatusCode.BadRequest, MonitorErrorResponse(
                                error = "Bad Request",
                                message = "Dashboard ID is required"
                            ))
                            return@get
                        }
                        
                        val dashboard = monitorService.getDashboard(dashboardId)
                        
                        if (dashboard != null) {
                            call.respond(HttpStatusCode.OK, dashboard)
                        } else {
                            call.respond(HttpStatusCode.NotFound, MonitorErrorResponse(
                                error = "Not Found",
                                message = "Dashboard not found"
                            ))
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, MonitorErrorResponse(
                            error = "Internal Server Error",
                            message = e.message ?: "Failed to retrieve dashboard"
                        ))
                    }
                }
            }
            
            // Log search endpoints
            route("/logs") {
                get("/search") {
                    try {
                        val query = call.request.queryParameters["q"] ?: ""
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                        
                        if (limit < 1 || limit > 1000) {
                            call.respond(HttpStatusCode.BadRequest, MonitorErrorResponse(
                                error = "Bad Request",
                                message = "Limit must be between 1 and 1000"
                            ))
                            return@get
                        }
                        
                        val startTime = System.currentTimeMillis()
                        val logs = monitorService.searchLogs(query, limit)
                        val executionTime = System.currentTimeMillis() - startTime
                        
                        call.respond(HttpStatusCode.OK, LogSearchResponse(
                            logs = logs,
                            totalCount = logs.size,
                            query = query,
                            executionTime = executionTime
                        ))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, MonitorErrorResponse(
                            error = "Internal Server Error",
                            message = e.message ?: "Failed to search logs"
                        ))
                    }
                }
            }
            
            // Statistics endpoint
            get("/stats") {
                try {
                    val stats = monitorService.getMonitoringStats()
                    
                    val systemHealth = when {
                        stats.activeAlertsCount == 0 -> "excellent"
                        stats.activeAlertsCount < 5 -> "good"
                        stats.activeAlertsCount < 10 -> "fair"
                        else -> "poor"
                    }
                    
                    val recommendations = mutableListOf<String>()
                    if (stats.activeAlertsCount > 0) {
                        recommendations.add("Review and acknowledge ${stats.activeAlertsCount} active alerts")
                    }
                    if (stats.alertRulesCount == 0) {
                        recommendations.add("Consider creating alert rules for proactive monitoring")
                    }
                    if (stats.uptimeSeconds < 3600) {
                        recommendations.add("System recently restarted - monitor for stability")
                    }
                    
                    call.respond(HttpStatusCode.OK, MonitoringStatsResponse(
                        stats = stats,
                        systemHealth = systemHealth,
                        recommendations = recommendations
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, MonitorErrorResponse(
                        error = "Internal Server Error",
                        message = e.message ?: "Failed to retrieve monitoring statistics"
                    ))
                }
            }
        }
    }
}

private fun validateCreateAlertRequest(request: CreateAlertRequest): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    
    if (request.name.isBlank()) {
        errors.add(ValidationError("name", "Name is required"))
    }
    
    if (request.name.length > 100) {
        errors.add(ValidationError("name", "Name must be 100 characters or less"))
    }
    
    if (request.description.length > 500) {
        errors.add(ValidationError("description", "Description must be 500 characters or less"))
    }
    
    if (request.metricName.isBlank()) {
        errors.add(ValidationError("metricName", "Metric name is required"))
    }
    
    val validConditions = setOf("greater_than", "less_than", "equals")
    if (!validConditions.contains(request.condition)) {
        errors.add(ValidationError("condition", "Condition must be one of: ${validConditions.joinToString(", ")}"))
    }
    
    if (request.threshold < 0) {
        errors.add(ValidationError("threshold", "Threshold must be non-negative"))
    }
    
    val validSeverities = setOf("low", "medium", "high", "critical")
    if (!validSeverities.contains(request.severity)) {
        errors.add(ValidationError("severity", "Severity must be one of: ${validSeverities.joinToString(", ")}"))
    }
    
    return errors
}