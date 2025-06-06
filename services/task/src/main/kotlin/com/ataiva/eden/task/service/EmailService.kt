package com.ataiva.eden.task.service

import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMultipart
import org.slf4j.LoggerFactory

/**
 * Service for sending email notifications from the Task service
 */
class EmailService {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)
    private val properties = Properties()
    
    init {
        try {
            // Load default properties
            properties.setProperty("mail.smtp.host", "smtp.example.com")
            properties.setProperty("mail.smtp.port", "587")
            properties.setProperty("mail.smtp.auth", "true")
            properties.setProperty("mail.smtp.starttls.enable", "true")
            
            // Try to load from environment or config file
            val configPath = System.getenv("EDEN_CONFIG_PATH") ?: "application.properties"
            try {
                val configFile = java.io.File(configPath)
                if (configFile.exists()) {
                    val fileProps = Properties()
                    fileProps.load(java.io.FileInputStream(configFile))
                    
                    // Override with values from config file
                    properties.setProperty("mail.smtp.host", fileProps.getProperty("email.smtp.host", properties.getProperty("mail.smtp.host")))
                    properties.setProperty("mail.smtp.port", fileProps.getProperty("email.smtp.port", properties.getProperty("mail.smtp.port")))
                    properties.setProperty("mail.smtp.auth", fileProps.getProperty("email.smtp.auth", properties.getProperty("mail.smtp.auth")))
                    properties.setProperty("mail.smtp.starttls.enable", fileProps.getProperty("email.smtp.starttls.enable", properties.getProperty("mail.smtp.starttls.enable")))
                    
                    logger.info("Email configuration loaded from $configPath")
                }
            } catch (e: Exception) {
                logger.warn("Failed to load email configuration from file: ${e.message}")
            }
            
            // Override with environment variables if present
            System.getenv("EMAIL_SMTP_HOST")?.let { properties.setProperty("mail.smtp.host", it) }
            System.getenv("EMAIL_SMTP_PORT")?.let { properties.setProperty("mail.smtp.port", it) }
            System.getenv("EMAIL_SMTP_AUTH")?.let { properties.setProperty("mail.smtp.auth", it) }
            System.getenv("EMAIL_SMTP_STARTTLS")?.let { properties.setProperty("mail.smtp.starttls.enable", it) }
            
        } catch (e: Exception) {
            logger.error("Failed to initialize email service: ${e.message}")
        }
    }
    
    /**
     * Send a task notification email
     */
    fun sendTaskNotification(
        to: String,
        taskName: String,
        taskId: String,
        status: String,
        details: String? = null
    ): Result<Unit> {
        val subject = "Task Notification: $taskName ($status)"
        val body = buildTaskNotificationBody(taskName, taskId, status, details)
        return sendEmail(to, subject, body, true)
    }
    
    /**
     * Send a task completion email
     */
    fun sendTaskCompletionEmail(
        to: String,
        taskName: String,
        taskId: String,
        executionTime: Long,
        results: Map<String, Any>? = null
    ): Result<Unit> {
        val subject = "Task Completed: $taskName"
        val body = buildTaskCompletionBody(taskName, taskId, executionTime, results)
        return sendEmail(to, subject, body, true)
    }
    
    /**
     * Send a task failure email
     */
    fun sendTaskFailureEmail(
        to: String,
        taskName: String,
        taskId: String,
        errorMessage: String,
        executionTime: Long
    ): Result<Unit> {
        val subject = "Task Failed: $taskName"
        val body = buildTaskFailureBody(taskName, taskId, errorMessage, executionTime)
        return sendEmail(to, subject, body, true)
    }
    
    /**
     * Send an email
     */
    private fun sendEmail(
        to: String,
        subject: String,
        body: String,
        isHtml: Boolean = false
    ): Result<Unit> {
        return try {
            val username = System.getenv("SMTP_USERNAME") ?: ""
            val password = System.getenv("SMTP_PASSWORD") ?: ""
            val fromAddress = System.getenv("EMAIL_FROM_ADDRESS") ?: "tasks@eden.example.com"
            val fromName = System.getenv("EMAIL_FROM_NAME") ?: "Eden Task Service"
            
            // Create mail session
            val session = Session.getInstance(properties, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password)
                }
            })
            
            // Create message
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(fromAddress, fromName))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            message.subject = subject
            
            if (isHtml) {
                // HTML email
                val multipart = MimeMultipart("alternative")
                
                // Plain text part
                val textPart = MimeBodyPart()
                textPart.setText(body.replace("<[^>]*>".toRegex(), ""), "utf-8")
                
                // HTML part
                val htmlPart = MimeBodyPart()
                htmlPart.setContent(body, "text/html; charset=utf-8")
                
                multipart.addBodyPart(textPart)
                multipart.addBodyPart(htmlPart)
                message.setContent(multipart)
            } else {
                // Plain text email
                message.setText(body, "utf-8")
            }
            
            // Send message
            Transport.send(message)
            
            logger.info("Email sent successfully to $to with subject: $subject")
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("Failed to send email: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Build task notification email body
     */
    private fun buildTaskNotificationBody(
        taskName: String,
        taskId: String,
        status: String,
        details: String?
    ): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                .header { background-color: #4a86e8; color: white; padding: 10px; text-align: center; }
                .content { padding: 20px; border: 1px solid #ddd; }
                .footer { font-size: 12px; color: #777; text-align: center; margin-top: 20px; }
                .status { font-weight: bold; }
                .status-running { color: #4a86e8; }
                .status-completed { color: #0f9d58; }
                .status-failed { color: #db4437; }
                .status-pending { color: #f4b400; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h2>Task Notification</h2>
                </div>
                <div class="content">
                    <p>Task <strong>${taskName}</strong> (ID: ${taskId}) is now <span class="status status-${status.toLowerCase()}">${status}</span>.</p>
                    ${if (details != null) "<p><strong>Details:</strong> $details</p>" else ""}
                    <p>You can check the task status in the Eden dashboard.</p>
                </div>
                <div class="footer">
                    <p>This is an automated message from the Eden Task Service. Please do not reply to this email.</p>
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
    
    /**
     * Build task completion email body
     */
    private fun buildTaskCompletionBody(
        taskName: String,
        taskId: String,
        executionTime: Long,
        results: Map<String, Any>?
    ): String {
        val executionTimeFormatted = formatExecutionTime(executionTime)
        
        val resultsHtml = if (results != null && results.isNotEmpty()) {
            """
            <h3>Results:</h3>
            <ul>
                ${results.entries.joinToString("\n") { "<li><strong>${it.key}:</strong> ${it.value}</li>" }}
            </ul>
            """.trimIndent()
        } else {
            "<p>No specific results were returned.</p>"
        }
        
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                .header { background-color: #0f9d58; color: white; padding: 10px; text-align: center; }
                .content { padding: 20px; border: 1px solid #ddd; }
                .footer { font-size: 12px; color: #777; text-align: center; margin-top: 20px; }
                .execution-time { font-weight: bold; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h2>Task Completed Successfully</h2>
                </div>
                <div class="content">
                    <p>Task <strong>${taskName}</strong> (ID: ${taskId}) has completed successfully.</p>
                    <p>Execution time: <span class="execution-time">${executionTimeFormatted}</span></p>
                    ${resultsHtml}
                </div>
                <div class="footer">
                    <p>This is an automated message from the Eden Task Service. Please do not reply to this email.</p>
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
    
    /**
     * Build task failure email body
     */
    private fun buildTaskFailureBody(
        taskName: String,
        taskId: String,
        errorMessage: String,
        executionTime: Long
    ): String {
        val executionTimeFormatted = formatExecutionTime(executionTime)
        
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                .header { background-color: #db4437; color: white; padding: 10px; text-align: center; }
                .content { padding: 20px; border: 1px solid #ddd; }
                .footer { font-size: 12px; color: #777; text-align: center; margin-top: 20px; }
                .error { color: #db4437; font-family: monospace; background-color: #f8f8f8; padding: 10px; border-left: 3px solid #db4437; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h2>Task Failed</h2>
                </div>
                <div class="content">
                    <p>Task <strong>${taskName}</strong> (ID: ${taskId}) has failed.</p>
                    <p>Execution time before failure: ${executionTimeFormatted}</p>
                    <h3>Error Message:</h3>
                    <div class="error">${errorMessage}</div>
                    <p>Please check the logs for more details.</p>
                </div>
                <div class="footer">
                    <p>This is an automated message from the Eden Task Service. Please do not reply to this email.</p>
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
    
    /**
     * Format execution time in a human-readable format
     */
    private fun formatExecutionTime(milliseconds: Long): String {
        if (milliseconds < 1000) {
            return "$milliseconds ms"
        }
        
        val seconds = milliseconds / 1000
        if (seconds < 60) {
            return "$seconds seconds"
        }
        
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        if (minutes < 60) {
            return "$minutes minutes, $remainingSeconds seconds"
        }
        
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return "$hours hours, $remainingMinutes minutes"
    }
}