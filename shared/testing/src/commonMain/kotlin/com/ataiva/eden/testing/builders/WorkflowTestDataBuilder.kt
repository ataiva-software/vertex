package com.ataiva.eden.testing.builders

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Test data builder for Workflow entities
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

class WorkflowTestDataBuilder {
    private var id: String = "workflow-${generateRandomId()}"
    private var name: String = "test-workflow"
    private var description: String? = "Test workflow for development"
    private var definition: Map<String, Any> = createDefaultDefinition()
    private var userId: String = "user-${generateRandomId()}"
    private var status: String = "active"
    private var version: Int = 1
    private var createdAt: Instant = Clock.System.now()
    private var updatedAt: Instant = Clock.System.now()

    fun withId(id: String) = apply { this.id = id }
    fun withName(name: String) = apply { this.name = name }
    fun withDescription(description: String?) = apply { this.description = description }
    fun withDefinition(definition: Map<String, Any>) = apply { this.definition = definition }
    fun withUserId(userId: String) = apply { this.userId = userId }
    fun withStatus(status: String) = apply { this.status = status }
    fun withVersion(version: Int) = apply { this.version = version }
    fun withCreatedAt(createdAt: Instant) = apply { this.createdAt = createdAt }
    fun withUpdatedAt(updatedAt: Instant) = apply { this.updatedAt = updatedAt }

