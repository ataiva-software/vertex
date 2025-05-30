package com.ataiva.eden.gateway.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.http.*

fun Application.configureHTTP() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Organization-Id")
        allowCredentials = true
        
        // Allow origins from environment or default to localhost
        val allowedOrigins = environment.config.propertyOrNull("cors.origins")?.getString()
            ?.split(",")
            ?: listOf("http://localhost:3000", "http://localhost:8080")
        
        allowedOrigins.forEach { origin ->
            allowHost(origin.removePrefix("http://").removePrefix("https://"))
        }
    }
}