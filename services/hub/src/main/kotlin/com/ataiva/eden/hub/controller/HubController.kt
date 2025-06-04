package com.ataiva.eden.hub.controller

import com.ataiva.eden.hub.service.HubService
import com.ataiva.eden.hub.model.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.datetime.Clock

/**
 * REST API controller for Eden Hub Service
 */
class HubController(private val hubService: HubService) {
    
    fun Route.hubRoutes() {
        route("/api/v1") {
            integrationsRoutes()
            webhooksRoutes()
            notificationsRoutes()
            eventsRoutes()
            healthRoutes()
        }
    }
    
    private fun Route.integrationsRoutes() {
        route("/integrations") {
            // Create integration
            post {
                try {
                    val request = call.receive<CreateIntegrationRequest>()
                    val enrichedRequest = request.copy(
                        // Add any request enrichment if needed
                    )
                    
                    when (val result = hubService.createIntegration(enrichedRequest)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.Created, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<IntegrationResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<IntegrationResponse>("Internal server error"))
                }
            }
            
            // List integrations
            get {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<IntegrationResponse>>("userId is required"))
                    
                    val organizationId = call.request.queryParameters["organizationId"]
                    
                    when (val result = hubService.listIntegrations(userId, organizationId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<IntegrationResponse>>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<IntegrationResponse>>("Internal server error"))
                }
            }
            
            // Get specific integration
            get("/{id}") {
                try {
                    val integrationId = call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<IntegrationResponse>("Integration ID is required"))
                    
                    when (val result = hubService.getIntegration(integrationId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.NotFound, ApiResponse.error<IntegrationResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<IntegrationResponse>("Internal server error"))
                }
            }
            
            // Update integration
            put("/{id}") {
                try {
                    val integrationId = call.parameters["id"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse.error<IntegrationResponse>("Integration ID is required"))
                    
                    val request = call.receive<UpdateIntegrationRequest>()
                    val enrichedRequest = request.copy(id = integrationId)
                    
                    when (val result = hubService.updateIntegration(enrichedRequest)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<IntegrationResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<IntegrationResponse>("Internal server error"))
                }
            }
            
            // Delete integration
            delete("/{id}") {
                try {
                    val integrationId = call.parameters["id"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("Integration ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("userId is required"))
                    
                    when (val result = hubService.deleteIntegration(integrationId, userId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.NoContent, ApiResponse.success(Unit))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Unit>("Internal server error"))
                }
            }
            
            // Test integration
            post("/{id}/test") {
                try {
                    val integrationId = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<IntegrationTestResult>("Integration ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<IntegrationTestResult>("userId is required"))
                    
                    when (val result = hubService.testIntegration(integrationId, userId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<IntegrationTestResult>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<IntegrationTestResult>("Internal server error"))
                }
            }
            
            // Execute integration operation
            post("/{id}/execute") {
                try {
                    val integrationId = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Map<String, Any>>("Integration ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Map<String, Any>>("userId is required"))
                    
                    val requestBody = call.receive<Map<String, Any>>()
                    val operation = requestBody["operation"] as? String
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Map<String, Any>>("Operation is required"))
                    
                    val parameters = requestBody["parameters"] as? Map<String, Any> ?: emptyMap()
                    
                    when (val result = hubService.executeIntegrationOperation(integrationId, operation, parameters, userId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Map<String, Any>>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Map<String, Any>>("Internal server error"))
                }
            }
        }
    }
    
    private fun Route.webhooksRoutes() {
        route("/webhooks") {
            // Create webhook
            post {
                try {
                    val request = call.receive<CreateWebhookRequest>()
                    
                    when (val result = hubService.createWebhook(request)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.Created, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WebhookResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<WebhookResponse>("Internal server error"))
                }
            }
            
            // List webhooks
            get {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<WebhookResponse>>("userId is required"))
                    
                    val organizationId = call.request.queryParameters["organizationId"]
                    
                    when (val result = hubService.listWebhooks(userId, organizationId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<WebhookResponse>>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<WebhookResponse>>("Internal server error"))
                }
            }
            
            // Get specific webhook
            get("/{id}") {
                try {
                    val webhookId = call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WebhookResponse>("Webhook ID is required"))
                    
                    when (val result = hubService.getWebhook(webhookId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.NotFound, ApiResponse.error<WebhookResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<WebhookResponse>("Internal server error"))
                }
            }
            
            // Update webhook
            put("/{id}") {
                try {
                    val webhookId = call.parameters["id"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WebhookResponse>("Webhook ID is required"))
                    
                    val request = call.receive<UpdateWebhookRequest>()
                    val enrichedRequest = request.copy(id = webhookId)
                    
                    when (val result = hubService.updateWebhook(enrichedRequest)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WebhookResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<WebhookResponse>("Internal server error"))
                }
            }
            
            // Delete webhook
            delete("/{id}") {
                try {
                    val webhookId = call.parameters["id"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("Webhook ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("userId is required"))
                    
                    when (val result = hubService.deleteWebhook(webhookId, userId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.NoContent, ApiResponse.success(Unit))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Unit>("Internal server error"))
                }
            }
            
            // Test webhook
            post("/{id}/test") {
                try {
                    val webhookId = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WebhookDeliveryResponse>("Webhook ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WebhookDeliveryResponse>("userId is required"))
                    
                    when (val result = hubService.testWebhook(webhookId, userId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WebhookDeliveryResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<WebhookDeliveryResponse>("Internal server error"))
                }
            }
            
            // Deliver webhook
            post("/{id}/deliver") {
                try {
                    val webhookId = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WebhookDeliveryResponse>("Webhook ID is required"))
                    
                    val requestBody = call.receive<Map<String, Any>>()
                    val event = requestBody["event"] as? String
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WebhookDeliveryResponse>("Event is required"))
                    
                    val payload = requestBody["payload"] as? Map<String, Any> ?: emptyMap()
                    
                    val deliveryRequest = WebhookDeliveryRequest(
                        webhookId = webhookId,
                        event = event,
                        payload = payload
                    )
                    
                    when (val result = hubService.deliverWebhook(deliveryRequest)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.Accepted, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WebhookDeliveryResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<WebhookDeliveryResponse>("Internal server error"))
                }
            }
            
            // Get webhook deliveries
            get("/{id}/deliveries") {
                try {
                    val webhookId = call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<WebhookDeliveryResponse>>("Webhook ID is required"))
                    
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    
                    when (val result = hubService.listWebhookDeliveries(webhookId, limit)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<WebhookDeliveryResponse>>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<WebhookDeliveryResponse>>("Internal server error"))
                }
            }
            
            // Get delivery status
            get("/deliveries/{id}") {
                try {
                    val deliveryId = call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<WebhookDeliveryResponse>("Delivery ID is required"))
                    
                    when (val result = hubService.getWebhookDelivery(deliveryId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.NotFound, ApiResponse.error<WebhookDeliveryResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<WebhookDeliveryResponse>("Internal server error"))
                }
            }
        }
    }
    
    private fun Route.notificationsRoutes() {
        route("/notifications") {
            // Send notification
            post("/send") {
                try {
                    val request = call.receive<SendNotificationRequest>()
                    
                    when (val result = hubService.sendNotification(request)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.Accepted, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<NotificationDeliveryResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<NotificationDeliveryResponse>("Internal server error"))
                }
            }
            
            // Get notification deliveries
            get("/deliveries") {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<NotificationDeliveryResponse>>("userId is required"))
                    
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    
                    when (val result = hubService.listNotificationDeliveries(userId, limit)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<NotificationDeliveryResponse>>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<NotificationDeliveryResponse>>("Internal server error"))
                }
            }
            
            // Get delivery status
            get("/deliveries/{id}") {
                try {
                    val deliveryId = call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<NotificationDeliveryResponse>("Delivery ID is required"))
                    
                    when (val result = hubService.getNotificationDelivery(deliveryId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.NotFound, ApiResponse.error<NotificationDeliveryResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<NotificationDeliveryResponse>("Internal server error"))
                }
            }
            
            // Cancel notification
            delete("/deliveries/{id}") {
                try {
                    val deliveryId = call.parameters["id"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("Delivery ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("userId is required"))
                    
                    when (val result = hubService.cancelNotification(deliveryId, userId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.NoContent, ApiResponse.success(Unit))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Unit>("Internal server error"))
                }
            }
            
            // Template management
            route("/templates") {
                // Create template
                post {
                    try {
                        val request = call.receive<CreateNotificationTemplateRequest>()
                        
                        when (val result = hubService.createNotificationTemplate(request)) {
                            is HubResult.Success -> {
                                call.respond(HttpStatusCode.Created, ApiResponse.success(result.data))
                            }
                            is HubResult.Error -> {
                                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<NotificationTemplateResponse>(result.message))
                            }
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<NotificationTemplateResponse>("Internal server error"))
                    }
                }
                
                // List templates
                get {
                    try {
                        val userId = call.request.queryParameters["userId"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<NotificationTemplateResponse>>("userId is required"))
                        
                        val organizationId = call.request.queryParameters["organizationId"]
                        
                        when (val result = hubService.listNotificationTemplates(userId, organizationId)) {
                            is HubResult.Success -> {
                                call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                            }
                            is HubResult.Error -> {
                                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<NotificationTemplateResponse>>(result.message))
                            }
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<NotificationTemplateResponse>>("Internal server error"))
                    }
                }
                
                // Get template
                get("/{id}") {
                    try {
                        val templateId = call.parameters["id"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<NotificationTemplateResponse>("Template ID is required"))
                        
                        when (val result = hubService.getNotificationTemplate(templateId)) {
                            is HubResult.Success -> {
                                call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                            }
                            is HubResult.Error -> {
                                call.respond(HttpStatusCode.NotFound, ApiResponse.error<NotificationTemplateResponse>(result.message))
                            }
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<NotificationTemplateResponse>("Internal server error"))
                    }
                }
                
                // Update template
                put("/{id}") {
                    try {
                        val templateId = call.parameters["id"]
                            ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse.error<NotificationTemplateResponse>("Template ID is required"))
                        
                        val request = call.receive<UpdateNotificationTemplateRequest>()
                        val enrichedRequest = request.copy(id = templateId)
                        
                        when (val result = hubService.updateNotificationTemplate(enrichedRequest)) {
                            is HubResult.Success -> {
                                call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                            }
                            is HubResult.Error -> {
                                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<NotificationTemplateResponse>(result.message))
                            }
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<NotificationTemplateResponse>("Internal server error"))
                    }
                }
                
                // Delete template
                delete("/{id}") {
                    try {
                        val templateId = call.parameters["id"]
                            ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("Template ID is required"))
                        
                        val userId = call.request.queryParameters["userId"]
                            ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("userId is required"))
                        
                        when (val result = hubService.deleteNotificationTemplate(templateId, userId)) {
                            is HubResult.Success -> {
                                call.respond(HttpStatusCode.NoContent, ApiResponse.success(Unit))
                            }
                            is HubResult.Error -> {
                                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>(result.message))
                            }
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Unit>("Internal server error"))
                    }
                }
            }
        }
    }
    
    private fun Route.eventsRoutes() {
        route("/events") {
            // Subscribe to events
            post("/subscribe") {
                try {
                    val requestBody = call.receive<Map<String, Any>>()
                    val eventTypes = requestBody["eventTypes"] as? List<String>
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<EventSubscription>("eventTypes is required"))
                    
                    val endpoint = requestBody["endpoint"] as? String
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<EventSubscription>("endpoint is required"))
                    
                    val secret = requestBody["secret"] as? String
                    val userId = requestBody["userId"] as? String
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse.error<EventSubscription>("userId is required"))
                    
                    when (val result = hubService.subscribeToEvents(eventTypes, endpoint, secret, userId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.Created, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<EventSubscription>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<EventSubscription>("Internal server error"))
                }
            }
            
            // Unsubscribe from events
            delete("/subscribe/{id}") {
                try {
                    val subscriptionId = call.parameters["id"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("Subscription ID is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("userId is required"))
                    
                    when (val result = hubService.unsubscribeFromEvents(subscriptionId, userId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.NoContent, ApiResponse.success(Unit))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Unit>("Internal server error"))
                }
            }
            
            // List event subscriptions
            get("/subscriptions") {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<EventSubscription>>("userId is required"))
                    
                    when (val result = hubService.listEventSubscriptions(userId)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<EventSubscription>>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<EventSubscription>>("Internal server error"))
                }
            }
            
            // Publish event
            post("/publish") {
                try {
                    val event = call.receive<HubEvent>()
                    
                    when (val result = hubService.publishEvent(event)) {
                        is HubResult.Success -> {
                            call.respond(HttpStatusCode.Accepted, ApiResponse.success(Unit))
                        }
                        is HubResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Unit>("Internal server error"))
                }
            }
        }
    }
    
    private fun Route.healthRoutes() {
        // Health check
        get("/health") {
            try {
                val healthResponse = hubService.getHealthStatus()
                call.respond(HttpStatusCode.OK, ApiResponse.success(healthResponse))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<HubHealthResponse>("Health check failed"))
            }
        }
        
        // Service statistics
        get("/stats") {
            try {
                val stats = hubService.getServiceStatistics()
                call.respond(HttpStatusCode.OK, ApiResponse.success(stats))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Map<String, Any>>("Failed to get statistics"))
            }
        }
    }
}