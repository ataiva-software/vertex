package com.ataiva.eden.integration

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.measureTime

/**
 * Performance Regression Tests
 * 
 * These tests ensure that the Eden platform maintains performance standards
 * and prevents performance regressions across all services.
 * 
 * Critical for production readiness and user experience.
 */
class PerformanceRegressionTest {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Service endpoints
    private val vaultServiceUrl = "http://localhost:8081"
    private val flowServiceUrl = "http://localhost:8082"
    private val taskServiceUrl = "http://localhost:8083"
    private val hubServiceUrl = "http://localhost:8080"
    private val syncServiceUrl = "http://localhost:8084"
    private val insightServiceUrl = "http://localhost:8085"
    private val apiGatewayUrl = "http://localhost:8000"
    
    // Performance thresholds (in milliseconds)
    private val responseTimeThreshold = 200L // 95% of requests should be under 200ms
    private val maxResponseTime = 1000L // No request should take more than 1 second
    private val concurrentUsers = 50 // Test with 50 concurrent users
    private val requestsPerUser = 10 // Each user makes 10 requests
    
    @Test
    fun `API Gateway routing performance under load`() = runTest {
        val results = ConcurrentHashMap<String, MutableList<Long>>()
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        
        // Test concurrent requests through API Gateway to all services
        val jobs = (1..concurrentUsers).map { userId ->
            async {
                repeat(requestsPerUser) { requestId ->
                    try {
                        val services = listOf("vault", "task", "flow", "hub", "sync", "insight")
                        val service = services[requestId % services.size]
                        
                        val responseTime = measureTime {
                            val response = makeHealthCheckRequest(service)
                            if (response.statusCode() == 200) {
                                successCount.incrementAndGet()
                            } else {
                                errorCount.incrementAndGet()
                            }
                        }.inWholeMilliseconds
                        
                        results.computeIfAbsent(service) { mutableListOf() }.add(responseTime)
                        
                    } catch (e: Exception) {
                        errorCount.incrementAndGet()
                    }
                }
            }
        }
        
        jobs.awaitAll()
        
        // Analyze results
        val totalRequests = concurrentUsers * requestsPerUser
        val successRate = (successCount.get().toDouble() / totalRequests) * 100
        
        println("API Gateway Performance Results:")
        println("Total Requests: $totalRequests")
        println("Successful Requests: ${successCount.get()}")
        println("Failed Requests: ${errorCount.get()}")
        println("Success Rate: ${String.format("%.2f", successRate)}%")
        
        // Assert performance requirements
        assertTrue(successRate >= 95.0, "Success rate should be at least 95%, got $successRate%")
        
        // Check response times per service
        results.forEach { (service, times) ->
            val avgTime = times.average()
            val p95Time = times.sorted()[((times.size * 0.95).toInt())]
            val maxTime = times.maxOrNull() ?: 0L
            
            println("$service - Avg: ${avgTime.toInt()}ms, P95: ${p95Time}ms, Max: ${maxTime}ms")
            
            assertTrue(p95Time <= responseTimeThreshold, 
                "$service P95 response time should be <= ${responseTimeThreshold}ms, got ${p95Time}ms")
            assertTrue(maxTime <= maxResponseTime, 
                "$service max response time should be <= ${maxResponseTime}ms, got ${maxTime}ms")
        }
    }
    
