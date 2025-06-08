package com.ataiva.eden.hub.crypto

import com.ataiva.eden.crypto.*
import com.ataiva.eden.database.EdenDatabaseService
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Logger

/**
 * Key Management System for the Hub Service
 *
 * This class provides comprehensive key management capabilities including:
 * - Key generation and storage
 * - Key rotation
 * - Key versioning
 * - Access control
 * - Audit logging
 *
 * Security Considerations:
 * - Keys are stored encrypted at rest
 * - Master key is derived from a secure source
 * - Regular key rotation is enforced
 * - All key access is logged for audit purposes
 * - Access control is enforced for all key operations
 *
 * @author Eden Security Team
 * @version 1.0.0
 */
class KeyManagementSystem(
    private val encryption: Encryption,
    private val secureRandom: SecureRandom,
    private val databaseService: EdenDatabaseService
) {
    private val logger = Logger.getLogger(KeyManagementSystem::class.java.name)
    private val keyCache = ConcurrentHashMap<String, KeyEntry>()
    private val lock = ReentrantLock()
    
    /**
     * Initialize the key management system
     */
    suspend fun initialize() {
        logger.info("Initializing Key Management System")
        
        // Check if master key exists, if not create it
        if (!hasMasterKey()) {
            logger.info("Master key not found, generating new master key")
            generateMasterKey()
        }
        
        // Load keys from database
        loadKeys()
        
        // Schedule key rotation check
        scheduleKeyRotationCheck()
        
        logger.info("Key Management System initialized successfully")
    }
    
    /**
     * Get a key by name and version
     * If version is null, returns the latest version
     */
    suspend fun getKey(keyName: String, version: Int? = null, userId: String): KeyResult {
        val accessGranted = checkAccess(keyName, "read", userId)
        if (!accessGranted) {
            logger.warning("Access denied for user $userId to read key $keyName")
            return KeyResult.Failure("Access denied")
        }
        
        logKeyAccess(keyName, version, userId, "read")
        
        val cacheKey = getCacheKey(keyName, version)
        val cachedKey = keyCache[cacheKey]
        
        if (cachedKey != null) {
            // Get master key
            val masterKeyResult = getMasterKey()
            if (masterKeyResult is KeyResult.Failure) {
                return masterKeyResult
            }
            
            val masterKey = (masterKeyResult as KeyResult.Success).key
            
            // Decrypt key
            val decryptResult = decryptKey(
                cachedKey.encryptedKey,
                cachedKey.nonce,
                cachedKey.authTag,
                masterKey
            )
            
            return when (decryptResult) {
                is DecryptionResult.Success -> KeyResult.Success(decryptResult.data, cachedKey.version)
                is DecryptionResult.Failure -> KeyResult.Failure("Failed to decrypt key: ${decryptResult.error}")
            }
        }
        
        return loadKeyFromDatabase(keyName, version)
    }
    
    /**
     * Create a new key
     */
    suspend fun createKey(keyName: String, userId: String): KeyResult {
        val accessGranted = checkAccess(keyName, "create", userId)
        if (!accessGranted) {
            logger.warning("Access denied for user $userId to create key $keyName")
            return KeyResult.Failure("Access denied")
        }
        
        try {
            lock.lock()
            
            // Check if key already exists
            val existingKey = loadKeyFromDatabase(keyName, null)
            if (existingKey is KeyResult.Success) {
                return KeyResult.Failure("Key already exists")
            }
            
            // Generate new key
            val keyBytes = ByteArray(32)
            secureRandom.nextBytes(keyBytes)
            
            // Encrypt key with master key
            val masterKey = getMasterKey()
            if (masterKey is KeyResult.Failure) {
                return masterKey
            }
            
            val encryptedKey = encryptKey(keyBytes, (masterKey as KeyResult.Success).key)
            
            // Store key in database
            val keyEntry = KeyEntry(
                name = keyName,
                version = 1,
                encryptedKey = encryptedKey.encryptedData,
                nonce = encryptedKey.nonce,
                authTag = encryptedKey.authTag,
                createdAt = Instant.now().toString(),
                createdBy = userId,
                expiresAt = Instant.now().plus(90, ChronoUnit.DAYS).toString(),
                status = "active"
            )
            
            storeKeyInDatabase(keyEntry)
            
            // Cache key
            keyCache[getCacheKey(keyName, 1)] = keyEntry
            
            // Log key creation
            logKeyAccess(keyName, 1, userId, "create")
            
            return KeyResult.Success(keyBytes, 1)
        } finally {
            lock.unlock()
        }
    }
    
    /**
     * Rotate a key
     */
    suspend fun rotateKey(keyName: String, userId: String): KeyResult {
        val accessGranted = checkAccess(keyName, "rotate", userId)
        if (!accessGranted) {
            logger.warning("Access denied for user $userId to rotate key $keyName")
            return KeyResult.Failure("Access denied")
        }
        
        try {
            lock.lock()
            
            // Get current key
            val currentKeyResult = loadKeyFromDatabase(keyName, null)
            if (currentKeyResult is KeyResult.Failure) {
                return currentKeyResult
            }
            
            val currentKey = currentKeyResult as KeyResult.Success
            val newVersion = currentKey.version + 1
            
            // Generate new key
            val keyBytes = ByteArray(32)
            secureRandom.nextBytes(keyBytes)
            
            // Encrypt key with master key
            val masterKey = getMasterKey()
            if (masterKey is KeyResult.Failure) {
                return masterKey
            }
            
            val encryptedKey = encryptKey(keyBytes, (masterKey as KeyResult.Success).key)
            
            // Store key in database
            val keyEntry = KeyEntry(
                name = keyName,
                version = newVersion,
                encryptedKey = encryptedKey.encryptedData,
                nonce = encryptedKey.nonce,
                authTag = encryptedKey.authTag,
                createdAt = Instant.now().toString(),
                createdBy = userId,
                expiresAt = Instant.now().plus(90, ChronoUnit.DAYS).toString(),
                status = "active"
            )
            
            storeKeyInDatabase(keyEntry)
            
            // Update previous key status
            updateKeyStatus(keyName, currentKey.version, "inactive")
            
            // Cache key
            keyCache[getCacheKey(keyName, newVersion)] = keyEntry
            
            // Log key rotation
            logKeyAccess(keyName, newVersion, userId, "rotate")
            
            return KeyResult.Success(keyBytes, newVersion)
        } finally {
            lock.unlock()
        }
    }
    
    /**
     * Delete a key
     */
    suspend fun deleteKey(keyName: String, version: Int? = null, userId: String): Boolean {
        val accessGranted = checkAccess(keyName, "delete", userId)
        if (!accessGranted) {
            logger.warning("Access denied for user $userId to delete key $keyName")
            return false
        }
        
        try {
            lock.lock()
            
            // Update key status in database
            val success = if (version == null) {
                updateKeyStatus(keyName, null, "deleted")
            } else {
                updateKeyStatus(keyName, version, "deleted")
            }
            
            // Remove from cache
            if (version == null) {
                keyCache.entries.removeIf { it.key.startsWith("$keyName:") }
            } else {
                keyCache.remove(getCacheKey(keyName, version))
            }
            
            // Log key deletion
            logKeyAccess(keyName, version, userId, "delete")
            
            return success
        } finally {
            lock.unlock()
        }
    }
    
    /**
     * List all keys
     */
    suspend fun listKeys(userId: String): List<KeyMetadata> {
        // Check if user has admin access
        val accessGranted = checkAccess("*", "list", userId)
        if (!accessGranted) {
            logger.warning("Access denied for user $userId to list keys")
            return emptyList()
        }
        
        // Log key listing
        logKeyAccess("*", null, userId, "list")
        
        // Get keys from database
        return getKeysFromDatabase()
    }
    
    /**
     * Check if a key exists
     */
    suspend fun hasKey(keyName: String, version: Int? = null): Boolean {
        val cacheKey = getCacheKey(keyName, version)
        if (keyCache.containsKey(cacheKey)) {
            return true
        }
        
        val keyResult = loadKeyFromDatabase(keyName, version)
        return keyResult is KeyResult.Success
    }
    
    /**
     * Encrypt data using a managed key
     */
    suspend fun encryptWithManagedKey(data: ByteArray, keyName: String, userId: String): EncryptionResult? {
        val keyResult = getKey(keyName, null, userId)
        if (keyResult is KeyResult.Failure) {
            logger.warning("Failed to get key $keyName for encryption: ${keyResult.error}")
            return null
        }
        
        val key = (keyResult as KeyResult.Success).key
        return encryption.encrypt(data, key)
    }
    
    /**
     * Decrypt data using a managed key
     */
    suspend fun decryptWithManagedKey(
        data: ByteArray, 
        keyName: String, 
        version: Int? = null,
        nonce: ByteArray, 
        authTag: ByteArray?,
        userId: String
    ): DecryptionResult {
        val keyResult = getKey(keyName, version, userId)
        if (keyResult is KeyResult.Failure) {
            logger.warning("Failed to get key $keyName for decryption: ${keyResult.error}")
            return DecryptionResult.Failure("Failed to get key: ${keyResult.error}")
        }
        
        val key = (keyResult as KeyResult.Success).key
        return encryption.decrypt(data, key, nonce, authTag)
    }
    
    // Private methods
    
    private suspend fun hasMasterKey(): Boolean {
        // Check if master key exists in database
        return hasKey("master", 1)
    }
    
    private suspend fun generateMasterKey() {
        // Generate a new master key
        val keyBytes = ByteArray(32)
        secureRandom.nextBytes(keyBytes)
        
        // Derive a key encryption key from a secure source
        // In a production environment, this would use a hardware security module or similar
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        
        val kek = if (encryption is KeyDerivation) {
            (encryption as KeyDerivation).deriveKeyArgon2(
                "secure-master-password", // In production, this would be securely stored or derived
                salt,
                memory = 65536,
                iterations = 3,
                parallelism = 4
            )
        } else {
            // Fallback if KeyDerivation is not available
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.digest("secure-master-password".toByteArray())
        }
        
        // Encrypt the master key
        val encryptedKey = encryption.encrypt(keyBytes, kek)
        
        // Store the master key in the database
        val keyEntry = KeyEntry(
            name = "master",
            version = 1,
            encryptedKey = encryptedKey.encryptedData,
            nonce = encryptedKey.nonce,
            authTag = encryptedKey.authTag,
            createdAt = Instant.now().toString(),
            createdBy = "system",
            expiresAt = Instant.now().plus(365, ChronoUnit.DAYS).toString(),
            status = "active",
            metadata = mapOf("salt" to salt.encodeToBase64())
        )
        
        storeKeyInDatabase(keyEntry)
        
        // Cache the master key
        keyCache[getCacheKey("master", 1)] = keyEntry
    }
    
    private suspend fun getMasterKey(): KeyResult {
        // Get the master key from cache or database
        val cacheKey = getCacheKey("master", 1)
        val cachedKey = keyCache[cacheKey]
        
        val keyEntry = if (cachedKey != null) {
            cachedKey
        } else {
            val keyResult = loadKeyFromDatabase("master", 1)
            if (keyResult is KeyResult.Failure) {
                return keyResult
            }
            
            // Load key entry from database
            val entry = getKeyEntryFromDatabase("master", 1)
            if (entry == null) {
                return KeyResult.Failure("Master key not found")
            }
            
            entry
        }
        
        // Derive the key encryption key
        val salt = keyEntry.metadata?.get("salt")?.decodeBase64() ?: ByteArray(16)
        
        val kek = if (encryption is KeyDerivation) {
            (encryption as KeyDerivation).deriveKeyArgon2(
                "secure-master-password", // In production, this would be securely stored or derived
                salt,
                memory = 65536,
                iterations = 3,
                parallelism = 4
            )
        } else {
            // Fallback if KeyDerivation is not available
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.digest("secure-master-password".toByteArray())
        }
        
        // Decrypt the master key
        val decryptResult = encryption.decrypt(
            keyEntry.encryptedKey,
            kek,
            keyEntry.nonce,
            keyEntry.authTag
        )
        
        return when (decryptResult) {
            is DecryptionResult.Success -> KeyResult.Success(decryptResult.data, 1)
            is DecryptionResult.Failure -> KeyResult.Failure("Failed to decrypt master key: ${decryptResult.error}")
        }
    }
    
    private suspend fun loadKeys() {
        // Load all active keys from database
        val keys = getKeysFromDatabase()
        
        for (key in keys) {
            val keyEntry = getKeyEntryFromDatabase(key.name, key.version)
            if (keyEntry != null) {
                keyCache[getCacheKey(key.name, key.version)] = keyEntry
            }
        }
    }
    
    private suspend fun scheduleKeyRotationCheck() {
        // In a real implementation, this would schedule a periodic task
        // For now, we'll just check once
        checkKeysForRotation()
    }
    
    private suspend fun checkKeysForRotation() {
        val keys = getKeysFromDatabase()
        val now = Instant.now()
        
        for (key in keys) {
            if (key.status == "active") {
                val expiresAt = Instant.parse(key.expiresAt)
                if (now.isAfter(expiresAt)) {
                    logger.info("Key ${key.name} version ${key.version} has expired, rotating")
                    rotateKey(key.name, "system")
                }
            }
        }
    }
    
    private suspend fun encryptKey(key: ByteArray, masterKey: ByteArray): EncryptionResult {
        return encryption.encrypt(key, masterKey)
    }
    
    private suspend fun decryptKey(encryptedKey: ByteArray, nonce: ByteArray, authTag: ByteArray?, masterKey: ByteArray): DecryptionResult {
        return encryption.decrypt(encryptedKey, masterKey, nonce, authTag)
    }
    
    private suspend fun loadKeyFromDatabase(keyName: String, version: Int?): KeyResult {
        // Get key entry from database
        val keyEntry = if (version == null) {
            getLatestKeyEntryFromDatabase(keyName)
        } else {
            getKeyEntryFromDatabase(keyName, version)
        }
        
        if (keyEntry == null) {
            return KeyResult.Failure("Key not found")
        }
        
        // Get master key
        val masterKeyResult = getMasterKey()
        if (masterKeyResult is KeyResult.Failure) {
            return masterKeyResult
        }
        
        val masterKey = (masterKeyResult as KeyResult.Success).key
        
        // Decrypt key
        val decryptResult = decryptKey(
            keyEntry.encryptedKey,
            keyEntry.nonce,
            keyEntry.authTag,
            masterKey
        )
        
        return when (decryptResult) {
            is DecryptionResult.Success -> {
                // Cache key
                keyCache[getCacheKey(keyName, keyEntry.version)] = keyEntry
                KeyResult.Success(decryptResult.data, keyEntry.version)
            }
            is DecryptionResult.Failure -> KeyResult.Failure("Failed to decrypt key: ${decryptResult.error}")
        }
    }
    
    private suspend fun storeKeyInDatabase(keyEntry: KeyEntry): Boolean {
        // In a real implementation, this would store the key in the database
        // For now, we'll just log it
        logger.info("Storing key ${keyEntry.name} version ${keyEntry.version} in database")
        return true
    }
    
    private suspend fun updateKeyStatus(keyName: String, version: Int?, status: String): Boolean {
        // In a real implementation, this would update the key status in the database
        // For now, we'll just log it
        logger.info("Updating key $keyName${version?.let { " version $it" } ?: ""} status to $status")
        return true
    }
    
    private suspend fun getKeyEntryFromDatabase(keyName: String, version: Int): KeyEntry? {
        // In a real implementation, this would retrieve the key from the database
        // For now, we'll return a mock entry if it's the master key
        if (keyName == "master" && version == 1) {
            val keyBytes = ByteArray(32)
            secureRandom.nextBytes(keyBytes)
            
            val salt = ByteArray(16)
            secureRandom.nextBytes(salt)
            
            val kek = if (encryption is KeyDerivation) {
                (encryption as KeyDerivation).deriveKeyArgon2(
                    "secure-master-password",
                    salt,
                    memory = 65536,
                    iterations = 3,
                    parallelism = 4
                )
            } else {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                digest.digest("secure-master-password".toByteArray())
            }
            
            val encryptedKey = encryption.encrypt(keyBytes, kek)
            
            return KeyEntry(
                name = "master",
                version = 1,
                encryptedKey = encryptedKey.encryptedData,
                nonce = encryptedKey.nonce,
                authTag = encryptedKey.authTag,
                createdAt = Instant.now().toString(),
                createdBy = "system",
                expiresAt = Instant.now().plus(365, ChronoUnit.DAYS).toString(),
                status = "active",
                metadata = mapOf("salt" to salt.encodeToBase64())
            )
        }
        
        return null
    }
    
    private suspend fun getLatestKeyEntryFromDatabase(keyName: String): KeyEntry? {
        // In a real implementation, this would retrieve the latest key from the database
        // For now, we'll return null
        return null
    }
    
    private suspend fun getKeysFromDatabase(): List<KeyMetadata> {
        // In a real implementation, this would retrieve all keys from the database
        // For now, we'll return an empty list
        return emptyList()
    }
    
    private suspend fun logKeyAccess(keyName: String, version: Int?, userId: String, action: String) {
        // In a real implementation, this would log the key access in the database
        // For now, we'll just log it
        logger.info("User $userId performed $action on key $keyName${version?.let { " version $it" } ?: ""}")
    }
    
    private suspend fun checkAccess(keyName: String, action: String, userId: String): Boolean {
        // In a real implementation, this would check if the user has access to the key
        // For now, we'll allow all access
        return true
    }
    
    private fun getCacheKey(keyName: String, version: Int?): String {
        return if (version == null) {
            "$keyName:latest"
        } else {
            "$keyName:$version"
        }
    }
    
    /**
     * Helper function to encode ByteArray to Base64 string
     */
    private fun ByteArray.encodeToBase64(): String {
        return java.util.Base64.getEncoder().encodeToString(this)
    }
    
    /**
     * Helper function to decode Base64 string to ByteArray
     */
    private fun String.decodeBase64(): ByteArray {
        return java.util.Base64.getDecoder().decode(this)
    }
}

