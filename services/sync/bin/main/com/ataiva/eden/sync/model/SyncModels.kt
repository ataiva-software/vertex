package com.ataiva.eden.sync.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

/**
 * Core data models for the Eden Sync Service
 * Handles data synchronization between multiple sources and destinations
 */

@Serializable
data class SyncJob(
    val id: String,
    val name: String,
    val sourceId: String,
    val destinationId: String,
    val mappingId: String,
    val status: SyncStatus,
    val schedule: SyncSchedule?,
    val configuration: SyncConfiguration,
    val createdAt: Long,
    val updatedAt: Long,
    val lastRunAt: Long?,
    val nextRunAt: Long?,
    val userId: String
)

@Serializable
enum class SyncStatus {
    CREATED,
    SCHEDULED,
    RUNNING,
    COMPLETED,
    FAILED,
    PAUSED,
    CANCELLED
}

@Serializable
data class SyncSchedule(
    val type: ScheduleType,
    val cronExpression: String?,
    val intervalMinutes: Int?,
    val enabled: Boolean = true
)

@Serializable
enum class ScheduleType {
    MANUAL,
    INTERVAL,
    CRON,
    REAL_TIME
}

@Serializable
data class SyncConfiguration(
    val batchSize: Int = 1000,
    val maxRetries: Int = 3,
    val timeoutSeconds: Int = 300,
    val conflictResolution: ConflictResolution = ConflictResolution.SOURCE_WINS,
    val enableValidation: Boolean = true,
    val enableTransformation: Boolean = false,
    val customSettings: Map<String, String> = emptyMap()
)

@Serializable
enum class ConflictResolution {
    SOURCE_WINS,
    DESTINATION_WINS,
    MERGE,
    SKIP,
    FAIL
}

@Serializable
data class DataSource(
    val id: String,
    val name: String,
    val type: SourceType,
    val connectionConfig: ConnectionConfig,
    val status: ConnectionStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val lastTestedAt: Long?,
    val userId: String
)

@Serializable
enum class SourceType {
    DATABASE,
    FILE_SYSTEM,
    REST_API,
    CLOUD_STORAGE,
    MESSAGE_QUEUE,
    WEBHOOK
}

@Serializable
enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    ERROR,
    TESTING,
    UNKNOWN
}

@Serializable
data class ConnectionConfig(
    val host: String?,
    val port: Int?,
    val database: String?,
    val username: String?,
    val password: String?, // Should be encrypted in real implementation
    val connectionString: String?,
    val apiKey: String?, // Should be encrypted in real implementation
    val headers: Map<String, String> = emptyMap(),
    val parameters: Map<String, String> = emptyMap()
)

@Serializable
data class SyncDestination(
    val id: String,
    val name: String,
    val type: DestinationType,
    val connectionConfig: ConnectionConfig,
    val status: ConnectionStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val lastTestedAt: Long?,
    val userId: String
)

@Serializable
enum class DestinationType {
    DATABASE,
    FILE_SYSTEM,
    REST_API,
    CLOUD_STORAGE,
    MESSAGE_QUEUE,
    DATA_WAREHOUSE
}

@Serializable
data class DataMapping(
    val id: String,
    val name: String,
    val sourceSchema: SchemaDefinition,
    val destinationSchema: SchemaDefinition,
    val fieldMappings: List<FieldMapping>,
    val transformations: List<DataTransformation>,
    val validationRules: List<ValidationRule>,
    val createdAt: Long,
    val updatedAt: Long,
    val userId: String
)

@Serializable
data class SchemaDefinition(
    val fields: List<SchemaField>
)

@Serializable
data class SchemaField(
    val name: String,
    val type: FieldType,
    val nullable: Boolean = true,
    val primaryKey: Boolean = false,
    val unique: Boolean = false
)

@Serializable
enum class FieldType {
    STRING,
    INTEGER,
    LONG,
    DOUBLE,
    BOOLEAN,
    DATE,
    TIMESTAMP,
    JSON,
    BINARY
}

