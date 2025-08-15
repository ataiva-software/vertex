package insight

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
)

func setupTestDB(t *testing.T) *gorm.DB {
	db, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	require.NoError(t, err)

	err = db.AutoMigrate(&Report{})
	require.NoError(t, err)

	return db
}

func TestInsightService(t *testing.T) {
	t.Run("should create new insight service", func(t *testing.T) {
		service := NewService()
		assert.NotNil(t, service)
	})
}

func TestReportOperations(t *testing.T) {
	db := setupTestDB(t)
	service := NewService()
	service.SetDB(db)
	ctx := context.Background()

	t.Run("should create report", func(t *testing.T) {
		report := &Report{
			Name:        "Monthly Report",
			Description: "Monthly analytics report",
			UserID:      "user1",
			Type:        "analytics",
		}

		err := service.CreateReport(ctx, report)
		require.NoError(t, err)
		assert.NotZero(t, report.ID)
		assert.Equal(t, ReportStatusPending, report.Status)
	})

	t.Run("should get reports", func(t *testing.T) {
		reports := []*Report{
			{Name: "Report 1", UserID: "user2", Type: "performance"},
			{Name: "Report 2", UserID: "user2", Type: "security"},
		}

		for _, report := range reports {
			err := service.CreateReport(ctx, report)
			require.NoError(t, err)
		}

		retrieved, err := service.GetReports(ctx, "user2")
		require.NoError(t, err)
		assert.Len(t, retrieved, 2)
	})
}
