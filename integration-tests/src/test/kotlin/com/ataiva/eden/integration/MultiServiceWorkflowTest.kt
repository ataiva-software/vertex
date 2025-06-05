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
 * Multi-Service Workflow Tests
 * 
 * These tests validate complete end-to-end workflows that span multiple services
 * in the Eden DevOps Suite. They ensure that services can communicate properly
 * and that data flows correctly across service boundaries.
 */
class MultiServiceWorkflowTest {
    
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
    private val apiGatewayUrl = "http://localhost:8000"
    
    private val testUserId = "integration-test-user"
    private val testPassword = "integration-test-password"
    private val testOrganizationId = "integration-test-org"
    
    // Timeout for async operations
    private val defaultTimeout = 5000L // 5 seconds
    
    @Test
    fun `complete user workflow from vault to flow to task to sync`() = runTest {
        // This test validates the complete workflow: Vault → Flow → Task → Sync
        
        // Step 1: Create a secret in Vault Service
        val secretName = "api-key-for-workflow"
        val secretValue = "secret-api-key-${System.currentTimeMillis()}"
        
        val secretRequest = mapOf(
            "name" to secretName,
            "value" to secretValue,
            "type" to "api-key",
            "description" to "API key for workflow test",
            "userId" to testUserId,
            "organizationId" to testOrganizationId,
            "userPassword" to testPassword
        )
        
        val secretResponse = createVaultSecret(secretRequest)
        assertEquals(201, secretResponse.statusCode())
        
        val secretResult = json.decodeFromString<Map<String, Any>>(secretResponse.body())
        assertTrue(secretResult["success"] as Boolean)
        val secretData = secretResult["data"] as Map<String, Any>
        val secretId = secretData["id"] as String
        
        // Step 2: Create a workflow in Flow Service that uses the secret
        val workflowRequest = mapOf(
            "name" to "Multi-Service Test Workflow",
            "description" to "Workflow that spans multiple services",
            "definition" to mapOf(
                "steps" to listOf(
                    mapOf(
                        "type" to "VAULT_RETRIEVE",
                        "name" to "Get API Key",
                        "configuration" to mapOf(
                            "secretName" to secretName,
                            "outputVariable" to "apiKey"
                        )
                    ),
                    mapOf(
                        "type" to "TASK_CREATE",
                        "name" to "Create Data Processing Task",
                        "configuration" to mapOf(
                            "taskName" to "Process Data Task",
                            "taskType" to "data-processing",
                            "taskConfig" to mapOf(
                                "apiKey" to "\${apiKey}",
                                "dataSource" to "test-source"
                            )
                        )
                    ),
                    mapOf(
                        "type" to "SYNC_TRIGGER",
                        "name" to "Trigger Data Sync",
                        "configuration" to mapOf(
                            "sourceName" to "test-source",
                            "destinationName" to "test-destination",
                            "mappingName" to "test-mapping"
                        )
                    )
                )
            ),
            "userId" to testUserId
        )
        
        val workflowResponse = createFlowWorkflow(workflowRequest)
        assertEquals(201, workflowResponse.statusCode())
        
        val workflowResult = json.decodeFromString<Map<String, Any>>(workflowResponse.body())
        assertTrue(workflowResult["success"] as Boolean)
        val workflowData = workflowResult["data"] as Map<String, Any>
        val workflowId = workflowData["id"] as String
        
        // Step 3: Set up data source and destination in Sync Service
        val sourceRequest = mapOf(
            "name" to "test-source",
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
            "name" to "test-destination",
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
        
        // Create mapping
        val mappingRequest = mapOf(
            "name" to "test-mapping",
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
        
        // Create sync job
        val syncJobRequest = mapOf(
            "name" to "test-sync-job",
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
        
        // Step 4: Execute the workflow
        val executionRequest = mapOf(
            "workflowId" to workflowId,
            "userId" to testUserId
        )
        
        val executionResponse = executeFlowWorkflow(executionRequest)
        assertEquals(200, executionResponse.statusCode())
        
        val executionResult = json.decodeFromString<Map<String, Any>>(executionResponse.body())
        assertTrue(executionResult["success"] as Boolean)
        val executionData = executionResult["data"] as Map<String, Any>
        val executionId = executionData["id"] as String
        
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
        assertTrue(tasks.any { it["name"] == "Process Data Task" })
        
        // Find the created task
        val createdTask = tasks.first { it["name"] == "Process Data Task" }
        val taskId = createdTask["id"] as String
        
        // Step 8: Verify sync job was triggered
        val syncExecutionsResponse = listSyncExecutions(syncJobId)
        assertEquals(200, syncExecutionsResponse.statusCode())
        
        val syncExecutionsResult = json.decodeFromString<Map<String, Any>>(syncExecutionsResponse.body())
        val syncExecutions = syncExecutionsResult["data"] as List<Map<String, Any>>
        assertTrue(syncExecutions.isNotEmpty())
        
        // Step 9: Verify data consistency across services
        // Check that the API key from vault was correctly passed to the task
        val taskResponse = getTask(taskId)
        assertEquals(200, taskResponse.statusCode())
        
        val taskResult = json.decodeFromString<Map<String, Any>>(taskResponse.body())
        val taskData = taskResult["data"] as Map<String, Any>
        val taskConfig = taskData["configuration"] as Map<String, Any>
        assertEquals(secretValue, taskConfig["apiKey"])
        
        // Cleanup
        deleteVaultSecret(secretId)
        deleteFlowWorkflow(workflowId)
        deleteTask(taskId)
        deleteSyncJob(syncJobId)
    }
    
    @Test
    fun `service-to-service communication validation test`() = runTest {
        // This test validates the communication between services
        
        // Step 1: Create a secret in Vault Service
        val secretName = "service-comm-test-secret"
        val secretValue = "service-comm-secret-value"
        
        val secretRequest = mapOf(
            "name" to secretName,
            "value" to secretValue,
            "type" to "api-key",
            "description" to "Secret for service communication test",
            "userId" to testUserId,
            "organizationId" to testOrganizationId,
            "userPassword" to testPassword
        )
        
        val secretResponse = createVaultSecret(secretRequest)
        assertEquals(201, secretResponse.statusCode())
        
        val secretResult = json.decodeFromString<Map<String, Any>>(secretResponse.body())
        val secretId = (secretResult["data"] as Map<String, Any>)["id"] as String
        
        // Step 2: Create a workflow that retrieves the secret and passes it to a task
        val workflowRequest = mapOf(
            "name" to "Service Communication Test",
            "description" to "Tests communication between services",
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
                        "name" to "Create Task With Secret",
                        "configuration" to mapOf(
                            "taskName" to "Service Comm Task",
                            "taskType" to "api-call",
                            "taskConfig" to mapOf(
                                "apiKey" to "\${secretValue}",
                                "endpoint" to "https://api.example.com"
                            )
                        )
                    )
                )
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
        
        // Step 6: Verify task was created with the correct secret value
        val tasksResponse = listTasks(testUserId)
        assertEquals(200, tasksResponse.statusCode())
        
        val tasksResult = json.decodeFromString<Map<String, Any>>(tasksResponse.body())
        val tasks = tasksResult["data"] as List<Map<String, Any>>
        assertTrue(tasks.any { it["name"] == "Service Comm Task" })
        
        val createdTask = tasks.first { it["name"] == "Service Comm Task" }
        val taskId = createdTask["id"] as String
        
        val taskResponse = getTask(taskId)
        assertEquals(200, taskResponse.statusCode())
        
        val taskResult = json.decodeFromString<Map<String, Any>>(taskResponse.body())
        val taskData = taskResult["data"] as Map<String, Any>
        val taskConfig = taskData["configuration"] as Map<String, Any>
        assertEquals(secretValue, taskConfig["apiKey"])
        
        // Cleanup
        deleteVaultSecret(secretId)
        deleteFlowWorkflow(workflowId)
        deleteTask(taskId)
    }
    
    @Test
    fun `data consistency across service boundaries test`() = runTest {
        // This test validates data consistency across service boundaries
        
        // Step 1: Create a unique identifier for this test
        val testId = "consistency-test-${System.currentTimeMillis()}"
        
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
        
        // Step 3: Create a workflow with the same test ID
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
        
        // Step 7: Verify task was created with the correct test ID
        val tasksResponse = listTasks(testUserId)
        assertEquals(200, tasksResponse.statusCode())
        
        val tasksResult = json.decodeFromString<Map<String, Any>>(tasksResponse.body())
        val tasks = tasksResult["data"] as List<Map<String, Any>>
        assertTrue(tasks.any { it["name"] == "$testId-task" })
        
        val createdTask = tasks.first { it["name"] == "$testId-task" }
        val taskId = createdTask["id"] as String
        
        // Step 8: Query Insight Service for cross-service correlation
        val correlationRequest = mapOf(
            "userId" to testUserId,
            "correlationType" to "CUSTOM",
            "query" to "testId:$testId"
        )
        
        val correlationResponse = getInsightCorrelation(correlationRequest)
        assertEquals(200, correlationResponse.statusCode())
        
        val correlationResult = json.decodeFromString<Map<String, Any>>(correlationResponse.body())
        val correlationData = correlationResult["data"] as Map<String, Any>
        
        // Step 9: Verify data consistency across services
        // Check that all services have the same test ID
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
        
        // Cleanup
        deleteVaultSecret(secretId)
        deleteFlowWorkflow(workflowId)
        deleteTask(taskId)
    }
    
    @Test
    fun `api gateway routing test for all services`() = runTest {
        // This test validates API Gateway routing for all services
        
        // Step 1: Authenticate through API Gateway
        val authRequest = mapOf(
            "username" to "test-user",
            "password" to "test-password"
        )
        
        val authResponse = authenticateCLI(authRequest)
        assertEquals(200, authResponse.statusCode())
        
        val authResult = json.decodeFromString<Map<String, Any>>(authResponse.body())
        val token = (authResult["data"] as Map<String, Any>)["token"] as String
        
        // Step 2: Test API Gateway routing to Vault Service
        val vaultRequest = executeCLICommand("vault", listOf(
            "list", "--user-id", testUserId
        ), token)
        assertEquals(200, vaultRequest.statusCode())
        
        // Step 3: Test API Gateway routing to Flow Service
        val flowRequest = executeCLICommand("flow", listOf(
            "list", "--user-id", testUserId
        ), token)
        assertEquals(200, flowRequest.statusCode())
        
        // Step 4: Test API Gateway routing to Task Service
        val taskRequest = executeCLICommand("task", listOf(
            "list", "--user-id", testUserId
        ), token)
        assertEquals(200, taskRequest.statusCode())
        
        // Step 5: Test API Gateway routing to Sync Service
        val syncRequest = executeCLICommand("sync", listOf(
            "jobs", "list", "--user-id", testUserId
        ), token)
        assertEquals(200, syncRequest.statusCode())
        
        // Step 6: Test API Gateway routing to Insight Service
        val insightRequest = executeCLICommand("insight", listOf(
            "analytics", "summary", "--user-id", testUserId
        ), token)
        assertEquals(200, insightRequest.statusCode())
        
        // Step 7: Verify API Gateway routing statistics
        val gatewayStatsResponse = getAPIGatewayStats()
        assertEquals(200, gatewayStatsResponse.statusCode())
        
        val statsResult = json.decodeFromString<Map<String, Any>>(gatewayStatsResponse.body())
        val stats = statsResult["data"] as Map<String, Any>
        
        // Verify requests were routed to all services
        val serviceStats = stats["serviceStats"] as Map<String, Any>
        assertTrue(serviceStats.containsKey("vault"))
        assertTrue(serviceStats.containsKey("flow"))
        assertTrue(serviceStats.containsKey("task"))
        assertTrue(serviceStats.containsKey("sync"))
        assertTrue(serviceStats.containsKey("insight"))
        
        // Verify all requests were successful
        val totalRequests = stats["totalRequests"] as Int
        val successfulRequests = stats["successfulRequests"] as Int
        assertTrue(successfulRequests >= 5) // At least 5 CLI commands executed
    }
    
    // Helper methods for Vault Service
    private fun createVaultSecret(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$vaultServiceUrl/api/v1/secrets", request)
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
    
    private fun createSyncDestination(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$syncServiceUrl/api/v1/destinations", request)
    }
    
    private fun createSyncMapping(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$syncServiceUrl/api/v1/mappings", request)
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
    
    // Helper methods for API Gateway
    private fun authenticateCLI(credentials: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$apiGatewayUrl/auth/login", credentials)
    }
    
    private fun executeCLICommand(service: String, args: List<String>, token: String): HttpResponse<String> {
        val request = mapOf(
            "service" to service,
            "command" to args.joinToString(" "),
            "args" to args
        )
        return makeRequestWithAuth("POST", "$apiGatewayUrl/cli/execute", request, token)
    }
    
    private fun getAPIGatewayStats(): HttpResponse<String> {
        return makeRequest("GET", "$apiGatewayUrl/stats")
    }
    
    // Generic HTTP request helpers
    private fun makeRequest(method: String, url: String, body: Map<String, Any>? = null): HttpResponse<String> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
        
        when (method) {
            "GET" -> requestBuilder.GET()
            "POST" -> requestBuilder.POST(
                body?.let { HttpRequest.BodyPublishers.ofString(json.encodeToString(body)) }
                    ?: HttpRequest.BodyPublishers.noBody()
            )
            "PUT" -> requestBuilder.PUT(
                body?.let { HttpRequest.BodyPublishers.ofString(json.encodeToString(body)) }
                    ?: HttpRequest.BodyPublishers.noBody()
            )
            "DELETE" -> requestBuilder.DELETE()
        }
        
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
    }
    
    private fun makeRequestWithAuth(method: String, url: String, body: Map<String, Any>? = null, token: String): HttpResponse<String> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
        
        when (method) {
            "GET" -> requestBuilder.GET()
            "POST" -> requestBuilder.POST(
                body?.let { HttpRequest.BodyPublishers.ofString(json.encodeToString(body)) }
                    ?: HttpRequest.BodyPublishers.noBody()
            )
            "PUT" -> requestBuilder.PUT(
                body?.let { HttpRequest.BodyPublishers.ofString(json.encodeToString(body)) }
                    ?: HttpRequest.BodyPublishers.noBody()
            )
            "DELETE" -> requestBuilder.DELETE()
        }
        
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
    }
}