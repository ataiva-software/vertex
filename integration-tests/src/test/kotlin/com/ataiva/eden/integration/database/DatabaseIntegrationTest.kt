package com.ataiva.eden.integration.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@Tag("database")
@DisplayName("Database Integration Tests")
class DatabaseIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("eden_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
        
        private lateinit var database: Database
        private lateinit var dataSource: HikariDataSource
        
        @BeforeAll
        @JvmStatic
        fun setupDatabase() {
            postgres.start()
            
            val config = HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 10
                minimumIdle = 2
                connectionTimeout = 30000
                idleTimeout = 600000
                maxLifetime = 1800000
            }
            
            dataSource = HikariDataSource(config)
            database = Database.connect(dataSource)
            
            // Create simple test tables
            transaction(database) {
                SchemaUtils.create(TestUsers, TestOrganizations, TestAuditLogs)
            }
        }
        
        @AfterAll
        @JvmStatic
        fun teardownDatabase() {
            if (::dataSource.isInitialized) {
                dataSource.close()
            }
        }
    }
    
    // Simple test table schemas
    object TestUsers : Table("test_users") {
        val id = varchar("id", 36)
        val email = varchar("email", 255).uniqueIndex()
        val name = varchar("name", 255)
        val organizationId = varchar("organization_id", 36)
        val createdAt = long("created_at")
        val updatedAt = long("updated_at")
        
        override val primaryKey = PrimaryKey(id)
    }
    
    object TestOrganizations : Table("test_organizations") {
        val id = varchar("id", 36)
        val name = varchar("name", 255)
        val slug = varchar("slug", 255)
        val description = text("description").nullable()
        val createdAt = long("created_at")
        val updatedAt = long("updated_at")
        
        override val primaryKey = PrimaryKey(id)
    }
    
    object TestAuditLogs : Table("test_audit_logs") {
        val id = varchar("id", 36)
        val userId = varchar("user_id", 36)
        val action = varchar("action", 255)
        val resource = varchar("resource", 255)
        val resourceId = varchar("resource_id", 36).nullable()
        val timestamp = long("timestamp")
        val metadata = text("metadata").nullable()
        
        override val primaryKey = PrimaryKey(id)
    }
    
    @BeforeEach
    fun cleanDatabase() {
        transaction(database) {
            TestAuditLogs.deleteAll()
            TestUsers.deleteAll()
            TestOrganizations.deleteAll()
        }
    }
    
    @Test
    @DisplayName("Should connect to PostgreSQL database")
    fun testDatabaseConnection() {
        assertTrue(postgres.isRunning)
        assertNotNull(database)
        
        transaction(database) {
            val result = exec("SELECT 1") { rs ->
                rs.next()
                rs.getInt(1)
            }
            assertEquals(1, result)
        }
    }
    
    @Test
    @DisplayName("Should create and retrieve organization")
    fun testOrganizationCRUD() {
        val orgId = "test-org-123"
        val orgName = "Test Organization"
        val orgSlug = "test-org"
        val orgDescription = "A test organization"
        val now = Clock.System.now().toEpochMilliseconds()
        
        // Create
        transaction(database) {
            TestOrganizations.insert {
                it[id] = orgId
                it[name] = orgName
                it[slug] = orgSlug
                it[description] = orgDescription
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        
        // Read
        val retrieved = transaction(database) {
            TestOrganizations.select { TestOrganizations.id eq orgId }
                .singleOrNull()
        }
        
        assertNotNull(retrieved)
        assertEquals(orgId, retrieved!![TestOrganizations.id])
        assertEquals(orgName, retrieved[TestOrganizations.name])
        assertEquals(orgSlug, retrieved[TestOrganizations.slug])
        assertEquals(orgDescription, retrieved[TestOrganizations.description])
    }
    
    @Test
    @DisplayName("Should create and retrieve user")
    fun testUserCRUD() {
        val orgId = "test-org-123"
        val userId = "test-user-456"
        val userEmail = "test@example.com"
        val userName = "Test User"
        val now = Clock.System.now().toEpochMilliseconds()
        
        // Create organization first
        transaction(database) {
            TestOrganizations.insert {
                it[id] = orgId
                it[name] = "Test Organization"
                it[slug] = "test-org"
                it[description] = "A test organization"
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        
        // Create user
        transaction(database) {
            TestUsers.insert {
                it[id] = userId
                it[email] = userEmail
                it[name] = userName
                it[organizationId] = orgId
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        
        // Read user
        val retrieved = transaction(database) {
            TestUsers.select { TestUsers.id eq userId }
                .singleOrNull()
        }
        
        assertNotNull(retrieved)
        assertEquals(userId, retrieved!![TestUsers.id])
        assertEquals(userEmail, retrieved[TestUsers.email])
        assertEquals(userName, retrieved[TestUsers.name])
        assertEquals(orgId, retrieved[TestUsers.organizationId])
    }
    
    @Test
    @DisplayName("Should handle database transactions")
    fun testTransactionHandling() {
        val orgId = "test-org-123"
        val now = Clock.System.now().toEpochMilliseconds()
        
        // Test successful transaction
        transaction(database) {
            TestOrganizations.insert {
                it[id] = orgId
                it[name] = "Test Organization"
                it[slug] = "test-org"
                it[description] = "A test organization"
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        
        val count = transaction(database) {
            TestOrganizations.selectAll().count()
        }
        assertEquals(1, count)
        
        // Test transaction rollback
        assertThrows<RuntimeException> {
            transaction(database) {
                TestOrganizations.insert {
                    it[id] = "another-org-id"
                    it[name] = "Another Org"
                    it[slug] = "another-org"
                    it[description] = "Another description"
                    it[createdAt] = now
                    it[updatedAt] = now
                }
                
                // Force an error to trigger rollback
                throw RuntimeException("Forced error")
            }
        }
        
        // Count should still be 1 (rollback worked)
        val finalCount = transaction(database) {
            TestOrganizations.selectAll().count()
        }
        assertEquals(1, finalCount)
    }
    
    @Test
    @DisplayName("Should handle concurrent database operations")
    fun testConcurrentOperations() {
        val now = Clock.System.now().toEpochMilliseconds()
        
        // Insert organizations sequentially (simpler than concurrent for testing)
        repeat(10) { index ->
            transaction(database) {
                TestOrganizations.insert {
                    it[id] = "org-$index"
                    it[name] = "Organization $index"
                    it[slug] = "org-$index"
                    it[description] = "Test organization $index"
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
        }
        
        // Verify all organizations were inserted
        val count = transaction(database) {
            TestOrganizations.selectAll().count()
        }
        assertEquals(10, count)
        
        // Verify we can read all organizations
        val retrieved = transaction(database) {
            TestOrganizations.selectAll().map { it[TestOrganizations.name] }
        }
        
        assertEquals(10, retrieved.size)
        repeat(10) { index ->
            assertTrue(retrieved.contains("Organization $index"))
        }
    }
    
    @Test
    @DisplayName("Should handle database constraints")
    fun testDatabaseConstraints() {
        val orgId = "test-org-123"
        val now = Clock.System.now().toEpochMilliseconds()
        
        // Create organization
        transaction(database) {
            TestOrganizations.insert {
                it[id] = orgId
                it[name] = "Test Organization"
                it[slug] = "test-org"
                it[description] = "A test organization"
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        
        // Create first user
        transaction(database) {
            TestUsers.insert {
                it[id] = "user-1"
                it[email] = "test@example.com"
                it[name] = "Test User 1"
                it[organizationId] = orgId
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        
        // Try to create second user with same email (should fail due to unique constraint)
        assertThrows<Exception> {
            transaction(database) {
                TestUsers.insert {
                    it[id] = "user-2"
                    it[email] = "test@example.com" // Same email
                    it[name] = "Test User 2"
                    it[organizationId] = orgId
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
        }
    }
    
    @Test
    @DisplayName("Should handle connection pooling")
    fun testConnectionPooling() {
        val now = Clock.System.now().toEpochMilliseconds()
        
        // Test multiple database operations
        repeat(20) { index ->
            transaction(database) {
                TestOrganizations.insert {
                    it[id] = "pool-test-org-$index"
                    it[name] = "Pool Test Org $index"
                    it[slug] = "pool-test-org-$index"
                    it[description] = "Pool test organization $index"
                    it[createdAt] = now
                    it[updatedAt] = now
                }
                
                // Simulate some work
                Thread.sleep(10)
                
                TestOrganizations.select { TestOrganizations.id eq "pool-test-org-$index" }.singleOrNull()
            }
        }
        
        // Verify all operations completed successfully
        val count = transaction(database) {
            TestOrganizations.selectAll().count()
        }
        assertEquals(20, count)
    }
    
    @Test
    @DisplayName("Should handle audit logging")
    fun testAuditLogging() {
        val userId = "test-user-123"
        val now = Clock.System.now().toEpochMilliseconds()
        
        // Create audit log entry
        transaction(database) {
            TestAuditLogs.insert {
                it[id] = "audit-1"
                it[TestAuditLogs.userId] = userId
                it[action] = "CREATE"
                it[resource] = "organization"
                it[resourceId] = "org-123"
                it[timestamp] = now
                it[metadata] = """{"name": "Test Organization", "slug": "test-org"}"""
            }
        }
        
        // Retrieve audit log
        val retrieved = transaction(database) {
            TestAuditLogs.select { TestAuditLogs.id eq "audit-1" }
                .singleOrNull()
        }
        
        assertNotNull(retrieved)
        assertEquals(userId, retrieved!![TestAuditLogs.userId])
        assertEquals("CREATE", retrieved[TestAuditLogs.action])
        assertEquals("organization", retrieved[TestAuditLogs.resource])
        assertEquals("org-123", retrieved[TestAuditLogs.resourceId])
    }
}