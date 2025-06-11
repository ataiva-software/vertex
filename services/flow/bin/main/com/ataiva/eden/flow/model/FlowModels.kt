package com.ataiva.eden.flow.model

import com.ataiva.eden.database.repositories.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

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
            is Map<*, *> -> encoder.encodeString(value.toString())
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