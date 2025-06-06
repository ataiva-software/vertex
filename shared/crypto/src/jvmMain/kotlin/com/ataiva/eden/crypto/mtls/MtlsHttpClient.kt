package com.ataiva.eden.crypto.mtls

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * HTTP client with mTLS support for secure service-to-service communication
 */
class MtlsHttpClient(
    private val mtlsConfig: MtlsConfig,
    private val trustAll: Boolean = false,
    private val timeout: Long = 30000,
    private val retries: Int = 3
) {
    /**
     * Create a Ktor HTTP client with mTLS configuration
     */
    fun createClient(): HttpClient {
        val sslContext = if (trustAll) {
            createTrustAllSslContext()
        } else {
            mtlsConfig.createClientSslContext()
        }
        
        return HttpClient(CIO) {
            engine {
                https {
                    // Use the configured SSL context
                    sslContext = sslContext
                    
                    // Verify hostname
                    trustManager = if (trustAll) {
                        mtlsConfig.createTrustAllManager()
                    } else {
                        null // Use the default trust manager from the SSL context
                    }
                }
                
                // Configure timeouts
                requestTimeout = timeout
                endpoint {
                    connectTimeout = 5000
                    connectAttempts = retries
                    keepAliveTime = 5000
                    maxConnectionsPerRoute = 100
                    maxConnectionsTotal = 1000
                    pipelineMaxSize = 20
                }
            }
            
            // Configure content negotiation
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            
            // Configure logging
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }
            
            // Configure default request headers
            defaultRequest {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                
                // Add service identification headers
                header("X-Service-Name", System.getenv("SERVICE_NAME") ?: "unknown")
                header("X-Service-Version", System.getenv("SERVICE_VERSION") ?: "unknown")
                header("X-Request-ID", java.util.UUID.randomUUID().toString())
            }
            
            // Configure HTTP client features
            install(HttpTimeout) {
                requestTimeoutMillis = timeout
                connectTimeoutMillis = 5000
                socketTimeoutMillis = timeout
            }
            
            // Configure retry behavior
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = retries)
                retryOnException(maxRetries = retries)
                exponentialDelay()
            }
        }
    }
    
    /**
     * Create an SSL context that trusts all certificates
     * WARNING: This should only be used for development/testing
     */
    private fun createTrustAllSslContext(): SSLContext {
        val trustManager = mtlsConfig.createTrustAllManager()
        
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        
        return sslContext
    }
    
    companion object {
        /**
         * Create an mTLS HTTP client from environment variables
         */
        fun fromEnvironment(trustAll: Boolean = false): HttpClient {
            val mtlsConfig = MtlsConfig.fromEnvironment()
            val timeout = System.getenv("HTTP_CLIENT_TIMEOUT")?.toLongOrNull() ?: 30000
            val retries = System.getenv("HTTP_CLIENT_RETRIES")?.toIntOrNull() ?: 3
            
            return MtlsHttpClient(
                mtlsConfig = mtlsConfig,
                trustAll = trustAll,
                timeout = timeout,
                retries = retries
            ).createClient()
        }
    }
}

/**
 * Extension function to create an mTLS HTTP client
 */
fun createMtlsHttpClient(
    keyStorePath: String,
    keyStorePassword: String,
    keyAlias: String,
    trustStorePath: String,
    trustStorePassword: String,
    trustAll: Boolean = false,
    timeout: Long = 30000,
    retries: Int = 3
): HttpClient {
    val mtlsConfig = MtlsConfig(
        keyStorePath = keyStorePath,
        keyStorePassword = keyStorePassword,
        keyAlias = keyAlias,
        trustStorePath = trustStorePath,
        trustStorePassword = trustStorePassword
    )
    
    return MtlsHttpClient(
        mtlsConfig = mtlsConfig,
        trustAll = trustAll,
        timeout = timeout,
        retries = retries
    ).createClient()
}