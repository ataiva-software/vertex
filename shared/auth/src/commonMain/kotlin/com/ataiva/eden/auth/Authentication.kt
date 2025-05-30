package com.ataiva.eden.auth

import com.ataiva.eden.core.models.User
import com.ataiva.eden.core.models.UserContext
import com.ataiva.eden.core.models.UserSession
import kotlinx.datetime.Instant

/**
 * Core authentication interface for Eden services
 */
interface Authentication {
    /**
     * Authenticate user with email and password
     */
    suspend fun authenticate(email: String, password: String): AuthResult
    
    /**
     * Authenticate user with token
     */
    suspend fun authenticateWithToken(token: String): AuthResult
    
    /**
     * Refresh authentication token
     */
    suspend fun refreshToken(refreshToken: String): AuthResult
    
    /**
     * Logout user and invalidate session
     */
    suspend fun logout(sessionId: String): Boolean
    
    /**
     * Validate MFA token
     */
    suspend fun validateMfa(userId: String, token: String): Boolean
    
    /**
     * Generate password reset token
     */
    suspend fun generatePasswordResetToken(email: String): String?
    
    /**
     * Reset password with token
     */
    suspend fun resetPassword(token: String, newPassword: String): Boolean
}

/**
 * Authentication result
 */
sealed class AuthResult {
    data class Success(
        val userContext: UserContext,
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresAt: Instant
    ) : AuthResult()
    
    data class MfaRequired(
        val userId: String,
        val mfaToken: String
    ) : AuthResult()
    
    data class Failure(
        val reason: AuthFailureReason,
        val message: String
    ) : AuthResult()
}

/**
 * Authentication failure reasons
 */
enum class AuthFailureReason {
    INVALID_CREDENTIALS,
    USER_NOT_FOUND,
    USER_INACTIVE,
    EMAIL_NOT_VERIFIED,
    ACCOUNT_LOCKED,
    TOKEN_EXPIRED,
    TOKEN_INVALID,
    MFA_REQUIRED,
    MFA_INVALID,
    RATE_LIMITED,
    INTERNAL_ERROR
}

/**
 * Token manager interface
 */
interface TokenManager {
    /**
     * Generate JWT token for user
     */
    fun generateToken(user: User, expiresIn: Long = 3600): String
    
    /**
     * Generate refresh token
     */
    fun generateRefreshToken(userId: String): String
    
    /**
     * Validate and parse JWT token
     */
    fun validateToken(token: String): TokenValidationResult
    
    /**
     * Extract user ID from token
     */
    fun extractUserId(token: String): String?
    
    /**
     * Check if token is expired
     */
    fun isTokenExpired(token: String): Boolean
}

/**
 * Token validation result
 */
sealed class TokenValidationResult {
    data class Valid(
        val userId: String,
        val claims: Map<String, Any>,
        val expiresAt: Instant
    ) : TokenValidationResult()
    
    data class Invalid(val reason: String) : TokenValidationResult()
    object Expired : TokenValidationResult()
}

/**
 * Password hasher interface
 */
interface PasswordHasher {
    /**
     * Hash password with salt
     */
    fun hashPassword(password: String): String
    
    /**
     * Verify password against hash
     */
    fun verifyPassword(password: String, hash: String): Boolean
    
    /**
     * Check if password meets security requirements
     */
    fun validatePasswordStrength(password: String): PasswordValidationResult
}

/**
 * Password validation result
 */
data class PasswordValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val score: Int = 0 // 0-100 strength score
)

/**
 * MFA (Multi-Factor Authentication) interface
 */
interface MfaProvider {
    /**
     * Generate MFA secret for user
     */
    fun generateSecret(userId: String): String
    
    /**
     * Generate QR code URL for MFA setup
     */
    fun generateQrCodeUrl(userId: String, secret: String, issuer: String): String
    
    /**
     * Validate MFA token
     */
    fun validateToken(secret: String, token: String): Boolean
    
    /**
     * Generate backup codes
     */
    fun generateBackupCodes(count: Int = 10): List<String>
    
    /**
     * Validate backup code
     */
    fun validateBackupCode(userId: String, code: String): Boolean
}

/**
 * Session manager interface
 */
interface SessionManager {
    /**
     * Create new session for user
     */
    suspend fun createSession(
        userId: String,
        ipAddress: String?,
        userAgent: String?,
        expiresIn: Long = 28800 // 8 hours
    ): UserSession
    
    /**
     * Get session by ID
     */
    suspend fun getSession(sessionId: String): UserSession?
    
    /**
     * Update session last activity
     */
    suspend fun updateSessionActivity(sessionId: String): Boolean
    
    /**
     * Invalidate session
     */
    suspend fun invalidateSession(sessionId: String): Boolean
    
    /**
     * Invalidate all sessions for user
     */
    suspend fun invalidateAllUserSessions(userId: String): Boolean
    
    /**
     * Get active sessions for user
     */
    suspend fun getUserSessions(userId: String): List<UserSession>
    
    /**
     * Clean up expired sessions
     */
    suspend fun cleanupExpiredSessions(): Int
}