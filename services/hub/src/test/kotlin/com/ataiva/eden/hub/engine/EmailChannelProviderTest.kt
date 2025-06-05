package com.ataiva.eden.hub.engine

import com.ataiva.eden.hub.model.*
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
        
        // Create provider with mocked session
        emailProvider = object : EmailChannelProvider(config) {
            override fun createMailSession(): Session {
                return mockSession
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
}

// Extension of EmailChannelProvider for testing
abstract class TestableEmailChannelProvider(config: EmailProviderConfig) : EmailChannelProvider(config) {
    abstract override fun createMailSession(): Session
}