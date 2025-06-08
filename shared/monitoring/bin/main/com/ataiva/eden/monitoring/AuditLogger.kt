package com.ataiva.eden.monitoring

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

/**
 * Audit event type
 */
enum class AuditEventType {
    USER_LOGIN,
    USER_LOGOUT,
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,
    RESOURCE_CREATED,
    RESOURCE_UPDATED,
    RESOURCE_DELETED,
    RESOURCE_ACCESSED,
    CONFIGURATION_CHANGED,
    SECURITY_ALERT,
    API_ACCESS,
    DATA_EXPORT,
    ADMIN_ACTION,
    SYSTEM_EVENT
}

/**
 * Audit event
 */
data class AuditEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = Instant.now().toEpochMilli(),
    val type: AuditEventType,
    val userId: String? = null,
    val username: String? = null,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val action: String,
    val outcome: String,
    val details: Map<String, String> = emptyMap(),
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val traceId: String? = null,
    val spanId: String? = null
)

/**
 * Audit logger for security events
 */
class AuditLogger(openTelemetry: OpenTelemetry, serviceName: String) {
    private val logger: Logger = openTelemetry.getLogsBridge()
        .loggerBuilder(serviceName)
        .setInstrumentationVersion("1.0.0")
        .setSchemaUrl("https://opentelemetry.io/schemas/1.18.0")
        .build()
    
    private val json = Json { 
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Log an audit event
     */
    fun logAuditEvent(event: AuditEvent) {
        val currentSpan = Span.current()
        val traceId = event.traceId ?: currentSpan.spanContext.traceId
        val spanId = event.spanId ?: currentSpan.spanContext.spanId
        
        val logRecordBuilder = logger.logRecordBuilder()
            .setSeverity(Severity.INFO)
            .setSeverityText("INFO")
            .setBody(json.encodeToString(event))
            .setTimestamp(event.timestamp, java.util.concurrent.TimeUnit.MILLISECONDS)
        
        // Add attributes
        val attributesBuilder = Attributes.builder()
            .put("audit.event.id", event.id)
            .put("audit.event.type", event.type.name)
            .put("audit.event.action", event.action)
            .put("audit.event.outcome", event.outcome)
        
        event.userId?.let { attributesBuilder.put("audit.user.id", it) }
        event.username?.let { attributesBuilder.put("audit.user.name", it) }
        event.resourceType?.let { attributesBuilder.put("audit.resource.type", it) }
        event.resourceId?.let { attributesBuilder.put("audit.resource.id", it) }
        event.ipAddress?.let { attributesBuilder.put(SemanticAttributes.NET_SOCK_PEER_ADDR, it) }
        event.userAgent?.let { attributesBuilder.put(SemanticAttributes.USER_AGENT_ORIGINAL, it) }
        
        // Add trace context
        logRecordBuilder.setContext(Context.current())
        
        // Add all attributes
        logRecordBuilder.setAllAttributes(attributesBuilder.build())
        
        // Emit the log record
        logRecordBuilder.emit()
    }
    
    /**
     * Log a user login event
     */
    fun logUserLogin(
        userId: String,
        username: String,
        outcome: String,
        ipAddress: String? = null,
        userAgent: String? = null,
        details: Map<String, String> = emptyMap()
    ) {
        logAuditEvent(
            AuditEvent(
                type = AuditEventType.USER_LOGIN,
                userId = userId,
                username = username,
                action = "login",
                outcome = outcome,
                ipAddress = ipAddress,
                userAgent = userAgent,
                details = details
            )
        )
    }
    
    /**
     * Log a user logout event
     */
    fun logUserLogout(
        userId: String,
        username: String,
        ipAddress: String? = null,
        userAgent: String? = null
    ) {
        logAuditEvent(
            AuditEvent(
                type = AuditEventType.USER_LOGOUT,
                userId = userId,
                username = username,
                action = "logout",
                outcome = "success",
                ipAddress = ipAddress,
                userAgent = userAgent
            )
        )
    }
    
    /**
     * Log a resource access event
     */
    fun logResourceAccess(
        userId: String,
        username: String,
        resourceType: String,
        resourceId: String,
        action: String,
        outcome: String,
        ipAddress: String? = null,
        userAgent: String? = null,
        details: Map<String, String> = emptyMap()
    ) {
        logAuditEvent(
            AuditEvent(
                type = AuditEventType.RESOURCE_ACCESSED,
                userId = userId,
                username = username,
                resourceType = resourceType,
                resourceId = resourceId,
                action = action,
                outcome = outcome,
                ipAddress = ipAddress,
                userAgent = userAgent,
                details = details
            )
        )
    }
    
    /**
     * Log a configuration change event
     */
    fun logConfigurationChange(
        userId: String,
        username: String,
        configName: String,
        oldValue: String?,
        newValue: String?,
        ipAddress: String? = null,
        userAgent: String? = null
    ) {
        logAuditEvent(
            AuditEvent(
                type = AuditEventType.CONFIGURATION_CHANGED,
                userId = userId,
                username = username,
                resourceType = "configuration",
                resourceId = configName,
                action = "update",
                outcome = "success",
                ipAddress = ipAddress,
                userAgent = userAgent,
                details = mapOf(
                    "oldValue" to (oldValue ?: "null"),
                    "newValue" to (newValue ?: "null")
                )
            )
        )
    }
    
    /**
     * Log a security alert
     */
    fun logSecurityAlert(
        alertType: String,
        description: String,
        severity: String,
        userId: String? = null,
        username: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        details: Map<String, String> = emptyMap()
    ) {
        logAuditEvent(
            AuditEvent(
                type = AuditEventType.SECURITY_ALERT,
                userId = userId,
                username = username,
                action = alertType,
                outcome = severity,
                ipAddress = ipAddress,
                userAgent = userAgent,
                details = details + mapOf("description" to description)
            )
        )
    }
    
    /**
     * Log an admin action
     */
    fun logAdminAction(
        userId: String,
        username: String,
        action: String,
        targetType: String,
        targetId: String,
        outcome: String,
        ipAddress: String? = null,
        userAgent: String? = null,
        details: Map<String, String> = emptyMap()
    ) {
        logAuditEvent(
            AuditEvent(
                type = AuditEventType.ADMIN_ACTION,
                userId = userId,
                username = username,
                resourceType = targetType,
                resourceId = targetId,
                action = action,
                outcome = outcome,
                ipAddress = ipAddress,
                userAgent = userAgent,
                details = details
            )
        )
    }
}