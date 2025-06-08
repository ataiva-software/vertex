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
import java.util.Base64

/**
 * JIRA integration connector for issue management and project workflows
 */
class JiraConnector : IntegrationConnector {
    override val type = IntegrationType.JIRA
    
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
            val baseUrl = integration.configuration["baseUrl"]
                ?: return ConnectorResult(false, "JIRA base URL is required")
            
            // Validate credentials
            val authResult = validateAuthentication(integration)
            if (!authResult.success) {
                return authResult
            }
            
            ConnectorResult(
                success = true,
                message = "JIRA connector initialized successfully",
                details = mapOf(
                    "baseUrl" to baseUrl,
                    "authenticated" to true
                )
            )
        } catch (e: Exception) {
            ConnectorResult(false, "Failed to initialize JIRA connector: ${e.message}")
        }
    }
    
    override suspend fun reconfigure(integration: IntegrationInstance): ConnectorResult {
        return initialize(integration)
    }
    
    override suspend fun testConnection(integration: IntegrationInstance): ConnectorTestResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"]!!
            val authHeader = getAuthHeader(integration)
                ?: return ConnectorTestResult(false, "Authentication required")
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/rest/api/2/myself"))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val userInfo = json.decodeFromString<Map<String, Any>>(response.body())
                ConnectorTestResult(
                    success = true,
                    message = "JIRA connection successful",
                    details = mapOf(
                        "user" to (userInfo["displayName"] ?: "unknown"),
                        "accountId" to (userInfo["accountId"] ?: ""),
                        "statusCode" to response.statusCode()
                    )
                )
            } else {
                ConnectorTestResult(
                    success = false,
                    message = "JIRA connection failed: HTTP ${response.statusCode()}",
                    details = mapOf("statusCode" to response.statusCode(), "body" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorTestResult(false, "JIRA connection test failed: ${e.message}")
        }
    }
    
    override suspend fun executeOperation(
        integration: IntegrationInstance,
        operation: String,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return when (operation) {
            "listProjects" -> listProjects(integration, parameters)
            "getProject" -> getProject(integration, parameters)
            "listIssues" -> listIssues(integration, parameters)
            "getIssue" -> getIssue(integration, parameters)
            "createIssue" -> createIssue(integration, parameters)
            "updateIssue" -> updateIssue(integration, parameters)
            "transitionIssue" -> transitionIssue(integration, parameters)
            "addComment" -> addComment(integration, parameters)
            "listTransitions" -> listTransitions(integration, parameters)
            "assignIssue" -> assignIssue(integration, parameters)
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
                name = "listProjects",
                description = "List all projects accessible to the user",
                parameters = listOf(
                    OperationParameter("expand", "String", false, "Expand project details", ""),
                    OperationParameter("recent", "Int", false, "Number of recent projects", 20)
                ),
                returnType = "List<Project>"
            ),
            ConnectorOperation(
                name = "getProject",
                description = "Get a specific project by key or ID",
                parameters = listOf(
                    OperationParameter("projectIdOrKey", "String", true, "Project ID or key")
                ),
                returnType = "Project"
            ),
            ConnectorOperation(
                name = "listIssues",
                description = "Search for issues using JQL",
                parameters = listOf(
                    OperationParameter("jql", "String", false, "JQL query", ""),
                    OperationParameter("startAt", "Int", false, "Start index", 0),
                    OperationParameter("maxResults", "Int", false, "Maximum results", 50),
                    OperationParameter("fields", "String", false, "Fields to include", "*all")
                ),
                returnType = "SearchResult"
            ),
            ConnectorOperation(
                name = "getIssue",
                description = "Get a specific issue by key or ID",
                parameters = listOf(
                    OperationParameter("issueIdOrKey", "String", true, "Issue ID or key"),
                    OperationParameter("fields", "String", false, "Fields to include", "*all")
                ),
                returnType = "Issue"
            ),
            ConnectorOperation(
                name = "createIssue",
                description = "Create a new issue",
                parameters = listOf(
                    OperationParameter("project", "String", true, "Project key or ID"),
                    OperationParameter("issueType", "String", true, "Issue type name or ID"),
                    OperationParameter("summary", "String", true, "Issue summary"),
                    OperationParameter("description", "String", false, "Issue description"),
                    OperationParameter("assignee", "String", false, "Assignee account ID"),
                    OperationParameter("priority", "String", false, "Priority name or ID"),
                    OperationParameter("labels", "List<String>", false, "Issue labels")
                ),
                returnType = "Issue"
            ),
            ConnectorOperation(
                name = "updateIssue",
                description = "Update an existing issue",
                parameters = listOf(
                    OperationParameter("issueIdOrKey", "String", true, "Issue ID or key"),
                    OperationParameter("summary", "String", false, "Issue summary"),
                    OperationParameter("description", "String", false, "Issue description"),
                    OperationParameter("assignee", "String", false, "Assignee account ID"),
                    OperationParameter("priority", "String", false, "Priority name or ID"),
                    OperationParameter("labels", "List<String>", false, "Issue labels")
                ),
                returnType = "Issue"
            ),
            ConnectorOperation(
                name = "transitionIssue",
                description = "Transition an issue to a different status",
                parameters = listOf(
                    OperationParameter("issueIdOrKey", "String", true, "Issue ID or key"),
                    OperationParameter("transitionId", "String", true, "Transition ID"),
                    OperationParameter("comment", "String", false, "Transition comment")
                ),
                returnType = "TransitionResult"
            )
        )
    }
    
    // Private helper methods
    
    private fun validateAuthentication(integration: IntegrationInstance): ConnectorResult {
        return when (integration.credentials.type) {
            CredentialType.BASIC_AUTH -> {
                if (integration.credentials.encryptedData.isBlank()) {
                    ConnectorResult(false, "JIRA username and password are required")
                } else {
                    ConnectorResult(true, "Basic authentication configured")
                }
            }
            CredentialType.API_KEY -> {
                if (integration.credentials.encryptedData.isBlank()) {
                    ConnectorResult(false, "JIRA API token is required")
                } else {
                    ConnectorResult(true, "API token authentication configured")
                }
            }
            else -> {
                ConnectorResult(false, "Unsupported authentication type: ${integration.credentials.type}")
            }
        }
    }
    
    private fun getAuthHeader(integration: IntegrationInstance): String? {
        return when (integration.credentials.type) {
            CredentialType.BASIC_AUTH -> {
                val credentials = integration.credentials.encryptedData // TODO: Decrypt
                "Basic ${Base64.getEncoder().encodeToString(credentials.toByteArray())}"
            }
            CredentialType.API_KEY -> {
                val token = integration.credentials.encryptedData // TODO: Decrypt
                "Bearer $token"
            }
            else -> null
        }
    }
    
    private suspend fun listProjects(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"]!!
            val authHeader = getAuthHeader(integration)!!
            
            val expand = parameters["expand"] as? String ?: ""
            val recent = parameters["recent"] as? Int ?: 20
            
            var url = "$baseUrl/rest/api/2/project"
            if (expand.isNotEmpty()) {
                url += "?expand=$expand"
            }
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val projects = json.decodeFromString<List<Map<String, Any>>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Projects retrieved successfully",
                    data = mapOf("projects" to projects.take(recent))
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to list projects: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to list projects: ${e.message}")
        }
    }
    
    private suspend fun getProject(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"]!!
            val projectIdOrKey = parameters["projectIdOrKey"] as? String
                ?: return ConnectorOperationResult(false, "Project ID or key is required")
            val authHeader = getAuthHeader(integration)!!
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/rest/api/2/project/$projectIdOrKey"))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val project = json.decodeFromString<Map<String, Any>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Project retrieved successfully",
                    data = mapOf("project" to project)
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to get project: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to get project: ${e.message}")
        }
    }
    
    private suspend fun listIssues(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"]!!
            val authHeader = getAuthHeader(integration)!!
            
            val jql = parameters["jql"] as? String ?: ""
            val startAt = parameters["startAt"] as? Int ?: 0
            val maxResults = parameters["maxResults"] as? Int ?: 50
            val fields = parameters["fields"] as? String ?: "*all"
            
            val requestBody = mapOf(
                "jql" to jql,
                "startAt" to startAt,
                "maxResults" to maxResults,
                "fields" to fields.split(",")
            )
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/rest/api/2/search"))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val searchResult = json.decodeFromString<Map<String, Any>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Issues retrieved successfully",
                    data = searchResult
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to list issues: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to list issues: ${e.message}")
        }
    }
    
    private suspend fun getIssue(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"]!!
            val issueIdOrKey = parameters["issueIdOrKey"] as? String
                ?: return ConnectorOperationResult(false, "Issue ID or key is required")
            val authHeader = getAuthHeader(integration)!!
            
            val fields = parameters["fields"] as? String ?: "*all"
            val url = "$baseUrl/rest/api/2/issue/$issueIdOrKey?fields=$fields"
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val issue = json.decodeFromString<Map<String, Any>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Issue retrieved successfully",
                    data = mapOf("issue" to issue)
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to get issue: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to get issue: ${e.message}")
        }
    }
    
    private suspend fun createIssue(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"]!!
            val project = parameters["project"] as? String
                ?: return ConnectorOperationResult(false, "Project is required")
            val issueType = parameters["issueType"] as? String
                ?: return ConnectorOperationResult(false, "Issue type is required")
            val summary = parameters["summary"] as? String
                ?: return ConnectorOperationResult(false, "Summary is required")
            val authHeader = getAuthHeader(integration)!!
            
            val fields = mutableMapOf<String, Any>(
                "project" to mapOf("key" to project),
                "issuetype" to mapOf("name" to issueType),
                "summary" to summary
            )
            
            parameters["description"]?.let { fields["description"] = it }
            parameters["assignee"]?.let { fields["assignee"] = mapOf("accountId" to it) }
            parameters["priority"]?.let { fields["priority"] = mapOf("name" to it) }
            parameters["labels"]?.let { fields["labels"] = it }
            
            val requestBody = mapOf("fields" to fields)
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/rest/api/2/issue"))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 201) {
                val issue = json.decodeFromString<Map<String, Any>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Issue created successfully",
                    data = mapOf("issue" to issue)
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to create issue: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to create issue: ${e.message}")
        }
    }
    
    private suspend fun updateIssue(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"]!!
            val issueIdOrKey = parameters["issueIdOrKey"] as? String
                ?: return ConnectorOperationResult(false, "Issue ID or key is required")
            val authHeader = getAuthHeader(integration)!!
            
            val fields = mutableMapOf<String, Any>()
            parameters["summary"]?.let { fields["summary"] = it }
            parameters["description"]?.let { fields["description"] = it }
            parameters["assignee"]?.let { fields["assignee"] = mapOf("accountId" to it) }
            parameters["priority"]?.let { fields["priority"] = mapOf("name" to it) }
            parameters["labels"]?.let { fields["labels"] = it }
            
            if (fields.isEmpty()) {
                return ConnectorOperationResult(false, "No fields to update")
            }
            
            val requestBody = mapOf("fields" to fields)
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/rest/api/2/issue/$issueIdOrKey"))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 204) {
                ConnectorOperationResult(
                    success = true,
                    message = "Issue updated successfully",
                    data = mapOf("issueKey" to issueIdOrKey)
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to update issue: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to update issue: ${e.message}")
        }
    }
    
    private suspend fun transitionIssue(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"]!!
            val issueIdOrKey = parameters["issueIdOrKey"] as? String
                ?: return ConnectorOperationResult(false, "Issue ID or key is required")
            val transitionId = parameters["transitionId"] as? String
                ?: return ConnectorOperationResult(false, "Transition ID is required")
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mutableMapOf<String, Any>(
                "transition" to mapOf("id" to transitionId)
            )
            
            parameters["comment"]?.let { comment ->
                requestBody["update"] = mapOf(
                    "comment" to listOf(
                        mapOf("add" to mapOf("body" to comment))
                    )
                )
            }
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/rest/api/2/issue/$issueIdOrKey/transitions"))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 204) {
                ConnectorOperationResult(
                    success = true,
                    message = "Issue transitioned successfully",
                    data = mapOf(
                        "issueKey" to issueIdOrKey,
                        "transitionId" to transitionId
                    )
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to transition issue: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to transition issue: ${e.message}")
        }
    }
    
    private suspend fun addComment(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"]!!
            val issueIdOrKey = parameters["issueIdOrKey"] as? String
                ?: return ConnectorOperationResult(false, "Issue ID or key is required")
            val comment = parameters["comment"] as? String
                ?: return ConnectorOperationResult(false, "Comment is required")
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mapOf("body" to comment)
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/rest/api/2/issue/$issueIdOrKey/comment"))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 201) {
                val commentResult = json.decodeFromString<Map<String, Any>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Comment added successfully",
                    data = mapOf("comment" to commentResult)
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to add comment: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to add comment: ${e.message}")
        }
    }
    
    private suspend fun listTransitions(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"]!!
            val issueIdOrKey = parameters["issueIdOrKey"] as? String
                ?: return ConnectorOperationResult(false, "Issue ID or key is required")
            val authHeader = getAuthHeader(integration)!!
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/rest/api/2/issue/$issueIdOrKey/transitions"))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val transitions = json.decodeFromString<Map<String, Any>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Transitions retrieved successfully",
                    data = transitions
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to list transitions: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to list transitions: ${e.message}")
        }
    }
    
    private suspend fun assignIssue(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"]!!
            val issueIdOrKey = parameters["issueIdOrKey"] as? String
                ?: return ConnectorOperationResult(false, "Issue ID or key is required")
            val assignee = parameters["assignee"] as? String
                ?: return ConnectorOperationResult(false, "Assignee account ID is required")
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mapOf("accountId" to assignee)
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/rest/api/2/issue/$issueIdOrKey/assignee"))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 204) {
                ConnectorOperationResult(
                    success = true,
                    message = "Issue assigned successfully",
                    data = mapOf(
                        "issueKey" to issueIdOrKey,
                        "assignee" to assignee
                    )
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to assign issue: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to assign issue: ${e.message}")
        }
    }
}