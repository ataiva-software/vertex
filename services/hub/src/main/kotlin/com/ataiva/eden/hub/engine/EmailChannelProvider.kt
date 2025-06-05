package com.ataiva.eden.hub.engine

import com.ataiva.eden.hub.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/**
 * Email channel provider using SMTP
 */
class EmailChannelProvider(
    private val config: EmailProviderConfig
) : NotificationChannelProvider {
    
    override suspend fun sendNotification(
        recipients: List<NotificationRecipient>,
        subject: String?,
        body: String,
        priority: NotificationPriority
    ): ChannelDeliveryResult {
        return try {
            val emailRecipients = recipients.filter { it.type == RecipientType.EMAIL }
            if (emailRecipients.isEmpty()) {
                return ChannelDeliveryResult(false, "No email recipients found")
            }
            
            // Send email using JavaMail API
            withContext(Dispatchers.IO) {
                sendEmail(emailRecipients, subject ?: "", body, priority)
            }
            
            ChannelDeliveryResult(
                success = true,
                details = mapOf(
                    "recipients" to emailRecipients.map { it.address },
                    "subject" to (subject ?: ""),
                    "provider" to config.provider
                )
            )
        } catch (e: Exception) {
            ChannelDeliveryResult(false, "Email delivery failed: ${e.message}")
        }
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
        // Create session
        val session = createMailSession()
        
        // Create message
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(config.fromEmail, config.fromName))
        
        // Add recipients
        recipients.forEach { recipient ->
            val address = InternetAddress(recipient.address, recipient.name)
            message.addRecipient(Message.RecipientType.TO, address)
        }
        
        // Set subject
        message.subject = subject
        
        // Set priority headers based on notification priority
        when (priority) {
            NotificationPriority.HIGH, NotificationPriority.URGENT -> {
                message.setHeader("X-Priority", "1")
                message.setHeader("Importance", "high")
                message.setHeader("Priority", "urgent")
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
        Transport.send(message)
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
    private val fromName: String
) : NotificationChannelProvider {
    
    private val client = com.sendgrid.SendGrid(apiKey)
    
    override suspend fun sendNotification(
        recipients: List<NotificationRecipient>,
        subject: String?,
        body: String,
        priority: NotificationPriority
    ): ChannelDeliveryResult {
        return try {
            val emailRecipients = recipients.filter { it.type == RecipientType.EMAIL }
            if (emailRecipients.isEmpty()) {
                return ChannelDeliveryResult(false, "No email recipients found")
            }
            
            // Send email using SendGrid API
            withContext(Dispatchers.IO) {
                sendEmailWithSendGrid(emailRecipients, subject ?: "", body, priority)
            }
            
            ChannelDeliveryResult(
                success = true,
                details = mapOf(
                    "recipients" to emailRecipients.map { it.address },
                    "subject" to (subject ?: ""),
                    "provider" to "sendgrid"
                )
            )
        } catch (e: Exception) {
            ChannelDeliveryResult(false, "Email delivery failed: ${e.message}")
        }
    }
    
    private fun sendEmailWithSendGrid(
        recipients: List<NotificationRecipient>,
        subject: String,
        body: String,
        priority: NotificationPriority
    ) {
        val mail = com.sendgrid.Mail()
        
        // Set from address
        val from = com.sendgrid.Email(fromEmail, fromName)
        mail.setFrom(from)
        
        // Set subject
        mail.setSubject(subject)
        
        // Add recipients
        recipients.forEach { recipient ->
            val to = com.sendgrid.Email(recipient.address, recipient.name)
            val personalization = com.sendgrid.Personalization()
            personalization.addTo(to)
            mail.addPersonalization(personalization)
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
        
        // Send request
        val request = com.sendgrid.Request()
        request.method = com.sendgrid.Method.POST
        request.endpoint = "mail/send"
        request.body = mail.build()
        
        val response = client.api(request)
        
        // Check for errors
        if (response.statusCode >= 400) {
            throw Exception("SendGrid API error: ${response.statusCode} - ${response.body}")
        }
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