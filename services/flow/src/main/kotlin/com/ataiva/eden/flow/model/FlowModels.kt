package com.ataiva.eden.flow.model

import com.ataiva.eden.database.repositories.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Request to create a new workflow
 */
@Serializable
data class CreateWorkflowRequest(
    val name: String,
    val description: String?,
    val definition: Map<String, @Serializable(with = AnySerializer::class) Any>,
    val userId: String
)

/**
 * Request to update a workflow
 */
@Serializable
data class UpdateWorkflowRequest(
    val workflowId: String,
    val description: String?,
    val definition: Map<String, @Serializable(with = AnySerializer::class) Any>?,
    val userId: String
)

/**
 * Request to list workflows
 */
@Serializable
data class ListWorkflowsRequest(
    val userId: String,
    val status: String? = null,
    val namePattern: String? = null,
    val includeArchived: Boolean = false
)

/**
 * Request to execute a workflow
 */
@Serializable
data class ExecuteWorkflowRequest(
    val workflowId: String,
    val userId: String,
    val inputData: Map<String, @Serializable(with = AnySerializer::class) Any>? = null,
    val triggeredBy: String? = null
)

/**
 * Request to list executions
 */
@Serializable
data class ListExecutionsRequest(
    val userId: String,
    val workflowId: String? = null,
    val status: String? = null,
    val limit: Int? = null
)

/**
 * Workflow response
 */
@Serializable
data class WorkflowResponse(
    val id: String,
    val name: String,
    val description: String?,
    val definition: Map<String, @Serializable(with = AnySerializer::class) Any>,
    val status: String,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun fromWorkflow(workflow: Workflow): WorkflowResponse {
            return WorkflowResponse(
                id = workflow.id,
                name = workflow.name,
                description = workflow.description,
                definition = workflow.definition,
                status = workflow.status,
                version = workflow.version,
                createdAt = workflow.createdAt,
                updatedAt = workflow.updatedAt
            )
        }
    }
}

/**
 * Execution response
 */
@Serializable
data class ExecutionResponse(
    val id: String,
    val workflowId: String,
    val triggeredBy: String?,
    val status: String,
    val inputData: Map<String, @Serializable(with = AnySerializer::class) Any>?,
    val outputData: Map<String, @Serializable(with = AnySerializer::class) Any>?,
    val errorMessage: String?,
    val startedAt: Instant,
    val completedAt: Instant?,
    val durationMs: Int?
) {
    companion object {
        fun fromExecution(execution: WorkflowExecution): ExecutionResponse {
            return ExecutionResponse(
                id = execution.id,
                workflowId = execution.workflowId,
                triggeredBy = execution.triggeredBy,
                status = execution.status,
                inputData = execution.inputData,
                outputData = execution.outputData,
                errorMessage = execution.errorMessage,
                startedAt = execution.startedAt,
                completedAt = execution.completedAt,
                durationMs = execution.durationMs
            )
        }
    }
}

/**
 * Step response
 */
@Serializable
data class StepResponse(
    val id: String,
    val executionId: String,
    val stepName: String,
    val stepType: String,
    val status: String,
    val inputData: Map<String, @Serializable(with = AnySerializer::class) Any>?,
    val outputData: Map<String, @Serializable(with = AnySerializer::class) Any>?,
    val errorMessage: String?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val durationMs: Int?,
    val stepOrder: Int
) {
    companion object {
        fun fromStep(step: WorkflowStep): StepResponse {
            return StepResponse(
                id = step.id,
                executionId = step.executionId,
                stepName = step.stepName,
                stepType = step.stepType,
                status = step.status,
                inputData = step.inputData,
                outputData = step.outputData,
                errorMessage = step.errorMessage,
                startedAt = step.startedAt,
                completedAt = step.completedAt,
                durationMs = step.durationMs,
                stepOrder = step.stepOrder
            )
        }
    }
}

/**
 * Workflow template response
 */
@Serializable
data class WorkflowTemplateResponse(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val definition: Map<String, @Serializable(with = AnySerializer::class) Any>,
    val parameters: List<TemplateParameter>,
    val tags: List<String>
)

/**
 * Template parameter
 */
@Serializable
data class TemplateParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val defaultValue: String? = null,
    val options: List<String>? = null
)

/**
 * Workflow validation request
 */
@Serializable
data class ValidateWorkflowRequest(
    val definition: Map<String, @Serializable(with = AnySerializer::class) Any>
)

/**
 * Workflow validation response
 */
@Serializable
data class ValidateWorkflowResponse(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String> = emptyList(),
    val stepCount: Int,
    val estimatedDuration: Int? = null
)

/**
 * Bulk workflow operations request
 */
@Serializable
data class BulkWorkflowRequest(
    val workflows: List<CreateWorkflowRequest>,
    val userId: String
)

/**
 * Bulk workflow operations response
 */
@Serializable
data class BulkWorkflowResponse(
    val successful: List<WorkflowResponse>,
    val failed: List<BulkOperationError>,
    val totalProcessed: Int,
    val successCount: Int,
    val failureCount: Int
)