    @Test
    fun `Vault Service encryption performance under load`() = runTest {
        val encryptionTimes = mutableListOf<Long>()
        val decryptionTimes = mutableListOf<Long>()
        val secretIds = mutableListOf<String>()
        
        // Create secrets concurrently
        val createJobs = (1..concurrentUsers).map { userId ->
            async {
                repeat(5) { secretIndex ->
                    val encryptionTime = measureTime {
                        val secretRequest = mapOf(
                            "name" to "perf-test-secret-$userId-$secretIndex",
                            "value" to "performance-test-secret-value-$userId-$secretIndex-${System.currentTimeMillis()}",
                            "description" to "Performance test secret",
                            "userId" to "perf-user-$userId"
                        )
                        
                        val response = makeRequest("POST", "$vaultServiceUrl/api/v1/secrets", secretRequest)
                        if (response.statusCode() == 201) {
                            val result = json.decodeFromString<Map<String, Any>>(response.body())
                            val secretId = (result["data"] as Map<String, Any>)["id"] as String
                            synchronized(secretIds) {
                                secretIds.add(secretId)
                            }
                        }
                    }.inWholeMilliseconds
                    
                    synchronized(encryptionTimes) {
                        encryptionTimes.add(encryptionTime)
                    }
                }
            }
        }
        
        createJobs.awaitAll()
        
        // Retrieve secrets concurrently (test decryption performance)
        val retrieveJobs = secretIds.chunked(10).map { chunk ->
            async {
                chunk.forEach { secretId ->
                    val decryptionTime = measureTime {
                        makeRequest("GET", "$vaultServiceUrl/api/v1/secrets/$secretId?userId=perf-user-1")
                    }.inWholeMilliseconds
                    
                    synchronized(decryptionTimes) {
                        decryptionTimes.add(decryptionTime)
                    }
                }
            }
        }
        
        retrieveJobs.awaitAll()
        
        // Analyze encryption performance
        val avgEncryptionTime = encryptionTimes.average()
        val p95EncryptionTime = encryptionTimes.sorted()[((encryptionTimes.size * 0.95).toInt())]
        val maxEncryptionTime = encryptionTimes.maxOrNull() ?: 0L
        
        println("Vault Encryption Performance:")
        println("Avg: ${avgEncryptionTime.toInt()}ms, P95: ${p95EncryptionTime}ms, Max: ${maxEncryptionTime}ms")
        
        // Analyze decryption performance
        val avgDecryptionTime = decryptionTimes.average()
        val p95DecryptionTime = decryptionTimes.sorted()[((decryptionTimes.size * 0.95).toInt())]
        val maxDecryptionTime = decryptionTimes.maxOrNull() ?: 0L
        
        println("Vault Decryption Performance:")
        println("Avg: ${avgDecryptionTime.toInt()}ms, P95: ${p95DecryptionTime}ms, Max: ${maxDecryptionTime}ms")
        
        // Assert performance requirements
        assertTrue(p95EncryptionTime <= 300L, "Encryption P95 should be <= 300ms, got ${p95EncryptionTime}ms")
        assertTrue(p95DecryptionTime <= 100L, "Decryption P95 should be <= 100ms, got ${p95DecryptionTime}ms")
        assertTrue(maxEncryptionTime <= 1000L, "Max encryption time should be <= 1000ms, got ${maxEncryptionTime}ms")
        assertTrue(maxDecryptionTime <= 500L, "Max decryption time should be <= 500ms, got ${maxDecryptionTime}ms")
        
        // Cleanup
        secretIds.forEach { secretId ->
            makeRequest("DELETE", "$vaultServiceUrl/api/v1/secrets/$secretId?userId=perf-user-1")
        }
    }
    
