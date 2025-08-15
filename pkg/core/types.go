package core

import (
	"errors"
	"fmt"
	"strings"
	"time"
)

// ServiceStatus represents the current status of a service
type ServiceStatus int

const (
	ServiceStatusStarting ServiceStatus = iota
	ServiceStatusHealthy
	ServiceStatusUnhealthy
	ServiceStatusStopping
	ServiceStatusStopped
)

// String returns the string representation of ServiceStatus
func (s ServiceStatus) String() string {
	switch s {
	case ServiceStatusStarting:
		return "starting"
	case ServiceStatusHealthy:
		return "healthy"
	case ServiceStatusUnhealthy:
		return "unhealthy"
	case ServiceStatusStopping:
		return "stopping"
	case ServiceStatusStopped:
		return "stopped"
	default:
		return "unknown"
	}
}

// ServiceInfo contains information about a service
type ServiceInfo struct {
	Name            string        `json:"name"`
	Version         string        `json:"version"`
	Port            int           `json:"port"`
	Status          ServiceStatus `json:"status"`
	StartTime       time.Time     `json:"start_time"`
	LastHealthCheck time.Time     `json:"last_health_check"`
}

// NewServiceInfo creates a new ServiceInfo instance
func NewServiceInfo(name, version string, port int) *ServiceInfo {
	now := time.Now()
	return &ServiceInfo{
		Name:            name,
		Version:         version,
		Port:            port,
		Status:          ServiceStatusStarting,
		StartTime:       now,
		LastHealthCheck: now,
	}
}

// SetStatus updates the service status and health check timestamp
func (s *ServiceInfo) SetStatus(status ServiceStatus) {
	s.Status = status
	s.LastHealthCheck = time.Now()
}

// Uptime returns the duration since the service started
func (s *ServiceInfo) Uptime() time.Duration {
	return time.Since(s.StartTime)
}

// HealthStatus represents the health status of a service or component
type HealthStatus struct {
	Healthy   bool                   `json:"healthy"`
	Message   string                 `json:"message"`
	Timestamp time.Time              `json:"timestamp"`
	Details   map[string]interface{} `json:"details,omitempty"`
}

// NewHealthStatus creates a new HealthStatus instance
func NewHealthStatus(healthy bool, message string) *HealthStatus {
	return &HealthStatus{
		Healthy:   healthy,
		Message:   message,
		Timestamp: time.Now(),
		Details:   make(map[string]interface{}),
	}
}

// NewHealthStatusWithDetails creates a new HealthStatus instance with details
func NewHealthStatusWithDetails(healthy bool, message string, details map[string]interface{}) *HealthStatus {
	return &HealthStatus{
		Healthy:   healthy,
		Message:   message,
		Timestamp: time.Now(),
		Details:   details,
	}
}

// Result represents the result of an operation
type Result struct {
	Success   bool        `json:"success"`
	Data      interface{} `json:"data,omitempty"`
	Error     string      `json:"error,omitempty"`
	Timestamp time.Time   `json:"timestamp"`
}

// Success creates a successful result
func Success(data interface{}) *Result {
	return &Result{
		Success:   true,
		Data:      data,
		Timestamp: time.Now(),
	}
}

// Error creates an error result
func Error(message string) *Result {
	return &Result{
		Success:   false,
		Error:     message,
		Timestamp: time.Now(),
	}
}

// ErrorFromErr creates an error result from a Go error
func ErrorFromErr(err error) *Result {
	return &Result{
		Success:   false,
		Error:     err.Error(),
		Timestamp: time.Now(),
	}
}

// ValidateRequired validates that a string field is not empty
func ValidateRequired(value, fieldName string) error {
	if strings.TrimSpace(value) == "" {
		return fmt.Errorf("%s is required", fieldName)
	}
	return nil
}

// ValidatePort validates that a port number is valid
func ValidatePort(port int) error {
	if port < 1 || port > 65535 {
		return errors.New("port must be between 1 and 65535")
	}
	return nil
}
