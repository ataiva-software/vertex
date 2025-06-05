package com.ataiva.eden.insight.repository.impl

import com.ataiva.eden.insight.model.AnalyticsQuery
import com.ataiva.eden.insight.model.QueryType
import com.ataiva.eden.insight.repository.AnalyticsQueriesTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.*

class AnalyticsQueryRepositoryImplTest {
    
    private lateinit var repository: AnalyticsQueryRepositoryImpl
    private lateinit var testDb: Database
    
    @BeforeEach
    fun setUp() {
        // Set up in-memory database for testing
        testDb = Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        
        // Create tables
        transaction(testDb) {
            SchemaUtils.create(AnalyticsQueriesTable)
        }
        
        // Initialize repository
        repository = AnalyticsQueryRepositoryImpl(testDb)
    }
    
    @AfterEach
    fun tearDown() {
        // Drop tables
        transaction(testDb) {
            SchemaUtils.drop(AnalyticsQueriesTable)
        }
    }
    
    @Test
    fun `save should persist analytics query`() = runBlocking {
        // Arrange
        val query = createSampleQuery()
        
        // Act
        val savedQuery = repository.save(query)
        
        // Assert
        assertNotNull(savedQuery)
        assertEquals(query.id, savedQuery.id)
        assertEquals(query.name, savedQuery.name)
        assertEquals(query.queryText, savedQuery.queryText)
    }
    
    @Test
    fun `findById should return query when it exists`() = runBlocking {
        // Arrange
        val query = createSampleQuery()
        repository.save(query)
        
        // Act
        val foundQuery = repository.findById(query.id)
        
        // Assert
        assertNotNull(foundQuery)
        assertEquals(query.id, foundQuery?.id)
        assertEquals(query.name, foundQuery?.name)
    }
    
    @Test
    fun `findById should return null when query does not exist`() = runBlocking {
        // Act
        val foundQuery = repository.findById("non-existent-id")
        
        // Assert
        assertNull(foundQuery)
    }
    
    @Test
    fun `findAll should return all queries`() = runBlocking {
        // Arrange
        val query1 = createSampleQuery()
        val query2 = createSampleQuery().copy(id = "query_2", name = "Second Query")
        repository.save(query1)
        repository.save(query2)
        
        // Act
        val allQueries = repository.findAll()
        
        // Assert
        assertEquals(2, allQueries.size)
        assertTrue(allQueries.any { it.id == query1.id })
        assertTrue(allQueries.any { it.id == query2.id })
    }
    
    @Test
    fun `update should modify existing query`() = runBlocking {
        // Arrange
        val query = createSampleQuery()
        repository.save(query)
        
        val updatedQuery = query.copy(
            name = "Updated Query",
            description = "Updated description",
            isActive = false
        )
        
        // Act
        repository.update(updatedQuery)
        val retrievedQuery = repository.findById(query.id)
        
        // Assert
        assertNotNull(retrievedQuery)
        assertEquals("Updated Query", retrievedQuery?.name)
        assertEquals("Updated description", retrievedQuery?.description)
        assertFalse(retrievedQuery?.isActive ?: true)
    }
    
    @Test
    fun `delete should remove query`() = runBlocking {
        // Arrange
        val query = createSampleQuery()
        repository.save(query)
        
        // Act
        val result = repository.delete(query.id)
        val retrievedQuery = repository.findById(query.id)
        
        // Assert
        assertTrue(result)
        assertNull(retrievedQuery)
    }
    
    @Test
    fun `delete should return false when query does not exist`() = runBlocking {
        // Act
        val result = repository.delete("non-existent-id")
        
        // Assert
        assertFalse(result)
    }
    
    @Test
    fun `findByCreatedBy should return queries by creator`() = runBlocking {
        // Arrange
        val query1 = createSampleQuery()
        val query2 = createSampleQuery().copy(id = "query_2", createdBy = "another-user")
        repository.save(query1)
        repository.save(query2)
        
        // Act
        val userQueries = repository.findByCreatedBy("test-user")
        
        // Assert
        assertEquals(1, userQueries.size)
        assertEquals(query1.id, userQueries[0].id)
    }
    
    @Test
    fun `findByQueryType should return queries by type`() = runBlocking {
        // Arrange
        val query1 = createSampleQuery()
        val query2 = createSampleQuery().copy(id = "query_2", queryType = QueryType.CUSTOM)
        repository.save(query1)
        repository.save(query2)
        
        // Act
        val sqlQueries = repository.findByQueryType(QueryType.SQL)
        val customQueries = repository.findByQueryType(QueryType.CUSTOM)
        
        // Assert
        assertEquals(1, sqlQueries.size)
        assertEquals(query1.id, sqlQueries[0].id)
        
        assertEquals(1, customQueries.size)
        assertEquals(query2.id, customQueries[0].id)
    }
    
    @Test
    fun `findActive should return only active queries`() = runBlocking {
        // Arrange
        val activeQuery = createSampleQuery()
        val inactiveQuery = createSampleQuery().copy(id = "query_2", isActive = false)
        repository.save(activeQuery)
        repository.save(inactiveQuery)
        
        // Act
        val activeQueries = repository.findActive()
        
        // Assert
        assertEquals(1, activeQueries.size)
        assertEquals(activeQuery.id, activeQueries[0].id)
    }
    
    @Test
    fun `findByCreatedByAndActive should filter by creator and active status`() = runBlocking {
        // Arrange
        val activeQuery = createSampleQuery()
        val inactiveQuery = createSampleQuery().copy(id = "query_2", isActive = false)
        val otherUserQuery = createSampleQuery().copy(id = "query_3", createdBy = "another-user")
        repository.save(activeQuery)
        repository.save(inactiveQuery)
        repository.save(otherUserQuery)
        
        // Act
        val userActiveQueries = repository.findByCreatedByAndActive("test-user", true)
        
        // Assert
        assertEquals(1, userActiveQueries.size)
        assertEquals(activeQuery.id, userActiveQueries[0].id)
    }
    
    private fun createSampleQuery(): AnalyticsQuery {
        return AnalyticsQuery(
            id = "query_1",
            name = "Test Query",
            description = "A test query",
            queryText = "SELECT * FROM test",
            queryType = QueryType.SQL,
            parameters = mapOf("param1" to "value1"),
            createdBy = "test-user",
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis(),
            isActive = true,
            tags = listOf("test", "sample")
        )
    }
}