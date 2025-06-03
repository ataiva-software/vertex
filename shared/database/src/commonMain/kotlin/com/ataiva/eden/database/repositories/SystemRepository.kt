package com.ataiva.eden.database.repositories

import com.ataiva.eden.database.Repository
import kotlinx.datetime.Instant

/**
 * System event entity for database operations
 */
data class SystemEvent(
    val id: String,
    val eventType: String,
    val sourceService: String,
    val eventData: Map<String, Any>,
    val severity: String,
    val userId: String?,
    val createdAt: Instant
)

/**
 * Audit log entity for database operations
 */
data class AuditLog(
    val id: String,
    val userId: String?,
    val organizationId: String?,
    val action: String,
    val resource: String,
    val resourceId: String?,
    val details: Map<String, Any>,
    val ipAddress: String?,
    val userAgent: String?,
    val timestamp: Instant,
    val severity: String
)

/**
 * Repository interface for system event operations
 */
interface SystemEventRepository : Repository<SystemEvent, String> {
    
    /**
     * Find events by type
     */
    suspend fun findByEventType(eventType: String): List<SystemEvent>
    
    /**
     * Find events by source service
     */
    suspend fun findBySourceService(sourceService: String): List<SystemEvent>
    
    /**
     * Find events by severity
     */
    suspend fun findBySeverity(severity: String): List<SystemEvent>
    
    /**
     * Find events by user ID
     */
    suspend fun findByUserId(userId: String): List<SystemEvent>
    
    /**
     * Find recent events
     */
    suspend fun findRecent(limit: Int = 100): List<SystemEvent>
    
    /**
     * Find events within time range
     */
    suspend fun findByTimeRange(startTime: Instant, endTime: Instant): List<SystemEvent>
    
    /**
     * Find critical events
     */
    suspend fun findCriticalEvents(): List<SystemEvent>
    
    /**
     * Find error events
     */
    suspend fun findErrorEvents(): List<SystemEvent>
    
    /**
     * Search events by event data content
     */
    suspend fun searchByEventData(searchTerm: String): List<SystemEvent>
    
    /**
     * Get event statistics
     */
    suspend fun getEventStats(): SystemEventStats
    
    /**
     * Get event statistics by service
     */
    suspend fun getEventStatsByService(sourceService: String): SystemEventStats
    
    /**
     * Get event statistics by time period
     */
    suspend fun getEventStatsByPeriod(startTime: Instant, endTime: Instant): SystemEventStats
}

/**
 * Repository interface for audit log operations
 */
interface AuditLogRepository : Repository<AuditLog, String> {
    
    /**
     * Find audit logs by user ID
     */
    suspend fun findByUserId(userId: String): List<AuditLog>
    
    /**
     * Find audit logs by organization ID
     */
    suspend fun findByOrganizationId(organizationId: String): List<AuditLog>
    
    /**
     * Find audit logs by action
     */
    suspend fun findByAction(action: String): List<AuditLog>
    
    /**
     * Find audit logs by resource
     */
    suspend fun findByResource(resource: String): List<AuditLog>
    
    /**
     * Find audit logs by resource and resource ID
     */
    suspend fun findByResourceAndId(resource: String, resourceId: String): List<AuditLog>
    
    /**
     * Find audit logs by severity
     */
    suspend fun findBySeverity(severity: String): List<AuditLog>
    
    /**
     * Find recent audit logs
     */
    suspend fun findRecent(limit: Int = 100): List<AuditLog>
    
    /**
     * Find audit logs within time range
     */
    suspend fun findByTimeRange(startTime: Instant, endTime: Instant): List<AuditLog>
    
    /**
     * Find audit logs by IP address
     */
    suspend fun findByIpAddress(ipAddress: String): List<AuditLog>
    
    /**
     * Search audit logs by details content
     */
    suspend fun searchByDetails(searchTerm: String): List<AuditLog>
    
    /**
     * Get audit trail for specific resource
     */
    suspend fun getAuditTrail(resource: String, resourceId: String): List<AuditLog>
    
    /**
     * Get user activity summary
     */
    suspend fun getUserActivity(userId: String, startTime: Instant, endTime: Instant): List<AuditLog>
    
    /**
     * Get audit statistics
     */
    suspend fun getAuditStats(): AuditStats
    
    /**
     * Get audit statistics by user
     */
    suspend fun getAuditStatsByUser(userId: String): AuditStats
    
    /**
     * Get audit statistics by time period
     */
    suspend fun getAuditStatsByPeriod(startTime: Instant, endTime: Instant): AuditStats
}

/**
 * System event statistics data class
 */
data class SystemEventStats(
    val totalEvents: Long,
    val eventsByType: Map<String, Long>,
    val eventsByService: Map<String, Long>,
    val eventsBySeverity: Map<String, Long>,
    val recentEvents: Long,
    val criticalEvents: Long,
    val errorEvents: Long,
    val eventsPerHour: Double?
)

/**
 * Audit statistics data class
 */
data class AuditStats(
    val totalLogs: Long,
    val logsByAction: Map<String, Long>,
    val logsByResource: Map<String, Long>,
    val logsBySeverity: Map<String, Long>,
    val uniqueUsers: Long,
    val uniqueIpAddresses: Long,
    val recentLogs: Long,
    val logsPerHour: Double?
)

/**
 * Repository interface for user management operations
 */
interface UserRepository : Repository<User, String> {
    
    /**
     * Find user by email
     */
    suspend fun findByEmail(email: String): User?
    
    /**
     * Find active users
     */
    suspend fun findActiveUsers(): List<User>
    
    /**
     * Find verified users
     */
    suspend fun findVerifiedUsers(): List<User>
    
    /**
     * Find users by verification status
     */
    suspend fun findByVerificationStatus(isVerified: Boolean): List<User>
    
    /**
     * Search users by name or email
     */
    suspend fun searchUsers(searchTerm: String): List<User>
    
    /**
     * Update user status
     */
    suspend fun updateStatus(id: String, isActive: Boolean): Boolean
    
    /**
     * Update user verification status
     */
    suspend fun updateVerificationStatus(id: String, isVerified: Boolean): Boolean
    
    /**
     * Update user password hash
     */
    suspend fun updatePasswordHash(id: String, passwordHash: String): Boolean
    
    /**
     * Update last login time
     */
    suspend fun updateLastLogin(id: String, lastLoginAt: Instant): Boolean
    
    /**
     * Get user statistics
     */
    suspend fun getUserStats(): UserStats
}

/**
 * User entity for database operations
 */
data class User(
    val id: String,
    val email: String,
    val passwordHash: String,
    val fullName: String?,
    val isActive: Boolean,
    val isVerified: Boolean,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * User statistics data class
 */
data class UserStats(
    val totalUsers: Long,
    val activeUsers: Long,
    val verifiedUsers: Long,
    val recentlyCreated: Long,
    val recentlyActive: Long
)