package com.ataiva.eden.insight.repository.impl

import com.ataiva.eden.insight.model.AggregationType
import com.ataiva.eden.insight.model.Metric
import com.ataiva.eden.insight.model.MetricThreshold
import com.ataiva.eden.insight.repository.Metrics
import com.ataiva.eden.insight.repository.MetricRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Implementation of MetricRepository using Exposed
 */
class MetricRepositoryImpl(
    private val database: Database,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : MetricRepository {

    override suspend fun findById(id: String): Metric? = transaction(database) {
        Metrics.select { Metrics.id eq id }
            .singleOrNull()
            ?.toMetric()
    }

    override suspend fun findAll(): List<Metric> = transaction(database) {
        Metrics.selectAll()
            .map { it.toMetric() }
    }

    override suspend fun save(entity: Metric): Metric = transaction(database) {
        Metrics.insert {
            it[id] = entity.id
            it[name] = entity.name
            it[description] = entity.description
            it[category] = entity.category
            it[unit] = entity.unit
            it[aggregationType] = entity.aggregationType.name
            it[queryId] = entity.queryId
            it[thresholds] = json.encodeToString(entity.thresholds)
            it[isActive] = entity.isActive
            it[createdAt] = Instant.ofEpochMilli(entity.createdAt)
        }
        entity
    }

    override suspend fun update(entity: Metric): Boolean = transaction(database) {
        val updatedRows = Metrics.update({ Metrics.id eq entity.id }) {
            it[name] = entity.name
            it[description] = entity.description
            it[category] = entity.category
            it[unit] = entity.unit
            it[aggregationType] = entity.aggregationType.name
            it[queryId] = entity.queryId
            it[thresholds] = json.encodeToString(entity.thresholds)
            it[isActive] = entity.isActive
        }
        updatedRows > 0
    }

    override suspend fun delete(id: String): Boolean = transaction(database) {
        val deletedRows = Metrics.deleteWhere { Metrics.id eq id }
        deletedRows > 0
    }

    override suspend fun count(): Long = transaction(database) {
        Metrics.selectAll().count()
    }

    override suspend fun findByName(name: String): Metric? = transaction(database) {
        Metrics.select { Metrics.name eq name }
            .singleOrNull()
            ?.toMetric()
    }

    override suspend fun findByCategory(category: String): List<Metric> = transaction(database) {
        Metrics.select { Metrics.category eq category }
            .map { it.toMetric() }
    }

    override suspend fun findActive(): List<Metric> = transaction(database) {
        Metrics.select { Metrics.isActive eq true }
            .map { it.toMetric() }
    }

    override suspend fun findByQueryId(queryId: String): List<Metric> = transaction(database) {
        Metrics.select { Metrics.queryId eq queryId }
            .map { it.toMetric() }
    }

    override suspend fun updateStatus(id: String, isActive: Boolean): Boolean = transaction(database) {
        val updatedRows = Metrics.update({ Metrics.id eq id }) {
            it[Metrics.isActive] = isActive
        }
        updatedRows > 0
    }

    override suspend fun search(namePattern: String): List<Metric> = transaction(database) {
        Metrics.select { Metrics.name like "%$namePattern%" }
            .map { it.toMetric() }
    }

    /**
     * Extension function to convert ResultRow to Metric
     */
    private fun ResultRow.toMetric(): Metric {
        return Metric(
            id = this[Metrics.id],
            name = this[Metrics.name],
            description = this[Metrics.description],
            category = this[Metrics.category],
            unit = this[Metrics.unit],
            aggregationType = AggregationType.valueOf(this[Metrics.aggregationType]),
            queryId = this[Metrics.queryId],
            thresholds = json.decodeFromString<List<MetricThreshold>>(this[Metrics.thresholds]),
            isActive = this[Metrics.isActive],
            createdAt = this[Metrics.createdAt].toEpochMilli()
        )
    }
}