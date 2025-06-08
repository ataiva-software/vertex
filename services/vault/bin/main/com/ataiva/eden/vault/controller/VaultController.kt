package com.ataiva.eden.vault.controller

import com.ataiva.eden.vault.service.VaultService
import com.ataiva.eden.vault.service.VaultResult
import com.ataiva.eden.vault.model.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.datetime.Clock

/**
 * REST API controller for Eden Vault Service
 */
class VaultController(private val vaultService: VaultService) {
    
    fun Route.vaultRoutes() {
        route("/api/v1") {
            secretsRoutes()
            externalSecretsRoutes()
            policiesRoutes()
            authRoutes()
            bulkRoutes()
            searchRoutes()
            exportImportRoutes()
        }
    }
    
    private fun Route.secretsRoutes() {
        route("/secrets") {
            // Create secret
            post {
                try {
                    val request = call.receive<CreateSecretRequest>()
                    val enrichedRequest = request.copy(
                        ipAddress = call.request.origin.remoteHost,
                        userAgent = call.request.headers["User-Agent"]
                    )
                    
                    when (val result = vaultService.createSecret(enrichedRequest)) {
                        is VaultResult.Success -> {
                            call.respond(HttpStatusCode.Created, ApiResponse.success(result.data))
                        }
                        is VaultResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<SecretResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<SecretResponse>("Internal server error"))
                }
            }
            
            // List secrets
            get {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<SecretResponse>>("userId is required"))
                    
                    val request = ListSecretsRequest(
                        userId = userId,
                        type = call.request.queryParameters["type"],
                        namePattern = call.request.queryParameters["namePattern"],
                        includeInactive = call.request.queryParameters["includeInactive"]?.toBoolean() ?: false
                    )
                    
                    when (val result = vaultService.listSecrets(request)) {
                        is VaultResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is VaultResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<SecretResponse>>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<SecretResponse>>("Internal server error"))
                }
            }
            
            // Get specific secret
            get("/{name}") {
                try {
                    val name = call.parameters["name"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<SecretValueResponse>("Secret name is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<SecretValueResponse>("userId is required"))
                    
                    val userPassword = call.request.queryParameters["userPassword"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<SecretValueResponse>("userPassword is required"))
                    
                    val request = GetSecretRequest(
                        name = name,
                        userId = userId,
                        version = call.request.queryParameters["version"]?.toIntOrNull(),
                        userPassword = userPassword,
                        ipAddress = call.request.origin.remoteHost,
                        userAgent = call.request.headers["User-Agent"]
                    )
                    
                    when (val result = vaultService.getSecret(request)) {
                        is VaultResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is VaultResult.Error -> {
                            call.respond(HttpStatusCode.NotFound, ApiResponse.error<SecretValueResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<SecretValueResponse>("Internal server error"))
                }
            }
            
            // Update secret
            put("/{name}") {
                try {
                    val name = call.parameters["name"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse.error<SecretResponse>("Secret name is required"))
                    
                    val updateData = call.receive<UpdateSecretRequest>()
                    val request = updateData.copy(
                        name = name,
                        ipAddress = call.request.origin.remoteHost,
                        userAgent = call.request.headers["User-Agent"]
                    )
                    
                    when (val result = vaultService.updateSecret(request)) {
                        is VaultResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is VaultResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<SecretResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<SecretResponse>("Internal server error"))
                }
            }
            
            // Delete secret
            delete("/{name}") {
                try {
                    val name = call.parameters["name"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("Secret name is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("userId is required"))
                    
                    val request = DeleteSecretRequest(
                        name = name,
                        userId = userId,
                        ipAddress = call.request.origin.remoteHost,
                        userAgent = call.request.headers["User-Agent"]
                    )
                    
                    when (val result = vaultService.deleteSecret(request)) {
                        is VaultResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(Unit))
                        }
                        is VaultResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Unit>("Internal server error"))
                }
            }
            
            // Get secret versions
            get("/{name}/versions") {
                try {
                    val name = call.parameters["name"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<SecretResponse>>("Secret name is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<SecretResponse>>("userId is required"))
                    
                    val request = GetSecretVersionsRequest(name = name, userId = userId)
                    
                    when (val result = vaultService.getSecretVersions(request)) {
                        is VaultResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is VaultResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<SecretResponse>>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<SecretResponse>>("Internal server error"))
                }
            }
        }
    }
    
    private fun Route.policiesRoutes() {
        route("/policies") {
            get {
                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Secret policies management",
                    "available_operations" to listOf("list", "get", "create", "update", "delete"),
                    "note" to "Policy management will be implemented in Phase 2"
                ))
            }
            
            post {
                call.respond(HttpStatusCode.NotImplemented, ApiResponse.error<Unit>("Policy creation not yet implemented"))
            }
        }
    }
    
    private fun Route.authRoutes() {
        route("/auth") {
            post("/login") {
                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Vault authentication",
                    "note" to "Authentication is handled by the API Gateway"
                ))
            }
        }
    }
    
