package com.ataiva.eden.database.repositories

import com.ataiva.eden.database.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * PostgreSQL implementation of WorkflowRepository
 */
class PostgreSQLWorkflowRepository(
    private val database: DatabaseConnection
) : WorkflowRepository {

    override suspend fun findById(id: String): Workflow? {
        return database.queryOne(
            """
            SELECT id, name, description, definition, user_id, status, version, created_at, updated_at
            FROM workflows 
            WHERE id = ?
            """.trimIndent(),
            mapOf("id" to id)
        ) { row -> mapRowToWorkflow(row) }
    }

    override suspend fun findAll(): List<Workflow> {
        return database.query(
            """
            SELECT id, name, description, definition, user_id, status, version, created_at, updated_at
            FROM workflows 
            ORDER BY created_at DESC
            """.trimIndent()
        ) { row -> mapRowToWorkflow(row) }
    }

    override suspend fun findAll(offset: Int, limit: Int): Page<Workflow> {
        val workflows = database.query(
            """
            SELECT id, name, description, definition, user_id, status, version, created_at, updated_at
            FROM workflows 
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            mapOf("limit" to limit, "offset" to offset)
        ) { row -> mapRowToWorkflow(row) }

        val totalCount = count()
        val totalPages = ((totalCount + limit - 1) / limit).toInt()
        val currentPage = (offset / limit) + 1

        return Page(
            content = workflows,
            totalElements = totalCount,
            totalPages = totalPages,
            page = currentPage,
            size = limit,
            hasNext = currentPage < totalPages,
            hasPrevious = currentPage > 1
        )
    }

    override suspend fun save(entity: Workflow): Workflow {
        val now = Clock.System.now()
        val workflowToSave = entity.copy(updatedAt = now)

        val exists = existsById(entity.id)
        
        if (exists) {
            database.execute(
                """
                UPDATE workflows 
                SET name = ?, description = ?, definition = ?::jsonb, status = ?, version = ?, updated_at = ?
                WHERE id = ?
                """.trimIndent(),
                mapOf(
                    "name" to workflowToSave.name,
                    "description" to workflowToSave.description,
                    "definition" to Json.encodeToString(workflowToSave.definition),
                    "status" to workflowToSave.status,
                    "version" to workflowToSave.version,
                    "updated_at" to workflowToSave.updatedAt.toString(),
                    "id" to workflowToSave.id
                )
            )
        } else {
            database.execute(
                """
                INSERT INTO workflows (id, name, description, definition, user_id, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                """.trimIndent(),
                mapOf(
                    "id" to workflowToSave.id,
                    "name" to workflowToSave.name,
                    "description" to workflowToSave.description,
                    "definition" to Json.encodeToString(workflowToSave.definition),
                    "user_id" to workflowToSave.userId,
                    "status" to workflowToSave.status,
                    "version" to workflowToSave.version,
                    "created_at" to workflowToSave.createdAt.toString(),
                    "updated_at" to workflowToSave.updatedAt.toString()
                )
            )
        }

        return workflowToSave
    }

    override suspend fun saveAll(entities: List<Workflow>): List<Workflow> {
        return entities.map { save(it) }
    }

    override suspend fun deleteById(id: String): Boolean {
        val rowsAffected = database.execute(
            "DELETE FROM workflows WHERE id = ?",
            mapOf("id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun delete(entity: Workflow): Boolean {
        return deleteById(entity.id)
    }

    override suspend fun existsById(id: String): Boolean {
        return database.queryOne(
            "SELECT COUNT(*) as count FROM workflows WHERE id = ?",
            mapOf("id" to id)
        ) { row -> (row.getLong("count") ?: 0) > 0 } ?: false
    }

    override suspend fun count(): Long {
        return database.queryOne(
            "SELECT COUNT(*) as count FROM workflows"
        ) { row -> row.getLong("count") ?: 0 } ?: 0
    }

    // WorkflowRepository specific methods

    override suspend fun findByNameAndUser(name: String, userId: String): Workflow? {
        return database.queryOne(
            """
            SELECT id, name, description, definition, user_id, status, version, created_at, updated_at
            FROM workflows 
            WHERE name = ? AND user_id = ?
            ORDER BY version DESC
            LIMIT 1
            """.trimIndent(),
            mapOf("name" to name, "user_id" to userId)
        ) { row -> mapRowToWorkflow(row) }
    }

    override suspend fun findByUserId(userId: String): List<Workflow> {
        return database.query(
            """
            SELECT id, name, description, definition, user_id, status, version, created_at, updated_at
            FROM workflows 
            WHERE user_id = ?
            ORDER BY name, version DESC
            """.trimIndent(),
            mapOf("user_id" to userId)
        ) { row -> mapRowToWorkflow(row) }
    }

    override suspend fun findActiveByUserId(userId: String): List<Workflow> {
        return database.query(
            """
            SELECT id, name, description, definition, user_id, status, version, created_at, updated_at
            FROM workflows 
            WHERE user_id = ? AND status = 'active'
            ORDER BY name, version DESC
            """.trimIndent(),
            mapOf("user_id" to userId)
        ) { row -> mapRowToWorkflow(row) }
    }

    override suspend fun findByStatus(status: String): List<Workflow> {
        return database.query(
            """
            SELECT id, name, description, definition, user_id, status, version, created_at, updated_at
            FROM workflows 
            WHERE status = ?
            ORDER BY created_at DESC
            """.trimIndent(),
            mapOf("status" to status)
        ) { row -> mapRowToWorkflow(row) }
    }

    override suspend fun searchByName(userId: String, namePattern: String): List<Workflow> {
        return database.query(
            """
            SELECT id, name, description, definition, user_id, status, version, created_at, updated_at
            FROM workflows 
            WHERE user_id = ? AND name ILIKE ?
            ORDER BY name, version DESC
            """.trimIndent(),
            mapOf("user_id" to userId, "name_pattern" to "%$namePattern%")
        ) { row -> mapRowToWorkflow(row) }
    }

    override suspend fun updateStatus(id: String, status: String): Boolean {
        val rowsAffected = database.execute(
            "UPDATE workflows SET status = ?, updated_at = ? WHERE id = ?",
            mapOf("status" to status, "updated_at" to Clock.System.now().toString(), "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun updateDefinition(id: String, definition: Map<String, Any>): Boolean {
        val rowsAffected = database.execute(
            "UPDATE workflows SET definition = ?::jsonb, updated_at = ? WHERE id = ?",
            mapOf(
                "definition" to Json.encodeToString(definition),
                "updated_at" to Clock.System.now().toString(),
                "id" to id
            )
        )
        return rowsAffected > 0
    }

    override suspend fun getWorkflowStats(userId: String): WorkflowStats {
        val totalWorkflows = database.queryOne(
            "SELECT COUNT(*) as count FROM workflows WHERE user_id = ?",
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val activeWorkflows = database.queryOne(
            "SELECT COUNT(*) as count FROM workflows WHERE user_id = ? AND status = 'active'",
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val pausedWorkflows = database.queryOne(
            "SELECT COUNT(*) as count FROM workflows WHERE user_id = ? AND status = 'paused'",
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val archivedWorkflows = database.queryOne(
            "SELECT COUNT(*) as count FROM workflows WHERE user_id = ? AND status = 'archived'",
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val recentlyCreated = database.queryOne(
            """
            SELECT COUNT(*) as count 
            FROM workflows 
            WHERE user_id = ? AND created_at > NOW() - INTERVAL '7 days'
            """.trimIndent(),
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val recentlyUpdated = database.queryOne(
            """
            SELECT COUNT(*) as count 
            FROM workflows 
            WHERE user_id = ? AND updated_at > NOW() - INTERVAL '7 days' AND updated_at != created_at
            """.trimIndent(),
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        return WorkflowStats(
            totalWorkflows = totalWorkflows,
            activeWorkflows = activeWorkflows,
            pausedWorkflows = pausedWorkflows,
            archivedWorkflows = archivedWorkflows,
            recentlyCreated = recentlyCreated,
            recentlyUpdated = recentlyUpdated
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapRowToWorkflow(row: ResultRow): Workflow {
        val definitionJson = row.getString("definition") ?: "{}"
        val definition = try {
            Json.decodeFromString<Map<String, Any>>(definitionJson)
        } catch (e: Exception) {
            emptyMap()
        }

        return Workflow(
            id = row.getString("id") ?: "",
            name = row.getString("name") ?: "",
            description = row.getString("description"),
            definition = definition,
            userId = row.getString("user_id") ?: "",
            status = row.getString("status") ?: "active",
            version = row.getInt("version") ?: 1,
            createdAt = row.getTimestamp("created_at") ?: Clock.System.now(),
            updatedAt = row.getTimestamp("updated_at") ?: Clock.System.now()
        )
    }
}

/**
 * PostgreSQL implementation of WorkflowExecutionRepository
 */
class PostgreSQLWorkflowExecutionRepository(
    private val database: DatabaseConnection
) : WorkflowExecutionRepository {

    override suspend fun findById(id: String): WorkflowExecution? {
        return database.queryOne(
            """
            SELECT id, workflow_id, triggered_by, status, input_data, output_data, 
                   error_message, started_at, completed_at, duration_ms
            FROM workflow_executions 
            WHERE id = ?
            """.trimIndent(),
            mapOf("id" to id)
        ) { row -> mapRowToWorkflowExecution(row) }
    }

    override suspend fun findAll(): List<WorkflowExecution> {
        return database.query(
            """
            SELECT id, workflow_id, triggered_by, status, input_data, output_data, 
                   error_message, started_at, completed_at, duration_ms
            FROM workflow_executions 
            ORDER BY started_at DESC
            """.trimIndent()
        ) { row -> mapRowToWorkflowExecution(row) }
    }

    override suspend fun findAll(offset: Int, limit: Int): Page<WorkflowExecution> {
        val executions = database.query(
            """
            SELECT id, workflow_id, triggered_by, status, input_data, output_data, 
                   error_message, started_at, completed_at, duration_ms
            FROM workflow_executions 
            ORDER BY started_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            mapOf("limit" to limit, "offset" to offset)
        ) { row -> mapRowToWorkflowExecution(row) }

        val totalCount = count()
        val totalPages = ((totalCount + limit - 1) / limit).toInt()
        val currentPage = (offset / limit) + 1

        return Page(
            content = executions,
            totalElements = totalCount,
            totalPages = totalPages,
            page = currentPage,
            size = limit,
            hasNext = currentPage < totalPages,
            hasPrevious = currentPage > 1
        )
    }

    override suspend fun save(entity: WorkflowExecution): WorkflowExecution {
        val exists = existsById(entity.id)
        
        if (exists) {
            database.execute(
                """
                UPDATE workflow_executions 
                SET status = ?, input_data = ?::jsonb, output_data = ?::jsonb, error_message = ?, 
                    completed_at = ?, duration_ms = ?
                WHERE id = ?
                """.trimIndent(),
                mapOf(
                    "status" to entity.status,
                    "input_data" to entity.inputData?.let { Json.encodeToString(it) },
                    "output_data" to entity.outputData?.let { Json.encodeToString(it) },
                    "error_message" to entity.errorMessage,
                    "completed_at" to entity.completedAt?.toString(),
                    "duration_ms" to entity.durationMs,
                    "id" to entity.id
                )
            )
        } else {
            database.execute(
                """
                INSERT INTO workflow_executions (id, workflow_id, triggered_by, status, input_data, 
                                               output_data, error_message, started_at, completed_at, duration_ms)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                """.trimIndent(),
                mapOf(
                    "id" to entity.id,
                    "workflow_id" to entity.workflowId,
                    "triggered_by" to entity.triggeredBy,
                    "status" to entity.status,
                    "input_data" to entity.inputData?.let { Json.encodeToString(it) },
                    "output_data" to entity.outputData?.let { Json.encodeToString(it) },
                    "error_message" to entity.errorMessage,
                    "started_at" to entity.startedAt.toString(),
                    "completed_at" to entity.completedAt?.toString(),
                    "duration_ms" to entity.durationMs
                )
            )
        }

        return entity
    }

    override suspend fun saveAll(entities: List<WorkflowExecution>): List<WorkflowExecution> {
        return entities.map { save(it) }
    }

    override suspend fun deleteById(id: String): Boolean {
        val rowsAffected = database.execute(
            "DELETE FROM workflow_executions WHERE id = ?",
            mapOf("id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun delete(entity: WorkflowExecution): Boolean {
        return deleteById(entity.id)
    }

    override suspend fun existsById(id: String): Boolean {
        return database.queryOne(
            "SELECT COUNT(*) as count FROM workflow_executions WHERE id = ?",
            mapOf("id" to id)
        ) { row -> (row.getLong("count") ?: 0) > 0 } ?: false
    }

    override suspend fun count(): Long {
        return database.queryOne(
            "SELECT COUNT(*) as count FROM workflow_executions"
        ) { row -> row.getLong("count") ?: 0 } ?: 0
    }

    // WorkflowExecutionRepository specific methods

    override suspend fun findByWorkflowId(workflowId: String): List<WorkflowExecution> {
        return database.query(
            """
            SELECT id, workflow_id, triggered_by, status, input_data, output_data, 
                   error_message, started_at, completed_at, duration_ms
            FROM workflow_executions 
            WHERE workflow_id = ?
            ORDER BY started_at DESC
            """.trimIndent(),
            mapOf("workflow_id" to workflowId)
        ) { row -> mapRowToWorkflowExecution(row) }
    }

    override suspend fun findByStatus(status: String): List<WorkflowExecution> {
        return database.query(
            """
            SELECT id, workflow_id, triggered_by, status, input_data, output_data, 
                   error_message, started_at, completed_at, duration_ms
            FROM workflow_executions 
            WHERE status = ?
            ORDER BY started_at DESC
            """.trimIndent(),
            mapOf("status" to status)
        ) { row -> mapRowToWorkflowExecution(row) }
    }

    override suspend fun findByTriggeredBy(userId: String): List<WorkflowExecution> {
        return database.query(
            """
            SELECT id, workflow_id, triggered_by, status, input_data, output_data, 
                   error_message, started_at, completed_at, duration_ms
            FROM workflow_executions 
            WHERE triggered_by = ?
            ORDER BY started_at DESC
            """.trimIndent(),
            mapOf("triggered_by" to userId)
        ) { row -> mapRowToWorkflowExecution(row) }
    }

    override suspend fun findRecent(limit: Int): List<WorkflowExecution> {
        return database.query(
            """
            SELECT id, workflow_id, triggered_by, status, input_data, output_data, 
                   error_message, started_at, completed_at, duration_ms
            FROM workflow_executions 
            ORDER BY started_at DESC
            LIMIT ?
            """.trimIndent(),
            mapOf("limit" to limit)
        ) { row -> mapRowToWorkflowExecution(row) }
    }

    override suspend fun findRunning(): List<WorkflowExecution> {
        return database.query(
            """
            SELECT id, workflow_id, triggered_by, status, input_data, output_data, 
                   error_message, started_at, completed_at, duration_ms
            FROM workflow_executions 
            WHERE status = 'running'
            ORDER BY started_at ASC
            """.trimIndent()
        ) { row -> mapRowToWorkflowExecution(row) }
    }

    override suspend fun updateStatus(id: String, status: String, completedAt: Instant?): Boolean {
        val rowsAffected = database.execute(
            "UPDATE workflow_executions SET status = ?, completed_at = ? WHERE id = ?",
            mapOf("status" to status, "completed_at" to completedAt?.toString(), "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun updateOutput(id: String, outputData: Map<String, Any>): Boolean {
        val rowsAffected = database.execute(
            "UPDATE workflow_executions SET output_data = ?::jsonb WHERE id = ?",
            mapOf("output_data" to Json.encodeToString(outputData), "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun updateError(id: String, errorMessage: String, completedAt: Instant): Boolean {
        val rowsAffected = database.execute(
            "UPDATE workflow_executions SET error_message = ?, status = 'failed', completed_at = ? WHERE id = ?",
            mapOf("error_message" to errorMessage, "completed_at" to completedAt.toString(), "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun getExecutionStats(workflowId: String?): ExecutionStats {
        val whereClause = if (workflowId != null) "WHERE workflow_id = ?" else ""
        val params = if (workflowId != null) mapOf("workflow_id" to workflowId) else emptyMap()

        val totalExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM workflow_executions $whereClause",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val completedExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM workflow_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} status = 'completed'",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val failedExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM workflow_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} status = 'failed'",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val runningExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM workflow_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} status = 'running'",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val averageDurationMs = database.queryOne(
            "SELECT AVG(duration_ms) as avg_duration FROM workflow_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} duration_ms IS NOT NULL",
            params
        ) { row -> row.getDouble("avg_duration") }

        val successRate = if (totalExecutions > 0) {
            completedExecutions.toDouble() / totalExecutions.toDouble()
        } else {
            0.0
        }

        return ExecutionStats(
            totalExecutions = totalExecutions,
            completedExecutions = completedExecutions,
            failedExecutions = failedExecutions,
            runningExecutions = runningExecutions,
            averageDurationMs = averageDurationMs,
            successRate = successRate
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapRowToWorkflowExecution(row: ResultRow): WorkflowExecution {
        val inputDataJson = row.getString("input_data")
        val inputData = inputDataJson?.let {
            try {
                Json.decodeFromString<Map<String, Any>>(it)
            } catch (e: Exception) {
                null
            }
        }

        val outputDataJson = row.getString("output_data")
        val outputData = outputDataJson?.let {
            try {
                Json.decodeFromString<Map<String, Any>>(it)
            } catch (e: Exception) {
                null
            }
        }

        return WorkflowExecution(
            id = row.getString("id") ?: "",
            workflowId = row.getString("workflow_id") ?: "",
            triggeredBy = row.getString("triggered_by"),
            status = row.getString("status") ?: "pending",
            inputData = inputData,
            outputData = outputData,
            errorMessage = row.getString("error_message"),
            startedAt = row.getTimestamp("started_at") ?: Clock.System.now(),
            completedAt = row.getTimestamp("completed_at"),
            durationMs = row.getInt("duration_ms")
        )
    }
}