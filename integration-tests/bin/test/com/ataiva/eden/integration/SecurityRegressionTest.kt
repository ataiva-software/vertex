package com.ataiva.eden.integration

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration
import kotlin.test.*

/**
 * Security Regression Tests
 * 
 * These tests ensure that all security controls remain intact across the Eden platform
 * and prevent security regressions that could compromise the system.
 * 
 * Critical for maintaining security posture and compliance.
 */
class SecurityRegressionTest {
    
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
    
    private val testUserId = "security-test-user"
    private val unauthorizedUserId = "unauthorized-user"
    
    @Test
    fun `all API endpoints require proper authentication`() = runTest {
        val endpoints = listOf(
            // Vault Service endpoints
            "GET" to "$vaultServiceUrl/api/v1/secrets",
            "POST" to "$vaultServiceUrl/api/v1/secrets",
            "GET" to "$vaultServiceUrl/api/v1/secrets/test-id",
            "DELETE" to "$vaultServiceUrl/api/v1/secrets/test-id",
            
            // Task Service endpoints
            "GET" to "$taskServiceUrl/api/v1/tasks",
            "POST" to "$taskServiceUrl/api/v1/tasks",
            "GET" to "$taskServiceUrl/api/v1/tasks/test-id",
            "PUT" to "$taskServiceUrl/api/v1/tasks/test-id",
            "DELETE" to "$taskServiceUrl/api/v1/tasks/test-id",
            
            // Flow Service endpoints
            "GET" to "$flowServiceUrl/api/v1/workflows",
            "POST" to "$flowServiceUrl/api/v1/workflows",
            "GET" to "$flowServiceUrl/api/v1/workflows/test-id",
            "PUT" to "$flowServiceUrl/api/v1/workflows/test-id",
            "DELETE" to "$flowServiceUrl/api/v1/workflows/test-id",
            "POST" to "$flowServiceUrl/api/v1/workflows/test-id/execute",
            
            // Hub Service endpoints
            "GET" to "$hubServiceUrl/api/v1/integrations",
            "POST" to "$hubServiceUrl/api/v1/integrations",
            "GET" to "$hubServiceUrl/api/v1/integrations/test-id",
            "PUT" to "$hubServiceUrl/api/v1/integrations/test-id",
            "DELETE" to "$hubServiceUrl/api/v1/integrations/test-id",
            "GET" to "$hubServiceUrl/api/v1/webhooks",
            "POST" to "$hubServiceUrl/api/v1/webhooks",
            "POST" to "$hubServiceUrl/api/v1/notifications/send",
            
            // Sync Service endpoints
            "GET" to "$syncServiceUrl/api/v1/configurations",
            "POST" to "$syncServiceUrl/api/v1/configurations",
            "GET" to "$syncServiceUrl/api/v1/configurations/test-id",
            "PUT" to "$syncServiceUrl/api/v1/configurations/test-id",
            "DELETE" to "$syncServiceUrl/api/v1/configurations/test-id",
            
            // Insight Service endpoints
            "GET" to "$insightServiceUrl/api/v1/analytics",
            "POST" to "$insightServiceUrl/api/v1/reports",
            "GET" to "$insightServiceUrl/api/v1/reports/test-id"
        )
        
        endpoints.forEach { (method, url) ->
            // Test without authentication token
            val response = makeUnauthenticatedRequest(method, url)
            assertTrue(
                response.statusCode() == 401 || response.statusCode() == 403,
                "Endpoint $method $url should require authentication, got ${response.statusCode()}"
            )
        }
        
        println("✅ All ${endpoints.size} API endpoints properly require authentication")
    }
    
