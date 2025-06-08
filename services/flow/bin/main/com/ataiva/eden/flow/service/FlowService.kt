package com.ataiva.eden.flow.service

import com.ataiva.eden.database.EdenDatabaseService
import com.ataiva.eden.database.repositories.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Core business logic for Eden Flow - Workflow orchestration and automation
 */
class FlowService(
    private val databaseService: EdenDatabaseService,
    private val workflowEngine: WorkflowEngine,
    private val stepExecutor: StepExecutor
) {
    
    private val runningExecutions = ConcurrentHashMap<String, Job>()
    
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
            val existing = databaseService.workflowRepository.findByNameAndUser(request.name, request.userId)
            if (existing != null) {
                return FlowResult.Error("Workflow with name '${request.name}' already exists")
            }
            
            // Create workflow entity
            val workflow = Workflow(
                id = generateId(),
                name = request.name,
                description = request.description,
                definition = request.definition,
                userId = request.userId,
                status = "active",
                version = 1,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            
            // Save to database
            val savedWorkflow = databaseService.workflowRepository.create(workflow)
            
            FlowResult.Success(WorkflowResponse.fromWorkflow(savedWorkflow))
            
        } catch (e: Exception) {
            FlowResult.Error("Failed to create workflow: ${e.message}")
        }
    }
    
    /**
     * Get workflow by ID
     */
    suspend fun getWorkflow(workflowId: String, userId: String): FlowResult<WorkflowResponse> {
        return try {
            val workflow = databaseService.workflowRepository.findById(workflowId)
                ?: return FlowResult.Error("Workflow not found")
            
            // Check user access
            if (workflow.userId != userId) {
                return FlowResult.Error("Access denied")
            }
            
            FlowResult.Success(WorkflowResponse.fromWorkflow(workflow))
            
        } catch (e: Exception) {
            FlowResult.Error("Failed to get workflow: ${e.message}")
        }
    }
    
    /**
     * Update workflow definition
     */
    suspend fun updateWorkflow(request: UpdateWorkflowRequest): FlowResult<WorkflowResponse> {
        return try {
            val workflow = databaseService.workflowRepository.findById(request.workflowId)
                ?: return FlowResult.Error("Workflow not found")
            
            // Check user access
            if (workflow.userId != request.userId) {
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
                description = request.description ?: workflow.description,
                definition = request.definition ?: workflow.definition,
                updatedAt = Clock.System.now()
            )
            
            val saved = databaseService.workflowRepository.update(updatedWorkflow)
            if (!saved) {
                return FlowResult.Error("Failed to update workflow")
            }
            
            FlowResult.Success(WorkflowResponse.fromWorkflow(updatedWorkflow))
            
        } catch (e: Exception) {
            FlowResult.Error("Failed to update workflow: ${e.message}")
        }
    }
    
    /**
     * Delete workflow (set status to archived)
     */
    suspend fun deleteWorkflow(workflowId: String, userId: String): FlowResult<Unit> {
        return try {
            val workflow = databaseService.workflowRepository.findById(workflowId)
                ?: return FlowResult.Error("Workflow not found")
            
            // Check user access
            if (workflow.userId != userId) {
                return FlowResult.Error("Access denied")
            }
            
            // Check if there are running executions
            val runningExecutions = databaseService.workflowExecutionRepository.findByWorkflowId(workflowId)
                .filter { it.status == "running" || it.status == "pending" }
            
            if (runningExecutions.isNotEmpty()) {
                return FlowResult.Error("Cannot delete workflow with running executions")
            }
            
            // Archive the workflow
            val success = databaseService.workflowRepository.updateStatus(workflowId, "archived")
            if (!success) {
                return FlowResult.Error("Failed to delete workflow")
            }
            
            FlowResult.Success(Unit)
            
        } catch (e: Exception) {
            FlowResult.Error("Failed to delete workflow: ${e.message}")
        }
    }
    
    /**
     * List workflows for a user
     */
    suspend fun listWorkflows(request: ListWorkflowsRequest): FlowResult<List<WorkflowResponse>> {
        return try {
            val workflows = when {
                request.status != null -> {
                    databaseService.workflowRepository.findByUserId(request.userId)
                        .filter { it.status == request.status }
                }
                request.namePattern != null -> {
                    databaseService.workflowRepository.searchByName(request.userId, request.namePattern)
                }
                request.includeArchived -> {
                    databaseService.workflowRepository.findByUserId(request.userId)
                }
                else -> {
                    databaseService.workflowRepository.findActiveByUserId(request.userId)
                }
            }
            
            FlowResult.Success(workflows.map { WorkflowResponse.fromWorkflow(it) })
            
        } catch (e: Exception) {
            FlowResult.Error("Failed to list workflows: ${e.message}")
        }
    }
    
    /**
     * Execute a workflow
     */
    suspend fun executeWorkflow(request: ExecuteWorkflowRequest): FlowResult<ExecutionResponse> {
        return try {
            val workflow = databaseService.workflowRepository.findById(request.workflowId)
                ?: return FlowResult.Error("Workflow not found")
            
            // Check user access
            if (workflow.userId != request.userId) {
                return FlowResult.Error("Access denied")
            }
            
            // Check workflow status
            if (workflow.status != "active") {
                return FlowResult.Error("Workflow is not active")
            }
            
            // Create execution record
            val execution = WorkflowExecution(
                id = generateId(),
                workflowId = workflow.id,
                triggeredBy = request.userId,
                status = "pending",
                inputData = request.inputData,
                outputData = null,
                errorMessage = null,
                startedAt = Clock.System.now(),
                completedAt = null,
                durationMs = null
            )
            
            val savedExecution = databaseService.workflowExecutionRepository.create(execution)
            
            // Start workflow execution asynchronously
            val executionJob = CoroutineScope(Dispatchers.IO).launch {
                executeWorkflowAsync(savedExecution, workflow)
            }
            
            // Track running execution
            runningExecutions[savedExecution.id] = executionJob
            
            FlowResult.Success(ExecutionResponse.fromExecution(savedExecution))
            
        } catch (e: Exception) {
            FlowResult.Error("Failed to execute workflow: ${e.message}")
        }
    }
    
    /**
     * Get execution status
     */
    suspend fun getExecution(executionId: String, userId: String): FlowResult<ExecutionResponse> {
        return try {
            val execution = databaseService.workflowExecutionRepository.findById(executionId)
                ?: return FlowResult.Error("Execution not found")
            
            // Check user access through workflow
            val workflow = databaseService.workflowRepository.findById(execution.workflowId)
            if (workflow?.userId != userId) {
                return FlowResult.Error("Access denied")
            }
            
            FlowResult.Success(ExecutionResponse.fromExecution(execution))
            
        } catch (e: Exception) {
            FlowResult.Error("Failed to get execution: ${e.message}")
        }
    }
    
    /**
     * Cancel workflow execution
     */
    suspend fun cancelExecution(executionId: String, userId: String): FlowResult<Unit> {
        return try {
            val execution = databaseService.workflowExecutionRepository.findById(executionId)
                ?: return FlowResult.Error("Execution not found")
            
            // Check user access through workflow
            val workflow = databaseService.workflowRepository.findById(execution.workflowId)
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
            databaseService.workflowExecutionRepository.updateStatus(
                executionId, 
                "cancelled", 
                Clock.System.now()
            )
            
            FlowResult.Success(Unit)
            
        } catch (e: Exception) {
            FlowResult.Error("Failed to cancel execution: ${e.message}")
        }
    }
    
    /**
     * List executions for a workflow or user
     */
    suspend fun listExecutions(request: ListExecutionsRequest): FlowResult<List<ExecutionResponse>> {
        return try {
            val executions = when {
                request.workflowId != null -> {
                    // Verify user access to workflow
                    val workflow = databaseService.workflowRepository.findById(request.workflowId)
                    if (workflow?.userId != request.userId) {
                        return FlowResult.Error("Access denied")
                    }
                    databaseService.workflowExecutionRepository.findByWorkflowId(request.workflowId)
                }
                request.status != null -> {
                    databaseService.workflowExecutionRepository.findByTriggeredBy(request.userId)
                        .filter { it.status == request.status }
                }
                else -> {
                    databaseService.workflowExecutionRepository.findByTriggeredBy(request.userId)
                }
            }
            
            val limitedExecutions = if (request.limit != null) {
                executions.take(request.limit)
            } else {
                executions
            }
            
            FlowResult.Success(limitedExecutions.map { ExecutionResponse.fromExecution(it) })
            
        } catch (e: Exception) {
            FlowResult.Error("Failed to list executions: ${e.message}")
        }
    }
    
    /**
     * Get execution steps
     */
    suspend fun getExecutionSteps(executionId: String, userId: String): FlowResult<List<StepResponse>> {
        return try {
            val execution = databaseService.workflowExecutionRepository.findById(executionId)
                ?: return FlowResult.Error("Execution not found")
            
            // Check user access through workflow
            val workflow = databaseService.workflowRepository.findById(execution.workflowId)
            if (workflow?.userId != userId) {
                return FlowResult.Error("Access denied")
            }
            
            val steps = databaseService.workflowStepRepository.findByExecutionIdOrdered(executionId)
            
            FlowResult.Success(steps.map { StepResponse.fromStep(it) })
            
        } catch (e: Exception) {
            FlowResult.Error("Failed to get execution steps: ${e.message}")
        }
    }
    
    /**
     * Get workflow statistics
     */
    suspend fun getWorkflowStats(userId: String): FlowResult<WorkflowStats> {
        return try {
            val stats = databaseService.workflowRepository.getWorkflowStats(userId)
            FlowResult.Success(stats)
            
        } catch (e: Exception) {
            FlowResult.Error("Failed to get workflow statistics: ${e.message}")
        }
    }
    
    /**
     * Get execution statistics
     */
    suspend fun getExecutionStats(workflowId: String?, userId: String): FlowResult<ExecutionStats> {
        return try {
            // Verify user access if workflowId is provided
            if (workflowId != null) {
                val workflow = databaseService.workflowRepository.findById(workflowId)
                if (workflow?.userId != userId) {
                    return FlowResult.Error("Access denied")
                }
            }
            
            val stats = databaseService.workflowExecutionRepository.getExecutionStats(workflowId)
            FlowResult.Success(stats)
            
        } catch (e: Exception) {
            FlowResult.Error("Failed to get execution statistics: ${e.message}")
        }
    }
    
    /**
     * Execute workflow asynchronously
     */
    private suspend fun executeWorkflowAsync(execution: WorkflowExecution, workflow: Workflow) {
        try {
            // Update status to running
            databaseService.workflowExecutionRepository.updateStatus(execution.id, "running")
            
            // Parse workflow definition and create steps
            val steps = workflowEngine.parseSteps(workflow.definition)
            val createdSteps = mutableListOf<WorkflowStep>()
            
            // Create step records
            for ((index, stepDef) in steps.withIndex()) {
                val step = WorkflowStep(
                    id = generateId(),
                    executionId = execution.id,
                    stepName = stepDef.name,
                    stepType = stepDef.type,
                    status = "pending",
                    inputData = stepDef.inputData,
                    outputData = null,
                    errorMessage = null,
                    startedAt = null,
                    completedAt = null,
                    durationMs = null,
                    stepOrder = index + 1
                )
                
                val savedStep = databaseService.workflowStepRepository.create(step)
                createdSteps.add(savedStep)
            }
            
            // Execute steps sequentially
            var currentInputData = execution.inputData
            val executionStartTime = Clock.System.now()
            
            for (step in createdSteps) {
                // Check if execution was cancelled
                if (!runningExecutions.containsKey(execution.id)) {
                    return
                }
                
                // Execute step
                val stepResult = stepExecutor.executeStep(step, currentInputData)
                
                // Update step with result
                when (stepResult) {
                    is StepResult.Success -> {
                        databaseService.workflowStepRepository.updateStatus(
                            step.id, 
                            "completed", 
                            stepResult.startedAt, 
                            stepResult.completedAt
                        )
                        databaseService.workflowStepRepository.updateOutput(step.id, stepResult.outputData)
                        currentInputData = stepResult.outputData
                    }
                    is StepResult.Error -> {
                        databaseService.workflowStepRepository.updateError(
                            step.id, 
                            stepResult.errorMessage, 
                            stepResult.completedAt
                        )
                        
                        // Fail the entire execution
                        val executionEndTime = Clock.System.now()
                        val duration = (executionEndTime.toEpochMilliseconds() - executionStartTime.toEpochMilliseconds()).toInt()
                        
                        databaseService.workflowExecutionRepository.updateError(
                            execution.id,
                            "Step '${step.stepName}' failed: ${stepResult.errorMessage}",
                            executionEndTime
                        )
                        
                        runningExecutions.remove(execution.id)
                        return
                    }
                }
            }
            
            // All steps completed successfully
            val executionEndTime = Clock.System.now()
            val duration = (executionEndTime.toEpochMilliseconds() - executionStartTime.toEpochMilliseconds()).toInt()
            
            databaseService.workflowExecutionRepository.updateStatus(execution.id, "completed", executionEndTime)
            databaseService.workflowExecutionRepository.updateOutput(execution.id, currentInputData ?: emptyMap())
            
            runningExecutions.remove(execution.id)
            
        } catch (e: Exception) {
            // Handle unexpected errors
            val executionEndTime = Clock.System.now()
            databaseService.workflowExecutionRepository.updateError(
                execution.id,
                "Unexpected error: ${e.message}",
                executionEndTime
            )
            
            runningExecutions.remove(execution.id)
        }
    }
    
    private fun generateId(): String {
        return java.util.UUID.randomUUID().toString()
    }
}

/**
 * Flow operation result
 */
sealed class FlowResult<out T> {
    data class Success<T>(val data: T) : FlowResult<T>()
    data class Error(val message: String) : FlowResult<Nothing>()
    
    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }
    
    fun getErrorOrNull(): String? = when (this) {
        is Success -> null
        is Error -> message
    }
}