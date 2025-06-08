package com.ataiva.eden.insight.service

import com.ataiva.eden.insight.model.Report
import com.ataiva.eden.insight.model.ReportExecution
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.*
import javax.mail.internet.*
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service responsible for sending report notifications and delivering reports to recipients.
 */
class ReportNotificationService(private val config: NotificationConfig) {
    private val logger = LoggerFactory.getLogger(ReportNotificationService::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    /**
     * Send a report notification with the report attached
     */
    suspend fun sendReportNotification(
        report: Report,
        execution: ReportExecution,
        recipients: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        if (recipients.isEmpty()) {
            logger.info("No recipients specified for report ${report.id}")
            return@withContext true
        }
        
        if (execution.outputPath == null) {
            logger.error("No output path available for report execution ${execution.id}")
            return@withContext false
        }
        
        val reportFile = File(execution.outputPath)
        if (!reportFile.exists()) {
            logger.error("Report file not found: ${execution.outputPath}")
            return@withContext false
        }
        
        try {
            // Prepare email properties
            val properties = Properties()
            properties["mail.smtp.host"] = config.smtpHost
            properties["mail.smtp.port"] = config.smtpPort
            properties["mail.smtp.auth"] = "true"
            properties["mail.smtp.starttls.enable"] = "true"
            
            // Create session with authentication
            val session = Session.getInstance(properties, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(config.username, config.password)
                }
            })
            
            // Create message
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(config.fromEmail))
            
            // Add recipients
            for (recipient in recipients) {
                message.addRecipient(Message.RecipientType.TO, InternetAddress(recipient))
            }
            
            // Set subject
            message.subject = "Report: ${report.name} - ${LocalDateTime.now().format(dateTimeFormatter)}"
            
            // Create multipart message
            val multipart = MimeMultipart()
            
            // Create text part
            val textPart = MimeBodyPart()
            textPart.setText(createEmailBody(report, execution), "utf-8", "html")
            multipart.addBodyPart(textPart)
            
            // Create attachment part
            val attachmentPart = MimeBodyPart()
            val source = FileDataSource(reportFile)
            attachmentPart.dataHandler = DataHandler(source)
            attachmentPart.fileName = reportFile.name
            multipart.addBodyPart(attachmentPart)
            
            // Set content
            message.setContent(multipart)
            
            // Send message
            Transport.send(message)
            
            logger.info("Report notification sent successfully to ${recipients.size} recipients for report ${report.id}")
            return@withContext true
            
        } catch (e: Exception) {
            logger.error("Error sending report notification: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Create the email body for the report notification
     */
    private fun createEmailBody(report: Report, execution: ReportExecution): String {
        val executionTime = if (execution.endTime != null && execution.startTime != null) {
            (execution.endTime - execution.startTime) / 1000.0
        } else {
            0.0
        }
        
        return """
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; }
                    .header { background-color: #336699; color: white; padding: 10px; }
                    .content { padding: 15px; }
                    .footer { background-color: #f2f2f2; padding: 10px; font-size: 0.8em; }
                    table { border-collapse: collapse; width: 100%; }
                    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                    th { background-color: #f2f2f2; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h2>Report: ${report.name}</h2>
                </div>
                <div class="content">
                    <p>Please find attached the requested report.</p>
                    
                    <h3>Report Details:</h3>
                    <table>
                        <tr>
                            <th>Report Name</th>
                            <td>${report.name}</td>
                        </tr>
                        ${report.description?.let { "<tr><th>Description</th><td>$it</td></tr>" } ?: ""}
                        <tr>
                            <th>Generated At</th>
                            <td>${LocalDateTime.now().format(dateTimeFormatter)}</td>
                        </tr>
                        <tr>
                            <th>Format</th>
                            <td>${report.format}</td>
                        </tr>
                        <tr>
                            <th>Generation Time</th>
                            <td>${String.format("%.2f", executionTime)} seconds</td>
                        </tr>
                        <tr>
                            <th>File Size</th>
                            <td>${formatFileSize(execution.fileSize)}</td>
                        </tr>
                    </table>
                    
                    <p>This is an automated notification. Please do not reply to this email.</p>
                </div>
                <div class="footer">
                    <p>Generated by Eden Insight Service</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
    
    /**
     * Format file size in human-readable format
     */
    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}

/**
 * Configuration for email notifications
 */
data class NotificationConfig(
    val smtpHost: String,
    val smtpPort: String,
    val username: String,
    val password: String,
    val fromEmail: String
)