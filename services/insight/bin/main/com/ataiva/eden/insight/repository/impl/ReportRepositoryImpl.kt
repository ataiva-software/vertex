package com.ataiva.eden.insight.repository.impl

import com.ataiva.eden.insight.model.Report
import com.ataiva.eden.insight.model.ReportFormat
import com.ataiva.eden.insight.model.ReportSchedule
import com.ataiva.eden.insight.repository.Reports
import com.ataiva.eden.insight.repository.ReportRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Implementation of ReportRepository using Exposed
 */
class ReportRepositoryImpl(
    private val database: Database,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ReportRepository {

    override suspend fun findById(id: String): Report? = transaction(database) {
        Reports.select { Reports.id eq id }
            .singleOrNull()
            ?.toReport()
    }

    override suspend fun findAll(): List<Report> = transaction(database) {
        Reports.selectAll()
            .map { it.toReport() }
    }

    override suspend fun save(entity: Report): Report = transaction(database) {
        Reports.insert {
            it[id] = entity.id
            it[name] = entity.name
            it[description] = entity.description
            it[templateId] = entity.templateId
            it[parameters] = json.encodeToString(entity.parameters)
            it[schedule] = entity.schedule?.let { schedule -> json.encodeToString(schedule) }
            it[recipients] = json.encodeToString(entity.recipients)
            it[format] = entity.format.name
            it[createdBy] = entity.createdBy
            it[createdAt] = Instant.ofEpochMilli(entity.createdAt)
            it[lastGenerated] = entity.lastGenerated?.let { timestamp -> Instant.ofEpochMilli(timestamp) }
            it[isActive] = entity.isActive
        }
        entity
    }

    override suspend fun update(entity: Report): Boolean = transaction(database) {
        val updatedRows = Reports.update({ Reports.id eq entity.id }) {
            it[name] = entity.name
            it[description] = entity.description
            it[templateId] = entity.templateId
            it[parameters] = json.encodeToString(entity.parameters)
            it[schedule] = entity.schedule?.let { schedule -> json.encodeToString(schedule) }
            it[recipients] = json.encodeToString(entity.recipients)
            it[format] = entity.format.name
            it[lastGenerated] = entity.lastGenerated?.let { timestamp -> Instant.ofEpochMilli(timestamp) }
            it[isActive] = entity.isActive
        }
        updatedRows > 0
    }

    override suspend fun delete(id: String): Boolean = transaction(database) {
        val deletedRows = Reports.deleteWhere { Reports.id eq id }
        deletedRows > 0
    }

    override suspend fun count(): Long = transaction(database) {
        Reports.selectAll().count()
    }

    override suspend fun findByName(name: String): Report? = transaction(database) {
        Reports.select { Reports.name eq name }
            .singleOrNull()
            ?.toReport()
    }

    override suspend fun findByCreatedBy(createdBy: String): List<Report> = transaction(database) {
        Reports.select { Reports.createdBy eq createdBy }
            .map { it.toReport() }
    }

    override suspend fun findByTemplateId(templateId: String): List<Report> = transaction(database) {
        Reports.select { Reports.templateId eq templateId }
            .map { it.toReport() }
    }

    override suspend fun findActive(): List<Report> = transaction(database) {
        Reports.select { Reports.isActive eq true }
            .map { it.toReport() }
    }

    override suspend fun findByCreatedByAndActive(createdBy: String, isActive: Boolean): List<Report> = transaction(database) {
        Reports.select { 
            (Reports.createdBy eq createdBy) and (Reports.isActive eq isActive)
        }.map { it.toReport() }
    }

    override suspend fun findScheduled(): List<Report> = transaction(database) {
        // Find reports with non-null schedule
        Reports.select { Reports.schedule.isNotNull() and (Reports.isActive eq true) }
            .map { it.toReport() }
            .filter { it.schedule?.enabled == true } // Additional filter on the schedule enabled flag
    }

    override suspend fun findScheduledBefore(time: Long): List<Report> = transaction(database) {
        // This is a simplified implementation
        // In a real implementation, we would use a JSON operator to check the nextExecution field
        findScheduled().filter { report ->
            report.schedule?.nextExecution?.let { it <= time } ?: false
        }
    }

    override suspend fun updateStatus(id: String, isActive: Boolean): Boolean = transaction(database) {
        val updatedRows = Reports.update({ Reports.id eq id }) {
            it[Reports.isActive] = isActive
        }
        updatedRows > 0
    }

    override suspend fun updateLastGenerated(id: String, timestamp: Long): Boolean = transaction(database) {
        val updatedRows = Reports.update({ Reports.id eq id }) {
            it[lastGenerated] = Instant.ofEpochMilli(timestamp)
        }
        updatedRows > 0
    }

    override suspend fun search(namePattern: String): List<Report> = transaction(database) {
        Reports.select { Reports.name like "%$namePattern%" }
            .map { it.toReport() }
    }

    /**
     * Extension function to convert ResultRow to Report
     */
    private fun ResultRow.toReport(): Report {
        return Report(
            id = this[Reports.id],
            name = this[Reports.name],
            description = this[Reports.description],
            templateId = this[Reports.templateId],
            parameters = json.decodeFromString(this[Reports.parameters]),
            schedule = this[Reports.schedule]?.let { json.decodeFromString<ReportSchedule>(it) },
            recipients = json.decodeFromString(this[Reports.recipients]),
            format = ReportFormat.valueOf(this[Reports.format]),
            createdBy = this[Reports.createdBy],
            createdAt = this[Reports.createdAt].toEpochMilli(),
            lastGenerated = this[Reports.lastGenerated]?.toEpochMilli(),
            isActive = this[Reports.isActive]
        )
    }
}