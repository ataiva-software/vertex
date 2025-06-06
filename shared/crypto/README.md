# Eden Cryptography Library

This library provides cryptographic services for the Eden platform, with a focus on security, performance, and ease of use.

## Features

- **Symmetric Encryption**: AES-GCM authenticated encryption
- **Key Derivation**: Argon2id and PBKDF2 for password-based key derivation
- **Zero-Knowledge Encryption**: Client-side encryption where the server never sees plaintext
- **Digital Signatures**: RSA and ECDSA signature generation and verification
- **Secure Random**: Cryptographically secure random number generation
- **Password Hashing**: BCrypt password hashing and verification
- **Multi-Factor Authentication**: TOTP and backup code generation

## Security Considerations

- **AES-GCM**: Uses 256-bit keys with 12-byte nonces and 16-byte authentication tags
- **Argon2id**: Memory-hard key derivation function resistant to side-channel attacks
- **Secure Random**: Uses platform-specific secure random number generators
- **Zero-Knowledge**: Server never has access to plaintext data or encryption keys

## Usage

### Initialization

```kotlin
// Create crypto services using the factory
val encryption = CryptoFactory.createEncryption()
val keyDerivation = CryptoFactory.createKeyDerivation()
val zkEncryption = CryptoFactory.createZeroKnowledgeEncryption()
val secureRandom = CryptoFactory.createSecureRandom()
```

### Symmetric Encryption

```kotlin
// Generate a key
val key = keyDerivation.generateSalt(32) // 256-bit key

// Encrypt data
val data = "Sensitive data".toByteArray()
val encryptionResult = encryption.encrypt(data, key)

// Store these values securely
val encryptedData = encryptionResult.encryptedData
val nonce = encryptionResult.nonce
val authTag = encryptionResult.authTag

// Decrypt data
val decryptionResult = encryption.decrypt(encryptedData, key, nonce, authTag)
when (decryptionResult) {
    is DecryptionResult.Success -> {
        val decryptedData = decryptionResult.data
        // Use decrypted data
    }
    is DecryptionResult.Failure -> {
        val errorMessage = decryptionResult.error
        // Handle error
    }
}
```

### Key Derivation

```kotlin
// Generate a salt
val salt = keyDerivation.generateSalt(16)

// Derive a key using PBKDF2
val key1 = keyDerivation.deriveKey(
    password = "user-password",
    salt = salt,
    iterations = 100000,
    keyLength = 32
)

// Derive a key using Argon2id (recommended)
val key2 = keyDerivation.deriveKeyArgon2(
    password = "user-password",
    salt = salt,
    memory = 65536, // 64 MB
    iterations = 3,
    parallelism = 4
)

// Derive multiple keys from a master key
val masterKey = keyDerivation.generateSalt(32)
val keys = keyDerivation.deriveKeys(
    masterKey = masterKey,
    info = "context-info",
    count = 3,
    keyLength = 32
)
```

### Zero-Knowledge Encryption

```kotlin
// Encrypt data with zero-knowledge approach
val zkResult = zkEncryption.encryptZeroKnowledge(
    data = "Sensitive data",
    password = "user-password"
)

// Store these values
val encryptedData = zkResult.encryptedData
val salt = zkResult.salt
val nonce = zkResult.nonce
val authTag = zkResult.authTag
val keyDerivationParams = zkResult.keyDerivationParams

// Decrypt data
val decryptedData = zkEncryption.decryptZeroKnowledge(zkResult, "user-password")
```

### Secure Random

```kotlin
// Generate random bytes
val randomBytes = secureRandom.nextBytes(32)

// Generate random string
val randomString = secureRandom.nextString(16)

// Generate random string with custom charset
val charset = "0123456789ABCDEF"
val randomHex = secureRandom.nextString(16, charset)

// Generate UUID
val uuid = secureRandom.nextUuid()
```

## Best Practices

1. **Never reuse nonces**: Always generate a fresh nonce for each encryption operation
2. **Store salt, nonce, and auth tag**: These values are not secret and must be stored alongside the encrypted data
3. **Use appropriate key derivation parameters**: Adjust Argon2 parameters based on your security requirements and available resources
4. **Validate data integrity**: Always verify the authentication tag when decrypting data
5. **Secure key storage**: Store encryption keys securely, preferably in a hardware security module (HSM) or key vault
6. **Zero out sensitive data**: Clear sensitive data from memory as soon as it's no longer needed

## Implementation Details

The library uses the BouncyCastle provider for most cryptographic operations, with platform-specific optimizations for JVM, JS, and native targets.

### JVM Implementation

- Uses BouncyCastle for AES-GCM encryption
- Uses Argon2-JVM for Argon2id key derivation
- Uses Java's SecureRandom for random number generation
- Uses BCrypt for password hashing

## Testing

The library includes comprehensive tests for all cryptographic operations, including:

- Basic encryption/decryption tests
- Key derivation tests
- Zero-knowledge encryption tests
- Digital signature tests
- Secure random tests
- Password hashing tests
- MFA tests

## Security Audit

The library has undergone a security audit by an independent security firm. The audit report is available in the `security/audit` directory.

## License

This library is licensed under the MIT License. See the LICENSE file for details.