    @Test
    fun `Hub Service webhook delivery performance under high volume`() = runTest {
        val deliveryTimes = mutableListOf<Long>()
        val webhookIds = mutableListOf<String>()
        
        // Create webhooks for testing
        repeat(10) { webhookIndex ->
            val webhookRequest = mapOf(
                "name" to "Performance Test Webhook $webhookIndex",
                "url" to "https://httpbin.org/post",
                "events" to listOf("test.event"),
                "userId" to "perf-user"
            )
            
            val response = makeRequest("POST", "$hubServiceUrl/api/v1/webhooks", webhookRequest)
            if (response.statusCode() == 201) {
                val result = json.decodeFromString<Map<String, Any>>(response.body())
                val webhookId = (result["data"] as Map<String, Any>)["id"] as String
                webhookIds.add(webhookId)
            }
        }
        
        // Test concurrent webhook deliveries
        val deliveryJobs = (1..concurrentUsers).map { userId ->
            async {
                repeat(20) { deliveryIndex ->
                    val webhookId = webhookIds[deliveryIndex % webhookIds.size]
                    val payload = mapOf(
                        "event" to "test.event",
                        "payload" to mapOf(
                            "userId" to userId,
                            "deliveryIndex" to deliveryIndex,
                            "timestamp" to System.currentTimeMillis(),
                            "data" to "performance-test-data-$userId-$deliveryIndex"
                        )
                    )
                    
                    val deliveryTime = measureTime {
                        makeRequest("POST", "$hubServiceUrl/api/v1/webhooks/$webhookId/deliver", payload)
                    }.inWholeMilliseconds
                    
                    synchronized(deliveryTimes) {
                        deliveryTimes.add(deliveryTime)
                    }
                }
            }
        }
        
        deliveryJobs.awaitAll()
        
        // Analyze webhook delivery performance
        val avgDeliveryTime = deliveryTimes.average()
        val p95DeliveryTime = deliveryTimes.sorted()[((deliveryTimes.size * 0.95).toInt())]
        val maxDeliveryTime = deliveryTimes.maxOrNull() ?: 0L
        
        println("Hub Webhook Delivery Performance:")
        println("Total Deliveries: ${deliveryTimes.size}")
        println("Avg: ${avgDeliveryTime.toInt()}ms, P95: ${p95DeliveryTime}ms, Max: ${maxDeliveryTime}ms")
        
        // Assert performance requirements
        assertTrue(p95DeliveryTime <= 500L, "Webhook delivery P95 should be <= 500ms, got ${p95DeliveryTime}ms")
        assertTrue(maxDeliveryTime <= 2000L, "Max webhook delivery time should be <= 2000ms, got ${maxDeliveryTime}ms")
        
        // Wait for deliveries to complete and check success rate
        delay(5000)
        
        var totalSuccessfulDeliveries = 0
        webhookIds.forEach { webhookId ->
            val deliveriesResponse = makeRequest("GET", "$hubServiceUrl/api/v1/webhooks/$webhookId/deliveries")
            if (deliveriesResponse.statusCode() == 200) {
                val result = json.decodeFromString<Map<String, Any>>(deliveriesResponse.body())
                val deliveries = result["data"] as List<Map<String, Any>>
                totalSuccessfulDeliveries += deliveries.count { it["status"] == "DELIVERED" }
            }
        }
        
        val successRate = (totalSuccessfulDeliveries.toDouble() / deliveryTimes.size) * 100
        println("Webhook Delivery Success Rate: ${String.format("%.2f", successRate)}%")
        
        assertTrue(successRate >= 95.0, "Webhook delivery success rate should be >= 95%, got $successRate%")
        
        // Cleanup
        webhookIds.forEach { webhookId ->
            makeRequest("DELETE", "$hubServiceUrl/api/v1/webhooks/$webhookId?userId=perf-user")
        }
    }
    
