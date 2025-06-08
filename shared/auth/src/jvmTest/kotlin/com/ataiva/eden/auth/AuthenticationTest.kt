package com.ataiva.eden.auth

import com.ataiva.eden.testing.mocks.MockFactory
import com.ataiva.eden.testing.mocks.MockTimeProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.Instant

class AuthenticationTest : DescribeSpec({
    
    describe("AuthResult sealed class") {
        
        describe("Success result") {
            it("should create success result with all fields") {
                val userContext = MockFactory.createMockUserContext()
                val expiresAt = MockTimeProvider.futureInstant(1)
                
                val result = AuthResult.Success(
                    userContext = userContext,
                    accessToken = "access-token-123",
                    refreshToken = "refresh-token-123",
                    expiresAt = expiresAt
                )
                
                result.userContext shouldBe userContext
                result.accessToken shouldBe "access-token-123"
                result.refreshToken shouldBe "refresh-token-123"
                result.expiresAt shouldBe expiresAt
            }
            
            it("should create success result without refresh token") {
                val userContext = MockFactory.createMockUserContext()
                val expiresAt = MockTimeProvider.futureInstant(1)
                
                val result = AuthResult.Success(
                    userContext = userContext,
                    accessToken = "access-token-123",
                    expiresAt = expiresAt
                )
                
                result.refreshToken.shouldBeNull()
            }
        }
        
        describe("MfaRequired result") {
            it("should create MFA required result") {
                val result = AuthResult.MfaRequired(
                    userId = "user-123",
                    mfaToken = "mfa-token-123"
                )
                
                result.userId shouldBe "user-123"
                result.mfaToken shouldBe "mfa-token-123"
            }
        }
        
        describe("Failure result") {
            it("should create failure result with reason and message") {
                val result = AuthResult.Failure(
                    reason = AuthFailureReason.INVALID_CREDENTIALS,
                    message = "Invalid email or password"
                )
                
                result.reason shouldBe AuthFailureReason.INVALID_CREDENTIALS
                result.message shouldBe "Invalid email or password"
            }
            
            it("should create failure result for all failure reasons") {
                AuthFailureReason.values().forEach { reason ->
                    val result = AuthResult.Failure(
                        reason = reason,
                        message = "Test failure: ${reason.name}"
                    )
                    
                    result.reason shouldBe reason
                    result.message shouldBe "Test failure: ${reason.name}"
                }
            }
        }
    }
    
    describe("AuthFailureReason enum") {
        
        it("should have all expected failure reasons") {
            val reasons = AuthFailureReason.values()
            
            reasons shouldContain AuthFailureReason.INVALID_CREDENTIALS
            reasons shouldContain AuthFailureReason.USER_NOT_FOUND
            reasons shouldContain AuthFailureReason.USER_INACTIVE
            reasons shouldContain AuthFailureReason.EMAIL_NOT_VERIFIED
            reasons shouldContain AuthFailureReason.ACCOUNT_LOCKED
            reasons shouldContain AuthFailureReason.TOKEN_EXPIRED
            reasons shouldContain AuthFailureReason.TOKEN_INVALID
            reasons shouldContain AuthFailureReason.MFA_REQUIRED
            reasons shouldContain AuthFailureReason.MFA_INVALID
            reasons shouldContain AuthFailureReason.RATE_LIMITED
            reasons shouldContain AuthFailureReason.INTERNAL_ERROR
        }
        
        it("should have expected number of failure reasons") {
            val reasons = AuthFailureReason.values()
            reasons shouldHaveSize 11
        }
    }
    
    describe("Authentication interface") {
        
        val mockAuth = mockk<Authentication>()
        
        describe("authenticate with email and password") {
            it("should return success for valid credentials") {
                val userContext = MockFactory.createMockUserContext()
                val expectedResult = AuthResult.Success(
                    userContext = userContext,
                    accessToken = "token-123",
                    refreshToken = "refresh-123",
                    expiresAt = MockTimeProvider.futureInstant(1)
                )
                
                coEvery { 
                    mockAuth.authenticate("user@example.com", "password123") 
                } returns expectedResult
                
                val result = mockAuth.authenticate("user@example.com", "password123")
                
                result shouldBe expectedResult
                result.shouldBeInstanceOf<AuthResult.Success>()
                coVerify { mockAuth.authenticate("user@example.com", "password123") }
            }
            
            it("should return failure for invalid credentials") {
                val expectedResult = AuthResult.Failure(
                    reason = AuthFailureReason.INVALID_CREDENTIALS,
                    message = "Invalid credentials"
                )
                
                coEvery { 
                    mockAuth.authenticate("user@example.com", "wrongpassword") 
                } returns expectedResult
                
                val result = mockAuth.authenticate("user@example.com", "wrongpassword")
                
                result shouldBe expectedResult
                result.shouldBeInstanceOf<AuthResult.Failure>()
            }
            
            it("should return MFA required for MFA-enabled user") {
                val expectedResult = AuthResult.MfaRequired(
                    userId = "user-123",
                    mfaToken = "mfa-token-123"
                )
                
                coEvery { 
                    mockAuth.authenticate("mfa-user@example.com", "password123") 
                } returns expectedResult
                
                val result = mockAuth.authenticate("mfa-user@example.com", "password123")
                
                result shouldBe expectedResult
                result.shouldBeInstanceOf<AuthResult.MfaRequired>()
            }
        }
        
        describe("authenticate with token") {
            it("should return success for valid token") {
                val userContext = MockFactory.createMockUserContext()
                val expectedResult = AuthResult.Success(
                    userContext = userContext,
                    accessToken = "new-token-123",
                    expiresAt = MockTimeProvider.futureInstant(1)
                )
                
                coEvery { 
                    mockAuth.authenticateWithToken("valid-token-123") 
                } returns expectedResult
                
                val result = mockAuth.authenticateWithToken("valid-token-123")
                
                result shouldBe expectedResult
                result.shouldBeInstanceOf<AuthResult.Success>()
            }
            
            it("should return failure for invalid token") {
                val expectedResult = AuthResult.Failure(
                    reason = AuthFailureReason.TOKEN_INVALID,
                    message = "Invalid token"
                )
                
                coEvery { 
                    mockAuth.authenticateWithToken("invalid-token") 
                } returns expectedResult
                
                val result = mockAuth.authenticateWithToken("invalid-token")
                
                result shouldBe expectedResult
                result.shouldBeInstanceOf<AuthResult.Failure>()
            }
            
            it("should return failure for expired token") {
                val expectedResult = AuthResult.Failure(
                    reason = AuthFailureReason.TOKEN_EXPIRED,
                    message = "Token has expired"
                )
                
                coEvery { 
                    mockAuth.authenticateWithToken("expired-token") 
                } returns expectedResult
                
                val result = mockAuth.authenticateWithToken("expired-token")
                
                result shouldBe expectedResult
                result.shouldBeInstanceOf<AuthResult.Failure>()
            }
        }
        
        describe("refresh token") {
            it("should return success with new tokens") {
                val userContext = MockFactory.createMockUserContext()
                val expectedResult = AuthResult.Success(
                    userContext = userContext,
                    accessToken = "new-access-token",
                    refreshToken = "new-refresh-token",
                    expiresAt = MockTimeProvider.futureInstant(1)
                )
                
                coEvery { 
                    mockAuth.refreshToken("valid-refresh-token") 
                } returns expectedResult
                
                val result = mockAuth.refreshToken("valid-refresh-token")
                
                result shouldBe expectedResult
                result.shouldBeInstanceOf<AuthResult.Success>()
            }
            
            it("should return failure for invalid refresh token") {
                val expectedResult = AuthResult.Failure(
                    reason = AuthFailureReason.TOKEN_INVALID,
                    message = "Invalid refresh token"
                )
                
                coEvery { 
                    mockAuth.refreshToken("invalid-refresh-token") 
                } returns expectedResult
                
                val result = mockAuth.refreshToken("invalid-refresh-token")
                
                result shouldBe expectedResult
                result.shouldBeInstanceOf<AuthResult.Failure>()
            }
        }
        
        describe("logout") {
            it("should return true for successful logout") {
                coEvery { mockAuth.logout("session-123") } returns true
                
                val result = mockAuth.logout("session-123")
                
                result shouldBe true
                coVerify { mockAuth.logout("session-123") }
            }
            
            it("should return false for invalid session") {
                coEvery { mockAuth.logout("invalid-session") } returns false
                
                val result = mockAuth.logout("invalid-session")
                
                result shouldBe false
            }
        }
        
        describe("MFA validation") {
            it("should return true for valid MFA token") {
                coEvery { mockAuth.validateMfa("user-123", "123456") } returns true
                
                val result = mockAuth.validateMfa("user-123", "123456")
                
                result shouldBe true
                coVerify { mockAuth.validateMfa("user-123", "123456") }
            }
            
            it("should return false for invalid MFA token") {
                coEvery { mockAuth.validateMfa("user-123", "invalid") } returns false
                
                val result = mockAuth.validateMfa("user-123", "invalid")
                
                result shouldBe false
            }
        }
        
        describe("password reset") {
            it("should generate password reset token") {
                coEvery { 
                    mockAuth.generatePasswordResetToken("user@example.com") 
                } returns "reset-token-123"
                
                val result = mockAuth.generatePasswordResetToken("user@example.com")
                
                result shouldBe "reset-token-123"
                coVerify { mockAuth.generatePasswordResetToken("user@example.com") }
            }
            
            it("should return null for non-existent user") {
                coEvery { 
                    mockAuth.generatePasswordResetToken("nonexistent@example.com") 
                } returns null
                
                val result = mockAuth.generatePasswordResetToken("nonexistent@example.com")
                
                result.shouldBeNull()
            }
            
            it("should reset password successfully") {
                coEvery { 
                    mockAuth.resetPassword("reset-token-123", "newpassword123") 
                } returns true
                
                val result = mockAuth.resetPassword("reset-token-123", "newpassword123")
                
                result shouldBe true
                coVerify { mockAuth.resetPassword("reset-token-123", "newpassword123") }
            }
            
            it("should fail to reset password with invalid token") {
                coEvery { 
                    mockAuth.resetPassword("invalid-token", "newpassword123") 
                } returns false
                
                val result = mockAuth.resetPassword("invalid-token", "newpassword123")
                
                result shouldBe false
            }
        }
    }
    
    describe("TokenManager interface") {
        
        val mockTokenManager = mockk<TokenManager>()
        
        describe("token generation") {
            it("should generate JWT token for user") {
                val user = MockFactory.createMockUser()
                coEvery { 
                    mockTokenManager.generateToken(user, 3600) 
                } returns "jwt-token-123"
                
                val result = mockTokenManager.generateToken(user, 3600)
                
                result shouldBe "jwt-token-123"
                coVerify { mockTokenManager.generateToken(user, 3600) }
            }
            
            it("should generate refresh token") {
                coEvery { 
                    mockTokenManager.generateRefreshToken("user-123") 
                } returns "refresh-token-123"
                
                val result = mockTokenManager.generateRefreshToken("user-123")
                
                result shouldBe "refresh-token-123"
                coVerify { mockTokenManager.generateRefreshToken("user-123") }
            }
        }
        
        describe("token validation") {
            it("should validate valid token") {
                val expectedResult = TokenValidationResult.Valid(
                    userId = "user-123",
                    claims = mapOf("sub" to "user-123", "exp" to 1234567890),
                    expiresAt = MockTimeProvider.futureInstant(1)
                )
                
                coEvery { 
                    mockTokenManager.validateToken("valid-token") 
                } returns expectedResult
                
                val result = mockTokenManager.validateToken("valid-token")
                
                result shouldBe expectedResult
                result.shouldBeInstanceOf<TokenValidationResult.Valid>()
            }
            
            it("should return invalid for malformed token") {
                val expectedResult = TokenValidationResult.Invalid("Malformed token")
                
                coEvery { 
                    mockTokenManager.validateToken("malformed-token") 
                } returns expectedResult
                
                val result = mockTokenManager.validateToken("malformed-token")
                
                result shouldBe expectedResult
                result.shouldBeInstanceOf<TokenValidationResult.Invalid>()
            }
            
            it("should return expired for expired token") {
                coEvery { 
                    mockTokenManager.validateToken("expired-token") 
                } returns TokenValidationResult.Expired
                
                val result = mockTokenManager.validateToken("expired-token")
                
                result shouldBe TokenValidationResult.Expired
                result.shouldBeInstanceOf<TokenValidationResult.Expired>()
            }
        }
        
        describe("token utilities") {
            it("should extract user ID from token") {
                coEvery { 
                    mockTokenManager.extractUserId("valid-token") 
                } returns "user-123"
                
                val result = mockTokenManager.extractUserId("valid-token")
                
                result shouldBe "user-123"
            }
            
            it("should return null for invalid token") {
                coEvery { 
                    mockTokenManager.extractUserId("invalid-token") 
                } returns null
                
                val result = mockTokenManager.extractUserId("invalid-token")
                
                result.shouldBeNull()
            }
            
            it("should check if token is expired") {
                coEvery { mockTokenManager.isTokenExpired("expired-token") } returns true
                coEvery { mockTokenManager.isTokenExpired("valid-token") } returns false
                
                mockTokenManager.isTokenExpired("expired-token") shouldBe true
                mockTokenManager.isTokenExpired("valid-token") shouldBe false
            }
        }
    }
    
    describe("TokenValidationResult sealed class") {
        
        describe("Valid result") {
            it("should create valid result with all fields") {
                val claims = mapOf("sub" to "user-123", "role" to "admin")
                val expiresAt = MockTimeProvider.futureInstant(1)
                
                val result = TokenValidationResult.Valid(
                    userId = "user-123",
                    claims = claims,
                    expiresAt = expiresAt
                )
                
                result.userId shouldBe "user-123"
                result.claims shouldBe claims
                result.expiresAt shouldBe expiresAt
            }
        }
        
        describe("Invalid result") {
            it("should create invalid result with reason") {
                val result = TokenValidationResult.Invalid("Token signature invalid")
                
                result.reason shouldBe "Token signature invalid"
            }
        }
        
        describe("Expired result") {
            it("should create expired result") {
                val result = TokenValidationResult.Expired
                
                result shouldBe TokenValidationResult.Expired
            }
        }
    }
    
    describe("PasswordHasher interface") {
        
        val mockPasswordHasher = mockk<PasswordHasher>()
        
        describe("password hashing") {
            it("should hash password") {
                coEvery { 
                    mockPasswordHasher.hashPassword("password123") 
                } returns "hashed-password-123"
                
                val result = mockPasswordHasher.hashPassword("password123")
                
                result shouldBe "hashed-password-123"
                coVerify { mockPasswordHasher.hashPassword("password123") }
            }
            
            it("should verify correct password") {
                coEvery { 
                    mockPasswordHasher.verifyPassword("password123", "hashed-password-123") 
                } returns true
                
                val result = mockPasswordHasher.verifyPassword("password123", "hashed-password-123")
                
                result shouldBe true
            }
            
            it("should reject incorrect password") {
                coEvery { 
                    mockPasswordHasher.verifyPassword("wrongpassword", "hashed-password-123") 
                } returns false
                
                val result = mockPasswordHasher.verifyPassword("wrongpassword", "hashed-password-123")
                
                result shouldBe false
            }
        }
        
        describe("password strength validation") {
            it("should validate strong password") {
                val expectedResult = PasswordValidationResult(
                    isValid = true,
                    errors = emptyList(),
                    score = 85
                )
                
                coEvery { 
                    mockPasswordHasher.validatePasswordStrength("StrongP@ssw0rd123") 
                } returns expectedResult
                
                val result = mockPasswordHasher.validatePasswordStrength("StrongP@ssw0rd123")
                
                result shouldBe expectedResult
                result.isValid shouldBe true
                result.errors shouldHaveSize 0
                result.score shouldBe 85
            }
            
            it("should reject weak password") {
                val expectedResult = PasswordValidationResult(
                    isValid = false,
                    errors = listOf("Password too short", "Missing special characters"),
                    score = 25
                )
                
                coEvery { 
                    mockPasswordHasher.validatePasswordStrength("weak") 
                } returns expectedResult
                
                val result = mockPasswordHasher.validatePasswordStrength("weak")
                
                result shouldBe expectedResult
                result.isValid shouldBe false
                result.errors shouldHaveSize 2
                result.score shouldBe 25
            }
        }
    }
    
    describe("PasswordValidationResult") {
        
        it("should create validation result for valid password") {
            val result = PasswordValidationResult(
                isValid = true,
                errors = emptyList(),
                score = 90
            )
            
            result.isValid shouldBe true
            result.errors shouldHaveSize 0
            result.score shouldBe 90
        }
        
        it("should create validation result for invalid password") {
            val errors = listOf("Too short", "No uppercase", "No numbers")
            val result = PasswordValidationResult(
                isValid = false,
                errors = errors,
                score = 15
            )
            
            result.isValid shouldBe false
            result.errors shouldBe errors
            result.errors shouldHaveSize 3
            result.score shouldBe 15
        }
    }
    
    describe("MfaProvider interface") {
        
        val mockMfaProvider = mockk<MfaProvider>()
        
        describe("MFA setup") {
            it("should generate MFA secret") {
                coEvery { 
                    mockMfaProvider.generateSecret("user-123") 
                } returns "JBSWY3DPEHPK3PXP"
                
                val result = mockMfaProvider.generateSecret("user-123")
                
                result shouldBe "JBSWY3DPEHPK3PXP"
                coVerify { mockMfaProvider.generateSecret("user-123") }
            }
            
            it("should generate QR code URL") {
                val expectedUrl = "otpauth://totp/Eden:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Eden"
                
                coEvery { 
                    mockMfaProvider.generateQrCodeUrl("user-123", "JBSWY3DPEHPK3PXP", "Eden") 
                } returns expectedUrl
                
                val result = mockMfaProvider.generateQrCodeUrl("user-123", "JBSWY3DPEHPK3PXP", "Eden")
                
                result shouldBe expectedUrl
                result shouldStartWith "otpauth://totp/"
            }
        }
        
        describe("MFA validation") {
            it("should validate correct MFA token") {
                coEvery { 
                    mockMfaProvider.validateToken("JBSWY3DPEHPK3PXP", "123456") 
                } returns true
                
                val result = mockMfaProvider.validateToken("JBSWY3DPEHPK3PXP", "123456")
                
                result shouldBe true
            }
            
            it("should reject incorrect MFA token") {
                coEvery { 
                    mockMfaProvider.validateToken("JBSWY3DPEHPK3PXP", "654321") 
                } returns false
                
                val result = mockMfaProvider.validateToken("JBSWY3DPEHPK3PXP", "654321")
                
                result shouldBe false
            }
        }
        
        describe("backup codes") {
            it("should generate backup codes") {
                val expectedCodes = listOf("ABC123", "DEF456", "GHI789")
                
                coEvery { 
                    mockMfaProvider.generateBackupCodes(3) 
                } returns expectedCodes
                
                val result = mockMfaProvider.generateBackupCodes(3)
                
                result shouldBe expectedCodes
                result shouldHaveSize 3
            }
            
            it("should validate backup code") {
                coEvery { 
                    mockMfaProvider.validateBackupCode("user-123", "ABC123") 
                } returns true
                
                val result = mockMfaProvider.validateBackupCode("user-123", "ABC123")
                
                result shouldBe true
            }
            
            it("should reject invalid backup code") {
                coEvery { 
                    mockMfaProvider.validateBackupCode("user-123", "INVALID") 
                } returns false
                
                val result = mockMfaProvider.validateBackupCode("user-123", "INVALID")
                
                result shouldBe false
            }
        }
    }
    
    describe("SessionManager interface") {
        
        val mockSessionManager = mockk<SessionManager>()
        
        describe("session creation") {
            it("should create new session") {
                val expectedSession = MockFactory.createMockUserContext().session
                
                coEvery { 
                    mockSessionManager.createSession("user-123", "192.168.1.1", "Mozilla/5.0", 28800) 
                } returns expectedSession
                
                val result = mockSessionManager.createSession("user-123", "192.168.1.1", "Mozilla/5.0", 28800)
                
                result shouldBe expectedSession
                coVerify { mockSessionManager.createSession("user-123", "192.168.1.1", "Mozilla/5.0", 28800) }
            }
        }
        
        describe("session management") {
            it("should get session by ID") {
                val expectedSession = MockFactory.createMockUserContext().session
                
                coEvery { 
                    mockSessionManager.getSession("session-123") 
                } returns expectedSession
                
                val result = mockSessionManager.getSession("session-123")
                
                result shouldBe expectedSession
            }
            
            it("should return null for non-existent session") {
                coEvery { 
                    mockSessionManager.getSession("non-existent") 
                } returns null
                
                val result = mockSessionManager.getSession("non-existent")
                
                result.shouldBeNull()
            }
            
            it("should update session activity") {
                coEvery { 
                    mockSessionManager.updateSessionActivity("session-123") 
                } returns true
                
                val result = mockSessionManager.updateSessionActivity("session-123")
                
                result shouldBe true
            }
            
            it("should invalidate session") {
                coEvery { 
                    mockSessionManager.invalidateSession("session-123") 
                } returns true
                
                val result = mockSessionManager.invalidateSession("session-123")
                
                result shouldBe true
            }
            
            it("should invalidate all user sessions") {
                coEvery { 
                    mockSessionManager.invalidateAllUserSessions("user-123") 
                } returns true
                
                val result = mockSessionManager.invalidateAllUserSessions("user-123")
                
                result shouldBe true
            }
            
            it("should get user sessions") {
                val expectedSessions = listOf(MockFactory.createMockUserContext().session)
                
                coEvery { 
                    mockSessionManager.getUserSessions("user-123") 
                } returns expectedSessions
                
                val result = mockSessionManager.getUserSessions("user-123")
                
                result shouldBe expectedSessions
                result shouldHaveSize 1
            }
            
            it("should cleanup expired sessions") {
                coEvery { 
                    mockSessionManager.cleanupExpiredSessions() 
                } returns 5
                
                val result = mockSessionManager.cleanupExpiredSessions()
                
                result shouldBe 5
            }
        }
    }
})