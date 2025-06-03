package com.ataiva.eden.testing.builders

import com.ataiva.eden.core.models.Organization
import com.ataiva.eden.core.models.OrganizationSettings
import com.ataiva.eden.core.models.OrganizationPlan

/**
 * Test data builder for Organization model
 * Provides fluent API for creating test Organization instances with sensible defaults
 */
class OrganizationTestDataBuilder {
    private var id: String = "test-org-${kotlin.random.Random.nextInt(1000, 9999)}"
    private var name: String = "Test Organization"
    private var slug: String = "test-org"
    private var description: String = "A test organization"
    private var settings: OrganizationSettings = OrganizationSettings()
    private var plan: OrganizationPlan = OrganizationPlan.FREE
    private var isActive: Boolean = true
    
    fun withId(id: String) = apply { this.id = id }
    
    fun withName(name: String) = apply { this.name = name }
    
    fun withSlug(slug: String) = apply { this.slug = slug }
    
    fun withDescription(description: String) = apply { this.description = description }
    
    fun withSettings(settings: OrganizationSettings) = apply { this.settings = settings }
    
    fun withPlan(plan: OrganizationPlan) = apply { this.plan = plan }
    
    fun withActive(isActive: Boolean) = apply { this.isActive = isActive }
    
    fun withFreePlan() = apply { this.plan = OrganizationPlan.FREE }
    
    fun withStarterPlan() = apply { this.plan = OrganizationPlan.STARTER }
    
    fun withProfessionalPlan() = apply { this.plan = OrganizationPlan.PROFESSIONAL }
    
    fun withEnterprisePlan() = apply { this.plan = OrganizationPlan.ENTERPRISE }
    
    fun withMfaRequired() = apply { 
        this.settings = this.settings.copy(requireMfa = true)
    }
    
    fun withAllowedDomains(domains: List<String>) = apply {
        this.settings = this.settings.copy(allowedDomains = domains)
    }
    
    fun withSessionTimeout(minutes: Int) = apply {
        this.settings = this.settings.copy(sessionTimeoutMinutes = minutes)
    }
    
    fun inactive() = apply { this.isActive = false }
    
    fun build(): Organization {
        // Create a mock timestamp for testing
        val mockInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(1640995200000L)
        
        return Organization(
            id = id,
            name = name,
            slug = slug,
            description = description,
            settings = settings,
            plan = plan,
            isActive = isActive,
            createdAt = mockInstant,
            updatedAt = mockInstant
        )
    }
    
    companion object {
        fun anOrganization() = OrganizationTestDataBuilder()
        
        fun aFreeOrganization() = OrganizationTestDataBuilder().withFreePlan()
        
        fun anEnterpriseOrganization() = OrganizationTestDataBuilder()
            .withEnterprisePlan()
            .withMfaRequired()
        
        fun anInactiveOrganization() = OrganizationTestDataBuilder().inactive()
        
        fun organizationsWithNames(names: List<String>): List<Organization> {
            return names.map { name ->
                anOrganization()
                    .withName(name)
                    .withSlug(name.lowercase().replace(" ", "-"))
                    .build()
            }
        }
        
        fun multipleOrganizations(count: Int = 3): List<Organization> {
            return (1..count).map { index ->
                anOrganization()
                    .withName("Organization $index")
                    .withSlug("org-$index")
                    .withDescription("Test organization $index")
                    .build()
            }
        }
    }
}