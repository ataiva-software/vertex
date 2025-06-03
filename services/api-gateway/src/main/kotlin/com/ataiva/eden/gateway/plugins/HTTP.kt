package com.ataiva.eden.gateway.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.http.*

fun Application.configureHTTP() {
    install(CORS) {
        // Add missing HTTP methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        
        // Add missing headers
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Organization-Id")
        
        // Allow credentials
        allowCredentials = true
        
        // DIAGNOSTIC: Allow any host for now to test origin configuration
        anyHost()
        
        // DIAGNOSTIC: Log CORS configuration
        println("CORS configured with anyHost(), all methods, and credentials enabled")
    }
}