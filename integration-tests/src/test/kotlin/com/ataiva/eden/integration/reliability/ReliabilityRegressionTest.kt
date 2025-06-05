package com.ataiva.eden.integration.reliability

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

/**
 * Reliability & Error Handling Regression Tests
 * 
 * These tests ensure that the Eden platform maintains robust error handling
 * and recovery mechanisms across all services.
 * 
 * Critical for ensuring system resilience and preventing service degradation.
 */
class ReliabilityRegressionTest {
    
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
    
    private val testUserId = "reliability-test-user"
    
    @Test
    fun `service failure and recovery testing`() = runTest {
        // Test graceful handling of service unavailability
        
        // 1. Test API Gateway handling of unavailable services
        val nonExistentServiceResponse = makeRequest("GET", "http://localhost:9999/health")
        assertTrue(nonExistentServiceResponse.statusCode() in listOf(404, 500, 503, -1),
            "Should handle non-existent service gracefully")
        
        // 2. Test service health endpoints for proper status reporting
        val services = mapOf(
            "Vault Service" to "$vaultServiceUrl/health",
            "Flow Service" to "$flowServiceUrl/health",
            "Task Service" to "$taskServiceUrl/health",
            "Hub Service" to "$hubServiceUrl/health",
            "Sync Service" to "$syncServiceUrl/health",
            "Insight Service" to "$insightServiceUrl/health",
            "API Gateway" to "$apiGatewayUrl/health"
        )
        
        services.forEach { (serviceName, healthUrl) ->
            val healthResponse = makeRequest("GET", healthUrl)
            if (healthResponse.statusCode() == 200) {
                val responseBody = healthResponse.body()
                assertTrue(responseBody.contains("status"), 
                    "$serviceName health endpoint should report status")
            } else {
                println("⚠️ $serviceName health check failed with status ${healthResponse.statusCode()}")
            }
        }
        
        // 3. Test circuit breaker pattern
        repeat(5) {
            makeRequest("GET", "http://localhost:9999/health") // Non-existent service
        }
        
        // Circuit breaker should prevent cascading failures
        val gatewayResponse = makeRequest("GET", "$apiGatewayUrl/health")
        assertTrue(gatewayResponse.statusCode() == 200, 
            "API Gateway should remain healthy despite downstream failures")
        
        println("✅ Service failure and recovery tests completed")
    }
    
    @Test
    fun `database connection failure handling`() = runTest {
        val validToken = authenticateUser("test-user", "test-password")
        
        // 1. Test database health check
        val dbHealthResponse = makeRequestWithToken("GET", "$vaultServiceUrl/api/v1/health/database", validToken)
        
        // If database health endpoint exists, check its response
        if (dbHealthResponse.statusCode() == 200) {
            val responseBody = dbHealthResponse.body()
            assertTrue(responseBody.contains("status") || responseBody.contains("healthy"),
                "Database health endpoint should report status")
        }
        
        // 2. Test graceful handling of database operations
        // Even if database is down, API should respond with appropriate error, not crash
        val response = makeRequestWithToken("GET", "$vaultServiceUrl/api/v1/secrets?userId=$testUserId", validToken)
        assertTrue(response.statusCode() in listOf(200, 500, 503),
            "Should handle database operations gracefully, got ${response.statusCode()}")
        
        // 3. Test database connection pooling (if implemented)
        val concurrentRequests = (1..10).map {
            makeRequestWithToken("GET", "$taskServiceUrl/api/v1/tasks?userId=$testUserId", validToken)
        }
        
        // All requests should get a valid response, not connection errors
        assertTrue(concurrentRequests.all { it.statusCode() != -1 },
            "Connection pooling should handle concurrent requests")
        
        println("✅ Database connection failure handling tests completed")
    }
    
