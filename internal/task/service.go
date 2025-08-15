package task

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"gorm.io/gorm"
)

// Service provides task orchestration functionality
type Service struct {
	db *gorm.DB
}

// NewService creates a new task service
func NewService() *Service {
	return &Service{}
}

// SetDB sets the database connection
func (s *Service) SetDB(db *gorm.DB) {
	s.db = db
}

// CreateTask creates a new task
func (s *Service) CreateTask(ctx context.Context, task *Task) error {
	if err := s.validateTask(task); err != nil {
		return err
	}

	if task.Status == 0 {
		task.Status = TaskStatusPending
	}

	if err := s.db.Create(task).Error; err != nil {
		return fmt.Errorf("failed to create task: %w", err)
	}

	return nil
}

// GetTask retrieves a task by ID
func (s *Service) GetTask(ctx context.Context, userID string, taskID uint) (*Task, error) {
	var task Task
	err := s.db.Where("id = ? AND user_id = ?", taskID, userID).First(&task).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, fmt.Errorf("task %d not found", taskID)
	}
	if err != nil {
		return nil, fmt.Errorf("failed to retrieve task: %w", err)
	}

	return &task, nil
}

// ListTasks returns all tasks for a user
func (s *Service) ListTasks(ctx context.Context, userID string) ([]*Task, error) {
	var tasks []*Task
	err := s.db.Where("user_id = ?", userID).Find(&tasks).Error
	if err != nil {
		return nil, fmt.Errorf("failed to list tasks: %w", err)
	}

	return tasks, nil
}

// UpdateTaskStatus updates the status of a task
func (s *Service) UpdateTaskStatus(ctx context.Context, userID string, taskID uint, status TaskStatus) error {
	var task Task
	err := s.db.Where("id = ? AND user_id = ?", taskID, userID).First(&task).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return fmt.Errorf("task %d not found", taskID)
	}
	if err != nil {
		return fmt.Errorf("failed to find task: %w", err)
	}

	task.Status = status
	if status == TaskStatusCompleted || status == TaskStatusFailed {
		now := time.Now()
		task.CompletedAt = &now
	}

	if err := s.db.Save(&task).Error; err != nil {
		return fmt.Errorf("failed to update task status: %w", err)
	}

	return nil
}

// DeleteTask deletes a task
func (s *Service) DeleteTask(ctx context.Context, userID string, taskID uint) error {
	var task Task
	err := s.db.Where("id = ? AND user_id = ?", taskID, userID).First(&task).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return fmt.Errorf("task %d not found", taskID)
	}
	if err != nil {
		return fmt.Errorf("failed to find task: %w", err)
	}

	if err := s.db.Delete(&task).Error; err != nil {
		return fmt.Errorf("failed to delete task: %w", err)
	}

	return nil
}

// validateTask validates a task before creating
func (s *Service) validateTask(task *Task) error {
	if strings.TrimSpace(task.Name) == "" {
		return errors.New("name is required")
	}
	if strings.TrimSpace(task.UserID) == "" {
		return errors.New("user ID is required")
	}
	if strings.TrimSpace(task.Type) == "" {
		return errors.New("type is required")
	}
	return nil
}
