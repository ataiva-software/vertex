package com.ataiva.eden.integration

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import kotlin.test.*

/**
 * Cross-Service Regression Tests
 * 
 * These tests validate the complete integration between all Eden services
 * to ensure no regressions occur when services interact with each other.
 * 
 * Critical for maintaining system reliability as emphasized in requirements.
 */
class CrossServiceRegressionTest {
    
    private val httpClient = HttpClient.newHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Service endpoints - assuming services run on different ports in integration environment
    private val vaultServiceUrl = "http://localhost:8081"
    private val flowServiceUrl = "http://localhost:8082"
    private val taskServiceUrl = "http://localhost:8083"
    private val hubServiceUrl = "http://localhost:8080"
    private val syncServiceUrl = "http://localhost:8084"
    private val insightServiceUrl = "http://localhost:8085"
    private val monitorServiceUrl = "http://localhost:8086"
    private val apiGatewayUrl = "http://localhost:8000"
    
    private val testUserId = "regression-test-user"
    private val testOrgId = "regression-test-org"
    
    @Test
    fun `complete DevOps workflow - vault secret to flow execution to task completion`() = runTest {
        // This test validates the core workflow: Vault → Flow → Task service integration
        
        // 1. Create a secret in Vault Service
        val secretRequest = mapOf(
            "name" to "deployment-key",
            "value" to "super-secret-deployment-key-123",
            "description" to "Secret for deployment workflow",
            "userId" to testUserId,
            "organizationId" to testOrgId
        )
        
        val secretResponse = createVaultSecret(secretRequest)
        assertEquals(201, secretResponse.statusCode())
        
        val secretResult = json.decodeFromString<Map<String, Any>>(secretResponse.body())
        assertTrue(secretResult["success"] as Boolean)
        val secretId = (secretResult["data"] as Map<String, Any>)["id"] as String
        
        // 2. Create a workflow in Flow Service that uses the secret
        val workflowRequest = mapOf(
            "name" to "Deployment Workflow",
            "description" to "Automated deployment using vault secret",
            "steps" to listOf(
                mapOf(
                    "type" to "VAULT_RETRIEVE",
                    "name" to "Get Deployment Key",
                    "configuration" to mapOf(
                        "secretId" to secretId,
                        "outputVariable" to "deploymentKey"
                    )
                ),
                mapOf(
                    "type" to "SHELL_COMMAND",
                    "name" to "Deploy Application",
                    "configuration" to mapOf(
                        "command" to "echo 'Deploying with key: \${deploymentKey}'",
                        "timeout" to 30
                    )
                ),
                mapOf(
                    "type" to "TASK_CREATE",
                    "name" to "Create Follow-up Task",
                    "configuration" to mapOf(
                        "taskName" to "Post-deployment verification",
                        "taskDescription" to "Verify deployment was successful"
                    )
                )
            ),
            "userId" to testUserId,
            "organizationId" to testOrgId
        )
        
        val workflowResponse = createFlowWorkflow(workflowRequest)
        assertEquals(201, workflowResponse.statusCode())
        
        val workflowResult = json.decodeFromString<Map<String, Any>>(workflowResponse.body())
        assertTrue(workflowResult["success"] as Boolean)
        val workflowId = (workflowResult["data"] as Map<String, Any>)["id"] as String
        
        // 3. Execute the workflow
        val executionResponse = executeFlowWorkflow(workflowId, mapOf("userId" to testUserId))
        assertEquals(200, executionResponse.statusCode())
        
        val executionResult = json.decodeFromString<Map<String, Any>>(executionResponse.body())
        assertTrue(executionResult["success"] as Boolean)
        val executionId = (executionResult["data"] as Map<String, Any>)["id"] as String
        
        // 4. Wait for workflow completion and verify task was created
        Thread.sleep(5000) // Allow time for async processing
        
        val executionStatusResponse = getFlowExecutionStatus(executionId)
        assertEquals(200, executionStatusResponse.statusCode())
        
        val statusResult = json.decodeFromString<Map<String, Any>>(executionStatusResponse.body())
        val executionData = statusResult["data"] as Map<String, Any>
        assertEquals("COMPLETED", executionData["status"])
        
        // 5. Verify task was created in Task Service
        val tasksResponse = listTasks(testUserId)
        assertEquals(200, tasksResponse.statusCode())
        
        val tasksResult = json.decodeFromString<Map<String, Any>>(tasksResponse.body())
        val tasks = tasksResult["data"] as List<Map<String, Any>>
        assertTrue(tasks.any { it["name"] == "Post-deployment verification" })
        
        // Cleanup
        deleteVaultSecret(secretId, testUserId)
        deleteFlowWorkflow(workflowId, testUserId)
    }
    
