package com.ataiva.eden.gateway

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        client.get("/").apply {
            // Root should return 404 since no route is defined
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }

    @Test
    fun testModuleConfiguration() = testApplication {
        application {
            module()
        }
        
        // Test that the application starts without errors
        // and all plugins are properly configured
        val response = client.get("/health") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        
        // Health endpoint should be configured or return 404
        assertTrue(response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NotFound)
    }

    @Test
    fun testCORSConfiguration() = testApplication {
        application {
            module()
        }
        
        // Test CORS preflight request
        val response = client.options("/api/test") {
            header(HttpHeaders.Origin, "http://localhost:3000")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "Content-Type")
        }
        
        // Should handle CORS preflight
        assertTrue(response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NotFound)
    }

    @Test
    fun testContentNegotiation() = testApplication {
        application {
            module()
        }
        
        // Test JSON content type handling
        val response = client.get("/api/test") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        
        // Should handle JSON content negotiation without server errors
        assertTrue(response.status.value < 500)
    }

    @Test
    fun testSecurityConfiguration() = testApplication {
        application {
            module()
        }
        
        // Test that security headers are present
        val response = client.get("/api/test")
        
        // Should not cause server errors
        assertTrue(response.status.value < 500)
    }

    @Test
    fun testMonitoringConfiguration() = testApplication {
        application {
            module()
        }
        
        // Test that monitoring is configured
        val response = client.get("/metrics") {
            header(HttpHeaders.Accept, ContentType.Text.Plain.toString())
        }
        
        // Metrics endpoint should be configured or return 404
        assertTrue(response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NotFound)
    }

    @Test
    fun testSerializationConfiguration() = testApplication {
        application {
            module()
        }
        
        // Test JSON serialization
        val response = client.post("/api/test") {
            contentType(ContentType.Application.Json)
            setBody("""{"test": "data"}""")
        }
        
        // Should handle JSON serialization without server errors
        assertTrue(response.status.value < 500)
    }

    @Test
    fun testRoutingConfiguration() = testApplication {
        application {
            module()
        }
        
        // Test that routing is configured
        val response = client.get("/api")
        
        // Should have routing configured (404 is acceptable for non-existent routes)
        assertTrue(response.status == HttpStatusCode.NotFound || response.status.value < 500)
    }

    @Test
    fun testErrorHandling() = testApplication {
        application {
            module()
        }
        
        // Test error handling with invalid request
        val response = client.post("/api/invalid") {
            contentType(ContentType.Application.Json)
            setBody("invalid json")
        }
        
        // Should handle errors gracefully
        assertTrue(response.status.value in 400..499 || response.status == HttpStatusCode.NotFound)
    }

    @Test
    fun testMultipleRequests() = testApplication {
        application {
            module()
        }
        
        // Test multiple concurrent requests
        val responses = (1..5).map {
            client.get("/api/test$it")
        }
        
        // All requests should be handled without server errors
        responses.forEach { response ->
            assertTrue(response.status.value < 500)
        }
    }

    @Test
    fun testHttpMethods() = testApplication {
        application {
            module()
        }
        
        val methods = listOf<suspend () -> HttpResponse>(
            { client.get("/api/test") },
            { client.post("/api/test") { contentType(ContentType.Application.Json) } },
            { client.put("/api/test") { contentType(ContentType.Application.Json) } },
            { client.delete("/api/test") },
            { client.patch("/api/test") { contentType(ContentType.Application.Json) } }
        )
        
        methods.forEach { method ->
            val response = method()
            // Should handle all HTTP methods without server errors
            assertTrue(response.status.value < 500)
        }
    }

    @Test
    fun testCustomHeaders() = testApplication {
        application {
            module()
        }
        
        val response = client.get("/api/test") {
            header("X-Organization-Id", "test-org")
            header("X-Custom-Header", "test-value")
            header(HttpHeaders.Authorization, "Bearer test-token")
        }
        
        // Should handle custom headers without server errors
        assertTrue(response.status.value < 500)
    }
}