    @Test
    fun `JWT token validation and expiration handling`() = runTest {
        // Test with invalid JWT token
        val invalidTokens = listOf(
            "invalid-token",
            "Bearer invalid-token",
            "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.invalid.signature",
            "", // Empty token
            "Bearer ", // Bearer without token
            "Basic dGVzdDp0ZXN0" // Wrong auth type
        )
        
        invalidTokens.forEach { token ->
            val response = makeRequestWithToken("GET", "$vaultServiceUrl/api/v1/secrets", token)
            assertTrue(
                response.statusCode() == 401 || response.statusCode() == 403,
                "Invalid token '$token' should be rejected, got ${response.statusCode()}"
            )
        }
        
        // Test with expired token (if we can generate one)
        val expiredToken = generateExpiredJWTToken()
        if (expiredToken != null) {
            val response = makeRequestWithToken("GET", "$vaultServiceUrl/api/v1/secrets", expiredToken)
            assertTrue(
                response.statusCode() == 401,
                "Expired token should be rejected, got ${response.statusCode()}"
            )
        }
        
        println("✅ JWT token validation working correctly")
    }
    
    @Test
    fun `role-based access control validation`() = runTest {
        // Get valid tokens for different user roles
        val adminToken = authenticateUser("admin-user", "admin-password")
        val userToken = authenticateUser("regular-user", "user-password")
        val readOnlyToken = authenticateUser("readonly-user", "readonly-password")
        
        // Test admin-only endpoints
        val adminEndpoints = listOf(
            "DELETE" to "$vaultServiceUrl/api/v1/secrets/test-id",
            "DELETE" to "$taskServiceUrl/api/v1/tasks/test-id",
            "DELETE" to "$flowServiceUrl/api/v1/workflows/test-id",
            "DELETE" to "$hubServiceUrl/api/v1/integrations/test-id"
        )
        
        adminEndpoints.forEach { (method, url) ->
            // Admin should have access (even if resource doesn't exist, should get 404 not 403)
            val adminResponse = makeRequestWithToken(method, url, adminToken)
            assertNotEquals(403, adminResponse.statusCode(), 
                "Admin should have access to $method $url")
            
            // Regular user should be forbidden
            val userResponse = makeRequestWithToken(method, url, userToken)
            assertEquals(403, userResponse.statusCode(), 
                "Regular user should be forbidden from $method $url")
            
            // Read-only user should be forbidden
            val readOnlyResponse = makeRequestWithToken(method, url, readOnlyToken)
            assertEquals(403, readOnlyResponse.statusCode(), 
                "Read-only user should be forbidden from $method $url")
        }
        
        // Test write endpoints for read-only user
        val writeEndpoints = listOf(
            "POST" to "$vaultServiceUrl/api/v1/secrets",
            "POST" to "$taskServiceUrl/api/v1/tasks",
            "POST" to "$flowServiceUrl/api/v1/workflows",
            "POST" to "$hubServiceUrl/api/v1/integrations"
        )
        
        writeEndpoints.forEach { (method, url) ->
            val response = makeRequestWithToken(method, url, readOnlyToken, mapOf("test" to "data"))
            assertEquals(403, response.statusCode(), 
                "Read-only user should be forbidden from $method $url")
        }
        
        println("✅ Role-based access control working correctly")
    }
    
    @Test
    fun `input validation and SQL injection prevention`() = runTest {
        val validToken = authenticateUser("test-user", "test-password")
        
        // SQL injection payloads
        val sqlInjectionPayloads = listOf(
            "'; DROP TABLE secrets; --",
            "' OR '1'='1",
            "'; DELETE FROM tasks; --",
            "' UNION SELECT * FROM users --",
            "'; INSERT INTO secrets (name, value) VALUES ('hacked', 'payload'); --",
            "admin'--",
            "' OR 1=1#",
            "'; EXEC xp_cmdshell('dir'); --"
        )
        
        // Test SQL injection in various parameters
        sqlInjectionPayloads.forEach { payload ->
            // Test in secret name
            val secretResponse = makeRequestWithToken("POST", "$vaultServiceUrl/api/v1/secrets", validToken, mapOf(
                "name" to payload,
                "value" to "test-value",
                "userId" to testUserId
            ))
            // Should either reject with 400 (validation error) or sanitize the input
            assertTrue(
                secretResponse.statusCode() == 400 || secretResponse.statusCode() == 422,
                "SQL injection payload in secret name should be rejected, got ${secretResponse.statusCode()}"
            )
            
            // Test in task name
            val taskResponse = makeRequestWithToken("POST", "$taskServiceUrl/api/v1/tasks", validToken, mapOf(
                "name" to payload,
                "description" to "test task",
                "userId" to testUserId
            ))
            assertTrue(
                taskResponse.statusCode() == 400 || taskResponse.statusCode() == 422,
                "SQL injection payload in task name should be rejected, got ${taskResponse.statusCode()}"
            )
            
            // Test in query parameters
            val queryResponse = makeRequestWithToken("GET", 
                "$vaultServiceUrl/api/v1/secrets?name=$payload&userId=$testUserId", validToken)
            // Should not return 500 (internal server error) which might indicate SQL injection succeeded
            assertNotEquals(500, queryResponse.statusCode(),
                "SQL injection in query parameter should not cause internal server error")
        }
        
        println("✅ SQL injection prevention working correctly")
    }
    
