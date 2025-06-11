package com.ataiva.eden.insight.repository.impl

import com.ataiva.eden.insight.model.ReportTemplate
import com.ataiva.eden.insight.model.ParameterDefinition
import com.ataiva.eden.insight.model.ReportFormat
import com.ataiva.eden.insight.repository.ReportTemplates
import com.ataiva.eden.insight.repository.ReportTemplateRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Implementation of ReportTemplateRepository using Exposed
 */
class ReportTemplateRepositoryImpl(
    private val database: Database,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ReportTemplateRepository {

    override suspend fun findById(id: String): ReportTemplate? = transaction(database) {
        ReportTemplates.select { ReportTemplates.id eq id }
            .singleOrNull()
            ?.toReportTemplate()
    }

    override suspend fun findAll(): List<ReportTemplate> = transaction(database) {
        ReportTemplates.selectAll()
            .map { it.toReportTemplate() }
    }

    override suspend fun save(entity: ReportTemplate): ReportTemplate = transaction(database) {
        ReportTemplates.insert {
            it[id] = entity.id
            it[name] = entity.name
            it[description] = entity.description
            it[templateContent] = entity.templateContent
            it[requiredParameters] = json.encodeToString(entity.requiredParameters)
            it[supportedFormats] = json.encodeToString(entity.supportedFormats)
            it[category] = entity.category
            it[createdBy] = entity.createdBy
            it[createdAt] = Instant.ofEpochMilli(entity.createdAt)
            it[version] = entity.version
        }
        entity
    }

    override suspend fun update(entity: ReportTemplate): Boolean = transaction(database) {
        val updatedRows = ReportTemplates.update({ ReportTemplates.id eq entity.id }) {
            it[name] = entity.name
            it[description] = entity.description
            it[templateContent] = entity.templateContent
            it[requiredParameters] = json.encodeToString(entity.requiredParameters)
            it[supportedFormats] = json.encodeToString(entity.supportedFormats)
            it[category] = entity.category
            it[version] = entity.version
        }
        updatedRows > 0
    }

    override suspend fun delete(id: String): Boolean = transaction(database) {
        val deletedRows = ReportTemplates.deleteWhere { ReportTemplates.id eq id }
        deletedRows > 0
    }

    override suspend fun count(): Long = transaction(database) {
        ReportTemplates.selectAll().count()
    }

    override suspend fun findByName(name: String): ReportTemplate? = transaction(database) {
        ReportTemplates.select { ReportTemplates.name eq name }
            .singleOrNull()
            ?.toReportTemplate()
    }

    override suspend fun findByCategory(category: String): List<ReportTemplate> = transaction(database) {
        ReportTemplates.select { ReportTemplates.category eq category }
            .map { it.toReportTemplate() }
    }

    override suspend fun findByCreatedBy(createdBy: String): List<ReportTemplate> = transaction(database) {
        ReportTemplates.select { ReportTemplates.createdBy eq createdBy }
            .map { it.toReportTemplate() }
    }

    override suspend fun search(namePattern: String): List<ReportTemplate> = transaction(database) {
        ReportTemplates.select { ReportTemplates.name like "%$namePattern%" }
            .map { it.toReportTemplate() }
    }

    /**
     * Extension function to convert ResultRow to ReportTemplate
     */
    private fun ResultRow.toReportTemplate(): ReportTemplate {
        return ReportTemplate(
            id = this[ReportTemplates.id],
            name = this[ReportTemplates.name],
            description = this[ReportTemplates.description],
            templateContent = this[ReportTemplates.templateContent],
            requiredParameters = json.decodeFromString<List<ParameterDefinition>>(this[ReportTemplates.requiredParameters]),
            supportedFormats = json.decodeFromString<List<ReportFormat>>(this[ReportTemplates.supportedFormats]),
            category = this[ReportTemplates.category],
            createdBy = this[ReportTemplates.createdBy],
            createdAt = this[ReportTemplates.createdAt].toEpochMilli(),
            version = this[ReportTemplates.version]
        )
    }
}