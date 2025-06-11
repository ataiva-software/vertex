package com.ataiva.eden.hub.engine

import com.ataiva.eden.hub.model.*
import com.ataiva.eden.hub.connector.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration as JavaDuration

/**
 * Core integration engine that manages all external service integrations
 */
class IntegrationEngine(
    private val encryption: Any, // Placeholder for actual encryption implementation
    private val secureRandom: Any // Placeholder for actual secure random implementation
) {
    private val connectors = ConcurrentHashMap<IntegrationType, IntegrationConnector>()
    private val activeIntegrations = ConcurrentHashMap<String, IntegrationInstance>()
    private val authenticationManager = AuthenticationManager(encryption)
    private val eventSubscriptions = ConcurrentHashMap<String, EventSubscription>()
    private val deliveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Register a connector for a specific integration type
     */
    fun registerConnector(type: IntegrationType, connector: IntegrationConnector) {
        connectors[type] = connector
    }
    
    /**
     * Register all available connectors
     */
    fun registerConnectors(
        githubConnector: GitHubConnector,
        slackConnector: SlackConnector,
        jiraConnector: JiraConnector,
        awsConnector: AwsConnector
    ) {
        registerConnector(IntegrationType.GITHUB, githubConnector)
        registerConnector(IntegrationType.SLACK, slackConnector)
        registerConnector(IntegrationType.JIRA, jiraConnector)
        registerConnector(IntegrationType.AWS, awsConnector)
    }
    
    /**
     * Subscribe to integration events
     */
    fun subscribeToEvents(subscription: EventSubscription): HubResult<EventSubscription> {
        return try {
            if (subscription.eventTypes.isEmpty()) {
                return HubResult.Error("At least one event type must be specified")
            }
            
            eventSubscriptions[subscription.id] = subscription
            HubResult.Success(subscription)
        } catch (e: Exception) {
            HubResult.Error("Failed to subscribe to events: ${e.message}")
        }
    }
    
    /**
     * Unsubscribe from integration events
     */
    fun unsubscribeFromEvents(subscriptionId: String): HubResult<Unit> {
        return try {
            eventSubscriptions.remove(subscriptionId)
            HubResult.Success(Unit)
        } catch (e: Exception) {
            HubResult.Error("Failed to unsubscribe from events: ${e.message}")
        }
    }
    
    /**
     * Create and configure a new integration
     */
    suspend fun createIntegration(request: CreateIntegrationRequest): HubResult<IntegrationResponse> {
        return try {
            // Validate integration type
            val connector = connectors[request.type]
                ?: return HubResult.Error("Unsupported integration type: ${request.type}")
            
            // Encrypt credentials
            val encryptedCredentials = authenticationManager.encryptCredentials(request.credentials)
            
            // Create integration instance
            val integrationId = java.util.UUID.randomUUID().toString()
            val integration = IntegrationInstance(
                id = integrationId,
                name = request.name,
                type = request.type,
                description = request.description,
                configuration = request.configuration,
                credentials = encryptedCredentials,
                status = IntegrationStatus.CONFIGURING,
                userId = request.userId,
                organizationId = request.organizationId,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            
            // Initialize connector
            val initResult = connector.initialize(integration)
            if (!initResult.success) {
                return HubResult.Error("Failed to initialize integration: ${initResult.message}")
            }
            
            // Store active integration
            activeIntegrations[integrationId] = integration.copy(status = IntegrationStatus.ACTIVE)
            
            HubResult.Success(integration.toResponse())
            
        } catch (e: Exception) {
            HubResult.Error("Failed to create integration: ${e.message}")
        }
    }
    
    /**
     * Update an existing integration
     */
    suspend fun updateIntegration(request: UpdateIntegrationRequest): HubResult<IntegrationResponse> {
        return try {
            val integration = activeIntegrations[request.id]
                ?: return HubResult.Error("Integration not found: ${request.id}")
            
            val connector = connectors[integration.type]
                ?: return HubResult.Error("Connector not available for type: ${integration.type}")
            
            // Update integration properties
            val updatedIntegration = integration.copy(
                name = request.name ?: integration.name,
                description = request.description ?: integration.description,
                configuration = request.configuration ?: integration.configuration,
                credentials = request.credentials?.let { authenticationManager.encryptCredentials(it) } ?: integration.credentials,
                status = if (request.isActive == false) IntegrationStatus.INACTIVE else integration.status,
                updatedAt = Clock.System.now()
            )
            
            // Reconfigure connector if needed
            if (request.configuration != null || request.credentials != null) {
                val reconfigResult = connector.reconfigure(updatedIntegration)
                if (!reconfigResult.success) {
                    return HubResult.Error("Failed to reconfigure integration: ${reconfigResult.message}")
                }
            }
            
            activeIntegrations[request.id] = updatedIntegration
            HubResult.Success(updatedIntegration.toResponse())
            
        } catch (e: Exception) {
            HubResult.Error("Failed to update integration: ${e.message}")
        }
    }
    
    /**
     * Test an integration connection
     */
    suspend fun testIntegration(integrationId: String): HubResult<IntegrationTestResult> {
        return try {
            val integration = activeIntegrations[integrationId]
                ?: return HubResult.Error("Integration not found: $integrationId")
            
            val connector = connectors[integration.type]
                ?: return HubResult.Error("Connector not available for type: ${integration.type}")
            
            val startTime = System.currentTimeMillis()
            val testResult = connector.testConnection(integration)
            val responseTime = System.currentTimeMillis() - startTime
            
            // Update integration status based on test result
            val updatedStatus = if (testResult.success) IntegrationStatus.ACTIVE else IntegrationStatus.ERROR
            activeIntegrations[integrationId] = integration.copy(
                status = updatedStatus,
                lastTestAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            
            HubResult.Success(IntegrationTestResult(
                success = testResult.success,
                message = testResult.message,
                responseTime = responseTime,
                details = testResult.details
            ))
            
        } catch (e: Exception) {
            HubResult.Error("Failed to test integration: ${e.message}")
        }
    }
    
    /**
     * Execute an operation on an integration
     */
    suspend fun executeOperation(
        integrationId: String,
        operation: String,
        parameters: Map<String, Any>
    ): HubResult<Map<String, Any>> {
        return try {
            val integration = activeIntegrations[integrationId]
                ?: return HubResult.Error("Integration not found: $integrationId")
            
            if (integration.status != IntegrationStatus.ACTIVE) {
                return HubResult.Error("Integration is not active: ${integration.status}")
            }
            
            val connector = connectors[integration.type]
                ?: return HubResult.Error("Connector not available for type: ${integration.type}")
            
            val result = connector.executeOperation(integration, operation, parameters)
            if (result.success) {
                HubResult.Success(result.data)
            } else {
                HubResult.Error(result.message)
            }
            
        } catch (e: Exception) {
            HubResult.Error("Failed to execute operation: ${e.message}")
        }
    }
    
    /**
     * Get integration by ID
     */
    fun getIntegration(integrationId: String): HubResult<IntegrationResponse> {
        val integration = activeIntegrations[integrationId]
            ?: return HubResult.Error("Integration not found: $integrationId")
        
        return HubResult.Success(integration.toResponse())
    }
    
    /**
     * List all integrations for a user
     */
    fun listIntegrations(userId: String, organizationId: String? = null): HubResult<List<IntegrationResponse>> {
        val userIntegrations = activeIntegrations.values.filter { integration ->
            integration.userId == userId && 
            (organizationId == null || integration.organizationId == organizationId)
        }
        
        return HubResult.Success(userIntegrations.map { it.toResponse() })
    }
    
    /**
     * Delete an integration
     */
    suspend fun deleteIntegration(integrationId: String): HubResult<Unit> {
        return try {
            val integration = activeIntegrations[integrationId]
                ?: return HubResult.Error("Integration not found: $integrationId")
            
            val connector = connectors[integration.type]
            connector?.cleanup(integration)
            
            activeIntegrations.remove(integrationId)
            HubResult.Success(Unit)
            
        } catch (e: Exception) {
            HubResult.Error("Failed to delete integration: ${e.message}")
        }
    }
    
    /**
     * Get health status of all integrations
     */
    fun getIntegrationsHealth(): IntegrationsHealth {
        val totalIntegrations = activeIntegrations.size
        val activeIntegrationsCount = activeIntegrations.values.count { it.status == IntegrationStatus.ACTIVE }
        val errorIntegrations = activeIntegrations.values.count { it.status == IntegrationStatus.ERROR }
        val lastTestAt = activeIntegrations.values.mapNotNull { it.lastTestAt }.maxOrNull()
        
        return IntegrationsHealth(
            totalIntegrations = totalIntegrations,
            activeIntegrations = activeIntegrationsCount,
            errorIntegrations = errorIntegrations,
            lastTestAt = lastTestAt
        )
    }
    /**
     * Process integration events
     */
    suspend fun processEvent(event: HubEvent): HubResult<Unit> {
        return try {
            // Find all subscriptions that match this event type
            val matchingSubscriptions = eventSubscriptions.values
                .filter { subscription ->
                    subscription.isActive && (
                        subscription.eventTypes.contains(event.type) ||
                        subscription.eventTypes.contains("*")
                    )
                }
            
            // Deliver event to each subscriber
            matchingSubscriptions.forEach { subscription ->
                deliveryScope.launch {
                    deliverEventToSubscriber(event, subscription)
                }
            }
            
            HubResult.Success(Unit)
        } catch (e: Exception) {
            HubResult.Error("Failed to process event: ${e.message}")
        }
    }
    
    /**
     * Deliver event to a subscriber
     */
    private suspend fun deliverEventToSubscriber(event: HubEvent, subscription: EventSubscription) {
        try {
            val httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build()
            
            val payload = mapOf(
                "id" to event.id,
                "type" to event.type,
                "source" to event.source,
                "data" to event.data,
                "timestamp" to event.timestamp.toString(),
                "userId" to event.userId,
                "organizationId" to event.organizationId
            )
            
            val payloadJson = Json.encodeToString(payload)
            
            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(subscription.endpoint))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Eden-Hub-Integration/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
            
            // Add signature if secret is provided
            subscription.secret?.let { secret ->
                val signature = generateSignature(payloadJson, secret)
                requestBuilder.header("X-Hub-Signature-256", "sha256=$signature")
            }
            
            val request = requestBuilder.build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            // Log delivery result
            if (response.statusCode() !in 200..299) {
                // In production, implement proper logging and retry mechanism
                println("Failed to deliver event ${event.id} to ${subscription.endpoint}: HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            // In production, implement proper error handling and retry mechanism
            println("Error delivering event ${event.id} to ${subscription.endpoint}: ${e.message}")
        }
    }
    
    /**
     * Generate HMAC signature for webhook payload
     */
    private fun generateSignature(payload: String, secret: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val secretKeySpec = javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        mac.init(secretKeySpec)
        val signature = mac.doFinal(payload.toByteArray())
        return signature.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Base interface for all integration connectors
 */
interface IntegrationConnector {
    val type: IntegrationType
    
    /**
     * Initialize the connector with integration configuration
     */
    suspend fun initialize(integration: IntegrationInstance): ConnectorResult
    
    /**
     * Reconfigure the connector with updated settings
     */
    suspend fun reconfigure(integration: IntegrationInstance): ConnectorResult
    
    /**
     * Test the connection to the external service
     */
    suspend fun testConnection(integration: IntegrationInstance): ConnectorTestResult
    
    /**
     * Execute a specific operation
     */
    suspend fun executeOperation(
        integration: IntegrationInstance,
        operation: String,
        parameters: Map<String, Any>
    ): ConnectorOperationResult
    
    /**
     * Cleanup resources when integration is deleted
     */
    suspend fun cleanup(integration: IntegrationInstance)
    
    /**
     * Get supported operations for this connector
     */
    fun getSupportedOperations(): List<ConnectorOperation>
}

/**
 * Integration instance data class
 */
data class IntegrationInstance(
    val id: String,
    val name: String,
    val type: IntegrationType,
    val description: String?,
    val configuration: Map<String, String>,
    val credentials: IntegrationCredentials,
    val status: IntegrationStatus,
    val lastTestAt: Instant? = null,
    val userId: String,
    val organizationId: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toResponse(): IntegrationResponse {
        return IntegrationResponse(
            id = id,
            name = name,
            type = type,
            description = description,
            configuration = configuration,
            isActive = status == IntegrationStatus.ACTIVE,
            status = status,
            lastTestAt = lastTestAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            userId = userId,
            organizationId = organizationId
        )
    }
}

/**
 * Connector operation definition
 */
data class ConnectorOperation(
    val name: String,
    val description: String,
    val parameters: List<OperationParameter>,
    val returnType: String
)

/**
 * Operation parameter definition
 */
data class OperationParameter(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String,
    val defaultValue: Any? = null
)

/**
 * Connector result types
 */
data class ConnectorResult(
    val success: Boolean,
    val message: String,
    val details: Map<String, Any> = emptyMap()
)

data class ConnectorTestResult(
    val success: Boolean,
    val message: String,
    val details: Map<String, Any> = emptyMap()
)

data class ConnectorOperationResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any> = emptyMap()
)

/**
 * Authentication manager for handling credentials
 */
class AuthenticationManager(
    private val encryption: Any // Placeholder for actual encryption implementation
) {
    
    /**
     * Encrypt integration credentials
     */
    suspend fun encryptCredentials(credentials: IntegrationCredentials): IntegrationCredentials {
        // If already encrypted, return as-is
        if (credentials.encryptionKeyId.isNotEmpty()) {
            return credentials
        }
        
        // Generate a secure random key and ID
        val encryptionKey = ByteArray(32).apply {
            java.security.SecureRandom().nextBytes(this)
        }
        val encryptionKeyId = java.util.UUID.randomUUID().toString()
        
        // Encrypt the data (simplified implementation)
        val encryptedData = java.util.Base64.getEncoder().encodeToString(
            credentials.encryptedData.toByteArray()
        )
        
        return credentials.copy(
            encryptedData = encryptedData,
            encryptionKeyId = encryptionKeyId
        )
    }
    
    /**
     * Decrypt integration credentials
     */
    suspend fun decryptCredentials(credentials: IntegrationCredentials): String? {
        return try {
            if (credentials.encryptionKeyId.isEmpty()) {
                return credentials.encryptedData
            }
            
            val encryptedData = java.util.Base64.getDecoder().decode(credentials.encryptedData)
            val encryptionKey = getEncryptionKey(credentials.encryptionKeyId)
            
            // Simplified decryption (just decode the Base64)
            String(encryptedData)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get encryption key by ID (placeholder - should integrate with key management)
     */
    private fun getEncryptionKey(keyId: String): ByteArray {
        // TODO: Integrate with proper key management system
        return keyId.toByteArray().copyOf(32)
    }
    
    /**
     * Create OAuth2 authentication flow
     */
    suspend fun createOAuth2Flow(
        config: OAuth2Config,
        integrationId: String
    ): HubResult<String> {
        return try {
            val state = generateRandomString(32)
            val authUrl = buildOAuth2AuthUrl(config, state, integrationId)
            
            // Store state for validation (in production, use proper storage)
            storeOAuth2State(integrationId, state)
            
            HubResult.Success(authUrl)
        } catch (e: Exception) {
            HubResult.Error("Failed to create OAuth2 flow: ${e.message}")
        }
    }
    
    /**
     * Handle OAuth2 callback
     */
    suspend fun handleOAuth2Callback(
        integrationId: String,
        code: String,
        state: String,
        config: OAuth2Config
    ): HubResult<OAuth2Token> {
        return try {
            // Validate state
            if (!validateOAuth2State(integrationId, state)) {
                return HubResult.Error("Invalid OAuth2 state")
            }
            
            // Exchange code for token
            val token = exchangeOAuth2Code(code, config)
            
            // Clean up state
            removeOAuth2State(integrationId)
            
            HubResult.Success(token)
        } catch (e: Exception) {
            HubResult.Error("Failed to handle OAuth2 callback: ${e.message}")
        }
    }
    
    /**
     * Refresh OAuth2 token
     */
    suspend fun refreshOAuth2Token(
        refreshToken: String,
        config: OAuth2Config
    ): HubResult<OAuth2Token> {
        return try {
            val token = refreshOAuth2TokenInternal(refreshToken, config)
            HubResult.Success(token)
        } catch (e: Exception) {
            HubResult.Error("Failed to refresh OAuth2 token: ${e.message}")
        }
    }
    
    // OAuth2 helper methods (simplified implementations)
    private fun buildOAuth2AuthUrl(config: OAuth2Config, state: String, integrationId: String): String {
        val scopes = config.scopes.joinToString(" ")
        return "${config.authorizationUrl}?" +
                "client_id=${config.clientId}&" +
                "response_type=code&" +
                "scope=$scopes&" +
                "state=$state&" +
                "redirect_uri=${config.redirectUri}"
    }
    
    // Map to store OAuth2 state values with expiration times
    private val oauth2StateStore = ConcurrentHashMap<String, OAuth2StateEntry>()
    
    private fun storeOAuth2State(integrationId: String, state: String) {
        // Store state with expiration time (10 minutes from now)
        val expirationTime = Clock.System.now().plus(kotlin.time.Duration.parse("PT10M"))
        oauth2StateStore[integrationId] = OAuth2StateEntry(state, expirationTime)
        
        // Schedule cleanup of expired states
        scheduleStateCleanup()
    }
    
    private fun validateOAuth2State(integrationId: String, state: String): Boolean {
        val stateEntry = oauth2StateStore[integrationId] ?: return false
        
        // Check if state has expired
        if (Clock.System.now() > stateEntry.expirationTime) {
            removeOAuth2State(integrationId)
            return false
        }
        
        // Validate state using constant-time comparison to prevent timing attacks
        return constantTimeEquals(stateEntry.state, state)
    }
    
    private fun removeOAuth2State(integrationId: String) {
        oauth2StateStore.remove(integrationId)
    }
    
    // Constant-time string comparison to prevent timing attacks
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) {
            return false
        }
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        
        return result == 0
    }
    
    // Periodically clean up expired states
    private fun scheduleStateCleanup() {
        CoroutineScope(Dispatchers.IO).launch {
            delay(kotlin.time.Duration.parse("PT5M").inWholeMilliseconds)
            cleanupExpiredStates()
        }
    }
    
    private fun cleanupExpiredStates() {
        val now = Clock.System.now()
        val expiredKeys = oauth2StateStore.entries
            .filter { now > it.value.expirationTime }
            .map { it.key }
        
        expiredKeys.forEach { oauth2StateStore.remove(it) }
    }
    
    // Data class to store OAuth2 state with expiration time
    private data class OAuth2StateEntry(
        val state: String,
        val expirationTime: Instant
    )
    
    private suspend fun exchangeOAuth2Code(code: String, config: OAuth2Config): OAuth2Token {
        try {
            // Create HTTP client
            val client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build()
            
            // Prepare request body
            val requestBody = mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "client_id" to config.clientId,
                "client_secret" to config.clientSecret,
                "redirect_uri" to config.redirectUri
            ).entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }
            
            // Create request
            val request = HttpRequest.newBuilder()
                .uri(URI.create(config.tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
            
            // Send request and parse response
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() !in 200..299) {
                throw SecurityException("OAuth2 token exchange failed: HTTP ${response.statusCode()}")
            }
            
            // Parse JSON response
            val responseJson = Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(response.body())
            
            // Extract token information
            val accessToken = responseJson["access_token"]?.toString()?.trim('"')
                ?: throw SecurityException("Missing access_token in OAuth2 response")
            
            val refreshToken = responseJson["refresh_token"]?.toString()?.trim('"')
            val expiresIn = responseJson["expires_in"]?.toString()?.toIntOrNull() ?: 3600
            
            return OAuth2Token(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = expiresIn.toLong()
            )
        } catch (e: Exception) {
            throw SecurityException("OAuth2 token exchange failed: ${e.message}", e)
        }
    }
    
    private suspend fun refreshOAuth2TokenInternal(refreshToken: String, config: OAuth2Config): OAuth2Token {
        try {
            // Create HTTP client
            val client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build()
            
            // Prepare request body
            val requestBody = mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to config.clientId,
                "client_secret" to config.clientSecret
            ).entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }
            
            // Create request
            val request = HttpRequest.newBuilder()
                .uri(URI.create(config.tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
            
            // Send request and parse response
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() !in 200..299) {
                throw SecurityException("OAuth2 token refresh failed: HTTP ${response.statusCode()}")
            }
            
            // Parse JSON response
            val responseJson = Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(response.body())
            
            // Extract token information
            val accessToken = responseJson["access_token"]?.toString()?.trim('"')
                ?: throw SecurityException("Missing access_token in OAuth2 response")
            
            // Some providers may issue a new refresh token
            val newRefreshToken = responseJson["refresh_token"]?.toString()?.trim('"') ?: refreshToken
            val expiresIn = responseJson["expires_in"]?.toString()?.toIntOrNull() ?: 3600
            
            return OAuth2Token(
                accessToken = accessToken,
                refreshToken = newRefreshToken,
                expiresIn = expiresIn.toLong()
            )
        } catch (e: Exception) {
            throw SecurityException("OAuth2 token refresh failed: ${e.message}", e)
        }
    }
    
    // Helper function to generate random string
    private fun generateRandomString(length: Int, charset: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"): String {
        val random = java.security.SecureRandom()
        return (1..length)
            .map { charset[random.nextInt(charset.length)] }
            .joinToString("")
    }
}