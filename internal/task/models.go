package task

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

// Task represents a task in the system
type Task struct {
	ID          uint        `json:"id" gorm:"primaryKey"`
	Name        string      `json:"name" gorm:"not null"`
	Description string      `json:"description"`
	Type        string      `json:"type" gorm:"not null"`
	UserID      string      `json:"user_id" gorm:"index;not null"`
	Status      TaskStatus  `json:"status" gorm:"default:0"`
	Priority    int         `json:"priority" gorm:"default:0"`
	Config      JSONMap     `json:"config" gorm:"type:text"`
	Result      JSONMap     `json:"result" gorm:"type:text"`
	Error       string      `json:"error"`
	ScheduledAt *time.Time  `json:"scheduled_at"`
	StartedAt   *time.Time  `json:"started_at"`
	CompletedAt *time.Time  `json:"completed_at"`
	CreatedAt   time.Time   `json:"created_at"`
	UpdatedAt   time.Time   `json:"updated_at"`
	DeletedAt   gorm.DeletedAt `json:"-" gorm:"index"`
}

// TableName returns the table name for the Task model
func (Task) TableName() string {
	return "tasks"
}

// TaskStatus represents the status of a task
type TaskStatus int

const (
	TaskStatusPending TaskStatus = iota
	TaskStatusRunning
	TaskStatusCompleted
	TaskStatusFailed
	TaskStatusCancelled
)

// String returns the string representation of TaskStatus
func (t TaskStatus) String() string {
	switch t {
	case TaskStatusPending:
		return "pending"
	case TaskStatusRunning:
		return "running"
	case TaskStatusCompleted:
		return "completed"
	case TaskStatusFailed:
		return "failed"
	case TaskStatusCancelled:
		return "cancelled"
	default:
		return "unknown"
	}
}
