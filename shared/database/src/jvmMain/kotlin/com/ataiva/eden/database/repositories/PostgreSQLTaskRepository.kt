package com.ataiva.eden.database.repositories

import com.ataiva.eden.database.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * PostgreSQL implementation of TaskRepository
 */
class PostgreSQLTaskRepository(
    private val database: DatabaseConnection
) : TaskRepository {

    override suspend fun findById(id: String): Task? {
        return database.queryOne(
            """
            SELECT id, name, description, task_type, configuration, schedule_cron, 
                   user_id, is_active, created_at, updated_at
            FROM tasks 
            WHERE id = ?
            """.trimIndent(),
            mapOf("id" to id)
        ) { row -> mapRowToTask(row) }
    }

    override suspend fun findAll(): List<Task> {
        return database.query(
            """
            SELECT id, name, description, task_type, configuration, schedule_cron, 
                   user_id, is_active, created_at, updated_at
            FROM tasks 
            ORDER BY created_at DESC
            """.trimIndent()
        ) { row -> mapRowToTask(row) }
    }

    override suspend fun findAll(offset: Int, limit: Int): Page<Task> {
        val tasks = database.query(
            """
            SELECT id, name, description, task_type, configuration, schedule_cron, 
                   user_id, is_active, created_at, updated_at
            FROM tasks 
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            mapOf("limit" to limit, "offset" to offset)
        ) { row -> mapRowToTask(row) }

        val totalCount = count()
        val totalPages = ((totalCount + limit - 1) / limit).toInt()
        val currentPage = (offset / limit) + 1

        return Page(
            content = tasks,
            totalElements = totalCount,
            totalPages = totalPages,
            page = currentPage,
            size = limit,
            hasNext = currentPage < totalPages,
            hasPrevious = currentPage > 1
        )
    }

    override suspend fun save(entity: Task): Task {
        val now = Clock.System.now()
        val taskToSave = entity.copy(updatedAt = now)

        val exists = existsById(entity.id)
        
        if (exists) {
            database.execute(
                """
                UPDATE tasks 
                SET name = ?, description = ?, task_type = ?, configuration = ?::jsonb, 
                    schedule_cron = ?, is_active = ?, updated_at = ?
                WHERE id = ?
                """.trimIndent(),
                mapOf(
                    "name" to taskToSave.name,
                    "description" to taskToSave.description,
                    "task_type" to taskToSave.taskType,
                    "configuration" to Json.encodeToString(taskToSave.configuration),
                    "schedule_cron" to taskToSave.scheduleCron,
                    "is_active" to taskToSave.isActive,
                    "updated_at" to taskToSave.updatedAt.toString(),
                    "id" to taskToSave.id
                )
            )
        } else {
            database.execute(
                """
                INSERT INTO tasks (id, name, description, task_type, configuration, schedule_cron, 
                                 user_id, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                """.trimIndent(),
                mapOf(
                    "id" to taskToSave.id,
                    "name" to taskToSave.name,
                    "description" to taskToSave.description,
                    "task_type" to taskToSave.taskType,
                    "configuration" to Json.encodeToString(taskToSave.configuration),
                    "schedule_cron" to taskToSave.scheduleCron,
                    "user_id" to taskToSave.userId,
                    "is_active" to taskToSave.isActive,
                    "created_at" to taskToSave.createdAt.toString(),
                    "updated_at" to taskToSave.updatedAt.toString()
                )
            )
        }

        return taskToSave
    }

    override suspend fun saveAll(entities: List<Task>): List<Task> {
        return entities.map { save(it) }
    }

    override suspend fun deleteById(id: String): Boolean {
        val rowsAffected = database.execute(
            "UPDATE tasks SET is_active = false WHERE id = ?",
            mapOf("id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun delete(entity: Task): Boolean {
        return deleteById(entity.id)
    }

    override suspend fun existsById(id: String): Boolean {
        return database.queryOne(
            "SELECT COUNT(*) as count FROM tasks WHERE id = ?",
            mapOf("id" to id)
        ) { row -> (row.getLong("count") ?: 0) > 0 } ?: false
    }

    override suspend fun count(): Long {
        return database.queryOne(
            "SELECT COUNT(*) as count FROM tasks WHERE is_active = true"
        ) { row -> row.getLong("count") ?: 0 } ?: 0
    }

    // TaskRepository specific methods

    override suspend fun findByNameAndUser(name: String, userId: String): Task? {
        return database.queryOne(
            """
            SELECT id, name, description, task_type, configuration, schedule_cron, 
                   user_id, is_active, created_at, updated_at
            FROM tasks 
            WHERE name = ? AND user_id = ? AND is_active = true
            """.trimIndent(),
            mapOf("name" to name, "user_id" to userId)
        ) { row -> mapRowToTask(row) }
    }

    override suspend fun findByUserId(userId: String): List<Task> {
        return database.query(
            """
            SELECT id, name, description, task_type, configuration, schedule_cron, 
                   user_id, is_active, created_at, updated_at
            FROM tasks 
            WHERE user_id = ? AND is_active = true
            ORDER BY name
            """.trimIndent(),
            mapOf("user_id" to userId)
        ) { row -> mapRowToTask(row) }
    }

    override suspend fun findActiveByUserId(userId: String): List<Task> {
        return findByUserId(userId) // Already filtered by is_active = true
    }

    override suspend fun findByType(taskType: String): List<Task> {
        return database.query(
            """
            SELECT id, name, description, task_type, configuration, schedule_cron, 
                   user_id, is_active, created_at, updated_at
            FROM tasks 
            WHERE task_type = ? AND is_active = true
            ORDER BY created_at DESC
            """.trimIndent(),
            mapOf("task_type" to taskType)
        ) { row -> mapRowToTask(row) }
    }

    override suspend fun findByTypeAndUser(taskType: String, userId: String): List<Task> {
        return database.query(
            """
            SELECT id, name, description, task_type, configuration, schedule_cron, 
                   user_id, is_active, created_at, updated_at
            FROM tasks 
            WHERE task_type = ? AND user_id = ? AND is_active = true
            ORDER BY name
            """.trimIndent(),
            mapOf("task_type" to taskType, "user_id" to userId)
        ) { row -> mapRowToTask(row) }
    }

    override suspend fun findScheduledTasks(): List<Task> {
        return database.query(
            """
            SELECT id, name, description, task_type, configuration, schedule_cron, 
                   user_id, is_active, created_at, updated_at
            FROM tasks 
            WHERE schedule_cron IS NOT NULL AND is_active = true
            ORDER BY name
            """.trimIndent()
        ) { row -> mapRowToTask(row) }
    }

    override suspend fun findScheduledTasksByUser(userId: String): List<Task> {
        return database.query(
            """
            SELECT id, name, description, task_type, configuration, schedule_cron, 
                   user_id, is_active, created_at, updated_at
            FROM tasks 
            WHERE user_id = ? AND schedule_cron IS NOT NULL AND is_active = true
            ORDER BY name
            """.trimIndent(),
            mapOf("user_id" to userId)
        ) { row -> mapRowToTask(row) }
    }

    override suspend fun searchByName(userId: String, namePattern: String): List<Task> {
        return database.query(
            """
            SELECT id, name, description, task_type, configuration, schedule_cron, 
                   user_id, is_active, created_at, updated_at
            FROM tasks 
            WHERE user_id = ? AND name ILIKE ? AND is_active = true
            ORDER BY name
            """.trimIndent(),
            mapOf("user_id" to userId, "name_pattern" to "%$namePattern%")
        ) { row -> mapRowToTask(row) }
    }

    override suspend fun updateStatus(id: String, isActive: Boolean): Boolean {
        val rowsAffected = database.execute(
            "UPDATE tasks SET is_active = ?, updated_at = ? WHERE id = ?",
            mapOf("is_active" to isActive, "updated_at" to Clock.System.now().toString(), "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun updateConfiguration(id: String, configuration: Map<String, Any>): Boolean {
        val rowsAffected = database.execute(
            "UPDATE tasks SET configuration = ?::jsonb, updated_at = ? WHERE id = ?",
            mapOf(
                "configuration" to Json.encodeToString(configuration),
                "updated_at" to Clock.System.now().toString(),
                "id" to id
            )
        )
        return rowsAffected > 0
    }

    override suspend fun updateSchedule(id: String, scheduleCron: String?): Boolean {
        val rowsAffected = database.execute(
            "UPDATE tasks SET schedule_cron = ?, updated_at = ? WHERE id = ?",
            mapOf(
                "schedule_cron" to scheduleCron,
                "updated_at" to Clock.System.now().toString(),
                "id" to id
            )
        )
        return rowsAffected > 0
    }

    override suspend fun getTaskStats(userId: String): TaskStats {
        val totalTasks = database.queryOne(
            "SELECT COUNT(*) as count FROM tasks WHERE user_id = ?",
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val activeTasks = database.queryOne(
            "SELECT COUNT(*) as count FROM tasks WHERE user_id = ? AND is_active = true",
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val inactiveTasks = totalTasks - activeTasks

        val scheduledTasks = database.queryOne(
            "SELECT COUNT(*) as count FROM tasks WHERE user_id = ? AND schedule_cron IS NOT NULL AND is_active = true",
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val tasksByType = database.query(
            """
            SELECT task_type, COUNT(*) as count 
            FROM tasks 
            WHERE user_id = ? AND is_active = true 
            GROUP BY task_type
            """.trimIndent(),
            mapOf("user_id" to userId)
        ) { row ->
            (row.getString("task_type") ?: "unknown") to (row.getLong("count") ?: 0)
        }.toMap()

        val recentlyCreated = database.queryOne(
            """
            SELECT COUNT(*) as count 
            FROM tasks 
            WHERE user_id = ? AND created_at > NOW() - INTERVAL '7 days'
            """.trimIndent(),
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val recentlyUpdated = database.queryOne(
            """
            SELECT COUNT(*) as count 
            FROM tasks 
            WHERE user_id = ? AND updated_at > NOW() - INTERVAL '7 days' AND updated_at != created_at
            """.trimIndent(),
            mapOf("user_id" to userId)
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        return TaskStats(
            totalTasks = totalTasks,
            activeTasks = activeTasks,
            inactiveTasks = inactiveTasks,
            scheduledTasks = scheduledTasks,
            tasksByType = tasksByType,
            recentlyCreated = recentlyCreated,
            recentlyUpdated = recentlyUpdated
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapRowToTask(row: ResultRow): Task {
        val configurationJson = row.getString("configuration") ?: "{}"
        val configuration = try {
            Json.decodeFromString<Map<String, Any>>(configurationJson)
        } catch (e: Exception) {
            emptyMap()
        }

        return Task(
            id = row.getString("id") ?: "",
            name = row.getString("name") ?: "",
            description = row.getString("description"),
            taskType = row.getString("task_type") ?: "",
            configuration = configuration,
            scheduleCron = row.getString("schedule_cron"),
            userId = row.getString("user_id") ?: "",
            isActive = row.getBoolean("is_active") ?: true,
            createdAt = row.getTimestamp("created_at") ?: Clock.System.now(),
            updatedAt = row.getTimestamp("updated_at") ?: Clock.System.now()
        )
    }
}

/**
 * PostgreSQL implementation of TaskExecutionRepository
 */
class PostgreSQLTaskExecutionRepository(
    private val database: DatabaseConnection
) : TaskExecutionRepository {

    override suspend fun findById(id: String): TaskExecution? {
        return database.queryOne(
            """
            SELECT id, task_id, status, priority, input_data, output_data, error_message, 
                   progress_percentage, queued_at, started_at, completed_at, duration_ms
            FROM task_executions 
            WHERE id = ?
            """.trimIndent(),
            mapOf("id" to id)
        ) { row -> mapRowToTaskExecution(row) }
    }

    override suspend fun findAll(): List<TaskExecution> {
        return database.query(
            """
            SELECT id, task_id, status, priority, input_data, output_data, error_message, 
                   progress_percentage, queued_at, started_at, completed_at, duration_ms
            FROM task_executions 
            ORDER BY queued_at DESC
            """.trimIndent()
        ) { row -> mapRowToTaskExecution(row) }
    }

    override suspend fun findAll(offset: Int, limit: Int): Page<TaskExecution> {
        val executions = database.query(
            """
            SELECT id, task_id, status, priority, input_data, output_data, error_message, 
                   progress_percentage, queued_at, started_at, completed_at, duration_ms
            FROM task_executions 
            ORDER BY queued_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            mapOf("limit" to limit, "offset" to offset)
        ) { row -> mapRowToTaskExecution(row) }

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

    override suspend fun save(entity: TaskExecution): TaskExecution {
        val exists = existsById(entity.id)
        
        if (exists) {
            database.execute(
                """
                UPDATE task_executions 
                SET status = ?, priority = ?, input_data = ?::jsonb, output_data = ?::jsonb, 
                    error_message = ?, progress_percentage = ?, started_at = ?, completed_at = ?, duration_ms = ?
                WHERE id = ?
                """.trimIndent(),
                mapOf(
                    "status" to entity.status,
                    "priority" to entity.priority,
                    "input_data" to entity.inputData?.let { Json.encodeToString(it) },
                    "output_data" to entity.outputData?.let { Json.encodeToString(it) },
                    "error_message" to entity.errorMessage,
                    "progress_percentage" to entity.progressPercentage,
                    "started_at" to entity.startedAt?.toString(),
                    "completed_at" to entity.completedAt?.toString(),
                    "duration_ms" to entity.durationMs,
                    "id" to entity.id
                )
            )
        } else {
            database.execute(
                """
                INSERT INTO task_executions (id, task_id, status, priority, input_data, output_data, 
                                           error_message, progress_percentage, queued_at, started_at, completed_at, duration_ms)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                mapOf(
                    "id" to entity.id,
                    "task_id" to entity.taskId,
                    "status" to entity.status,
                    "priority" to entity.priority,
                    "input_data" to entity.inputData?.let { Json.encodeToString(it) },
                    "output_data" to entity.outputData?.let { Json.encodeToString(it) },
                    "error_message" to entity.errorMessage,
                    "progress_percentage" to entity.progressPercentage,
                    "queued_at" to entity.queuedAt.toString(),
                    "started_at" to entity.startedAt?.toString(),
                    "completed_at" to entity.completedAt?.toString(),
                    "duration_ms" to entity.durationMs
                )
            )
        }

        return entity
    }

    override suspend fun saveAll(entities: List<TaskExecution>): List<TaskExecution> {
        return entities.map { save(it) }
    }

    override suspend fun deleteById(id: String): Boolean {
        val rowsAffected = database.execute(
            "DELETE FROM task_executions WHERE id = ?",
            mapOf("id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun delete(entity: TaskExecution): Boolean {
        return deleteById(entity.id)
    }

    override suspend fun existsById(id: String): Boolean {
        return database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions WHERE id = ?",
            mapOf("id" to id)
        ) { row -> (row.getLong("count") ?: 0) > 0 } ?: false
    }

    override suspend fun count(): Long {
        return database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions"
        ) { row -> row.getLong("count") ?: 0 } ?: 0
    }

    // TaskExecutionRepository specific methods

    override suspend fun findByTaskId(taskId: String): List<TaskExecution> {
        return database.query(
            """
            SELECT id, task_id, status, priority, input_data, output_data, error_message, 
                   progress_percentage, queued_at, started_at, completed_at, duration_ms
            FROM task_executions 
            WHERE task_id = ?
            ORDER BY queued_at DESC
            """.trimIndent(),
            mapOf("task_id" to taskId)
        ) { row -> mapRowToTaskExecution(row) }
    }

    override suspend fun findByStatus(status: String): List<TaskExecution> {
        return database.query(
            """
            SELECT id, task_id, status, priority, input_data, output_data, error_message, 
                   progress_percentage, queued_at, started_at, completed_at, duration_ms
            FROM task_executions 
            WHERE status = ?
            ORDER BY queued_at DESC
            """.trimIndent(),
            mapOf("status" to status)
        ) { row -> mapRowToTaskExecution(row) }
    }

    override suspend fun findQueuedByPriority(): List<TaskExecution> {
        return database.query(
            """
            SELECT id, task_id, status, priority, input_data, output_data, error_message, 
                   progress_percentage, queued_at, started_at, completed_at, duration_ms
            FROM task_executions 
            WHERE status = 'queued'
            ORDER BY priority DESC, queued_at ASC
            """.trimIndent()
        ) { row -> mapRowToTaskExecution(row) }
    }

    override suspend fun findRunning(): List<TaskExecution> {
        return database.query(
            """
            SELECT id, task_id, status, priority, input_data, output_data, error_message, 
                   progress_percentage, queued_at, started_at, completed_at, duration_ms
            FROM task_executions 
            WHERE status = 'running'
            ORDER BY started_at ASC
            """.trimIndent()
        ) { row -> mapRowToTaskExecution(row) }
    }

    override suspend fun findRecent(limit: Int): List<TaskExecution> {
        return database.query(
            """
            SELECT id, task_id, status, priority, input_data, output_data, error_message, 
                   progress_percentage, queued_at, started_at, completed_at, duration_ms
            FROM task_executions 
            ORDER BY queued_at DESC
            LIMIT ?
            """.trimIndent(),
            mapOf("limit" to limit)
        ) { row -> mapRowToTaskExecution(row) }
    }

    override suspend fun findByPriority(priority: Int): List<TaskExecution> {
        return database.query(
            """
            SELECT id, task_id, status, priority, input_data, output_data, error_message, 
                   progress_percentage, queued_at, started_at, completed_at, duration_ms
            FROM task_executions 
            WHERE priority = ?
            ORDER BY queued_at DESC
            """.trimIndent(),
            mapOf("priority" to priority)
        ) { row -> mapRowToTaskExecution(row) }
    }

    override suspend fun findHighPriority(threshold: Int): List<TaskExecution> {
        return database.query(
            """
            SELECT id, task_id, status, priority, input_data, output_data, error_message, 
                   progress_percentage, queued_at, started_at, completed_at, duration_ms
            FROM task_executions 
            WHERE priority > ?
            ORDER BY priority DESC, queued_at ASC
            """.trimIndent(),
            mapOf("threshold" to threshold)
        ) { row -> mapRowToTaskExecution(row) }
    }

    override suspend fun findNextQueued(): TaskExecution? {
        return database.queryOne(
            """
            SELECT id, task_id, status, priority, input_data, output_data, error_message, 
                   progress_percentage, queued_at, started_at, completed_at, duration_ms
            FROM task_executions 
            WHERE status = 'queued'
            ORDER BY priority DESC, queued_at ASC
            LIMIT 1
            """.trimIndent()
        ) { row -> mapRowToTaskExecution(row) }
    }

    override suspend fun updateStatus(id: String, status: String, startedAt: Instant?, completedAt: Instant?): Boolean {
        val rowsAffected = database.execute(
            "UPDATE task_executions SET status = ?, started_at = ?, completed_at = ? WHERE id = ?",
            mapOf(
                "status" to status,
                "started_at" to startedAt?.toString(),
                "completed_at" to completedAt?.toString(),
                "id" to id
            )
        )
        return rowsAffected > 0
    }

    override suspend fun updateProgress(id: String, progressPercentage: Int): Boolean {
        val rowsAffected = database.execute(
            "UPDATE task_executions SET progress_percentage = ? WHERE id = ?",
            mapOf("progress_percentage" to progressPercentage, "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun updateOutput(id: String, outputData: Map<String, Any>): Boolean {
        val rowsAffected = database.execute(
            "UPDATE task_executions SET output_data = ?::jsonb WHERE id = ?",
            mapOf("output_data" to Json.encodeToString(outputData), "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun updateError(id: String, errorMessage: String, completedAt: Instant): Boolean {
        val rowsAffected = database.execute(
            "UPDATE task_executions SET error_message = ?, status = 'failed', completed_at = ? WHERE id = ?",
            mapOf("error_message" to errorMessage, "completed_at" to completedAt.toString(), "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun markStarted(id: String, startedAt: Instant): Boolean {
        val rowsAffected = database.execute(
            "UPDATE task_executions SET status = 'running', started_at = ? WHERE id = ?",
            mapOf("started_at" to startedAt.toString(), "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun markCompleted(id: String, outputData: Map<String, Any>?, completedAt: Instant, durationMs: Int): Boolean {
        val rowsAffected = database.execute(
            """
            UPDATE task_executions 
            SET status = 'completed', output_data = ?::jsonb, completed_at = ?, duration_ms = ?, progress_percentage = 100 
            WHERE id = ?
            """.trimIndent(),
            mapOf(
                "output_data" to outputData?.let { Json.encodeToString(it) },
                "completed_at" to completedAt.toString(),
                "duration_ms" to durationMs,
                "id" to id
            )
        )
        return rowsAffected > 0
    }

    override suspend fun markFailed(id: String, errorMessage: String, completedAt: Instant, durationMs: Int): Boolean {
        val rowsAffected = database.execute(
            """
            UPDATE task_executions 
            SET status = 'failed', error_message = ?, completed_at = ?, duration_ms = ? 
            WHERE id = ?
            """.trimIndent(),
            mapOf(
                "error_message" to errorMessage,
                "completed_at" to completedAt.toString(),
                "duration_ms" to durationMs,
                "id" to id
            )
        )
        return rowsAffected > 0
    }

    override suspend fun cancel(id: String, completedAt: Instant): Boolean {
        val rowsAffected = database.execute(
            "UPDATE task_executions SET status = 'cancelled', completed_at = ? WHERE id = ?",
            mapOf("completed_at" to completedAt.toString(), "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun getExecutionStats(taskId: String?): TaskExecutionStats {
        val whereClause = if (taskId != null) "WHERE task_id = ?" else ""
        val params = if (taskId != null) mapOf("task_id" to taskId) else emptyMap()

        val totalExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions $whereClause",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val completedExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} status = 'completed'",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val failedExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} status = 'failed'",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val runningExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} status = 'running'",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val queuedExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} status = 'queued'",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val cancelledExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} status = 'cancelled'",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val averageDurationMs = database.queryOne(
            "SELECT AVG(duration_ms) as avg_duration FROM task_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} duration_ms IS NOT NULL",
            params
        ) { row -> row.getDouble("avg_duration") }

        val successRate = if (totalExecutions > 0) {
            completedExecutions.toDouble() / totalExecutions.toDouble()
        } else {
            0.0
        }

        val averageQueueTime = database.queryOne(
            """
            SELECT AVG(EXTRACT(EPOCH FROM (started_at - queued_at)) * 1000) as avg_queue_time 
            FROM task_executions 
            $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} started_at IS NOT NULL
            """.trimIndent(),
            params
        ) { row -> row.getDouble("avg_queue_time") }

        return TaskExecutionStats(
            totalExecutions = totalExecutions,
            completedExecutions = completedExecutions,
            failedExecutions = failedExecutions,
            runningExecutions = runningExecutions,
            queuedExecutions =
"status" to status,
                "started_at" to startedAt?.toString(),
                "completed_at" to completedAt?.toString(),
                "id" to id
            )
        )
        return rowsAffected > 0
    }

    override suspend fun updateProgress(id: String, progressPercentage: Int): Boolean {
        val rowsAffected = database.execute(
            "UPDATE task_executions SET progress_percentage = ? WHERE id = ?",
            mapOf("progress_percentage" to progressPercentage, "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun updateOutput(id: String, outputData: Map<String, Any>): Boolean {
        val rowsAffected = database.execute(
            "UPDATE task_executions SET output_data = ?::jsonb WHERE id = ?",
            mapOf("output_data" to Json.encodeToString(outputData), "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun updateError(id: String, errorMessage: String, completedAt: Instant): Boolean {
        val rowsAffected = database.execute(
            "UPDATE task_executions SET error_message = ?, status = 'failed', completed_at = ? WHERE id = ?",
            mapOf("error_message" to errorMessage, "completed_at" to completedAt.toString(), "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun markStarted(id: String, startedAt: Instant): Boolean {
        val rowsAffected = database.execute(
            "UPDATE task_executions SET status = 'running', started_at = ? WHERE id = ?",
            mapOf("started_at" to startedAt.toString(), "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun markCompleted(id: String, outputData: Map<String, Any>?, completedAt: Instant, durationMs: Int): Boolean {
        val rowsAffected = database.execute(
            """
            UPDATE task_executions 
            SET status = 'completed', output_data = ?::jsonb, completed_at = ?, duration_ms = ?, progress_percentage = 100 
            WHERE id = ?
            """.trimIndent(),
            mapOf(
                "output_data" to outputData?.let { Json.encodeToString(it) },
                "completed_at" to completedAt.toString(),
                "duration_ms" to durationMs,
                "id" to id
            )
        )
        return rowsAffected > 0
    }

    override suspend fun markFailed(id: String, errorMessage: String, completedAt: Instant, durationMs: Int): Boolean {
        val rowsAffected = database.execute(
            """
            UPDATE task_executions 
            SET status = 'failed', error_message = ?, completed_at = ?, duration_ms = ? 
            WHERE id = ?
            """.trimIndent(),
            mapOf(
                "error_message" to errorMessage,
                "completed_at" to completedAt.toString(),
                "duration_ms" to durationMs,
                "id" to id
            )
        )
        return rowsAffected > 0
    }

    override suspend fun cancel(id: String, completedAt: Instant): Boolean {
        val rowsAffected = database.execute(
            "UPDATE task_executions SET status = 'cancelled', completed_at = ? WHERE id = ?",
            mapOf("completed_at" to completedAt.toString(), "id" to id)
        )
        return rowsAffected > 0
    }

    override suspend fun getExecutionStats(taskId: String?): TaskExecutionStats {
        val whereClause = if (taskId != null) "WHERE task_id = ?" else ""
        val params = if (taskId != null) mapOf("task_id" to taskId) else emptyMap()

        val totalExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions $whereClause",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val completedExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} status = 'completed'",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val failedExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} status = 'failed'",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val runningExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} status = 'running'",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val queuedExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} status = 'queued'",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val cancelledExecutions = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} status = 'cancelled'",
            params
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val averageDurationMs = database.queryOne(
            "SELECT AVG(duration_ms) as avg_duration FROM task_executions $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} duration_ms IS NOT NULL",
            params
        ) { row -> row.getDouble("avg_duration") }

        val successRate = if (totalExecutions > 0) {
            completedExecutions.toDouble() / totalExecutions.toDouble()
        } else {
            0.0
        }

        val averageQueueTime = database.queryOne(
            """
            SELECT AVG(EXTRACT(EPOCH FROM (started_at - queued_at)) * 1000) as avg_queue_time 
            FROM task_executions 
            $whereClause ${if (whereClause.isNotEmpty()) "AND" else "WHERE"} started_at IS NOT NULL
            """.trimIndent(),
            params
        ) { row -> row.getDouble("avg_queue_time") }

        return TaskExecutionStats(
            totalExecutions = totalExecutions,
            completedExecutions = completedExecutions,
            failedExecutions = failedExecutions,
            runningExecutions = runningExecutions,
            queuedExecutions = queuedExecutions,
            cancelledExecutions = cancelledExecutions,
            averageDurationMs = averageDurationMs,
            successRate = successRate,
            averageQueueTime = averageQueueTime
        )
    }

    override suspend fun getQueueStats(): QueueStats {
        val queuedCount = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions WHERE status = 'queued'"
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val runningCount = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions WHERE status = 'running'"
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val highPriorityCount = database.queryOne(
            "SELECT COUNT(*) as count FROM task_executions WHERE status = 'queued' AND priority > 5"
        ) { row -> row.getLong("count") ?: 0 } ?: 0

        val averagePriority = database.queryOne(
            "SELECT AVG(priority) as avg_priority FROM task_executions WHERE status = 'queued'"
        ) { row -> row.getDouble("avg_priority") }

        val oldestQueuedAt = database.queryOne(
            "SELECT MIN(queued_at) as oldest FROM task_executions WHERE status = 'queued'"
        ) { row -> row.getTimestamp("oldest") }

        // Estimate processing time based on average duration and queue size
        val estimatedProcessingTime = if (queuedCount > 0) {
            val avgDuration = database.queryOne(
                "SELECT AVG(duration_ms) as avg_duration FROM task_executions WHERE duration_ms IS NOT NULL"
            ) { row -> row.getDouble("avg_duration") } ?: 30000.0 // Default 30 seconds
            
            (queuedCount * avgDuration).toLong()
        } else {
            null
        }

        return QueueStats(
            queuedCount = queuedCount,
            runningCount = runningCount,
            highPriorityCount = highPriorityCount,
            averagePriority = averagePriority,
            oldestQueuedAt = oldestQueuedAt,
            estimatedProcessingTime = estimatedProcessingTime
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapRowToTaskExecution(row: ResultRow): TaskExecution {
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

        return TaskExecution(
            id = row.getString("id") ?: "",
            taskId = row.getString("task_id") ?: "",
            status = row.getString("status") ?: "queued",
            priority = row.getInt("priority") ?: 0,
            inputData = inputData,
            outputData = outputData,
            errorMessage = row.getString("error_message"),
            progressPercentage = row.getInt("progress_percentage") ?: 0,
            queuedAt = row.getTimestamp("queued_at") ?: Clock.System.now(),
            startedAt = row.getTimestamp("started_at"),
            completedAt = row.getTimestamp("completed_at"),
            durationMs = row.getInt("duration_ms")
        )
    }
}