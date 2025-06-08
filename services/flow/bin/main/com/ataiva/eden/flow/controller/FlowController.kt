package com.ataiva.eden.flow.controller

import com.ataiva.eden.flow.service.FlowService
import com.ataiva.eden.flow.service.FlowResult
import com.ataiva.eden.flow.model.*
import com.ataiva.eden.flow.engine.WorkflowEngine
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.datetime.Clock

/**
 * REST API controller for Eden Flow Service
 */
class FlowController(
    private val flowService: FlowService,
    private val workflowEngine: WorkflowEngine
) {
    
    fun Route.flowRoutes() {
        route("/api/v1") {
            workflowRoutes()
            executionRoutes()
            templateRoutes()
            validationRoutes()
            bulkRoutes()
            searchRoutes()
            exportImportRoutes()
        }
    }
    
    private fun Route.workflowRoutes() {
        route("/workflows") {
            // Create workflow
            post {
                try {
                    val request = call.receive<CreateWorkflowRequest>()
                    
                    when (val result = flowService.createWorkflow(request)) {
                        is FlowResult.Success -> {
                            call.respond(HttpStatusCode.Created, ApiResponse.success(result.data))
                        }
                        is FlowResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WorkflowResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<WorkflowResponse>("Internal server error"))
                }
            }
            
            // List workflows
            get {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<WorkflowResponse>>("userId is required"))
                    
                    val request = ListWorkflowsRequest(
                        userId = userId,
                        status = call.request.queryParameters["status"],
                        namePattern = call.request.queryParameters["namePattern"],
                        includeArchived = call.request.queryParameters["includeArchived"]?.toBoolean() ?: false
                    )
                    
                    when (val result = flowService.listWorkflows(request)) {
                        is FlowResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is FlowResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<WorkflowResponse>>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<WorkflowResponse>>("Internal server error"))
                }
            }
            
            // Get specific workflow
            get("/{id}") {
                try {
                    val workflowId = call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WorkflowResponse>("Workflow ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WorkflowResponse>("userId is required"))
                    
                    when (val result = flowService.getWorkflow(workflowId, userId)) {
                        is FlowResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is FlowResult.Error -> {
                            call.respond(HttpStatusCode.NotFound, ApiResponse.error<WorkflowResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<WorkflowResponse>("Internal server error"))
                }
            }
            
            // Update workflow
            put("/{id}") {
                try {
                    val workflowId = call.parameters["id"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WorkflowResponse>("Workflow ID is required"))
                    
                    val updateData = call.receive<UpdateWorkflowRequest>()
                    val request = updateData.copy(workflowId = workflowId)
                    
                    when (val result = flowService.updateWorkflow(request)) {
                        is FlowResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is FlowResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WorkflowResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<WorkflowResponse>("Internal server error"))
                }
            }
            
            // Delete workflow
            delete("/{id}") {
                try {
                    val workflowId = call.parameters["id"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("Workflow ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("userId is required"))
                    
                    when (val result = flowService.deleteWorkflow(workflowId, userId)) {
                        is FlowResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(Unit))
                        }
                        is FlowResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Unit>("Internal server error"))
                }
            }
            
            // Execute workflow
            post("/{id}/execute") {
                try {
                    val workflowId = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExecutionResponse>("Workflow ID is required"))
                    
                    val executeData = call.receive<ExecuteWorkflowRequest>()
                    val request = executeData.copy(workflowId = workflowId)
                    
                    when (val result = flowService.executeWorkflow(request)) {
                        is FlowResult.Success -> {
                            call.respond(HttpStatusCode.Accepted, ApiResponse.success(result.data))
                        }
                        is FlowResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExecutionResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<ExecutionResponse>("Internal server error"))
                }
            }
        }
    }
    
    private fun Route.executionRoutes() {
        route("/executions") {
            // List executions
            get {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<ExecutionResponse>>("userId is required"))
                    
                    val request = ListExecutionsRequest(
                        userId = userId,
                        workflowId = call.request.queryParameters["workflowId"],
                        status = call.request.queryParameters["status"],
                        limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    )
                    
                    when (val result = flowService.listExecutions(request)) {
                        is FlowResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is FlowResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<ExecutionResponse>>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<ExecutionResponse>>("Internal server error"))
                }
            }
            
            // Get specific execution
            get("/{id}") {
                try {
                    val executionId = call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExecutionResponse>("Execution ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExecutionResponse>("userId is required"))
                    
                    when (val result = flowService.getExecution(executionId, userId)) {
                        is FlowResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is FlowResult.Error -> {
                            call.respond(HttpStatusCode.NotFound, ApiResponse.error<ExecutionResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<ExecutionResponse>("Internal server error"))
                }
            }
            
            // Cancel execution
            post("/{id}/cancel") {
                try {
                    val executionId = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("Execution ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("userId is required"))
                    
                    when (val result = flowService.cancelExecution(executionId, userId)) {
                        is FlowResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(Unit))
                        }
                        is FlowResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Unit>("Internal server error"))
                }
            }
            
            // Get execution steps
            get("/{id}/steps") {
                try {
                    val executionId = call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<StepResponse>>("Execution ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<StepResponse>>("userId is required"))
                    
                    when (val result = flowService.getExecutionSteps(executionId, userId)) {
                        is FlowResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is FlowResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<StepResponse>>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<StepResponse>>("Internal server error"))
                }
            }
        }
    }
    
    private fun Route.templateRoutes() {
        route("/templates") {
            get {
                // Return predefined workflow templates
                val templates = listOf(
                    WorkflowTemplateResponse(
                        id = "ci-cd-pipeline",
                        name = "CI/CD Pipeline",
                        description = "Continuous integration and deployment pipeline",
                        category = "DevOps",
                        definition = mapOf(
                            "steps" to listOf(
                                mapOf(
                                    "name" to "checkout",
                                    "type" to "shell_command",
                                    "config" to mapOf("command" to "git checkout main")
                                ),
                                mapOf(
                                    "name" to "build",
                                    "type" to "shell_command",
                                    "config" to mapOf("command" to "npm run build")
                                ),
                                mapOf(
                                    "name" to "test",
                                    "type" to "shell_command",
                                    "config" to mapOf("command" to "npm test")
                                ),
                                mapOf(
                                    "name" to "deploy",
                                    "type" to "shell_command",
                                    "config" to mapOf("command" to "npm run deploy")
                                )
                            )
                        ),
                        parameters = listOf(
                            TemplateParameter("repository_url", "string", "Git repository URL"),
                            TemplateParameter("branch", "string", "Git branch", false, "main"),
                            TemplateParameter("environment", "string", "Deployment environment", true, null, listOf("dev", "staging", "prod"))
                        ),
                        tags = listOf("ci-cd", "deployment", "automation")
                    ),
                    WorkflowTemplateResponse(
                        id = "backup-workflow",
                        name = "Database Backup",
                        description = "Automated database backup workflow",
                        category = "Maintenance",
                        definition = mapOf(
                            "steps" to listOf(
                                mapOf(
                                    "name" to "create_backup",
                                    "type" to "sql_query",
                                    "config" to mapOf("query" to "BACKUP DATABASE")
                                ),
                                mapOf(
                                    "name" to "upload_backup",
                                    "type" to "file_operation",
                                    "config" to mapOf("operation" to "upload")
                                ),
                                mapOf(
                                    "name" to "notify_completion",
                                    "type" to "email_notification",
                                    "config" to mapOf("subject" to "Backup Completed")
                                )
                            )
                        ),
                        parameters = listOf(
                            TemplateParameter("database_name", "string", "Database name"),
                            TemplateParameter("backup_location", "string", "Backup storage location"),
                            TemplateParameter("notification_email", "string", "Email for notifications")
                        ),
                        tags = listOf("backup", "database", "maintenance")
                    ),
                    WorkflowTemplateResponse(
                        id = "monitoring-alert",
                        name = "Monitoring Alert",
                        description = "System monitoring and alerting workflow",
                        category = "Monitoring",
                        definition = mapOf(
                            "steps" to listOf(
                                mapOf(
                                    "name" to "check_system_health",
                                    "type" to "http_request",
                                    "config" to mapOf("url" to "{{health_check_url}}", "method" to "GET")
                                ),
                                mapOf(
                                    "name" to "evaluate_response",
                                    "type" to "condition",
                                    "config" to mapOf("condition" to "status_code == 200")
                                ),
                                mapOf(
                                    "name" to "send_alert",
                                    "type" to "slack_notification",
                                    "config" to mapOf("channel" to "#alerts", "message" to "System health check failed")
                                )
                            )
                        ),
                        parameters = listOf(
                            TemplateParameter("health_check_url", "string", "Health check endpoint URL"),
                            TemplateParameter("alert_channel", "string", "Slack channel for alerts", false, "#alerts")
                        ),
                        tags = listOf("monitoring", "alerts", "health-check")
                    )
                )
                
                call.respond(HttpStatusCode.OK, ApiResponse.success(templates))
            }
            
            get("/{id}") {
                val templateId = call.parameters["id"]
                call.respond(HttpStatusCode.NotImplemented, ApiResponse.error<WorkflowTemplateResponse>("Template details not yet implemented"))
            }
        }
    }
    
    private fun Route.validationRoutes() {
        route("/validate") {
            post("/workflow") {
                try {
                    val request = call.receive<ValidateWorkflowRequest>()
                    
                    val validationResult = workflowEngine.validateDefinition(request.definition)
                    val steps = workflowEngine.parseSteps(request.definition)
                    
                    val response = ValidateWorkflowResponse(
                        isValid = validationResult.isValid,
                        errors = validationResult.errors,
                        warnings = emptyList(), // TODO: Add warning detection
                        stepCount = steps.size,
                        estimatedDuration = steps.sumOf { it.timeout } // Rough estimate
                    )
                    
                    call.respond(HttpStatusCode.OK, ApiResponse.success(response))
                    
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<ValidateWorkflowResponse>("Validation failed: ${e.message}"))
                }
            }
        }
    }
    
    private fun Route.bulkRoutes() {
        route("/bulk") {
            post("/workflows") {
                try {
                    val request = call.receive<BulkWorkflowRequest>()
                    
                    val successful = mutableListOf<WorkflowResponse>()
                    val failed = mutableListOf<BulkOperationError>()
                    
                    for (workflowRequest in request.workflows) {
                        val createRequest = workflowRequest.copy(userId = request.userId)
                        
                        when (val result = flowService.createWorkflow(createRequest)) {
                            is FlowResult.Success -> successful.add(result.data)
                            is FlowResult.Error -> failed.add(BulkOperationError(workflowRequest.name, result.message))
                        }
                    }
                    
                    val response = BulkWorkflowResponse(
                        successful = successful,
                        failed = failed,
                        totalProcessed = request.workflows.size,
                        successCount = successful.size,
                        failureCount = failed.size
                    )
                    
                    call.respond(HttpStatusCode.OK, ApiResponse.success(response))
                    
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<BulkWorkflowResponse>("Bulk operation failed"))
                }
            }
        }
    }
    
    private fun Route.searchRoutes() {
        route("/search") {
            post("/workflows") {
                try {
                    val request = call.receive<SearchWorkflowsRequest>()
                    
                    val listRequest = ListWorkflowsRequest(
                        userId = request.userId,
                        status = request.status,
                        namePattern = request.query
                    )
                    
                    when (val result = flowService.listWorkflows(listRequest)) {
                        is FlowResult.Success -> {
                            val workflows = result.data.drop(request.offset).take(request.limit)
                            val response = SearchWorkflowsResponse(
                                workflows = workflows,
                                totalCount = result.data.size,
                                hasMore = result.data.size > request.offset + request.limit
                            )
                            call.respond(HttpStatusCode.OK, ApiResponse.success(response))
                        }
                        is FlowResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<SearchWorkflowsResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<SearchWorkflowsResponse>("Search failed"))
                }
            }
        }
    }
    
    private fun Route.exportImportRoutes() {
        route("/export") {
            post("/workflows") {
                call.respond(HttpStatusCode.NotImplemented, ApiResponse.error<Unit>("Export functionality not yet implemented"))
            }
        }
        
        route("/import") {
            post("/workflows") {
                call.respond(HttpStatusCode.NotImplemented, ApiResponse.error<Unit>("Import functionality not yet implemented"))
            }
        }
    }
    
    // Statistics and monitoring endpoints
    fun Route.statsRoutes() {
        route("/stats") {
            get("/workflows") {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Any>("userId is required"))
                    
                    when (val result = flowService.getWorkflowStats(userId)) {
                        is FlowResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is FlowResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Any>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Any>("Failed to get statistics"))
                }
            }
            
            get("/executions") {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Any>("userId is required"))
                    
                    val workflowId = call.request.queryParameters["workflowId"]
                    
                    when (val result = flowService.getExecutionStats(workflowId, userId)) {
                        is FlowResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is FlowResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Any>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Any>("Failed to get execution statistics"))
                }
            }
        }
    }
}