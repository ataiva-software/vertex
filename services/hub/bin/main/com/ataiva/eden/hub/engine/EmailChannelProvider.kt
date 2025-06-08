package com.ataiva.eden.hub.engine

import com.ataiva.eden.hub.model.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/**
 * Email channel provider using SMTP
 */
class EmailChannelProvider(
    private val config: EmailProviderConfig,
    private val meterRegistry: MeterRegistry? = null
) : NotificationChannelProvider {
    
    private val logger = LoggerFactory.getLogger(EmailChannelProvider::class.java)
    
    // Maximum number of retry attempts for sending emails
    private val maxRetries = 3
    
    // Base delay for exponential backoff (in milliseconds)
    private val baseDelayMs = 1000L
    
    override suspend fun sendNotification(
        recipients: List<NotificationRecipient>,
        subject: String?,
        body: String,
        priority: NotificationPriority
    ): ChannelDeliveryResult {
        val startTime = System.currentTimeMillis()
        logger.info("Sending email notification with priority ${priority.name} to ${recipients.size} recipients")
        
        return try {
            val emailRecipients = recipients.filter { it.type == RecipientType.EMAIL }
            if (emailRecipients.isEmpty()) {
                logger.warn("No email recipients found in notification request")
                return ChannelDeliveryResult(false, "No email recipients found")
            }
            
            // Send email using JavaMail API with retry
            val result = withContext(Dispatchers.IO) {
                sendEmailWithRetry(emailRecipients, subject ?: "", body, priority)
            }
            
            val duration = System.currentTimeMillis() - startTime
            
            if (result.success) {
                logger.info("Email sent successfully to ${emailRecipients.size} recipients in ${duration}ms")
                recordMetrics("success", duration, emailRecipients.size, priority)
                
                ChannelDeliveryResult(
                    success = true,
                    details = mapOf(
                        "recipients" to emailRecipients.map { it.address },
                        "subject" to (subject ?: ""),
                        "provider" to config.provider,
                        "durationMs" to duration
                    )
                )
            } else {
                logger.error("Email delivery failed: ${result.error}")
                recordMetrics("failure", duration, emailRecipients.size, priority)
                
                ChannelDeliveryResult(
                    success = false,
                    error = result.error,
                    details = result.details
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error("Unexpected error during email delivery: ${e.message}", e)
            recordMetrics("error", duration, recipients.size, priority)
            
            ChannelDeliveryResult(
                false,
                "Email delivery failed: ${e.message}",
                details = mapOf(
                    "errorType" to e.javaClass.simpleName,
                    "durationMs" to duration
                )
            )
        }
    }
    
    /**
     * Record metrics for email sending
     */
    private fun recordMetrics(result: String, durationMs: Long, recipientCount: Int, priority: NotificationPriority) {
        meterRegistry?.let { registry ->
            val tags = listOf(
                Tag.of("result", result),
                Tag.of("provider", config.provider),
                Tag.of("priority", priority.name)
            )
            
            // Count emails sent
            registry.counter("email.send.count", tags).increment()
            
            // Record duration
            registry.timer("email.send.duration", tags).record(durationMs, TimeUnit.MILLISECONDS)
            
            // Record recipient count
            registry.gauge("email.recipients", tags, recipientCount.toDouble())
        }
    }
    
    /**
     * Send email with retry logic
     */
    private suspend fun sendEmailWithRetry(
        recipients: List<NotificationRecipient>,
        subject: String,
        body: String,
        priority: NotificationPriority
    ): ChannelDeliveryResult {
        var lastException: Exception? = null
        
        for (attempt in 0 until maxRetries) {
            try {
                if (attempt > 0) {
                    logger.info("Retry attempt ${attempt + 1}/$maxRetries for sending email")
                    // Exponential backoff with jitter
                    val delayMs = (baseDelayMs * Math.pow(2.0, attempt.toDouble())).toLong() +
                                 (Math.random() * baseDelayMs).toLong()
                    delay(delayMs)
                }
                
                sendEmail(recipients, subject, body, priority)
                return ChannelDeliveryResult(
                    success = true,
                    details = mapOf(
                        "attempt" to (attempt + 1),
                        "maxAttempts" to maxRetries
                    )
                )
            } catch (e: MessagingException) {
                lastException = e
                logger.warn("Email sending failed on attempt ${attempt + 1}/$maxRetries: ${e.message}")
                
                // Don't retry for certain types of exceptions
                if (e is AddressException || e.message?.contains("Invalid Addresses") == true) {
                    logger.error("Not retrying due to invalid address error: ${e.message}")
                    return ChannelDeliveryResult(
                        success = false,
                        error = "Invalid email address: ${e.message}",
                        details = mapOf("errorType" to "AddressError")
                    )
                }
            } catch (e: Exception) {
                lastException = e
                logger.warn("Email sending failed on attempt ${attempt + 1}/$maxRetries: ${e.message}")
            }
        }
        
        // All retries failed
        logger.error("Email sending failed after $maxRetries attempts")
        return ChannelDeliveryResult(
            success = false,
            error = "Email delivery failed after $maxRetries attempts: ${lastException?.message}",
            details = mapOf(
                "attempts" to maxRetries,
                "errorType" to (lastException?.javaClass?.simpleName ?: "Unknown")
            )
        )
    }
    
    /**
     * Create a mail session with the configured properties
     * This method can be overridden in tests
     */
    protected open fun createMailSession(): Session {
        // Set up mail server properties
        val properties = Properties()
        properties["mail.smtp.host"] = config.host
        properties["mail.smtp.port"] = config.port
        properties["mail.smtp.auth"] = "true"
        properties["mail.smtp.starttls.enable"] = "true"
        
        // Create session with authentication
        return Session.getInstance(properties, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(config.username, config.password)
            }
        })
    }
    
    private fun sendEmail(
        recipients: List<NotificationRecipient>,
        subject: String,
        body: String,
        priority: NotificationPriority
    ) {
        logger.debug("Preparing email with subject '$subject' for ${recipients.size} recipients")
        
        // Create session
        val session = createMailSession()
        
        // Create message
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(config.fromEmail, config.fromName))
        
        // Add recipients
        val validRecipients = mutableListOf<NotificationRecipient>()
        recipients.forEach { recipient ->
            try {
                val address = InternetAddress(recipient.address, recipient.name)
                address.validate() // Validate email address format
                message.addRecipient(Message.RecipientType.TO, address)
                validRecipients.add(recipient)
            } catch (e: AddressException) {
                logger.warn("Invalid email address: ${recipient.address} - ${e.message}")
                // Skip invalid addresses
            }
        }
        
        if (validRecipients.isEmpty()) {
            logger.error("No valid email recipients found")
            throw MessagingException("No valid email recipients")
        }
        
        // Set subject
        message.subject = subject
        
        // Set message ID for tracking
        val messageId = "<${System.currentTimeMillis()}.${Math.random()}@${config.fromEmail.split("@")[1]}>"
        message.setHeader("Message-ID", messageId)
        
        // Set priority headers based on notification priority
        when (priority) {
            NotificationPriority.HIGH, NotificationPriority.URGENT -> {
                message.setHeader("X-Priority", "1")
                message.setHeader("Importance", "high")
                message.setHeader("Priority", "urgent")
                logger.debug("Setting email priority to HIGH/URGENT")
            }
            NotificationPriority.NORMAL -> {
                message.setHeader("X-Priority", "3")
                message.setHeader("Importance", "normal")
                message.setHeader("Priority", "normal")
            }
            NotificationPriority.LOW -> {
                message.setHeader("X-Priority", "5")
                message.setHeader("Importance", "low")
                message.setHeader("Priority", "non-urgent")
            }
        }
        
        // Create multipart message for HTML content
        val multipart = MimeMultipart("alternative")
        
        // Create text part as fallback
        val textPart = MimeBodyPart()
        textPart.setText(stripHtml(body), "utf-8")
        multipart.addBodyPart(textPart)
        
        // Create HTML part
        val htmlPart = MimeBodyPart()
        htmlPart.setContent(body, "text/html; charset=utf-8")
        multipart.addBodyPart(htmlPart)
        
        // Set content
        message.setContent(multipart)
        
        // Send message
        logger.debug("Sending email via ${config.host}:${config.port}")
        Transport.send(message)
        logger.debug("Email sent successfully with message ID: $messageId")
    }
    
    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&apos;"), "'")
    }
}

