package monitor

import (
	"time"

	"gorm.io/gorm"
)

type Metric struct {
	ID          uint      `json:"id" gorm:"primaryKey"`
	ServiceName string    `json:"service_name" gorm:"index;not null"`
	Name        string    `json:"name" gorm:"not null"`
	Value       float64   `json:"value"`
	Unit        string    `json:"unit"`
	Tags        string    `json:"tags"`
	Timestamp   time.Time `json:"timestamp"`
	CreatedAt   time.Time `json:"created_at"`
}

func (Metric) TableName() string {
	return "metrics"
}

type Alert struct {
	ID          uint        `json:"id" gorm:"primaryKey"`
	Name        string      `json:"name" gorm:"not null"`
	Description string      `json:"description"`
	UserID      string      `json:"user_id" gorm:"index;not null"`
	Condition   string      `json:"condition" gorm:"not null"`
	Status      AlertStatus `json:"status" gorm:"default:0"`
	CreatedAt   time.Time   `json:"created_at"`
	UpdatedAt   time.Time   `json:"updated_at"`
	DeletedAt   gorm.DeletedAt `json:"-" gorm:"index"`
}

func (Alert) TableName() string {
	return "alerts"
}

type AlertStatus int

const (
	AlertStatusActive AlertStatus = iota
	AlertStatusInactive
	AlertStatusTriggered
)

func (a AlertStatus) String() string {
	switch a {
	case AlertStatusActive:
		return "active"
	case AlertStatusInactive:
		return "inactive"
	case AlertStatusTriggered:
		return "triggered"
	default:
		return "unknown"
	}
}