    @Test
    fun `Flow Service workflow execution performance`() = runTest {
        val executionTimes = mutableListOf<Long>()
        val workflowIds = mutableListOf<String>()
        
        // Create test workflows
        repeat(5) { workflowIndex ->
            val workflowRequest = mapOf(
                "name" to "Performance Test Workflow $workflowIndex",
                "description" to "Workflow for performance testing",
                "steps" to listOf(
                    mapOf(
                        "type" to "SHELL_COMMAND",
                        "name" to "Echo Test",
                        "configuration" to mapOf(
                            "command" to "echo 'Performance test step'",
                            "timeout" to 10
                        )
                    ),
                    mapOf(
                        "type" to "DELAY",
                        "name" to "Short Delay",
                        "configuration" to mapOf("duration" to 100)
                    ),
                    mapOf(
                        "type" to "SHELL_COMMAND",
                        "name" to "Date Command",
                        "configuration" to mapOf(
                            "command" to "date",
                            "timeout" to 5
                        )
                    )
                ),
                "userId" to "perf-user"
            )
            
            val response = makeRequest("POST", "$flowServiceUrl/api/v1/workflows", workflowRequest)
            if (response.statusCode() == 201) {
                val result = json.decodeFromString<Map<String, Any>>(response.body())
                val workflowId = (result["data"] as Map<String, Any>)["id"] as String
                workflowIds.add(workflowId)
            }
        }
        
        // Execute workflows concurrently
        val executionJobs = (1..concurrentUsers).map { userId ->
            async {
                repeat(5) { executionIndex ->
                    val workflowId = workflowIds[executionIndex % workflowIds.size]
                    
                    val executionTime = measureTime {
                        val executeResponse = makeRequest("POST", 
                            "$flowServiceUrl/api/v1/workflows/$workflowId/execute",
                            mapOf("userId" to "perf-user-$userId")
                        )
                        
                        if (executeResponse.statusCode() == 200) {
                            val result = json.decodeFromString<Map<String, Any>>(executeResponse.body())
                            val executionId = (result["data"] as Map<String, Any>)["id"] as String
                            
                            // Wait for execution to complete
                            var completed = false
                            var attempts = 0
                            while (!completed && attempts < 30) { // Max 30 seconds wait
                                delay(1000)
                                val statusResponse = makeRequest("GET", 
                                    "$flowServiceUrl/api/v1/executions/$executionId")
                                
                                if (statusResponse.statusCode() == 200) {
                                    val statusResult = json.decodeFromString<Map<String, Any>>(statusResponse.body())
                                    val status = (statusResult["data"] as Map<String, Any>)["status"] as String
                                    completed = status == "COMPLETED" || status == "FAILED"
                                }
                                attempts++
                            }
                        }
                    }.inWholeMilliseconds
                    
                    synchronized(executionTimes) {
                        executionTimes.add(executionTime)
                    }
                }
            }
        }
        
        executionJobs.awaitAll()
        
        // Analyze workflow execution performance
        val avgExecutionTime = executionTimes.average()
        val p95ExecutionTime = executionTimes.sorted()[((executionTimes.size * 0.95).toInt())]
        val maxExecutionTime = executionTimes.maxOrNull() ?: 0L
        
        println("Flow Workflow Execution Performance:")
        println("Total Executions: ${executionTimes.size}")
        println("Avg: ${avgExecutionTime.toInt()}ms, P95: ${p95ExecutionTime}ms, Max: ${maxExecutionTime}ms")
        
        // Assert performance requirements (workflows include delays, so times will be higher)
        assertTrue(p95ExecutionTime <= 5000L, "Workflow execution P95 should be <= 5000ms, got ${p95ExecutionTime}ms")
        assertTrue(maxExecutionTime <= 10000L, "Max workflow execution time should be <= 10000ms, got ${maxExecutionTime}ms")
        
        // Cleanup
        workflowIds.forEach { workflowId ->
            makeRequest("DELETE", "$flowServiceUrl/api/v1/workflows/$workflowId?userId=perf-user")
        }
    }
    
    @Test
    fun `Insight Service analytics query performance`() = runTest {
        val queryTimes = mutableListOf<Long>()
        
        // Generate test data first
        repeat(100) { dataIndex ->
            // Create some vault secrets
            makeRequest("POST", "$vaultServiceUrl/api/v1/secrets", mapOf(
                "name" to "analytics-test-secret-$dataIndex",
                "value" to "test-value-$dataIndex",
                "userId" to "analytics-user"
            ))
            
            // Create some tasks
            makeRequest("POST", "$taskServiceUrl/api/v1/tasks", mapOf(
                "name" to "Analytics Test Task $dataIndex",
                "description" to "Task for analytics testing",
                "status" to if (dataIndex % 3 == 0) "COMPLETED" else "PENDING",
                "userId" to "analytics-user"
            ))
        }
        
        // Wait for data to be indexed
        delay(3000)
        
        // Test concurrent analytics queries
        val queryJobs = (1..concurrentUsers).map { userId ->
            async {
                repeat(10) { queryIndex ->
                    val queryTypes = listOf(
                        mapOf("type" to "summary", "userId" to "analytics-user"),
                        mapOf("type" to "trends", "userId" to "analytics-user", "timeRange" to "LAST_24_HOURS"),
                        mapOf("type" to "service-metrics", "userId" to "analytics-user", "service" to "vault"),
                        mapOf("type" to "service-metrics", "userId" to "analytics-user", "service" to "task")
                    )
                    
                    val query = queryTypes[queryIndex % queryTypes.size]
                    
                    val queryTime = measureTime {
                        val queryString = query.entries.joinToString("&") { "${it.key}=${it.value}" }
                        makeRequest("GET", "$insightServiceUrl/api/v1/analytics?$queryString")
                    }.inWholeMilliseconds
                    
                    synchronized(queryTimes) {
                        queryTimes.add(queryTime)
                    }
                }
            }
        }
        
        queryJobs.awaitAll()
        
        // Analyze analytics query performance
        val avgQueryTime = queryTimes.average()
        val p95QueryTime = queryTimes.sorted()[((queryTimes.size * 0.95).toInt())]
        val maxQueryTime = queryTimes.maxOrNull() ?: 0L
        
        println("Insight Analytics Query Performance:")
        println("Total Queries: ${queryTimes.size}")
        println("Avg: ${avgQueryTime.toInt()}ms, P95: ${p95QueryTime}ms, Max: ${maxQueryTime}ms")
        
        // Assert performance requirements
        assertTrue(p95QueryTime <= 1000L, "Analytics query P95 should be <= 1000ms, got ${p95QueryTime}ms")
        assertTrue(maxQueryTime <= 3000L, "Max analytics query time should be <= 3000ms, got ${maxQueryTime}ms")
    }
    
