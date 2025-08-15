package crypto

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestAESEncryption(t *testing.T) {
	t.Run("should encrypt and decrypt data successfully", func(t *testing.T) {
		plaintext := "Hello, World! This is a secret message."
		password := "super-secret-password"
		
		encrypted, err := EncryptAES([]byte(plaintext), password)
		require.NoError(t, err)
		assert.NotEmpty(t, encrypted)
		assert.NotEqual(t, plaintext, string(encrypted))
		
		decrypted, err := DecryptAES(encrypted, password)
		require.NoError(t, err)
		assert.Equal(t, plaintext, string(decrypted))
	})

	t.Run("should fail decryption with wrong password", func(t *testing.T) {
		plaintext := "Hello, World!"
		password := "correct-password"
		wrongPassword := "wrong-password"
		
		encrypted, err := EncryptAES([]byte(plaintext), password)
		require.NoError(t, err)
		
		_, err = DecryptAES(encrypted, wrongPassword)
		assert.Error(t, err)
	})

	t.Run("should handle empty data", func(t *testing.T) {
		password := "password"
		
		encrypted, err := EncryptAES([]byte(""), password)
		require.NoError(t, err)
		
		decrypted, err := DecryptAES(encrypted, password)
		require.NoError(t, err)
		assert.Empty(t, decrypted)
	})

	t.Run("should fail with empty password", func(t *testing.T) {
		plaintext := "Hello, World!"
		
		_, err := EncryptAES([]byte(plaintext), "")
		assert.Error(t, err)
		assert.Contains(t, err.Error(), "password cannot be empty")
	})
}

func TestKeyDerivation(t *testing.T) {
	t.Run("should derive consistent keys from same password and salt", func(t *testing.T) {
		password := "test-password"
		salt := []byte("test-salt-16byte")
		
		key1, err := DeriveKey(password, salt)
		require.NoError(t, err)
		
		key2, err := DeriveKey(password, salt)
		require.NoError(t, err)
		
		assert.Equal(t, key1, key2)
		assert.Len(t, key1, 32) // AES-256 key length
	})

	t.Run("should derive different keys for different passwords", func(t *testing.T) {
		salt := []byte("test-salt-16byte")
		
		key1, err := DeriveKey("password1", salt)
		require.NoError(t, err)
		
		key2, err := DeriveKey("password2", salt)
		require.NoError(t, err)
		
		assert.NotEqual(t, key1, key2)
	})

	t.Run("should derive different keys for different salts", func(t *testing.T) {
		password := "test-password"
		
		key1, err := DeriveKey(password, []byte("salt1-16byte-len"))
		require.NoError(t, err)
		
		key2, err := DeriveKey(password, []byte("salt2-16byte-len"))
		require.NoError(t, err)
		
		assert.NotEqual(t, key1, key2)
	})
}

func TestGenerateSalt(t *testing.T) {
	t.Run("should generate salt of correct length", func(t *testing.T) {
		salt, err := GenerateSalt()
		require.NoError(t, err)
		assert.Len(t, salt, 16)
	})

	t.Run("should generate different salts", func(t *testing.T) {
		salt1, err := GenerateSalt()
		require.NoError(t, err)
		
		salt2, err := GenerateSalt()
		require.NoError(t, err)
		
		assert.NotEqual(t, salt1, salt2)
	})
}

func TestHashPassword(t *testing.T) {
	t.Run("should hash password successfully", func(t *testing.T) {
		password := "test-password"
		
		hash, err := HashPassword(password)
		require.NoError(t, err)
		assert.NotEmpty(t, hash)
		assert.NotEqual(t, password, hash)
	})

	t.Run("should verify correct password", func(t *testing.T) {
		password := "test-password"
		
		hash, err := HashPassword(password)
		require.NoError(t, err)
		
		valid := VerifyPassword(password, hash)
		assert.True(t, valid)
	})

	t.Run("should reject incorrect password", func(t *testing.T) {
		password := "correct-password"
		wrongPassword := "wrong-password"
		
		hash, err := HashPassword(password)
		require.NoError(t, err)
		
		valid := VerifyPassword(wrongPassword, hash)
		assert.False(t, valid)
	})

	t.Run("should generate different hashes for same password", func(t *testing.T) {
		password := "test-password"
		
		hash1, err := HashPassword(password)
		require.NoError(t, err)
		
		hash2, err := HashPassword(password)
		require.NoError(t, err)
		
		assert.NotEqual(t, hash1, hash2)
		
		// But both should verify correctly
		assert.True(t, VerifyPassword(password, hash1))
		assert.True(t, VerifyPassword(password, hash2))
	})
}

func TestGenerateRandomBytes(t *testing.T) {
	t.Run("should generate bytes of correct length", func(t *testing.T) {
		length := 32
		bytes, err := GenerateRandomBytes(length)
		require.NoError(t, err)
		assert.Len(t, bytes, length)
	})

	t.Run("should generate different byte sequences", func(t *testing.T) {
		bytes1, err := GenerateRandomBytes(16)
		require.NoError(t, err)
		
		bytes2, err := GenerateRandomBytes(16)
		require.NoError(t, err)
		
		assert.NotEqual(t, bytes1, bytes2)
	})

	t.Run("should handle zero length", func(t *testing.T) {
		bytes, err := GenerateRandomBytes(0)
		require.NoError(t, err)
		assert.Empty(t, bytes)
	})
}