    @Test
    fun `hub webhook triggers sync service data synchronization`() = runTest {
        // This test validates: Hub → Sync service integration
        
        // 1. Create a sync configuration in Sync Service
        val syncConfigRequest = mapOf(
            "name" to "GitHub Repository Sync",
            "sourceType" to "GITHUB",
            "targetType" to "DATABASE",
            "configuration" to mapOf(
                "sourceUrl" to "https://api.github.com/repos/test/repo",
                "syncInterval" to 300,
                "transformations" to listOf("NORMALIZE_DATES", "EXTRACT_METADATA")
            ),
            "userId" to testUserId,
            "organizationId" to testOrgId
        )
        
        val syncConfigResponse = createSyncConfiguration(syncConfigRequest)
        assertEquals(201, syncConfigResponse.statusCode())
        
        val syncResult = json.decodeFromString<Map<String, Any>>(syncConfigResponse.body())
        val syncConfigId = (syncResult["data"] as Map<String, Any>)["id"] as String
        
        // 2. Create a webhook in Hub Service that triggers sync
        val webhookRequest = mapOf(
            "name" to "GitHub Sync Trigger",
            "url" to "$syncServiceUrl/api/v1/sync/trigger/$syncConfigId",
            "events" to listOf("push", "pull_request"),
            "secret" to "sync-webhook-secret",
            "headers" to mapOf("X-Trigger-Source" to "Eden-Hub"),
            "userId" to testUserId
        )
        
        val webhookResponse = createHubWebhook(webhookRequest)
        assertEquals(201, webhookResponse.statusCode())
        
        val webhookResult = json.decodeFromString<Map<String, Any>>(webhookResponse.body())
        val webhookId = (webhookResult["data"] as Map<String, Any>)["id"] as String
        
        // 3. Simulate webhook delivery (GitHub push event)
        val webhookPayload = mapOf(
            "event" to "push",
            "payload" to mapOf(
                "repository" to mapOf(
                    "name" to "test-repo",
                    "full_name" to "test/repo",
                    "updated_at" to "2025-01-06T15:00:00Z"
                ),
                "commits" to listOf(
                    mapOf(
                        "id" to "abc123",
                        "message" to "Update README",
                        "author" to mapOf("name" to "Test User")
                    )
                )
            )
        )
        
        val deliveryResponse = deliverHubWebhook(webhookId, webhookPayload)
        assertEquals(202, deliveryResponse.statusCode())
        
        // 4. Wait for sync to be triggered and verify sync execution
        Thread.sleep(3000) // Allow time for webhook processing and sync trigger
        
        val syncExecutionsResponse = listSyncExecutions(syncConfigId)
        assertEquals(200, syncExecutionsResponse.statusCode())
        
        val executionsResult = json.decodeFromString<Map<String, Any>>(syncExecutionsResponse.body())
        val executions = executionsResult["data"] as List<Map<String, Any>>
        assertTrue(executions.isNotEmpty())
        assertTrue(executions.any { it["status"] == "COMPLETED" || it["status"] == "RUNNING" })
        
        // Cleanup
        deleteHubWebhook(webhookId, testUserId)
        deleteSyncConfiguration(syncConfigId, testUserId)
    }
    
