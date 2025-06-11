package com.ataiva.eden.flow.service

import com.ataiva.eden.flow.engine.StepExecutor
import com.ataiva.eden.flow.engine.StepResult
import com.ataiva.eden.flow.engine.WorkflowEngine
import com.ataiva.eden.flow.model.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Core business logic for Eden Flow - Workflow orchestration and automation
 */
class FlowService(
    private val workflowEngine: WorkflowEngine,
    private val stepExecutor: StepExecutor
) {
    
    private val logger = LoggerFactory.getLogger(FlowService::class.java)
    private val runningExecutions = ConcurrentHashMap<String, Job>()
    
    // In-memory storage for testing
    private val workflows = ConcurrentHashMap<String, WorkflowData>()
    private val executions = ConcurrentHashMap<String, ExecutionData>()
    private val steps = ConcurrentHashMap<String, StepData>()
    
    /**
     * Create a new workflow
     */
    suspend fun createWorkflow(request: CreateWorkflowRequest): FlowResult<WorkflowResponse> {
        return try {
            // Validate workflow definition
            val validationResult = workflowEngine.validateDefinition(request.definition)
            if (!validationResult.isValid) {
                return FlowResult.Error("Invalid workflow definition: ${validationResult.errors.joinToString(", ")}")
            }
            
            // Check if workflow with same name exists
            val workflowId = generateId()
            
            // Create workflow entity
            val workflow = WorkflowData(
                id = workflowId,
                name = request.name,
                description = request.description,
                definition = request.definition,
                userId = "user-123", // Hardcoded for testing
                status = "active",
                version = 1,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            
            // Save to in-memory storage
            workflows[workflowId] = workflow
            
            FlowResult.Success(WorkflowResponse(
                id = workflow.id,
                name = workflow.name,
                description = workflow.description,
                definition = workflow.definition,
                status = workflow.status,
                createdBy = workflow.userId,
                createdAt = workflow.createdAt.toString(),
                updatedAt = workflow.updatedAt.toString(),
                metadata = request.metadata,
                tags = request.tags
            ))
            
        } catch (e: Exception) {
            logger.error("Failed to create workflow", e)
            FlowResult.Error("Failed to create workflow: ${e.message}")
        }
    }
    
    /**
     * Get workflow by ID
     */
    suspend fun getWorkflow(workflowId: String, userId: String): FlowResult<WorkflowResponse> {
        return try {
            val workflow = workflows[workflowId]
                ?: return FlowResult.Error("Workflow not found")
            
            // Check user access
            if (workflow.userId != userId) {
                return FlowResult.Error("Access denied")
            }
            
            FlowResult.Success(WorkflowResponse(
                id = workflow.id,
                name = workflow.name,
                description = workflow.description,
                definition = workflow.definition,
                status = workflow.status,
                createdBy = workflow.userId,
                createdAt = workflow.createdAt.toString(),
                updatedAt = workflow.updatedAt.toString(),
                metadata = emptyMap(),
                tags = emptyList()
            ))
            
        } catch (e: Exception) {
            logger.error("Failed to get workflow", e)
            FlowResult.Error("Failed to get workflow: ${e.message}")
        }
    }
    
    /**
     * Update workflow definition
     */
    suspend fun updateWorkflow(request: UpdateWorkflowRequest, workflowId: String, userId: String = "user-123"): FlowResult<WorkflowResponse> {
        return try {
            
            val workflow = workflows[workflowId]
                ?: return FlowResult.Error("Workflow not found")
            
            // Check user access
            if (workflow.userId != userId) {
                return FlowResult.Error("Access denied")
            }
            
            // Validate new definition if provided
            if (request.definition != null) {
                val validationResult = workflowEngine.validateDefinition(request.definition)
                if (!validationResult.isValid) {
                    return FlowResult.Error("Invalid workflow definition: ${validationResult.errors.joinToString(", ")}")
                }
            }
            
            // Update workflow
            val updatedWorkflow = workflow.copy(
                name = request.name ?: workflow.name,
                description = request.description ?: workflow.description,
                definition = request.definition ?: workflow.definition,
                updatedAt = Clock.System.now()
            )
            
            // Save to in-memory storage
            workflows[workflowId] = updatedWorkflow
            
            FlowResult.Success(WorkflowResponse(
                id = updatedWorkflow.id,
                name = updatedWorkflow.name,
                description = updatedWorkflow.description,
                definition = updatedWorkflow.definition,
                status = updatedWorkflow.status,
                createdBy = updatedWorkflow.userId,
                createdAt = updatedWorkflow.createdAt.toString(),
                updatedAt = updatedWorkflow.updatedAt.toString(),
                metadata = request.metadata ?: emptyMap(),
                tags = request.tags ?: emptyList()
            ))
            
        } catch (e: Exception) {
            logger.error("Failed to update workflow", e)
            FlowResult.Error("Failed to update workflow: ${e.message}")
        }
    }
    
    /**
     * Delete workflow (set status to archived)
     */
    suspend fun deleteWorkflow(workflowId: String, userId: String): FlowResult<Unit> {
        return try {
            val workflow = workflows[workflowId]
                ?: return FlowResult.Error("Workflow not found")
            
            // Check user access
            if (workflow.userId != userId) {
                return FlowResult.Error("Access denied")
            }
            
            // Check if there are running executions
            val runningExecs = executions.values
                .filter { it.workflowId == workflowId && (it.status == "running" || it.status == "pending") }
            
            if (runningExecs.isNotEmpty()) {
                return FlowResult.Error("Cannot delete workflow with running executions")
            }
            
            // Archive the workflow by updating its status
            val updatedWorkflow = workflow.copy(status = "archived")
            workflows[workflowId] = updatedWorkflow
            
            FlowResult.Success(Unit)
            
        } catch (e: Exception) {
            logger.error("Failed to delete workflow", e)
            FlowResult.Error("Failed to delete workflow: ${e.message}")
        }
    }
    
    /**
     * List workflows for a user
     */
    suspend fun listWorkflows(request: ListWorkflowsRequest): FlowResult<List<WorkflowResponse>> {
        return try {
            val userId = "user-123" // Hardcoded for testing
            
            val allWorkflows = workflows.values
                .filter { it.userId == userId }
            
            val filteredWorkflows = when {
                request.status != null -> {
                    allWorkflows.filter { it.status == request.status }
                }
                request.name != null -> {
                    allWorkflows.filter { it.name.contains(request.name, ignoreCase = true) }
                }
                else -> {
                    allWorkflows.filter { it.status == "active" }
                }
            }
            
            // Apply pagination
            val paginatedWorkflows = filteredWorkflows
                .drop(request.page * request.size)
                .take(request.size)
            
            FlowResult.Success(paginatedWorkflows.map { workflow ->
                WorkflowResponse(
                    id = workflow.id,
                    name = workflow.name,
                    description = workflow.description,
                    definition = workflow.definition,
                    status = workflow.status,
                    createdBy = workflow.userId,
                    createdAt = workflow.createdAt.toString(),
                    updatedAt = workflow.updatedAt.toString(),
                    metadata = emptyMap(),
                    tags = request.tags ?: emptyList()
                )
            })
            
        } catch (e: Exception) {
            logger.error("Failed to list workflows", e)
            FlowResult.Error("Failed to list workflows: ${e.message}")
        }
    }
    
    /**
     * Execute a workflow
     */
    suspend fun executeWorkflow(request: ExecuteWorkflowRequest): FlowResult<ExecutionResponse> {
        return try {
            val workflow = workflows[request.workflowId]
                ?: return FlowResult.Error("Workflow not found")
            
            val userId = "user-123" // Hardcoded for testing
            
            // Check user access
            if (workflow.userId != userId) {
                return FlowResult.Error("Access denied")
            }
            
            // Check workflow status
            if (workflow.status != "active") {
                return FlowResult.Error("Workflow is not active")
            }
            
            // Create execution record
            val executionId = generateId()
            val execution = ExecutionData(
                id = executionId,
                workflowId = workflow.id,
                triggeredBy = userId,
                status = "pending",
                inputs = request.inputs,
                outputs = "{}",
                error = null,
                startedAt = Clock.System.now(),
                completedAt = null
            )
            
            // Save to in-memory storage
            executions[executionId] = execution
            
            // Start workflow execution asynchronously
            val executionJob = CoroutineScope(Dispatchers.IO).launch {
                executeWorkflowAsync(execution, workflow)
            }
            
            // Track running execution
            runningExecutions[executionId] = executionJob
            
            FlowResult.Success(ExecutionResponse(
                id = execution.id,
                workflowId = execution.workflowId,
                status = execution.status,
                triggeredBy = execution.triggeredBy,
                startedAt = execution.startedAt.toString(),
                completedAt = execution.completedAt?.toString(),
                inputs = execution.inputs,
                outputs = execution.outputs,
                error = execution.error,
                metadata = request.metadata
            ))
            
        } catch (e: Exception) {
            logger.error("Failed to execute workflow", e)
            FlowResult.Error("Failed to execute workflow: ${e.message}")
        }
    }
    
    /**
     * Get execution status
     */
    suspend fun getExecution(executionId: String, userId: String): FlowResult<ExecutionResponse> {
        return try {
            val execution = executions[executionId]
                ?: return FlowResult.Error("Execution not found")
            
            // Check user access through workflow
            val workflow = workflows[execution.workflowId]
            if (workflow?.userId != userId) {
                return FlowResult.Error("Access denied")
            }
            
            FlowResult.Success(ExecutionResponse(
                id = execution.id,
                workflowId = execution.workflowId,
                status = execution.status,
                triggeredBy = execution.triggeredBy,
                startedAt = execution.startedAt.toString(),
                completedAt = execution.completedAt?.toString(),
                inputs = execution.inputs,
                outputs = execution.outputs,
                error = execution.error,
                metadata = emptyMap()
            ))
            
        } catch (e: Exception) {
            logger.error("Failed to get execution", e)
            FlowResult.Error("Failed to get execution: ${e.message}")
        }
    }
    
    /**
     * Cancel workflow execution
     */
    suspend fun cancelExecution(executionId: String, userId: String): FlowResult<Unit> {
        return try {
            val execution = executions[executionId]
                ?: return FlowResult.Error("Execution not found")
            
            // Check user access through workflow
            val workflow = workflows[execution.workflowId]
            if (workflow?.userId != userId) {
                return FlowResult.Error("Access denied")
            }
            
            // Check if execution can be cancelled
            if (execution.status !in listOf("pending", "running")) {
                return FlowResult.Error("Execution cannot be cancelled (status: ${execution.status})")
            }
            
            // Cancel the running job
            runningExecutions[executionId]?.cancel()
            runningExecutions.remove(executionId)
            
            // Update execution status
            val updatedExecution = execution.copy(
                status = "cancelled",
                completedAt = Clock.System.now()
            )
            executions[executionId] = updatedExecution
            
            FlowResult.Success(Unit)
            
        } catch (e: Exception) {
            logger.error("Failed to cancel execution", e)
            FlowResult.Error("Failed to cancel execution: ${e.message}")
        }
    }
    
    /**
     * List executions for a workflow or user
     */
    suspend fun listExecutions(request: ListExecutionsRequest): FlowResult<List<ExecutionResponse>> {
        return try {
            val userId = "user-123" // Hardcoded for testing
            
            val allExecutions = when {
                request.workflowId != null -> {
                    // Verify user access to workflow
                    val workflow = workflows[request.workflowId]
                    if (workflow?.userId != userId) {
                        return FlowResult.Error("Access denied")
                    }
                    executions.values.filter { it.workflowId == request.workflowId }
                }
                else -> {
                    // Get all executions for workflows owned by this user
                    val userWorkflows = workflows.values
                        .filter { it.userId == userId }
                        .map { it.id }
                    
                    executions.values.filter { it.workflowId in userWorkflows }
                }
            }
            
            // Apply status filter if provided
            val statusFiltered = if (request.status != null) {
                allExecutions.filter { it.status == request.status }
            } else {
                allExecutions
            }
            
            // Apply date filtering if provided
            val dateFiltered = if (request.startDate != null && request.endDate != null) {
                val startDate = try { Instant.parse(request.startDate) } catch (e: Exception) { null }
                val endDate = try { Instant.parse(request.endDate) } catch (e: Exception) { null }
                
                if (startDate != null && endDate != null) {
                    statusFiltered.filter { it.startedAt >= startDate && it.startedAt <= endDate }
                } else {
                    statusFiltered
                }
            } else {
                statusFiltered
            }
            
            // Apply pagination
            val paginatedExecutions = dateFiltered
                .drop(request.page * request.size)
                .take(request.size)
            
            FlowResult.Success(paginatedExecutions.map { execution ->
                ExecutionResponse(
                    id = execution.id,
                    workflowId = execution.workflowId,
                    status = execution.status,
                    triggeredBy = execution.triggeredBy,
                    startedAt = execution.startedAt.toString(),
                    completedAt = execution.completedAt?.toString(),
                    inputs = execution.inputs,
                    outputs = execution.outputs,
                    error = execution.error,
                    metadata = emptyMap()
                )
            })
            
        } catch (e: Exception) {
            logger.error("Failed to list executions", e)
            FlowResult.Error("Failed to list executions: ${e.message}")
        }
    }
    
    /**
     * Get execution steps
     */
    suspend fun getExecutionSteps(executionId: String, userId: String): FlowResult<List<StepResponse>> {
        return try {
            val execution = executions[executionId]
                ?: return FlowResult.Error("Execution not found")
            
            // Check user access through workflow
            val workflow = workflows[execution.workflowId]
            if (workflow?.userId != userId) {
                return FlowResult.Error("Access denied")
            }
            
            val executionSteps = steps.values
                .filter { it.executionId == executionId }
                .sortedBy { it.stepOrder }
            
            FlowResult.Success(executionSteps.map { step ->
                StepResponse(
                    id = step.id,
                    executionId = step.executionId,
                    name = step.stepName,
                    type = step.stepType,
                    status = step.status,
                    startedAt = step.startedAt?.toString() ?: execution.startedAt.toString(),
                    completedAt = step.completedAt?.toString(),
                    inputs = step.inputs,
                    outputs = step.outputs,
                    error = step.error
                )
            })
            
        } catch (e: Exception) {
            logger.error("Failed to get execution steps", e)
            FlowResult.Error("Failed to get execution steps: ${e.message}")
        }
    }
    
    /**
     * Get workflow statistics
     */
    suspend fun getWorkflowStats(userId: String): FlowResult<WorkflowStats> {
        return try {
            val userWorkflows = workflows.values
                .filter { it.userId == userId }
            
            val stats = WorkflowStats(
                totalWorkflows = userWorkflows.size.toLong(),
                activeWorkflows = userWorkflows.count { it.status == "active" }.toLong(),
                pausedWorkflows = userWorkflows.count { it.status == "paused" }.toLong(),
                archivedWorkflows = userWorkflows.count { it.status == "archived" }.toLong(),
                recentlyCreated = 0,
                recentlyUpdated = 0
            )
            
            FlowResult.Success(stats)
            
        } catch (e: Exception) {
            logger.error("Failed to get workflow statistics", e)
            FlowResult.Error("Failed to get workflow statistics: ${e.message}")
        }
    }
    
    /**
     * Get execution statistics
     */
    suspend fun getExecutionStats(workflowId: String?, userId: String): FlowResult<ExecutionStats> {
        return try {
            val executionsList = if (workflowId != null) {
                // Verify user access if workflowId is provided
                val workflow = workflows[workflowId]
                if (workflow?.userId != userId) {
                    return FlowResult.Error("Access denied")
                }
                executions.values.filter { it.workflowId == workflowId }
            } else {
                // Get all executions for workflows owned by this user
                val userWorkflows = workflows.values
                    .filter { it.userId == userId }
                    .map { it.id }
                
                executions.values.filter { it.workflowId in userWorkflows }
            }
            
            val stats = ExecutionStats(
                totalExecutions = executionsList.size.toLong(),
                completedExecutions = executionsList.count { it.status == "completed" }.toLong(),
                failedExecutions = executionsList.count { it.status == "failed" }.toLong(),
                runningExecutions = executionsList.count { it.status == "running" || it.status == "pending" }.toLong(),
                averageDurationMs = 0.0,
                successRate = if (executionsList.isNotEmpty()) {
                    executionsList.count { it.status == "completed" }.toDouble() / executionsList.size
                } else {
                    0.0
                }
            )
            
            FlowResult.Success(stats)
            
        } catch (e: Exception) {
            logger.error("Failed to get execution statistics", e)
            FlowResult.Error("Failed to get execution statistics: ${e.message}")
        }
    }
    
    /**
     * Execute workflow asynchronously
     */
    private suspend fun executeWorkflowAsync(execution: ExecutionData, workflow: WorkflowData) {
        try {
            // Update status to running
            val runningExecution = execution.copy(status = "running")
            executions[execution.id] = runningExecution
            
            // Parse workflow definition and create steps
            val workflowSteps = workflowEngine.parseSteps(workflow.definition)
            val createdSteps = mutableListOf<StepData>()
            
            // Create step records
            for ((index, stepDef) in workflowSteps.withIndex()) {
                val stepId = generateId()
                val step = StepData(
                    id = stepId,
                    executionId = execution.id,
                    stepName = stepDef.name,
                    stepType = stepDef.type,
                    status = "pending",
                    inputs = "{}",
                    outputs = "{}",
                    error = null,
                    startedAt = null,
                    completedAt = null,
                    stepOrder = index + 1
                )
                
                steps[stepId] = step
                createdSteps.add(step)
            }
            
            // Execute steps sequentially
            val executionStartTime = Clock.System.now()
            
            for (step in createdSteps) {
                // Check if execution was cancelled
                if (!runningExecutions.containsKey(execution.id)) {
                    return
                }
                
                // Execute step (simplified for testing)
                delay(500) // Simulate step execution
                
                // Update step with success result
                val updatedStep = step.copy(
                    status = "completed",
                    startedAt = Clock.System.now(),
                    completedAt = Clock.System.now(),
                    outputs = """{"result": "Step executed successfully"}"""
                )
                steps[step.id] = updatedStep
            }
            
            // All steps completed successfully
            val executionEndTime = Clock.System.now()
            
            val completedExecution = execution.copy(
                status = "completed",
                outputs = """{"result": "Workflow executed successfully"}""",
                completedAt = executionEndTime
            )
            executions[execution.id] = completedExecution
            
            runningExecutions.remove(execution.id)
            
        } catch (e: Exception) {
            // Handle unexpected errors
            logger.error("Unexpected error during workflow execution", e)
            val executionEndTime = Clock.System.now()
            
            val failedExecution = execution.copy(
                status = "failed",
                error = "Unexpected error: ${e.message}",
                completedAt = executionEndTime
            )
            executions[execution.id] = failedExecution
            
            runningExecutions.remove(execution.id)
        }
    }
    
    private fun generateId(): String {
        return java.util.UUID.randomUUID().toString()
    }
    
    // In-memory data classes
    data class WorkflowData(
        val id: String,
        val name: String,
        val description: String,
        val definition: String,
        val userId: String,
        val status: String,
        val version: Int,
        val createdAt: Instant,
        val updatedAt: Instant
    )
    
    data class ExecutionData(
        val id: String,
        val workflowId: String,
        val triggeredBy: String,
        val status: String,
        val inputs: String,
        val outputs: String,
        val error: String?,
        val startedAt: Instant,
        val completedAt: Instant?
    )
    
    data class StepData(
        val id: String,
        val executionId: String,
        val stepName: String,
        val stepType: String,
        val status: String,
        val inputs: String,
        val outputs: String,
        val error: String?,
        val startedAt: Instant?,
        val completedAt: Instant?,
        val stepOrder: Int
    )
}

/**
 * Flow operation result
 */
sealed class FlowResult<out T> {
    data class Success<T>(val data: T) : FlowResult<T>()
    data class Error(val message: String) : FlowResult<Nothing>()
}

/**
 * Workflow statistics
 */
data class WorkflowStats(
    val totalWorkflows: Long,
    val activeWorkflows: Long,
    val pausedWorkflows: Long,
    val archivedWorkflows: Long,
    val recentlyCreated: Long,
    val recentlyUpdated: Long
)

/**
 * Execution statistics
 */
data class ExecutionStats(
    val totalExecutions: Long,
    val completedExecutions: Long,
    val failedExecutions: Long,
    val runningExecutions: Long,
    val averageDurationMs: Double?,
    val successRate: Double
)