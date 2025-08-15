package hub

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"gorm.io/gorm"
)

type Service struct {
	db *gorm.DB
}

func NewService() *Service {
	return &Service{}
}

func (s *Service) SetDB(db *gorm.DB) {
	s.db = db
}

func (s *Service) CreateIntegration(ctx context.Context, integration *Integration) error {
	if err := s.validateIntegration(integration); err != nil {
		return err
	}

	if integration.Status == 0 {
		integration.Status = IntegrationStatusActive
	}

	if err := s.db.Create(integration).Error; err != nil {
		return fmt.Errorf("failed to create integration: %w", err)
	}

	return nil
}

func (s *Service) GetIntegrations(ctx context.Context, userID string) ([]*Integration, error) {
	var integrations []*Integration
	err := s.db.Where("user_id = ?", userID).Find(&integrations).Error
	if err != nil {
		return nil, fmt.Errorf("failed to get integrations: %w", err)
	}

	return integrations, nil
}

func (s *Service) validateIntegration(integration *Integration) error {
	if strings.TrimSpace(integration.Name) == "" {
		return errors.New("name is required")
	}
	if strings.TrimSpace(integration.UserID) == "" {
		return errors.New("user ID is required")
	}
	if strings.TrimSpace(integration.Type) == "" {
		return errors.New("type is required")
	}
	return nil
}

type Integration struct {
	ID          uint               `json:"id" gorm:"primaryKey"`
	Name        string             `json:"name" gorm:"not null"`
	Description string             `json:"description"`
	UserID      string             `json:"user_id" gorm:"index;not null"`
	Type        string             `json:"type" gorm:"not null"`
	Status      IntegrationStatus  `json:"status" gorm:"default:0"`
	CreatedAt   time.Time          `json:"created_at"`
	UpdatedAt   time.Time          `json:"updated_at"`
	DeletedAt   gorm.DeletedAt     `json:"-" gorm:"index"`
}

func (Integration) TableName() string {
	return "integrations"
}

type IntegrationStatus int

const (
	IntegrationStatusActive IntegrationStatus = iota
	IntegrationStatusInactive
	IntegrationStatusError
)

func (i IntegrationStatus) String() string {
	switch i {
	case IntegrationStatusActive:
		return "active"
	case IntegrationStatusInactive:
		return "inactive"
	case IntegrationStatusError:
		return "error"
	default:
		return "unknown"
	}
}
