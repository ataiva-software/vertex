package com.ataiva.eden.insight.repository.impl

import com.ataiva.eden.insight.model.ExecutionStatus
import com.ataiva.eden.insight.model.QueryExecution
import com.ataiva.eden.insight.repository.QueryExecutions
import com.ataiva.eden.insight.repository.QueryExecutionRepository
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
 * Implementation of QueryExecutionRepository using Exposed
 */
class QueryExecutionRepositoryImpl(
    private val database: Database,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : QueryExecutionRepository {

    override suspend fun findById(id: String): QueryExecution? = transaction(database) {
        QueryExecutions.select { QueryExecutions.id eq id }
            .singleOrNull()
            ?.toQueryExecution()
    }

    override suspend fun findAll(): List<QueryExecution> = transaction(database) {
        QueryExecutions.selectAll()
            .map { it.toQueryExecution() }
    }

    override suspend fun save(entity: QueryExecution): QueryExecution = transaction(database) {
        QueryExecutions.insert {
            it[id] = entity.id
            it[queryId] = entity.queryId
            it[executedBy] = entity.executedBy
            it[startTime] = Instant.ofEpochMilli(entity.startTime)
            it[endTime] = entity.endTime?.let { time -> Instant.ofEpochMilli(time) }
            it[status] = entity.status.name
            it[resultCount] = entity.resultCount
            it[executionTimeMs] = entity.executionTimeMs
            it[errorMessage] = entity.errorMessage
            it[parameters] = json.encodeToString(entity.parameters)
        }
        entity
    }

    override suspend fun update(entity: QueryExecution): Boolean = transaction(database) {
        val updatedRows = QueryExecutions.update({ QueryExecutions.id eq entity.id }) {
            it[queryId] = entity.queryId
            it[executedBy] = entity.executedBy
            it[startTime] = Instant.ofEpochMilli(entity.startTime)
            it[endTime] = entity.endTime?.let { time -> Instant.ofEpochMilli(time) }
            it[status] = entity.status.name
            it[resultCount] = entity.resultCount
            it[executionTimeMs] = entity.executionTimeMs
            it[errorMessage] = entity.errorMessage
            it[parameters] = json.encodeToString(entity.parameters)
        }
        updatedRows > 0
    }

    override suspend fun delete(id: String): Boolean = transaction(database) {
        val deletedRows = QueryExecutions.deleteWhere { QueryExecutions.id eq id }
        deletedRows > 0
    }

    override suspend fun count(): Long = transaction(database) {
        QueryExecutions.selectAll().count()
    }

    override suspend fun findByQueryId(queryId: String): List<QueryExecution> = transaction(database) {
        QueryExecutions.select { QueryExecutions.queryId eq queryId }
            .map { it.toQueryExecution() }
    }

    override suspend fun findByStatus(status: ExecutionStatus): List<QueryExecution> = transaction(database) {
        QueryExecutions.select { QueryExecutions.status eq status.name }
            .map { it.toQueryExecution() }
    }

    override suspend fun findByExecutedBy(executedBy: String): List<QueryExecution> = transaction(database) {
        QueryExecutions.select { QueryExecutions.executedBy eq executedBy }
            .map { it.toQueryExecution() }
    }

    override suspend fun findByTimeRange(startTime: Long, endTime: Long): List<QueryExecution> = transaction(database) {
        val startInstant = Instant.ofEpochMilli(startTime)
        val endInstant = Instant.ofEpochMilli(endTime)
        
        QueryExecutions.select { 
            (QueryExecutions.startTime greaterEq startInstant) and 
            (QueryExecutions.startTime lessEq endInstant) 
        }.map { it.toQueryExecution() }
    }

    override suspend fun findByQueryIdAndTimeRange(queryId: String, startTime: Long, endTime: Long): List<QueryExecution> = transaction(database) {
        val startInstant = Instant.ofEpochMilli(startTime)
        val endInstant = Instant.ofEpochMilli(endTime)
        
        QueryExecutions.select { 
            (QueryExecutions.queryId eq queryId) and
            (QueryExecutions.startTime greaterEq startInstant) and 
            (QueryExecutions.startTime lessEq endInstant) 
        }.map { it.toQueryExecution() }
    }

    override suspend fun findLatestByQueryId(queryId: String): QueryExecution? = transaction(database) {
        QueryExecutions.select { QueryExecutions.queryId eq queryId }
            .orderBy(QueryExecutions.startTime, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toQueryExecution()
    }

    override suspend fun updateStatus(id: String, status: ExecutionStatus): Boolean = transaction(database) {
        val updatedRows = QueryExecutions.update({ QueryExecutions.id eq id }) {
            it[QueryExecutions.status] = status.name
            if (status == ExecutionStatus.COMPLETED || status == ExecutionStatus.FAILED || status == ExecutionStatus.CANCELLED) {
                it[endTime] = Instant.now()
            }
        }
        updatedRows > 0
    }

    /**
     * Extension function to convert ResultRow to QueryExecution
     */
    private fun ResultRow.toQueryExecution(): QueryExecution {
        return QueryExecution(
            id = this[QueryExecutions.id],
            queryId = this[QueryExecutions.queryId],
            executedBy = this[QueryExecutions.executedBy],
            startTime = this[QueryExecutions.startTime].toEpochMilli(),
            endTime = this[QueryExecutions.endTime]?.toEpochMilli(),
            status = ExecutionStatus.valueOf(this[QueryExecutions.status]),
            resultCount = this[QueryExecutions.resultCount],
            executionTimeMs = this[QueryExecutions.executionTimeMs],
            errorMessage = this[QueryExecutions.errorMessage],
            parameters = json.decodeFromString(this[QueryExecutions.parameters])
        )
    }
}