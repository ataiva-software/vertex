package com.ataiva.eden.hub.connector

import com.ataiva.eden.hub.engine.*
import com.ataiva.eden.hub.model.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration

/**
 * GitHub integration connector for repository management, issues, and pull requests
 */
class GitHubConnector : IntegrationConnector {
    override val type = IntegrationType.GITHUB
    
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
            val baseUrl = integration.configuration["baseUrl"] ?: "https://api.github.com"
            val owner = integration.configuration["owner"]
                ?: return ConnectorResult(false, "GitHub owner/organization is required")
            
            // Validate credentials
            val authResult = validateAuthentication(integration)
            if (!authResult.success) {
                return authResult
            }
            
            ConnectorResult(
                success = true,
                message = "GitHub connector initialized successfully",
                details = mapOf(
                    "baseUrl" to baseUrl,
                    "owner" to owner,
                    "authenticated" to true
                )
            )
        } catch (e: Exception) {
            ConnectorResult(false, "Failed to initialize GitHub connector: ${e.message}")
        }
    }
    
    override suspend fun reconfigure(integration: IntegrationInstance): ConnectorResult {
        return initialize(integration)
    }
    
    override suspend fun testConnection(integration: IntegrationInstance): ConnectorTestResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://api.github.com"
            val authHeader = getAuthHeader(integration)
                ?: return ConnectorTestResult(false, "Authentication required")
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/user"))
                .header("Authorization", authHeader)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Eden-Hub/1.0")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val userInfo = json.decodeFromString<Map<String, Any>>(response.body())
                ConnectorTestResult(
                    success = true,
                    message = "GitHub connection successful",
                    details = mapOf(
                        "user" to (userInfo["login"] ?: "unknown"),
                        "name" to (userInfo["name"] ?: ""),
                        "statusCode" to response.statusCode()
                    )
                )
            } else {
                ConnectorTestResult(
                    success = false,
                    message = "GitHub connection failed: HTTP ${response.statusCode()}",
                    details = mapOf("statusCode" to response.statusCode(), "body" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorTestResult(false, "GitHub connection test failed: ${e.message}")
        }
    }
    
    override suspend fun executeOperation(
        integration: IntegrationInstance,
        operation: String,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return when (operation) {
            "listRepositories" -> listRepositories(integration, parameters)
            "getRepository" -> getRepository(integration, parameters)
            "createRepository" -> createRepository(integration, parameters)
            "listIssues" -> listIssues(integration, parameters)
            "createIssue" -> createIssue(integration, parameters)
            "updateIssue" -> updateIssue(integration, parameters)
            "listPullRequests" -> listPullRequests(integration, parameters)
            "createPullRequest" -> createPullRequest(integration, parameters)
            "mergePullRequest" -> mergePullRequest(integration, parameters)
            "createWebhook" -> createWebhook(integration, parameters)
            "listWebhooks" -> listWebhooks(integration, parameters)
            "deleteWebhook" -> deleteWebhook(integration, parameters)
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
                name = "listRepositories",
                description = "List repositories for the authenticated user or organization",
                parameters = listOf(
                    OperationParameter("type", "String", false, "Repository type: all, owner, member", "all"),
                    OperationParameter("sort", "String", false, "Sort by: created, updated, pushed, full_name", "updated"),
                    OperationParameter("direction", "String", false, "Sort direction: asc, desc", "desc"),
                    OperationParameter("per_page", "Int", false, "Results per page (max 100)", 30)
                ),
                returnType = "List<Repository>"
            ),
            ConnectorOperation(
                name = "getRepository",
                description = "Get a specific repository",
                parameters = listOf(
                    OperationParameter("repo", "String", true, "Repository name")
                ),
                returnType = "Repository"
            ),
            ConnectorOperation(
                name = "createRepository",
                description = "Create a new repository",
                parameters = listOf(
                    OperationParameter("name", "String", true, "Repository name"),
                    OperationParameter("description", "String", false, "Repository description"),
                    OperationParameter("private", "Boolean", false, "Private repository", false),
                    OperationParameter("auto_init", "Boolean", false, "Initialize with README", true)
                ),
                returnType = "Repository"
            ),
            ConnectorOperation(
                name = "listIssues",
                description = "List issues for a repository",
                parameters = listOf(
                    OperationParameter("repo", "String", true, "Repository name"),
                    OperationParameter("state", "String", false, "Issue state: open, closed, all", "open"),
                    OperationParameter("labels", "String", false, "Comma-separated list of labels"),
                    OperationParameter("assignee", "String", false, "Assignee username")
                ),
                returnType = "List<Issue>"
            ),
            ConnectorOperation(
                name = "createIssue",
                description = "Create a new issue",
                parameters = listOf(
                    OperationParameter("repo", "String", true, "Repository name"),
                    OperationParameter("title", "String", true, "Issue title"),
                    OperationParameter("body", "String", false, "Issue body"),
                    OperationParameter("assignees", "List<String>", false, "List of assignee usernames"),
                    OperationParameter("labels", "List<String>", false, "List of label names")
                ),
                returnType = "Issue"
            ),
            ConnectorOperation(
                name = "createPullRequest",
                description = "Create a new pull request",
                parameters = listOf(
                    OperationParameter("repo", "String", true, "Repository name"),
                    OperationParameter("title", "String", true, "Pull request title"),
                    OperationParameter("head", "String", true, "Branch to merge from"),
                    OperationParameter("base", "String", true, "Branch to merge into"),
                    OperationParameter("body", "String", false, "Pull request body"),
                    OperationParameter("draft", "Boolean", false, "Create as draft", false)
                ),
                returnType = "PullRequest"
            )
        )
    }
    
    // Private helper methods
    
    private fun validateAuthentication(integration: IntegrationInstance): ConnectorResult {
        return when (integration.credentials.type) {
            CredentialType.TOKEN -> {
                if (integration.credentials.encryptedData.isBlank()) {
                    ConnectorResult(false, "GitHub token is required")
                } else {
                    ConnectorResult(true, "Token authentication configured")
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
                val token = integration.credentials.encryptedData // TODO: Decrypt
                "token $token"
            }
            CredentialType.OAUTH2 -> {
                val token = integration.credentials.encryptedData // TODO: Decrypt OAuth2 token
                "Bearer $token"
            }
            else -> null
        }
    }
    
    private suspend fun listRepositories(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://api.github.com"
            val owner = integration.configuration["owner"]!!
            val authHeader = getAuthHeader(integration)!!
            
            val type = parameters["type"] as? String ?: "all"
            val sort = parameters["sort"] as? String ?: "updated"
            val direction = parameters["direction"] as? String ?: "desc"
            val perPage = parameters["per_page"] as? Int ?: 30
            
            val url = "$baseUrl/users/$owner/repos?type=$type&sort=$sort&direction=$direction&per_page=$perPage"
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Eden-Hub/1.0")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val repositories = json.decodeFromString<List<Map<String, Any>>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Repositories retrieved successfully",
                    data = mapOf("repositories" to repositories)
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to list repositories: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to list repositories: ${e.message}")
        }
    }
    
    private suspend fun getRepository(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://api.github.com"
            val owner = integration.configuration["owner"]!!
            val repo = parameters["repo"] as? String
                ?: return ConnectorOperationResult(false, "Repository name is required")
            val authHeader = getAuthHeader(integration)!!
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/repos/$owner/$repo"))
                .header("Authorization", authHeader)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Eden-Hub/1.0")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val repository = json.decodeFromString<Map<String, Any>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Repository retrieved successfully",
                    data = mapOf("repository" to repository)
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to get repository: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to get repository: ${e.message}")
        }
    }
    
    private suspend fun createRepository(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://api.github.com"
            val name = parameters["name"] as? String
                ?: return ConnectorOperationResult(false, "Repository name is required")
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mapOf(
                "name" to name,
                "description" to (parameters["description"] as? String ?: ""),
                "private" to (parameters["private"] as? Boolean ?: false),
                "auto_init" to (parameters["auto_init"] as? Boolean ?: true)
            )
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/user/repos"))
                .header("Authorization", authHeader)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Eden-Hub/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 201) {
                val repository = json.decodeFromString<Map<String, Any>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Repository created successfully",
                    data = mapOf("repository" to repository)
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to create repository: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to create repository: ${e.message}")
        }
    }
    
    private suspend fun listIssues(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://api.github.com"
            val owner = integration.configuration["owner"]!!
            val repo = parameters["repo"] as? String
                ?: return ConnectorOperationResult(false, "Repository name is required")
            val authHeader = getAuthHeader(integration)!!
            
            val state = parameters["state"] as? String ?: "open"
            val labels = parameters["labels"] as? String ?: ""
            val assignee = parameters["assignee"] as? String ?: ""
            
            var url = "$baseUrl/repos/$owner/$repo/issues?state=$state"
            if (labels.isNotEmpty()) url += "&labels=$labels"
            if (assignee.isNotEmpty()) url += "&assignee=$assignee"
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Eden-Hub/1.0")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val issues = json.decodeFromString<List<Map<String, Any>>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Issues retrieved successfully",
                    data = mapOf("issues" to issues)
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
    
    private suspend fun createIssue(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://api.github.com"
            val owner = integration.configuration["owner"]!!
            val repo = parameters["repo"] as? String
                ?: return ConnectorOperationResult(false, "Repository name is required")
            val title = parameters["title"] as? String
                ?: return ConnectorOperationResult(false, "Issue title is required")
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mutableMapOf<String, Any>(
                "title" to title
            )
            
            parameters["body"]?.let { requestBody["body"] = it }
            parameters["assignees"]?.let { requestBody["assignees"] = it }
            parameters["labels"]?.let { requestBody["labels"] = it }
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/repos/$owner/$repo/issues"))
                .header("Authorization", authHeader)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Eden-Hub/1.0")
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
            val baseUrl = integration.configuration["baseUrl"] ?: "https://api.github.com"
            val owner = integration.configuration["owner"]!!
            val repo = parameters["repo"] as? String
                ?: return ConnectorOperationResult(false, "Repository name is required")
            val issueNumber = parameters["issue_number"] as? Int
                ?: return ConnectorOperationResult(false, "Issue number is required")
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mutableMapOf<String, Any>()
            parameters["title"]?.let { requestBody["title"] = it }
            parameters["body"]?.let { requestBody["body"] = it }
            parameters["state"]?.let { requestBody["state"] = it }
            parameters["assignees"]?.let { requestBody["assignees"] = it }
            parameters["labels"]?.let { requestBody["labels"] = it }
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/repos/$owner/$repo/issues/$issueNumber"))
                .header("Authorization", authHeader)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Eden-Hub/1.0")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val issue = json.decodeFromString<Map<String, Any>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Issue updated successfully",
                    data = mapOf("issue" to issue)
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
    
    private suspend fun listPullRequests(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://api.github.com"
            val owner = integration.configuration["owner"]!!
            val repo = parameters["repo"] as? String
                ?: return ConnectorOperationResult(false, "Repository name is required")
            val authHeader = getAuthHeader(integration)!!
            
            val state = parameters["state"] as? String ?: "open"
            val head = parameters["head"] as? String ?: ""
            val base = parameters["base"] as? String ?: ""
            
            var url = "$baseUrl/repos/$owner/$repo/pulls?state=$state"
            if (head.isNotEmpty()) url += "&head=$head"
            if (base.isNotEmpty()) url += "&base=$base"
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Eden-Hub/1.0")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val pullRequests = json.decodeFromString<List<Map<String, Any>>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Pull requests retrieved successfully",
                    data = mapOf("pullRequests" to pullRequests)
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to list pull requests: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to list pull requests: ${e.message}")
        }
    }
    
    private suspend fun createPullRequest(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://api.github.com"
            val owner = integration.configuration["owner"]!!
            val repo = parameters["repo"] as? String
                ?: return ConnectorOperationResult(false, "Repository name is required")
            val title = parameters["title"] as? String
                ?: return ConnectorOperationResult(false, "Pull request title is required")
            val head = parameters["head"] as? String
                ?: return ConnectorOperationResult(false, "Head branch is required")
            val base = parameters["base"] as? String
                ?: return ConnectorOperationResult(false, "Base branch is required")
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mutableMapOf<String, Any>(
                "title" to title,
                "head" to head,
                "base" to base
            )
            
            parameters["body"]?.let { requestBody["body"] = it }
            parameters["draft"]?.let { requestBody["draft"] = it }
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/repos/$owner/$repo/pulls"))
                .header("Authorization", authHeader)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Eden-Hub/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 201) {
                val pullRequest = json.decodeFromString<Map<String, Any>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Pull request created successfully",
                    data = mapOf("pullRequest" to pullRequest)
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to create pull request: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to create pull request: ${e.message}")
        }
    }
    
    private suspend fun mergePullRequest(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://api.github.com"
            val owner = integration.configuration["owner"]!!
            val repo = parameters["repo"] as? String
                ?: return ConnectorOperationResult(false, "Repository name is required")
            val pullNumber = parameters["pull_number"] as? Int
                ?: return ConnectorOperationResult(false, "Pull request number is required")
            val authHeader = getAuthHeader(integration)!!
            
            val requestBody = mutableMapOf<String, Any>()
            parameters["commit_title"]?.let { requestBody["commit_title"] = it }
            parameters["commit_message"]?.let { requestBody["commit_message"] = it }
            parameters["merge_method"]?.let { requestBody["merge_method"] = it }
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/repos/$owner/$repo/pulls/$pullNumber/merge"))
                .header("Authorization", authHeader)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Eden-Hub/1.0")
                .PUT(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val mergeResult = json.decodeFromString<Map<String, Any>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Pull request merged successfully",
                    data = mapOf("merge" to mergeResult)
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to merge pull request: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to merge pull request: ${e.message}")
        }
    }
    
    private suspend fun createWebhook(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://api.github.com"
            val owner = integration.configuration["owner"]!!
            val repo = parameters["repo"] as? String
                ?: return ConnectorOperationResult(false, "Repository name is required")
            val url = parameters["url"] as? String
                ?: return ConnectorOperationResult(false, "Webhook URL is required")
            val authHeader = getAuthHeader(integration)!!
            
            val events = parameters["events"] as? List<String> ?: listOf("push", "pull_request")
            val secret = parameters["secret"] as? String
            
            val config = mutableMapOf<String, Any>(
                "url" to url,
                "content_type" to "json"
            )
            secret?.let { config["secret"] = it }
            
            val requestBody = mapOf(
                "name" to "web",
                "active" to true,
                "events" to events,
                "config" to config
            )
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/repos/$owner/$repo/hooks"))
                .header("Authorization", authHeader)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Eden-Hub/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 201) {
                val webhook = json.decodeFromString<Map<String, Any>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Webhook created successfully",
                    data = mapOf("webhook" to webhook)
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to create webhook: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to create webhook: ${e.message}")
        }
    }
    
    private suspend fun listWebhooks(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://api.github.com"
            val owner = integration.configuration["owner"]!!
            val repo = parameters["repo"] as? String
                ?: return ConnectorOperationResult(false, "Repository name is required")
            val authHeader = getAuthHeader(integration)!!
val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/repos/$owner/$repo/hooks"))
                .header("Authorization", authHeader)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Eden-Hub/1.0")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val webhooks = json.decodeFromString<List<Map<String, Any>>>(response.body())
                ConnectorOperationResult(
                    success = true,
                    message = "Webhooks retrieved successfully",
                    data = mapOf("webhooks" to webhooks)
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to list webhooks: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to list webhooks: ${e.message}")
        }
    }
    
    private suspend fun deleteWebhook(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val baseUrl = integration.configuration["baseUrl"] ?: "https://api.github.com"
            val owner = integration.configuration["owner"]!!
            val repo = parameters["repo"] as? String
                ?: return ConnectorOperationResult(false, "Repository name is required")
            val hookId = parameters["hook_id"] as? Int
                ?: return ConnectorOperationResult(false, "Webhook ID is required")
            val authHeader = getAuthHeader(integration)!!
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/repos/$owner/$repo/hooks/$hookId"))
                .header("Authorization", authHeader)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Eden-Hub/1.0")
                .DELETE()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 204) {
                ConnectorOperationResult(
                    success = true,
                    message = "Webhook deleted successfully",
                    data = mapOf("hookId" to hookId)
                )
            } else {
                ConnectorOperationResult(
                    success = false,
                    message = "Failed to delete webhook: HTTP ${response.statusCode()}",
                    data = mapOf("statusCode" to response.statusCode(), "error" to response.body())
                )
            }
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to delete webhook: ${e.message}")
        }
    }
}