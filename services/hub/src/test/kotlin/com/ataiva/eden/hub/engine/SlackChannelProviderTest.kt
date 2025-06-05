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
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class SlackChannelProviderTest {

    private lateinit var slackProvider: SlackChannelProvider
    private lateinit var config: SlackProviderConfig
    
    @Mock
    private lateinit var httpClient: HttpClient
    
    @Mock
    private lateinit var httpResponse: HttpResponse<String>
    
    @Captor
    private lateinit var requestCaptor: ArgumentCaptor<HttpRequest>
    
    @BeforeEach
    fun setup() {
        // Create test configuration
        config = SlackProviderConfig(
            botToken = "xoxb-test-token",
            signingSecret = "test-signing-secret",
            appToken = "xapp-test-token"
        )
        
        // Create provider with mocked HTTP client
        slackProvider = object : SlackChannelProvider(config) {
            override fun createHttpClient(): HttpClient {
                return httpClient
            }
        }
    }
    
    @Test
    fun `sendNotification should return success when Slack API call succeeds`() = runBlocking {
        // Arrange
        val recipients = listOf(
            NotificationRecipient(
                type = RecipientType.SLACK_CHANNEL,
                address = "general",
                name = "General Channel"
            )
        )
        val subject = "Test Subject"
        val body = "This is a test message"
        val priority = NotificationPriority.NORMAL
        
        // Mock HTTP response
        `when`(httpResponse.statusCode()).thenReturn(200)
        `when`(httpResponse.body()).thenReturn("""{"ok":true,"channel":"C12345","ts":"1234567890.123456"}""")
        
        // Mock HTTP client to return the response
        `when`(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())).thenReturn(httpResponse)
        
        // Act
        val result = slackProvider.sendNotification(recipients, subject, body, priority)
        
        // Assert
        assertTrue(result.success)
        assertEquals(1, result.details["messageCount"])
        assertEquals("slack-api", result.details["provider"])
        
        // Verify HTTP request was sent
        verify(httpClient).send(requestCaptor.capture(), any<HttpResponse.BodyHandler<String>>())
        
        // Verify request properties
        val request = requestCaptor.value
        assertEquals("POST", request.method())
        assertEquals("https://slack.com/api/chat.postMessage", request.uri().toString())
        assertTrue(request.headers().firstValue("Authorization").isPresent)
        assertEquals("Bearer xoxb-test-token", request.headers().firstValue("Authorization").get())
    }
    
    @Test
    fun `sendNotification should include priority context for high priority messages`() = runBlocking {
        // Arrange
        val recipients = listOf(
            NotificationRecipient(
                type = RecipientType.SLACK_CHANNEL,
                address = "alerts",
                name = "Alerts Channel"
            )
        )
        val subject = "Urgent: System Alert"
        val body = "Critical system issue detected"
        val priority = NotificationPriority.URGENT
        
        // Mock HTTP response
        `when`(httpResponse.statusCode()).thenReturn(200)
        `when`(httpResponse.body()).thenReturn("""{"ok":true,"channel":"C12345","ts":"1234567890.123456"}""")
        
        // Mock HTTP client to return the response
        `when`(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())).thenReturn(httpResponse)
        
        // Act
        val result = slackProvider.sendNotification(recipients, subject, body, priority)
        
        // Assert
        assertTrue(result.success)
        
        // Verify HTTP request was sent
        verify(httpClient).send(requestCaptor.capture(), any<HttpResponse.BodyHandler<String>>())
        
        // Verify request body contains priority context
        val requestBody = String(requestCaptor.value.bodyPublisher().get().contentLength().toInt().let { size ->
            val buffer = ByteArray(size)
            val subscriber = object : java.util.concurrent.Flow.Subscriber<ByteBuffer> {
                override fun onSubscribe(subscription: java.util.concurrent.Flow.Subscription) {
                    subscription.request(Long.MAX_VALUE)
                }
                override fun onNext(item: ByteBuffer) {
                    item.get(buffer)
                }
                override fun onError(throwable: Throwable) {}
                override fun onComplete() {}
            }
            requestCaptor.value.bodyPublisher().get().subscribe(subscriber)
            buffer
        })
        
        assertTrue(requestBody.contains("URGENT"))
        assertTrue(requestBody.contains(":rotating_light:"))
    }
    
    @Test
    fun `sendNotification should return failure when no Slack recipients`() = runBlocking {
        // Arrange
        val recipients = listOf(
            NotificationRecipient(
                type = RecipientType.EMAIL,
                address = "user@example.com",
                name = "Test User"
            )
        )
        val subject = "Test Subject"
        val body = "This is a test message"
        val priority = NotificationPriority.NORMAL
        
        // Act
        val result = slackProvider.sendNotification(recipients, subject, body, priority)
        
        // Assert
        assertFalse(result.success)
        assertEquals("No Slack recipients found", result.error)
        
        // Verify no HTTP request was sent
        verifyNoInteractions(httpClient)
    }
    
    @Test
    fun `sendNotification should return failure when Slack API returns error`() = runBlocking {
        // Arrange
        val recipients = listOf(
            NotificationRecipient(
                type = RecipientType.SLACK_CHANNEL,
                address = "invalid-channel",
                name = "Invalid Channel"
            )
        )
        val subject = "Test Subject"
        val body = "This is a test message"
        val priority = NotificationPriority.NORMAL
        
        // Mock HTTP response with error
        `when`(httpResponse.statusCode()).thenReturn(200) // Slack returns 200 even for errors
        `when`(httpResponse.body()).thenReturn("""{"ok":false,"error":"channel_not_found"}""")
        
        // Mock HTTP client to return the response
        `when`(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())).thenReturn(httpResponse)
        
        // Act
        val result = slackProvider.sendNotification(recipients, subject, body, priority)
        
        // Assert
        assertFalse(result.success)
        assertTrue(result.error?.contains("Failed to send messages") == true)
        
        // Verify HTTP request was sent
        verify(httpClient).send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())
    }
    
    @Test
    fun `sendNotification should return failure when HTTP client throws exception`() = runBlocking {
        // Arrange
        val recipients = listOf(
            NotificationRecipient(
                type = RecipientType.SLACK_CHANNEL,
                address = "general",
                name = "General Channel"
            )
        )
        val subject = "Test Subject"
        val body = "This is a test message"
        val priority = NotificationPriority.NORMAL
        
        // Mock HTTP client to throw exception
        `when`(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>()))
            .thenThrow(RuntimeException("Connection failed"))
        
        // Act
        val result = slackProvider.sendNotification(recipients, subject, body, priority)
        
        // Assert
        assertFalse(result.success)
        assertTrue(result.error?.contains("Slack delivery failed") == true)
        
        // Verify HTTP request was attempted
        verify(httpClient).send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())
    }
}