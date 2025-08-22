package vault

import (
	"context"
	"os"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
)

func setupTestDB(t *testing.T) *gorm.DB {
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)

	// Auto-migrate the schema
	err = db.AutoMigrate(&Secret{}, &AuditLog{})
	require.NoError(t, err)

	return db
}

func TestVaultService(t *testing.T) {
	t.Run("should create new vault service", func(t *testing.T) {
		// Set required environment variable for test
		os.Setenv("VERTEX_MASTER_PASSWORD", "test-password")
		defer os.Unsetenv("VERTEX_MASTER_PASSWORD")
		
		service := NewService()
		assert.NotNil(t, service)
	})
}

func TestSecretOperations(t *testing.T) {
	// Set required environment variable for test
	os.Setenv("VERTEX_MASTER_PASSWORD", "test-password")
	defer os.Unsetenv("VERTEX_MASTER_PASSWORD")
	
	db := setupTestDB(t)
	service := NewService()
	service.SetDB(db)
	ctx := context.Background()

	t.Run("should store and retrieve secret", func(t *testing.T) {
		secret := &Secret{
			Key:         "api-key",
			Value:       "secret-value",
			Description: "API key for external service",
			Tags:        StringSlice{"api", "external"},
		}

		// Store secret
		err := service.StoreSecret(ctx, "user1", secret)
		require.NoError(t, err)

		// Retrieve secret
		retrieved, err := service.GetSecret(ctx, "user1", "api-key")
		require.NoError(t, err)
		assert.Equal(t, secret.Key, retrieved.Key)
		assert.Equal(t, secret.Value, retrieved.Value)
		assert.Equal(t, secret.Description, retrieved.Description)
		assert.Equal(t, secret.Tags, retrieved.Tags)
		assert.WithinDuration(t, time.Now(), retrieved.CreatedAt, time.Second)
		assert.WithinDuration(t, time.Now(), retrieved.UpdatedAt, time.Second)
	})

	t.Run("should list all secrets globally", func(t *testing.T) {
		// Store multiple secrets
		secrets := []*Secret{
			{Key: "secret1", Value: "value1", Description: "First secret"},
			{Key: "secret2", Value: "value2", Description: "Second secret"},
		}

		for _, secret := range secrets {
			err := service.StoreSecret(ctx, "user2", secret)
			require.NoError(t, err)
		}

		// List secrets - should return all secrets regardless of user
		list, err := service.ListSecrets(ctx, "user2")
		require.NoError(t, err)
		// Should include the 2 new secrets plus any from previous tests
		assert.GreaterOrEqual(t, len(list), 2)

		// Check that values are not included in list (SecretListItem doesn't have Value field)
		secretFound := false
		for _, item := range list {
			assert.NotEmpty(t, item.Key)
			if item.Key == "secret1" || item.Key == "secret2" {
				secretFound = true
				assert.NotEmpty(t, item.Description)
			}
		}
		assert.True(t, secretFound, "Should find at least one of the stored secrets")
	})

	t.Run("should update existing secret", func(t *testing.T) {
		// Store initial secret
		secret := &Secret{
			Key:         "update-test",
			Value:       "initial-value",
			Description: "Initial description",
		}
		err := service.StoreSecret(ctx, "user3", secret)
		require.NoError(t, err)

		// Update secret
		updatedSecret := &Secret{
			Key:         "update-test",
			Value:       "updated-value",
			Description: "Updated description",
			Tags:        StringSlice{"updated"},
		}
		err = service.UpdateSecret(ctx, "user3", updatedSecret)
		require.NoError(t, err)

		// Verify update
		retrieved, err := service.GetSecret(ctx, "user3", "update-test")
		require.NoError(t, err)
		assert.Equal(t, "updated-value", retrieved.Value)
		assert.Equal(t, "Updated description", retrieved.Description)
		assert.Equal(t, StringSlice{"updated"}, retrieved.Tags)
	})

	t.Run("should delete secret", func(t *testing.T) {
		// Store secret
		secret := &Secret{Key: "delete-test", Value: "value"}
		err := service.StoreSecret(ctx, "user4", secret)
		require.NoError(t, err)

		// Delete secret
		err = service.DeleteSecret(ctx, "user4", "delete-test")
		require.NoError(t, err)

		// Verify deletion
		_, err = service.GetSecret(ctx, "user4", "delete-test")
		assert.Error(t, err)
		assert.Contains(t, err.Error(), "not found")
	})

	t.Run("should return error for non-existent secret", func(t *testing.T) {
		_, err := service.GetSecret(ctx, "user5", "non-existent")
		assert.Error(t, err)
		assert.Contains(t, err.Error(), "not found")
	})

	t.Run("should allow global access to secrets", func(t *testing.T) {
		// Store secret for user1
		secret := &Secret{Key: "global-test", Value: "global-value"}
		err := service.StoreSecret(ctx, "user1", secret)
		require.NoError(t, err)

		// Access from user2 should work (global secrets)
		retrieved, err := service.GetSecret(ctx, "user2", "global-test")
		require.NoError(t, err)
		assert.Equal(t, "global-value", retrieved.Value)
	})
}

func TestSecretValidation(t *testing.T) {
	// Set required environment variable for test
	os.Setenv("VERTEX_MASTER_PASSWORD", "test-password")
	defer os.Unsetenv("VERTEX_MASTER_PASSWORD")
	
	db := setupTestDB(t)
	service := NewService()
	service.SetDB(db)
	ctx := context.Background()

	t.Run("should validate required fields", func(t *testing.T) {
		tests := []struct {
			name   string
			secret *Secret
			error  string
		}{
			{
				name:   "empty key",
				secret: &Secret{Value: "value"},
				error:  "key is required",
			},
			{
				name:   "empty value",
				secret: &Secret{Key: "key"},
				error:  "value is required",
			},
			{
				name:   "key too long",
				secret: &Secret{Key: string(make([]byte, 256)), Value: "value"},
				error:  "key too long",
			},
		}

		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				err := service.StoreSecret(ctx, "user", tt.secret)
				require.Error(t, err)
				assert.Contains(t, err.Error(), tt.error)
			})
		}
	})
}

func TestSecretEncryption(t *testing.T) {
	// Set required environment variable for test
	os.Setenv("VERTEX_MASTER_PASSWORD", "test-password")
	defer os.Unsetenv("VERTEX_MASTER_PASSWORD")
	
	db := setupTestDB(t)
	service := NewService()
	service.SetDB(db)
	ctx := context.Background()

	t.Run("should encrypt secret values", func(t *testing.T) {
		secret := &Secret{
			Key:   "encryption-test",
			Value: "sensitive-data",
		}

		err := service.StoreSecret(ctx, "user", secret)
		require.NoError(t, err)

		// Access the underlying storage to verify encryption
		var storedSecret Secret
		err = db.Where("user_id = ? AND key = ?", "user", "encryption-test").First(&storedSecret).Error
		require.NoError(t, err)
		
		// The stored value should be encrypted (different from original)
		assert.NotEqual(t, "sensitive-data", storedSecret.Value)
		assert.NotEmpty(t, storedSecret.Value)

		// But retrieval should work correctly
		retrieved, err := service.GetSecret(ctx, "user", "encryption-test")
		require.NoError(t, err)
		assert.Equal(t, "sensitive-data", retrieved.Value)
	})
}
