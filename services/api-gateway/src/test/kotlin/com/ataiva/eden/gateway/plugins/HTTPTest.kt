package com.ataiva.eden.gateway.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class HTTPTest {

    @Test
    fun testCORSConfiguration() = testApplication {
        application {
            configureHTTP()
        }
        
        // Test CORS preflight request
        val response = client.options("/test") {
            header(HttpHeaders.Origin, "http://localhost:3000")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "Content-Type")
        }
        
        // Should handle CORS preflight request
        assertEquals(HttpStatusCode.OK, response.status)
        
        // Check CORS headers are present
        val corsHeaders = response.headers
        assertTrue(corsHeaders.contains(HttpHeaders.AccessControlAllowOrigin))
        assertTrue(corsHeaders.contains(HttpHeaders.AccessControlAllowMethods))
        assertTrue(corsHeaders.contains(HttpHeaders.AccessControlAllowHeaders))
    }

    @Test
    fun testCORSAllowedOrigins() = testApplication {
        application {
            configureHTTP()
        }
        
        val allowedOrigins = listOf(
            "http://localhost:3000",
            "http://localhost:8080"
        )
        
        allowedOrigins.forEach { origin ->
            val response = client.options("/test") {
                header(HttpHeaders.Origin, origin)
                header(HttpHeaders.AccessControlRequestMethod, "GET")
            }
            
            assertEquals(HttpStatusCode.OK, response.status)
            val allowOriginHeader = response.headers[HttpHeaders.AccessControlAllowOrigin]
            assertTrue(allowOriginHeader != null)
        }
    }

    @Test
    fun testCORSAllowedMethods() = testApplication {
        application {
            configureHTTP()
        }
        
        val allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        
        allowedMethods.forEach { method ->
            val response = client.options("/test") {
                header(HttpHeaders.Origin, "http://localhost:3000")
                header(HttpHeaders.AccessControlRequestMethod, method)
            }
            
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun testCORSAllowedHeaders() = testApplication {
        application {
            configureHTTP()
        }
        
        val allowedHeaders = listOf(
            "Authorization",
            "Content-Type",
            "X-Organization-Id"
        )
        
        allowedHeaders.forEach { headerName ->
            val response = client.options("/test") {
                header(HttpHeaders.Origin, "http://localhost:3000")
                header(HttpHeaders.AccessControlRequestMethod, "POST")
                header(HttpHeaders.AccessControlRequestHeaders, headerName)
            }
            
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun testCORSCredentials() = testApplication {
        application {
            configureHTTP()
        }
        
        val response = client.options("/test") {
            header(HttpHeaders.Origin, "http://localhost:3000")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        // Check that credentials are allowed
        val allowCredentials = response.headers[HttpHeaders.AccessControlAllowCredentials]
        assertEquals("true", allowCredentials)
    }

    @Test
    fun testCORSWithCustomOrigin() = testApplication {
        application {
            configureHTTP()
        }
        
        val response = client.options("/test") {
            header(HttpHeaders.Origin, "https://example.com")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }
        
        // Should handle CORS request without errors
        assertTrue(response.status.value < 500)
    }

    @Test
    fun testCORSWithInvalidOrigin() = testApplication {
        application {
            configureHTTP()
        }
        
        val response = client.options("/test") {
            header(HttpHeaders.Origin, "https://malicious-site.com")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }
        
        // Should still handle the request but may not include CORS headers
        assertTrue(response.status.value < 500)
    }

    @Test
    fun testCORSWithComplexRequest() = testApplication {
        application {
            configureHTTP()
        }
        
        val response = client.options("/api/users") {
            header(HttpHeaders.Origin, "http://localhost:3000")
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "Content-Type,Authorization,X-Organization-Id")
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        // Verify all required CORS headers are present
        val headers = response.headers
        assertNotNull(headers[HttpHeaders.AccessControlAllowOrigin])
        assertNotNull(headers[HttpHeaders.AccessControlAllowMethods])
        assertNotNull(headers[HttpHeaders.AccessControlAllowHeaders])
        assertEquals("true", headers[HttpHeaders.AccessControlAllowCredentials])
    }

    @Test
    fun testCORSWithActualRequest() = testApplication {
        application {
            configureHTTP()
        }
        
        // Test actual request (not preflight)
        val response = client.get("/test") {
            header(HttpHeaders.Origin, "http://localhost:3000")
            header("X-Organization-Id", "test-org")
        }
        
        // Should handle actual requests without issues
        assertTrue(response.status.value < 500)
    }

    @Test
    fun testHTTPConfigurationWithoutErrors() = testApplication {
        application {
            // Test that configureHTTP doesn't throw exceptions
            assertDoesNotThrow {
                configureHTTP()
            }
        }
    }
}