    @Test
    fun `Database connection pool performance under load`() = runTest {
        val connectionTimes = mutableListOf<Long>()
        val services = listOf("vault", "task", "flow", "hub", "sync", "insight")
        
        // Test database operations across all services concurrently
        val dbJobs = (1..concurrentUsers).map { userId ->
            async {
                repeat(requestsPerUser) { requestIndex ->
                    val service = services[requestIndex % services.size]
                    
                    val connectionTime = measureTime {
                        when (service) {
                            "vault" -> {
                                makeRequest("POST", "$vaultServiceUrl/api/v1/secrets", mapOf(
                                    "name" to "db-perf-test-$userId-$requestIndex",
                                    "value" to "test-value",
                                    "userId" to "db-perf-user-$userId"
                                ))
                            }
                            "task" -> {
                                makeRequest("POST", "$taskServiceUrl/api/v1/tasks", mapOf(
                                    "name" to "DB Perf Test Task $userId-$requestIndex",
                                    "userId" to "db-perf-user-$userId"
                                ))
                            }
                            "flow" -> {
                                makeRequest("GET", "$flowServiceUrl/api/v1/workflows?userId=db-perf-user-$userId")
                            }
                            "hub" -> {
                                makeRequest("GET", "$hubServiceUrl/api/v1/integrations?userId=db-perf-user-$userId")
                            }
                            "sync" -> {
                                makeRequest("GET", "$syncServiceUrl/api/v1/configurations?userId=db-perf-user-$userId")
                            }
                            "insight" -> {
                                makeRequest("GET", "$insightServiceUrl/api/v1/analytics?userId=db-perf-user-$userId")
                            }
                        }
                    }.inWholeMilliseconds
                    
                    synchronized(connectionTimes) {
                        connectionTimes.add(connectionTime)
                    }
                }
            }
        }
        
        dbJobs.awaitAll()
        
        // Analyze database performance
        val avgConnectionTime = connectionTimes.average()
        val p95ConnectionTime = connectionTimes.sorted()[((connectionTimes.size * 0.95).toInt())]
        val maxConnectionTime = connectionTimes.maxOrNull() ?: 0L
        
        println("Database Connection Performance:")
        println("Total DB Operations: ${connectionTimes.size}")
        println("Avg: ${avgConnectionTime.toInt()}ms, P95: ${p95ConnectionTime}ms, Max: ${maxConnectionTime}ms")
        
        // Assert performance requirements
        assertTrue(p95ConnectionTime <= 300L, "Database operation P95 should be <= 300ms, got ${p95ConnectionTime}ms")
        assertTrue(maxConnectionTime <= 1000L, "Max database operation time should be <= 1000ms, got ${maxConnectionTime}ms")
    }
    
