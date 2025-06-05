package com.ataiva.eden.hub.connector

import com.ataiva.eden.hub.engine.*
import com.ataiva.eden.hub.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration

/**
 * Slack integration connector for messaging, channels, and bot interactions
 */
class SlackConnector : IntegrationConnector {
    override val type = IntegrationType.SLACK
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    override suspend fun initialize(integration: IntegrationInstance): ConnectorResult {
        return try {
            // Validate required configuration
            val baseUrl = integration.configuration["baseUrl"] ?: "https://slack.com/api"
            
            // Validate credentials
            val authResult = validateAuthentication(integration)
            if (!authResult.success) {
                return authResult
            }
            
            ConnectorResult(
                success = true,
                message = "Slack connector initialized successfully",
                details = mapOf(
                    "baseUrl" to baseUrl,
                    "authenticated" to true
                )
            )
        } catch (e: Exception) {
            ConnectorResult(false, "Failed to initialize Slack connector: ${e.message}")
        }
    }
    
    override suspend fun reconfigure(integration: IntegrationInstance): ConnectorResult {
        return initialize(integration)
    }
    
    override suspend fun testConnection(integration: IntegrationInstance): ConnectorTestResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://slack.com/api"
            val authHeader = getAuthHeader(integration)
                ?: return ConnectorTestResult(false, "Authentication required")
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/auth.test"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val responseData = json.decodeFromString<Map<String, Any>>(response.body())
                val ok = responseData["ok"] as? Boolean ?: false
                