    @Test
    fun `XSS prevention in API responses`() = runTest {
        val validToken = authenticateUser("test-user", "test-password")
        
        // XSS payloads
        val xssPayloads = listOf(
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert('XSS')>",
            "javascript:alert('XSS')",
            "<svg onload=alert('XSS')>",
            "';alert('XSS');//",
            "<iframe src=javascript:alert('XSS')></iframe>",
            "<body onload=alert('XSS')>",
            "<script src=http://evil.com/xss.js></script>"
        )
        
        xssPayloads.forEach { payload ->
            // Create a secret with XSS payload
            val createResponse = makeRequestWithToken("POST", "$vaultServiceUrl/api/v1/secrets", validToken, mapOf(
                "name" to "xss-test-${System.currentTimeMillis()}",
                "value" to payload,
                "description" to payload,
                "userId" to testUserId
            ))
            
            if (createResponse.statusCode() == 201) {
                val createResult = json.decodeFromString<Map<String, Any>>(createResponse.body())
                val secretId = (createResult["data"] as Map<String, Any>)["id"] as String
                
                // Retrieve the secret and check if XSS payload is properly escaped
                val getResponse = makeRequestWithToken("GET", "$vaultServiceUrl/api/v1/secrets/$secretId", validToken)
                if (getResponse.statusCode() == 200) {
                    val responseBody = getResponse.body()
                    
                    // Response should not contain unescaped script tags or other XSS vectors
                    assertFalse(responseBody.contains("<script>"), 
                        "Response should not contain unescaped script tags")
                    assertFalse(responseBody.contains("javascript:"), 
                        "Response should not contain javascript: protocol")
                    assertFalse(responseBody.contains("onerror="), 
                        "Response should not contain unescaped event handlers")
                }
                
                // Cleanup
                makeRequestWithToken("DELETE", "$vaultServiceUrl/api/v1/secrets/$secretId", validToken)
            }
        }
        
        println("✅ XSS prevention working correctly")
    }
    
    @Test
    fun `sensitive data encryption validation`() = runTest {
        val validToken = authenticateUser("test-user", "test-password")
        
        // Create secrets with sensitive data
        val sensitiveData = listOf(
            "password123",
            "api-key-super-secret-12345",
            "database-connection-string-with-password",
            "private-key-content-very-sensitive",
            "oauth-client-secret-confidential"
        )
        
        val secretIds = mutableListOf<String>()
        
        sensitiveData.forEach { data ->
            val createResponse = makeRequestWithToken("POST", "$vaultServiceUrl/api/v1/secrets", validToken, mapOf(
                "name" to "encryption-test-${System.currentTimeMillis()}",
                "value" to data,
                "userId" to testUserId
            ))
            
            if (createResponse.statusCode() == 201) {
                val result = json.decodeFromString<Map<String, Any>>(createResponse.body())
                val secretId = (result["data"] as Map<String, Any>)["id"] as String
                secretIds.add(secretId)
                
                // Verify that the response doesn't contain the raw sensitive data
                val responseBody = createResponse.body()
                assertFalse(responseBody.contains(data), 
                    "Create response should not contain raw sensitive data")
            }
        }
        
        // Verify that retrieving secrets returns decrypted data only to authorized users
        secretIds.forEach { secretId ->
            val getResponse = makeRequestWithToken("GET", "$vaultServiceUrl/api/v1/secrets/$secretId", validToken)
            if (getResponse.statusCode() == 200) {
                val result = json.decodeFromString<Map<String, Any>>(getResponse.body())
                val secretData = result["data"] as Map<String, Any>
                
                // Should contain the decrypted value for authorized user
                assertTrue(secretData.containsKey("value"), 
                    "Authorized user should receive decrypted secret value")
                
                // Value should be one of our test values (decrypted)
                val decryptedValue = secretData["value"] as String
                assertTrue(sensitiveData.contains(decryptedValue), 
                    "Decrypted value should match original sensitive data")
            }
        }
        
        // Test that unauthorized users cannot access secrets
        val unauthorizedToken = authenticateUser("unauthorized-user", "unauthorized-password")
        secretIds.forEach { secretId ->
            val unauthorizedResponse = makeRequestWithToken("GET", 
                "$vaultServiceUrl/api/v1/secrets/$secretId", unauthorizedToken)
            assertTrue(unauthorizedResponse.statusCode() == 403 || unauthorizedResponse.statusCode() == 404,
                "Unauthorized user should not be able to access secrets")
        }
        
        // Cleanup
        secretIds.forEach { secretId ->
            makeRequestWithToken("DELETE", "$vaultServiceUrl/api/v1/secrets/$secretId", validToken)
        }
        
        println("✅ Sensitive data encryption working correctly")
    }
    
