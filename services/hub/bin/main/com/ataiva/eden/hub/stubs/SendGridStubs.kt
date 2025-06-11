package com.ataiva.eden.hub.stubs

import org.slf4j.LoggerFactory

/**
 * Stub implementation of SendGrid classes for development/testing
 */
class SendGrid(private val apiKey: String) {
    private val logger = LoggerFactory.getLogger(SendGrid::class.java)
    
    fun api(request: Request): Response {
        logger.info("SendGrid API stub called with endpoint: ${request.endpoint}")
        return Response(200, "OK", mapOf("message" to "Success"))
    }
}

class Request {
    var method: Method = Method.GET
    var endpoint: String = ""
    var body: String = ""
}

enum class Method {
    GET, POST, PUT, DELETE
}

class Response(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap()
)

class Mail {
    private val headers = mutableMapOf<String, String>()
    private val personalizations = mutableListOf<Personalization>()
    private val contents = mutableListOf<Content>()
    private var from: Email? = null
    private var subject: String = ""
    private var trackingSettings: TrackingSettings? = null
    
    fun addHeader(key: String, value: String) {
        headers[key] = value
    }
    
    fun setFrom(email: Email) {
        this.from = email
    }
    
    fun setSubject(subject: String) {
        this.subject = subject
    }
    
    fun addPersonalization(personalization: Personalization) {
        personalizations.add(personalization)
    }
    
    fun addContent(content: Content) {
        contents.add(content)
    }
    
    fun setTrackingSettings(trackingSettings: TrackingSettings) {
        this.trackingSettings = trackingSettings
    }
    
    fun build(): String {
        // In a real implementation, this would serialize to JSON
        return "{\"personalizations\":${personalizations.size},\"contents\":${contents.size}}"
    }
}

class Email(val email: String, val name: String? = null)

class Personalization {
    private val to = mutableListOf<Email>()
    
    fun addTo(email: Email) {
        to.add(email)
    }
}

class Content(val type: String, val value: String)

class TrackingSettings {
    private var clickTracking: ClickTracking? = null
    
    fun setClickTracking(clickTracking: ClickTracking) {
        this.clickTracking = clickTracking
    }
}

class ClickTracking {
    private var enable: Boolean = false
    private var enableText: Boolean = false
    
    fun setEnable(enable: Boolean) {
        this.enable = enable
    }
    
    fun setEnableText(enableText: Boolean) {
        this.enableText = enableText
    }
}