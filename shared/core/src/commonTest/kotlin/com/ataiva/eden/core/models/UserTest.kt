package com.ataiva.eden.core.models

import com.ataiva.eden.testing.builders.UserTestDataBuilder.Companion.aUser
import com.ataiva.eden.testing.builders.UserTestDataBuilder.Companion.aUserWithMfa
import com.ataiva.eden.testing.builders.UserTestDataBuilder.Companion.aVerifiedUser
import com.ataiva.eden.testing.builders.UserTestDataBuilder.Companion.anActiveUser
import com.ataiva.eden.testing.builders.UserTestDataBuilder.Companion.anInactiveUser
import com.ataiva.eden.testing.builders.UserTestDataBuilder.Companion.anUnverifiedUser
import com.ataiva.eden.testing.mocks.MockFactory
import com.ataiva.eden.testing.mocks.MockTimeProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.datetime.Instant

class UserTest : DescribeSpec({
    
    describe("User model") {
        
        describe("construction") {
            it("should create user with all required fields") {
                val user = aUser()
                    .withId("user-123")
                    .withEmail("test@example.com")
                    .build()
                
                user.id shouldBe "user-123"
                user.email shouldBe "test@example.com"
                user.isActive shouldBe true
                user.emailVerified shouldBe false
                user.passwordHash.shouldBeNull()
                user.mfaSecret.shouldBeNull()
                user.lastLoginAt.shouldBeNull()
                user.profile.shouldNotBeNull()
                user.createdAt.shouldNotBeNull()
                user.updatedAt.shouldNotBeNull()
            }
            
            it("should create user with optional fields") {
                val now = MockTimeProvider.fixedInstant()
                val user = aUser()
                    .withPasswordHash("hashed-password")
                    .withMfaSecret("mfa-secret")
                    .withLastLoginAt(now)
                    .build()
                
                user.passwordHash shouldBe "hashed-password"
                user.mfaSecret shouldBe "mfa-secret"
                user.lastLoginAt shouldBe now
            }
            
            it("should create user with custom profile") {
                val profile = UserProfile(
                    firstName = "John",
                    lastName = "Doe",
                    displayName = "John Doe",
                    avatarUrl = "https://example.com/avatar.jpg",
                    timezone = "America/New_York",
                    locale = "en-US",
                    preferences = mapOf("theme" to "dark")
                )
                
                val user = aUser().withProfile(profile).build()
                
                user.profile shouldBe profile
                user.profile.firstName shouldBe "John"
                user.profile.lastName shouldBe "Doe"
                user.profile.displayName shouldBe "John Doe"
                user.profile.avatarUrl shouldBe "https://example.com/avatar.jpg"
                user.profile.timezone shouldBe "America/New_York"
                user.profile.locale shouldBe "en-US"
                user.profile.preferences shouldBe mapOf("theme" to "dark")
            }
        }
        
        describe("builder patterns") {
            it("should create active user") {
                val user = anActiveUser().build()
                
                user.isActive shouldBe true
                user.emailVerified shouldBe true
            }
            
            it("should create inactive user") {
                val user = anInactiveUser().build()
                
                user.isActive shouldBe false
            }
            
            it("should create verified user") {
                val user = aVerifiedUser().build()
                
                user.emailVerified shouldBe true
                user.lastLoginAt.shouldNotBeNull()
            }
            
            it("should create unverified user") {
                val user = anUnverifiedUser().build()
                
                user.emailVerified shouldBe false
            }
            
            it("should create user with MFA") {
                val user = aUserWithMfa().build()
                
                user.mfaSecret shouldBe "JBSWY3DPEHPK3PXP"
                user.emailVerified shouldBe true
            }
        }
        
        describe("property-based testing") {
            it("should handle arbitrary email addresses") {
                checkAll(Arb.string(1..100)) { email ->
                    val user = aUser().withEmail(email).build()
                    user.email shouldBe email
                }
            }
            
            it("should handle arbitrary user IDs") {
                checkAll(Arb.string(1..50)) { id ->
                    val user = aUser().withId(id).build()
                    user.id shouldBe id
                }
            }
        }
        
        describe("edge cases") {
            it("should handle empty profile preferences") {
                val user = aUser().withProfile(
                    UserProfile(preferences = emptyMap())
                ).build()
                
                user.profile.preferences shouldBe emptyMap()
            }
            
            it("should handle null optional fields") {
                val user = aUser()
                    .withPasswordHash(null)
                    .withMfaSecret(null)
                    .withLastLoginAt(null)
                    .build()
                
                user.passwordHash.shouldBeNull()
                user.mfaSecret.shouldBeNull()
                user.lastLoginAt.shouldBeNull()
            }
        }
    }
    
    describe("UserProfile model") {
        
        describe("construction") {
            it("should create profile with default values") {
                val profile = UserProfile()
                
                profile.firstName shouldBe ""
                profile.lastName shouldBe ""
                profile.displayName shouldBe ""
                profile.avatarUrl.shouldBeNull()
                profile.timezone shouldBe "UTC"
                profile.locale shouldBe "en"
                profile.preferences shouldBe emptyMap()
            }
            
            it("should create profile with custom values") {
                val preferences = mapOf("theme" to "dark", "notifications" to "enabled")
                val profile = UserProfile(
                    firstName = "Jane",
                    lastName = "Smith",
                    displayName = "Jane Smith",
                    avatarUrl = "https://example.com/jane.jpg",
                    timezone = "Europe/London",
                    locale = "en-GB",
                    preferences = preferences
                )
                
                profile.firstName shouldBe "Jane"
                profile.lastName shouldBe "Smith"
                profile.displayName shouldBe "Jane Smith"
                profile.avatarUrl shouldBe "https://example.com/jane.jpg"
                profile.timezone shouldBe "Europe/London"
                profile.locale shouldBe "en-GB"
                profile.preferences shouldBe preferences
            }
        }
        
        describe("property-based testing") {
            it("should handle arbitrary timezone values") {
                checkAll(Arb.string(1..50)) { timezone ->
                    val profile = UserProfile(timezone = timezone)
                    profile.timezone shouldBe timezone
                }
            }
            
            it("should handle arbitrary locale values") {
                checkAll(Arb.string(1..10)) { locale ->
                    val profile = UserProfile(locale = locale)
                    profile.locale shouldBe locale
                }
            }
        }
    }
    
    describe("UserSession model") {
        
        describe("construction") {
            it("should create session with all required fields") {
                val now = MockTimeProvider.fixedInstant()
                val session = UserSession(
                    id = "session-123",
                    userId = "user-123",
                    token = "access-token",
                    refreshToken = "refresh-token",
                    expiresAt = now,
                    ipAddress = "192.168.1.1",
                    userAgent = "Mozilla/5.0",
                    isActive = true,
                    createdAt = now
                )
                
                session.id shouldBe "session-123"
                session.userId shouldBe "user-123"
                session.token shouldBe "access-token"
                session.refreshToken shouldBe "refresh-token"
                session.expiresAt shouldBe now
                session.ipAddress shouldBe "192.168.1.1"
                session.userAgent shouldBe "Mozilla/5.0"
                session.isActive shouldBe true
                session.createdAt shouldBe now
            }
            
            it("should create session with optional fields as null") {
                val now = MockTimeProvider.fixedInstant()
                val session = UserSession(
                    id = "session-123",
                    userId = "user-123",
                    token = "access-token",
                    expiresAt = now,
                    createdAt = now
                )
                
                session.refreshToken.shouldBeNull()
                session.ipAddress.shouldBeNull()
                session.userAgent.shouldBeNull()
                session.isActive shouldBe true
            }
        }
        
        describe("edge cases") {
            it("should handle inactive session") {
                val now = MockTimeProvider.fixedInstant()
                val session = UserSession(
                    id = "session-123",
                    userId = "user-123",
                    token = "access-token",
                    expiresAt = now,
                    isActive = false,
                    createdAt = now
                )
                
                session.isActive shouldBe false
            }
        }
    }
    
    describe("UserContext model") {
        
        describe("construction") {
            it("should create context with all components") {
                val user = MockFactory.createMockUser()
                val session = UserSession(
                    id = "session-123",
                    userId = user.id,
                    token = "token",
                    expiresAt = MockTimeProvider.futureInstant(),
                    createdAt = MockTimeProvider.fixedInstant()
                )
                val permissions = setOf(
                    MockFactory.createMockPermission(name = "org:read"),
                    MockFactory.createMockPermission(name = "vault:write")
                )
                val memberships = listOf(
                    MockFactory.createMockMembership(userId = user.id)
                )
                
                val context = UserContext(
                    user = user,
                    session = session,
                    permissions = permissions,
                    organizationMemberships = memberships
                )
                
                context.user shouldBe user
                context.session shouldBe session
                context.permissions shouldBe permissions
                context.organizationMemberships shouldBe memberships
            }
        }
        
        describe("permission checking") {
            it("should check if user has permission") {
                val context = MockFactory.createMockUserContext()
                
                context.hasPermission("org:read") shouldBe true
                context.hasPermission("vault:read") shouldBe true
                context.hasPermission("admin:delete") shouldBe false
            }
            
            it("should handle empty permissions") {
                val user = MockFactory.createMockUser()
                val session = UserSession(
                    id = "session-123",
                    userId = user.id,
                    token = "token",
                    expiresAt = MockTimeProvider.futureInstant(),
                    createdAt = MockTimeProvider.fixedInstant()
                )
                val context = UserContext(
                    user = user,
                    session = session,
                    permissions = emptySet(),
                    organizationMemberships = emptyList()
                )
                
                context.hasPermission("any:permission") shouldBe false
            }
        }
        
        describe("role checking") {
            it("should check if user has role in organization") {
                val context = MockFactory.createMockUserContext()
                
                context.hasRole("mock-org-123", "developer") shouldBe true
                context.hasRole("mock-org-123", "admin") shouldBe false
                context.hasRole("other-org", "developer") shouldBe false
            }
            
            it("should handle empty memberships") {
                val user = MockFactory.createMockUser()
                val session = UserSession(
                    id = "session-123",
                    userId = user.id,
                    token = "token",
                    expiresAt = MockTimeProvider.futureInstant(),
                    createdAt = MockTimeProvider.fixedInstant()
                )
                val context = UserContext(
                    user = user,
                    session = session,
                    permissions = emptySet(),
                    organizationMemberships = emptyList()
                )
                
                context.hasRole("any-org", "any-role") shouldBe false
            }
        }
        
        describe("organization access") {
            it("should get organization IDs") {
                val context = MockFactory.createMockUserContext()
                
                val orgIds = context.getOrganizationIds()
                orgIds shouldBe setOf("mock-org-123")
            }
            
            it("should handle multiple organizations") {
                val user = MockFactory.createMockUser()
                val session = UserSession(
                    id = "session-123",
                    userId = user.id,
                    token = "token",
                    expiresAt = MockTimeProvider.futureInstant(),
                    createdAt = MockTimeProvider.fixedInstant()
                )
                val memberships = listOf(
                    MockFactory.createMockMembership(userId = user.id, organizationId = "org-1"),
                    MockFactory.createMockMembership(userId = user.id, organizationId = "org-2"),
                    MockFactory.createMockMembership(userId = user.id, organizationId = "org-3")
                )
                val context = UserContext(
                    user = user,
                    session = session,
                    permissions = emptySet(),
                    organizationMemberships = memberships
                )
                
                val orgIds = context.getOrganizationIds()
                orgIds shouldBe setOf("org-1", "org-2", "org-3")
            }
            
            it("should handle empty memberships") {
                val user = MockFactory.createMockUser()
                val session = UserSession(
                    id = "session-123",
                    userId = user.id,
                    token = "token",
                    expiresAt = MockTimeProvider.futureInstant(),
                    createdAt = MockTimeProvider.fixedInstant()
                )
                val context = UserContext(
                    user = user,
                    session = session,
                    permissions = emptySet(),
                    organizationMemberships = emptyList()
                )
                
                val orgIds = context.getOrganizationIds()
                orgIds shouldBe emptySet()
            }
        }
    }
})