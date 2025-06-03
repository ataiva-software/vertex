package com.ataiva.eden.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.ataiva.eden.core.models.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.mindrot.jbcrypt.BCrypt
import java.security.SecureRandom
import java.util.*
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * JWT-based authentication implementation
 */
class JwtAuthenticationImpl(
    private val jwtSecret: String,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : Authentication, TokenManager, PasswordHasher, MfaProvider {

    private val algorithm = Algorithm.HMAC256(jwtSecret)
    private val jwtVerifier = JWT.require(algorithm)
        .withIssuer("eden-devops")
        .build()
    
    private val secureRandom = SecureRandom()

    // Authentication Implementation
    override suspend fun authenticate(email: String, password: String): AuthResult {
        return try {
            val user = userRepository.findByEmail(email)
                ?: return AuthResult.Failure(AuthFailureReason.USER_NOT_FOUND, "User not found")

            if (!user.isActive) {
                return AuthResult.Failure(AuthFailureReason.USER_INACTIVE, "User account is inactive")
            }

            if (!user.emailVerified) {
                return AuthResult.Failure(AuthFailureReason.EMAIL_NOT_VERIFIED, "Email not verified")
            }

            if (user.passwordHash == null || !verifyPassword(password, user.passwordHash)) {
                return AuthResult.Failure(AuthFailureReason.INVALID_CREDENTIALS, "Invalid credentials")
            }

            // Check if MFA is required
            if (user.mfaSecret != null) {
                val mfaToken = generateMfaToken(user.id)
                return AuthResult.MfaRequired(user.id, mfaToken)
            }

            createSuccessfulAuthResult(user)
        } catch (e: Exception) {
            AuthResult.Failure(AuthFailureReason.INTERNAL_ERROR, "Authentication failed: ${e.message}")
        }
    }

    override suspend fun authenticateWithToken(token: String): AuthResult {
        return try {
            when (val validationResult = validateToken(token)) {
                is TokenValidationResult.Valid -> {
                    val user = userRepository.findById(validationResult.userId)
                        ?: return AuthResult.Failure(AuthFailureReason.USER_NOT_FOUND, "User not found")
                    
                    if (!user.isActive) {
                        return AuthResult.Failure(AuthFailureReason.USER_INACTIVE, "User account is inactive")
                    }

                    createSuccessfulAuthResult(user, token)
                }
                is TokenValidationResult.Invalid -> {
                    AuthResult.Failure(AuthFailureReason.TOKEN_INVALID, validationResult.reason)
                }
                is TokenValidationResult.Expired -> {
                    AuthResult.Failure(AuthFailureReason.TOKEN_EXPIRED, "Token has expired")
                }
            }
        } catch (e: Exception) {
            AuthResult.Failure(AuthFailureReason.INTERNAL_ERROR, "Token authentication failed: ${e.message}")
        }
    }

    override suspend fun refreshToken(refreshToken: String): AuthResult {
        return try {
            val session = sessionManager.getSessionByRefreshToken(refreshToken)
                ?: return AuthResult.Failure(AuthFailureReason.TOKEN_INVALID, "Invalid refresh token")

            if (!session.isActive || Clock.System.now() > session.expiresAt) {
                return AuthResult.Failure(AuthFailureReason.TOKEN_EXPIRED, "Refresh token expired")
            }

            val user = userRepository.findById(session.userId)
                ?: return AuthResult.Failure(AuthFailureReason.USER_NOT_FOUND, "User not found")

            createSuccessfulAuthResult(user)
        } catch (e: Exception) {
            AuthResult.Failure(AuthFailureReason.INTERNAL_ERROR, "Token refresh failed: ${e.message}")
        }
    }

    override suspend fun logout(sessionId: String): Boolean {
        return sessionManager.invalidateSession(sessionId)
    }

    override suspend fun validateMfa(userId: String, token: String): Boolean {
        val user = userRepository.findById(userId) ?: return false
        val secret = user.mfaSecret ?: return false
        return validateToken(secret, token)
    }

    override suspend fun generatePasswordResetToken(email: String): String? {
        val user = userRepository.findByEmail(email) ?: return null
        return generateToken(user, expiresIn = 3600) // 1 hour
    }

    override suspend fun resetPassword(token: String, newPassword: String): Boolean {
        return try {
            when (val validationResult = validateToken(token)) {
                is TokenValidationResult.Valid -> {
                    val hashedPassword = hashPassword(newPassword)
                    userRepository.updatePassword(validationResult.userId, hashedPassword)
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    // Token Manager Implementation
    override fun generateToken(user: User, expiresIn: Long): String {
        val now = Clock.System.now()
        val expiresAt = now.plus(expiresIn.hours)

        return JWT.create()
            .withIssuer("eden-devops")
            .withSubject(user.id)
            .withClaim("email", user.email)
            .withClaim("name", user.profile.displayName.ifEmpty { user.profile.firstName })
            .withIssuedAt(Date.from(now.toJavaInstant()))
            .withExpiresAt(Date.from(expiresAt.toJavaInstant()))
            .sign(algorithm)
    }

    override fun generateRefreshToken(userId: String): String {
        val now = Clock.System.now()
        val expiresAt = now.plus(30 * 24.hours) // 30 days

        return JWT.create()
            .withIssuer("eden-devops")
            .withSubject(userId)
            .withClaim("type", "refresh")
            .withIssuedAt(Date.from(now.toJavaInstant()))
            .withExpiresAt(Date.from(expiresAt.toJavaInstant()))
            .sign(algorithm)
    }

    override fun validateToken(token: String): TokenValidationResult {
        return try {
            val decodedJWT: DecodedJWT = jwtVerifier.verify(token)
            val userId = decodedJWT.subject
            val expiresAt = decodedJWT.expiresAt.toInstant().toKotlinInstant()
            
            val claims = mutableMapOf<String, Any>()
            decodedJWT.claims.forEach { (key, claim) ->
                when {
                    claim.asString() != null -> claims[key] = claim.asString()
                    claim.asBoolean() != null -> claims[key] = claim.asBoolean()
                    claim.asInt() != null -> claims[key] = claim.asInt()
                    claim.asLong() != null -> claims[key] = claim.asLong()
                }
            }

            TokenValidationResult.Valid(userId, claims, expiresAt)
        } catch (e: JWTVerificationException) {
            if (e.message?.contains("expired") == true) {
                TokenValidationResult.Expired
            } else {
                TokenValidationResult.Invalid(e.message ?: "Invalid token")
            }
        }
    }

    override fun extractUserId(token: String): String? {
        return try {
            val decodedJWT = JWT.decode(token)
            decodedJWT.subject
        } catch (e: Exception) {
            null
        }
    }

    override fun isTokenExpired(token: String): Boolean {
        return try {
            val decodedJWT = JWT.decode(token)
            decodedJWT.expiresAt.before(Date())
        } catch (e: Exception) {
            true
        }
    }

    // Password Hasher Implementation
    override fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt(12))
    }

    override fun verifyPassword(password: String, hash: String): Boolean {
        return try {
            BCrypt.checkpw(password, hash)
        } catch (e: Exception) {
            false
        }
    }

    override fun validatePasswordStrength(password: String): PasswordValidationResult {
        val errors = mutableListOf<String>()
        var score = 0

        if (password.length < 8) {
            errors.add("Password must be at least 8 characters long")
        } else {
            score += 20
        }

        if (password.length >= 12) score += 10

        if (password.any { it.isLowerCase() }) score += 10
        else errors.add("Password must contain lowercase letters")

        if (password.any { it.isUpperCase() }) score += 10
        else errors.add("Password must contain uppercase letters")

        if (password.any { it.isDigit() }) score += 20
        else errors.add("Password must contain numbers")

        if (password.any { !it.isLetterOrDigit() }) score += 20
        else errors.add("Password must contain special characters")

        if (password.length >= 16) score += 10

        return PasswordValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            score = minOf(score, 100)
        )
    }

    // MFA Provider Implementation
    override fun generateSecret(userId: String): String {
        val bytes = ByteArray(20)
        secureRandom.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    override fun generateQrCodeUrl(userId: String, secret: String, issuer: String): String {
        val user = runBlocking { userRepository.findById(userId) }
        val email = user?.email ?: "user@example.com"
        return "otpauth://totp/$issuer:$email?secret=$secret&issuer=$issuer"
    }

    override fun validateToken(secret: String, token: String): Boolean {
        // Simple TOTP validation - in production, use a proper TOTP library
        return try {
            val currentTime = System.currentTimeMillis() / 30000
            val expectedToken = generateTotpToken(secret, currentTime)
            token == expectedToken
        } catch (e: Exception) {
            false
        }
    }

    override fun generateBackupCodes(count: Int): List<String> {
        return (1..count).map {
            val bytes = ByteArray(8)
            secureRandom.nextBytes(bytes)
            bytes.joinToString("") { "%02x".format(it) }
        }
    }

    override fun validateBackupCode(userId: String, code: String): Boolean {
        // This would typically check against stored backup codes in the database
        return false // Placeholder implementation
    }

    // Helper methods
    private suspend fun createSuccessfulAuthResult(user: User, existingToken: String? = null): AuthResult {
        val token = existingToken ?: generateToken(user)
        val refreshToken = generateRefreshToken(user.id)
        val expiresAt = Clock.System.now().plus(8.hours)
        
        val session = sessionManager.createSession(
            userId = user.id,
            ipAddress = null, // Would be passed from request context
            userAgent = null, // Would be passed from request context
            expiresIn = 8 * 3600 // 8 hours
        )

        val permissions = userRepository.getUserPermissions(user.id)
        val organizationMemberships = userRepository.getUserOrganizationMemberships(user.id)
        
        val userContext = UserContext(
            user = user,
            session = session,
            permissions = permissions,
            organizationMemberships = organizationMemberships
        )

        return AuthResult.Success(
            userContext = userContext,
            accessToken = token,
            refreshToken = refreshToken,
            expiresAt = expiresAt
        )
    }

    private fun generateMfaToken(userId: String): String {
        val now = Clock.System.now()
        val expiresAt = now.plus(5.minutes)

        return JWT.create()
            .withIssuer("eden-devops")
            .withSubject(userId)
            .withClaim("type", "mfa")
            .withIssuedAt(Date.from(now.toJavaInstant()))
            .withExpiresAt(Date.from(expiresAt.toJavaInstant()))
            .sign(algorithm)
    }

    private fun generateTotpToken(secret: String, timeStep: Long): String {
        // Simplified TOTP implementation - use a proper library in production
        val secretBytes = Base64.getDecoder().decode(secret)
        val timeBytes = ByteArray(8)
        for (i in 7 downTo 0) {
            timeBytes[i] = (timeStep shr (i * 8)).toByte()
        }
        
        val hash = javax.crypto.Mac.getInstance("HmacSHA1").apply {
            init(SecretKeySpec(secretBytes, "HmacSHA1"))
        }.doFinal(timeBytes)
        
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val code = ((hash[offset].toInt() and 0x7f) shl 24) or
                   ((hash[offset + 1].toInt() and 0xff) shl 16) or
                   ((hash[offset + 2].toInt() and 0xff) shl 8) or
                   (hash[offset + 3].toInt() and 0xff)
        
        return String.format("%06d", code % 1000000)
    }
}

// Repository interfaces that would be implemented by the database layer
interface UserRepository {
    suspend fun findByEmail(email: String): User?
    suspend fun findById(id: String): User?
    suspend fun updatePassword(userId: String, passwordHash: String): Boolean
    suspend fun getUserPermissions(userId: String): Set<Permission>
    suspend fun getUserOrganizationMemberships(userId: String): List<OrganizationMembership>
}

// Extension for SessionManager to support refresh tokens
interface SessionManagerExtended : SessionManager {
    suspend fun getSessionByRefreshToken(refreshToken: String): UserSession?
}

// Helper function for runBlocking (would be replaced with proper coroutine context)
private fun <T> runBlocking(block: suspend () -> T): T {
    return kotlinx.coroutines.runBlocking { block() }
}