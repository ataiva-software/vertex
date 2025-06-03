package com.ataiva.eden.auth

import com.ataiva.eden.core.models.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.*
import kotlin.time.Duration.Companion.hours

class JwtAuthenticationTest {
    
    private val jwtSecret = "test-jwt-secret-key-for-testing-purposes-only"
    private val mockUserRepository = MockUserRepository()
    private val sessionManager = InMemorySessionManager()
    private val authentication = JwtAuthenticationImpl(jwtSecret, mockUserRepository, sessionManager)
    
    @BeforeTest
    fun setup() = runTest {
        sessionManager.clearAllSessions()
    }
    
    @Test
    fun `authenticate with valid credentials should succeed`() = runTest {
        val email = "test@example.com"
        val password = "password123"
        val hashedPassword = authentication.hashPassword(password)
        
        val user = createTestUser(email = email, passwordHash = hashedPassword, emailVerified = true)
        mockUserRepository.addUser(user)
        
        val result = authentication.authenticate(email, password)
        
        assertTrue(result is AuthResult.Success)
        assertEquals(user.id, result.userContext.user.id)
        assertTrue(result.accessToken.isNotEmpty())
        assertNotNull(result.refreshToken)
    }
    
    @Test
    fun `authenticate with invalid credentials should fail`() = runTest {
        val email = "test@example.com"
        val password = "password123"
        val hashedPassword = authentication.hashPassword("different-password")
        
        val user = createTestUser(email = email, passwordHash = hashedPassword, emailVerified = true)
        mockUserRepository.addUser(user)
        
        val result = authentication.authenticate(email, password)
        
        assertTrue(result is AuthResult.Failure)
        assertEquals(AuthFailureReason.INVALID_CREDENTIALS, result.reason)
    }
    
    @Test
    fun `authenticate with non-existent user should fail`() = runTest {
        val result = authentication.authenticate("nonexistent@example.com", "password")
        
        assertTrue(result is AuthResult.Failure)
        assertEquals(AuthFailureReason.USER_NOT_FOUND, result.reason)
    }
    
    @Test
    fun `authenticate with inactive user should fail`() = runTest {
        val email = "inactive@example.com"
        val password = "password123"
        val hashedPassword = authentication.hashPassword(password)
        
        val user = createTestUser(email = email, passwordHash = hashedPassword, isActive = false)
        mockUserRepository.addUser(user)
        
        val result = authentication.authenticate(email, password)
        
        assertTrue(result is AuthResult.Failure)
        assertEquals(AuthFailureReason.USER_INACTIVE, result.reason)
    }
    
    @Test
    fun `authenticate with unverified email should fail`() = runTest {
        val email = "unverified@example.com"
        val password = "password123"
        val hashedPassword = authentication.hashPassword(password)
        
        val user = createTestUser(email = email, passwordHash = hashedPassword, emailVerified = false)
        mockUserRepository.addUser(user)
        
        val result = authentication.authenticate(email, password)
        
        assertTrue(result is AuthResult.Failure)
        assertEquals(AuthFailureReason.EMAIL_NOT_VERIFIED, result.reason)
    }
    
    @Test
    fun `authenticate with MFA should require MFA`() = runTest {
        val email = "mfa@example.com"
        val password = "password123"
        val hashedPassword = authentication.hashPassword(password)
        
        val user = createTestUser(
            email = email, 
            passwordHash = hashedPassword, 
            emailVerified = true,
            mfaSecret = "test-mfa-secret"
        )
        mockUserRepository.addUser(user)
        
        val result = authentication.authenticate(email, password)
        
        assertTrue(result is AuthResult.MfaRequired)
        assertEquals(user.id, result.userId)
        assertTrue(result.mfaToken.isNotEmpty())
    }
    
    @Test
    fun `authenticate with token should work`() = runTest {
        val user = createTestUser(emailVerified = true)
        mockUserRepository.addUser(user)
        
        val token = authentication.generateToken(user)
        val result = authentication.authenticateWithToken(token)
        
        assertTrue(result is AuthResult.Success)
        assertEquals(user.id, result.userContext.user.id)
    }
    
    @Test
    fun `authenticate with invalid token should fail`() = runTest {
        val result = authentication.authenticateWithToken("invalid-token")
        
        assertTrue(result is AuthResult.Failure)
        assertEquals(AuthFailureReason.TOKEN_INVALID, result.reason)
    }
    
