package com.ataiva.eden.crypto

/**
 * Factory for creating cryptographic service instances
 * This provides a centralized way to obtain implementations of the various
 * cryptographic interfaces used throughout the application.
 */
object CryptoFactory {
    /**
     * Create an instance of the Encryption interface
     */
    fun createEncryption(): Encryption {
        // Platform-specific implementation will be provided
        return createPlatformEncryption()
    }
    
    /**
     * Create an instance of the KeyDerivation interface
     */
    fun createKeyDerivation(): KeyDerivation {
        // Platform-specific implementation will be provided
        return createPlatformEncryption() as KeyDerivation
    }
    
    /**
     * Create an instance of the ZeroKnowledgeEncryption interface
     */
    fun createZeroKnowledgeEncryption(): ZeroKnowledgeEncryption {
        // Platform-specific implementation will be provided
        return createPlatformEncryption() as ZeroKnowledgeEncryption
    }
    
    /**
     * Create an instance of the DigitalSignature interface
     */
    fun createDigitalSignature(): DigitalSignature {
        // Platform-specific implementation will be provided
        return createPlatformDigitalSignature()
    }
    
    /**
     * Create an instance of the SecureRandom interface
     */
    fun createSecureRandom(): SecureRandom {
        // Platform-specific implementation will be provided
        return createPlatformSecureRandom()
    }
    
    /**
     * Create a platform-specific encryption implementation
     * This is implemented in the platform-specific source sets
     */
    internal fun createPlatformEncryption(): Encryption {
        // This will be overridden in platform-specific implementations
        throw UnsupportedOperationException("Platform-specific implementation required")
    }
    
    /**
     * Create a platform-specific digital signature implementation
     * This is implemented in the platform-specific source sets
     */
    internal fun createPlatformDigitalSignature(): DigitalSignature {
        // This will be overridden in platform-specific implementations
        throw UnsupportedOperationException("Platform-specific implementation required")
    }
    
    /**
     * Create a platform-specific secure random implementation
     * This is implemented in the platform-specific source sets
     */
    internal fun createPlatformSecureRandom(): SecureRandom {
        // This will be overridden in platform-specific implementations
        throw UnsupportedOperationException("Platform-specific implementation required")
    }
}