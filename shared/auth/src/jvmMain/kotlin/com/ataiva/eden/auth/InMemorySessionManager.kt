package com.ataiva.eden.auth

import com.ataiva.eden.core.models.UserSession
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * In-memory session manager implementation for development/testing
 * In production, this would be backed by Redis or a database
 */
class InMemorySessionManager : SessionManagerExtended {
    
    private val sessions = ConcurrentHashMap<String, UserSession>()
    private val refreshTokenToSessionId = ConcurrentHashMap<String, String>()
    private val userSessions = ConcurrentHashMap<String, MutableSet<String>>()

    override suspend fun createSession(
        userId: String,
        ipAddress: String?,
        userAgent: String?,
        expiresIn: Long
    ): UserSession {
        val sessionId = UUID.randomUUID().toString()
        val refreshToken = UUID.randomUUID().toString()
        val now = Clock.System.now()
        val expiresAt = now.plus(expiresIn.seconds)
        
        val session = UserSession(
            id = sessionId,
            userId = userId,
            token = sessionId, // Using session ID as token for simplicity
            refreshToken = refreshToken,
            expiresAt = expiresAt,
            ipAddress = ipAddress,
            userAgent = userAgent,
            isActive = true,
            createdAt = now
        )
        
        sessions[sessionId] = session
        refreshTokenToSessionId[refreshToken] = sessionId
        userSessions.computeIfAbsent(userId) { mutableSetOf() }.add(sessionId)
        
        return session
    }

    override suspend fun getSession(sessionId: String): UserSession? {
        val session = sessions[sessionId] ?: return null
        
        // Check if session is expired
        if (Clock.System.now() > session.expiresAt) {
            invalidateSession(sessionId)
            return null
        }
        
        return if (session.isActive) session else null
    }

    override suspend fun getSessionByRefreshToken(refreshToken: String): UserSession? {
        val sessionId = refreshTokenToSessionId[refreshToken] ?: return null
        return getSession(sessionId)
    }

    override suspend fun updateSessionActivity(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        
        if (!session.isActive || Clock.System.now() > session.expiresAt) {
            return false
        }
        
        // In a real implementation, you might update last activity timestamp
        // For now, we'll just return true if session exists and is valid
        return true
    }

    override suspend fun invalidateSession(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        
        // Mark session as inactive
        val inactiveSession = session.copy(isActive = false)
        sessions[sessionId] = inactiveSession
        
        // Remove from refresh token mapping
        session.refreshToken?.let { refreshToken ->
            refreshTokenToSessionId.remove(refreshToken)
        }
        
        // Remove from user sessions
        userSessions[session.userId]?.remove(sessionId)
        
        return true
    }

    override suspend fun invalidateAllUserSessions(userId: String): Boolean {
        val sessionIds = userSessions[userId]?.toList() ?: return false
        
        var invalidatedCount = 0
        sessionIds.forEach { sessionId ->
            if (invalidateSession(sessionId)) {
                invalidatedCount++
            }
        }
        
        userSessions.remove(userId)
        return invalidatedCount > 0
    }

    override suspend fun getUserSessions(userId: String): List<UserSession> {
        val sessionIds = userSessions[userId] ?: return emptyList()
        val now = Clock.System.now()
        
        return sessionIds.mapNotNull { sessionId ->
            sessions[sessionId]?.takeIf { 
                it.isActive && now <= it.expiresAt 
            }
        }
    }

    override suspend fun cleanupExpiredSessions(): Int {
        val now = Clock.System.now()
        var cleanedCount = 0
        
        val expiredSessions = sessions.values.filter { session ->
            now > session.expiresAt || !session.isActive
        }
        
        expiredSessions.forEach { session ->
            sessions.remove(session.id)
            session.refreshToken?.let { refreshToken ->
                refreshTokenToSessionId.remove(refreshToken)
            }
            userSessions[session.userId]?.remove(session.id)
            cleanedCount++
        }
        
        // Clean up empty user session sets
        userSessions.entries.removeAll { (_, sessionIds) ->
            sessionIds.isEmpty()
        }
        
        return cleanedCount
    }
    
    // Additional utility methods for testing/debugging
    fun getActiveSessionCount(): Int = sessions.values.count { it.isActive }
    
    fun getTotalSessionCount(): Int = sessions.size
    
    fun clearAllSessions() {
        sessions.clear()
        refreshTokenToSessionId.clear()
        userSessions.clear()
    }
}