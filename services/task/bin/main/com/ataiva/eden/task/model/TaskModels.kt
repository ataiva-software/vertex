package com.ataiva.eden.task.model

import com.ataiva.eden.database.repositories.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Request to create a new task
 */
@Serializable
data class CreateTaskRequest(
    val name: String,
    val description: String?,
    val taskType: String,
    val configuration: Map<String, @Serializable(with = AnySerializer::class) Any>,
    val scheduleCron: String? = null,
    val userId: String
)

/**
 * Request to update a task
 */
@Serializable
data class UpdateTaskRequest(
    val taskId: String,
    val description: String? = null,
    val configuration: Map<String, @Serializable(with = AnySerializer::class) Any>? = null,
    val scheduleCron: String? = null,
    val userId: String
)

/**
 * Request to list tasks
 */
@Serializable
data class ListTasksRequest(
    val userId: String,
    val taskType: String? = null,
    val namePattern: String? = null,
    val includeInactive: Boolean = false,
    val scheduledOnly: Boolean = false
)

/**
 * Request to execute a task
 */
@Serializable
data class ExecuteTaskRequest(
    val taskId: String,
    val userId: String,
    val priority: Int? = null,
    val inputData: Map<String, @Serializable(with = AnySerializer::class) Any>? = null
)

/**
 * Request to list executions
 */
@Serializable
data class ListExecutionsRequest(
    val userId: String,
    val taskId: String? = null,
    val status: String? = null,
    val limit: Int? = null
)

/**
 * Task response
 */
@Serializable
data class TaskResponse(
    val id: String,
    val name: String,
    val description: String?,
    val taskType: String,
    val configuration: Map<String, @Serializable(with = AnySerializer::class) Any>,
    val scheduleCron: String?,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun fromTask(task: Task): TaskResponse {
            return TaskResponse(
                id = task.id,
                name = task.name,
                description = task.description,
                taskType = task.taskType,
                configuration = task.configuration,
                scheduleCron = task.scheduleCron,
                isActive = task.isActive,
                createdAt = task.createdAt,
                updatedAt = task.updatedAt
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
    val taskId: String,
    val status: String,
    val priority: Int,
    val inputData: Map<String, @Serializable(with = AnySerializer::class) Any>?,
    val outputData: Map<String, @Serializable(with = AnySerializer::class) Any>?,
    val errorMessage: String?,
    val progressPercentage: Int,
    val queuedAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val durationMs: Int?
) {
    companion object {
        fun fromExecution(execution: TaskExecution): ExecutionResponse {
            return ExecutionResponse(
                id = execution.id,
                taskId = execution.taskId,
                status = execution.status,
                priority = execution.priority,
                inputData = execution.inputData,
                outputData = execution.outputData,
                errorMessage = execution.errorMessage,
                progressPercentage = execution.progressPercentage,
                queuedAt = execution.queuedAt,
                startedAt = execution.startedAt,
                completedAt = execution.completedAt,
                durationMs = execution.durationMs
            )
        }
    }
}

/**
 * Task template response
 */
@Serializable
data class TaskTemplateResponse(
    val id: String,
    val name: String,
    val description: String,
    val taskType: String,
    val category: String,
    val configuration: Map<String, @Serializable(with = AnySerializer::class) Any>,
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
 * Task validation request
 */
@Serializable
data class ValidateTaskRequest(
    val taskType: String,
    val configuration: Map<String, @Serializable(with = AnySerializer::class) Any>
)

/**
 * Task validation response
 */
@Serializable
data class ValidateTaskResponse(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String> = emptyList(),
    val supportedTaskType: Boolean
)

/**
 * Bulk task operations request
 */
@Serializable
data class BulkTaskRequest(
    val tasks: List<CreateTaskRequest>,
    val userId: String
)

/**
 * Bulk task operations response
 */
@Serializable
data class BulkTaskResponse(
    val successful: List<TaskResponse>,
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
    val taskName: String,
    val error: String
)

/**
 * Task search request
 */
@Serializable
data class SearchTasksRequest(
    val query: String,
    val userId: String,
    val taskType: String? = null,
    val limit: Int = 50,
    val offset: Int = 0
)

/**
 * Task search response
 */
@Serializable
data class SearchTasksResponse(
    val tasks: List<TaskResponse>,
    val totalCount: Int,
    val hasMore: Boolean
)

/**
 * Execution logs request
 */
@Serializable
data class ExecutionLogsRequest(
    val executionId: String,
    val userId: String,
    val limit: Int = 100,
    val offset: Int = 0
)

/**
 * Execution logs response
 */
@Serializable
data class ExecutionLogsResponse(
    val logs: List<LogEntry>,
    val totalCount: Int,
    val hasMore: Boolean
)

/**
 * Log entry
 */
@Serializable
data class LogEntry(
    val timestamp: Instant,
    val level: String,
    val message: String,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Queue statistics response
 */
@Serializable
data class QueueStatsResponse(
    val totalQueued: Int,
    val highPriority: Int,
    val mediumPriority: Int,
    val lowPriority: Int,
    val averagePriority: Double,
    val oldestQueuedAt: Instant?,
    val priorityDistribution: Map<Int, Int>,
    val estimatedWaitTime: Long? = null
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
 * Health check response for Task service
 */
@Serializable
data class TaskHealthResponse(
    val status: String,
    val timestamp: Instant,
    val uptime: Long,
    val service: String,
    val database: DatabaseHealth,
    val taskExecutor: TaskExecutorHealth,
    val taskQueue: TaskQueueHealth,
    val scheduler: SchedulerHealth
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
 * Task executor health status
 */
@Serializable
data class TaskExecutorHealth(
    val available: Boolean,
    val supportedTaskTypes: List<String>,
    val runningExecutions: Int = 0,
    val maxConcurrentExecutions: Int = 10
)

/**
 * Task queue health status
 */
@Serializable
data class TaskQueueHealth(
    val available: Boolean,
    val queuedTasks: Int,
    val processingRate: Double? = null
)

/**
 * Scheduler health status
 */
@Serializable
data class SchedulerHealth(
    val available: Boolean,
    val scheduledTasks: Int,
    val nextScheduledRun: Instant? = null
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