                if (ok) {
                    ConnectorTestResult(
                        success = true,
                        message = "Slack connection successful",
                        details = mapOf(
                            "team" to (responseData["team"] ?: "unknown"),
                            "user" to (responseData["user"] ?: "unknown"),
                            "statusCode" to response.statusCode()
                        )
                    )
                } else {
                    ConnectorTestResult(
                        success = false,
                        message = "Slack authentication failed: ${responseData["error"]}",
                        details = responseData
                    )
                }
            } else {
                ConnectorTestResult(
                    success = false,
                    message = "Slack connection failed: HTTP ${response.statusCode()}",
                    details = mapOf("statusCode" to response.statusCode(), "body" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorTestResult(false, "Slack connection test failed: ${e.message}")
        }
    }
    
    override suspend fun executeOperation(
        integration: IntegrationInstance,
        operation: String,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return when (operation) {
            "sendMessage" -> sendMessage(integration, parameters)
            "listChannels" -> listChannels(integration, parameters)
            "createChannel" -> createChannel(integration, parameters)
            "inviteToChannel" -> inviteToChannel(integration, parameters)
            "uploadFile" -> uploadFile(integration, parameters)
            "getUser" -> getUser(integration, parameters)
            "listUsers" -> listUsers(integration, parameters)
            "setStatus" -> setStatus(integration, parameters)
            "scheduleMessage" -> scheduleMessage(integration, parameters)
            "deleteMessage" -> deleteMessage(integration, parameters)
            else -> ConnectorOperationResult(
                success = false,
                message = "Unsupported operation: $operation"
            )
        }
    }
    
    override suspend fun cleanup(integration: IntegrationInstance) {
        // Cleanup any resources if needed
    }
    
    override fun getSupportedOperations(): List<ConnectorOperation> {
        return listOf(
            ConnectorOperation(
                name = "sendMessage",
                description = "Send a message to a channel or user",
                parameters = listOf(
                    OperationParameter("channel", "String", true, "Channel ID or name"),
                    OperationParameter("text", "String", true, "Message text"),
                    OperationParameter("username", "String", false, "Bot username"),
                    OperationParameter("icon_emoji", "String", false, "Bot icon emoji"),
                    OperationParameter("attachments", "List<Map>", false, "Message attachments")
                ),
                returnType = "MessageResponse"
            ),
            ConnectorOperation(
                name = "listChannels",
                description = "List channels in the workspace",
                parameters = listOf(
                    OperationParameter("types", "String", false, "Channel types: public_channel,private_channel", "public_channel"),
                    OperationParameter("limit", "Int", false, "Number of channels to return", 100)
                ),
                returnType = "List<Channel>"
            ),
            ConnectorOperation(
                name = "createChannel",
                description = "Create a new channel",
                parameters = listOf(
                    OperationParameter("name", "String", true, "Channel name"),
                    OperationParameter("is_private", "Boolean", false, "Create private channel", false)
                ),
                returnType = "Channel"
            ),
            ConnectorOperation(
                name = "inviteToChannel",
                description = "Invite users to a channel",
                parameters = listOf(
                    OperationParameter("channel", "String", true, "Channel ID"),
                    OperationParameter("users", "String", true, "Comma-separated user IDs")
                ),
                returnType = "InviteResponse"
            ),
            ConnectorOperation(
                name = "uploadFile",
                description = "Upload a file to Slack",
                parameters = listOf(
                    OperationParameter("channels", "String", false, "Comma-separated channel IDs"),
                    OperationParameter("content", "String", false, "File content"),
                    OperationParameter("filename", "String", false, "File name"),
                    OperationParameter("title", "String", false, "File title"),
                    OperationParameter("initial_comment", "String", false, "Initial comment")
                ),
                returnType = "FileResponse"
            )
        )
    }
    
    // Private helper methods
    
    private fun validateAuthentication(integration: IntegrationInstance): ConnectorResult {
        return when (integration.credentials.type) {
            CredentialType.TOKEN -> {
                if (integration.credentials.encryptedData.isBlank()) {
                    ConnectorResult(false, "Slack bot token is required")
                } else {
                    ConnectorResult(true, "Bot token authentication configured")
                }
            }
            CredentialType.OAUTH2 -> {
                ConnectorResult(true, "OAuth2 authentication configured")
            }
            else -> {
                ConnectorResult(false, "Unsupported authentication type: ${integration.credentials.type}")
            }
        }
    }
    
    private fun getAuthHeader(integration: IntegrationInstance): String? {
        return when (integration.credentials.type) {
            CredentialType.TOKEN -> {
                val token = integration.credentials.encryptedData // In production, this would be decrypted
                "Bearer $token"
            }
            CredentialType.OAUTH2 -> {
                val token = integration.credentials.encryptedData // In production, this would be decrypted
                "Bearer $token"
            }
            else -> null
        }
    }
    
    private suspend fun sendMessage(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://slack.com/api"
            val channel = parameters["channel"] as? String
                ?: return ConnectorOperationResult(false, "Channel is required")
            val text = parameters["text"] as? String
                ?: return ConnectorOperationResult(false, "Message text is required")
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mutableMapOf<String, Any>(
                "channel" to channel,
                "text" to text
            )
            
            parameters["username"]?.let { requestBody["username"] = it }
            parameters["icon_emoji"]?.let { requestBody["icon_emoji"] = it }
            parameters["attachments"]?.let { requestBody["attachments"] = it }
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/chat.postMessage"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val responseData = json.decodeFromString<Map<String, Any>>(response.body())
                val ok = responseData["ok"] as? Boolean ?: false
                
                if (ok) {
                    ConnectorOperationResult(
                        success = true,
                        message = "Message sent successfully",
                        data = responseData
                    )
                } else {
                    ConnectorOperationResult(
                        success = false,
                        message = "Failed to send message: ${responseData["error"]}",
                        data = responseData
                    )
                }
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to send message: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to send message: ${e.message}")
        }
    }
    
    private suspend fun listChannels(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://slack.com/api"
            val authHeader = getAuthHeader(integration)!!
            
            val types = parameters["types"] as? String ?: "public_channel"
            val limit = parameters["limit"] as? Int ?: 100
            
            val url = "$baseUrl/conversations.list?types=$types&limit=$limit"
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val responseData = json.decodeFromString<Map<String, Any>>(response.body())
                val ok = responseData["ok"] as? Boolean ?: false
                
                if (ok) {
                    ConnectorOperationResult(
                        success = true,
                        message = "Channels retrieved successfully",
                        data = responseData
                    )
                } else {
                    ConnectorOperationResult(
                        success = false,
                        message = "Failed to list channels: ${responseData["error"]}",
                        data = responseData
                    )
                }
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to list channels: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to list channels: ${e.message}")
        }
    }
    
    private suspend fun createChannel(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://slack.com/api"
            val name = parameters["name"] as? String
                ?: return ConnectorOperationResult(false, "Channel name is required")
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mutableMapOf<String, Any>(
                "name" to name
            )
            
            parameters["is_private"]?.let { requestBody["is_private"] = it }
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/conversations.create"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val responseData = json.decodeFromString<Map<String, Any>>(response.body())
                val ok = responseData["ok"] as? Boolean ?: false
                
                if (ok) {
                    ConnectorOperationResult(
                        success = true,
                        message = "Channel created successfully",
                        data = responseData
                    )
                } else {
                    ConnectorOperationResult(
                        success = false,
                        message = "Failed to create channel: ${responseData["error"]}",
                        data = responseData
                    )
                }
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to create channel: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to create channel: ${e.message}")
        }
    }
    
    private suspend fun inviteToChannel(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://slack.com/api"
            val channel = parameters["channel"] as? String
                ?: return ConnectorOperationResult(false, "Channel ID is required")
            val users = parameters["users"] as? String
                ?: return ConnectorOperationResult(false, "User IDs are required")
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mapOf(
                "channel" to channel,
                "users" to users
            )
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/conversations.invite"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val responseData = json.decodeFromString<Map<String, Any>>(response.body())
                val ok = responseData["ok"] as? Boolean ?: false
                
                if (ok) {
                    ConnectorOperationResult(
                        success = true,
                        message = "Users invited successfully",
                        data = responseData
                    )
                } else {
                    ConnectorOperationResult(
                        success = false,
                        message = "Failed to invite users: ${responseData["error"]}",
                        data = responseData
                    )
                }
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to invite users: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to invite users: ${e.message}")
        }
    }
    
    private suspend fun uploadFile(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://slack.com/api"
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mutableMapOf<String, Any>()
            parameters["channels"]?.let { requestBody["channels"] = it }
            parameters["content"]?.let { requestBody["content"] = it }
            parameters["filename"]?.let { requestBody["filename"] = it }
            parameters["title"]?.let { requestBody["title"] = it }
            parameters["initial_comment"]?.let { requestBody["initial_comment"] = it }
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/files.upload"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val responseData = json.decodeFromString<Map<String, Any>>(response.body())
                val ok = responseData["ok"] as? Boolean ?: false
                
                if (ok) {
                    ConnectorOperationResult(
                        success = true,
                        message = "File uploaded successfully",
                        data = responseData
                    )
                } else {
                    ConnectorOperationResult(
                        success = false,
                        message = "Failed to upload file: ${responseData["error"]}",
                        data = responseData
                    )
                }
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to upload file: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to upload file: ${e.message}")
        }
    }
    
    private suspend fun getUser(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://slack.com/api"
            val user = parameters["user"] as? String
                ?: return ConnectorOperationResult(false, "User ID is required")
            val authHeader = getAuthHeader(integration)!!
            
            val url = "$baseUrl/users.info?user=$user"
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val responseData = json.decodeFromString<Map<String, Any>>(response.body())
                val ok = responseData["ok"] as? Boolean ?: false
                
                if (ok) {
                    ConnectorOperationResult(
                        success = true,
                        message = "User retrieved successfully",
                        data = responseData
                    )
                } else {
                    ConnectorOperationResult(
                        success = false,
                        message = "Failed to get user: ${responseData["error"]}",
                        data = responseData
                    )
                }
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to get user: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to get user: ${e.message}")
        }
    }
    
    private suspend fun listUsers(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://slack.com/api"
            val authHeader = getAuthHeader(integration)!!
            
            val limit = parameters["limit"] as? Int ?: 100
            val url = "$baseUrl/users.list?limit=$limit"
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val responseData = json.decodeFromString<Map<String, Any>>(response.body())
                val ok = responseData["ok"] as? Boolean ?: false
                
                if (ok) {
                    ConnectorOperationResult(
                        success = true,
                        message = "Users retrieved successfully",
                        data = responseData
                    )
                } else {
                    ConnectorOperationResult(
                        success = false,
                        message = "Failed to list users: ${responseData["error"]}",
                        data = responseData
                    )
                }
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to list users: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to list users: ${e.message}")
        }
    }
    
    private suspend fun setStatus(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://slack.com/api"
            val authHeader = getAuthHeader(integration)!!
            
            val profile = mutableMapOf<String, Any>()
            parameters["status_text"]?.let { profile["status_text"] = it }
            parameters["status_emoji"]?.let { profile["status_emoji"] = it }
            parameters["status_expiration"]?.let { profile["status_expiration"] = it }
            
            val requestBody = mapOf("profile" to profile)
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/users.profile.set"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val responseData = json.decodeFromString<Map<String, Any>>(response.body())
                val ok = responseData["ok"] as? Boolean ?: false
                
                if (ok) {
                    ConnectorOperationResult(
                        success = true,
                        message = "Status updated successfully",
                        data = responseData
                    )
                } else {
                    ConnectorOperationResult(
                        success = false,
                        message = "Failed to set status: ${responseData["error"]}",
                        data = responseData
                    )
                }
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to set status: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to set status: ${e.message}")
        }
    }
    
    private suspend fun scheduleMessage(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://slack.com/api"
            val channel = parameters["channel"] as? String
                ?: return ConnectorOperationResult(false, "Channel is required")
            val text = parameters["text"] as? String
                ?: return ConnectorOperationResult(false, "Message text is required")
            val postAt = parameters["post_at"] as? Long
                ?: return ConnectorOperationResult(false, "Schedule time is required")
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mapOf(
                "channel" to channel,
                "text" to text,
                "post_at" to postAt
            )
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/chat.scheduleMessage"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val responseData = json.decodeFromString<Map<String, Any>>(response.body())
                val ok = responseData["ok"] as? Boolean ?: false
                
                if (ok) {
                    ConnectorOperationResult(
                        success = true,
                        message = "Message scheduled successfully",
                        data = responseData
                    )
                } else {
                    ConnectorOperationResult(
                        success = false,
                        message = "Failed to schedule message: ${responseData["error"]}",
                        data = responseData
                    )
                }
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to schedule message: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to schedule message: ${e.message}")
        }
    }
    
    private suspend fun deleteMessage(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://slack.com/api"
            val channel = parameters["channel"] as? String
                ?: return ConnectorOperationResult(false, "Channel is required")
            val ts = parameters["ts"] as? String
                ?: return ConnectorOperationResult(false, "Message timestamp is required")
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mutableMapOf(
                "channel" to channel,
                "ts" to ts
            )
            
            // Add optional parameters
            parameters["as_user"]?.let { requestBody["as_user"] = it }
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/chat.delete"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val responseData = json.decodeFromString<Map<String, Any>>(response.body())
                val ok = responseData["ok"] as? Boolean ?: false
                
                if (ok) {
                    ConnectorOperationResult(
                        success = true,
                        message = "Message deleted successfully",
                        data = responseData
                    )
                } else {
                    ConnectorOperationResult(
                        success = false,
                        message = "Failed to delete message: ${responseData["error"]}",
                        data = responseData
                    )
                }
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to delete message: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to delete message: ${e.message}")
        }
    }
    
    /**
     * Get conversation history (messages in a channel)
     */
    private suspend fun getConversationHistory(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://slack.com/api"
            val channel = parameters["channel"] as? String
                ?: return ConnectorOperationResult(false, "Channel is required")
            val authHeader = getAuthHeader(integration)!!
            
            // Optional parameters
            val latest = parameters["latest"] as? String
            val oldest = parameters["oldest"] as? String
            val inclusive = parameters["inclusive"] as? Boolean
            val limit = parameters["limit"] as? Int ?: 100
            val cursor = parameters["cursor"] as? String
            
            var url = "$baseUrl/conversations.history?channel=$channel&limit=$limit"
            latest?.let { url += "&latest=$it" }
            oldest?.let { url += "&oldest=$it" }
            inclusive?.let { url += "&inclusive=${it.toString()}" }
            cursor?.let { url += "&cursor=$it" }
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val responseData = json.decodeFromString<Map<String, Any>>(response.body())
                val ok = responseData["ok"] as? Boolean ?: false
                
                if (ok) {
                    ConnectorOperationResult(
                        success = true,
                        message = "Conversation history retrieved successfully",
                        data = responseData
                    )
                } else {
                    ConnectorOperationResult(
                        success = false,
                        message = "Failed to get conversation history: ${responseData["error"]}",
                        data = responseData
                    )
                }
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to get conversation history: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to get conversation history: ${e.message}")
        }
    }
    
    /**
     * Add a reaction to a message
     */
    private suspend fun addReaction(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://slack.com/api"
            val channel = parameters["channel"] as? String
                ?: return ConnectorOperationResult(false, "Channel is required")
            val timestamp = parameters["timestamp"] as? String
                ?: return ConnectorOperationResult(false, "Message timestamp is required")
            val name = parameters["name"] as? String
                ?: return ConnectorOperationResult(false, "Reaction name is required")
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mapOf(
                "channel" to channel,
                "timestamp" to timestamp,
                "name" to name
            )
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/reactions.add"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val responseData = json.decodeFromString<Map<String, Any>>(response.body())
                val ok = responseData["ok"] as? Boolean ?: false
                
                if (ok) {
                    ConnectorOperationResult(
                        success = true,
                        message = "Reaction added successfully",
                        data = responseData
                    )
                } else {
                    ConnectorOperationResult(
                        success = false,
                        message = "Failed to add reaction: ${responseData["error"]}",
                        data = responseData
                    )
                }
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to add reaction: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to add reaction: ${e.message}")
        }
    }
}