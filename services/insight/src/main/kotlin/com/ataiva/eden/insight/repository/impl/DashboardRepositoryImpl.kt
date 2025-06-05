package com.ataiva.eden.insight.repository.impl

import com.ataiva.eden.insight.model.Dashboard
import com.ataiva.eden.insight.model.DashboardLayout
import com.ataiva.eden.insight.model.DashboardPermissions
import com.ataiva.eden.insight.model.DashboardWidget
import com.ataiva.eden.insight.repository.Dashboards
import com.ataiva.eden.insight.repository.DashboardRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Implementation of DashboardRepository using Exposed
 */
class DashboardRepositoryImpl(
    private val database: Database,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : DashboardRepository {

    override suspend fun findById(id: String): Dashboard? = transaction(database) {
        Dashboards.select { Dashboards.id eq id }
            .singleOrNull()
            ?.toDashboard()
    }

    override suspend fun findAll(): List<Dashboard> = transaction(database) {
        Dashboards.selectAll()
            .map { it.toDashboard() }
    }

    override suspend fun save(entity: Dashboard): Dashboard = transaction(database) {
        Dashboards.insert {
            it[id] = entity.id
            it[name] = entity.name
            it[description] = entity.description
            it[widgets] = json.encodeToString(entity.widgets)
            it[layout] = json.encodeToString(entity.layout)
            it[permissions] = json.encodeToString(entity.permissions)
            it[createdBy] = entity.createdBy
            it[createdAt] = Instant.ofEpochMilli(entity.createdAt)
            it[lastModified] = Instant.ofEpochMilli(entity.lastModified)
            it[isPublic] = entity.isPublic
            it[tags] = json.encodeToString(entity.tags)
        }
        entity
    }

    override suspend fun update(entity: Dashboard): Boolean = transaction(database) {
        val updatedRows = Dashboards.update({ Dashboards.id eq entity.id }) {
            it[name] = entity.name
            it[description] = entity.description
            it[widgets] = json.encodeToString(entity.widgets)
            it[layout] = json.encodeToString(entity.layout)
            it[permissions] = json.encodeToString(entity.permissions)
            it[lastModified] = Instant.ofEpochMilli(entity.lastModified)
            it[isPublic] = entity.isPublic
            it[tags] = json.encodeToString(entity.tags)
        }
        updatedRows > 0
    }

    override suspend fun delete(id: String): Boolean = transaction(database) {
        val deletedRows = Dashboards.deleteWhere { Dashboards.id eq id }
        deletedRows > 0
    }

    override suspend fun count(): Long = transaction(database) {
        Dashboards.selectAll().count()
    }

    override suspend fun findByName(name: String): Dashboard? = transaction(database) {
        Dashboards.select { Dashboards.name eq name }
            .singleOrNull()
            ?.toDashboard()
    }

    override suspend fun findByCreatedBy(createdBy: String): List<Dashboard> = transaction(database) {
        Dashboards.select { Dashboards.createdBy eq createdBy }
            .map { it.toDashboard() }
    }

    override suspend fun findPublic(): List<Dashboard> = transaction(database) {
        Dashboards.select { Dashboards.isPublic eq true }
            .map { it.toDashboard() }
    }

    override suspend fun findByTags(tags: List<String>): List<Dashboard> = transaction(database) {
        // This is a simplified implementation that checks if any of the tags match
        // A more sophisticated implementation would use a JSON contains operator
        Dashboards.selectAll()
            .map { it.toDashboard() }
            .filter { dashboard -> dashboard.tags.any { it in tags } }
    }

    override suspend fun findByCreatedByOrPublic(createdBy: String): List<Dashboard> = transaction(database) {
        Dashboards.select { 
            (Dashboards.createdBy eq createdBy) or (Dashboards.isPublic eq true)
        }.map { it.toDashboard() }
    }

    override suspend fun search(namePattern: String): List<Dashboard> = transaction(database) {
        Dashboards.select { Dashboards.name like "%$namePattern%" }
            .map { it.toDashboard() }
    }

    /**
     * Extension function to convert ResultRow to Dashboard
     */
    private fun ResultRow.toDashboard(): Dashboard {
        return Dashboard(
            id = this[Dashboards.id],
            name = this[Dashboards.name],
            description = this[Dashboards.description],
            widgets = json.decodeFromString<List<DashboardWidget>>(this[Dashboards.widgets]),
            layout = json.decodeFromString<DashboardLayout>(this[Dashboards.layout]),
            permissions = json.decodeFromString<DashboardPermissions>(this[Dashboards.permissions]),
            createdBy = this[Dashboards.createdBy],
            createdAt = this[Dashboards.createdAt].toEpochMilli(),
            lastModified = this[Dashboards.lastModified].toEpochMilli(),
            isPublic = this[Dashboards.isPublic],
            tags = json.decodeFromString(this[Dashboards.tags])
        )
    }
}