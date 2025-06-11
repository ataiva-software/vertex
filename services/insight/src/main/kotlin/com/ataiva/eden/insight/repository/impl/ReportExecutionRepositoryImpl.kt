package com.ataiva.eden.insight.repository.impl

import com.ataiva.eden.insight.model.ExecutionStatus
import com.ataiva.eden.insight.model.ReportExecution
import com.ataiva.eden.insight.repository.ReportExecutions
import com.ataiva.eden.insight.repository.ReportExecutionRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Implementation of ReportExecutionRepository using Exposed
 */
class ReportExecutionRepositoryImpl(
    private val database: Database,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ReportExecutionRepository {

    override suspend fun findById(id: String): ReportExecution? = transaction(database) {
        ReportExecutions.select { ReportExecutions.id eq id }
            .singleOrNull()
            ?.toReportExecution()
    }

    override suspend fun findAll(): List<ReportExecution> = transaction(database) {
        ReportExecutions.selectAll()
            .map { it.toReportExecution() }
    }

    override suspend fun save(entity: ReportExecution): ReportExecution = transaction(database) {
        ReportExecutions.insert {
            it[id] = entity.id
            it[reportId] = entity.reportId
            it[executedBy] = entity.executedBy
            it[startTime] = Instant.ofEpochMilli(entity.startTime)
            it[endTime] = entity.endTime?.let { time -> Instant.ofEpochMilli(time) }
            it[status] = entity.status.name
            it[outputPath] = entity.outputPath
            it[fileSize] = entity.fileSize
            it[errorMessage] = entity.errorMessage
            it[parameters] = json.encodeToString(entity.parameters)
        }
        entity
    }

    override suspend fun update(entity: ReportExecution): Boolean = transaction(database) {
        val updatedRows = ReportExecutions.update({ ReportExecutions.id eq entity.id }) {
            it[reportId] = entity.reportId
            it[executedBy] = entity.executedBy
            it[startTime] = Instant.ofEpochMilli(entity.startTime)
            it[endTime] = entity.endTime?.let { time -> Instant.ofEpochMilli(time) }
            it[status] = entity.status.name
            it[outputPath] = entity.outputPath
            it[fileSize] = entity.fileSize
            it[errorMessage] = entity.errorMessage
            it[parameters] = json.encodeToString(entity.parameters)
        }
        updatedRows > 0
    }

    override suspend fun delete(id: String): Boolean = transaction(database) {
        val deletedRows = ReportExecutions.deleteWhere { ReportExecutions.id eq id }
        deletedRows > 0
    }

    override suspend fun count(): Long = transaction(database) {
        ReportExecutions.selectAll().count()
    }

    override suspend fun findByReportId(reportId: String): List<ReportExecution> = transaction(database) {
        ReportExecutions.select { ReportExecutions.reportId eq reportId }
            .map { it.toReportExecution() }
    }

    override suspend fun findByStatus(status: ExecutionStatus): List<ReportExecution> = transaction(database) {
        ReportExecutions.select { ReportExecutions.status eq status.name }
            .map { it.toReportExecution() }
    }

    override suspend fun findByExecutedBy(executedBy: String): List<ReportExecution> = transaction(database) {
        ReportExecutions.select { ReportExecutions.executedBy eq executedBy }
            .map { it.toReportExecution() }
    }

    override suspend fun findByTimeRange(startTime: Long, endTime: Long): List<ReportExecution> = transaction(database) {
        val startInstant = Instant.ofEpochMilli(startTime)
        val endInstant = Instant.ofEpochMilli(endTime)
        
        ReportExecutions.select { 
            (ReportExecutions.startTime greaterEq startInstant) and 
            (ReportExecutions.startTime lessEq endInstant) 
        }.map { it.toReportExecution() }
    }

    override suspend fun findByReportIdAndTimeRange(reportId: String, startTime: Long, endTime: Long): List<ReportExecution> = transaction(database) {
        val startInstant = Instant.ofEpochMilli(startTime)
        val endInstant = Instant.ofEpochMilli(endTime)
        
        ReportExecutions.select { 
            (ReportExecutions.reportId eq reportId) and
            (ReportExecutions.startTime greaterEq startInstant) and 
            (ReportExecutions.startTime lessEq endInstant) 
        }.map { it.toReportExecution() }
    }

    override suspend fun findLatestByReportId(reportId: String): ReportExecution? = transaction(database) {
        ReportExecutions.select { ReportExecutions.reportId eq reportId }
            .orderBy(ReportExecutions.startTime, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toReportExecution()
    }

    override suspend fun updateStatus(id: String, status: ExecutionStatus): Boolean = transaction(database) {
        val updatedRows = ReportExecutions.update({ ReportExecutions.id eq id }) {
            it[ReportExecutions.status] = status.name
            if (status == ExecutionStatus.COMPLETED || status == ExecutionStatus.FAILED || status == ExecutionStatus.CANCELLED) {
                it[endTime] = Instant.now()
            }
        }
        updatedRows > 0
    }

    override suspend fun updateOutput(id: String, outputPath: String, fileSize: Long): Boolean = transaction(database) {
        val updatedRows = ReportExecutions.update({ ReportExecutions.id eq id }) {
            it[ReportExecutions.outputPath] = outputPath
            it[ReportExecutions.fileSize] = fileSize
        }
        updatedRows > 0
    }

    override suspend fun updateError(id: String, errorMessage: String): Boolean = transaction(database) {
        val updatedRows = ReportExecutions.update({ ReportExecutions.id eq id }) {
            it[ReportExecutions.errorMessage] = errorMessage
            it[status] = ExecutionStatus.FAILED.name
            it[endTime] = Instant.now()
        }
        updatedRows > 0
    }

    /**
     * Extension function to convert ResultRow to ReportExecution
     */
    private fun ResultRow.toReportExecution(): ReportExecution {
        return ReportExecution(
            id = this[ReportExecutions.id],
            reportId = this[ReportExecutions.reportId],
            executedBy = this[ReportExecutions.executedBy],
            startTime = this[ReportExecutions.startTime].toEpochMilli(),
            endTime = this[ReportExecutions.endTime]?.toEpochMilli(),
            status = ExecutionStatus.valueOf(this[ReportExecutions.status]),
            outputPath = this[ReportExecutions.outputPath],
            fileSize = this[ReportExecutions.fileSize],
            errorMessage = this[ReportExecutions.errorMessage],
            parameters = json.decodeFromString(this[ReportExecutions.parameters])
        )
    }
}