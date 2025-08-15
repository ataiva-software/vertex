package core

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestServiceInfo(t *testing.T) {
	t.Run("should create service info with required fields", func(t *testing.T) {
		info := NewServiceInfo("vault", "1.0.0", 8080)
		
		assert.Equal(t, "vault", info.Name)
		assert.Equal(t, "1.0.0", info.Version)
		assert.Equal(t, 8080, info.Port)
		assert.Equal(t, ServiceStatusStarting, info.Status)
		assert.WithinDuration(t, time.Now(), info.StartTime, time.Second)
	})

	t.Run("should update service status", func(t *testing.T) {
		info := NewServiceInfo("vault", "1.0.0", 8080)
		
		info.SetStatus(ServiceStatusHealthy)
		
		assert.Equal(t, ServiceStatusHealthy, info.Status)
		assert.WithinDuration(t, time.Now(), info.LastHealthCheck, time.Second)
	})

	t.Run("should calculate uptime", func(t *testing.T) {
		info := NewServiceInfo("vault", "1.0.0", 8080)
		time.Sleep(10 * time.Millisecond)
		
		uptime := info.Uptime()
		
		assert.True(t, uptime > 0)
		assert.True(t, uptime < time.Second)
	})
}

func TestHealthStatus(t *testing.T) {
	t.Run("should create healthy status", func(t *testing.T) {
		status := NewHealthStatus(true, "All systems operational")
		
		assert.True(t, status.Healthy)
		assert.Equal(t, "All systems operational", status.Message)
		assert.WithinDuration(t, time.Now(), status.Timestamp, time.Second)
		assert.Empty(t, status.Details)
	})

	t.Run("should create unhealthy status with details", func(t *testing.T) {
		details := map[string]interface{}{
			"database": "connection failed",
			"redis":    "timeout",
		}
		
		status := NewHealthStatusWithDetails(false, "Service degraded", details)
		
		assert.False(t, status.Healthy)
		assert.Equal(t, "Service degraded", status.Message)
		assert.Equal(t, details, status.Details)
	})
}

func TestResult(t *testing.T) {
	t.Run("should create success result", func(t *testing.T) {
		data := map[string]string{"key": "value"}
		result := Success(data)
		
		assert.True(t, result.Success)
		assert.Equal(t, data, result.Data)
		assert.Empty(t, result.Error)
		assert.WithinDuration(t, time.Now(), result.Timestamp, time.Second)
	})

	t.Run("should create error result", func(t *testing.T) {
		err := "something went wrong"
		result := Error(err)
		
		assert.False(t, result.Success)
		assert.Equal(t, err, result.Error)
		assert.Nil(t, result.Data)
	})

	t.Run("should create error result from Go error", func(t *testing.T) {
		err := assert.AnError
		result := ErrorFromErr(err)
		
		assert.False(t, result.Success)
		assert.Equal(t, err.Error(), result.Error)
		assert.Nil(t, result.Data)
	})
}

func TestServiceStatus(t *testing.T) {
	tests := []struct {
		status   ServiceStatus
		expected string
	}{
		{ServiceStatusStarting, "starting"},
		{ServiceStatusHealthy, "healthy"},
		{ServiceStatusUnhealthy, "unhealthy"},
		{ServiceStatusStopping, "stopping"},
		{ServiceStatusStopped, "stopped"},
	}

	for _, tt := range tests {
		t.Run("should convert status to string", func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.status.String())
		})
	}
}

func TestValidateRequired(t *testing.T) {
	t.Run("should pass validation for non-empty string", func(t *testing.T) {
		err := ValidateRequired("test", "field")
		assert.NoError(t, err)
	})

	t.Run("should fail validation for empty string", func(t *testing.T) {
		err := ValidateRequired("", "field")
		require.Error(t, err)
		assert.Contains(t, err.Error(), "field is required")
	})

	t.Run("should fail validation for whitespace-only string", func(t *testing.T) {
		err := ValidateRequired("   ", "field")
		require.Error(t, err)
		assert.Contains(t, err.Error(), "field is required")
	})
}

func TestValidatePort(t *testing.T) {
	t.Run("should pass validation for valid port", func(t *testing.T) {
		err := ValidatePort(8080)
		assert.NoError(t, err)
	})

	t.Run("should fail validation for port too low", func(t *testing.T) {
		err := ValidatePort(0)
		require.Error(t, err)
		assert.Contains(t, err.Error(), "port must be between 1 and 65535")
	})

	t.Run("should fail validation for port too high", func(t *testing.T) {
		err := ValidatePort(70000)
		require.Error(t, err)
		assert.Contains(t, err.Error(), "port must be between 1 and 65535")
	})
}
