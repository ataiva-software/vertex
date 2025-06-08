package com.ataiva.eden.monitoring

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.UUID

/**
 * Security logger for Eden
 * Provides structured security event logging with standardized formats
 */
class SecurityLogger(
    private val serviceName: String,
    private val environment: String,
    private val metricsCollector: MetricsCollector? = null
) {
    private val logger = LoggerFactory.getLogger("security")
    
    /**
     * Log an authentication event
     */
    fun logAuthEvent(
        eventType: AuthEventType,
        userId: String? = null,
        username: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        success: Boolean = true,
        failureReason: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val eventId = UUID.randomUUID().toString()
        val timestamp = Clock.System.now()
        
        val event = SecurityEvent(
            id = eventId,
            timestamp = timestamp,
            type = "AUTH",
            subType = eventType.name,
            serviceName = serviceName,
            environment = environment,
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            userAgent = userAgent,
            success = success,
            failureReason = failureReason,
            metadata = metadata
        )
        
        logEvent(event)
        
        // Increment metrics counter
        metricsCollector?.incrementCounter(
            name = "security_auth_events_total",
            tags = mapOf(
                "event_type" to eventType.name,
                "success" to success.toString(),
                "service" to serviceName
            )
        )
    }
    
    /**
     * Log an access control event
     */
    fun logAccessControlEvent(
        eventType: AccessControlEventType,
        userId: String,
        resource: String,
        action: String,
        ipAddress: String? = null,
        userAgent: String? = null,
        success: Boolean = true,
        failureReason: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val eventId = UUID.randomUUID().toString()
        val timestamp = Clock.System.now()
        
        val event = SecurityEvent(
            id = eventId,
            timestamp = timestamp,
            type = "ACCESS_CONTROL",
            subType = eventType.name,
            serviceName = serviceName,
            environment = environment,
            userId = userId,
            resource = resource,
            action = action,
            ipAddress = ipAddress,
            userAgent = userAgent,
            success = success,
            failureReason = failureReason,
            metadata = metadata
        )
        
        logEvent(event)
        
        // Increment metrics counter
        metricsCollector?.incrementCounter(
            name = "security_access_control_events_total",
            tags = mapOf(
                "event_type" to eventType.name,
                "success" to success.toString(),
                "resource" to resource.split(":")[0],
                "action" to action,
                "service" to serviceName
            )
        )
    }
    
    /**
     * Log a data access event
     */
    fun logDataAccessEvent(
        eventType: DataAccessEventType,
        userId: String,
        resource: String,
        action: String,
        ipAddress: String? = null,
        userAgent: String? = null,
        success: Boolean = true,
        failureReason: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val eventId = UUID.randomUUID().toString()
        val timestamp = Clock.System.now()
        
        val event = SecurityEvent(
            id = eventId,
            timestamp = timestamp,
            type = "DATA_ACCESS",
            subType = eventType.name,
            serviceName = serviceName,
            environment = environment,
            userId = userId,
            resource = resource,
            action = action,
            ipAddress = ipAddress,
            userAgent = userAgent,
            success = success,
            failureReason = failureReason,
            metadata = metadata
        )
        
        logEvent(event)
        
        // Increment metrics counter
        metricsCollector?.incrementCounter(
            name = "security_data_access_events_total",
            tags = mapOf(
                "event_type" to eventType.name,
                "success" to success.toString(),
                "resource" to resource.split(":")[0],
                "action" to action,
                "service" to serviceName
            )
        )
    }
    
    /**
     * Log a security configuration event
     */
    fun logSecurityConfigEvent(
        eventType: SecurityConfigEventType,
        userId: String,
        resource: String,
        action: String,
        ipAddress: String? = null,
        userAgent: String? = null,
        success: Boolean = true,
        failureReason: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val eventId = UUID.randomUUID().toString()
        val timestamp = Clock.System.now()
        
        val event = SecurityEvent(
            id = eventId,
            timestamp = timestamp,
            type = "SECURITY_CONFIG",
            subType = eventType.name,
            serviceName = serviceName,
            environment = environment,
            userId = userId,
            resource = resource,
            action = action,
            ipAddress = ipAddress,
            userAgent = userAgent,
            success = success,
            failureReason = failureReason,
            metadata = metadata
        )
        
        logEvent(event)
        
        // Increment metrics counter
        metricsCollector?.incrementCounter(
            name = "security_config_events_total",
            tags = mapOf(
                "event_type" to eventType.name,
                "success" to success.toString(),
                "resource" to resource.split(":")[0],
                "action" to action,
                "service" to serviceName
            )
        )
    }
    
    /**
     * Log a security threat event
     */
    fun logThreatEvent(
        eventType: ThreatEventType,
        ipAddress: String? = null,
        userAgent: String? = null,
        userId: String? = null,
        resource: String? = null,
        severity: ThreatSeverity = ThreatSeverity.MEDIUM,
        metadata: Map<String, String> = emptyMap()
    ) {
        val eventId = UUID.randomUUID().toString()
        val timestamp = Clock.System.now()
        
        val event = SecurityEvent(
            id = eventId,
            timestamp = timestamp,
            type = "THREAT",
            subType = eventType.name,
            serviceName = serviceName,
            environment = environment,
            userId = userId,
            resource = resource,
            ipAddress = ipAddress,
            userAgent = userAgent,
            severity = severity.name,
            metadata = metadata
        )
        
        logEvent(event)
        
        // Increment metrics counter
        metricsCollector?.incrementCounter(
            name = "security_threat_events_total",
            tags = mapOf(
                "event_type" to eventType.name,
                "severity" to severity.name,
                "service" to serviceName
            )
        )
        
        // For high severity threats, also increment a separate counter for alerting
        if (severity == ThreatSeverity.HIGH || severity == ThreatSeverity.CRITICAL) {
            metricsCollector?.incrementCounter(
                name = "security_high_severity_threats_total",
                tags = mapOf(
                    "event_type" to eventType.name,
                    "severity" to severity.name,
                    "service" to serviceName
                )
            )
        }
    }
    
    /**
     * Log a security event
     */
    private fun logEvent(event: SecurityEvent) {
        try {
            // Add event details to MDC for structured logging
            MDC.put("security_event_id", event.id)
            MDC.put("security_event_type", event.type)
            MDC.put("security_event_subtype", event.subType)
            MDC.put("service_name", event.serviceName)
            MDC.put("environment", event.environment)
            
            event.userId?.let { MDC.put("user_id", it) }
            event.username?.let { MDC.put("username", it) }
            event.resource?.let { MDC.put("resource", it) }
            event.action?.let { MDC.put("action", it) }
            event.ipAddress?.let { MDC.put("ip_address", it) }
            event.userAgent?.let { MDC.put("user_agent", it) }
            event.severity?.let { MDC.put("severity", it) }
            
            // Log the event
            val message = buildLogMessage(event)
            
            when (event.type) {
                "THREAT" -> {
                    when (event.severity) {
                        "CRITICAL", "HIGH" -> logger.error(message)
                        "MEDIUM" -> logger.warn(message)
                        else -> logger.info(message)
                    }
                }
                else -> {
                    if (event.success) {
                        logger.info(message)
                    } else {
                        logger.warn(message)
                    }
                }
            }
        } finally {
            // Clear MDC
            MDC.clear()
        }
    }
    
    /**
     * Build a log message from a security event
     */
    private fun buildLogMessage(event: SecurityEvent): String {
        return buildString {
            append("SECURITY_EVENT [${event.type}:${event.subType}] ")
            
            if (event.userId != null) {
                append("user=${event.userId} ")
            }
            
            if (event.resource != null) {
                append("resource=${event.resource} ")
            }
            
            if (event.action != null) {
                append("action=${event.action} ")
            }
            
            if (event.success != null) {
                append("success=${event.success} ")
            }
            
            if (event.failureReason != null) {
                append("reason=\"${event.failureReason}\" ")
            }
            
            if (event.ipAddress != null) {
                append("ip=${event.ipAddress} ")
            }
            
            if (event.severity != null) {
                append("severity=${event.severity} ")
            }
            
            if (event.metadata.isNotEmpty()) {
                event.metadata.forEach { (key, value) ->
                    append("$key=\"$value\" ")
                }
            }
        }
    }
}

