package vault

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"github.com/ataivadev/eden/pkg/crypto"
	"gorm.io/gorm"
)

// Service provides vault operations
type Service struct {
	db       *gorm.DB
	password string // Master password for encryption
}

// NewService creates a new vault service
func NewService() *Service {
	return &Service{
		password: "default-master-password", // In real implementation, this would come from config
	}
}

// SetDB sets the database connection
func (s *Service) SetDB(db *gorm.DB) {
	s.db = db
}

// SetMasterPassword sets the master password for encryption
func (s *Service) SetMasterPassword(password string) {
	s.password = password
}

// StoreSecret stores a new secret
func (s *Service) StoreSecret(ctx context.Context, userID string, secret *Secret) error {
	if err := s.validateSecret(secret); err != nil {
		return err
	}

	// Check if secret already exists
	var existing Secret
	err := s.db.Where("user_id = ? AND key = ?", userID, secret.Key).First(&existing).Error
	if err == nil {
		return fmt.Errorf("secret with key '%s' already exists", secret.Key)
	}
	if !errors.Is(err, gorm.ErrRecordNotFound) {
		return fmt.Errorf("failed to check existing secret: %w", err)
	}

	// Encrypt the value
	encryptedValue, err := crypto.EncryptAES([]byte(secret.Value), s.password)
	if err != nil {
		return fmt.Errorf("failed to encrypt secret: %w", err)
	}

	// Create new secret record
	newSecret := &Secret{
		UserID:      userID,
		Key:         secret.Key,
		Value:       string(encryptedValue),
		Description: secret.Description,
		Tags:        StringSlice(secret.Tags),
	}

	if err := s.db.Create(newSecret).Error; err != nil {
		return fmt.Errorf("failed to store secret: %w", err)
	}

	// Log the operation
	s.logOperation(userID, secret.Key, "CREATE", "", "")

	return nil
}

// GetSecret retrieves a secret by key
func (s *Service) GetSecret(ctx context.Context, userID, key string) (*Secret, error) {
	var secret Secret
	err := s.db.Where("user_id = ? AND key = ?", userID, key).First(&secret).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, fmt.Errorf("secret '%s' not found", key)
	}
	if err != nil {
		return nil, fmt.Errorf("failed to retrieve secret: %w", err)
	}

	// Decrypt the value
	decryptedValue, err := crypto.DecryptAES([]byte(secret.Value), s.password)
	if err != nil {
		return nil, fmt.Errorf("failed to decrypt secret: %w", err)
	}

	secret.Value = string(decryptedValue)

	// Log the operation
	s.logOperation(userID, key, "READ", "", "")

	return &secret, nil
}

// ListSecrets returns a list of secrets for a user (without values)
func (s *Service) ListSecrets(ctx context.Context, userID string) ([]*SecretListItem, error) {
	var secrets []Secret
	err := s.db.Select("key, description, tags, created_at, updated_at").
		Where("user_id = ?", userID).
		Find(&secrets).Error
	if err != nil {
		return nil, fmt.Errorf("failed to list secrets: %w", err)
	}

	items := make([]*SecretListItem, len(secrets))
	for i, secret := range secrets {
		items[i] = &SecretListItem{
			Key:         secret.Key,
			Description: secret.Description,
			Tags:        secret.Tags,
			CreatedAt:   secret.CreatedAt,
			UpdatedAt:   secret.UpdatedAt,
		}
	}

	return items, nil
}

// UpdateSecret updates an existing secret
func (s *Service) UpdateSecret(ctx context.Context, userID string, secret *Secret) error {
	if err := s.validateSecret(secret); err != nil {
		return err
	}

	// Check if secret exists
	var existing Secret
	err := s.db.Where("user_id = ? AND key = ?", userID, secret.Key).First(&existing).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return fmt.Errorf("secret '%s' not found", secret.Key)
	}
	if err != nil {
		return fmt.Errorf("failed to find secret: %w", err)
	}

	// Encrypt the new value
	encryptedValue, err := crypto.EncryptAES([]byte(secret.Value), s.password)
	if err != nil {
		return fmt.Errorf("failed to encrypt secret: %w", err)
	}

	// Update the secret
	updates := map[string]interface{}{
		"value":       string(encryptedValue),
		"description": secret.Description,
		"tags":        StringSlice(secret.Tags),
	}

	if err := s.db.Model(&existing).Updates(updates).Error; err != nil {
		return fmt.Errorf("failed to update secret: %w", err)
	}

	// Log the operation
	s.logOperation(userID, secret.Key, "UPDATE", "", "")

	return nil
}

// DeleteSecret deletes a secret
func (s *Service) DeleteSecret(ctx context.Context, userID, key string) error {
	// Check if secret exists
	var secret Secret
	err := s.db.Where("user_id = ? AND key = ?", userID, key).First(&secret).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return fmt.Errorf("secret '%s' not found", key)
	}
	if err != nil {
		return fmt.Errorf("failed to find secret: %w", err)
	}

	// Delete the secret (soft delete)
	if err := s.db.Delete(&secret).Error; err != nil {
		return fmt.Errorf("failed to delete secret: %w", err)
	}

	// Log the operation
	s.logOperation(userID, key, "DELETE", "", "")

	return nil
}

// validateSecret validates a secret before storing/updating
func (s *Service) validateSecret(secret *Secret) error {
	if strings.TrimSpace(secret.Key) == "" {
		return errors.New("key is required")
	}
	if strings.TrimSpace(secret.Value) == "" {
		return errors.New("value is required")
	}
	if len(secret.Key) > 255 {
		return errors.New("key too long (max 255 characters)")
	}
	return nil
}

// logOperation logs an audit entry
func (s *Service) logOperation(userID, secretKey, action, ipAddress, userAgent string) {
	if s.db == nil {
		return // Skip logging if no database connection
	}

	auditLog := &AuditLog{
		UserID:    userID,
		SecretKey: secretKey,
		Action:    action,
		IPAddress: ipAddress,
		UserAgent: userAgent,
	}

	// Log errors but don't fail the operation
	if err := s.db.Create(auditLog).Error; err != nil {
		// In a real implementation, this would use proper logging
		fmt.Printf("Failed to log audit entry: %v\n", err)
	}
}
