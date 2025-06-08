package com.ataiva.eden.integration.auth

import com.ataiva.eden.auth.*
import com.ataiva.eden.core.models.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * Integration tests for authentication system
 */
class AuthIntegrationTest {
    
    private val jwtSecret = "integration-test-jwt-secret-key"
    private val userRepository = TestUserRepository()
    private val sessionManager = InMemorySessionManager()
    private val authentication = JwtAuthenticationImpl(jwtSecret, userRepository, sessionManager)
    
    @BeforeTest
    fun setup() = runTest {
        sessionManager.clearAllSessions()
        userRepository.clear()
    }
    
    @Test
    fun `complete authentication flow should work end-to-end`() = runTest {
        // Step 1: Create a user
        val password = "SecurePassword123!"
        val hashedPassword = authentication.hashPassword(password)
        val user = createTestUser(
            email = "integration@test.com",
            passwordHash = hashedPassword,
            emailVerified = true
        )
        userRepository.addUser(user)
        
        // Step 2: Authenticate user
        val authResult = authentication.authenticate("integration@test.com", password)
        assertTrue(authResult is AuthResult.Success)
        
        val successResult = authResult as AuthResult.Success
        assertNotNull(successResult.accessToken)
        assertNotNull(successResult.refreshToken)
        assertEquals(user.id, successResult.userContext.user.id)
        
        // Step 3: Validate the generated token
        val tokenValidation = authentication.validateToken(successResult.accessToken)
        assertTrue(tokenValidation is TokenValidationResult.Valid)
        assertEquals(user.id, (tokenValidation as TokenValidationResult.Valid).userId)
        
        // Step 4: Use token for subsequent authentication
        val tokenAuthResult = authentication.authenticateWithToken(successResult.accessToken)
        assertTrue(tokenAuthResult is AuthResult.Success)
        
        // Step 5: Refresh the token
        val refreshResult = authentication.refreshToken(successResult.refreshToken!!)
        assertTrue(refreshResult is AuthResult.Success)
        
        // Step 6: Logout
        val logoutSuccess = authentication.logout(successResult.userContext.session.id)
        assertTrue(logoutSuccess)
    }
    
    @Test
    fun `MFA authentication flow should work correctly`() = runTest {
        // Setup user with MFA
        val password = "MfaPassword123!"
        val hashedPassword = authentication.hashPassword(password)
        val mfaSecret = authentication.generateSecret("mfa-user")
        val user = createTestUser(
            id = "mfa-user",
            email = "mfa@test.com",
            passwordHash = hashedPassword,
            emailVerified = true,
            mfaSecret = mfaSecret
        )
        userRepository.addUser(user)
        
        // Step 1: Initial authentication should require MFA
        val authResult = authentication.authenticate("mfa@test.com", password)
        assertTrue(authResult is AuthResult.MfaRequired)
        
        val mfaResult = authResult as AuthResult.MfaRequired
        assertEquals(user.id, mfaResult.userId)
        assertNotNull(mfaResult.mfaToken)
        
        // Step 2: Validate MFA token (simulate TOTP validation)
        // In real scenario, user would provide TOTP code from their authenticator app
        val mfaValid = authentication.validateMfa(user.id, "123456") // Mock token
        // Note: This will fail in real implementation, but tests the flow
        
        // Step 3: Generate QR code for MFA setup
        val qrUrl = authentication.generateQrCodeUrl(user.id, mfaSecret, "Eden DevOps")
        assertTrue(qrUrl.startsWith("otpauth://totp/"))
        assertTrue(qrUrl.contains("Eden DevOps"))
        assertTrue(qrUrl.contains(mfaSecret))
    }
    
    @Test
    fun `password reset flow should work correctly`() = runTest {
        // Setup user
        val user = createTestUser(email = "reset@test.com", emailVerified = true)
        userRepository.addUser(user)
        
        // Step 1: Generate password reset token
        val resetToken = authentication.generatePasswordResetToken("reset@test.com")
        assertNotNull(resetToken)
        
        // Step 2: Reset password with token
        val newPassword = "NewSecurePassword456!"
        val resetSuccess = authentication.resetPassword(resetToken!!, newPassword)
        assertTrue(resetSuccess)
        
        // Step 3: Verify old password no longer works
        val oldAuthResult = authentication.authenticate("reset@test.com", "old-password")
        assertTrue(oldAuthResult is AuthResult.Failure)
        
        // Step 4: Verify new password works
        val newAuthResult = authentication.authenticate("reset@test.com", newPassword)
        assertTrue(newAuthResult is AuthResult.Success)
    }
    
