package com.ataiva.eden.flow.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

/**
 * Workflow response
 */
@Serializable
data class WorkflowResponse(
    val id: String,
    val name: String,
    val description: String,
    val definition: String, // JSON string representation of the workflow definition
    val status: String,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val metadata: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList()
)

/**
 * Execution response
 */
@Serializable
data class ExecutionResponse(
    val id: String,
    val workflowId: String,
    val status: String,
    val triggeredBy: String,
    val startedAt: String,
    val completedAt: String? = null,
    val inputs: String = "{}", // JSON string representation of the inputs
    val outputs: String = "{}", // JSON string representation of the outputs
    val error: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Step response
 */
@Serializable
data class StepResponse(
    val id: String,
    val executionId: String,
    val name: String,
    val type: String,
    val status: String,
    val startedAt: String,
    val completedAt: String? = null,
    val inputs: String = "{}", // JSON string representation of the inputs
    val outputs: String = "{}", // JSON string representation of the outputs
    val error: String? = null
)

/**
 * Step result
 */
@Serializable
data class StepResult(
    val success: Boolean,
    val outputs: String = "{}", // JSON string representation of the outputs
    val error: String? = null
)

/**
 * API response wrapper
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> {
            return ApiResponse(success = true, data = data)
        }
        
        fun <T> error(message: String): ApiResponse<T> {
            return ApiResponse(success = false, error = message)
        }
    }
}