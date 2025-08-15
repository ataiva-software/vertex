package monitor

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
)

func setupTestDB(t *testing.T) *gorm.DB {
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)

	err = db.AutoMigrate(&Metric{}, &Alert{})
	require.NoError(t, err)

	return db
}

func TestMonitorService(t *testing.T) {
	t.Run("should create new monitor service", func(t *testing.T) {
		service := NewService()
		assert.NotNil(t, service)
	})
}

func TestMetricOperations(t *testing.T) {
	db := setupTestDB(t)
	service := NewService()
	service.SetDB(db)
	ctx := context.Background()

	t.Run("should create metric", func(t *testing.T) {
		metric := &Metric{
			ServiceName: "vault",
			Name:        "cpu_usage",
			Value:       75.5,
			Unit:        "percent",
			Timestamp:   time.Now(),
		}

		err := service.CreateMetric(ctx, metric)
		require.NoError(t, err)
		assert.NotZero(t, metric.ID)
	})

	t.Run("should get metrics by service", func(t *testing.T) {
		metrics := []*Metric{
			{ServiceName: "vault", Name: "memory_usage", Value: 512, Unit: "MB", Timestamp: time.Now()},
			{ServiceName: "vault", Name: "disk_usage", Value: 1024, Unit: "MB", Timestamp: time.Now()},
		}

		for _, metric := range metrics {
			err := service.CreateMetric(ctx, metric)
			require.NoError(t, err)
		}

		retrieved, err := service.GetMetrics(ctx, "vault")
		require.NoError(t, err)
		assert.True(t, len(retrieved) >= 2)
	})
}

func TestAlertOperations(t *testing.T) {
	db := setupTestDB(t)
	service := NewService()
	service.SetDB(db)
	ctx := context.Background()

	t.Run("should create alert", func(t *testing.T) {
		alert := &Alert{
			Name:        "High CPU Usage",
			Description: "Alert when CPU usage exceeds 80%",
			UserID:      "user1",
			Condition:   "cpu_usage > 80",
		}

		err := service.CreateAlert(ctx, alert)
		require.NoError(t, err)
		assert.NotZero(t, alert.ID)
		assert.Equal(t, AlertStatusActive, alert.Status)
	})

	t.Run("should get user alerts", func(t *testing.T) {
		alerts := []*Alert{
			{Name: "Alert 1", UserID: "user2", Condition: "memory > 1GB"},
			{Name: "Alert 2", UserID: "user2", Condition: "disk > 10GB"},
		}

		for _, alert := range alerts {
			err := service.CreateAlert(ctx, alert)
			require.NoError(t, err)
		}

		retrieved, err := service.GetAlerts(ctx, "user2")
		require.NoError(t, err)
		assert.Len(t, retrieved, 2)
	})
}
