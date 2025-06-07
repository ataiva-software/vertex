# Eden Cryptography Module

This module provides cryptographic functionality for the Eden platform, implementing industry-standard encryption, key derivation, and integrity verification.

## Features

- **Symmetric Encryption**: AES-GCM encryption with authentication tags
- **Zero-Knowledge Encryption**: Client-side encryption where the server never has access to unencrypted data
- **Key Derivation**: PBKDF2 and Argon2 key derivation functions
- **Integrity Verification**: HMAC-SHA256 for data integrity verification
- **Multiplatform Support**: Works on JVM and JavaScript platforms

## Usage

### Basic Encryption and Decryption

```kotlin
// Create an encryption instance
val encryption = EncryptionFactory.createEncryption()

// Encrypt data
val data = "Sensitive data".encodeToByteArray()
val key = generateSecureKey() // 32 bytes for AES-256
val encryptionResult = encryption.encrypt(data, key)

// Decrypt data
val decryptionResult = encryption.decrypt(
    encryptionResult.encryptedData,
    key,
    encryptionResult.nonce,
    encryptionResult.authTag
)

when (decryptionResult) {
    is DecryptionResult.Success -> {
        val decryptedData = decryptionResult.data.decodeToString()
        println("Decrypted: $decryptedData")
    }
    is DecryptionResult.Failure -> {
        println("Decryption failed: ${decryptionResult.error}")
    }
}
```

### Zero-Knowledge Encryption

Zero-knowledge encryption ensures that sensitive data is encrypted with a user-provided password before it leaves the client. The server never has access to the unencrypted data or the encryption key.

```kotlin
// Create an encryption instance
val encryption = EncryptionFactory.createEncryption()

// Encrypt data with zero-knowledge approach
val data = "Sensitive data"
val password = "user-provided-password"
val zkResult = encryption.encryptZeroKnowledge(data, password)

// Store zkResult in the database (it's safe to store)

// Later, decrypt the data with the user's password
val decryptedData = encryption.decryptZeroKnowledge(zkResult, password)
```

### Key Derivation

```kotlin
// Create a key derivation instance
val keyDerivation = EncryptionFactory.createKeyDerivation()

// Generate a random salt
val salt = keyDerivation.generateSalt()

// Derive a key using PBKDF2
val key = keyDerivation.deriveKey(
    password = "user-password",
    salt = salt,
    iterations = 100000, // Higher is more secure but slower
    keyLength = 32 // 256 bits
)

// Derive a key using Argon2 (more resistant to hardware attacks)
val argon2Key = keyDerivation.deriveKeyArgon2(
    password = "user-password",
    salt = salt,
    memory = 65536, // 64MB
    iterations = 3,
    parallelism = 4
)
```

### Integrity Verification

```kotlin
// Verify the integrity of zero-knowledge encrypted data
val isValid = encryption.verifyIntegrity(zkResult)
if (isValid) {
    println("Data integrity verified")
} else {
    println("Data may have been tampered with")
}
```

## Implementation Details

### Encryption

The encryption implementation uses AES-GCM (Galois/Counter Mode) with 256-bit keys, which provides both confidentiality and authenticity. The nonce (IV) is randomly generated for each encryption operation and must be stored alongside the ciphertext for decryption.

### Key Derivation

- **PBKDF2**: Used for deriving encryption keys from passwords. The implementation uses HMAC-SHA256 with a configurable number of iterations.
- **Argon2**: A memory-hard function designed to be resistant to GPU and ASIC attacks. Used for password hashing and key derivation in security-critical applications.

### Integrity Verification

Data integrity is verified using HMAC-SHA256, which ensures that the encrypted data has not been tampered with. The HMAC key is derived from the encryption salt using a separate key derivation process.

## Security Considerations

1. **Key Management**: Encryption keys should be properly managed and never stored in plaintext.
2. **Password Strength**: For password-based encryption, ensure that users choose strong passwords.
3. **Salt Generation**: Always use cryptographically secure random number generators for salt generation.
4. **Iterations**: Use a high number of iterations for PBKDF2 to increase resistance to brute-force attacks.
5. **Memory Requirements**: Argon2 requires significant memory, which should be considered in resource-constrained environments.

## Platform-Specific Implementations

The cryptography module uses a platform-specific provider pattern to implement cryptographic operations efficiently on each platform:

- **JVM**: Uses Java Cryptography Architecture (JCA) with BouncyCastle for enhanced functionality
- **JavaScript**: Uses Web Crypto API in browsers and Node.js crypto modules in Node environments

## Testing

The cryptography module includes comprehensive tests to ensure correctness and security:

```bash
# Run tests
./gradlew :shared:crypto:jvmTest