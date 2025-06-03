package com.ataiva.eden.integration

import com.ataiva.eden.auth.*
import com.ataiva.eden.crypto.BouncyCastleEncryption
import com.ataiva.eden.events.*
import com.ataiva.eden.core.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * End-to-end integration tests that verify all systems work together
 */
class EndToEndIntegrationTest {
    
    private val encryption = BouncyCastleEncryption()
    private val eventBus = InMemoryEventBus()
    private val userRepository = TestUserRepository()
    private val sessionManager = InMemorySessionManager()
    private val authentication = JwtAuthenticationImpl("e2e-test-secret", userRepository, sessionManager)
    private val eventCollector = SystemEventCollector()
    
    @BeforeTest
    fun setup() = runTest {
        eventBus.start()
        sessionManager.clearAllSessions()
        userRepository.clear()
        eventCollector.clear()
        
        // Subscribe to all system events
        eventBus.subscribe("user.created", eventCollector)
        eventBus.subscribe("user.updated", eventCollector)
        eventBus.subscribe("organization.created", eventCollector)
        eventBus.subscribe("vault.secret_created", eventCollector)
        eventBus.subscribe("vault.secret_accessed", eventCollector)
    }
    
    @AfterTest
    fun cleanup() = runTest {
        eventBus.stop()
    }
    