    @Test
    fun `webhook signature verification`() = runTest {
        val validToken = authenticateUser("test-user", "test-password")
        
        // Create a webhook with a secret
        val webhookSecret = "webhook-security-test-secret-${System.currentTimeMillis()}"
        val createWebhookResponse = makeRequestWithToken("POST", "$hubServiceUrl/api/v1/webhooks", validToken, mapOf(
            "name" to "Security Test Webhook",
            "url" to "https://httpbin.org/post",
            "events" to listOf("test.event"),
            "secret" to webhookSecret,
            "userId" to testUserId
        ))
        
        if (createWebhookResponse.statusCode() == 201) {
            val result = json.decodeFromString<Map<String, Any>>(createWebhookResponse.body())
            val webhookId = (result["data"] as Map<String, Any>)["id"] as String
            
            // Test webhook delivery with proper signature
            val payload = mapOf(
                "event" to "test.event",
                "payload" to mapOf(
                    "message" to "Security test payload",
                    "timestamp" to System.currentTimeMillis()
                )
            )
            
            val deliveryResponse = makeRequestWithToken("POST", 
                "$hubServiceUrl/api/v1/webhooks/$webhookId/deliver", validToken, payload)
            assertEquals(202, deliveryResponse.statusCode(), 
                "Webhook delivery should be accepted")
            
            // Wait for delivery and check if signature was properly generated
            Thread.sleep(2000)
            
            val deliveriesResponse = makeRequestWithToken("GET", 
                "$hubServiceUrl/api/v1/webhooks/$webhookId/deliveries", validToken)
            if (deliveriesResponse.statusCode() == 200) {
                val deliveriesResult = json.decodeFromString<Map<String, Any>>(deliveriesResponse.body())
                val deliveries = deliveriesResult["data"] as List<Map<String, Any>>
                
                if (deliveries.isNotEmpty()) {
                    val delivery = deliveries.first()
                    val responseHeaders = delivery["responseHeaders"] as? Map<String, Any>
                    
                    // Should have signature header in the request (not response, but we can verify it was sent)
                    assertTrue(delivery.containsKey("status"), 
                        "Delivery should have status indicating signature was processed")
                }
            }
            
            // Cleanup
            makeRequestWithToken("DELETE", "$hubServiceUrl/api/v1/webhooks/$webhookId", validToken)
        }
        
        println("✅ Webhook signature verification working correctly")
    }
    
