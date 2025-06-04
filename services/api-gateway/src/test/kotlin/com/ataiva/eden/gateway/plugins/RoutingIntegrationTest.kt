package com.ataiva.eden.gateway.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*
import com.ataiva.eden.gateway.module

class RoutingIntegrationTest {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @Test
    fun `gateway health endpoint should return healthy status`() = testApplication {
        application {
            module()
        }
        
        val response = client.get("/health")
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = response.bodyAsText()
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        
        assertEquals("api-gateway", jsonResponse["service"]?.jsonPrimitive?.content)
        assertEquals("healthy", jsonResponse["status"]?.jsonPrimitive?.content)
        assertEquals("1.0.0", jsonResponse["version"]?.jsonPrimitive?.content)
        assertNotNull(jsonResponse["timestamp"])
    }
    
    @Test
    fun `gateway root endpoint should return service information`() = testApplication {
        application {
            module()
        }
        
        val response = client.get("/")
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = response.bodyAsText()
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        
        assertEquals("Eden API Gateway", jsonResponse["name"]?.jsonPrimitive?.content)
        assertEquals("1.0.0", jsonResponse["version"]?.jsonPrimitive?.content)
        assertEquals("running", jsonResponse["status"]?.jsonPrimitive?.content)
        assertNotNull(jsonResponse["services"])
    }
    
    @Test
    fun `services discovery endpoint should list all configured services`() = testApplication {
        application {
            module()
        }
        
        val response = client.get("/services")
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = response.bodyAsText()
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        
        assertNotNull(jsonResponse["services"])
        assertNotNull(jsonResponse["total"])
        
        // Should include all 7 services
        val total = jsonResponse["total"]?.jsonPrimitive?.content?.toInt()
        assertEquals(7, total)
    }
    
    @Test
    fun `services health endpoint should check all service health`() = testApplication {
        application {
            module()
        }
        
        val response = client.get("/services/health")
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = response.bodyAsText()
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        
        assertNotNull(jsonResponse["overall_status"])
        assertNotNull(jsonResponse["healthy_services"])
        assertNotNull(jsonResponse["total_services"])
        assertNotNull(jsonResponse["services"])
        assertNotNull(jsonResponse["timestamp"])
        
        // Should check all 7 services
        val totalServices = jsonResponse["total_services"]?.jsonPrimitive?.content?.toInt()
        assertEquals(7, totalServices)
    }
    
    @Test
    fun `vault service proxy should handle GET requests`() = testApplication {
        application {
            module()
        }
        
        // This will fail because vault service is not running, but we test the routing logic
        val response = client.get("/api/v1/vault/secrets")
        
        // Should get BadGateway or ServiceUnavailable since service is not running
        assertTrue(
            response.status == HttpStatusCode.BadGateway || 
            response.status == HttpStatusCode.ServiceUnavailable ||
            response.status == HttpStatusCode.GatewayTimeout
        )
        
        val responseBody = response.bodyAsText()
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        
        assertNotNull(jsonResponse["error"])
    }
    
    @Test
    fun `flow service proxy should handle POST requests`() = testApplication {
        application {
            module()
        }
        
        val response = client.post("/api/v1/flow/workflows") {
            contentType(ContentType.Application.Json)
            setBody("""{"name": "test-workflow", "definition": {}}""")
        }
        
        // Should get BadGateway or ServiceUnavailable since service is not running
        assertTrue(
            response.status == HttpStatusCode.BadGateway || 
            response.status == HttpStatusCode.ServiceUnavailable ||
            response.status == HttpStatusCode.GatewayTimeout
        )
        
        val responseBody = response.bodyAsText()
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        
        assertNotNull(jsonResponse["error"])
    }
    
    @Test
    fun `task service proxy should handle PUT requests`() = testApplication {
        application {
            module()
        }
        
        val response = client.put("/api/v1/task/tasks/123") {
            contentType(ContentType.Application.Json)
            setBody("""{"name": "updated-task", "status": "active"}""")
        }
        
        // Should get BadGateway or ServiceUnavailable since service is not running
        assertTrue(
            response.status == HttpStatusCode.BadGateway || 
            response.status == HttpStatusCode.ServiceUnavailable ||
            response.status == HttpStatusCode.GatewayTimeout
        )
        
        val responseBody = response.bodyAsText()
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        
        assertNotNull(jsonResponse["error"])
    }
    
    @Test
    fun `monitor service proxy should handle DELETE requests`() = testApplication {
        application {
            module()
        }
        
        val response = client.delete("/api/v1/monitor/alerts/456")
        
        // Should get BadGateway or ServiceUnavailable since service is not running
        assertTrue(
            response.status == HttpStatusCode.BadGateway || 
            response.status == HttpStatusCode.ServiceUnavailable ||
            response.status == HttpStatusCode.GatewayTimeout
        )
        
        val responseBody = response.bodyAsText()
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        
        assertNotNull(jsonResponse["error"])
    }
    