@Serializable
data class FieldMapping(
    val sourceField: String,
    val destinationField: String,
    val transformation: String? = null
)

@Serializable
data class DataTransformation(
    val id: String,
    val name: String,
    val type: TransformationType,
    val expression: String,
    val parameters: Map<String, String> = emptyMap()
)

@Serializable
enum class TransformationType {
    FIELD_RENAME,
    VALUE_MAPPING,
    FORMAT_CONVERSION,
    CALCULATION,
    CONDITIONAL,
    AGGREGATION,
    CUSTOM_SCRIPT
}

@Serializable
data class ValidationRule(
    val id: String,
    val field: String,
    val type: ValidationType,
    val parameters: Map<String, String> = emptyMap(),
    val errorMessage: String
)

@Serializable
enum class ValidationType {
    NOT_NULL,
    MIN_LENGTH,
    MAX_LENGTH,
    REGEX_MATCH,
    RANGE_CHECK,
    UNIQUE_CHECK,
    FOREIGN_KEY_CHECK,
    CUSTOM_VALIDATION
}

@Serializable
data class SyncExecution(
    val id: String,
    val jobId: String,
    val status: SyncStatus,
    val startedAt: Long,
    val completedAt: Long?,
    val recordsProcessed: Long = 0,
    val recordsSucceeded: Long = 0,
    val recordsFailed: Long = 0,
    val recordsSkipped: Long = 0,
    val errorMessage: String? = null,
    val progressPercentage: Double = 0.0,
    val metrics: SyncMetrics
)

@Serializable
data class SyncMetrics(
    val throughputRecordsPerSecond: Double = 0.0,
    val averageProcessingTimeMs: Double = 0.0,
    val memoryUsageMB: Double = 0.0,
    val networkBytesTransferred: Long = 0,
    val validationErrors: Int = 0,
    val transformationErrors: Int = 0
)

// Request/Response models for API endpoints

@Serializable
data class CreateSyncJobRequest(
    val name: String,
    val sourceId: String,
    val destinationId: String,
    val mappingId: String,
    val schedule: SyncSchedule?,
    val configuration: SyncConfiguration,
    val userId: String
)

@Serializable
data class UpdateSyncJobRequest(
    val name: String?,
    val schedule: SyncSchedule?,
    val configuration: SyncConfiguration?,
    val status: SyncStatus?
)

@Serializable
data class CreateDataSourceRequest(
    val name: String,
    val type: SourceType,
    val connectionConfig: ConnectionConfig,
    val userId: String
)

@Serializable
data class CreateDestinationRequest(
    val name: String,
    val type: DestinationType,
    val connectionConfig: ConnectionConfig,
    val userId: String
)

@Serializable
data class CreateMappingRequest(
    val name: String,
    val sourceSchema: SchemaDefinition,
    val destinationSchema: SchemaDefinition,
    val fieldMappings: List<FieldMapping>,
    val transformations: List<DataTransformation> = emptyList(),
    val validationRules: List<ValidationRule> = emptyList(),
    val userId: String
)

@Serializable
data class ExecuteSyncRequest(
    val jobId: String,
    val userId: String,
    val overrideConfiguration: SyncConfiguration? = null
)

@Serializable
data class TestConnectionRequest(
    val connectionConfig: ConnectionConfig,
    val type: SourceType
)

@Serializable
data class TestConnectionResponse(
    val success: Boolean,
    val message: String,
    val latencyMs: Long,
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class SyncStatusResponse(
    val activeSyncs: Int,
    val pendingSyncs: Int,
    val failedSyncs: Int,
    val completedSyncsToday: Int,
    val lastSyncAt: Long?,
    val systemLoad: Double
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
        fun <T> success(data: T, message: String? = null): ApiResponse<T> = 
            ApiResponse(true, data, message)
        
        fun <T> error(message: String): ApiResponse<T> = 
            ApiResponse(false, null, null, message)
    }
}