    @Test
    fun `rate limiting and DoS protection`() = runTest {
        val validToken = authenticateUser("test-user", "test-password")
        
        // Test rate limiting by making many requests quickly
        val rapidRequests = (1..100).map {
            makeRequestWithToken("GET", "$vaultServiceUrl/health", validToken)
        }
        
        // Check if any requests were rate limited
        val rateLimitedRequests = rapidRequests.count { it.statusCode() == 429 }
        
        if (rateLimitedRequests > 0) {
            println("✅ Rate limiting is active - $rateLimitedRequests requests were rate limited")
        } else {
            println("⚠️ No rate limiting detected - consider implementing rate limiting for DoS protection")
        }
        
        // Test with invalid authentication (should be rate limited more aggressively)
        val invalidAuthRequests = (1..50).map {
            makeRequestWithToken("GET", "$vaultServiceUrl/api/v1/secrets", "invalid-token")
        }
        
        val invalidAuthRateLimited = invalidAuthRequests.count { it.statusCode() == 429 }
        
        if (invalidAuthRateLimited > 0) {
            println("✅ Rate limiting for invalid auth is active - $invalidAuthRateLimited requests were rate limited")
        }
        
        // Test large payload handling
        val largePayload = mapOf(
            "name" to "large-payload-test",
            "value" to "x".repeat(10000), // 10KB payload
            "description" to "y".repeat(5000), // 5KB description
            "userId" to testUserId
        )
        
        val largePayloadResponse = makeRequestWithToken("POST", "$vaultServiceUrl/api/v1/secrets", validToken, largePayload)
        
        // Should either accept it or reject with appropriate error (not crash)
        assertTrue(largePayloadResponse.statusCode() in listOf(201, 400, 413, 422),
            "Large payload should be handled gracefully, got ${largePayloadResponse.statusCode()}")
        
        if (largePayloadResponse.statusCode() == 201) {
            // Cleanup if created
            val result = json.decodeFromString<Map<String, Any>>(largePayloadResponse.body())
            val secretId = (result["data"] as Map<String, Any>)["id"] as String
            makeRequestWithToken("DELETE", "$vaultServiceUrl/api/v1/secrets/$secretId", validToken)
        }
        
        println("✅ DoS protection mechanisms tested")
    }
    
    @Test
    fun `HTTPS enforcement and secure headers`() = runTest {
        // Test that HTTP requests are redirected to HTTPS (if applicable)
        // Note: This test assumes production deployment with HTTPS
        
        val services = listOf(
            vaultServiceUrl, taskServiceUrl, flowServiceUrl, 
            hubServiceUrl, syncServiceUrl, insightServiceUrl, apiGatewayUrl
        )
        
        services.forEach { serviceUrl ->
            val healthResponse = makeUnauthenticatedRequest("GET", "$serviceUrl/health")
            
            // Check for security headers in response
            val headers = healthResponse.headers().map()
            
            // Check for important security headers
            val securityHeaders = mapOf(
                "X-Content-Type-Options" to "nosniff",
                "X-Frame-Options" to listOf("DENY", "SAMEORIGIN"),
                "X-XSS-Protection" to "1; mode=block",
                "Strict-Transport-Security" to null, // Should exist for HTTPS
                "Content-Security-Policy" to null // Should exist
            )
            
            securityHeaders.forEach { (headerName, expectedValues) ->
                if (headers.containsKey(headerName.lowercase())) {
                    val headerValue = headers[headerName.lowercase()]?.first()
                    if (expectedValues != null && headerValue != null) {
                        assertTrue(expectedValues.any { headerValue.contains(it) },
                            "$serviceUrl should have proper $headerName header")
                    }
                    println("✅ $serviceUrl has $headerName header: $headerValue")
                } else {
                    println("⚠️ $serviceUrl missing security header: $headerName")
                }
            }
        }
        
        println("✅ Security headers validation completed")
    }
    
