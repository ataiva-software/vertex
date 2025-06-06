package com.ataiva.eden.flow.service

import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMultipart
import org.slf4j.LoggerFactory

/**
 * Service for sending email notifications
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
     * Send an email
     */
    fun sendEmail(
        to: String,
        subject: String,
        body: String,
        isHtml: Boolean = false
    ): Result<Unit> {
        return try {
            val username = System.getenv("SMTP_USERNAME") ?: ""
            val password = System.getenv("SMTP_PASSWORD") ?: ""
            val fromAddress = System.getenv("EMAIL_FROM_ADDRESS") ?: "notifications@eden.example.com"
            val fromName = System.getenv("EMAIL_FROM_NAME") ?: "Eden Notifications"
            
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
}