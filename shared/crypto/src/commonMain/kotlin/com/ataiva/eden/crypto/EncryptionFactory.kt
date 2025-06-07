package com.ataiva.eden.crypto

/**
 * Factory for creating platform-specific encryption implementations
 */
object EncryptionFactory {
    /**
     * Create a platform-specific encryption implementation
     */
    fun createEncryption(keyDerivation: KeyDerivation? = null): Encryption {
        val actualKeyDerivation = keyDerivation ?: createKeyDerivation()
        
        // Platform-specific implementations will be registered at runtime
        // This is a simplified approach for multiplatform support
        return PlatformEncryptionProvider.createEncryption(actualKeyDerivation)
    }
    
    /**
     * Create a platform-specific key derivation implementation
     */
    fun createKeyDerivation(): KeyDerivation {
        // Platform-specific implementations will be registered at runtime
        return PlatformEncryptionProvider.createKeyDerivation()
    }
}

/**
 * Provider interface for platform-specific encryption implementations
 * Each platform registers its own implementation
 */
interface PlatformEncryptionProvider {
    fun createEncryption(keyDerivation: KeyDerivation): Encryption
    fun createKeyDerivation(): KeyDerivation
    
    companion object {
        private var instance: PlatformEncryptionProvider? = null
        
        fun register(provider: PlatformEncryptionProvider) {
            instance = provider
        }
        
        fun createEncryption(keyDerivation: KeyDerivation): Encryption {
            return instance?.createEncryption(keyDerivation)
                ?: throw IllegalStateException("No platform encryption provider registered")
        }
        
        fun createKeyDerivation(): KeyDerivation {
            return instance?.createKeyDerivation()
                ?: throw IllegalStateException("No platform encryption provider registered")
        }
    }
}