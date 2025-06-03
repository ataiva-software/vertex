package com.ataiva.eden.gateway.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.request.httpMethod

fun Application.configureRouting() {
    routing {
        // DIAGNOSTIC: Add basic routes for CORS testing
        get("/test") {
            call.respond(HttpStatusCode.OK, "Test endpoint")
        }
        
        post("/test") {
            call.respond(HttpStatusCode.OK, "Test POST endpoint")
        }
        
        get("/api/users") {
            call.respond(HttpStatusCode.OK, "Users endpoint")
        }
        
        post("/api/users") {
            call.respond(HttpStatusCode.OK, "Users POST endpoint")
        }
        
        // Handle all other methods for test endpoints
        route("/test") {
            handle {
                call.respond(HttpStatusCode.OK, "Test endpoint - ${call.request.httpMethod.value}")
            }
        }
        
        route("/api/users") {
            handle {
                call.respond(HttpStatusCode.OK, "Users endpoint - ${call.request.httpMethod.value}")
            }
        }
        
        println("DIAGNOSTIC: Routes configured for /test and /api/users")
    }
}