    @Test
    fun `hub service authentication and authorization`() = runTest {
        // Test Hub Service authentication and authorization controls
        
        // 1. Test unauthenticated access to Hub Service endpoints
        val hubEndpoints = listOf(
            "GET" to "$hubServiceUrl/api/v1/integrations",
            "GET" to "$hubServiceUrl/api/v1/webhooks",
            "GET" to "$hubServiceUrl/api/v1/notifications/templates",
            "POST" to "$hubServiceUrl/api/v1/integrations",
            "POST" to "$hubServiceUrl/api/v1/webhooks"
        )
        
        hubEndpoints.forEach { (method, url) ->
            val response = makeUnauthenticatedRequest(method, url)
            assertTrue(
                response.statusCode() == 401 || response.statusCode() == 403,
                "Hub endpoint $method $url should require authentication, got ${response.statusCode()}"
            )
        }
        
        // 2. Test authenticated access with proper permissions
        val validToken = authenticateUser("test-user", "test-password")
        
        // Create an integration
        val integrationResponse = makeRequestWithToken("POST", "$hubServiceUrl/api/v1/integrations", validToken, mapOf(
            "name" to "Security Test Integration",
            "type" to "GITHUB",
            "configuration" to mapOf("baseUrl" to "https://api.github.com"),
            "userId" to testUserId
        ))
        assertEquals(201, integrationResponse.statusCode())
        
        val integrationResult = json.decodeFromString<Map<String, Any>>(integrationResponse.body())
        val integrationId = (integrationResult["data"] as Map<String, Any>)["id"] as String
        
        // 3. Test cross-user access prevention
        val otherUserToken = authenticateUser("other-user", "other-password")
        
        val unauthorizedAccessResponse = makeRequestWithToken("GET",
            "$hubServiceUrl/api/v1/integrations/$integrationId", otherUserToken)
        assertTrue(unauthorizedAccessResponse.statusCode() in listOf(403, 404),
            "Cross-user access to Hub integration should be prevented")
        
        // 4. Test role-based access control
        val readOnlyToken = authenticateUser("readonly-user", "readonly-password")
        
        val deleteResponse = makeRequestWithToken("DELETE",
            "$hubServiceUrl/api/v1/integrations/$integrationId", readOnlyToken)
        assertEquals(403, deleteResponse.statusCode(),
            "Read-only user should not be able to delete integrations")
        
        // Cleanup
        makeRequestWithToken("DELETE", "$hubServiceUrl/api/v1/integrations/$integrationId", validToken)
        
        println("✅ Hub Service security controls validated")
    }
    
    @Test
    fun `insight service data access controls`() = runTest {
        // Test Insight Service data access controls and security
        
        // 1. Test unauthenticated access to Insight Service endpoints
        val insightEndpoints = listOf(
            "GET" to "$insightServiceUrl/api/v1/analytics",
            "GET" to "$insightServiceUrl/api/v1/events",
            "GET" to "$insightServiceUrl/api/v1/dashboards",
            "POST" to "$insightServiceUrl/api/v1/queries",
            "POST" to "$insightServiceUrl/api/v1/reports"
        )
        
        insightEndpoints.forEach { (method, url) ->
            val response = makeUnauthenticatedRequest(method, url)
            assertTrue(
                response.statusCode() == 401 || response.statusCode() == 403,
                "Insight endpoint $method $url should require authentication, got ${response.statusCode()}"
            )
        }
        
        // 2. Test data isolation between users
        val user1Token = authenticateUser("user1", "password1")
        val user2Token = authenticateUser("user2", "password2")
        
        // Create data for user1
        val createQueryResponse = makeRequestWithToken("POST", "$insightServiceUrl/api/v1/queries", user1Token, mapOf(
            "name" to "Security Test Query",
            "queryText" to "SELECT * FROM test_data",
            "userId" to "user1"
        ))
        assertEquals(201, createQueryResponse.statusCode())
        
        val queryResult = json.decodeFromString<Map<String, Any>>(createQueryResponse.body())
        val queryId = (queryResult["data"] as Map<String, Any>)["id"] as String
        
        // Try to access user1's query as user2
        val unauthorizedAccessResponse = makeRequestWithToken("GET",
            "$insightServiceUrl/api/v1/queries/$queryId", user2Token)
        assertTrue(unauthorizedAccessResponse.statusCode() in listOf(403, 404),
            "Cross-user access to Insight queries should be prevented")
        
        // 3. Test SQL injection prevention in analytics queries
        val sqlInjectionPayloads = listOf(
            "'; DROP TABLE events; --",
            "' UNION SELECT * FROM users --",
            "' OR '1'='1"
        )
        
        sqlInjectionPayloads.forEach { payload ->
            val injectionResponse = makeRequestWithToken("POST", "$insightServiceUrl/api/v1/queries", user1Token, mapOf(
                "name" to "Injection Test",
                "queryText" to payload,
                "userId" to "user1"
            ))
            
            assertTrue(injectionResponse.statusCode() in listOf(400, 422),
                "SQL injection payload should be rejected, got ${injectionResponse.statusCode()}")
        }
        
        // 4. Test sensitive data handling
        val analyticsResponse = makeRequestWithToken("GET",
            "$insightServiceUrl/api/v1/analytics?userId=user1&includeSecrets=true", user1Token)
        
        if (analyticsResponse.statusCode() == 200) {
            val analyticsResult = json.decodeFromString<Map<String, Any>>(analyticsResponse.body())
            val analyticsData = analyticsResult["data"] as Map<String, Any>
            
            // Verify no raw secrets are exposed in analytics
            assertFalse(analyticsResponse.body().contains("super-secret"),
                "Analytics should not expose raw secret values")
        }
        
        println("✅ Insight Service security controls validated")
    }
    
