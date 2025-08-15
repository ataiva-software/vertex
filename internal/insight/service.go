package insight

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

func (s *Service) CreateReport(ctx context.Context, report *Report) error {
	if err := s.validateReport(report); err != nil {
		return err
	}

	if report.Status == 0 {
		report.Status = ReportStatusPending
	}

	if err := s.db.Create(report).Error; err != nil {
		return fmt.Errorf("failed to create report: %w", err)
	}

	return nil
}

func (s *Service) GetReports(ctx context.Context, userID string) ([]*Report, error) {
	var reports []*Report
	err := s.db.Where("user_id = ?", userID).Find(&reports).Error
	if err != nil {
		return nil, fmt.Errorf("failed to get reports: %w", err)
	}

	return reports, nil
}

func (s *Service) validateReport(report *Report) error {
	if strings.TrimSpace(report.Name) == "" {
		return errors.New("name is required")
	}
	if strings.TrimSpace(report.UserID) == "" {
		return errors.New("user ID is required")
	}
	return nil
}

type Report struct {
	ID          uint         `json:"id" gorm:"primaryKey"`
	Name        string       `json:"name" gorm:"not null"`
	Description string       `json:"description"`
	UserID      string       `json:"user_id" gorm:"index;not null"`
	Type        string       `json:"type" gorm:"not null"`
	Status      ReportStatus `json:"status" gorm:"default:0"`
	CreatedAt   time.Time    `json:"created_at"`
	UpdatedAt   time.Time    `json:"updated_at"`
	DeletedAt   gorm.DeletedAt `json:"-" gorm:"index"`
}

func (Report) TableName() string {
	return "reports"
}

type ReportStatus int

const (
	ReportStatusPending ReportStatus = iota
	ReportStatusGenerating
	ReportStatusCompleted
	ReportStatusFailed
)

func (r ReportStatus) String() string {
	switch r {
	case ReportStatusPending:
		return "pending"
	case ReportStatusGenerating:
		return "generating"
	case ReportStatusCompleted:
		return "completed"
	case ReportStatusFailed:
		return "failed"
	default:
		return "unknown"
	}
}
