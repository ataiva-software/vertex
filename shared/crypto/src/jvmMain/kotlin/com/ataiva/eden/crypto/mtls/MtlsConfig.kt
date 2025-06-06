package com.ataiva.eden.crypto.mtls

import io.ktor.network.tls.certificates.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * mTLS configuration for Eden services
 * Provides mutual TLS authentication between services
 */
class MtlsConfig(
    private val keyStorePath: String,
    private val keyStorePassword: String,
    private val keyAlias: String,
    private val trustStorePath: String,
    private val trustStorePassword: String,
    private val generateSelfSignedOnMissing: Boolean = false
) {
    /**
     * Configure Ktor server engine with mTLS
     */
    fun configureServer(builder: NettyApplicationEngine.Configuration) {
        val keyStore = loadKeyStore(keyStorePath, keyStorePassword, generateSelfSignedOnMissing)
        val trustStore = loadKeyStore(trustStorePath, trustStorePassword, false)
        
        builder.sslConnector(
            keyStore = keyStore,
            keyAlias = keyAlias,
            keyStorePassword = { keyStorePassword.toCharArray() },
            trustStore = trustStore,
            trustStorePassword = { trustStorePassword.toCharArray() }
        ) {
            port = 8443
            keyStorePath = File(this@MtlsConfig.keyStorePath)
            keyStorePassword = this@MtlsConfig.keyStorePassword
            
            // Require client authentication
            needClientAuth = true
        }
    }
    
    /**
     * Create SSL context for client connections
     */
    fun createClientSslContext(): SSLContext {
        val keyStore = loadKeyStore(keyStorePath, keyStorePassword, generateSelfSignedOnMissing)
        val trustStore = loadKeyStore(trustStorePath, trustStorePassword, false)
        
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray())
        
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(trustStore)
        
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(
            keyManagerFactory.keyManagers,
            trustManagerFactory.trustManagers,
            null
        )
        
        return sslContext
    }
    
    /**
     * Load a KeyStore from a file, generating a self-signed certificate if needed
     */
    private fun loadKeyStore(path: String, password: String, generateIfMissing: Boolean): KeyStore {
        val file = File(path)
        
        if (!file.exists() && generateIfMissing) {
            generateSelfSignedCertificate(file, password)
        }
        
        if (!file.exists()) {
            throw IllegalArgumentException("KeyStore file not found: $path")
        }
        
        val keyStore = KeyStore.getInstance("JKS")
        file.inputStream().use { stream ->
            keyStore.load(stream, password.toCharArray())
        }
        
        return keyStore
    }
    
    /**
     * Generate a self-signed certificate
     */
    private fun generateSelfSignedCertificate(file: File, password: String) {
        file.parentFile?.mkdirs()
        
        val keyStore = buildKeyStore {
            certificate(keyAlias) {
                hash = HashAlgorithm.SHA256
                sign = SignatureAlgorithm.RSA
                keySizeInBits = 2048
                daysValid = 365
                organization = "Eden DevOps Suite"
                organizationUnit = "Security"
                commonName = "eden.ataiva.com"
                domains = listOf("localhost", "*.eden.ataiva.com")
            }
            password = password
        }
        
        keyStore.saveToFile(file, password)
    }
    
    /**
     * Create a trust manager that trusts all certificates
     * WARNING: This should only be used for development/testing
     */
    fun createTrustAllManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                // Trust all clients
            }
            
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                // Trust all servers
            }
            
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return emptyArray()
            }
        }
    }
    
    companion object {
        /**
         * Create mTLS config from environment variables
         */
        fun fromEnvironment(): MtlsConfig {
            val keyStorePath = System.getenv("MTLS_KEYSTORE_PATH") ?: "certs/keystore.jks"
            val keyStorePassword = System.getenv("MTLS_KEYSTORE_PASSWORD") ?: "changeit"
            val keyAlias = System.getenv("MTLS_KEY_ALIAS") ?: "eden"
            val trustStorePath = System.getenv("MTLS_TRUSTSTORE_PATH") ?: "certs/truststore.jks"
            val trustStorePassword = System.getenv("MTLS_TRUSTSTORE_PASSWORD") ?: "changeit"
            val generateSelfSigned = System.getenv("MTLS_GENERATE_SELF_SIGNED")?.toBoolean() ?: false
            
            return MtlsConfig(
                keyStorePath = keyStorePath,
                keyStorePassword = keyStorePassword,
                keyAlias = keyAlias,
                trustStorePath = trustStorePath,
                trustStorePassword = trustStorePassword,
                generateSelfSignedOnMissing = generateSelfSigned
            )
        }
    }
}