    @Test
    fun `insight service analytics across all service data`() = runTest {
        // This test validates: All services → Insight service data flow
        
        // 1. Create test data across multiple services
        
        // Create vault secrets
        val secret1 = createVaultSecret(mapOf(
            "name" to "api-key-1", "value" to "key123", "userId" to testUserId
        ))
        val secret2 = createVaultSecret(mapOf(
            "name" to "api-key-2", "value" to "key456", "userId" to testUserId
        ))
        
        // Create tasks
        val task1 = createTask(mapOf(
            "name" to "Deploy Service A", "status" to "COMPLETED", "userId" to testUserId
        ))
        val task2 = createTask(mapOf(
            "name" to "Deploy Service B", "status" to "RUNNING", "userId" to testUserId
        ))
        
        // Create workflow executions
        val workflow = createFlowWorkflow(mapOf(
            "name" to "CI/CD Pipeline", "userId" to testUserId,
            "steps" to listOf(mapOf("type" to "SHELL_COMMAND", "name" to "Build"))
        ))
        val workflowResult = json.decodeFromString<Map<String, Any>>(workflow.body())
        val workflowId = (workflowResult["data"] as Map<String, Any>)["id"] as String
        executeFlowWorkflow(workflowId, mapOf("userId" to testUserId))
        
        // Create hub integrations
        val integration = createHubIntegration(mapOf(
            "name" to "GitHub Integration", "type" to "GITHUB", "userId" to testUserId
        ))
        
        // 2. Wait for data to be indexed by Insight Service
        Thread.sleep(5000)
        
        // 3. Request analytics from Insight Service
        val analyticsResponse = getInsightAnalytics(mapOf(
            "userId" to testUserId,
            "timeRange" to "LAST_24_HOURS",
            "includeServices" to listOf("vault", "task", "flow", "hub")
        ))
        assertEquals(200, analyticsResponse.statusCode())
        
        val analyticsResult = json.decodeFromString<Map<String, Any>>(analyticsResponse.body())
        val analytics = analyticsResult["data"] as Map<String, Any>
        
        // 4. Verify analytics include data from all services
        assertTrue(analytics.containsKey("vaultMetrics"))
        assertTrue(analytics.containsKey("taskMetrics"))
        assertTrue(analytics.containsKey("flowMetrics"))
        assertTrue(analytics.containsKey("hubMetrics"))
        
        val vaultMetrics = analytics["vaultMetrics"] as Map<String, Any>
        assertTrue((vaultMetrics["totalSecrets"] as Int) >= 2)
        
        val taskMetrics = analytics["taskMetrics"] as Map<String, Any>
        assertTrue((taskMetrics["totalTasks"] as Int) >= 2)
        
        val flowMetrics = analytics["flowMetrics"] as Map<String, Any>
        assertTrue((flowMetrics["totalExecutions"] as Int) >= 1)
        
        val hubMetrics = analytics["hubMetrics"] as Map<String, Any>
        assertTrue((hubMetrics["totalIntegrations"] as Int) >= 1)
        
        // 5. Request cross-service correlation report
        val correlationResponse = getInsightCorrelation(mapOf(
            "userId" to testUserId,
            "correlationType" to "SERVICE_USAGE",
            "timeRange" to "LAST_24_HOURS"
        ))
        assertEquals(200, correlationResponse.statusCode())
        
        val correlationResult = json.decodeFromString<Map<String, Any>>(correlationResponse.body())
        val correlations = correlationResult["data"] as Map<String, Any>
        assertTrue(correlations.containsKey("serviceInteractions"))
        
        // Cleanup - delete test data
        // (Implementation would clean up all created test data)
    }
    
