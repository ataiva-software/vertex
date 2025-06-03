package com.ataiva.eden.testing.builders

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Test data builder for Task entities
 */
data class Task(
    val id: String,
    val name: String,
    val description: String?,
    val taskType: String,
    val configuration: Map<String, Any>,
    val scheduleCron: String?,
    val userId: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

class TaskTestDataBuilder {
    private var id: String = "task-${generateRandomId()}"
    private var name: String = "test-task"
    private var description: String? = "Test task for development"
    private var taskType: String = "shell"
    private var configuration: Map<String, Any> = mapOf("command" to "echo 'Hello World'")
    private var scheduleCron: String? = null
    private var userId: String = "user-${generateRandomId()}"
    private var isActive: Boolean = true
    private var createdAt: Instant = Clock.System.now()
    private var updatedAt: Instant = Clock.System.now()

    fun withId(id: String) = apply { this.id = id }
    fun withName(name: String) = apply { this.name = name }
    fun withDescription(description: String?) = apply { this.description = description }
    fun withTaskType(taskType: String) = apply { this.taskType = taskType }
    fun withConfiguration(configuration: Map<String, Any>) = apply { this.configuration = configuration }
    fun withScheduleCron(scheduleCron: String?) = apply { this.scheduleCron = scheduleCron }
    fun withUserId(userId: String) = apply { this.userId = userId }
    fun withIsActive(isActive: Boolean) = apply { this.isActive = isActive }
    fun withCreatedAt(createdAt: Instant) = apply { this.createdAt = createdAt }
    fun withUpdatedAt(updatedAt: Instant) = apply { this.updatedAt = updatedAt }

    fun build(): Task {
        return Task(
            id = id,
            name = name,
            description = description,
            taskType = taskType,
            configuration = configuration,
            scheduleCron = scheduleCron,
            userId = userId,
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun aTask() = TaskTestDataBuilder()
        
        fun httpCheckTask() = TaskTestDataBuilder()
            .withName("health-check-api")
            .withDescription("Monitor API health endpoints")
            .withTaskType("http_check")
            .withConfiguration(mapOf(
                "url" to "https://api.eden.local/health",
                "method" to "GET",
                "expected_status" to 200,
                "timeout" to 30,
                "retry_count" to 3
            ))
            .withScheduleCron("*/5 * * * *")
            
        fun fileCleanupTask() = TaskTestDataBuilder()
            .withName("cleanup-temp-files")
            .withDescription("Clean up temporary files older than 7 days")
            .withTaskType("file_cleanup")
            .withConfiguration(mapOf(
                "path" to "/tmp/eden",
                "pattern" to "*.tmp",
                "max_age_days" to 7,
                "recursive" to true
            ))
            .withScheduleCron("0 2 * * *")
            
        fun dataSyncTask() = TaskTestDataBuilder()
            .withName("sync-user-data")
            .withDescription("Synchronize user data with external systems")
            .withTaskType("data_sync")
            .withConfiguration(mapOf(
                "source" to "ldap://company.local",
                "destination" to "database",
                "mapping" to mapOf(
                    "email" to "mail",
                    "name" to "displayName",
                    "department" to "department"
                )
            ))
            .withScheduleCron("0 1 * * *")
            
        fun backupTask() = TaskTestDataBuilder()
            .withName("database-backup")
            .withDescription("Create database backup")
            .withTaskType("backup")
            .withConfiguration(mapOf(
                "type" to "postgresql",
                "host" to "localhost",
                "database" to "eden_prod",
                "output_path" to "/backups/",
                "compression" to "gzip"
            ))
            .withScheduleCron("0 3 * * *")

        private fun generateRandomId(): String {
            return (1..8).map { ('a'..'z').random() }.joinToString("")
        }
    }
}

/**
 * Test data builder for TaskExecution entities
 */
data class TaskExecution(
    val id: String,
    val taskId: String,
    val status: String,
    val priority: Int,
    val inputData: Map<String, Any>?,
    val outputData: Map<String, Any>?,
    val errorMessage: String?,
    val progressPercentage: Int,
    val queuedAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val durationMs: Int?
)

class TaskExecutionTestDataBuilder {
    private var id: String = "execution-${generateRandomId()}"
    private var taskId: String = "task-${generateRandomId()}"
    private var status: String = "queued"
    private var priority: Int = 0
    private var inputData: Map<String, Any>? = null
    private var outputData: Map<String, Any>? = null
    private var errorMessage: String? = null
    private var progressPercentage: Int = 0
    private var queuedAt: Instant = Clock.System.now()
    private var startedAt: Instant? = null
    private var completedAt: Instant? = null
    private var durationMs: Int? = null

    fun withId(id: String) = apply { this.id = id }
    fun withTaskId(taskId: String) = apply { this.taskId = taskId }
    fun withStatus(status: String) = apply { this.status = status }
    fun withPriority(priority: Int) = apply { this.priority = priority }
    fun withInputData(inputData: Map<String, Any>?) = apply { this.inputData = inputData }
    fun withOutputData(outputData: Map<String, Any>?) = apply { this.outputData = outputData }
    fun withErrorMessage(errorMessage: String?) = apply { this.errorMessage = errorMessage }
    fun withProgressPercentage(progressPercentage: Int) = apply { this.progressPercentage = progressPercentage }
    fun withQueuedAt(queuedAt: Instant) = apply { this.queuedAt = queuedAt }
    fun withStartedAt(startedAt: Instant?) = apply { this.startedAt = startedAt }
    fun withCompletedAt(completedAt: Instant?) = apply { this.completedAt = completedAt }
    fun withDurationMs(durationMs: Int?) = apply { this.durationMs = durationMs }

    fun build(): TaskExecution {
        return TaskExecution(
            id = id,
            taskId = taskId,
            status = status,
            priority = priority,
            inputData = inputData,
            outputData = outputData,
            errorMessage = errorMessage,
            progressPercentage = progressPercentage,
            queuedAt = queuedAt,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMs = durationMs
        )
    }

    companion object {
        fun aTaskExecution() = TaskExecutionTestDataBuilder()
        
        fun queuedExecution() = TaskExecutionTestDataBuilder()
            .withStatus("queued")
            .withPriority(1)
            
        fun runningExecution() = TaskExecutionTestDataBuilder()
            .withStatus("running")
            .withStartedAt(Clock.System.now())
            .withProgressPercentage(50)
            
        fun completedExecution() = TaskExecutionTestDataBuilder()
            .withStatus("completed")
            .withStartedAt(Clock.System.now())
            .withCompletedAt(Clock.System.now())
            .withProgressPercentage(100)
            .withDurationMs(30000)
            .withOutputData(mapOf(
                "result" to "success",
                "processed_items" to 150,
                "output_file" to "/tmp/result.txt"
            ))
            
        fun failedExecution() = TaskExecutionTestDataBuilder()
            .withStatus("failed")
            .withStartedAt(Clock.System.now())
            .withCompletedAt(Clock.System.now())
            .withProgressPercentage(25)
            .withDurationMs(10000)
            .withErrorMessage("Connection timeout after 30 seconds")
            
        fun cancelledExecution() = TaskExecutionTestDataBuilder()
            .withStatus("cancelled")
            .withStartedAt(Clock.System.now())
            .withCompletedAt(Clock.System.now())
            .withProgressPercentage(75)
            .withDurationMs(45000)
            
        fun highPriorityExecution() = TaskExecutionTestDataBuilder()
            .withStatus("queued")
            .withPriority(10)

        private fun generateRandomId(): String {
            return (1..8).map { ('a'..'z').random() }.joinToString("")
        }
    }
}

/**
 * Test data builder for SystemEvent entities
 */
data class SystemEvent(
    val id: String,
    val eventType: String,
    val sourceService: String,
    val eventData: Map<String, Any>,
    val severity: String,
    val userId: String?,
    val createdAt: Instant
)

class SystemEventTestDataBuilder {
    private var id: String = "event-${generateRandomId()}"
    private var eventType: String = "test_event"
    private var sourceService: String = "test-service"
    private var eventData: Map<String, Any> = mapOf("message" to "Test event")
    private var severity: String = "info"
    private var userId: String? = null
    private var createdAt: Instant = Clock.System.now()

    fun withId(id: String) = apply { this.id = id }
    fun withEventType(eventType: String) = apply { this.eventType = eventType }
    fun withSourceService(sourceService: String) = apply { this.sourceService = sourceService }
    fun withEventData(eventData: Map<String, Any>) = apply { this.eventData = eventData }
    fun withSeverity(severity: String) = apply { this.severity = severity }
    fun withUserId(userId: String?) = apply { this.userId = userId }
    fun withCreatedAt(createdAt: Instant) = apply { this.createdAt = createdAt }

    fun build(): SystemEvent {
        return SystemEvent(
            id = id,
            eventType = eventType,
            sourceService = sourceService,
            eventData = eventData,
            severity = severity,
            userId = userId,
            createdAt = createdAt
        )
    }

    companion object {
        fun aSystemEvent() = SystemEventTestDataBuilder()
        
        fun userLoginEvent() = SystemEventTestDataBuilder()
            .withEventType("user_login")
            .withSourceService("api-gateway")
            .withEventData(mapOf(
                "ip" to "192.168.1.100",
                "user_agent" to "Eden CLI/1.0"
            ))
            .withSeverity("info")
            
        fun secretAccessedEvent() = SystemEventTestDataBuilder()
            .withEventType("secret_accessed")
            .withSourceService("vault")
            .withEventData(mapOf(
                "secret_name" to "database-password",
                "action" to "read"
            ))
            .withSeverity("info")
            
        fun workflowFailedEvent() = SystemEventTestDataBuilder()
            .withEventType("workflow_failed")
            .withSourceService("flow")
            .withEventData(mapOf(
                "workflow_id" to "workflow-123",
                "error" to "Connection timeout"
            ))
            .withSeverity("error")
            
        fun taskCompletedEvent() = SystemEventTestDataBuilder()
            .withEventType("task_completed")
            .withSourceService("task")
            .withEventData(mapOf(
                "task_id" to "task-456",
                "duration_ms" to 30000,
                "result" to "success"
            ))
            .withSeverity("info")
            
        fun criticalErrorEvent() = SystemEventTestDataBuilder()
            .withEventType("system_error")
            .withSourceService("monitor")
            .withEventData(mapOf(
                "error" to "Database connection lost",
                "stack_trace" to "java.sql.SQLException: Connection refused"
            ))
            .withSeverity("critical")

        private fun generateRandomId(): String {
            return (1..8).map { ('a'..'z').random() }.joinToString("")
        }
    }
}

/**
 * Test data builder for AuditLog entities
 */
data class AuditLog(
    val id: String,
    val userId: String?,
    val organizationId: String?,
    val action: String,
    val resource: String,
    val resourceId: String?,
    val details: Map<String, Any>,
    val ipAddress: String?,
    val userAgent: String?,
    val timestamp: Instant,
    val severity: String
)

class AuditLogTestDataBuilder {
    private var id: String = "audit-${generateRandomId()}"
    private var userId: String? = "user-${generateRandomId()}"
    private var organizationId: String? = null
    private var action: String = "READ"
    private var resource: String = "test_resource"
    private var resourceId: String? = null
    private var details: Map<String, Any> = mapOf("action" to "test")
    private var ipAddress: String? = "192.168.1.100"
    private var userAgent: String? = "Eden CLI/1.0"
    private var timestamp: Instant = Clock.System.now()
    private var severity: String = "INFO"

    fun withId(id: String) = apply { this.id = id }
    fun withUserId(userId: String?) = apply { this.userId = userId }
    fun withOrganizationId(organizationId: String?) = apply { this.organizationId = organizationId }
    fun withAction(action: String) = apply { this.action = action }
    fun withResource(resource: String) = apply { this.resource = resource }
    fun withResourceId(resourceId: String?) = apply { this.resourceId = resourceId }
    fun withDetails(details: Map<String, Any>) = apply { this.details = details }
    fun withIpAddress(ipAddress: String?) = apply { this.ipAddress = ipAddress }
    fun withUserAgent(userAgent: String?) = apply { this.userAgent = userAgent }
    fun withTimestamp(timestamp: Instant) = apply { this.timestamp = timestamp }
    fun withSeverity(severity: String) = apply { this.severity = severity }

    fun build(): AuditLog {
        return AuditLog(
            id = id,
            userId = userId,
            organizationId = organizationId,
            action = action,
            resource = resource,
            resourceId = resourceId,
            details = details,
            ipAddress = ipAddress,
            userAgent = userAgent,
            timestamp = timestamp,
            severity = severity
        )
    }

    companion object {
        fun anAuditLog() = AuditLogTestDataBuilder()
        
        fun secretCreatedLog() = AuditLogTestDataBuilder()
            .withAction("CREATE")
            .withResource("secret")
            .withDetails(mapOf(
                "secret_name" to "database-password",
                "secret_type" to "database"
            ))
            
        fun workflowExecutedLog() = AuditLogTestDataBuilder()
            .withAction("EXECUTE")
            .withResource("workflow")
            .withDetails(mapOf(
                "workflow_name" to "deploy-to-staging",
                "execution_id" to "execution-123"
            ))
            
        fun secretAccessedLog() = AuditLogTestDataBuilder()
            .withAction("READ")
            .withResource("secret")
            .withDetails(mapOf(
                "secret_name" to "api-key-github",
                "access_method" to "api"
            ))

        private fun generateRandomId(): String {
            return (1..8).map { ('a'..'z').random() }.joinToString("")
        }
    }
}