package flow

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"gorm.io/gorm"
)

// Service provides workflow management functionality
type Service struct {
	db *gorm.DB
}

// NewService creates a new flow service
func NewService() *Service {
	return &Service{}
}

// SetDB sets the database connection
func (s *Service) SetDB(db *gorm.DB) {
	s.db = db
}

// CreateWorkflow creates a new workflow
func (s *Service) CreateWorkflow(ctx context.Context, workflow *Workflow) error {
	if err := s.validateWorkflow(workflow); err != nil {
		return err
	}

	// Set default status
	if workflow.Status == 0 {
		workflow.Status = WorkflowStatusDraft
	}

	// Initialize variables if nil
	if workflow.Variables == nil {
		workflow.Variables = make(JSONMap)
	}

	// Validate and set step orders
	for i := range workflow.Steps {
		if err := s.validateStep(&workflow.Steps[i]); err != nil {
			return err
		}
		if workflow.Steps[i].Config == nil {
			workflow.Steps[i].Config = make(JSONMap)
		}
	}

	if err := s.db.Create(workflow).Error; err != nil {
		return fmt.Errorf("failed to create workflow: %w", err)
	}

	return nil
}

// GetWorkflow retrieves a workflow by ID
func (s *Service) GetWorkflow(ctx context.Context, userID string, workflowID uint) (*Workflow, error) {
	var workflow Workflow
	err := s.db.Preload("Steps").Where("id = ? AND user_id = ?", workflowID, userID).First(&workflow).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, fmt.Errorf("workflow %d not found", workflowID)
	}
	if err != nil {
		return nil, fmt.Errorf("failed to retrieve workflow: %w", err)
	}

	return &workflow, nil
}

// ListWorkflows returns all workflows for a user
func (s *Service) ListWorkflows(ctx context.Context, userID string) ([]*Workflow, error) {
	var workflows []*Workflow
	err := s.db.Preload("Steps").Where("user_id = ?", userID).Find(&workflows).Error
	if err != nil {
		return nil, fmt.Errorf("failed to list workflows: %w", err)
	}

	return workflows, nil
}

// UpdateWorkflow updates an existing workflow
func (s *Service) UpdateWorkflow(ctx context.Context, userID string, workflow *Workflow) error {
	if err := s.validateWorkflow(workflow); err != nil {
		return err
	}

	// Check if workflow exists and belongs to user
	var existing Workflow
	err := s.db.Where("id = ? AND user_id = ?", workflow.ID, userID).First(&existing).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return fmt.Errorf("workflow %d not found", workflow.ID)
	}
	if err != nil {
		return fmt.Errorf("failed to find workflow: %w", err)
	}

	// Update workflow in transaction
	return s.db.Transaction(func(tx *gorm.DB) error {
		// Delete existing steps
		if err := tx.Where("workflow_id = ?", workflow.ID).Delete(&WorkflowStep{}).Error; err != nil {
			return fmt.Errorf("failed to delete existing steps: %w", err)
		}

		// Validate and prepare new steps
		for i := range workflow.Steps {
			if err := s.validateStep(&workflow.Steps[i]); err != nil {
				return err
			}
			workflow.Steps[i].WorkflowID = workflow.ID
			if workflow.Steps[i].Config == nil {
				workflow.Steps[i].Config = make(JSONMap)
			}
		}

		// Update workflow
		if err := tx.Save(workflow).Error; err != nil {
			return fmt.Errorf("failed to update workflow: %w", err)
		}

		return nil
	})
}

// DeleteWorkflow deletes a workflow
func (s *Service) DeleteWorkflow(ctx context.Context, userID string, workflowID uint) error {
	// Check if workflow exists and belongs to user
	var workflow Workflow
	err := s.db.Where("id = ? AND user_id = ?", workflowID, userID).First(&workflow).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return fmt.Errorf("workflow %d not found", workflowID)
	}
	if err != nil {
		return fmt.Errorf("failed to find workflow: %w", err)
	}

	// Delete workflow (soft delete)
	if err := s.db.Delete(&workflow).Error; err != nil {
		return fmt.Errorf("failed to delete workflow: %w", err)
	}

	return nil
}