/**
 * Security event data class
 */
data class SecurityEvent(
    val id: String,
    val timestamp: Instant,
    val type: String,
    val subType: String,
    val serviceName: String,
    val environment: String,
    val userId: String? = null,
    val username: String? = null,
    val resource: String? = null,
    val action: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val success: Boolean? = null,
    val failureReason: String? = null,
    val severity: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Authentication event types
 */
enum class AuthEventType {
    LOGIN_ATTEMPT,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    PASSWORD_CHANGE,
    PASSWORD_RESET_REQUEST,
    PASSWORD_RESET_COMPLETE,
    MFA_ENABLED,
    MFA_DISABLED,
    MFA_CHALLENGE,
    TOKEN_ISSUED,
    TOKEN_REFRESHED,
    TOKEN_REVOKED,
    SESSION_CREATED,
    SESSION_EXPIRED,
    SESSION_TERMINATED
}

/**
 * Access control event types
 */
enum class AccessControlEventType {
    PERMISSION_GRANTED,
    PERMISSION_DENIED,
    ROLE_ASSIGNED,
    ROLE_REMOVED,
    ROLE_CREATED,
    ROLE_UPDATED,
    ROLE_DELETED,
    PERMISSION_CREATED,
    PERMISSION_UPDATED,
    PERMISSION_DELETED
}

/**
 * Data access event types
 */
enum class DataAccessEventType {
    DATA_READ,
    DATA_CREATED,
    DATA_UPDATED,
    DATA_DELETED,
    DATA_EXPORTED,
    DATA_IMPORTED,
    SENSITIVE_DATA_ACCESS
}

/**
 * Security configuration event types
 */
enum class SecurityConfigEventType {
    SECURITY_SETTING_CHANGED,
    SECURITY_POLICY_CREATED,
    SECURITY_POLICY_UPDATED,
    SECURITY_POLICY_DELETED,
    API_KEY_CREATED,
    API_KEY_REVOKED,
    CERTIFICATE_CREATED,
    CERTIFICATE_REVOKED,
    SECRET_ROTATED
}

/**
 * Threat event types
 */
enum class ThreatEventType {
    RATE_LIMIT_EXCEEDED,
    BRUTE_FORCE_ATTEMPT,
    SUSPICIOUS_LOGIN,
    SUSPICIOUS_ACTIVITY,
    MALICIOUS_IP_BLOCKED,
    SQL_INJECTION_ATTEMPT,
    XSS_ATTEMPT,
    CSRF_ATTEMPT,
    FILE_UPLOAD_VIOLATION,
    UNAUTHORIZED_ACCESS_ATTEMPT,
    DATA_LEAKAGE_DETECTED
}

/**
 * Threat severity levels
 */
enum class ThreatSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}