package com.ataiva.eden.insight.repository.impl

import com.ataiva.eden.insight.model.MetricValue
import com.ataiva.eden.insight.repository.MetricValues
import com.ataiva.eden.insight.repository.MetricValueRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

/**
 * Implementation of MetricValueRepository using Exposed
 */
class MetricValueRepositoryImpl(
    private val database: Database,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : MetricValueRepository {

    override suspend fun findById(id: String): MetricValue? = transaction(database) {
        MetricValues.select { MetricValues.id eq id }
            .singleOrNull()
            ?.toMetricValue()
    }

    override suspend fun findAll(): List<MetricValue> = transaction(database) {
        MetricValues.selectAll()
            .map { it.toMetricValue() }
    }

    override suspend fun save(entity: MetricValue): MetricValue = transaction(database) {
        // Generate ID since MetricValue doesn't have one
        val id = UUID.randomUUID().toString()
        
        MetricValues.insert {
            it[MetricValues.id] = id
            it[metricId] = entity.metricId
            it[value] = entity.value
            it[timestamp] = Instant.ofEpochMilli(entity.timestamp)
            it[dimensions] = json.encodeToString(entity.dimensions)
            it[metadata] = json.encodeToString(entity.metadata)
        }
        
        // Return the original entity since we don't store the ID in the model
        entity
    }

    override suspend fun update(entity: MetricValue): Boolean = transaction(database) {
        // For update, we need to find by natural key (metricId + timestamp) since we don't have ID in the model
        val updatedRows = MetricValues.update({
            (MetricValues.metricId eq entity.metricId) and
            (MetricValues.timestamp eq Instant.ofEpochMilli(entity.timestamp))
        }) {
            it[metricId] = entity.metricId
            it[value] = entity.value
            it[timestamp] = Instant.ofEpochMilli(entity.timestamp)
            it[dimensions] = json.encodeToString(entity.dimensions)
            it[metadata] = json.encodeToString(entity.metadata)
        }
        updatedRows > 0
    }

    override suspend fun delete(id: String): Boolean = transaction(database) {
        val deletedRows = MetricValues.deleteWhere { MetricValues.id eq id }
        deletedRows > 0
    }

    override suspend fun count(): Long = transaction(database) {
        MetricValues.selectAll().count()
    }

    override suspend fun findByMetricId(metricId: String): List<MetricValue> = transaction(database) {
        MetricValues.select { MetricValues.metricId eq metricId }
            .map { it.toMetricValue() }
    }

    override suspend fun findByMetricIdAndTimeRange(metricId: String, startTime: Long, endTime: Long): List<MetricValue> = transaction(database) {
        val startInstant = Instant.ofEpochMilli(startTime)
        val endInstant = Instant.ofEpochMilli(endTime)
        
        MetricValues.select { 
            (MetricValues.metricId eq metricId) and
            (MetricValues.timestamp greaterEq startInstant) and 
            (MetricValues.timestamp lessEq endInstant) 
        }.map { it.toMetricValue() }
    }

    override suspend fun findByDimension(dimension: String, value: String): List<MetricValue> = transaction(database) {
        // This is a simplified implementation that checks if the dimension exists in the JSON
        // A more sophisticated implementation would use a JSON contains operator
        MetricValues.selectAll()
            .map { it.toMetricValue() }
            .filter { it.dimensions[dimension] == value }
    }

    override suspend fun findLatestByMetricId(metricId: String, limit: Int): List<MetricValue> = transaction(database) {
        MetricValues.select { MetricValues.metricId eq metricId }
            .orderBy(MetricValues.timestamp, SortOrder.DESC)
            .limit(limit)
            .map { it.toMetricValue() }
    }

    override suspend fun deleteOlderThan(timestamp: Long): Int = transaction(database) {
        val instant = Instant.ofEpochMilli(timestamp)
        MetricValues.deleteWhere { MetricValues.timestamp less instant }
    }

    override suspend fun bulkInsert(values: List<MetricValue>): Int = transaction(database) {
        var insertedCount = 0
        
        values.forEach { metricValue ->
            val id = UUID.randomUUID().toString()
            
            MetricValues.insert {
                it[MetricValues.id] = id
                it[metricId] = metricValue.metricId
                it[value] = metricValue.value
                it[timestamp] = Instant.ofEpochMilli(metricValue.timestamp)
                it[dimensions] = json.encodeToString(metricValue.dimensions)
                it[metadata] = json.encodeToString(metricValue.metadata)
            }
            
            insertedCount++
        }
        
        insertedCount
    }

    /**
     * Extension function to convert ResultRow to MetricValue
     */
    private fun ResultRow.toMetricValue(): MetricValue {
        return MetricValue(
            metricId = this[MetricValues.metricId],
            value = this[MetricValues.value],
            timestamp = this[MetricValues.timestamp].toEpochMilli(),
            dimensions = json.decodeFromString(this[MetricValues.dimensions]),
            metadata = json.decodeFromString(this[MetricValues.metadata])
        )
    }
}