package com.ataiva.eden.task.queue

import com.ataiva.eden.database.repositories.TaskExecution
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ConcurrentHashMap

/**
 * Priority-based task queue for managing task execution order
 */
class TaskQueue {
    
    private val queue = PriorityBlockingQueue<QueuedExecution>(100) { a, b ->
        // Higher priority first, then by queued time (FIFO for same priority)
        when {
            a.priority != b.priority -> b.priority.compareTo(a.priority)
            else -> a.queuedAt.compareTo(b.queuedAt)
        }
    }
    
    private val executionMap = ConcurrentHashMap<String, QueuedExecution>()
    private val mutex = Mutex()
    
    /**
     * Add task execution to queue
     */
    suspend fun enqueue(execution: TaskExecution) {
        mutex.withLock {
            val queuedExecution = QueuedExecution(
                execution = execution,
                priority = execution.priority,
                queuedAt = execution.queuedAt
            )
            
            queue.offer(queuedExecution)
            executionMap[execution.id] = queuedExecution
        }
    }
    
    /**
     * Get next task execution from queue
     */
    suspend fun dequeue(): TaskExecution? {
        return mutex.withLock {
            val queuedExecution = queue.poll()
            if (queuedExecution != null) {
                executionMap.remove(queuedExecution.execution.id)
                queuedExecution.execution
            } else {
                null
            }
        }
    }
    
    /**
     * Remove specific execution from queue
     */
    suspend fun remove(executionId: String): Boolean {
        return mutex.withLock {
            val queuedExecution = executionMap.remove(executionId)
            if (queuedExecution != null) {
                queue.remove(queuedExecution)
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Get queue size
     */
    fun size(): Int = queue.size
    
    /**
     * Check if queue is empty
     */
    fun isEmpty(): Boolean = queue.isEmpty()
    
    /**
     * Get queue statistics
     */
    suspend fun getStats(): QueueStatistics {
        return mutex.withLock {
            val executions = queue.toList()
            val priorityGroups = executions.groupBy { it.priority }
            
            QueueStatistics(
                totalQueued = executions.size,
                highPriority = executions.count { it.priority >= 8 },
                mediumPriority = executions.count { it.priority in 4..7 },
                lowPriority = executions.count { it.priority <= 3 },
                averagePriority = if (executions.isNotEmpty()) {
                    executions.map { it.priority }.average()
                } else 0.0,
                oldestQueuedAt = executions.minByOrNull { it.queuedAt }?.queuedAt,
                priorityDistribution = priorityGroups.mapValues { it.value.size }
            )
        }
    }
    
    /**
     * Get queued executions by priority
     */
    suspend fun getQueuedByPriority(minPriority: Int = 0): List<TaskExecution> {
        return mutex.withLock {
            queue.filter { it.priority >= minPriority }
                .map { it.execution }
                .toList()
        }
    }
    
    /**
     * Clear all queued executions
     */
    suspend fun clear() {
        mutex.withLock {
            queue.clear()
            executionMap.clear()
        }
    }
    
    /**
     * Peek at next execution without removing it
     */
    suspend fun peek(): TaskExecution? {
        return mutex.withLock {
            queue.peek()?.execution
        }
    }
}

/**
 * Queued execution wrapper with priority and timing info
 */
private data class QueuedExecution(
    val execution: TaskExecution,
    val priority: Int,
    val queuedAt: kotlinx.datetime.Instant
)

/**
 * Queue statistics
 */
data class QueueStatistics(
    val totalQueued: Int,
    val highPriority: Int,
    val mediumPriority: Int,
    val lowPriority: Int,
    val averagePriority: Double,
    val oldestQueuedAt: kotlinx.datetime.Instant?,
    val priorityDistribution: Map<Int, Int>
)