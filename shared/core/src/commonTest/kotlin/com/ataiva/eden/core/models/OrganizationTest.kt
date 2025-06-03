package com.ataiva.eden.core.models

import com.ataiva.eden.testing.builders.OrganizationTestDataBuilder.Companion.anOrganization
import com.ataiva.eden.testing.mocks.MockFactory
import com.ataiva.eden.testing.mocks.MockTimeProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.datetime.Clock

class OrganizationTest : DescribeSpec({
    
    describe("Organization model") {
        
        describe("construction") {
            it("should create organization with all required fields") {
                val org = anOrganization()
                    .withId("org-123")
                    .withName("Test Organization")
                    .withSlug("test-org")
                    .build()
                
                org.id shouldBe "org-123"
                org.name shouldBe "Test Organization"
                org.slug shouldBe "test-org"
                org.description shouldBe ""
                org.plan shouldBe OrganizationPlan.FREE
                org.isActive shouldBe true
                org.settings.shouldNotBeNull()
                org.createdAt.shouldNotBeNull()
                org.updatedAt.shouldNotBeNull()
            }
            
            it("should create organization with custom settings") {
                val settings = OrganizationSettings(
                    allowedDomains = listOf("example.com", "test.com"),
                    requireMfa = true,
                    sessionTimeoutMinutes = 240,
                    auditRetentionDays = 180,
                    features = mapOf("advanced_analytics" to true),
                    integrations = mapOf("slack" to "webhook-url"),
                    customFields = mapOf("department" to "engineering")
                )
                
                val org = anOrganization()
                    .withSettings(settings)
                    .build()
                
                org.settings shouldBe settings
                org.settings.allowedDomains shouldBe listOf("example.com", "test.com")
                org.settings.requireMfa shouldBe true
                org.settings.sessionTimeoutMinutes shouldBe 240
                org.settings.auditRetentionDays shouldBe 180
                org.settings.features shouldBe mapOf("advanced_analytics" to true)
                org.settings.integrations shouldBe mapOf("slack" to "webhook-url")
                org.settings.customFields shouldBe mapOf("department" to "engineering")
            }
            
            it("should create organization with different plans") {
                OrganizationPlan.values().forEach { plan ->
                    val org = anOrganization().withPlan(plan).build()
                    org.plan shouldBe plan
                }
            }
        }
        
        describe("builder patterns") {
            it("should create enterprise organization") {
                val org = anOrganization()
                    .withPlan(OrganizationPlan.ENTERPRISE)
                    .withDescription("Enterprise organization")
                    .build()
                
                org.plan shouldBe OrganizationPlan.ENTERPRISE
                org.description shouldBe "Enterprise organization"
            }
            
            it("should create inactive organization") {
                val org = anOrganization()
                    .withActive(false)
                    .build()
                
                org.isActive shouldBe false
            }
        }
        
        describe("property-based testing") {
            it("should handle arbitrary organization names") {
                checkAll(Arb.string(1..100)) { name ->
                    val org = anOrganization().withName(name).build()
                    org.name shouldBe name
                }
            }
            
            it("should handle arbitrary slugs") {
                checkAll(Arb.string(1..50)) { slug ->
                    val org = anOrganization().withSlug(slug).build()
                    org.slug shouldBe slug
                }
            }
        }
        
        describe("edge cases") {
            it("should handle empty description") {
                val org = anOrganization().withDescription("").build()
                org.description shouldBe ""
            }
            
            it("should handle default settings") {
                val org = anOrganization().build()
                val settings = org.settings
                
                settings.allowedDomains.shouldBeEmpty()
                settings.requireMfa shouldBe false
                settings.sessionTimeoutMinutes shouldBe 480
                settings.auditRetentionDays shouldBe 90
                settings.features.shouldBeEmpty()
                settings.integrations.shouldBeEmpty()
                settings.customFields.shouldBeEmpty()
            }
        }
    }
    
    describe("OrganizationSettings model") {
        
        describe("construction") {
            it("should create settings with default values") {
                val settings = OrganizationSettings()
                
                settings.allowedDomains.shouldBeEmpty()
                settings.requireMfa shouldBe false
                settings.sessionTimeoutMinutes shouldBe 480
                settings.auditRetentionDays shouldBe 90
                settings.features.shouldBeEmpty()
                settings.integrations.shouldBeEmpty()
                settings.customFields.shouldBeEmpty()
            }
            
            it("should create settings with custom values") {
                val settings = OrganizationSettings(
                    allowedDomains = listOf("company.com"),
                    requireMfa = true,
                    sessionTimeoutMinutes = 120,
                    auditRetentionDays = 365,
                    features = mapOf("sso" to true, "audit" to false),
                    integrations = mapOf("github" to "token123"),
                    customFields = mapOf("cost_center" to "12345")
                )
                
                settings.allowedDomains shouldContain "company.com"
                settings.requireMfa shouldBe true
                settings.sessionTimeoutMinutes shouldBe 120
                settings.auditRetentionDays shouldBe 365
                settings.features["sso"] shouldBe true
                settings.features["audit"] shouldBe false
                settings.integrations["github"] shouldBe "token123"
                settings.customFields["cost_center"] shouldBe "12345"
            }
        }
        
        describe("validation scenarios") {
            it("should handle multiple allowed domains") {
                val domains = listOf("example.com", "test.org", "company.net")
                val settings = OrganizationSettings(allowedDomains = domains)
                
                settings.allowedDomains shouldBe domains
                domains.forEach { domain ->
                    settings.allowedDomains shouldContain domain
                }
            }
            
            it("should handle various timeout values") {
                listOf(60, 120, 240, 480, 720, 1440).forEach { timeout ->
                    val settings = OrganizationSettings(sessionTimeoutMinutes = timeout)
                    settings.sessionTimeoutMinutes shouldBe timeout
                }
            }
            
            it("should handle various retention periods") {
                listOf(30, 60, 90, 180, 365, 730).forEach { days ->
                    val settings = OrganizationSettings(auditRetentionDays = days)
                    settings.auditRetentionDays shouldBe days
                }
            }
        }
    }
    
    describe("OrganizationPlan enum") {
        
        it("should have all expected plan types") {
            val plans = OrganizationPlan.values()
            
            plans shouldContain OrganizationPlan.FREE
            plans shouldContain OrganizationPlan.STARTER
            plans shouldContain OrganizationPlan.PROFESSIONAL
            plans shouldContain OrganizationPlan.ENTERPRISE
        }
        
        it("should maintain plan ordering") {
            val plans = OrganizationPlan.values()
            
            plans[0] shouldBe OrganizationPlan.FREE
            plans[1] shouldBe OrganizationPlan.STARTER
            plans[2] shouldBe OrganizationPlan.PROFESSIONAL
            plans[3] shouldBe OrganizationPlan.ENTERPRISE
        }
    }
    
    describe("OrganizationMembership model") {
        
        describe("construction") {
            it("should create membership with all required fields") {
                val now = MockTimeProvider.fixedInstant()
                val membership = OrganizationMembership(
                    id = "membership-123",
                    userId = "user-123",
                    organizationId = "org-123",
                    role = "developer",
                    permissions = setOf("read", "write"),
                    isActive = true,
                    invitedBy = "admin-user",
                    invitedAt = now,
                    joinedAt = now,
                    createdAt = now,
                    updatedAt = now
                )
                
                membership.id shouldBe "membership-123"
                membership.userId shouldBe "user-123"
                membership.organizationId shouldBe "org-123"
                membership.role shouldBe "developer"
                membership.permissions shouldBe setOf("read", "write")
                membership.isActive shouldBe true
                membership.invitedBy shouldBe "admin-user"
                membership.invitedAt shouldBe now
                membership.joinedAt shouldBe now
                membership.createdAt shouldBe now
                membership.updatedAt shouldBe now
            }
            
            it("should create membership with optional fields as null") {
                val now = MockTimeProvider.fixedInstant()
                val membership = OrganizationMembership(
                    id = "membership-123",
                    userId = "user-123",
                    organizationId = "org-123",
                    role = "viewer",
                    createdAt = now,
                    updatedAt = now
                )
                
                membership.permissions.shouldBeEmpty()
                membership.isActive shouldBe true
                membership.invitedBy.shouldBeNull()
                membership.invitedAt.shouldBeNull()
                membership.joinedAt.shouldBeNull()
            }
        }
        
        describe("edge cases") {
            it("should handle inactive membership") {
                val now = MockTimeProvider.fixedInstant()
                val membership = OrganizationMembership(
                    id = "membership-123",
                    userId = "user-123",
                    organizationId = "org-123",
                    role = "developer",
                    isActive = false,
                    createdAt = now,
                    updatedAt = now
                )
                
                membership.isActive shouldBe false
            }
            
            it("should handle empty permissions") {
                val now = MockTimeProvider.fixedInstant()
                val membership = OrganizationMembership(
                    id = "membership-123",
                    userId = "user-123",
                    organizationId = "org-123",
                    role = "viewer",
                    permissions = emptySet(),
                    createdAt = now,
                    updatedAt = now
                )
                
                membership.permissions.shouldBeEmpty()
            }
        }
    }
    
    describe("OrganizationInvitation model") {
        
        describe("construction") {
            it("should create invitation with all required fields") {
                val now = MockTimeProvider.fixedInstant()
                val future = MockTimeProvider.futureInstant(24)
                val invitation = OrganizationInvitation(
                    id = "invitation-123",
                    organizationId = "org-123",
                    email = "user@example.com",
                    role = "developer",
                    permissions = setOf("read", "write"),
                    invitedBy = "admin-user",
                    token = "invitation-token",
                    expiresAt = future,
                    acceptedAt = null,
                    createdAt = now
                )
                
                invitation.id shouldBe "invitation-123"
                invitation.organizationId shouldBe "org-123"
                invitation.email shouldBe "user@example.com"
                invitation.role shouldBe "developer"
                invitation.permissions shouldBe setOf("read", "write")
                invitation.invitedBy shouldBe "admin-user"
                invitation.token shouldBe "invitation-token"
                invitation.expiresAt shouldBe future
                invitation.acceptedAt.shouldBeNull()
                invitation.createdAt shouldBe now
            }
        }
        
        describe("computed properties") {
            it("should check if invitation is expired") {
                val past = MockTimeProvider.pastInstant(1)
                val future = MockTimeProvider.futureInstant(1)
                val now = MockTimeProvider.fixedInstant()
                
                val expiredInvitation = OrganizationInvitation(
                    id = "invitation-1",
                    organizationId = "org-123",
                    email = "user@example.com",
                    role = "developer",
                    invitedBy = "admin",
                    token = "token",
                    expiresAt = past,
                    createdAt = now
                )
                
                val validInvitation = OrganizationInvitation(
                    id = "invitation-2",
                    organizationId = "org-123",
                    email = "user@example.com",
                    role = "developer",
                    invitedBy = "admin",
                    token = "token",
                    expiresAt = future,
                    createdAt = now
                )
                
                expiredInvitation.isExpired shouldBe true
                validInvitation.isExpired shouldBe false
            }
            
            it("should check if invitation is accepted") {
                val now = MockTimeProvider.fixedInstant()
                val future = MockTimeProvider.futureInstant(24)
                
                val acceptedInvitation = OrganizationInvitation(
                    id = "invitation-1",
                    organizationId = "org-123",
                    email = "user@example.com",
                    role = "developer",
                    invitedBy = "admin",
                    token = "token",
                    expiresAt = future,
                    acceptedAt = now,
                    createdAt = now
                )
                
                val pendingInvitation = OrganizationInvitation(
                    id = "invitation-2",
                    organizationId = "org-123",
                    email = "user@example.com",
                    role = "developer",
                    invitedBy = "admin",
                    token = "token",
                    expiresAt = future,
                    acceptedAt = null,
                    createdAt = now
                )
                
                acceptedInvitation.isAccepted shouldBe true
                pendingInvitation.isAccepted shouldBe false
            }
        }
    }
    
    describe("OrganizationRole enum") {
        
        describe("role permissions") {
            it("should have correct permissions for OWNER role") {
                val ownerPermissions = OrganizationRole.OWNER.permissions
                
                ownerPermissions shouldContain "org:admin"
                ownerPermissions shouldContain "user:admin"
                ownerPermissions shouldContain "vault:admin"
                ownerPermissions shouldContain "flow:admin"
                ownerPermissions shouldContain "task:admin"
                ownerPermissions shouldContain "monitor:admin"
                ownerPermissions shouldContain "sync:admin"
                ownerPermissions shouldContain "insight:admin"
                ownerPermissions shouldContain "hub:admin"
            }
            
            it("should have correct permissions for ADMIN role") {
                val adminPermissions = OrganizationRole.ADMIN.permissions
                
                adminPermissions shouldContain "org:read"
                adminPermissions shouldContain "org:write"
                adminPermissions shouldContain "user:invite"
                adminPermissions shouldContain "user:remove"
                adminPermissions shouldContain "vault:admin"
                adminPermissions shouldContain "flow:admin"
            }
            
            it("should have correct permissions for DEVELOPER role") {
                val devPermissions = OrganizationRole.DEVELOPER.permissions
                
                devPermissions shouldContain "org:read"
                devPermissions shouldContain "vault:read"
                devPermissions shouldContain "vault:write"
                devPermissions shouldContain "flow:read"
                devPermissions shouldContain "flow:write"
                devPermissions shouldContain "flow:execute"
                devPermissions shouldContain "task:read"
                devPermissions shouldContain "task:write"
                devPermissions shouldContain "task:execute"
            }
            
            it("should have correct permissions for VIEWER role") {
                val viewerPermissions = OrganizationRole.VIEWER.permissions
                
                viewerPermissions shouldContain "org:read"
                viewerPermissions shouldContain "vault:read"
                viewerPermissions shouldContain "flow:read"
                viewerPermissions shouldContain "task:read"
                viewerPermissions shouldContain "monitor:read"
                viewerPermissions shouldContain "sync:read"
                viewerPermissions shouldContain "insight:read"
                viewerPermissions shouldContain "hub:read"
            }
        }
        
        describe("permission hierarchy") {
            it("should have OWNER with most permissions") {
                val ownerCount = OrganizationRole.OWNER.permissions.size
                val adminCount = OrganizationRole.ADMIN.permissions.size
                val devCount = OrganizationRole.DEVELOPER.permissions.size
                val viewerCount = OrganizationRole.VIEWER.permissions.size
                
                ownerCount shouldBe 13
                adminCount shouldBe 11
                devCount shouldBe 11
                viewerCount shouldBe 8
            }
            
            it("should have no admin permissions in VIEWER role") {
                val viewerPermissions = OrganizationRole.VIEWER.permissions
                
                viewerPermissions.none { it.contains("admin") } shouldBe true
                viewerPermissions.none { it.contains("write") } shouldBe true
                viewerPermissions.none { it.contains("delete") } shouldBe true
            }
        }
    }
})