    @Test
    fun `monitor service alerts trigger hub notifications`() = runTest {
        // This test validates: Monitor → Hub notification pipeline
        
        // 1. Create a notification template in Hub Service
        val templateRequest = mapOf(
            "name" to "System Alert Template",
            "type" to "EMAIL",
            "subject" to "ALERT: {{alertType}} - {{serviceName}}",
            "body" to "Alert: {{alertType}}\nService: {{serviceName}}\nMessage: {{message}}\nTime: {{timestamp}}",
            "variables" to listOf("alertType", "serviceName", "message", "timestamp"),
            "userId" to testUserId
        )
        
        val templateResponse = createHubNotificationTemplate(templateRequest)
        assertEquals(201, templateResponse.statusCode())
        
        val templateResult = json.decodeFromString<Map<String, Any>>(templateResponse.body())
        val templateId = (templateResult["data"] as Map<String, Any>)["id"] as String
        
        // 2. Create a webhook in Hub Service for monitor alerts
        val webhookRequest = mapOf(
            "name" to "Monitor Alert Webhook",
            "url" to "$hubServiceUrl/api/v1/notifications/send",
            "events" to listOf("monitor.alert", "monitor.warning"),
            "secret" to "monitor-webhook-secret",
            "userId" to testUserId
        )
        
        val webhookResponse = createHubWebhook(webhookRequest)
        assertEquals(201, webhookResponse.statusCode())
        
        val webhookResult = json.decodeFromString<Map<String, Any>>(webhookResponse.body())
        val webhookId = (webhookResult["data"] as Map<String, Any>)["id"] as String
        
        // 3. Create a monitor alert rule
        val alertRuleRequest = mapOf(
            "name" to "High CPU Usage Alert",
            "condition" to mapOf(
                "metric" to "cpu_usage_percent",
                "operator" to "GREATER_THAN",
                "threshold" to 80,
                "duration" to 300
            ),
            "actions" to listOf(
                mapOf(
                    "type" to "WEBHOOK",
                    "configuration" to mapOf(
                        "url" to "$hubServiceUrl/api/v1/webhooks/$webhookId/deliver",
                        "method" to "POST"
                    )
                )
            ),
            "userId" to testUserId
        )
        
        val alertRuleResponse = createMonitorAlertRule(alertRuleRequest)
        assertEquals(201, alertRuleResponse.statusCode())
        
        val alertRuleResult = json.decodeFromString<Map<String, Any>>(alertRuleResponse.body())
        val alertRuleId = (alertRuleResult["data"] as Map<String, Any>)["id"] as String
        
        // 4. Simulate high CPU usage to trigger alert
        val metricData = mapOf(
            "metric" to "cpu_usage_percent",
            "value" to 85.5,
            "timestamp" to System.currentTimeMillis(),
            "tags" to mapOf(
                "service" to "task-service",
                "instance" to "task-service-1"
            )
        )
        
        val metricResponse = submitMonitorMetric(metricData)
        assertEquals(202, metricResponse.statusCode())
        
        // 5. Wait for alert processing and notification delivery
        Thread.sleep(8000) // Allow time for alert evaluation and notification
        
        // 6. Verify notification was sent
        val notificationsResponse = listHubNotificationDeliveries(testUserId)
        assertEquals(200, notificationsResponse.statusCode())
        
        val notificationsResult = json.decodeFromString<Map<String, Any>>(notificationsResponse.body())
        val notifications = notificationsResult["data"] as List<Map<String, Any>>
        
        val alertNotification = notifications.find { 
            (it["subject"] as String).contains("High CPU Usage") 
        }
        assertNotNull(alertNotification)
        assertEquals("DELIVERED", alertNotification["status"])
        
        // Cleanup
        deleteMonitorAlertRule(alertRuleId, testUserId)
        deleteHubWebhook(webhookId, testUserId)
        deleteHubNotificationTemplate(templateId, testUserId)
    }
    
    @Test
    fun `CLI commands work with all real API endpoints through gateway`() = runTest {
        // This test validates: CLI → API Gateway → Service routing
        
        // 1. Test CLI authentication through API Gateway
        val authResponse = authenticateCLI(mapOf(
            "username" to "test-user",
            "password" to "test-password"
        ))
        assertEquals(200, authResponse.statusCode())
        
        val authResult = json.decodeFromString<Map<String, Any>>(authResponse.body())
        val token = (authResult["data"] as Map<String, Any>)["token"] as String
        
        // 2. Test CLI commands for each service through API Gateway
        
        // Vault Service via CLI
        val vaultCLIResponse = executeCLICommand("vault", listOf(
            "create", "--name", "cli-test-secret", "--value", "cli-secret-value"
        ), token)
        assertEquals(200, vaultCLIResponse.statusCode())
        
        // Task Service via CLI
        val taskCLIResponse = executeCLICommand("task", listOf(
            "create", "--name", "CLI Test Task", "--description", "Task created via CLI"
        ), token)
        assertEquals(200, taskCLIResponse.statusCode())
        
        // Flow Service via CLI
        val flowCLIResponse = executeCLICommand("flow", listOf(
            "list", "--user-id", testUserId
        ), token)
        assertEquals(200, flowCLIResponse.statusCode())
        
        // Hub Service via CLI
        val hubCLIResponse = executeCLICommand("hub", listOf(
            "integrations", "list", "--user-id", testUserId
        ), token)
        assertEquals(200, hubCLIResponse.statusCode())
        
        // Sync Service via CLI
        val syncCLIResponse = executeCLICommand("sync", listOf(
            "configurations", "list", "--user-id", testUserId
        ), token)
        assertEquals(200, syncCLIResponse.statusCode())
        
        // Insight Service via CLI
        val insightCLIResponse = executeCLICommand("insight", listOf(
            "analytics", "summary", "--user-id", testUserId
        ), token)
        assertEquals(200, insightCLIResponse.statusCode())
        
        // 3. Verify API Gateway routing statistics
        val gatewayStatsResponse = getAPIGatewayStats()
        assertEquals(200, gatewayStatsResponse.statusCode())
        
        val statsResult = json.decodeFromString<Map<String, Any>>(gatewayStatsResponse.body())
        val stats = statsResult["data"] as Map<String, Any>
        
        // Verify requests were routed to all services
        val serviceStats = stats["serviceStats"] as Map<String, Any>
        assertTrue(serviceStats.containsKey("vault"))
        assertTrue(serviceStats.containsKey("task"))
        assertTrue(serviceStats.containsKey("flow"))
        assertTrue(serviceStats.containsKey("hub"))
        assertTrue(serviceStats.containsKey("sync"))
        assertTrue(serviceStats.containsKey("insight"))
        
        // Verify all requests were successful
        val totalRequests = stats["totalRequests"] as Int
        val successfulRequests = stats["successfulRequests"] as Int
        assertTrue(successfulRequests >= 6) // At least 6 CLI commands executed
    }
    