    fun build(): Workflow {
        return Workflow(
            id = id,
            name = name,
            description = description,
            definition = definition,
            userId = userId,
            status = status,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun aWorkflow() = WorkflowTestDataBuilder()
        
        fun deploymentWorkflow() = WorkflowTestDataBuilder()
            .withName("deploy-to-staging")
            .withDescription("Deploy application to staging environment")
            .withDefinition(createDeploymentDefinition())
            
        fun backupWorkflow() = WorkflowTestDataBuilder()
            .withName("backup-database")
            .withDescription("Daily database backup workflow")
            .withDefinition(createBackupDefinition())
            
        fun testWorkflow() = WorkflowTestDataBuilder()
            .withName("run-tests")
            .withDescription("Run automated test suite")
            .withDefinition(createTestDefinition())

        private fun createDefaultDefinition(): Map<String, Any> {
            return mapOf(
                "name" to "test-workflow",
                "description" to "Test workflow",
                "version" to "1.0",
                "steps" to listOf(
                    mapOf(
                        "name" to "test-step",
                        "type" to "shell",
                        "configuration" to mapOf(
                            "command" to "echo 'Hello World'"
                        )
                    )
                )
            )
        }

        private fun createDeploymentDefinition(): Map<String, Any> {
            return mapOf(
                "name" to "deploy-to-staging",
                "description" to "Deploy application to staging environment",
                "version" to "1.0",
                "steps" to listOf(
                    mapOf(
                        "name" to "checkout-code",
                        "type" to "git",
                        "configuration" to mapOf(
                            "repository" to "https://github.com/example/app.git",
                            "branch" to "develop"
                        )
                    ),
                    mapOf(
                        "name" to "run-tests",
                        "type" to "test",
                        "configuration" to mapOf(
                            "command" to "npm test",
                            "timeout" to "300s"
                        )
                    ),
                    mapOf(
                        "name" to "build-image",
                        "type" to "docker",
                        "configuration" to mapOf(
                            "dockerfile" to "Dockerfile",
                            "tag" to "staging-\${BUILD_NUMBER}"
                        )
                    ),
                    mapOf(
                        "name" to "deploy",
                        "type" to "kubernetes",
                        "configuration" to mapOf(
                            "namespace" to "staging",
                            "manifest" to "k8s/staging.yaml"
                        )
                    )
                )
            )
        }

        private fun createBackupDefinition(): Map<String, Any> {
            return mapOf(
                "name" to "backup-database",
                "description" to "Daily database backup workflow",
                "version" to "1.0",
                "steps" to listOf(
                    mapOf(
                        "name" to "create-backup",
                        "type" to "database",
                        "configuration" to mapOf(
                            "type" to "postgresql",
                            "host" to "\${DB_HOST}",
                            "database" to "\${DB_NAME}",
                            "output" to "/backups/db-\${DATE}.sql"
                        )
                    ),
                    mapOf(
                        "name" to "upload-to-s3",
                        "type" to "storage",
                        "configuration" to mapOf(
                            "provider" to "aws-s3",
                            "bucket" to "eden-backups",
                            "path" to "database/\${DATE}/"
                        )
                    )
                )
            )
        }

        private fun createTestDefinition(): Map<String, Any> {
            return mapOf(
                "name" to "run-tests",
                "description" to "Run automated test suite",
                "version" to "1.0",
                "steps" to listOf(
                    mapOf(
                        "name" to "setup-environment",
                        "type" to "shell",
                        "configuration" to mapOf(
                            "command" to "npm install"
                        )
                    ),
                    mapOf(
                        "name" to "unit-tests",
                        "type" to "test",
                        "configuration" to mapOf(
                            "command" to "npm run test:unit",
                            "timeout" to "120s"
                        )
                    ),
                    mapOf(
                        "name" to "integration-tests",
                        "type" to "test",
                        "configuration" to mapOf(
                            "command" to "npm run test:integration",
                            "timeout" to "300s"
                        )
                    )
                )
            )
        }

        private fun generateRandomId(): String {
            return (1..8).map { ('a'..'z').random() }.joinToString("")
        }
    }
}

/**
 * Test data builder for WorkflowExecution entities
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

class WorkflowExecutionTestDataBuilder {
    private var id: String = "execution-${generateRandomId()}"
    private var workflowId: String = "workflow-${generateRandomId()}"
    private var triggeredBy: String? = "user-${generateRandomId()}"
    private var status: String = "pending"
    private var inputData: Map<String, Any>? = mapOf("branch" to "main")
    private var outputData: Map<String, Any>? = null
    private var errorMessage: String? = null
    private var startedAt: Instant = Clock.System.now()
    private var completedAt: Instant? = null
    private var durationMs: Int? = null

    fun withId(id: String) = apply { this.id = id }
    fun withWorkflowId(workflowId: String) = apply { this.workflowId = workflowId }
    fun withTriggeredBy(triggeredBy: String?) = apply { this.triggeredBy = triggeredBy }
    fun withStatus(status: String) = apply { this.status = status }
    fun withInputData(inputData: Map<String, Any>?) = apply { this.inputData = inputData }
    fun withOutputData(outputData: Map<String, Any>?) = apply { this.outputData = outputData }
    fun withErrorMessage(errorMessage: String?) = apply { this.errorMessage = errorMessage }
    fun withStartedAt(startedAt: Instant) = apply { this.startedAt = startedAt }
    fun withCompletedAt(completedAt: Instant?) = apply { this.completedAt = completedAt }
    fun withDurationMs(durationMs: Int?) = apply { this.durationMs = durationMs }

    fun build(): WorkflowExecution {
        return WorkflowExecution(
            id = id,
            workflowId = workflowId,
            triggeredBy = triggeredBy,
            status = status,
            inputData = inputData,
            outputData = outputData,
            errorMessage = errorMessage,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMs = durationMs
        )
    }

    companion object {
        fun aWorkflowExecution() = WorkflowExecutionTestDataBuilder()
        
        fun completedExecution() = WorkflowExecutionTestDataBuilder()
            .withStatus("completed")
            .withOutputData(mapOf("result" to "success", "artifacts" to listOf("build.zip")))
            .withCompletedAt(Clock.System.now())
            .withDurationMs(120000)
            
        fun failedExecution() = WorkflowExecutionTestDataBuilder()
            .withStatus("failed")
            .withErrorMessage("Build failed: compilation error")
            .withCompletedAt(Clock.System.now())
            .withDurationMs(45000)
            
        fun runningExecution() = WorkflowExecutionTestDataBuilder()
            .withStatus("running")

        private fun generateRandomId(): String {
            return (1..8).map { ('a'..'z').random() }.joinToString("")
        }
    }
}

/**
 * Test data builder for WorkflowStep entities
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

class WorkflowStepTestDataBuilder {
    private var id: String = "step-${generateRandomId()}"
    private var executionId: String = "execution-${generateRandomId()}"
    private var stepName: String = "test-step"
    private var stepType: String = "shell"
    private var status: String = "pending"
    private var inputData: Map<String, Any>? = null
    private var outputData: Map<String, Any>? = null
    private var errorMessage: String? = null
    private var startedAt: Instant? = null
    private var completedAt: Instant? = null
    private var durationMs: Int? = null
    private var stepOrder: Int = 1

    fun withId(id: String) = apply { this.id = id }
    fun withExecutionId(executionId: String) = apply { this.executionId = executionId }
    fun withStepName(stepName: String) = apply { this.stepName = stepName }
    fun withStepType(stepType: String) = apply { this.stepType = stepType }
    fun withStatus(status: String) = apply { this.status = status }
    fun withInputData(inputData: Map<String, Any>?) = apply { this.inputData = inputData }
    fun withOutputData(outputData: Map<String, Any>?) = apply { this.outputData = outputData }
    fun withErrorMessage(errorMessage: String?) = apply { this.errorMessage = errorMessage }
    fun withStartedAt(startedAt: Instant?) = apply { this.startedAt = startedAt }
    fun withCompletedAt(completedAt: Instant?) = apply { this.completedAt = completedAt }
    fun withDurationMs(durationMs: Int?) = apply { this.durationMs = durationMs }
    fun withStepOrder(stepOrder: Int) = apply { this.stepOrder = stepOrder }

    fun build(): WorkflowStep {
        return WorkflowStep(
            id = id,
            executionId = executionId,
            stepName = stepName,
            stepType = stepType,
            status = status,
            inputData = inputData,
            outputData = outputData,
            errorMessage = errorMessage,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMs = durationMs,
            stepOrder = stepOrder
        )
    }

    companion object {
        fun aWorkflowStep() = WorkflowStepTestDataBuilder()
        
        fun completedStep() = WorkflowStepTestDataBuilder()
            .withStatus("completed")
            .withStartedAt(Clock.System.now())
            .withCompletedAt(Clock.System.now())
            .withDurationMs(5000)
            
        fun failedStep() = WorkflowStepTestDataBuilder()
            .withStatus("failed")
            .withErrorMessage("Step execution failed")
            .withStartedAt(Clock.System.now())
            .withCompletedAt(Clock.System.now())
            .withDurationMs(2000)

        private fun generateRandomId(): String {
            return (1..8).map { ('a'..'z').random() }.joinToString("")
        }
    }
}