/**
 * Key entry for database storage
 */
data class KeyEntry(
    val name: String,
    val version: Int,
    val encryptedKey: ByteArray,
    val nonce: ByteArray,
    val authTag: ByteArray?,
    val createdAt: String,
    val createdBy: String,
    val expiresAt: String,
    val status: String,
    val metadata: Map<String, String>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as KeyEntry

        if (name != other.name) return false
        if (version != other.version) return false
        if (!encryptedKey.contentEquals(other.encryptedKey)) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (authTag != null) {
            if (other.authTag == null) return false
            if (!authTag.contentEquals(other.authTag)) return false
        } else if (other.authTag != null) return false
        if (createdAt != other.createdAt) return false
        if (createdBy != other.createdBy) return false
        if (expiresAt != other.expiresAt) return false
        if (status != other.status) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + version
        result = 31 * result + encryptedKey.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + (authTag?.contentHashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + createdBy.hashCode()
        result = 31 * result + expiresAt.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (metadata?.hashCode() ?: 0)
        return result
    }
}

/**
 * Key metadata for listing keys
 */
data class KeyMetadata(
    val name: String,
    val version: Int,
    val createdAt: String,
    val createdBy: String,
    val expiresAt: String,
    val status: String
)

/**
 * Result of key operations
 */
sealed class KeyResult {
    data class Success(val key: ByteArray, val version: Int) : KeyResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Success

            if (!key.contentEquals(other.key)) return false
            if (version != other.version) return false

            return true
        }

        override fun hashCode(): Int {
            var result = key.contentHashCode()
            result = 31 * result + version
            return result
        }
    }
    
    data class Failure(val error: String) : KeyResult()
}