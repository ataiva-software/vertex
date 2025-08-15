package flow

import (
	"database/sql/driver"
	"encoding/json"
	"errors"
	"time"

	"gorm.io/gorm"
)

// JSONMap is a custom type for handling JSON maps in GORM
type JSONMap map[string]interface{}

// Scan implements the Scanner interface for database deserialization
func (j *JSONMap) Scan(value interface{}) error {
	if value == nil {
		*j = JSONMap{}
		return nil
	}

	switch v := value.(type) {
	case []byte:
		return json.Unmarshal(v, j)
	case string:
		return json.Unmarshal([]byte(v), j)
	default:
		return errors.New("cannot scan into JSONMap")
	}
}

// Value implements the Valuer interface for database serialization
func (j JSONMap) Value() (driver.Value, error) {
	if len(j) == 0 {
		return "{}", nil
	}
	return json.Marshal(j)
}

// Workflow represents a workflow definition
type Workflow struct {
	ID          uint           `json:"id" gorm:"primaryKey"`
	Name        string         `json:"name" gorm:"not null"`
	Description string         `json:"description"`
	UserID      string         `json:"user_id" gorm:"index;not null"`
	Status      WorkflowStatus `json:"status" gorm:"default:0"`
	Steps       []WorkflowStep `json:"steps" gorm:"foreignKey:WorkflowID;constraint:OnDelete:CASCADE"`
	Variables   JSONMap        `json:"variables" gorm:"type:text"`
	CreatedAt   time.Time      `json:"created_at"`
	UpdatedAt   time.Time      `json:"updated_at"`
	DeletedAt   gorm.DeletedAt `json:"-" gorm:"index"`
}

// TableName returns the table name for the Workflow model
func (Workflow) TableName() string {
	return "workflows"
}

// WorkflowStatus represents the status of a workflow
type WorkflowStatus int

const (
	WorkflowStatusDraft WorkflowStatus = iota
	WorkflowStatusActive
	WorkflowStatusInactive
)

// String returns the string representation of WorkflowStatus
func (w WorkflowStatus) String() string {
	switch w {
	case WorkflowStatusDraft:
		return "draft"
	case WorkflowStatusActive:
		return "active"
	case WorkflowStatusInactive:
		return "inactive"
	default:
		return "unknown"
	}
}

// WorkflowStep represents a step in a workflow
type WorkflowStep struct {
	ID         uint     `json:"id" gorm:"primaryKey"`
	WorkflowID uint     `json:"workflow_id" gorm:"index;not null"`
	Name       string   `json:"name" gorm:"not null"`
	Type       StepType `json:"type" gorm:"not null"`
	Config     JSONMap  `json:"config" gorm:"type:text"`
	Order      int      `json:"order" gorm:"not null"`
	DependsOn  []uint   `json:"depends_on" gorm:"serializer:json"`
	Timeout    int      `json:"timeout" gorm:"default:300"` // seconds
	Retries    int      `json:"retries" gorm:"default:0"`
	CreatedAt  time.Time `json:"created_at"`
	UpdatedAt  time.Time `json:"updated_at"`
}

// TableName returns the table name for the WorkflowStep model
func (WorkflowStep) TableName() string {
	return "workflow_steps"
}

// StepType represents the type of a workflow step
type StepType int

const (
	StepTypeCommand StepType = iota
	StepTypeHTTP
	StepTypeScript
	StepTypeCondition
	StepTypeLoop
	StepTypeParallel
)

// String returns the string representation of StepType
func (s StepType) String() string {
	switch s {
	case StepTypeCommand:
		return "command"
	case StepTypeHTTP:
		return "http"
	case StepTypeScript:
		return "script"
	case StepTypeCondition:
		return "condition"
	case StepTypeLoop:
		return "loop"
	case StepTypeParallel:
		return "parallel"
	default:
		return "unknown"
	}
}

// WorkflowExecution represents an execution of a workflow
type WorkflowExecution struct {
	ID          uint            `json:"id" gorm:"primaryKey"`
	WorkflowID  uint            `json:"workflow_id" gorm:"index;not null"`
	UserID      string          `json:"user_id" gorm:"index;not null"`
	Status      ExecutionStatus `json:"status" gorm:"default:0"`
	Input       JSONMap         `json:"input" gorm:"type:text"`
	Output      JSONMap         `json:"output" gorm:"type:text"`
	Error       string          `json:"error"`
	StartedAt   time.Time       `json:"started_at"`
	CompletedAt *time.Time      `json:"completed_at"`
	Steps       []StepExecution `json:"steps" gorm:"foreignKey:ExecutionID;constraint:OnDelete:CASCADE"`
}

// TableName returns the table name for the WorkflowExecution model
func (WorkflowExecution) TableName() string {
	return "workflow_executions"
}

// ExecutionStatus represents the status of a workflow execution
type ExecutionStatus int

const (
	ExecutionStatusPending ExecutionStatus = iota
	ExecutionStatusRunning
	ExecutionStatusCompleted
	ExecutionStatusFailed
	ExecutionStatusCancelled
)

// String returns the string representation of ExecutionStatus
func (e ExecutionStatus) String() string {
	switch e {
	case ExecutionStatusPending:
		return "pending"
	case ExecutionStatusRunning:
		return "running"
	case ExecutionStatusCompleted:
		return "completed"
	case ExecutionStatusFailed:
		return "failed"
	case ExecutionStatusCancelled:
		return "cancelled"
	default:
		return "unknown"
	}
}

// StepExecution represents the execution of a workflow step
type StepExecution struct {
	ID          uint            `json:"id" gorm:"primaryKey"`
	ExecutionID uint            `json:"execution_id" gorm:"index;not null"`
	StepID      uint            `json:"step_id" gorm:"index;not null"`
	Status      ExecutionStatus `json:"status" gorm:"default:0"`
	Input       JSONMap         `json:"input" gorm:"type:text"`
	Output      JSONMap         `json:"output" gorm:"type:text"`
	Error       string          `json:"error"`
	StartedAt   time.Time       `json:"started_at"`
	CompletedAt *time.Time      `json:"completed_at"`
	Attempt     int             `json:"attempt" gorm:"default:1"`
}

// TableName returns the table name for the StepExecution model
func (StepExecution) TableName() string {
	return "step_executions"
}

// WorkflowTemplate represents a reusable workflow template
type WorkflowTemplate struct {
	ID          uint      `json:"id" gorm:"primaryKey"`
	Name        string    `json:"name" gorm:"not null"`
	Description string    `json:"description"`
	Category    string    `json:"category"`
	Template    JSONMap   `json:"template" gorm:"type:text"`
	Public      bool      `json:"public" gorm:"default:false"`
	CreatedBy   string    `json:"created_by" gorm:"not null"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}

// TableName returns the table name for the WorkflowTemplate model
func (WorkflowTemplate) TableName() string {
	return "workflow_templates"
}
