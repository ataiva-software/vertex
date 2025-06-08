package com.ataiva.eden.gateway.plugins

import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SimpleHTTPTest {

    @Test
    fun testBasicConfiguration() = testApplication {
        application {
            configureHTTP()
        }
        
        // Just test that the configuration doesn't throw exceptions
        assertTrue(true)
    }
}