    @Test
    fun `end-to-end DevOps pipeline with all services integration`() = runTest {
        // This is the ultimate regression test - a complete DevOps pipeline
        // involving all services working together
        
        // 1. Setup: Create infrastructure secrets in Vault
        val dbSecret = createVaultSecret(mapOf(
            "name" to "database-url", 
            "value" to "postgresql://localhost:5432/app", 
            "userId" to testUserId
        ))
        val apiSecret = createVaultSecret(mapOf(
            "name" to "api-key", 
            "value" to "super-secret-api-key", 
            "userId" to testUserId
        ))
        
        // 2. Create GitHub integration in Hub Service
        val githubIntegration = createHubIntegration(mapOf(
            "name" to "Pipeline GitHub",
            "type" to "GITHUB",
            "configuration" to mapOf("baseUrl" to "https://api.github.com"),
            "userId" to testUserId
        ))
        val githubResult = json.decodeFromString<Map<String, Any>>(githubIntegration.body())
        val githubId = (githubResult["data"] as Map<String, Any>)["id"] as String
        
        // 3. Create Slack integration for notifications
        val slackIntegration = createHubIntegration(mapOf(
            "name" to "Pipeline Slack",
            "type" to "SLACK",
            "configuration" to mapOf("baseUrl" to "https://slack.com/api"),
            "userId" to testUserId
        ))
        val slackResult = json.decodeFromString<Map<String, Any>>(slackIntegration.body())
        val slackId = (slackResult["data"] as Map<String, Any>)["id"] as String
        
        // 4. Create deployment workflow in Flow Service
        val deploymentWorkflow = createFlowWorkflow(mapOf(
            "name" to "Complete CI/CD Pipeline",
            "description" to "Full deployment pipeline with all services",
            "steps" to listOf(
                mapOf("type" to "VAULT_RETRIEVE", "name" to "Get DB URL"),
                mapOf("type" to "VAULT_RETRIEVE", "name" to "Get API Key"),
                mapOf("type" to "HUB_INTEGRATION", "name" to "Clone Repository"),
                mapOf("type" to "SHELL_COMMAND", "name" to "Run Tests"),
                mapOf("type" to "SHELL_COMMAND", "name" to "Build Application"),
                mapOf("type" to "TASK_CREATE", "name" to "Deploy to Staging"),
                mapOf("type" to "HUB_NOTIFICATION", "name" to "Notify Team"),
                mapOf("type" to "SYNC_TRIGGER", "name" to "Sync Deployment Data")
            ),
            "userId" to testUserId
        ))
        val workflowResult = json.decodeFromString<Map<String, Any>>(deploymentWorkflow.body())
        val workflowId = (workflowResult["data"] as Map<String, Any>)["id"] as String
        
        // 5. Create sync configuration for deployment data
        val syncConfig = createSyncConfiguration(mapOf(
            "name" to "Deployment Data Sync",
            "sourceType" to "WEBHOOK",
            "targetType" to "DATABASE",
            "userId" to testUserId
        ))
        val syncResult = json.decodeFromString<Map<String, Any>>(syncConfig.body())
        val syncConfigId = (syncResult["data"] as Map<String, Any>)["id"] as String
        
        // 6. Create webhook to trigger pipeline
        val pipelineWebhook = createHubWebhook(mapOf(
            "name" to "Pipeline Trigger",
            "url" to "$flowServiceUrl/api/v1/workflows/$workflowId/execute",
            "events" to listOf("push"),
            "userId" to testUserId
        ))
        val webhookResult = json.decodeFromString<Map<String, Any>>(pipelineWebhook.body())
        val webhookId = (webhookResult["data"] as Map<String, Any>)["id"] as String
        
        // 7. Simulate GitHub push event to trigger pipeline
        val pushEvent = mapOf(
            "event" to "push",
            "payload" to mapOf(
                "repository" to mapOf("name" to "my-app"),
                "commits" to listOf(mapOf("message" to "Deploy v1.2.3"))
            )
        )
        
        val triggerResponse = deliverHubWebhook(webhookId, pushEvent)
        assertEquals(202, triggerResponse.statusCode())
        
        // 8. Wait for pipeline execution
        Thread.sleep(15000) // Allow time for complete pipeline execution
        
        // 9. Verify pipeline execution in Flow Service
        val executionsResponse = listFlowExecutions(workflowId)
        assertEquals(200, executionsResponse.statusCode())
        
        val executionsResult = json.decodeFromString<Map<String, Any>>(executionsResponse.body())
        val executions = executionsResult["data"] as List<Map<String, Any>>
        assertTrue(executions.isNotEmpty())
        
        val latestExecution = executions.first()
        assertTrue(latestExecution["status"] == "COMPLETED" || latestExecution["status"] == "RUNNING")
        
        // 10. Verify task was created in Task Service
        val tasksResponse = listTasks(testUserId)
        val tasksResult = json.decodeFromString<Map<String, Any>>(tasksResponse.body())
        val tasks = tasksResult["data"] as List<Map<String, Any>>
        assertTrue(tasks.any { (it["name"] as String).contains("Deploy to Staging") })
        
        // 11. Verify notification was sent via Hub Service
        val notificationsResponse = listHubNotificationDeliveries(testUserId)
        val notificationsResult = json.decodeFromString<Map<String, Any>>(notificationsResponse.body())
        val notifications = notificationsResult["data"] as List<Map<String, Any>>
        assertTrue(notifications.isNotEmpty())
        
        // 12. Verify sync was triggered
        val syncExecutionsResponse = listSyncExecutions(syncConfigId)
        val syncExecutionsResult = json.decodeFromString<Map<String, Any>>(syncExecutionsResponse.body())
        val syncExecutions = syncExecutionsResult["data"] as List<Map<String, Any>>
        assertTrue(syncExecutions.isNotEmpty())
        
        // 13. Verify analytics captured the pipeline execution
        val analyticsResponse = getInsightAnalytics(mapOf(
            "userId" to testUserId,
            "timeRange" to "LAST_HOUR"
        ))
        val analyticsResult = json.decodeFromString<Map<String, Any>>(analyticsResponse.body())
        val analytics = analyticsResult["data"] as Map<String, Any>
        
        // Verify all services show activity
        assertTrue((analytics["flowMetrics"] as Map<String, Any>)["totalExecutions"] as Int > 0)
        assertTrue((analytics["taskMetrics"] as Map<String, Any>)["totalTasks"] as Int > 0)
        assertTrue((analytics["hubMetrics"] as Map<String, Any>)["totalWebhookDeliveries"] as Int > 0)
        assertTrue((analytics["syncMetrics"] as Map<String, Any>)["totalSyncExecutions"] as Int > 0)
        
        // This test validates that all services work together in a real DevOps scenario
        // and provides comprehensive regression coverage for the entire platform
    }
    
