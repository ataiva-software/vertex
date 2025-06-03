package com.ataiva.eden.database.repositories

import com.ataiva.eden.database.Repository
import kotlinx.datetime.Instant

/**
 * Workflow entity for database operations
 */
data class Workflow(
    val id: String,
    val name: String,
    val description: String?,
    val definition: Map<String, Any>,
    val userId: String,
    val status: String,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Workflow execution entity
 */
data class WorkflowExecution(
    val id: String,
    val workflowId: String,
    val triggeredBy: String?,
    val status: String,
    val inputData: Map<String, Any>?,
    val outputData: Map<String, Any>?,
    val errorMessage: String?,
    val startedAt: Instant,
    val completedAt: Instant?,
    val durationMs: Int?
)

/**
 * Workflow step entity
 */
data class WorkflowStep(
    val id: String,
    val executionId: String,
    val stepName: String,
    val stepType: String,
    val status: String,
    val inputData: Map<String, Any>?,
    val outputData: Map<String, Any>?,
    val errorMessage: String?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val durationMs: Int?,
    val stepOrder: Int
)

/**
 * Repository interface for workflow management operations
 */
interface WorkflowRepository : Repository<Workflow, String> {
    
    /**
     * Find workflow by name and user ID
     */
    suspend fun findByNameAndUser(name: String, userId: String): Workflow?
    
    /**
     * Find all workflows for a user
     */
    suspend fun findByUserId(userId: String): List<Workflow>
    
    /**
     * Find active workflows for a user
     */
    suspend fun findActiveByUserId(userId: String): List<Workflow>
    
    /**
     * Find workflows by status
     */
    suspend fun findByStatus(status: String): List<Workflow>
    
    /**
     * Search workflows by name pattern
     */
    suspend fun searchByName(userId: String, namePattern: String): List<Workflow>
    
    /**
     * Update workflow status
     */
    suspend fun updateStatus(id: String, status: String): Boolean
    
    /**
     * Update workflow definition
     */
    suspend fun updateDefinition(id: String, definition: Map<String, Any>): Boolean
    
    /**
     * Get workflow statistics for user
     */
    suspend fun getWorkflowStats(userId: String): WorkflowStats
}

/**
 * Repository interface for workflow execution operations
 */
interface WorkflowExecutionRepository : Repository<WorkflowExecution, String> {
    
    /**
     * Find executions by workflow ID
     */
    suspend fun findByWorkflowId(workflowId: String): List<WorkflowExecution>
    
    /**
     * Find executions by status
     */
    suspend fun findByStatus(status: String): List<WorkflowExecution>
    
    /**
     * Find executions triggered by user
     */
    suspend fun findByTriggeredBy(userId: String): List<WorkflowExecution>
    
    /**
     * Find recent executions
     */
    suspend fun findRecent(limit: Int = 50): List<WorkflowExecution>
    
    /**
     * Find running executions
     */
    suspend fun findRunning(): List<WorkflowExecution>
    
    /**
     * Update execution status
     */
    suspend fun updateStatus(id: String, status: String, completedAt: Instant? = null): Boolean
    
    /**
     * Update execution output
     */
    suspend fun updateOutput(id: String, outputData: Map<String, Any>): Boolean
    
    /**
     * Update execution error
     */
    suspend fun updateError(id: String, errorMessage: String, completedAt: Instant): Boolean
    
    /**
     * Get execution statistics
     */
    suspend fun getExecutionStats(workflowId: String? = null): ExecutionStats
}

/**
 * Repository interface for workflow step operations
 */
interface WorkflowStepRepository : Repository<WorkflowStep, String> {
    
    /**
     * Find steps by execution ID
     */
    suspend fun findByExecutionId(executionId: String): List<WorkflowStep>
    
    /**
     * Find steps by execution ID ordered by step order
     */
    suspend fun findByExecutionIdOrdered(executionId: String): List<WorkflowStep>
    
    /**
     * Find step by execution ID and step order
     */
    suspend fun findByExecutionIdAndOrder(executionId: String, stepOrder: Int): WorkflowStep?
    
    /**
     * Find steps by status
     */
    suspend fun findByStatus(status: String): List<WorkflowStep>
    
    /**
     * Update step status
     */
    suspend fun updateStatus(id: String, status: String, startedAt: Instant? = null, completedAt: Instant? = null): Boolean
    
    /**
     * Update step output
     */
    suspend fun updateOutput(id: String, outputData: Map<String, Any>): Boolean
    
    /**
     * Update step error
     */
    suspend fun updateError(id: String, errorMessage: String, completedAt: Instant): Boolean
    
    /**
     * Get step statistics for execution
     */
    suspend fun getStepStats(executionId: String): StepStats
}

/**
 * Workflow statistics data class
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
 * Execution statistics data class
 */
data class ExecutionStats(
    val totalExecutions: Long,
    val completedExecutions: Long,
    val failedExecutions: Long,
    val runningExecutions: Long,
    val averageDurationMs: Double?,
    val successRate: Double
)

/**
 * Step statistics data class
 */
data class StepStats(
    val totalSteps: Long,
    val completedSteps: Long,
    val failedSteps: Long,
    val runningSteps: Long,
    val averageDurationMs: Double?,
    val successRate: Double
)