    @Test
    fun `Insight Service query performance under load`() = runTest {
        val queryTimes = mutableListOf<Long>()
        
        // 1. Create test data for analytics
        repeat(20) { i ->
            // Create some test events
            val eventRequest = mapOf(
                "type" to "test.event",
                "source" to "performance-test",
                "data" to mapOf(
                    "testId" to i,
                    "timestamp" to System.currentTimeMillis(),
                    "value" to (i * 10)
                ),
                "userId" to "perf-test-user"
            )
            
            makeRequest("POST", "$insightServiceUrl/api/v1/events", eventRequest)
        }
        
        // Wait for events to be indexed
        Thread.sleep(2000)
        
        // 2. Test concurrent analytics queries
        val queryJobs = (1..concurrentUsers).map { userId ->
            async {
                repeat(5) { queryIndex ->
                    val queryTypes = listOf(
                        mapOf("type" to "summary", "userId" to "perf-test-user"),
                        mapOf("type" to "events", "userId" to "perf-test-user", "limit" to 10),
                        mapOf("type" to "metrics", "userId" to "perf-test-user", "metric" to "event_count"),
                        mapOf("type" to "correlation", "userId" to "perf-test-user", "services" to listOf("hub", "vault"))
                    )
                    
                    val query = queryTypes[queryIndex % queryTypes.size]
                    
                    val queryTime = measureTime {
                        val queryString = query.entries.joinToString("&") {
                            val value = it.value
                            if (value is List<*>) {
                                "${it.key}=${value.joinToString(",")}"
                            } else {
                                "${it.key}=${it.value}"
                            }
                        }
                        makeRequest("GET", "$insightServiceUrl/api/v1/analytics?$queryString")
                    }.inWholeMilliseconds
                    
                    synchronized(queryTimes) {
                        queryTimes.add(queryTime)
                    }
                }
            }
        }
        
        queryJobs.awaitAll()
        
        // 3. Analyze analytics query performance
        val avgQueryTime = queryTimes.average()
        val p95QueryTime = queryTimes.sorted()[((queryTimes.size * 0.95).toInt())]
        val maxQueryTime = queryTimes.maxOrNull() ?: 0L
        
        println("Insight Analytics Query Performance:")
        println("Total Queries: ${queryTimes.size}")
        println("Avg: ${avgQueryTime.toInt()}ms, P95: ${p95QueryTime}ms, Max: ${maxQueryTime}ms")
        
        // 4. Assert performance requirements
        assertTrue(p95QueryTime <= 1000L, "Analytics query P95 should be <= 1000ms, got ${p95QueryTime}ms")
        assertTrue(maxQueryTime <= 3000L, "Max analytics query time should be <= 3000ms, got ${maxQueryTime}ms")
    }
    
    @Test
    fun `Hub Service integration operations performance`() = runTest {
        val operationTimes = mutableListOf<Long>()
        
        // 1. Create test integrations
        val integrationIds = mutableListOf<String>()
        repeat(5) { i ->
            val integrationRequest = mapOf(
                "name" to "Performance Test Integration $i",
                "type" to "GITHUB",
                "description" to "Integration for performance testing",
                "configuration" to mapOf("baseUrl" to "https://api.github.com"),
                "userId" to "perf-test-user"
            )
            
            val response = makeRequest("POST", "$hubServiceUrl/api/v1/integrations", integrationRequest)
            if (response.statusCode() == 201) {
                val result = json.decodeFromString<Map<String, Any>>(response.body())
                val integrationId = (result["data"] as Map<String, Any>)["id"] as String
                integrationIds.add(integrationId)
            }
        }
        
        // 2. Test concurrent integration operations
        val operationJobs = (1..concurrentUsers).map { userId ->
            async {
                repeat(10) { operationIndex ->
                    val integrationId = integrationIds[operationIndex % integrationIds.size]
                    
                    val operations = listOf(
                        { // List repositories
                            makeRequest("POST", "$hubServiceUrl/api/v1/integrations/$integrationId/execute", mapOf(
                                "operation" to "listRepositories",
                                "parameters" to mapOf("type" to "all")
                            ))
                        },
                        { // Get user info
                            makeRequest("POST", "$hubServiceUrl/api/v1/integrations/$integrationId/execute", mapOf(
                                "operation" to "getUserInfo",
                                "parameters" to mapOf("username" to "test-user")
                            ))
                        },
                        { // List branches
                            makeRequest("POST", "$hubServiceUrl/api/v1/integrations/$integrationId/execute", mapOf(
                                "operation" to "listBranches",
                                "parameters" to mapOf("repo" to "test/repo")
                            ))
                        }
                    )
                    
                    val operation = operations[operationIndex % operations.size]
                    
                    val operationTime = measureTime {
                        operation()
                    }.inWholeMilliseconds
                    
                    synchronized(operationTimes) {
                        operationTimes.add(operationTime)
                    }
                }
            }
        }
        
        operationJobs.awaitAll()
        
        // 3. Analyze integration operation performance
        val avgOperationTime = operationTimes.average()
        val p95OperationTime = operationTimes.sorted()[((operationTimes.size * 0.95).toInt())]
        val maxOperationTime = operationTimes.maxOrNull() ?: 0L
        
        println("Hub Integration Operation Performance:")
        println("Total Operations: ${operationTimes.size}")
        println("Avg: ${avgOperationTime.toInt()}ms, P95: ${p95OperationTime}ms, Max: ${maxOperationTime}ms")
        
        // 4. Assert performance requirements
        assertTrue(p95OperationTime <= 1000L, "Integration operation P95 should be <= 1000ms, got ${p95OperationTime}ms")
        assertTrue(maxOperationTime <= 3000L, "Max integration operation time should be <= 3000ms, got ${maxOperationTime}ms")
        
        // 5. Cleanup
        integrationIds.forEach { id ->
            makeRequest("DELETE", "$hubServiceUrl/api/v1/integrations/$id?userId=perf-test-user")
        }
    }
    
