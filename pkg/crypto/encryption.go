package crypto

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"errors"
	"fmt"
	"io"

	"golang.org/x/crypto/bcrypt"
	"golang.org/x/crypto/pbkdf2"
)

const (
	// SaltLength is the length of salt used for key derivation
	SaltLength = 16
	// KeyLength is the length of derived keys (AES-256)
	KeyLength = 32
	// NonceLength is the length of nonce used for AES-GCM
	NonceLength = 12
	// PBKDF2Iterations is the number of iterations for PBKDF2
	PBKDF2Iterations = 100000
)

// EncryptAES encrypts data using AES-256-GCM with a password-derived key
func EncryptAES(data []byte, password string) ([]byte, error) {
	if password == "" {
		return nil, errors.New("password cannot be empty")
	}

	// Generate a random salt
	salt, err := GenerateRandomBytes(SaltLength)
	if err != nil {
		return nil, fmt.Errorf("failed to generate salt: %w", err)
	}

	// Derive key from password and salt
	key, err := DeriveKey(password, salt)
	if err != nil {
		return nil, fmt.Errorf("failed to derive key: %w", err)
	}

	// Create AES cipher
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("failed to create cipher: %w", err)
	}

	// Create GCM mode
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("failed to create GCM: %w", err)
	}

	// Generate nonce
	nonce, err := GenerateRandomBytes(NonceLength)
	if err != nil {
		return nil, fmt.Errorf("failed to generate nonce: %w", err)
	}

	// Encrypt data
	ciphertext := gcm.Seal(nil, nonce, data, nil)

	// Combine salt + nonce + ciphertext
	result := make([]byte, 0, SaltLength+NonceLength+len(ciphertext))
	result = append(result, salt...)
	result = append(result, nonce...)
	result = append(result, ciphertext...)

	return result, nil
}

// DecryptAES decrypts data using AES-256-GCM with a password-derived key
func DecryptAES(encryptedData []byte, password string) ([]byte, error) {
	if password == "" {
		return nil, errors.New("password cannot be empty")
	}

	if len(encryptedData) < SaltLength+NonceLength {
		return nil, errors.New("encrypted data too short")
	}

	// Extract salt, nonce, and ciphertext
	salt := encryptedData[:SaltLength]
	nonce := encryptedData[SaltLength : SaltLength+NonceLength]
	ciphertext := encryptedData[SaltLength+NonceLength:]

	// Derive key from password and salt
	key, err := DeriveKey(password, salt)
	if err != nil {
		return nil, fmt.Errorf("failed to derive key: %w", err)
	}

	// Create AES cipher
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("failed to create cipher: %w", err)
	}

	// Create GCM mode
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("failed to create GCM: %w", err)
	}

	// Decrypt data
	plaintext, err := gcm.Open(nil, nonce, ciphertext, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to decrypt: %w", err)
	}

	return plaintext, nil
}

// DeriveKey derives a key from password and salt using PBKDF2
func DeriveKey(password string, salt []byte) ([]byte, error) {
	if password == "" {
		return nil, errors.New("password cannot be empty")
	}
	if len(salt) == 0 {
		return nil, errors.New("salt cannot be empty")
	}

	key := pbkdf2.Key([]byte(password), salt, PBKDF2Iterations, KeyLength, sha256.New)
	return key, nil
}

// GenerateSalt generates a random salt
func GenerateSalt() ([]byte, error) {
	return GenerateRandomBytes(SaltLength)
}

// GenerateRandomBytes generates random bytes of specified length
func GenerateRandomBytes(length int) ([]byte, error) {
	if length == 0 {
		return []byte{}, nil
	}

	bytes := make([]byte, length)
	if _, err := io.ReadFull(rand.Reader, bytes); err != nil {
		return nil, fmt.Errorf("failed to generate random bytes: %w", err)
	}
	return bytes, nil
}

// HashPassword hashes a password using bcrypt
func HashPassword(password string) (string, error) {
	if password == "" {
		return "", errors.New("password cannot be empty")
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return "", fmt.Errorf("failed to hash password: %w", err)
	}
	return string(hash), nil
}

// VerifyPassword verifies a password against its hash
func VerifyPassword(password, hash string) bool {
	err := bcrypt.CompareHashAndPassword([]byte(hash), []byte(password))
	return err == nil
}