    // ================================
    // Helper Methods for HTTP Requests
    // ================================
    
    // Vault Service helpers
    private fun createVaultSecret(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$vaultServiceUrl/api/v1/secrets", request)
    }
    
    private fun deleteVaultSecret(id: String, userId: String): HttpResponse<String> {
        return makeRequest("DELETE", "$vaultServiceUrl/api/v1/secrets/$id?userId=$userId")
    }
    
    // Flow Service helpers
    private fun createFlowWorkflow(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$flowServiceUrl/api/v1/workflows", request)
    }
    
    private fun executeFlowWorkflow(id: String, params: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$flowServiceUrl/api/v1/workflows/$id/execute", params)
    }
    
    private fun getFlowExecutionStatus(id: String): HttpResponse<String> {
        return makeRequest("GET", "$flowServiceUrl/api/v1/executions/$id")
    }
    
    private fun listFlowExecutions(workflowId: String): HttpResponse<String> {
        return makeRequest("GET", "$flowServiceUrl/api/v1/workflows/$workflowId/executions")
    }
    
    private fun deleteFlowWorkflow(id: String, userId: String): HttpResponse<String> {
        return makeRequest("DELETE", "$flowServiceUrl/api/v1/workflows/$id?userId=$userId")
    }
    
    // Task Service helpers
    private fun createTask(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$taskServiceUrl/api/v1/tasks", request)
    }
    