// ExecuteWorkflow starts a new workflow execution
func (s *Service) ExecuteWorkflow(ctx context.Context, userID string, workflowID uint, input map[string]interface{}) (*WorkflowExecution, error) {
	// Check if workflow exists and belongs to user
	var workflow Workflow
	err := s.db.Preload("Steps").Where("id = ? AND user_id = ?", workflowID, userID).First(&workflow).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, fmt.Errorf("workflow %d not found", workflowID)
	}
	if err != nil {
		return nil, fmt.Errorf("failed to find workflow: %w", err)
	}

	// Create execution record
	execution := &WorkflowExecution{
		WorkflowID: workflowID,
		UserID:     userID,
		Status:     ExecutionStatusRunning,
		Input:      JSONMap(input),
		Output:     make(JSONMap),
		StartedAt:  time.Now(),
	}

	if err := s.db.Create(execution).Error; err != nil {
		return nil, fmt.Errorf("failed to create execution: %w", err)
	}

	// In a real implementation, this would start the actual workflow execution
	// For now, we'll just create the execution record

	return execution, nil
}

// GetExecutionStatus retrieves the status of a workflow execution
func (s *Service) GetExecutionStatus(ctx context.Context, userID string, executionID uint) (*WorkflowExecution, error) {
	var execution WorkflowExecution
	err := s.db.Preload("Steps").Where("id = ? AND user_id = ?", executionID, userID).First(&execution).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, fmt.Errorf("execution %d not found", executionID)
	}
	if err != nil {
		return nil, fmt.Errorf("failed to retrieve execution: %w", err)
	}

	return &execution, nil
}

// ListExecutions returns all executions for a workflow
func (s *Service) ListExecutions(ctx context.Context, userID string, workflowID uint) ([]*WorkflowExecution, error) {
	var executions []*WorkflowExecution
	err := s.db.Where("workflow_id = ? AND user_id = ?", workflowID, userID).
		Order("started_at DESC").
		Find(&executions).Error
	if err != nil {
		return nil, fmt.Errorf("failed to list executions: %w", err)
	}

	return executions, nil
}

// CancelExecution cancels a running workflow execution
func (s *Service) CancelExecution(ctx context.Context, userID string, executionID uint) error {
	// Check if execution exists and belongs to user
	var execution WorkflowExecution
	err := s.db.Where("id = ? AND user_id = ?", executionID, userID).First(&execution).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return fmt.Errorf("execution %d not found", executionID)
	}
	if err != nil {
		return fmt.Errorf("failed to find execution: %w", err)
	}

	// Update status to cancelled
	now := time.Now()
	execution.Status = ExecutionStatusCancelled
	execution.CompletedAt = &now

	if err := s.db.Save(&execution).Error; err != nil {
		return fmt.Errorf("failed to cancel execution: %w", err)
	}

	return nil
}

// validateWorkflow validates a workflow before creating/updating
func (s *Service) validateWorkflow(workflow *Workflow) error {
	if strings.TrimSpace(workflow.Name) == "" {
		return errors.New("name is required")
	}
	if strings.TrimSpace(workflow.UserID) == "" {
		return errors.New("user ID is required")
	}
	if len(workflow.Steps) == 0 {
		return errors.New("at least one step is required")
	}

	// Validate steps
	for i, step := range workflow.Steps {
		if err := s.validateStep(&workflow.Steps[i]); err != nil {
			return fmt.Errorf("step %d: %w", i+1, err)
		}
		_ = step // Use step to avoid unused variable warning
	}

	return nil
}

// validateStep validates a workflow step
func (s *Service) validateStep(step *WorkflowStep) error {
	if strings.TrimSpace(step.Name) == "" {
		return errors.New("step name is required")
	}
	if step.Order <= 0 {
		return errors.New("step order must be positive")
	}
	if step.Timeout <= 0 {
		step.Timeout = 300 // Default 5 minutes
	}
	if step.Retries < 0 {
		step.Retries = 0
	}

	return nil
}