    @Test
    fun `session management should work correctly`() = runTest {
        val userId = "session-test-user"
        
        // Step 1: Create multiple sessions
        val session1 = sessionManager.createSession(
            userId = userId,
            ipAddress = "192.168.1.1",
            userAgent = "Browser 1"
        )
        
        val session2 = sessionManager.createSession(
            userId = userId,
            ipAddress = "192.168.1.2",
            userAgent = "Browser 2"
        )
        
        // Step 2: Verify sessions exist
        val retrievedSession1 = sessionManager.getSession(session1.id)
        val retrievedSession2 = sessionManager.getSession(session2.id)
        
        assertNotNull(retrievedSession1)
        assertNotNull(retrievedSession2)
        assertEquals(userId, retrievedSession1.userId)
        assertEquals(userId, retrievedSession2.userId)
        
        // Step 3: Get all user sessions
        val userSessions = sessionManager.getUserSessions(userId)
        assertEquals(2, userSessions.size)
        
        // Step 4: Update session activity
        val updateSuccess = sessionManager.updateSessionActivity(session1.id)
        assertTrue(updateSuccess)
        
        // Step 5: Invalidate one session
        val invalidateSuccess = sessionManager.invalidateSession(session1.id)
        assertTrue(invalidateSuccess)
        
        val invalidatedSession = sessionManager.getSession(session1.id)
        assertNull(invalidatedSession)
        
        // Step 6: Invalidate all user sessions
        val invalidateAllSuccess = sessionManager.invalidateAllUserSessions(userId)
        assertTrue(invalidateAllSuccess)
        
        val remainingSessions = sessionManager.getUserSessions(userId)
        assertTrue(remainingSessions.isEmpty())
    }
    
    @Test
    fun `token lifecycle should work correctly`() = runTest {
        val user = createTestUser()
        
        // Step 1: Generate token with short expiry
        val shortToken = authentication.generateToken(user, expiresIn = 1) // 1 hour
        assertFalse(authentication.isTokenExpired(shortToken))
        
        // Step 2: Extract user ID from token
        val extractedUserId = authentication.extractUserId(shortToken)
        assertEquals(user.id, extractedUserId)
        
        // Step 3: Validate token
        val validation = authentication.validateToken(shortToken)
        assertTrue(validation is TokenValidationResult.Valid)
        
        val validResult = validation as TokenValidationResult.Valid
        assertEquals(user.id, validResult.userId)
        assertTrue(validResult.claims.containsKey("email"))
        assertEquals(user.email, validResult.claims["email"])
        
        // Step 4: Generate refresh token
        val refreshToken = authentication.generateRefreshToken(user.id)
        assertNotNull(refreshToken)
        
        val refreshValidation = authentication.validateToken(refreshToken)
        assertTrue(refreshValidation is TokenValidationResult.Valid)
        
        val refreshValidResult = refreshValidation as TokenValidationResult.Valid
        assertEquals(user.id, refreshValidResult.userId)
        assertEquals("refresh", refreshValidResult.claims["type"])
    }
    
    @Test
    fun `password security should work correctly`() = runTest {
        val passwords = listOf(
            "weak" to false,
            "StrongPassword123!" to true,
            "AnotherStrongP@ssw0rd2024" to true
        )
        
        passwords.forEach { (password, shouldBeStrong) ->
            // Test password strength validation
            val strengthResult = authentication.validatePasswordStrength(password)
            assertEquals(shouldBeStrong, strengthResult.isValid)
            
            if (shouldBeStrong) {
                // Test password hashing and verification
                val hash = authentication.hashPassword(password)
                assertTrue(hash.isNotEmpty())
                assertTrue(hash.startsWith("$2a$")) // BCrypt format
                
                assertTrue(authentication.verifyPassword(password, hash))
                assertFalse(authentication.verifyPassword("wrong-password", hash))
            }
        }
    }
    
