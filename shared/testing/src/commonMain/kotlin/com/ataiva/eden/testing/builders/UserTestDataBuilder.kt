package com.ataiva.eden.testing.builders

import com.ataiva.eden.core.models.User
import com.ataiva.eden.core.models.UserProfile

/**
 * Test data builder for User model
 * Provides fluent API for creating test User instances with sensible defaults
 */
class UserTestDataBuilder {
    private var id: String = "test-user-${kotlin.random.Random.nextInt(1000, 9999)}"
    private var email: String = "test.user@example.com"
    private var passwordHash: String? = null
    private var mfaSecret: String? = null
    private var profile: UserProfile = UserProfile(
        firstName = "Test",
        lastName = "User",
        displayName = "Test User"
    )
    private var isActive: Boolean = true
    private var emailVerified: Boolean = false
    private var lastLoginAt: kotlinx.datetime.Instant? = null
    private var createdAt: kotlinx.datetime.Instant = TestTimeProvider.now()
    private var updatedAt: kotlinx.datetime.Instant = TestTimeProvider.now()
    
    fun withId(id: String) = apply { this.id = id }
    
    fun withEmail(email: String) = apply { this.email = email }
    
    fun withPasswordHash(passwordHash: String?) = apply { this.passwordHash = passwordHash }
    
    fun withMfaSecret(mfaSecret: String?) = apply { this.mfaSecret = mfaSecret }
    
    fun withProfile(profile: UserProfile) = apply { this.profile = profile }
    
    fun withFirstName(firstName: String) = apply { 
        this.profile = this.profile.copy(firstName = firstName)
    }
    
    fun withLastName(lastName: String) = apply { 
        this.profile = this.profile.copy(lastName = lastName)
    }
    
    fun withDisplayName(displayName: String) = apply { 
        this.profile = this.profile.copy(displayName = displayName)
    }
    
    fun withAvatarUrl(avatarUrl: String?) = apply { 
        this.profile = this.profile.copy(avatarUrl = avatarUrl)
    }
    
    fun withTimezone(timezone: String) = apply { 
        this.profile = this.profile.copy(timezone = timezone)
    }
    
    fun withLocale(locale: String) = apply { 
        this.profile = this.profile.copy(locale = locale)
    }
    
    fun withActive(isActive: Boolean) = apply { this.isActive = isActive }
    
    fun withEmailVerified(emailVerified: Boolean) = apply { this.emailVerified = emailVerified }
    
    fun withLastLoginAt(lastLoginAt: kotlinx.datetime.Instant?) = apply { this.lastLoginAt = lastLoginAt }
    
    fun withCreatedAt(createdAt: kotlinx.datetime.Instant) = apply { this.createdAt = createdAt }
    
    fun withUpdatedAt(updatedAt: kotlinx.datetime.Instant) = apply { this.updatedAt = updatedAt }
    
    fun inactive() = apply { this.isActive = false }
    
    fun verified() = apply { this.emailVerified = true }
    
    fun withRecentLogin() = apply { this.lastLoginAt = TestTimeProvider.now() }
    
    fun build(): User = User(
        id = id,
        email = email,
        passwordHash = passwordHash,
        mfaSecret = mfaSecret,
        profile = profile,
        isActive = isActive,
        emailVerified = emailVerified,
        lastLoginAt = lastLoginAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
    
    companion object {
        fun aUser() = UserTestDataBuilder()
        
        fun anActiveUser() = UserTestDataBuilder().withActive(true).verified()
        
        fun anInactiveUser() = UserTestDataBuilder().inactive()
        
        fun aVerifiedUser() = UserTestDataBuilder().verified().withRecentLogin()
        
        fun anUnverifiedUser() = UserTestDataBuilder().withEmailVerified(false)
        
        fun aUserWithMfa() = UserTestDataBuilder()
            .withMfaSecret("JBSWY3DPEHPK3PXP")
            .verified()
        
        fun usersWithEmails(emails: List<String>): List<User> {
            return emails.mapIndexed { index, email ->
                aUser()
                    .withEmail(email)
                    .withFirstName("User")
                    .withLastName("${index + 1}")
                    .withDisplayName("User ${index + 1}")
                    .build()
            }
        }
        
        fun multipleUsers(count: Int = 3): List<User> {
            return (1..count).map { index ->
                aUser()
                    .withEmail("user$index@example.com")
                    .withFirstName("User")
                    .withLastName("$index")
                    .withDisplayName("User $index")
                    .build()
            }
        }
    }
}

/**
 * Test time provider for consistent test timestamps
 */
object TestTimeProvider {
    private var currentTime: kotlinx.datetime.Instant = kotlinx.datetime.Instant.fromEpochMilliseconds(1640995200000L) // 2022-01-01T00:00:00Z
    
    fun now(): kotlinx.datetime.Instant = currentTime
    
    fun setTime(instant: kotlinx.datetime.Instant) {
        currentTime = instant
    }
    
    fun advanceBy(duration: kotlin.time.Duration) {
        currentTime = currentTime.plus(duration)
    }
    
    fun reset() {
        currentTime = kotlinx.datetime.Instant.fromEpochMilliseconds(1640995200000L)
    }
}