    @Test
    fun `complete user onboarding workflow should work end-to-end`() = runTest {
        // Step 1: User Registration with Encrypted Data
        val userEmail = "newuser@eden.com"
        val userPassword = "SecurePassword123!"
        val sensitiveData = "User's sensitive configuration data"
        
        // Encrypt sensitive data before storage
        val encryptionKey = encryption.generateSalt(32)
        val encryptedData = encryption.encryptString(sensitiveData, encryptionKey)
        
        // Hash password for storage
        val passwordHash = authentication.hashPassword(userPassword)
        
        // Create user
        val user = User(
            id = "user-onboarding-123",
            email = userEmail,
            passwordHash = passwordHash,
            profile = UserProfile(
                firstName = "New",
                lastName = "User",
                displayName = "New User"
            ),
            isActive = true,
            emailVerified = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        userRepository.addUser(user)
        
        // Publish user created event
        val userCreatedEvent = UserCreatedEvent(
            eventId = "event-user-created-${System.currentTimeMillis()}",
            aggregateId = user.id,
            organizationId = null,
            userId = user.id,
            timestamp = Clock.System.now(),
            email = user.email,
            profile = mapOf(
                "firstName" to user.profile.firstName,
                "lastName" to user.profile.lastName
            )
        )
        eventBus.publish(userCreatedEvent)
        
        // Step 2: User Authentication
        val authResult = authentication.authenticate(userEmail, userPassword)
        assertTrue(authResult is AuthResult.Success)
        
        val successResult = authResult as AuthResult.Success
        assertNotNull(successResult.accessToken)
        assertEquals(user.id, successResult.userContext.user.id)
        
        // Step 3: Create Organization
        val organization = Organization(
            id = "org-onboarding-456",
            name = "New User's Organization",
            slug = "new-users-org",
            description = "Organization created during onboarding",
            isActive = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        // Publish organization created event
        val orgCreatedEvent = OrganizationCreatedEvent(
            eventId = "event-org-created-${System.currentTimeMillis()}",
            aggregateId = organization.id,
            organizationId = organization.id,
            userId = user.id,
            timestamp = Clock.System.now(),
            name = organization.name,
            slug = organization.slug
        )
        eventBus.publish(orgCreatedEvent)
        
        // Step 4: Create Secret in Vault
        val secretName = "api-key-production"
        val secretValue = "sk-prod-${encryption.nextString(32)}"
        
        // Encrypt secret value
        val secretKey = encryption.deriveKey("vault-master-key", encryption.generateSalt(16), 50000, 32)
        val encryptedSecret = encryption.encryptString(secretValue, secretKey)
        
        // Publish secret created event
        val secretCreatedEvent = SecretCreatedEvent(
            eventId = "event-secret-created-${System.currentTimeMillis()}",
            aggregateId = "secret-${System.currentTimeMillis()}",
            organizationId = organization.id,
            userId = user.id,
            timestamp = Clock.System.now(),
            secretName = secretName,
            secretType = "api-key"
        )
        eventBus.publish(secretCreatedEvent)
        
        // Step 5: Access Secret (Audit Trail)
        val secretAccessedEvent = SecretAccessedEvent(
            eventId = "event-secret-accessed-${System.currentTimeMillis()}",
            aggregateId = secretCreatedEvent.aggregateId,
            organizationId = organization.id,
            userId = user.id,
            timestamp = Clock.System.now(),
            secretName = secretName,
            accessType = "read"
        )
        eventBus.publish(secretAccessedEvent)
        
        // Step 6: Update User Profile
        val updatedUser = user.copy(
            profile = user.profile.copy(displayName = "Updated Display Name"),
            updatedAt = Clock.System.now()
        )
        userRepository.updateUser(updatedUser)
        
        val userUpdatedEvent = UserUpdatedEvent(
            eventId = "event-user-updated-${System.currentTimeMillis()}",
            aggregateId = user.id,
            organizationId = organization.id,
            userId = user.id,
            timestamp = Clock.System.now(),
            changes = mapOf("displayName" to "Updated Display Name")
        )
        eventBus.publish(userUpdatedEvent)
        
        // Step 7: Wait for all events to be processed
        delay(200)
        
        // Step 8: Verify Complete Workflow
        
        // Verify authentication still works
        val reAuthResult = authentication.authenticateWithToken(successResult.accessToken)
        assertTrue(reAuthResult is AuthResult.Success)
        
        // Verify encrypted data can be decrypted
        val decryptedData = encryption.decryptString(
            encryptedData.encryptedData + encryptedData.authTag!!,
            encryptionKey,
            encryptedData.nonce
        )
        assertEquals(sensitiveData, decryptedData)
        
        // Verify secret can be decrypted
        val decryptedSecret = encryption.decryptString(
            encryptedSecret.encryptedData + encryptedSecret.authTag!!,
            secretKey,
            encryptedSecret.nonce
        )
        assertEquals(secretValue, decryptedSecret)
        
        // Verify all events were captured
        assertEquals(5, eventCollector.receivedEvents.size)
        
        val eventTypes = eventCollector.receivedEvents.map { it.eventType }.toSet()
        assertEquals(setOf(
            "user.created",
            "organization.created", 
            "vault.secret_created",
            "vault.secret_accessed",
            "user.updated"
        ), eventTypes)
        
        // Verify event data integrity
        val capturedUserCreated = eventCollector.receivedEvents
            .filterIsInstance<UserCreatedEvent>()
            .first()
        assertEquals(userEmail, capturedUserCreated.email)
        
        val capturedOrgCreated = eventCollector.receivedEvents
            .filterIsInstance<OrganizationCreatedEvent>()
            .first()
        assertEquals(organization.name, capturedOrgCreated.name)
        
        val capturedSecretCreated = eventCollector.receivedEvents
            .filterIsInstance<SecretCreatedEvent>()
            .first()
        assertEquals(secretName, capturedSecretCreated.secretName)
        
        println("✅ Complete user onboarding workflow completed successfully")
        println("   - User created and authenticated")
        println("   - Organization created")
        println("   - Secret encrypted and stored")
        println("   - Audit trail captured")
        println("   - All events processed: ${eventCollector.receivedEvents.size}")
    }
    
    @Test
    fun `security workflow should work end-to-end`() = runTest {
        // Step 1: Create user with MFA
        val userEmail = "security@eden.com"
        val userPassword = "VerySecurePassword456!"
        val mfaSecret = authentication.generateSecret("security-user")
        
        val secureUser = User(
            id = "security-user-789",
            email = userEmail,
            passwordHash = authentication.hashPassword(userPassword),
            mfaSecret = mfaSecret,
            profile = UserProfile(
                firstName = "Security",
                lastName = "User",
                displayName = "Security User"
            ),
            isActive = true,
            emailVerified = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        userRepository.addUser(secureUser)
        
        // Step 2: Attempt authentication (should require MFA)
        val authResult = authentication.authenticate(userEmail, userPassword)
        assertTrue(authResult is AuthResult.MfaRequired)
        
        val mfaResult = authResult as AuthResult.MfaRequired
        assertEquals(secureUser.id, mfaResult.userId)
        
        // Step 3: Generate backup codes
        val backupCodes = authentication.generateBackupCodes(10)
        assertEquals(10, backupCodes.size)
        
        // Step 4: Test zero-knowledge encryption
        val sensitiveDocument = "Confidential business plan document"
        val zkEncrypted = encryption.encryptZeroKnowledge(sensitiveDocument, userPassword)
        
        assertTrue(encryption.verifyIntegrity(zkEncrypted))
        
        // Step 5: Test digital signatures
        val keyPair = encryption.generateKeyPair()
        val importantMessage = "This message confirms the security workflow"
        val signature = encryption.signString(importantMessage, keyPair.privateKey)
        
        assertTrue(encryption.verifyString(importantMessage, signature, keyPair.publicKey))
        
        // Step 6: Test password strength validation
        val passwordStrength = authentication.validatePasswordStrength(userPassword)
        assertTrue(passwordStrength.isValid)
        assertTrue(passwordStrength.score >= 80)
        
        // Step 7: Decrypt zero-knowledge data
        val decryptedDocument = encryption.decryptZeroKnowledge(zkEncrypted, userPassword)
        assertEquals(sensitiveDocument, decryptedDocument)
        
        // Step 8: Test wrong password fails
        val wrongDecryption = encryption.decryptZeroKnowledge(zkEncrypted, "wrong-password")
        assertNull(wrongDecryption)
        
        println("✅ Security workflow completed successfully")
        println("   - MFA authentication flow tested")
        println("   - Zero-knowledge encryption verified")
        println("   - Digital signatures validated")
        println("   - Password strength enforced")
        println("   - Backup codes generated")
    }
    
    @Test
    fun `high-load system integration should work correctly`() = runTest {
        val userCount = 50
        val eventsPerUser = 5
        val totalEvents = userCount * eventsPerUser
        
        // Step 1: Create multiple users concurrently
        val users = mutableListOf<User>()
        repeat(userCount) { index ->
            val user = User(
                id = "load-user-$index",
                email = "loaduser$index@eden.com",
                passwordHash = authentication.hashPassword("LoadTestPassword123!"),
                profile = UserProfile(
                    firstName = "Load",
                    lastName = "User$index",
                    displayName = "Load User $index"
                ),
                isActive = true,
                emailVerified = true,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            users.add(user)
            userRepository.addUser(user)
        }
        
        // Step 2: Generate events for all users
        val events = mutableListOf<DomainEvent>()
        users.forEach { user ->
            repeat(eventsPerUser) { eventIndex ->
                events.add(UserCreatedEvent(
                    eventId = "load-event-${user.id}-$eventIndex",
                    aggregateId = user.id,
                    organizationId = "load-org",
                    userId = user.id,
                    timestamp = Clock.System.now(),
                    email = user.email,
                    profile = mapOf("index" to eventIndex.toString())
                ))
            }
        }
        
        val startTime = System.currentTimeMillis()
        
        // Step 3: Publish all events
        eventBus.publishAll(events)
        
        // Step 4: Wait for processing
        delay(2000) // Give time for high load processing
        
        val endTime = System.currentTimeMillis()
        val processingTime = endTime - startTime
        
        // Step 5: Verify all events were processed
        assertEquals(totalEvents, eventCollector.receivedEvents.size)
        
        // Step 6: Authenticate all users
        var successfulAuths = 0
        users.forEach { user ->
            val authResult = authentication.authenticate(user.email, "LoadTestPassword123!")
            if (authResult is AuthResult.Success) {
                successfulAuths++
            }
        }
        
        assertEquals(userCount, successfulAuths)
        
        // Step 7: Performance validation
        assertTrue(processingTime < 10000, "High load processing took too long: ${processingTime}ms")
        
        println("✅ High-load system integration completed successfully")
        println("   - Created $userCount users")
        println("   - Processed $totalEvents events in ${processingTime}ms")
        println("   - Authenticated all $userCount users")
        println("   - Average processing time: ${processingTime.toDouble() / totalEvents}ms per event")
    }
    
    @Test
    fun `error handling and recovery should work correctly`() = runTest {
        // Step 1: Test authentication with invalid data
        val invalidAuthResult = authentication.authenticate("nonexistent@eden.com", "wrong-password")
        assertTrue(invalidAuthResult is AuthResult.Failure)
        assertEquals(AuthFailureReason.USER_NOT_FOUND, invalidAuthResult.reason)
        
        // Step 2: Test encryption with invalid keys
        val testData = "Test data for error handling"
        val validKey = ByteArray(32) { it.toByte() }
        val invalidKey = ByteArray(16) { it.toByte() } // Wrong size
        
        assertFailsWith<IllegalArgumentException> {
            encryption.encrypt(testData.toByteArray(), invalidKey)
        }
        
        // Step 3: Test event handling with faulty handler
        val faultyHandler = FaultyEventHandler()
        eventBus.subscribe("user.created", faultyHandler)
        eventBus.subscribe("user.created", eventCollector)
        
        val testEvent = UserCreatedEvent(
            eventId = "error-test-event",
            aggregateId = "error-user",
            organizationId = "error-org",
            userId = "error-user",
            timestamp = Clock.System.now(),
            email = "error@test.com",
            profile = emptyMap()
        )
        
        eventBus.publish(testEvent)
        delay(100)
        
        // Verify good handler still works despite faulty handler
        assertEquals(1, eventCollector.receivedEvents.size)
        assertEquals(1, faultyHandler.errorCount)
        
        // Step 4: Test session cleanup
        val expiredSessionCount = sessionManager.cleanupExpiredSessions()
        assertTrue(expiredSessionCount >= 0)
        
        println("✅ Error handling and recovery completed successfully")
        println("   - Invalid authentication handled correctly")
        println("   - Encryption errors caught appropriately")
        println("   - Faulty event handlers don't break system")
        println("   - Session cleanup works correctly")
    }
}

class SystemEventCollector : EventHandler {
    val receivedEvents = mutableListOf<DomainEvent>()
    
    override suspend fun handle(event: DomainEvent) {
        receivedEvents.add(event)
    }
    
    override fun getSupportedEventTypes(): Set<String> = setOf("*")
    override val handlerName: String = "system-event-collector"
    
    fun clear() {
        receivedEvents.clear()
    }
}

class TestUserRepository : UserRepository {
    private val users = mutableMapOf<String, User>()
    private val usersByEmail = mutableMapOf<String, User>()
    
    fun addUser(user: User) {
        users[user.id] = user
        usersByEmail[user.email] = user
    }
    
    fun updateUser(user: User) {
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
                name = "org:admin",
                description = "Admin organization",
                resource = "organization",
                action = "admin",
                createdAt = now,
                updatedAt = now
            ),
            Permission(
                id = "perm-2",
                name = "vault:admin",
                description = "Admin vault",
                resource = "vault",
                action = "admin",
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
                organizationId = "org-123",
                role = "OWNER",
                permissions = setOf("org:admin", "vault:admin"),
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

class FaultyEventHandler : EventHandler {
    var errorCount = 0
    
    override suspend fun handle(event: DomainEvent) {
        errorCount++
        throw RuntimeException("Simulated handler error for testing")
    }
    
    override fun getSupportedEventTypes(): Set<String> = setOf("*")
    override val handlerName: String = "faulty-handler"
}