package com.ataiva.eden.integration

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import kotlin.test.*
import kotlinx.datetime.Clock

/**
 * Data Consistency Tests
 * 
 * These tests validate that data remains consistent when flowing between different services
 * in the Eden DevOps Suite. They ensure that data integrity is maintained across service
 * boundaries and that all services have a consistent view of the data.
 */
class DataConsistencyTest {
    
    private val httpClient = HttpClient.newHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Service endpoints
    private val vaultServiceUrl = "http://localhost:8081"
    private val flowServiceUrl = "http://localhost:8082"
    private val taskServiceUrl = "http://localhost:8083"
    private val syncServiceUrl = "http://localhost:8084"
    private val insightServiceUrl = "http://localhost:8085"
    
    private val testUserId = "integration-test-user"
    private val testPassword = "integration-test-password"
    private val testOrganizationId = "integration-test-org"
    
    // Timeout for async operations
    private val defaultTimeout = 5000L // 5 seconds
    
    @Test
    fun `data consistency between vault and flow services`() = runTest {
        // This test validates data consistency between Vault and Flow services
        
        // Step 1: Create a unique identifier for this test
        val testId = "vault-flow-consistency-${System.currentTimeMillis()}"
        
        // Step 2: Create a secret in Vault Service
        val secretName = "$testId-secret"
        val secretValue = "$testId-value"
        
        val secretRequest = mapOf(
            "name" to secretName,
            "value" to secretValue,
            "type" to "api-key",
            "description" to "Secret for data consistency test",
            "userId" to testUserId,
            "organizationId" to testOrganizationId,
            "userPassword" to testPassword,
            "metadata" to mapOf("testId" to testId)
        )
        
        val secretResponse = createVaultSecret(secretRequest)
        assertEquals(201, secretResponse.statusCode())
        
        val secretResult = json.decodeFromString<Map<String, Any>>(secretResponse.body())
        val secretId = (secretResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 3: Create a workflow that uses the secret
        val workflowRequest = mapOf(
            "name" to "$testId-workflow",
            "description" to "Workflow for data consistency test",
            "definition" to mapOf(
                "steps" to listOf(
                    mapOf(
                        "type" to "VAULT_RETRIEVE",
                        "name" to "Get Secret",
                        "configuration" to mapOf(
                            "secretName" to secretName,
                            "outputVariable" to "secretValue"
                        )
                    ),
                    mapOf(
                        "type" to "LOG",
                        "name" to "Log Secret Value",
                        "configuration" to mapOf(
                            "message" to "Secret value: \${secretValue}"
                        )
                    )
                ),
                "metadata" to mapOf("testId" to testId)
            ),
            "userId" to testUserId
        )
        
        val workflowResponse = createFlowWorkflow(workflowRequest)
        assertEquals(201, workflowResponse.statusCode())
        
        val workflowResult = json.decodeFromString<Map<String, Any>>(workflowResponse.body())
        val workflowId = (workflowResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 4: Execute the workflow
        val executionRequest = mapOf(
            "workflowId" to workflowId,
            "userId" to testUserId
        )
        
        val executionResponse = executeFlowWorkflow(executionRequest)
        assertEquals(200, executionResponse.statusCode())
        
        val executionResult = json.decodeFromString<Map<String, Any>>(executionResponse.body())
        val executionId = (executionResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 5: Wait for workflow completion
        Thread.sleep(defaultTimeout)
        
        // Step 6: Verify workflow execution completed successfully
        val executionStatusResponse = getFlowExecutionStatus(executionId)
        assertEquals(200, executionStatusResponse.statusCode())
        
        val statusResult = json.decodeFromString<Map<String, Any>>(executionStatusResponse.body())
        val executionStatus = (statusResult["data"] as Map<String, Any>)["status"] as String
        assertEquals("COMPLETED", executionStatus)
        
        // Step 7: Verify the workflow output contains the correct secret value
        val executionDetailsResponse = getFlowExecutionDetails(executionId)
        assertEquals(200, executionDetailsResponse.statusCode())
        
        val detailsResult = json.decodeFromString<Map<String, Any>>(executionDetailsResponse.body())
        val outputData = (detailsResult["data"] as Map<String, Any>)["outputData"] as Map<String, Any>
        assertEquals(secretValue, outputData["secretValue"])
        
        // Step 8: Update the secret in Vault
        val updatedSecretValue = "$testId-updated-value"
        val updateRequest = mapOf(
            "name" to secretName,
            "newValue" to updatedSecretValue,
            "userId" to testUserId,
            "userPassword" to testPassword
        )
        
        val updateResponse = updateVaultSecret(updateRequest)
        assertEquals(200, updateResponse.statusCode())
        
        // Step 9: Execute the workflow again
        val secondExecutionResponse = executeFlowWorkflow(executionRequest)
        assertEquals(200, secondExecutionResponse.statusCode())
        
        val secondExecutionResult = json.decodeFromString<Map<String, Any>>(secondExecutionResponse.body())
        val secondExecutionId = (secondExecutionResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 10: Wait for workflow completion
        Thread.sleep(defaultTimeout)
        
        // Step 11: Verify the workflow output contains the updated secret value
        val secondExecutionDetailsResponse = getFlowExecutionDetails(secondExecutionId)
        assertEquals(200, secondExecutionDetailsResponse.statusCode())
        
        val secondDetailsResult = json.decodeFromString<Map<String, Any>>(secondExecutionDetailsResponse.body())
        val secondOutputData = (secondDetailsResult["data"] as Map<String, Any>)["outputData"] as Map<String, Any>
        assertEquals(updatedSecretValue, secondOutputData["secretValue"])
        
        // Cleanup
        deleteVaultSecret(secretId)
        deleteFlowWorkflow(workflowId)
    }
    
    @Test
    fun `data consistency between flow and task services`() = runTest {
        // This test validates data consistency between Flow and Task services
        
        // Step 1: Create a unique identifier for this test
        val testId = "flow-task-consistency-${System.currentTimeMillis()}"
        
        // Step 2: Create a workflow that creates a task
        val workflowRequest = mapOf(
            "name" to "$testId-workflow",
            "description" to "Workflow for data consistency test",
            "definition" to mapOf(
                "steps" to listOf(
                    mapOf(
                        "type" to "TASK_CREATE",
                        "name" to "Create Task",
                        "configuration" to mapOf(
                            "taskName" to "$testId-task",
                            "taskType" to "data-processing",
                            "taskConfig" to mapOf(
                                "testId" to testId,
                                "dataSource" to "test-source",
                                "processingType" to "test-processing"
                            )
                        )
                    )
                ),
                "metadata" to mapOf("testId" to testId)
            ),
            "userId" to testUserId
        )
        
        val workflowResponse = createFlowWorkflow(workflowRequest)
        assertEquals(201, workflowResponse.statusCode())
        
        val workflowResult = json.decodeFromString<Map<String, Any>>(workflowResponse.body())
        val workflowId = (workflowResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 3: Execute the workflow
        val executionRequest = mapOf(
            "workflowId" to workflowId,
            "userId" to testUserId
        )
        
        val executionResponse = executeFlowWorkflow(executionRequest)
        assertEquals(200, executionResponse.statusCode())
        
        val executionResult = json.decodeFromString<Map<String, Any>>(executionResponse.body())
        val executionId = (executionResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 4: Wait for workflow completion
        Thread.sleep(defaultTimeout)
        
        // Step 5: Verify workflow execution completed successfully
        val executionStatusResponse = getFlowExecutionStatus(executionId)
        assertEquals(200, executionStatusResponse.statusCode())
        
        val statusResult = json.decodeFromString<Map<String, Any>>(executionStatusResponse.body())
        val executionStatus = (statusResult["data"] as Map<String, Any>)["status"] as String
        assertEquals("COMPLETED", executionStatus)
        
        // Step 6: Verify task was created in Task Service with correct data
        val tasksResponse = listTasks(testUserId)
        assertEquals(200, tasksResponse.statusCode())
        
        val tasksResult = json.decodeFromString<Map<String, Any>>(tasksResponse.body())
        val tasks = tasksResult["data"] as List<Map<String, Any>>
        assertTrue(tasks.any { it["name"] == "$testId-task" })
        
        val createdTask = tasks.first { it["name"] == "$testId-task" }
        val taskId = createdTask["id"] as String
        
        val taskResponse = getTask(taskId)
        assertEquals(200, taskResponse.statusCode())
        
        val taskResult = json.decodeFromString<Map<String, Any>>(taskResponse.body())
        val taskData = taskResult["data"] as Map<String, Any>
        val taskConfig = taskData["configuration"] as Map<String, Any>
        
        assertEquals(testId, taskConfig["testId"])
        assertEquals("test-source", taskConfig["dataSource"])
        assertEquals("test-processing", taskConfig["processingType"])
        
        // Step 7: Update the task in Task Service
        val updateTaskRequest = mapOf(
            "taskId" to taskId,
            "configuration" to mapOf(
                "testId" to testId,
                "dataSource" to "updated-source",
                "processingType" to "updated-processing"
            ),
            "userId" to testUserId
        )
        
        val updateTaskResponse = updateTask(updateTaskRequest)
        assertEquals(200, updateTaskResponse.statusCode())
        
        // Step 8: Verify the task was updated
        val updatedTaskResponse = getTask(taskId)
        assertEquals(200, updatedTaskResponse.statusCode())
        
        val updatedTaskResult = json.decodeFromString<Map<String, Any>>(updatedTaskResponse.body())
        val updatedTaskData = updatedTaskResult["data"] as Map<String, Any>
        val updatedTaskConfig = updatedTaskData["configuration"] as Map<String, Any>
        
        assertEquals(testId, updatedTaskConfig["testId"])
        assertEquals("updated-source", updatedTaskConfig["dataSource"])
        assertEquals("updated-processing", updatedTaskConfig["processingType"])
        
        // Cleanup
        deleteFlowWorkflow(workflowId)
        deleteTask(taskId)
    }
    
    @Test
    fun `data consistency between task and sync services`() = runTest {
        // This test validates data consistency between Task and Sync services
        
        // Step 1: Create a unique identifier for this test
        val testId = "task-sync-consistency-${System.currentTimeMillis()}"
        
        // Step 2: Set up data source and destination in Sync Service
        val sourceRequest = mapOf(
            "name" to "$testId-source",
            "type" to "DATABASE",
            "connectionConfig" to mapOf(
                "host" to "localhost",
                "port" to 5432,
                "database" to "test_db",
                "username" to "test_user",
                "password" to "test_password"
            ),
            "userId" to testUserId
        )
        
        val sourceResponse = createSyncDataSource(sourceRequest)
        assertEquals(201, sourceResponse.statusCode())
        
        val sourceResult = json.decodeFromString<Map<String, Any>>(sourceResponse.body())
        val sourceId = (sourceResult["data"] as Map<String, Any>)["id"] as String
        
        val destinationRequest = mapOf(
            "name" to "$testId-destination",
            "type" to "DATABASE",
            "connectionConfig" to mapOf(
                "host" to "localhost",
                "port" to 5432,
                "database" to "dest_db",
                "username" to "dest_user",
                "password" to "dest_password"
            ),
            "userId" to testUserId
        )
        
        val destinationResponse = createSyncDestination(destinationRequest)
        assertEquals(201, destinationResponse.statusCode())
        
        val destinationResult = json.decodeFromString<Map<String, Any>>(destinationResponse.body())
        val destinationId = (destinationResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 3: Create mapping
        val mappingRequest = mapOf(
            "name" to "$testId-mapping",
            "sourceSchema" to mapOf(
                "fields" to listOf(
                    mapOf("name" to "id", "type" to "INTEGER", "primaryKey" to true),
                    mapOf("name" to "name", "type" to "STRING"),
                    mapOf("name" to "value", "type" to "STRING")
                )
            ),
            "destinationSchema" to mapOf(
                "fields" to listOf(
                    mapOf("name" to "id", "type" to "INTEGER", "primaryKey" to true),
                    mapOf("name" to "name", "type" to "STRING"),
                    mapOf("name" to "value", "type" to "STRING")
                )
            ),
            "fieldMappings" to listOf(
                mapOf("sourceField" to "id", "destinationField" to "id"),
                mapOf("sourceField" to "name", "destinationField" to "name"),
                mapOf("sourceField" to "value", "destinationField" to "value")
            ),
            "userId" to testUserId
        )
        
        val mappingResponse = createSyncMapping(mappingRequest)
        assertEquals(201, mappingResponse.statusCode())
        
        val mappingResult = json.decodeFromString<Map<String, Any>>(mappingResponse.body())
        val mappingId = (mappingResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 4: Create sync job
        val syncJobRequest = mapOf(
            "name" to "$testId-sync-job",
            "sourceId" to sourceId,
            "destinationId" to destinationId,
            "mappingId" to mappingId,
            "schedule" to mapOf(
                "type" to "MANUAL",
                "enabled" to true
            ),
            "configuration" to mapOf(
                "batchSize" to 100,
                "maxRetries" to 3,
                "timeoutSeconds" to 300,
                "conflictResolution" to "SOURCE_WINS",
                "enableValidation" to true
            ),
            "userId" to testUserId
        )
        
        val syncJobResponse = createSyncJob(syncJobRequest)
        assertEquals(201, syncJobResponse.statusCode())
        
        val syncJobResult = json.decodeFromString<Map<String, Any>>(syncJobResponse.body())
        val syncJobId = (syncJobResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 5: Create a task that triggers the sync job
        val taskRequest = mapOf(
            "name" to "$testId-task",
            "description" to "Task for data consistency test",
            "taskType" to "sync-trigger",
            "configuration" to mapOf(
                "syncJobId" to syncJobId,
                "testId" to testId
            ),
            "userId" to testUserId
        )
        
        val taskResponse = createTask(taskRequest)
        assertEquals(201, taskResponse.statusCode())
        
        val taskResult = json.decodeFromString<Map<String, Any>>(taskResponse.body())
        val taskId = (taskResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 6: Execute the task
        val executeTaskRequest = mapOf(
            "taskId" to taskId,
            "userId" to testUserId
        )
        
        val executeTaskResponse = executeTask(executeTaskRequest)
        assertEquals(200, executeTaskResponse.statusCode())
        
        val executeTaskResult = json.decodeFromString<Map<String, Any>>(executeTaskResponse.body())
        val executionId = (executeTaskResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 7: Wait for task execution to complete
        Thread.sleep(defaultTimeout)
        
        // Step 8: Verify task execution completed successfully
        val taskExecutionResponse = getTaskExecution(executionId)
        assertEquals(200, taskExecutionResponse.statusCode())
        
        val taskExecutionResult = json.decodeFromString<Map<String, Any>>(taskExecutionResponse.body())
        val taskExecutionStatus = (taskExecutionResult["data"] as Map<String, Any>)["status"] as String
        assertEquals("COMPLETED", taskExecutionStatus)
        
        // Step 9: Verify sync job was triggered
        val syncExecutionsResponse = listSyncExecutions(syncJobId)
        assertEquals(200, syncExecutionsResponse.statusCode())
        
        val syncExecutionsResult = json.decodeFromString<Map<String, Any>>(syncExecutionsResponse.body())
        val syncExecutions = syncExecutionsResult["data"] as List<Map<String, Any>>
        assertTrue(syncExecutions.isNotEmpty())
        
        // Cleanup
        deleteTask(taskId)
        deleteSyncJob(syncJobId)
        deleteSyncMapping(mappingId)
        deleteSyncDataSource(sourceId)
        deleteSyncDestination(destinationId)
    }
    
    @Test
    fun `data consistency across all services with insight analytics`() = runTest {
        // This test validates data consistency across all services with Insight analytics
        
        // Step 1: Create a unique identifier for this test
        val testId = "all-services-consistency-${System.currentTimeMillis()}"
        
        // Step 2: Create a secret in Vault Service
        val secretName = "$testId-secret"
        val secretValue = "$testId-value"
        
        val secretRequest = mapOf(
            "name" to secretName,
            "value" to secretValue,
            "type" to "api-key",
            "description" to "Secret for data consistency test",
            "userId" to testUserId,
            "organizationId" to testOrganizationId,
            "userPassword" to testPassword,
            "metadata" to mapOf("testId" to testId)
        )
        
        val secretResponse = createVaultSecret(secretRequest)
        assertEquals(201, secretResponse.statusCode())
        
        val secretResult = json.decodeFromString<Map<String, Any>>(secretResponse.body())
        val secretId = (secretResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 3: Create a workflow in Flow Service
        val workflowRequest = mapOf(
            "name" to "$testId-workflow",
            "description" to "Workflow for data consistency test",
            "definition" to mapOf(
                "steps" to listOf(
                    mapOf(
                        "type" to "VAULT_RETRIEVE",
                        "name" to "Get Secret",
                        "configuration" to mapOf(
                            "secretName" to secretName,
                            "outputVariable" to "secretValue"
                        )
                    ),
                    mapOf(
                        "type" to "TASK_CREATE",
                        "name" to "Create Task",
                        "configuration" to mapOf(
                            "taskName" to "$testId-task",
                            "taskType" to "data-processing",
                            "taskConfig" to mapOf(
                                "apiKey" to "\${secretValue}",
                                "testId" to testId
                            )
                        )
                    )
                ),
                "metadata" to mapOf("testId" to testId)
            ),
            "userId" to testUserId
        )
        
        val workflowResponse = createFlowWorkflow(workflowRequest)
        assertEquals(201, workflowResponse.statusCode())
        
        val workflowResult = json.decodeFromString<Map<String, Any>>(workflowResponse.body())
        val workflowId = (workflowResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 4: Execute the workflow
        val executionRequest = mapOf(
            "workflowId" to workflowId,
            "userId" to testUserId
        )
        
        val executionResponse = executeFlowWorkflow(executionRequest)
        assertEquals(200, executionResponse.statusCode())
        
        val executionResult = json.decodeFromString<Map<String, Any>>(executionResponse.body())
        val executionId = (executionResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 5: Wait for workflow completion
        Thread.sleep(defaultTimeout)
        
        // Step 6: Verify workflow execution completed successfully
        val executionStatusResponse = getFlowExecutionStatus(executionId)
        assertEquals(200, executionStatusResponse.statusCode())
        
        val statusResult = json.decodeFromString<Map<String, Any>>(executionStatusResponse.body())
        val executionStatus = (statusResult["data"] as Map<String, Any>)["status"] as String
        assertEquals("COMPLETED", executionStatus)
        
        // Step 7: Verify task was created in Task Service
        val tasksResponse = listTasks(testUserId)
        assertEquals(200, tasksResponse.statusCode())
        
        val tasksResult = json.decodeFromString<Map<String, Any>>(tasksResponse.body())
        val tasks = tasksResult["data"] as List<Map<String, Any>>
        assertTrue(tasks.any { it["name"] == "$testId-task" })
        
        val createdTask = tasks.first { it["name"] == "$testId-task" }
        val taskId = createdTask["id"] as String
        
        // Step 8: Wait for data to be indexed by Insight Service
        Thread.sleep(defaultTimeout)
        
        // Step 9: Query Insight Service for cross-service correlation
        val correlationRequest = mapOf(
            "userId" to testUserId,
            "correlationType" to "CUSTOM",
            "query" to "testId:$testId"
        )
        
        val correlationResponse = getInsightCorrelation(correlationRequest)
        assertEquals(200, correlationResponse.statusCode())
        
        val correlationResult = json.decodeFromString<Map<String, Any>>(correlationResponse.body())
        val correlationData = correlationResult["data"] as Map<String, Any>
        
        // Step 10: Verify data consistency across all services
        assertTrue(correlationData.containsKey("secrets"))
        assertTrue(correlationData.containsKey("workflows"))
        assertTrue(correlationData.containsKey("tasks"))
        
        val secrets = correlationData["secrets"] as List<Map<String, Any>>
        val workflows = correlationData["workflows"] as List<Map<String, Any>>
        val tasks = correlationData["tasks"] as List<Map<String, Any>>
        
        assertEquals(1, secrets.size)
        assertEquals(1, workflows.size)
        assertEquals(1, tasks.size)
        
        assertEquals(secretName, secrets[0]["name"])
        assertEquals("$testId-workflow", workflows[0]["name"])
        assertEquals("$testId-task", tasks[0]["name"])
        
        // Step 11: Get analytics from Insight Service
        val analyticsRequest = mapOf(
            "userId" to testUserId,
            "timeRange" to "LAST_HOUR",
            "filter" to "testId:$testId"
        )
        
        val analyticsResponse = getInsightAnalytics(analyticsRequest)
        assertEquals(200, analyticsResponse.statusCode())
        
        val analyticsResult = json.decodeFromString<Map<String, Any>>(analyticsResponse.body())
        val analytics = analyticsResult["data"] as Map<String, Any>
        
        // Step 12: Verify analytics data is consistent with operations performed
        assertTrue(analytics.containsKey("vaultMetrics"))
        assertTrue(analytics.containsKey("flowMetrics"))
        assertTrue(analytics.containsKey("taskMetrics"))
        
        val vaultMetrics = analytics["vaultMetrics"] as Map<String, Any>
        val flowMetrics = analytics["flowMetrics"] as Map<String, Any>
        val taskMetrics = analytics["taskMetrics"] as Map<String, Any>
        
        assertTrue((vaultMetrics["secretsCreated"] as Int) >= 1)
        assertTrue((flowMetrics["workflowsCreated"] as Int) >= 1)
        assertTrue((flowMetrics["workflowsExecuted"] as Int) >= 1)
        assertTrue((taskMetrics["tasksCreated"] as Int) >= 1)
        
        // Cleanup
        deleteVaultSecret(secretId)
        deleteFlowWorkflow(workflowId)
        deleteTask(taskId)
    }
    
    // Helper methods for Vault Service
    private fun createVaultSecret(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$vaultServiceUrl/api/v1/secrets", request)
    }
    
    private fun updateVaultSecret(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("PUT", "$vaultServiceUrl/api/v1/secrets/${request["name"]}", request)
    }
    
    private fun deleteVaultSecret(id: String): HttpResponse<String> {
        return makeRequest("DELETE", "$vaultServiceUrl/api/v1/secrets/$id")
    }
    
    // Helper methods for Flow Service
    private fun createFlowWorkflow(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$flowServiceUrl/api/v1/workflows", request)
    }
    
    private fun executeFlowWorkflow(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$flowServiceUrl/api/v1/workflows/${request["workflowId"]}/execute", request)
    }
    
    private fun getFlowExecutionStatus(id: String): HttpResponse<String> {
        return makeRequest("GET", "$flowServiceUrl/api/v1/executions/$id")
    }
    
    private fun getFlowExecutionDetails(id: String): HttpResponse<String> {
        return makeRequest("GET", "$flowServiceUrl/api/v1/executions/$id/details")
    }
    
    private fun deleteFlowWorkflow(id: String): HttpResponse<String> {
        return makeRequest("DELETE", "$flowServiceUrl/api/v1/workflows/$id")
    }
    
    // Helper methods for Task Service
    private fun createTask(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$taskServiceUrl/api/v1/tasks", request)
    }
    
    private fun getTask(id: String): HttpResponse<String> {
        return makeRequest("GET", "$taskServiceUrl/api/v1/tasks/$id")
    }
    
    private fun updateTask(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("PUT", "$taskServiceUrl/api/v1/tasks/${request["taskId"]}", request)
    }
    
    private fun executeTask(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$taskServiceUrl/api/v1/tasks/${request["taskId"]}/execute", request)
    }
    
    private fun getTaskExecution(id: String): HttpResponse<String> {
        return makeRequest("GET", "$taskServiceUrl/api/v1/executions/$id")
    }
    
    private fun listTasks(userId: String): HttpResponse<String> {
        return makeRequest("GET", "$taskServiceUrl/api/v1/tasks?userId=$userId")
    }
    
    private fun deleteTask(id: String): HttpResponse<String> {
        return makeRequest("DELETE", "$taskServiceUrl/api/v1/tasks/$id")
    }
    
    // Helper methods for Sync Service
    private fun createSyncDataSource(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$syncServiceUrl/api/v1/sources", request)
    }
    
    private fun deleteSyncDataSource(id: String): HttpResponse<String> {
        return makeRequest("DELETE", "$syncServiceUrl/api/v1/sources/$id")
    }
    
    private fun createSyncDestination(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$syncServiceUrl/api/v1/destinations", request)
    }
    
    private fun deleteSyncDestination(id: String): HttpResponse<String> {
        return makeRequest("DELETE", "$syncServiceUrl/api/v1/destinations/$id")
    }
    
    private fun createSyncMapping(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$syncServiceUrl/api/v1/mappings", request)
    }
    
    private fun deleteSyncMapping(id: String): HttpResponse<String> {
        return makeRequest("DELETE", "$syncServiceUrl/api/v1/mappings/$id")
    }
    
    private fun createSyncJob(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$syncServiceUrl/api/v1/jobs", request)
    }
    
    private fun listSyncExecutions(jobId: String): HttpResponse<String> {
        return makeRequest("GET", "$syncServiceUrl/api/v1/jobs/$jobId/executions")
    }
    
    private fun deleteSyncJob(id: String): HttpResponse<String> {
        return makeRequest("DELETE", "$syncServiceUrl/api/v1/jobs/$id")
    }
    
    // Helper methods for Insight Service
    private fun getInsightCorrelation(params: Map<String, Any>): HttpResponse<String> {
        val queryString = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return makeRequest("GET", "$insightServiceUrl/api/v1/correlation?$queryString")
    }
    
    private fun getInsightAnalytics(params: Map<String, Any>): HttpResponse<String> {
        val queryString = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return makeRequest("GET", "$insightServiceUrl/api/v1/analytics?$queryString")
    }
    
    // Generic HTTP request helpers
    private fun makeRequest(method: String, url: String, body: Any? = null): HttpResponse<String> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
        
        when (method) {
            "GET" -> requestBuilder.GET()
            "POST" -> requestBuilder.POST(
                when (body) {
                    is Map<*, *> -> HttpRequest.BodyPublishers.ofString(json.encodeToString(body))
                    is String -> HttpRequest.BodyPublishers.ofString(body)
                    else -> HttpRequest.BodyPublishers.noBody()
                }
            )
            "PUT" -> requestBuilder.PUT(
                when (body) {
                    is Map<*, *> -> HttpRequest.BodyPublishers.ofString(json.encodeToString(body))
                    is String -> HttpRequest.BodyPublishers.ofString(body)
                    else -> HttpRequest.BodyPublishers.noBody()
                }
            )
            "DELETE" -> requestBuilder.DELETE()
        }
        
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
    }
}