    private fun Route.bulkRoutes() {
        route("/bulk") {
            post("/secrets") {
                try {
                    val request = call.receive<BulkSecretsRequest>()
                    val enrichedRequest = request.copy(
                        ipAddress = call.request.origin.remoteHost,
                        userAgent = call.request.headers["User-Agent"]
                    )
                    
                    val successful = mutableListOf<SecretResponse>()
                    val failed = mutableListOf<BulkOperationError>()
                    
                    for (secretRequest in enrichedRequest.secrets) {
                        val createRequest = secretRequest.copy(
                            userId = enrichedRequest.userId,
                            userPassword = enrichedRequest.userPassword,
                            ipAddress = enrichedRequest.ipAddress,
                            userAgent = enrichedRequest.userAgent
                        )
                        
                        when (val result = vaultService.createSecret(createRequest)) {
                            is VaultResult.Success -> successful.add(result.data)
                            is VaultResult.Error -> failed.add(BulkOperationError(secretRequest.name, result.message))
                        }
                    }
                    
                    val response = BulkSecretsResponse(
                        successful = successful,
                        failed = failed,
                        totalProcessed = enrichedRequest.secrets.size,
                        successCount = successful.size,
                        failureCount = failed.size
                    )
                    
                    call.respond(HttpStatusCode.OK, ApiResponse.success(response))
                    
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<BulkSecretsResponse>("Internal server error"))
                }
            }
        }
    }
    
    private fun Route.searchRoutes() {
        route("/search") {
            post("/secrets") {
                try {
                    val request = call.receive<SearchSecretsRequest>()
                    
                    val listRequest = ListSecretsRequest(
                        userId = request.userId,
                        type = request.type,
                        namePattern = request.query
                    )
                    
                    when (val result = vaultService.listSecrets(listRequest)) {
                        is VaultResult.Success -> {
                            val secrets = result.data.drop(request.offset).take(request.limit)
                            val response = SearchSecretsResponse(
                                secrets = secrets,
                                totalCount = result.data.size,
                                hasMore = result.data.size > request.offset + request.limit
                            )
                            call.respond(HttpStatusCode.OK, ApiResponse.success(response))
                        }
                        is VaultResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<SearchSecretsResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<SearchSecretsResponse>("Internal server error"))
                }
            }
        }
    }
    
    private fun Route.exportImportRoutes() {
        route("/export") {
            post("/secrets") {
                call.respond(HttpStatusCode.NotImplemented, ApiResponse.error<Unit>("Export functionality not yet implemented"))
            }
        }
        
        route("/import") {
            post("/secrets") {
                call.respond(HttpStatusCode.NotImplemented, ApiResponse.error<Unit>("Import functionality not yet implemented"))
            }
        }
    }
    
    private fun Route.externalSecretsRoutes() {
        route("/external-secrets") {
            // Store external secret
            post {
                try {
                    val request = call.receive<StoreExternalSecretRequest>()
                    val enrichedRequest = request.copy(
                        ipAddress = call.request.origin.remoteHost,
                        userAgent = call.request.headers["User-Agent"]
                    )
                    
                    when (val result = vaultService.storeExternalSecret(enrichedRequest)) {
                        is VaultResult.Success -> {
                            call.respond(HttpStatusCode.Created, ApiResponse.success(result.data))
                        }
                        is VaultResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExternalSecretResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<ExternalSecretResponse>("Internal server error"))
                }
            }
            
            // List external secrets
            get {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<ExternalSecretResponse>>("userId is required"))
                    
                    val path = call.request.queryParameters["path"] ?: ""
                    
                    val request = ListExternalSecretsRequest(
                        userId = userId,
                        path = path
                    )
                    
                    when (val result = vaultService.listExternalSecrets(request)) {
                        is VaultResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is VaultResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<List<ExternalSecretResponse>>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<List<ExternalSecretResponse>>("Internal server error"))
                }
            }
            
            // Get specific external secret
            get("/{name}") {
                try {
                    val name = call.parameters["name"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExternalSecretValueResponse>("Secret name is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExternalSecretValueResponse>("userId is required"))
                    
                    val key = call.request.queryParameters["key"]
                    
                    val request = GetExternalSecretRequest(
                        name = name,
                        userId = userId,
                        key = key,
                        ipAddress = call.request.origin.remoteHost,
                        userAgent = call.request.headers["User-Agent"]
                    )
                    
                    when (val result = vaultService.getExternalSecret(request)) {
                        is VaultResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is VaultResult.Error -> {
                            call.respond(HttpStatusCode.NotFound, ApiResponse.error<ExternalSecretValueResponse>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<ExternalSecretValueResponse>("Internal server error"))
                }
            }
            
            // Delete external secret
            delete("/{name}") {
                try {
                    val name = call.parameters["name"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("Secret name is required"))
                    
                    val userId = call.request.queryParameters["userId"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>("userId is required"))
                    
                    val deleteFromProvider = call.request.queryParameters["deleteFromProvider"]?.toBoolean() ?: false
                    
                    val request = DeleteExternalSecretRequest(
                        name = name,
                        userId = userId,
                        deleteFromProvider = deleteFromProvider,
                        ipAddress = call.request.origin.remoteHost,
                        userAgent = call.request.headers["User-Agent"]
                    )
                    
                    when (val result = vaultService.deleteExternalSecret(request)) {
                        is VaultResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(Unit))
                        }
                        is VaultResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Unit>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Unit>("Internal server error"))
                }
            }
            
            // Get external secrets manager health
            get("/health") {
                try {
                    when (val result = vaultService.getExternalSecretsManagerHealth()) {
                        is VaultResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is VaultResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExternalSecretsManagerHealth>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<ExternalSecretsManagerHealth>("Internal server error"))
                }
            }
        }
    }

    // Statistics and monitoring endpoints
    fun Route.statsRoutes() {
        route("/stats") {
            get("/secrets") {
                try {
                    val userId = call.request.queryParameters["userId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Any>("userId is required"))
                    
                    when (val result = vaultService.getSecretStats(userId)) {
                        is VaultResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is VaultResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Any>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Any>("Internal server error"))
                }
            }
        }
        
        route("/logs") {
            get("/access") {
                try {
                    val secretId = call.request.queryParameters["secretId"]
                    val userId = call.request.queryParameters["userId"]
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    
                    val request = GetAccessLogsRequest(
                        secretId = secretId,
                        userId = userId,
                        limit = limit
                    )
                    
                    when (val result = vaultService.getAccessLogs(request)) {
                        is VaultResult.Success -> {
                            call.respond(HttpStatusCode.OK, ApiResponse.success(result.data))
                        }
                        is VaultResult.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ApiResponse.error<Any>(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<Any>("Internal server error"))
                }
            }
        }
    }
}