    @Test
    fun `token generation and validation should work`() {
        val user = createTestUser()
        val token = authentication.generateToken(user, expiresIn = 1)
        
        assertTrue(token.isNotEmpty())
        
        val validationResult = authentication.validateToken(token)
        assertTrue(validationResult is TokenValidationResult.Valid)
        assertEquals(user.id, validationResult.userId)
        
        val extractedUserId = authentication.extractUserId(token)
        assertEquals(user.id, extractedUserId)
        
        assertFalse(authentication.isTokenExpired(token))
    }
    
    @Test
    fun `refresh token should work`() = runTest {
        val user = createTestUser(emailVerified = true)
        mockUserRepository.addUser(user)
        
        val refreshToken = authentication.generateRefreshToken(user.id)
        
        // Create a session with the refresh token
        val session = sessionManager.createSession(
            userId = user.id,
            ipAddress = "127.0.0.1",
            userAgent = "test-agent"
        ).copy(refreshToken = refreshToken)
        
        // Mock the session manager to return our session
        val extendedSessionManager = object : SessionManagerExtended {
            override suspend fun getSessionByRefreshToken(refreshToken: String): UserSession? {
                return if (refreshToken == session.refreshToken) session else null
            }
            
            override suspend fun createSession(userId: String, ipAddress: String?, userAgent: String?, expiresIn: Long) = 
                sessionManager.createSession(userId, ipAddress, userAgent, expiresIn)
            override suspend fun getSession(sessionId: String) = sessionManager.getSession(sessionId)
            override suspend fun updateSessionActivity(sessionId: String) = sessionManager.updateSessionActivity(sessionId)
            override suspend fun invalidateSession(sessionId: String) = sessionManager.invalidateSession(sessionId)
            override suspend fun invalidateAllUserSessions(userId: String) = sessionManager.invalidateAllUserSessions(userId)
            override suspend fun getUserSessions(userId: String) = sessionManager.getUserSessions(userId)
            override suspend fun cleanupExpiredSessions() = sessionManager.cleanupExpiredSessions()
        }
        
        val authWithExtendedSession = JwtAuthenticationImpl(jwtSecret, mockUserRepository, extendedSessionManager)
        val result = authWithExtendedSession.refreshToken(refreshToken)
        
        assertTrue(result is AuthResult.Success)
        assertEquals(user.id, result.userContext.user.id)
    }
    
    @Test
    fun `password hashing and verification should work`() {
        val password = "test-password-123"
        val hash = authentication.hashPassword(password)
        
        assertTrue(hash.isNotEmpty())
        assertTrue(authentication.verifyPassword(password, hash))
        assertFalse(authentication.verifyPassword("wrong-password", hash))
    }
    
    @Test
    fun `password strength validation should work`() {
        val weakPassword = "123"
        val weakResult = authentication.validatePasswordStrength(weakPassword)
        assertFalse(weakResult.isValid)
        assertTrue(weakResult.errors.isNotEmpty())
        
        val strongPassword = "StrongP@ssw0rd123!"
        val strongResult = authentication.validatePasswordStrength(strongPassword)
        assertTrue(strongResult.isValid)
        assertTrue(strongResult.errors.isEmpty())
        assertTrue(strongResult.score >= 80)
    }
    
    @Test
    fun `MFA operations should work`() {
        val userId = "test-user-123"
        val secret = authentication.generateSecret(userId)
        
        assertTrue(secret.isNotEmpty())
        
        val qrUrl = authentication.generateQrCodeUrl(userId, secret, "Eden DevOps")
        assertTrue(qrUrl.startsWith("otpauth://totp/"))
        
        val backupCodes = authentication.generateBackupCodes(5)
        assertEquals(5, backupCodes.size)
        backupCodes.forEach { code ->
            assertTrue(code.isNotEmpty())
        }
    }
    
    @Test
    fun `logout should invalidate session`() = runTest {
        val session = sessionManager.createSession(
            userId = "test-user",
            ipAddress = "127.0.0.1",
            userAgent = "test-agent"
        )
        
        assertTrue(authentication.logout(session.id))
        
        val retrievedSession = sessionManager.getSession(session.id)
        assertNull(retrievedSession) // Should be null because it's invalidated
    }
    
    private fun createTestUser(
        id: String = "test-user-123",
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

class MockUserRepository : UserRepository {
    private val users = mutableMapOf<String, User>()
    private val usersByEmail = mutableMapOf<String, User>()
    
    fun addUser(user: User) {
        users[user.id] = user
        usersByEmail[user.email] = user
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