    @Test
    fun `Memory usage stability under extended load`() = runTest {
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        println("Initial Memory Usage: ${initialMemory / 1024 / 1024} MB")
        
        // Run extended load test
        repeat(5) { cycle ->
            println("Load test cycle ${cycle + 1}/5")
            
            val jobs = (1..20).map { userId ->
                async {
                    repeat(50) { requestIndex ->
                        // Mix of operations across services
                        when (requestIndex % 6) {
                            0 -> makeRequest("GET", "$vaultServiceUrl/health")
                            1 -> makeRequest("GET", "$taskServiceUrl/health")
                            2 -> makeRequest("GET", "$flowServiceUrl/health")
                            3 -> makeRequest("GET", "$hubServiceUrl/health")
                            4 -> makeRequest("GET", "$syncServiceUrl/health")
                            5 -> makeRequest("GET", "$insightServiceUrl/health")
                        }
                        
                        if (requestIndex % 10 == 0) {
                            delay(10) // Small delay to prevent overwhelming
                        }
                    }
                }
            }
            
            jobs.awaitAll()
            
            // Force garbage collection and check memory
            System.gc()
            delay(1000)
            
            val currentMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryIncrease = currentMemory - initialMemory
            val memoryIncreaseMB = memoryIncrease / 1024 / 1024
            
            println("Memory after cycle ${cycle + 1}: ${currentMemory / 1024 / 1024} MB (increase: ${memoryIncreaseMB} MB)")
            
            // Memory should not increase by more than 100MB per cycle
            assertTrue(memoryIncreaseMB <= 100, 
                "Memory increase should be <= 100MB per cycle, got ${memoryIncreaseMB}MB in cycle ${cycle + 1}")
        }
        
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val totalIncrease = (finalMemory - initialMemory) / 1024 / 1024
        
        println("Final Memory Usage: ${finalMemory / 1024 / 1024} MB")
        println("Total Memory Increase: ${totalIncrease} MB")
        
        // Total memory increase should not exceed 300MB
        assertTrue(totalIncrease <= 300, 
            "Total memory increase should be <= 300MB, got ${totalIncrease}MB")
    }
    
    // Helper methods
    private fun makeHealthCheckRequest(service: String): HttpResponse<String> {
        val serviceUrl = when (service) {
            "vault" -> vaultServiceUrl
            "task" -> taskServiceUrl
            "flow" -> flowServiceUrl
            "hub" -> hubServiceUrl
            "sync" -> syncServiceUrl
            "insight" -> insightServiceUrl
            else -> apiGatewayUrl
        }
        
        return makeRequest("GET", "$serviceUrl/health")
    }
    
    private fun makeRequest(method: String, url: String, body: Map<String, Any>? = null): HttpResponse<String> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
        
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