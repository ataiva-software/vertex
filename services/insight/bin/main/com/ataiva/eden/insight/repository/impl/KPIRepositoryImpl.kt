package com.ataiva.eden.insight.repository.impl

import com.ataiva.eden.insight.model.KPI
import com.ataiva.eden.insight.model.KPIDataPoint
import com.ataiva.eden.insight.model.TrendDirection
import com.ataiva.eden.insight.repository.KPIs
import com.ataiva.eden.insight.repository.KPIRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Implementation of KPIRepository using Exposed
 */
class KPIRepositoryImpl(
    private val database: Database,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : KPIRepository {

    override suspend fun findById(id: String): KPI? = transaction(database) {
        KPIs.select { KPIs.id eq id }
            .singleOrNull()
            ?.toKPI()
    }

    override suspend fun findAll(): List<KPI> = transaction(database) {
        KPIs.selectAll()
            .map { it.toKPI() }
    }

    override suspend fun save(entity: KPI): KPI = transaction(database) {
        KPIs.insert {
            it[id] = entity.id
            it[name] = entity.name
            it[description] = entity.description
            it[targetValue] = entity.targetValue
            it[currentValue] = entity.currentValue
            it[unit] = entity.unit
            it[trend] = entity.trend.name
            it[category] = entity.category
            it[lastUpdated] = Instant.ofEpochMilli(entity.lastUpdated)
            it[historicalData] = json.encodeToString(entity.historicalData)
        }
        entity
    }

    override suspend fun update(entity: KPI): Boolean = transaction(database) {
        val updatedRows = KPIs.update({ KPIs.id eq entity.id }) {
            it[name] = entity.name
            it[description] = entity.description
            it[targetValue] = entity.targetValue
            it[currentValue] = entity.currentValue
            it[unit] = entity.unit
            it[trend] = entity.trend.name
            it[category] = entity.category
            it[lastUpdated] = Instant.ofEpochMilli(entity.lastUpdated)
            it[historicalData] = json.encodeToString(entity.historicalData)
        }
        updatedRows > 0
    }

    override suspend fun delete(id: String): Boolean = transaction(database) {
        val deletedRows = KPIs.deleteWhere { KPIs.id eq id }
        deletedRows > 0
    }

    override suspend fun count(): Long = transaction(database) {
        KPIs.selectAll().count()
    }

    override suspend fun findByName(name: String): KPI? = transaction(database) {
        KPIs.select { KPIs.name eq name }
            .singleOrNull()
            ?.toKPI()
    }

    override suspend fun findByCategory(category: String): List<KPI> = transaction(database) {
        KPIs.select { KPIs.category eq category }
            .map { it.toKPI() }
    }

    override suspend fun updateValue(id: String, currentValue: Double, trend: TrendDirection): Boolean = transaction(database) {
        val updatedRows = KPIs.update({ KPIs.id eq id }) {
            it[KPIs.currentValue] = currentValue
            it[KPIs.trend] = trend.name
            it[lastUpdated] = Instant.now()
        }
        updatedRows > 0
    }

    override suspend fun updateHistoricalData(id: String, dataPoint: KPIDataPoint): Boolean = transaction(database) {
        // Get current KPI
        val kpi = KPIs.select { KPIs.id eq id }
            .singleOrNull()
            ?.toKPI() ?: return@transaction false
        
        // Update historical data
        val updatedHistoricalData = kpi.historicalData + dataPoint
        
        // Keep only the last 100 data points
        val trimmedHistoricalData = if (updatedHistoricalData.size > 100) {
            updatedHistoricalData.takeLast(100)
        } else {
            updatedHistoricalData
        }
        
        // Update the KPI
        val updatedRows = KPIs.update({ KPIs.id eq id }) {
            it[historicalData] = json.encodeToString(trimmedHistoricalData)
            it[lastUpdated] = Instant.now()
        }
        
        updatedRows > 0
    }

    override suspend fun search(namePattern: String): List<KPI> = transaction(database) {
        KPIs.select { KPIs.name like "%$namePattern%" }
            .map { it.toKPI() }
    }

    /**
     * Extension function to convert ResultRow to KPI
     */
    private fun ResultRow.toKPI(): KPI {
        return KPI(
            id = this[KPIs.id],
            name = this[KPIs.name],
            description = this[KPIs.description],
            targetValue = this[KPIs.targetValue],
            currentValue = this[KPIs.currentValue],
            unit = this[KPIs.unit],
            trend = TrendDirection.valueOf(this[KPIs.trend]),
            category = this[KPIs.category],
            lastUpdated = this[KPIs.lastUpdated].toEpochMilli(),
            historicalData = json.decodeFromString(this[KPIs.historicalData])
        )
    }
}