/**
 * Email provider configuration
 */
data class EmailProviderConfig(
    val provider: String,
    val host: String,
    val port: String,
    val username: String,
    val password: String,
    val fromEmail: String,
    val fromName: String
)

/**
 * SendGrid implementation of EmailChannelProvider
 */
class SendGridEmailProvider(
    private val apiKey: String,
    private val fromEmail: String,
    private val fromName: String,
    private val meterRegistry: MeterRegistry? = null
) : NotificationChannelProvider {
    
    private val logger = LoggerFactory.getLogger(SendGridEmailProvider::class.java)
    private val client = com.sendgrid.SendGrid(apiKey)
    
    // Maximum number of retry attempts for sending emails
    private val maxRetries = 3
    
    // Base delay for exponential backoff (in milliseconds)
    private val baseDelayMs = 1000L
    
    // Rate limiting - max emails per minute
    private val maxEmailsPerMinute = 100
    private val rateLimiter = RateLimiter(maxEmailsPerMinute, 60_000)
    
    override suspend fun sendNotification(
        recipients: List<NotificationRecipient>,
        subject: String?,
        body: String,
        priority: NotificationPriority
    ): ChannelDeliveryResult {
        val startTime = System.currentTimeMillis()
        logger.info("Sending SendGrid email notification with priority ${priority.name} to ${recipients.size} recipients")
        
        return try {
            val emailRecipients = recipients.filter { it.type == RecipientType.EMAIL }
            if (emailRecipients.isEmpty()) {
                logger.warn("No email recipients found in notification request")
                return ChannelDeliveryResult(false, "No email recipients found")
            }
            
            // Check rate limit
            if (!rateLimiter.tryAcquire(emailRecipients.size)) {
                logger.warn("Rate limit exceeded for SendGrid emails")
                return ChannelDeliveryResult(
                    success = false,
                    error = "Rate limit exceeded, please try again later",
                    details = mapOf("rateLimitPerMinute" to maxEmailsPerMinute)
                )
            }
            
            // Send email using SendGrid API with retry
            val result = withContext(Dispatchers.IO) {
                sendEmailWithRetry(emailRecipients, subject ?: "", body, priority)
            }
            
            val duration = System.currentTimeMillis() - startTime
            
            if (result.success) {
                logger.info("SendGrid email sent successfully to ${emailRecipients.size} recipients in ${duration}ms")
                recordMetrics("success", duration, emailRecipients.size, priority)
                
                ChannelDeliveryResult(
                    success = true,
                    details = mapOf(
                        "recipients" to emailRecipients.map { it.address },
                        "subject" to (subject ?: ""),
                        "provider" to "sendgrid",
                        "durationMs" to duration,
                        "messageId" to (result.details["messageId"] ?: "")
                    )
                )
            } else {
                logger.error("SendGrid email delivery failed: ${result.error}")
                recordMetrics("failure", duration, emailRecipients.size, priority)
                
                ChannelDeliveryResult(
                    success = false,
                    error = result.error,
                    details = result.details
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error("Unexpected error during SendGrid email delivery: ${e.message}", e)
            recordMetrics("error", duration, recipients.size, priority)
            
            ChannelDeliveryResult(
                false,
                "SendGrid email delivery failed: ${e.message}",
                details = mapOf(
                    "errorType" to e.javaClass.simpleName,
                    "durationMs" to duration
                )
            )
        }
    }
    
    /**
     * Record metrics for email sending
     */
    private fun recordMetrics(result: String, durationMs: Long, recipientCount: Int, priority: NotificationPriority) {
        meterRegistry?.let { registry ->
            val tags = listOf(
                Tag.of("result", result),
                Tag.of("provider", "sendgrid"),
                Tag.of("priority", priority.name)
            )
            
            // Count emails sent
            registry.counter("email.send.count", tags).increment()
            
            // Record duration
            registry.timer("email.send.duration", tags).record(durationMs, TimeUnit.MILLISECONDS)
            
            // Record recipient count
            registry.gauge("email.recipients", tags, recipientCount.toDouble())
        }
    }
    
    /**
     * Send email with retry logic
     */
    private suspend fun sendEmailWithRetry(
        recipients: List<NotificationRecipient>,
        subject: String,
        body: String,
        priority: NotificationPriority
    ): ChannelDeliveryResult {
        var lastException: Exception? = null
        
        for (attempt in 0 until maxRetries) {
            try {
                if (attempt > 0) {
                    logger.info("Retry attempt ${attempt + 1}/$maxRetries for sending SendGrid email")
                    // Exponential backoff with jitter
                    val delayMs = (baseDelayMs * Math.pow(2.0, attempt.toDouble())).toLong() +
                                 (Math.random() * baseDelayMs).toLong()
                    delay(delayMs)
                }
                
                val messageId = sendEmailWithSendGrid(recipients, subject, body, priority)
                return ChannelDeliveryResult(
                    success = true,
                    details = mapOf(
                        "attempt" to (attempt + 1),
                        "maxAttempts" to maxRetries,
                        "messageId" to messageId
                    )
                )
            } catch (e: Exception) {
                lastException = e
                logger.warn("SendGrid email sending failed on attempt ${attempt + 1}/$maxRetries: ${e.message}")
                
                // Don't retry for certain types of errors
                if (e.message?.contains("Invalid email address") == true) {
                    logger.error("Not retrying due to invalid address error: ${e.message}")
                    return ChannelDeliveryResult(
                        success = false,
                        error = "Invalid email address: ${e.message}",
                        details = mapOf("errorType" to "AddressError")
                    )
                }
            }
        }
        
        // All retries failed
        logger.error("SendGrid email sending failed after $maxRetries attempts")
        return ChannelDeliveryResult(
            success = false,
            error = "SendGrid email delivery failed after $maxRetries attempts: ${lastException?.message}",
            details = mapOf(
                "attempts" to maxRetries,
                "errorType" to (lastException?.javaClass?.simpleName ?: "Unknown")
            )
        )
    }
    
    private fun sendEmailWithSendGrid(
        recipients: List<NotificationRecipient>,
        subject: String,
        body: String,
        priority: NotificationPriority
    ): String {
        logger.debug("Preparing SendGrid email with subject '$subject' for ${recipients.size} recipients")
        
        val mail = com.sendgrid.Mail()
        
        // Generate message ID for tracking
        val messageId = "${System.currentTimeMillis()}.${Math.random()}@sendgrid.eden"
        mail.addHeader("Message-ID", messageId)
        
        // Set from address
        val from = com.sendgrid.Email(fromEmail, fromName)
        mail.setFrom(from)
        
        // Set subject
        mail.setSubject(subject)
        
        // Add recipients
        val validRecipients = mutableListOf<NotificationRecipient>()
        recipients.forEach { recipient ->
            try {
                // Validate email address format
                InternetAddress(recipient.address).validate()
                
                val to = com.sendgrid.Email(recipient.address, recipient.name)
                val personalization = com.sendgrid.Personalization()
                personalization.addTo(to)
                mail.addPersonalization(personalization)
                validRecipients.add(recipient)
            } catch (e: AddressException) {
                logger.warn("Invalid email address: ${recipient.address} - ${e.message}")
                // Skip invalid addresses
            }
        }
        
        if (validRecipients.isEmpty()) {
            logger.error("No valid email recipients found")
            throw Exception("No valid email recipients")
        }
        
        // Set content
        val content = com.sendgrid.Content("text/html", body)
        mail.addContent(content)
        
        // Add plain text version
        val plainText = com.sendgrid.Content("text/plain", stripHtml(body))
        mail.addContent(plainText)
        
        // Set priority headers
        when (priority) {
            NotificationPriority.HIGH, NotificationPriority.URGENT -> {
                mail.addHeader("X-Priority", "1")
                mail.addHeader("Importance", "high")
                mail.addHeader("Priority", "urgent")
                logger.debug("Setting email priority to HIGH/URGENT")
            }
            NotificationPriority.NORMAL -> {
                mail.addHeader("X-Priority", "3")
                mail.addHeader("Importance", "normal")
                mail.addHeader("Priority", "normal")
            }
            NotificationPriority.LOW -> {
                mail.addHeader("X-Priority", "5")
                mail.addHeader("Importance", "low")
                mail.addHeader("Priority", "non-urgent")
            }
        }
        
        // Add tracking settings
        val trackingSettings = com.sendgrid.TrackingSettings()
        val clickTracking = com.sendgrid.ClickTracking()
        clickTracking.setEnable(true)
        clickTracking.setEnableText(true)
        trackingSettings.setClickTracking(clickTracking)
        mail.setTrackingSettings(trackingSettings)
        
        // Send request
        val request = com.sendgrid.Request()
        request.method = com.sendgrid.Method.POST
        request.endpoint = "mail/send"
        request.body = mail.build()
        
        logger.debug("Sending email via SendGrid API")
        val response = client.api(request)
        
        // Check for errors
        if (response.statusCode >= 400) {
            logger.error("SendGrid API error: ${response.statusCode} - ${response.body}")
            throw Exception("SendGrid API error: ${response.statusCode} - ${response.body}")
        }
        
        logger.debug("SendGrid email sent successfully with message ID: $messageId")
        return messageId
    }
    
    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&apos;"), "'")
    }
    
    /**
     * Simple rate limiter for controlling email sending rate
     */
    private class RateLimiter(
        private val maxRequests: Int,
        private val windowMs: Long
    ) {
        private val requestTimestamps = ArrayDeque<Long>()
        private val lock = Any()
        
        /**
         * Try to acquire permits for sending emails
         * @param permits Number of permits to acquire (typically number of recipients)
         * @return true if permits were acquired, false if rate limit was exceeded
         */
        fun tryAcquire(permits: Int = 1): Boolean {
            synchronized(lock) {
                val now = System.currentTimeMillis()
                val windowStart = now - windowMs
                
                // Remove expired timestamps
                while (requestTimestamps.isNotEmpty() && requestTimestamps.first() <= windowStart) {
                    requestTimestamps.removeFirst()
                }
                
                // Check if we can acquire permits
                if (requestTimestamps.size + permits <= maxRequests) {
                    // Add timestamps for each permit
                    repeat(permits) {
                        requestTimestamps.addLast(now)
                    }
                    return true
                }
                
                return false
            }
        }
    }
}