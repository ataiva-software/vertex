package com.ataiva.eden.insight.repository.impl

import com.ataiva.eden.insight.model.*
import com.ataiva.eden.insight.repository.DashboardsTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlinx.serialization.json.Json

class DashboardRepositoryImplTest {
    
    private lateinit var repository: DashboardRepositoryImpl
    private lateinit var testDb: Database
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    
    @BeforeEach
    fun setUp() {
        // Set up in-memory database for testing
        testDb = Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        
        // Create tables
        transaction(testDb) {
            SchemaUtils.create(DashboardsTable)
        }
        
        // Initialize repository
        repository = DashboardRepositoryImpl(testDb)
    }
    
    @AfterEach
    fun tearDown() {
        // Drop tables
        transaction(testDb) {
            SchemaUtils.drop(DashboardsTable)
        }
    }
    
    @Test
    fun `save should persist dashboard`() = runBlocking {
        // Arrange
        val dashboard = createSampleDashboard()
        
        // Act
        val savedDashboard = repository.save(dashboard)
        
        // Assert
        assertNotNull(savedDashboard)
        assertEquals(dashboard.id, savedDashboard.id)
        assertEquals(dashboard.name, savedDashboard.name)
        assertEquals(dashboard.widgets.size, savedDashboard.widgets.size)
    }
    
    @Test
    fun `findById should return dashboard when it exists`() = runBlocking {
        // Arrange
        val dashboard = createSampleDashboard()
        repository.save(dashboard)
        
        // Act
        val foundDashboard = repository.findById(dashboard.id)
        
        // Assert
        assertNotNull(foundDashboard)
        assertEquals(dashboard.id, foundDashboard?.id)
        assertEquals(dashboard.name, foundDashboard?.name)
        assertEquals(dashboard.widgets.size, foundDashboard?.widgets?.size)
    }
    
    @Test
    fun `findById should return null when dashboard does not exist`() = runBlocking {
        // Act
        val foundDashboard = repository.findById("non-existent-id")
        
        // Assert
        assertNull(foundDashboard)
    }
    
    @Test
    fun `findAll should return all dashboards`() = runBlocking {
        // Arrange
        val dashboard1 = createSampleDashboard()
        val dashboard2 = createSampleDashboard().copy(id = "dashboard_2", name = "Second Dashboard")
        repository.save(dashboard1)
        repository.save(dashboard2)
        
        // Act
        val allDashboards = repository.findAll()
        
        // Assert
        assertEquals(2, allDashboards.size)
        assertTrue(allDashboards.any { it.id == dashboard1.id })
        assertTrue(allDashboards.any { it.id == dashboard2.id })
    }
    
    @Test
    fun `update should modify existing dashboard`() = runBlocking {
        // Arrange
        val dashboard = createSampleDashboard()
        repository.save(dashboard)
        
        val updatedDashboard = dashboard.copy(
            name = "Updated Dashboard",
            description = "Updated description",
            isPublic = true
        )
        
        // Act
        repository.update(updatedDashboard)
        val retrievedDashboard = repository.findById(dashboard.id)
        
        // Assert
        assertNotNull(retrievedDashboard)
        assertEquals("Updated Dashboard", retrievedDashboard?.name)
        assertEquals("Updated description", retrievedDashboard?.description)
        assertTrue(retrievedDashboard?.isPublic ?: false)
    }
    
    @Test
    fun `delete should remove dashboard`() = runBlocking {
        // Arrange
        val dashboard = createSampleDashboard()
        repository.save(dashboard)
        
        // Act
        val result = repository.delete(dashboard.id)
        val retrievedDashboard = repository.findById(dashboard.id)
        
        // Assert
        assertTrue(result)
        assertNull(retrievedDashboard)
    }
    
    @Test
    fun `delete should return false when dashboard does not exist`() = runBlocking {
        // Act
        val result = repository.delete("non-existent-id")
        
        // Assert
        assertFalse(result)
    }
    
    @Test
    fun `findByCreatedBy should return dashboards by creator`() = runBlocking {
        // Arrange
        val dashboard1 = createSampleDashboard()
        val dashboard2 = createSampleDashboard().copy(id = "dashboard_2", createdBy = "another-user")
        repository.save(dashboard1)
        repository.save(dashboard2)
        
        // Act
        val userDashboards = repository.findByCreatedBy("test-user")
        
        // Assert
        assertEquals(1, userDashboards.size)
        assertEquals(dashboard1.id, userDashboards[0].id)
    }
    
    @Test
    fun `findPublic should return only public dashboards`() = runBlocking {
        // Arrange
        val privateDashboard = createSampleDashboard()
        val publicDashboard = createSampleDashboard().copy(id = "dashboard_2", isPublic = true)
        repository.save(privateDashboard)
        repository.save(publicDashboard)
        
        // Act
        val publicDashboards = repository.findPublic()
        
        // Assert
        assertEquals(1, publicDashboards.size)
        assertEquals(publicDashboard.id, publicDashboards[0].id)
    }
    
    @Test
    fun `findByCreatedByOrPublic should return user's dashboards and public ones`() = runBlocking {
        // Arrange
        val userPrivateDashboard = createSampleDashboard()
        val userPublicDashboard = createSampleDashboard().copy(id = "dashboard_2", isPublic = true)
        val otherUserPrivateDashboard = createSampleDashboard().copy(id = "dashboard_3", createdBy = "another-user")
        val otherUserPublicDashboard = createSampleDashboard().copy(
            id = "dashboard_4", 
            createdBy = "another-user", 
            isPublic = true
        )
        
        repository.save(userPrivateDashboard)
        repository.save(userPublicDashboard)
        repository.save(otherUserPrivateDashboard)
        repository.save(otherUserPublicDashboard)
        
        // Act
        val accessibleDashboards = repository.findByCreatedByOrPublic("test-user")
        
        // Assert
        assertEquals(3, accessibleDashboards.size)
        assertTrue(accessibleDashboards.any { it.id == userPrivateDashboard.id })
        assertTrue(accessibleDashboards.any { it.id == userPublicDashboard.id })
        assertTrue(accessibleDashboards.any { it.id == otherUserPublicDashboard.id })
        assertFalse(accessibleDashboards.any { it.id == otherUserPrivateDashboard.id })
    }
    
    private fun createSampleDashboard(): Dashboard {
        val widget1 = DashboardWidget(
            id = "widget_1",
            title = "Test Widget 1",
            type = WidgetType.CHART,
            dataSource = WidgetDataSource.QUERY,
            sourceId = "query_1",
            config = mapOf("chartType" to "bar", "height" to "300px")
        )
        
        val widget2 = DashboardWidget(
            id = "widget_2",
            title = "Test Widget 2",
            type = WidgetType.KPI,
            dataSource = WidgetDataSource.KPI,
            sourceId = "kpi_1",
            config = mapOf("displayType" to "value", "showTrend" to "true")
        )
        
        return Dashboard(
            id = "dashboard_1",
            name = "Test Dashboard",
            description = "A test dashboard",
            widgets = listOf(widget1, widget2),
            layout = DashboardLayout(
                columns = 12,
                rows = 2,
                widgetPositions = mapOf(
                    "widget_1" to WidgetPosition(0, 0, 6, 1),
                    "widget_2" to WidgetPosition(6, 0, 6, 1)
                )
            ),
            permissions = DashboardPermissions(
                owners = listOf("test-user"),
                editors = listOf("editor-user"),
                viewers = listOf("viewer-user")
            ),
            createdBy = "test-user",
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis(),
            isPublic = false,
            tags = listOf("test", "sample")
        )
    }
}