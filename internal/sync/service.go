package sync

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

func (s *Service) CreateSyncJob(ctx context.Context, job *SyncJob) error {
	if err := s.validateSyncJob(job); err != nil {
		return err
	}

	if job.Status == 0 {
		job.Status = SyncStatusPending
	}

	if err := s.db.Create(job).Error; err != nil {
		return fmt.Errorf("failed to create sync job: %w", err)
	}

	return nil
}

func (s *Service) GetSyncJobs(ctx context.Context, userID string) ([]*SyncJob, error) {
	var jobs []*SyncJob
	err := s.db.Where("user_id = ?", userID).Find(&jobs).Error
	if err != nil {
		return nil, fmt.Errorf("failed to get sync jobs: %w", err)
	}

	return jobs, nil
}

func (s *Service) validateSyncJob(job *SyncJob) error {
	if strings.TrimSpace(job.Name) == "" {
		return errors.New("name is required")
	}
	if strings.TrimSpace(job.UserID) == "" {
		return errors.New("user ID is required")
	}
	if strings.TrimSpace(job.Source) == "" {
		return errors.New("source is required")
	}
	if strings.TrimSpace(job.Destination) == "" {
		return errors.New("destination is required")
	}
	return nil
}

type SyncJob struct {
	ID          uint       `json:"id" gorm:"primaryKey"`
	Name        string     `json:"name" gorm:"not null"`
	UserID      string     `json:"user_id" gorm:"index;not null"`
	Source      string     `json:"source" gorm:"not null"`
	Destination string     `json:"destination" gorm:"not null"`
	Status      SyncStatus `json:"status" gorm:"default:0"`
	CreatedAt   time.Time  `json:"created_at"`
	UpdatedAt   time.Time  `json:"updated_at"`
	DeletedAt   gorm.DeletedAt `json:"-" gorm:"index"`
}

func (SyncJob) TableName() string {
	return "sync_jobs"
}

type SyncStatus int

const (
	SyncStatusPending SyncStatus = iota
	SyncStatusRunning
	SyncStatusCompleted
	SyncStatusFailed
)

func (s SyncStatus) String() string {
	switch s {
	case SyncStatusPending:
		return "pending"
	case SyncStatusRunning:
		return "running"
	case SyncStatusCompleted:
		return "completed"
	case SyncStatusFailed:
		return "failed"
	default:
		return "unknown"
	}
}
