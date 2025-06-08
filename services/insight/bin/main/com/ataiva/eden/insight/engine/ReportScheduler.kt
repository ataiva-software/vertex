package com.ataiva.eden.insight.engine

import com.ataiva.eden.insight.model.Report
import com.ataiva.eden.insight.model.ReportExecution
import com.ataiva.eden.insight.model.ExecutionStatus
import com.ataiva.eden.insight.model.ReportGenerationRequest
import com.ataiva.eden.insight.repository.ReportRepository
import com.ataiva.eden.insight.repository.ReportTemplateRepository
import com.ataiva.eden.insight.repository.ReportExecutionRepository
import com.ataiva.eden.insight.service.InsightService
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import com.cronutils.model.time.ExecutionTime
import java.time.temporal.ChronoUnit

/**
 * Manages scheduled report generation, including cron expression parsing,
 * scheduling, and execution tracking.
 */
class ReportScheduler(
    private val reportRepository: ReportRepository,
    private val reportTemplateRepository: ReportTemplateRepository,
    private val reportExecutionRepository: ReportExecutionRepository,
    private val insightService: InsightService
) {
    private val logger = LoggerFactory.getLogger(ReportScheduler::class.java)
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(5)
    private val activeSchedules = ConcurrentHashMap<String, ScheduledTask>()
    private val cronParser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ))
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Start the scheduler
     */
    fun start() {
        logger.info("Starting report scheduler")
        
        // Schedule the main task to check for reports that need to be executed
        scheduler.scheduleAtFixedRate(
            { checkScheduledReports() },
            0,
            1,
            TimeUnit.MINUTES
        )
    }
    
    /**
     * Stop the scheduler
     */
    fun stop() {
        logger.info("Stopping report scheduler")
        coroutineScope.cancel()
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
    
    /**
     * Schedule a report for execution
     */
    fun scheduleReport(report: Report) {
        if (report.schedule == null || !report.schedule.enabled) {
            logger.info("Report ${report.id} has no schedule or is disabled")
            return
        }
        
        try {
            val cronExpression = cronParser.parse(report.schedule.cronExpression)
            val executionTime = ExecutionTime.forCron(cronExpression)
            
            val now = ZonedDateTime.now(ZoneId.of(report.schedule.timezone))
            val nextExecution = executionTime.nextExecution(now).orElse(null)
            
            if (nextExecution != null) {
                val delayMillis = ChronoUnit.MILLIS.between(now, nextExecution)
                
                val task = scheduler.schedule(
                    { executeReport(report.id) },
                    delayMillis,
                    TimeUnit.MILLISECONDS
                )
                
                activeSchedules[report.id] = ScheduledTask(task, nextExecution.toInstant().toEpochMilli())
                
                logger.info("Scheduled report ${report.id} for execution at $nextExecution")
                
                // Update the report with the next execution time
                coroutineScope.launch {
                    updateReportNextExecution(report.id, nextExecution.toInstant().toEpochMilli())
                }
            }
        } catch (e: Exception) {
            logger.error("Error scheduling report ${report.id}: ${e.message}", e)
        }
    }
    
    /**
     * Cancel a scheduled report
     */
    fun cancelScheduledReport(reportId: String) {
        val scheduledTask = activeSchedules[reportId]
        if (scheduledTask != null) {
            scheduledTask.task.cancel(false)
            activeSchedules.remove(reportId)
            logger.info("Cancelled scheduled report $reportId")
        }
    }
    
    /**
     * Check for reports that need to be executed
     */
    private fun checkScheduledReports() {
        coroutineScope.launch {
            try {
                val reports = reportRepository.findAll()
                    .filter { it.isActive && it.schedule != null && it.schedule.enabled }
                
                for (report in reports) {
                    // If the report is not already scheduled, schedule it
                    if (!activeSchedules.containsKey(report.id)) {
                        scheduleReport(report)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error checking scheduled reports: ${e.message}", e)
            }
        }
    }
    
    /**
     * Execute a report
     */
    private fun executeReport(reportId: String) {
        coroutineScope.launch {
            try {
                logger.info("Executing scheduled report $reportId")
                
                val report = reportRepository.findById(reportId)
                if (report == null) {
                    logger.error("Report $reportId not found")
                    return@launch
                }
                
                // Remove from active schedules
                activeSchedules.remove(reportId)
                
                // Generate the report
                val request = ReportGenerationRequest(
                    reportId = reportId,
                    parameters = report.parameters,
                    format = report.format,
                    async = true
                )
                
                val response = insightService.generateReport(request, "scheduler")
                
                // Update the report's last execution time
                val now = System.currentTimeMillis()
                updateReportLastExecution(reportId, now)
                
                // Schedule the next execution
                scheduleReport(report.copy(
                    schedule = report.schedule?.copy(
                        lastExecution = now
                    )
                ))
                
                logger.info("Scheduled report $reportId executed successfully: ${response.executionId}")
                
            } catch (e: Exception) {
                logger.error("Error executing scheduled report $reportId: ${e.message}", e)
            }
        }
    }
    
    /**
     * Update a report's next execution time
     */
    private suspend fun updateReportNextExecution(reportId: String, nextExecution: Long) {
        try {
            val report = reportRepository.findById(reportId)
            if (report != null && report.schedule != null) {
                val updatedReport = report.copy(
                    schedule = report.schedule.copy(
                        nextExecution = nextExecution
                    )
                )
                reportRepository.update(updatedReport)
            }
        } catch (e: Exception) {
            logger.error("Error updating next execution for report $reportId: ${e.message}", e)
        }
    }
    
    /**
     * Update a report's last execution time
     */
    private suspend fun updateReportLastExecution(reportId: String, lastExecution: Long) {
        try {
            val report = reportRepository.findById(reportId)
            if (report != null && report.schedule != null) {
                val updatedReport = report.copy(
                    lastGenerated = lastExecution,
                    schedule = report.schedule.copy(
                        lastExecution = lastExecution
                    )
                )
                reportRepository.update(updatedReport)
            }
        } catch (e: Exception) {
            logger.error("Error updating last execution for report $reportId: ${e.message}", e)
        }
    }
    
    /**
     * Helper class to track scheduled tasks
     */
    private data class ScheduledTask(
        val task: java.util.concurrent.ScheduledFuture<*>,
        val scheduledTime: Long
    )
}