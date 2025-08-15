package vault

import (
	"database/sql/driver"
	"encoding/json"
	"errors"
	"time"

	"gorm.io/gorm"
)

// StringSlice is a custom type for handling string slices in GORM
type StringSlice []string

// Scan implements the Scanner interface for database deserialization
func (s *StringSlice) Scan(value interface{}) error {
	if value == nil {
		*s = StringSlice{}
		return nil
	}

	switch v := value.(type) {
	case []byte:
		return json.Unmarshal(v, s)
	case string:
		return json.Unmarshal([]byte(v), s)
	default:
		return errors.New("cannot scan into StringSlice")
	}
}

// Value implements the Valuer interface for database serialization
func (s StringSlice) Value() (driver.Value, error) {
	if len(s) == 0 {
		return "[]", nil
	}
	return json.Marshal(s)
}

// Secret represents a stored secret
type Secret struct {
	ID          uint        `json:"id" gorm:"primaryKey"`
	UserID      string      `json:"user_id" gorm:"index;not null"`
	Key         string      `json:"key" gorm:"not null"`
	Value       string      `json:"value,omitempty" gorm:"not null"` // Encrypted
	Description string      `json:"description"`
	Tags        StringSlice `json:"tags" gorm:"type:text"`
	CreatedAt   time.Time   `json:"created_at"`
	UpdatedAt   time.Time   `json:"updated_at"`
	DeletedAt   gorm.DeletedAt `json:"-" gorm:"index"`
}

// TableName returns the table name for the Secret model
func (Secret) TableName() string {
	return "secrets"
}

// SecretListItem represents a secret in list operations (without value)
type SecretListItem struct {
	Key         string      `json:"key"`
	Description string      `json:"description"`
	Tags        StringSlice `json:"tags"`
	CreatedAt   time.Time   `json:"created_at"`
	UpdatedAt   time.Time   `json:"updated_at"`
}

// AuditLog represents an audit log entry for secret operations
type AuditLog struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	UserID    string    `json:"user_id" gorm:"index;not null"`
	SecretKey string    `json:"secret_key" gorm:"not null"`
	Action    string    `json:"action" gorm:"not null"` // CREATE, READ, UPDATE, DELETE
	IPAddress string    `json:"ip_address"`
	UserAgent string    `json:"user_agent"`
	CreatedAt time.Time `json:"created_at"`
}

// TableName returns the table name for the AuditLog model
func (AuditLog) TableName() string {
	return "audit_logs"
}