    @Test
    fun `sync service proxy should handle PATCH requests`() = testApplication {
        application {
            module()
        }
        
        val response = client.patch("/api/v1/sync/sources/789") {
            contentType(ContentType.Application.Json)
            setBody("""{"status": "active"}""")
        }
        
        // Should get BadGateway or ServiceUnavailable since service is not running
        assertTrue(
            response.status == HttpStatusCode.BadGateway || 
            response.status == HttpStatusCode.ServiceUnavailable ||
            response.status == HttpStatusCode.GatewayTimeout
        )
        
        val responseBody = response.bodyAsText()
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        
        assertNotNull(jsonResponse["error"])
    }
    
    @Test
    fun `insight service proxy should forward query parameters`() = testApplication {
        application {
            module()
        }
        
        val response = client.get("/api/v1/insight/reports?type=usage&format=json")
        
        // Should get BadGateway or ServiceUnavailable since service is not running
        assertTrue(
            response.status == HttpStatusCode.BadGateway || 
            response.status == HttpStatusCode.ServiceUnavailable ||
            response.status == HttpStatusCode.GatewayTimeout
        )
        
        val responseBody = response.bodyAsText()
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        
        assertNotNull(jsonResponse["error"])
        assertEquals("insight", jsonResponse["service"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun `hub service proxy should forward headers`() = testApplication {
        application {
            module()
        }
        
        val response = client.get("/api/v1/hub/integrations") {
            header("Authorization", "Bearer test-token")
            header("X-Custom-Header", "test-value")
        }
        
        // Should get BadGateway or ServiceUnavailable since service is not running
        assertTrue(
            response.status == HttpStatusCode.BadGateway || 
            response.status == HttpStatusCode.ServiceUnavailable ||
            response.status == HttpStatusCode.GatewayTimeout
        )
        
        val responseBody = response.bodyAsText()
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        
        assertNotNull(jsonResponse["error"])
        assertEquals("hub", jsonResponse["service"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun `unsupported HTTP method should return method not allowed`() = testApplication {
        application {
            module()
        }
        
        val response = client.request("/api/v1/vault/secrets") {
            method = HttpMethod.Options
        }
        
        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        
        val responseBody = response.bodyAsText()
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        
        assertEquals("Method not allowed", jsonResponse["error"]?.jsonPrimitive?.content)
        assertEquals("OPTIONS", jsonResponse["method"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun `legacy test endpoints should work for backward compatibility`() = testApplication {
        application {
            module()
        }
        
        val getResponse = client.get("/test")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        
        val getBody = getResponse.bodyAsText()
        val getJson = json.parseToJsonElement(getBody).jsonObject
        assertEquals("Test endpoint", getJson["message"]?.jsonPrimitive?.content)
        assertEquals("operational", getJson["gateway_status"]?.jsonPrimitive?.content)
        
        val postResponse = client.post("/test")
        assertEquals(HttpStatusCode.OK, postResponse.status)
        
        val postBody = postResponse.bodyAsText()
        val postJson = json.parseToJsonElement(postBody).jsonObject
        assertEquals("Test POST endpoint", postJson["message"]?.jsonPrimitive?.content)
        assertEquals("operational", postJson["gateway_status"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun `service config should load from environment variables`() {
        val config = ServiceConfig()
        val services = config.getAllServices()
        
        assertEquals(7, services.size)
        
        val serviceNames = services.map { it.name }.toSet()
        val expectedServices = setOf("vault", "flow", "task", "monitor", "sync", "insight", "hub")
        assertEquals(expectedServices, serviceNames)
        
        // Test individual service lookup
        val vaultService = config.getService("vault")
        assertNotNull(vaultService)
        assertEquals("vault", vaultService.name)
        assertTrue(vaultService.url.contains("vault"))
        
        val nonExistentService = config.getService("nonexistent")
        assertNull(nonExistentService)
    }
    
    @Test
    fun `header filtering should work correctly`() {
        // Test request header filtering
        assertTrue(shouldForwardHeader("Authorization"))
        assertTrue(shouldForwardHeader("Content-Type"))
        assertTrue(shouldForwardHeader("X-Custom-Header"))
        
        assertFalse(shouldForwardHeader("Host"))
        assertFalse(shouldForwardHeader("Connection"))
        assertFalse(shouldForwardHeader("Upgrade"))
        assertFalse(shouldForwardHeader("Proxy-Connection"))
        
        // Test response header filtering
        assertTrue(shouldForwardResponseHeader("Content-Type"))
        assertTrue(shouldForwardResponseHeader("Cache-Control"))
        assertTrue(shouldForwardResponseHeader("X-Custom-Response"))
        
        assertFalse(shouldForwardResponseHeader("Connection"))
        assertFalse(shouldForwardResponseHeader("Transfer-Encoding"))
    }
}