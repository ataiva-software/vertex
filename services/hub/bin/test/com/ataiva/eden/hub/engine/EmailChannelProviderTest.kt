package com.ataiva.eden.hub.engine

import com.ataiva.eden.hub.model.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class EmailChannelProviderTest {

    private lateinit var emailProvider: EmailChannelProvider
    private lateinit var config: EmailProviderConfig
    private lateinit var meterRegistry: MeterRegistry
    
    @Mock
    private lateinit var mockSession: Session
    
    @Captor
    private lateinit var messageCaptor: ArgumentCaptor<MimeMessage>
    
    @BeforeEach
    fun setup() {
        // Create test configuration
        config = EmailProviderConfig(
            provider = "smtp",
            host = "smtp.example.com",
            port = "587",
            username = "test@example.com",
            password = "password123",
            fromEmail = "notifications@example.com",
            fromName = "Test Notifications"
        )
        
        // Create simple meter registry for testing
        meterRegistry = SimpleMeterRegistry()
        
        // Create provider with mocked session
        emailProvider = object : EmailChannelProvider(config, meterRegistry) {
            override fun createMailSession(): Session {
                return mockSession
            }
            
            // Override retry logic for testing
            override suspend fun sendEmailWithRetry(
                recipients: List<NotificationRecipient>,
                subject: String,
                body: String,
                priority: NotificationPriority
            ): ChannelDeliveryResult {
                // Just call sendEmail directly without retries for testing
                sendEmail(recipients, subject, body, priority)
                return ChannelDeliveryResult(
                    success = true,
                    details = mapOf("attempt" to 1, "maxAttempts" to 3)
                )
            }
        }
        
        // Mock static Transport class
        mockStatic(Transport::class.java).use { mockedTransport ->
            // No setup needed here, we'll verify in each test
        }
    }
    
    @Test
    fun `sendNotification should return success when email is sent successfully`() = runBlocking {
        // Arrange
        val recipients = listOf(
            NotificationRecipient(
                type = RecipientType.EMAIL,
                address = "user@example.com",
                name = "Test User"
            )
        )
        val subject = "Test Subject"
        val body = "<p>This is a test email</p>"
        val priority = NotificationPriority.NORMAL
        
        // Mock Transport.send to do nothing (success)
        mockStatic(Transport::class.java).use { mockedTransport ->
            mockedTransport.`when`<Unit> { 
                Transport.send(any()) 
            }.then { invocation ->
                // Capture the message for verification
                messageCaptor.value = invocation.getArgument(0)
            }
            
            // Act
            val result = emailProvider.sendNotification(recipients, subject, body, priority)
            
            // Assert
            assertTrue(result.success)
            assertEquals(1, result.details["recipients"]?.toString()?.count { it == '@' })
            assertEquals("smtp", result.details["provider"])
            
            // Verify Transport.send was called
            mockedTransport.verify { Transport.send(any()) }
        }
    }
    
    @Test
    fun `sendNotification should set correct headers for high priority`() = runBlocking {
        // Arrange
        val recipients = listOf(
            NotificationRecipient(
                type = RecipientType.EMAIL,
                address = "user@example.com",
                name = "Test User"
            )
        )
        val subject = "Urgent: Test Subject"
        val body = "<p>This is an urgent test email</p>"
        val priority = NotificationPriority.HIGH
        
        // Mock Transport.send to do nothing (success)
        mockStatic(Transport::class.java).use { mockedTransport ->
            mockedTransport.`when`<Unit> { 
                Transport.send(any()) 
            }.then { invocation ->
                // Capture the message for verification
                messageCaptor.value = invocation.getArgument(0)
            }
            
            // Act
            val result = emailProvider.sendNotification(recipients, subject, body, priority)
            
            // Assert
            assertTrue(result.success)
            
            // Verify Transport.send was called with correct headers
            mockedTransport.verify { Transport.send(any()) }
            
            // Verify the message had high priority headers
            val message = messageCaptor.value
            assertEquals("1", message.getHeader("X-Priority")?.get(0))
            assertEquals("high", message.getHeader("Importance")?.get(0))
            assertEquals("urgent", message.getHeader("Priority")?.get(0))
        }
    }
    
    @Test
    fun `sendNotification should return failure when no email recipients`() = runBlocking {
        // Arrange
        val recipients = listOf(
            NotificationRecipient(
                type = RecipientType.SLACK_CHANNEL,
                address = "general",
                name = "General Channel"
            )
        )
        val subject = "Test Subject"
        val body = "<p>This is a test email</p>"
        val priority = NotificationPriority.NORMAL
        
        // Act
        val result = emailProvider.sendNotification(recipients, subject, body, priority)
        
        // Assert
        assertFalse(result.success)
        assertEquals("No email recipients found", result.error)
        
        // Verify Transport.send was not called
        mockStatic(Transport::class.java).use { mockedTransport ->
            mockedTransport.verifyNoInteractions()
        }
    }
    
    @Test
    fun `sendNotification should return failure when Transport throws exception`() = runBlocking {
        // Arrange
        val recipients = listOf(
            NotificationRecipient(
                type = RecipientType.EMAIL,
                address = "user@example.com",
                name = "Test User"
            )
        )
        val subject = "Test Subject"
        val body = "<p>This is a test email</p>"
        val priority = NotificationPriority.NORMAL
        
        // Mock Transport.send to throw exception
        mockStatic(Transport::class.java).use { mockedTransport ->
            mockedTransport.`when`<Unit> { 
                Transport.send(any()) 
            }.thenThrow(RuntimeException("SMTP server connection failed"))
            
            // Act
            val result = emailProvider.sendNotification(recipients, subject, body, priority)
            
            // Assert
            assertFalse(result.success)
            assertTrue(result.error?.contains("Email delivery failed") == true)
            
            // Verify Transport.send was called
            mockedTransport.verify { Transport.send(any()) }
        }
    }
    @Test
    fun `sendNotification should record metrics on success`() = runBlocking {
        // Arrange
        val recipients = listOf(
            NotificationRecipient(
                type = RecipientType.EMAIL,
                address = "user@example.com",
                name = "Test User"
            )
        )
        val subject = "Test Subject"
        val body = "<p>This is a test email</p>"
        val priority = NotificationPriority.NORMAL
        
        // Mock Transport.send to do nothing (success)
        mockStatic(Transport::class.java).use { mockedTransport ->
            mockedTransport.`when`<Unit> {
                Transport.send(any())
            }.then { invocation ->
                // Capture the message for verification
                messageCaptor.value = invocation.getArgument(0)
            }
            
            // Act
            val result = emailProvider.sendNotification(recipients, subject, body, priority)
            
            // Assert
            assertTrue(result.success)
            
            // Verify metrics were recorded
            val counter = meterRegistry.find("email.send.count").counter()
            assertNotNull(counter, "Email send count metric should be recorded")
            assertEquals(1.0, counter?.count())
            
            val timer = meterRegistry.find("email.send.duration").timer()
            assertNotNull(timer, "Email send duration metric should be recorded")
            assertEquals(1, timer?.count())
        }
    }
    
    @Test
    fun `sendNotification should handle retry logic on failure`() = runBlocking {
        // Create a provider that will test the retry logic
        val retryProvider = object : EmailChannelProvider(config, meterRegistry) {
            override fun createMailSession(): Session {
                return mockSession
            }
            
            // Override for testing - simulate a failure then success
            var attempts = 0
            override suspend fun sendEmail(
                recipients: List<NotificationRecipient>,
                subject: String,
                body: String,
                priority: NotificationPriority
            ) {
                attempts++
                if (attempts == 1) {
                    throw MessagingException("First attempt failed")
                }
                // Second attempt succeeds
            }
        }
        
        // Arrange
        val recipients = listOf(
            NotificationRecipient(
                type = RecipientType.EMAIL,
                address = "user@example.com",
                name = "Test User"
            )
        )
        val subject = "Test Subject"
        val body = "<p>This is a test email</p>"
        val priority = NotificationPriority.NORMAL
        
        // Act
        val result = retryProvider.sendNotification(recipients, subject, body, priority)
        
        // Assert
        assertTrue(result.success)
        assertEquals(2, retryProvider.attempts)
    }
}

// Helper function for null assertions
private fun assertNotNull(value: Any?, message: String) {
    assertTrue(value != null, message)
}