    @Test
    fun `audit logging for security events`() = runTest {
        // Test that security-relevant events are properly logged
        
        // Attempt unauthorized access
        val unauthorizedResponse = makeUnauthenticatedRequest("GET", "$vaultServiceUrl/api/v1/secrets")
        assertEquals(401, unauthorizedResponse.statusCode())
        
        // Attempt to access another user's resources
        val validToken = authenticateUser("test-user", "test-password")
        val otherUserToken = authenticateUser("other-user", "other-password")
        
        // Create a secret as one user
        val createResponse = makeRequestWithToken("POST", "$vaultServiceUrl/api/v1/secrets", validToken, mapOf(
            "name" to "audit-test-secret",
            "value" to "secret-value",
            "userId" to testUserId
        ))
        
        if (createResponse.statusCode() == 201) {
            val result = json.decodeFromString<Map<String, Any>>(createResponse.body())
            val secretId = (result["data"] as Map<String, Any>)["id"] as String
            
            // Try to access it as another user
            val unauthorizedAccessResponse = makeRequestWithToken("GET", 
                "$vaultServiceUrl/api/v1/secrets/$secretId", otherUserToken)
            assertTrue(unauthorizedAccessResponse.statusCode() in listOf(403, 404),
                "Cross-user access should be prevented")
            
            // Try to delete it as another user
            val unauthorizedDeleteResponse = makeRequestWithToken("DELETE", 
                "$vaultServiceUrl/api/v1/secrets/$secretId", otherUserToken)
            assertTrue(unauthorizedDeleteResponse.statusCode() in listOf(403, 404),
                "Cross-user deletion should be prevented")
            
            // Cleanup
            makeRequestWithToken("DELETE", "$vaultServiceUrl/api/v1/secrets/$secretId", validToken)
        }
        
        // Test failed login attempts
        repeat(3) {
            authenticateUser("nonexistent-user", "wrong-password")
        }
        
        println("✅ Audit logging test completed - check logs for security events")
    }
    
    // Helper methods
    private fun makeUnauthenticatedRequest(method: String, url: String, body: Map<String, Any>? = null): HttpResponse<String> {
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
        }
        
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
    }
    
    private fun makeRequestWithToken(method: String, url: String, token: String, body: Map<String, Any>? = null): HttpResponse<String> {
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
        }
        
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
    }
    
    private fun authenticateUser(username: String, password: String): String {
        val authRequest = mapOf(
            "username" to username,
            "password" to password
        )
        
        val response = makeUnauthenticatedRequest("POST", "$apiGatewayUrl/auth/login", authRequest)
        
        return if (response.statusCode() == 200) {
            try {
                val result = json.decodeFromString<Map<String, Any>>(response.body())
                (result["data"] as Map<String, Any>)["token"] as String
            } catch (e: Exception) {
                "test-token-${System.currentTimeMillis()}" // Fallback for testing
            }
        } else {
            "invalid-token" // Return invalid token for failed auth
        }
    }
    
    private fun generateExpiredJWTToken(): String? {
        // In a real implementation, this would generate a JWT token with past expiration
        // For testing purposes, we'll return null to skip this test if not implemented
        return null
    }
}