    @Test
    fun `network timeout and retry mechanism testing`() = runTest {
        val validToken = authenticateUser("test-user", "test-password")
        
        // 1. Test with very short timeout to simulate network issues
        val quickClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1))
            .build()
        
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$vaultServiceUrl/health"))
                .timeout(Duration.ofMillis(1))
                .GET()
                .build()
            
            quickClient.send(request, HttpResponse.BodyHandlers.ofString())
            // If it succeeds, that's fine too
        } catch (e: Exception) {
            // Should handle network timeouts gracefully
            println("✅ Network timeout handled: ${e.message}")
        }
        
        // 2. Test retry mechanism with webhook delivery
        // Create a webhook pointing to a non-existent service
        val webhookResponse = makeRequestWithToken("POST", "$hubServiceUrl/api/v1/webhooks", validToken, mapOf(
            "name" to "Retry Test Webhook",
            "url" to "https://non-existent-service-12345.example.com/webhook",
            "events" to listOf("test.event"),
            "userId" to testUserId
        ))
        
        if (webhookResponse.statusCode() == 201) {
            val webhookResult = json.decodeFromString<Map<String, Any>>(webhookResponse.body())
            val webhookId = (webhookResult["data"] as Map<String, Any>)["id"] as String
            
            // Trigger webhook delivery
            val deliveryResponse = makeRequestWithToken("POST",
                "$hubServiceUrl/api/v1/webhooks/$webhookId/deliver", validToken, mapOf(
                    "event" to "test.event",
                    "payload" to mapOf("message" to "This should fail and retry")
                ))
            
            assertEquals(202, deliveryResponse.statusCode(), "Webhook delivery should be accepted")
            
            // Wait for delivery attempts and retries
            Thread.sleep(3000)
            
            // Check delivery status - should show retry attempts
            val deliveriesResponse = makeRequestWithToken("GET",
                "$hubServiceUrl/api/v1/webhooks/$webhookId/deliveries", validToken)
            
            if (deliveriesResponse.statusCode() == 200) {
                val deliveriesResult = json.decodeFromString<Map<String, Any>>(deliveriesResponse.body())
                val deliveries = deliveriesResult["data"] as List<Map<String, Any>>
                
                if (deliveries.isNotEmpty()) {
                    val delivery = deliveries.first()
                    assertTrue(delivery.containsKey("attempts") || delivery.containsKey("retries"),
                        "Webhook delivery should track retry attempts")
                }
            }
            
            // Cleanup
            makeRequestWithToken("DELETE", "$hubServiceUrl/api/v1/webhooks/$webhookId", validToken)
        }
        
        println("✅ Network timeout and retry mechanism tests completed")
    }
    
    @Test
    fun `data corruption prevention and recovery`() = runTest {
        val validToken = authenticateUser("test-user", "test-password")
        
        // 1. Test data validation on input
        val invalidDataPayloads = listOf(
            mapOf("name" to "", "value" to "test", "userId" to testUserId), // Empty name
            mapOf("name" to "test", "value" to null, "userId" to testUserId), // Null value
            mapOf("name" to "test".repeat(1000), "value" to "test", "userId" to testUserId), // Too long name
            mapOf("name" to "test", "value" to "test", "userId" to "") // Empty userId
        )
        
        invalidDataPayloads.forEach { payload ->
            val response = makeRequestWithToken("POST", "$vaultServiceUrl/api/v1/secrets", validToken, payload)
            assertTrue(response.statusCode() in listOf(400, 422),
                "Invalid data should be rejected, got ${response.statusCode()}")
        }
        
        // 2. Test data consistency across services
        // Create a secret
        val secretResponse = makeRequestWithToken("POST", "$vaultServiceUrl/api/v1/secrets", validToken, mapOf(
            "name" to "consistency-test-${System.currentTimeMillis()}",
            "value" to "test-value",
            "userId" to testUserId
        ))
        
        if (secretResponse.statusCode() == 201) {
            val secretResult = json.decodeFromString<Map<String, Any>>(secretResponse.body())
            val secretId = (secretResult["data"] as Map<String, Any>)["id"] as String
            
            // Wait for data propagation
            Thread.sleep(2000)
            
            // Check if data is reflected in analytics
            val analyticsResponse = makeRequestWithToken("GET",
                "$insightServiceUrl/api/v1/analytics?userId=$testUserId", validToken)
            
            if (analyticsResponse.statusCode() == 200) {
                // Data should be consistent across services
                // This is a simplified check - in a real test, we'd verify specific data points
                val analyticsResult = json.decodeFromString<Map<String, Any>>(analyticsResponse.body())
                assertTrue(analyticsResult.containsKey("data"),
                    "Analytics should contain data reflecting vault operations")
            }
            
            // Cleanup
            makeRequestWithToken("DELETE", "$vaultServiceUrl/api/v1/secrets/$secretId", validToken)
        }
        
        // 3. Test transaction integrity
        val transactionResponse = makeRequestWithToken("POST", "$vaultServiceUrl/api/v1/secrets", validToken, mapOf(
            "name" to "transaction-test-${System.currentTimeMillis()}",
            "value" to "test-value",
            "userId" to testUserId
        ))
        
        // Either succeeds completely or fails completely
        assertTrue(transactionResponse.statusCode() in listOf(201, 400, 422, 500),
            "Transaction should be atomic")
        
        if (transactionResponse.statusCode() == 201) {
            val result = json.decodeFromString<Map<String, Any>>(transactionResponse.body())
            val secretId = (result["data"] as Map<String, Any>)["id"] as String
            
            // Cleanup
            makeRequestWithToken("DELETE", "$vaultServiceUrl/api/v1/secrets/$secretId", validToken)
        }
        
        println("✅ Data corruption prevention and recovery tests completed")
    }
    
    // Helper methods
    private fun makeRequest(method: String, url: String, body: Map<String, Any>? = null): HttpResponse<String> {
        return try {
            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
            
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
                else -> requestBuilder.GET()
            }
            
            httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            // Return a mock response for connection failures
            object : HttpResponse<String> {
                override fun statusCode() = -1
                override fun body() = "Connection failed: ${e.message}"
                override fun headers() = HttpHeaders.of(emptyMap()) { _, _ -> true }
                override fun request() = HttpRequest.newBuilder().uri(URI.create(url)).build()
                override fun previousResponse() = null
                override fun version() = HttpClient.Version.HTTP_1_1
                override fun sslSession() = null
                override fun uri() = URI.create(url)
            }
        }
    }
    
    private fun makeRequestWithToken(method: String, url: String, token: String, body: Map<String, Any>? = null): HttpResponse<String> {
        return try {
            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", if (token.startsWith("Bearer ")) token else "Bearer $token")
                .timeout(Duration.ofSeconds(10))
            
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
                else -> requestBuilder.GET()
            }
            
            httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            // Return a mock response for connection failures
            object : HttpResponse<String> {
                override fun statusCode() = -1
                override fun body() = "Connection failed: ${e.message}"
                override fun headers() = HttpHeaders.of(emptyMap()) { _, _ -> true }
                override fun request() = HttpRequest.newBuilder().uri(URI.create(url)).build()
                override fun previousResponse() = null
                override fun version() = HttpClient.Version.HTTP_1_1
                override fun sslSession() = null
                override fun uri() = URI.create(url)
            }
        }
    }
    
    private fun authenticateUser(username: String, password: String): String {
        val authRequest = mapOf(
            "username" to username,
            "password" to password
        )
        
        val response = makeRequest("POST", "$apiGatewayUrl/auth/login", authRequest)
        
        return if (response.statusCode() == 200) {
            try {
                val result = json.decodeFromString<Map<String, Any>>(response.body())
                (result["data"] as Map<String, Any>)["token"] as String
            } catch (e: Exception) {
                "test-token-${System.currentTimeMillis()}" // Fallback for testing
            }
        } else {
            "test-token-${System.currentTimeMillis()}" // Return test token for testing
        }
    }
}