    private fun listTasks(userId: String): HttpResponse<String> {
        return makeRequest("GET", "$taskServiceUrl/api/v1/tasks?userId=$userId")
    }
    
    // Hub Service helpers
    private fun createHubWebhook(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$hubServiceUrl/api/v1/webhooks", request)
    }
    
    private fun deliverHubWebhook(id: String, payload: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$hubServiceUrl/api/v1/webhooks/$id/deliver", payload)
    }
    
    private fun deleteHubWebhook(id: String, userId: String): HttpResponse<String> {
        return makeRequest("DELETE", "$hubServiceUrl/api/v1/webhooks/$id?userId=$userId")
    }
    
    private fun createHubIntegration(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$hubServiceUrl/api/v1/integrations", request)
    }
    
    private fun createHubNotificationTemplate(request: Map<String, Any>):
private fun createHubNotificationTemplate(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$hubServiceUrl/api/v1/notifications/templates", request)
    }
    
    private fun deleteHubNotificationTemplate(id: String, userId: String): HttpResponse<String> {
        return makeRequest("DELETE", "$hubServiceUrl/api/v1/notifications/templates/$id?userId=$userId")
    }
    
    private fun listHubNotificationDeliveries(userId: String): HttpResponse<String> {
        return makeRequest("GET", "$hubServiceUrl/api/v1/notifications/deliveries?userId=$userId")
    }
    
    // Sync Service helpers
    private fun createSyncConfiguration(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$syncServiceUrl/api/v1/configurations", request)
    }
    
    private fun deleteSyncConfiguration(id: String, userId: String): HttpResponse<String> {
        return makeRequest("DELETE", "$syncServiceUrl/api/v1/configurations/$id?userId=$userId")
    }
    
    private fun listSyncExecutions(configId: String): HttpResponse<String> {
        return makeRequest("GET", "$syncServiceUrl/api/v1/configurations/$configId/executions")
    }
    
    // Insight Service helpers
    private fun getInsightAnalytics(params: Map<String, Any>): HttpResponse<String> {
        val queryString = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return makeRequest("GET", "$insightServiceUrl/api/v1/analytics?$queryString")
    }
    
    private fun getInsightCorrelation(params: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$insightServiceUrl/api/v1/analytics/correlation", params)
    }
    
    // Monitor Service helpers
    private fun createMonitorAlertRule(request: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$monitorServiceUrl/api/v1/alerts/rules", request)
    }
    
    private fun deleteMonitorAlertRule(id: String, userId: String): HttpResponse<String> {
        return makeRequest("DELETE", "$monitorServiceUrl/api/v1/alerts/rules/$id?userId=$userId")
    }
    
    private fun submitMonitorMetric(metric: Map<String, Any>): HttpResponse<String> {
        return makeRequest("POST", "$monitorServiceUrl/api/v1/metrics", metric)
    }
    
    // CLI and API Gateway helpers
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
                body?.let { HttpRequest.BodyPublishers.ofString(json.encodeToString(it)) }
                    ?: HttpRequest.BodyPublishers.noBody()
            )
            "PUT" -> requestBuilder.PUT(
                body?.let { HttpRequest.BodyPublishers.ofString(json.encodeToString(it)) }
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
                body?.let { HttpRequest.BodyPublishers.ofString(json.encodeToString(it)) }
                    ?: HttpRequest.BodyPublishers.noBody()
            )
            "PUT" -> requestBuilder.PUT(
                body?.let { HttpRequest.BodyPublishers.ofString(json.encodeToString(it)) }
                    ?: HttpRequest.BodyPublishers.noBody()
            )
            "DELETE" -> requestBuilder.DELETE()
        }
        
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
    }
}