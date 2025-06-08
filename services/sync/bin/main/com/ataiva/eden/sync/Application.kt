package com.ataiva.eden.sync

import com.ataiva.eden.sync.controller.SyncController
import com.ataiva.eden.sync.service.SyncService
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureContentNegotiation()
    configureRouting()
}

fun Application.configureContentNegotiation() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

fun Application.configureRouting() {
    val syncService = SyncService()
    val syncController = SyncController(syncService)
    
    routing {
        syncController.configureRoutes(this)
    }
}