/**
 * Bulk operation error
 */
@Serializable
data class BulkOperationError(
    val workflowName: String,
    val error: String
)

/**
 * Workflow search request
 */
@Serializable
data class SearchWorkflowsRequest(
    val query: String,
    val userId: String,
    val status: String? = null,
    val limit: Int = 50,
    val offset: Int = 0
)

/**
 * Workflow search response
 */
@Serializable
data class SearchWorkflowsResponse(
    val workflows: List<WorkflowResponse>,
    val totalCount: Int,
    val hasMore: Boolean
)

/**
 * Execution statistics request
 */
@Serializable
data class ExecutionStatsRequest(
    val workflowId: String? = null,
    val userId: String,
    val dateRange: DateRangeFilter? = null
)

/**
 * Date range filter
 */
@Serializable
data class DateRangeFilter(
    val startDate: Instant,
    val endDate: Instant
)

/**
 * Workflow export request
 */
@Serializable
data class ExportWorkflowRequest(
    val workflowIds: List<String>,
    val userId: String,
    val format: String = "json", // "json", "yaml"
    val includeExecutionHistory: Boolean = false
)

/**
 * Workflow import request
 */
@Serializable
data class ImportWorkflowRequest(
    val data: String,
    val format: String = "json",
    val userId: String,
    val overwriteExisting: Boolean = false
)

/**
 * Import result
 */
@Serializable
data class ImportWorkflowResponse(
    val imported: List<WorkflowResponse>,
    val skipped: List<String>,
    val errors: List<BulkOperationError>,
    val totalProcessed: Int,
    val importedCount: Int,
    val skippedCount: Int,
    val errorCount: Int
)

/**
 * API response wrapper
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val timestamp: Instant = kotlinx.datetime.Clock.System.now()
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

/**
 * Health check response for Flow service
 */
@Serializable
data class FlowHealthResponse(
    val status: String,
    val timestamp: Instant,
    val uptime: Long,
    val service: String,
    val database: DatabaseHealth,
    val workflowEngine: WorkflowEngineHealth,
    val runningExecutions: Int
)

/**
 * Database health status
 */
@Serializable
data class DatabaseHealth(
    val connected: Boolean,
    val responseTime: Long? = null,
    val activeConnections: Int? = null
)

/**
 * Workflow engine health status
 */
@Serializable
data class WorkflowEngineHealth(
    val available: Boolean,
    val supportedStepTypes: List<String>,
    val maxConcurrentExecutions: Int = 10
)

/**
 * Custom serializer for Any type to handle JSON serialization
 */
object AnySerializer : kotlinx.serialization.KSerializer<Any> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("Any")
    
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Any) {
        when (value) {
            is String -> encoder.encodeString(value)
            is Int -> encoder.encodeInt(value)
            is Long -> encoder.encodeLong(value)
            is Double -> encoder.encodeDouble(value)
            is Boolean -> encoder.encodeBoolean(value)
            is List<*> -> {
                val listSerializer = kotlinx.serialization.builtins.ListSerializer(AnySerializer)
                @Suppress("UNCHECKED_CAST")
                listSerializer.serialize(encoder, value as List<Any>)
            }
            is Map<*, *> -> {
                val mapSerializer = kotlinx.serialization.builtins.MapSerializer(
                    kotlinx.serialization.builtins.serializer<String>(),
                    AnySerializer
                )
                @Suppress("UNCHECKED_CAST")
                mapSerializer.serialize(encoder, value as Map<String, Any>)
            }
            else -> encoder.encodeString(value.toString())
        }
    }
    
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Any {
        return when (val jsonElement = kotlinx.serialization.json.JsonElement.serializer().deserialize(decoder)) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                when {
                    jsonElement.isString -> jsonElement.content
                    jsonElement.content == "true" || jsonElement.content == "false" -> jsonElement.content.toBoolean()
                    jsonElement.content.contains('.') -> jsonElement.content.toDoubleOrNull() ?: jsonElement.content
                    else -> jsonElement.content.toLongOrNull() ?: jsonElement.content
                }
            }
            is kotlinx.serialization.json.JsonArray -> jsonElement.map { deserializeJsonElement(it) }
            is kotlinx.serialization.json.JsonObject -> jsonElement.mapValues { deserializeJsonElement(it.value) }
        }
    }
    
    private fun deserializeJsonElement(element: kotlinx.serialization.json.JsonElement): Any {
        return when (element) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.content == "true" || element.content == "false" -> element.content.toBoolean()
                    element.content.contains('.') -> element.content.toDoubleOrNull() ?: element.content
                    else -> element.content.toLongOrNull() ?: element.content
                }
            }
            is kotlinx.serialization.json.JsonArray -> element.map { deserializeJsonElement(it) }
            is kotlinx.serialization.json.JsonObject -> element.mapValues { deserializeJsonElement(it.value) }
        }
    }
}