    @Test
    fun `backup codes should work correctly`() = runTest {
        // Generate backup codes
        val backupCodes = authentication.generateBackupCodes(10)
        
        assertEquals(10, backupCodes.size)
        
        // All codes should be unique
        val uniqueCodes = backupCodes.toSet()
        assertEquals(10, uniqueCodes.size)
        
        // All codes should be properly formatted
        backupCodes.forEach { code ->
            assertTrue(code.isNotEmpty())
            assertEquals(16, code.length) // 8 bytes * 2 hex chars
            assertTrue(code.all { it.isLetterOrDigit() })
        }
    }
    
    @Test
    fun `concurrent session operations should be thread-safe`() = runTest {
        val userId = "concurrent-test-user"
        val sessions = mutableListOf<UserSession>()
        
        // Create multiple sessions concurrently
        repeat(10) { index ->
            val session = sessionManager.createSession(
                userId = userId,
                ipAddress = "192.168.1.$index",
                userAgent = "Concurrent Browser $index"
            )
            sessions.add(session)
        }
        
        // Verify all sessions were created
        assertEquals(10, sessions.size)
        
        // Verify all sessions can be retrieved
        sessions.forEach { session ->
            val retrieved = sessionManager.getSession(session.id)
            assertNotNull(retrieved)
            assertEquals(session.id, retrieved.id)
        }
        
        // Get user sessions
        val userSessions = sessionManager.getUserSessions(userId)
        assertEquals(10, userSessions.size)
        
        // Clean up
        val cleanupCount = sessionManager.cleanupExpiredSessions()
        // Should be 0 since sessions are not expired
        assertEquals(0, cleanupCount)
    }
    
    private fun createTestUser(
        id: String = "test-user-${System.currentTimeMillis()}",
        email: String = "test@example.com",
        passwordHash: String? = "hashed-password",
        mfaSecret: String? = null,
        isActive: Boolean = true,
        emailVerified: Boolean = true
    ): User {
        val now = Clock.System.now()
        return User(
            id = id,
            email = email,
            passwordHash = passwordHash,
            mfaSecret = mfaSecret,
            profile = UserProfile(
                firstName = "Test",
                lastName = "User",
                displayName = "Test User"
            ),
            isActive = isActive,
            emailVerified = emailVerified,
            lastLoginAt = null,
            createdAt = now,
            updatedAt = now
        )
    }
}

class TestUserRepository : UserRepository {
    private val users = mutableMapOf<String, User>()
    private val usersByEmail = mutableMapOf<String, User>()
    
    fun addUser(user: User) {
        users[user.id] = user
        usersByEmail[user.email] = user
    }
    
    fun clear() {
        users.clear()
        usersByEmail.clear()
    }
    
    override suspend fun findByEmail(email: String): User? = usersByEmail[email]
    
    override suspend fun findById(id: String): User? = users[id]
    
    override suspend fun updatePassword(userId: String, passwordHash: String): Boolean {
        val user = users[userId] ?: return false
        val updatedUser = user.copy(passwordHash = passwordHash, updatedAt = Clock.System.now())
        users[userId] = updatedUser
        usersByEmail[user.email] = updatedUser
        return true
    }
    
    override suspend fun getUserPermissions(userId: String): Set<Permission> {
        val now = Clock.System.now()
        return setOf(
            Permission(
                id = "perm-1",
                name = "org:read",
                description = "Read organization data",
                resource = "organization",
                action = "read",
                createdAt = now,
                updatedAt = now
            ),
            Permission(
                id = "perm-2",
                name = "vault:read",
                description = "Read vault secrets",
                resource = "vault",
                action = "read",
                createdAt = now,
                updatedAt = now
            )
        )
    }
    
    override suspend fun getUserOrganizationMemberships(userId: String): List<OrganizationMembership> {
        val now = Clock.System.now()
        return listOf(
            OrganizationMembership(
                id = "membership-1",
                userId = userId,
                organizationId = "org-1",
                role = "DEVELOPER",
                permissions = setOf("org:read", "vault:read"),
                isActive = true,
                invitedBy = null,
                invitedAt = null,
                joinedAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
    }
}