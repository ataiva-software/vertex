package com.ataiva.eden.insight.repository.impl

import com.ataiva.eden.insight.model.AnalyticsQuery
import com.ataiva.eden.insight.model.QueryType
import com.ataiva.eden.insight.repository.AnalyticsQueries
import com.ataiva.eden.insight.repository.AnalyticsQueryRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Implementation of AnalyticsQueryRepository using Exposed
 */
class AnalyticsQueryRepositoryImpl(
    private val database: Database,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AnalyticsQueryRepository {

    override suspend fun findById(id: String): AnalyticsQuery? = transaction(database) {
        AnalyticsQueries.select { AnalyticsQueries.id eq id }
            .singleOrNull()
            ?.toAnalyticsQuery()
    }

    override suspend fun findAll(): List<AnalyticsQuery> = transaction(database) {
        AnalyticsQueries.selectAll()
            .map { it.toAnalyticsQuery() }
    }

    override suspend fun save(entity: AnalyticsQuery): AnalyticsQuery = transaction(database) {
        AnalyticsQueries.insert {
            it[id] = entity.id
            it[name] = entity.name
            it[description] = entity.description
            it[queryText] = entity.queryText
            it[queryType] = entity.queryType.name
            it[parameters] = json.encodeToString(entity.parameters)
            it[createdBy] = entity.createdBy
            it[createdAt] = Instant.ofEpochMilli(entity.createdAt)
            it[lastModified] = Instant.ofEpochMilli(entity.lastModified)
            it[isActive] = entity.isActive
            it[tags] = json.encodeToString(entity.tags)
        }
        entity
    }

    override suspend fun update(entity: AnalyticsQuery): Boolean = transaction(database) {
        val updatedRows = AnalyticsQueries.update({ AnalyticsQueries.id eq entity.id }) {
            it[name] = entity.name
            it[description] = entity.description
            it[queryText] = entity.queryText
            it[queryType] = entity.queryType.name
            it[parameters] = json.encodeToString(entity.parameters)
            it[lastModified] = Instant.ofEpochMilli(entity.lastModified)
            it[isActive] = entity.isActive
            it[tags] = json.encodeToString(entity.tags)
        }
        updatedRows > 0
    }

    override suspend fun delete(id: String): Boolean = transaction(database) {
        val deletedRows = AnalyticsQueries.deleteWhere { AnalyticsQueries.id eq id }
        deletedRows > 0
    }

    override suspend fun count(): Long = transaction(database) {
        AnalyticsQueries.selectAll().count()
    }

    override suspend fun findByName(name: String): AnalyticsQuery? = transaction(database) {
        AnalyticsQueries.select { AnalyticsQueries.name eq name }
            .singleOrNull()
            ?.toAnalyticsQuery()
    }

    override suspend fun findByCreatedBy(createdBy: String): List<AnalyticsQuery> = transaction(database) {
        AnalyticsQueries.select { AnalyticsQueries.createdBy eq createdBy }
            .map { it.toAnalyticsQuery() }
    }

    override suspend fun findByQueryType(queryType: QueryType): List<AnalyticsQuery> = transaction(database) {
        AnalyticsQueries.select { AnalyticsQueries.queryType eq queryType.name }
            .map { it.toAnalyticsQuery() }
    }

    override suspend fun findByTags(tags: List<String>): List<AnalyticsQuery> = transaction(database) {
        // This is a simplified implementation that checks if any of the tags match
        // A more sophisticated implementation would use a JSON contains operator
        AnalyticsQueries.selectAll()
            .map { it.toAnalyticsQuery() }
            .filter { query -> query.tags.any { it in tags } }
    }

    override suspend fun findActive(): List<AnalyticsQuery> = transaction(database) {
        AnalyticsQueries.select { AnalyticsQueries.isActive eq true }
            .map { it.toAnalyticsQuery() }
    }

    override suspend fun findByCreatedByAndActive(createdBy: String, isActive: Boolean): List<AnalyticsQuery> = transaction(database) {
        AnalyticsQueries.select { 
            (AnalyticsQueries.createdBy eq createdBy) and (AnalyticsQueries.isActive eq isActive)
        }.map { it.toAnalyticsQuery() }
    }

    override suspend fun updateStatus(id: String, isActive: Boolean): Boolean = transaction(database) {
        val updatedRows = AnalyticsQueries.update({ AnalyticsQueries.id eq id }) {
            it[AnalyticsQueries.isActive] = isActive
            it[lastModified] = Instant.now()
        }
        updatedRows > 0
    }

    override suspend fun search(namePattern: String): List<AnalyticsQuery> = transaction(database) {
        AnalyticsQueries.select { AnalyticsQueries.name like "%$namePattern%" }
            .map { it.toAnalyticsQuery() }
    }

    /**
     * Extension function to convert ResultRow to AnalyticsQuery
     */
    private fun ResultRow.toAnalyticsQuery(): AnalyticsQuery {
        return AnalyticsQuery(
            id = this[AnalyticsQueries.id],
            name = this[AnalyticsQueries.name],
            description = this[AnalyticsQueries.description],
            queryText = this[AnalyticsQueries.queryText],
            queryType = QueryType.valueOf(this[AnalyticsQueries.queryType]),
            parameters = json.decodeFromString(this[AnalyticsQueries.parameters]),
            createdBy = this[AnalyticsQueries.createdBy],
            createdAt = this[AnalyticsQueries.createdAt].toEpochMilli(),
            lastModified = this[AnalyticsQueries.lastModified].toEpochMilli(),
            isActive = this[AnalyticsQueries.isActive],
            tags = json.decodeFromString(this[AnalyticsQueries.tags])
        )
    }
}