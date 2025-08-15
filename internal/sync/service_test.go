package sync

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

	err = db.AutoMigrate(&SyncJob{})
	require.NoError(t, err)

	return db
}

func TestSyncService(t *testing.T) {
	t.Run("should create new sync service", func(t *testing.T) {
		service := NewService()
		assert.NotNil(t, service)
	})
}

func TestSyncJobOperations(t *testing.T) {
	db := setupTestDB(t)
	service := NewService()
	service.SetDB(db)
	ctx := context.Background()

	t.Run("should create sync job", func(t *testing.T) {
		job := &SyncJob{
			Name:        "AWS to GCP Sync",
			UserID:      "user1",
			Source:      "aws://bucket1",
			Destination: "gcp://bucket2",
		}

		err := service.CreateSyncJob(ctx, job)
		require.NoError(t, err)
		assert.NotZero(t, job.ID)
		assert.Equal(t, SyncStatusPending, job.Status)
	})

	t.Run("should get sync jobs", func(t *testing.T) {
		jobs := []*SyncJob{
			{Name: "Job 1", UserID: "user2", Source: "s3://bucket1", Destination: "gcs://bucket2"},
			{Name: "Job 2", UserID: "user2", Source: "azure://container1", Destination: "s3://bucket3"},
		}

		for _, job := range jobs {
			err := service.CreateSyncJob(ctx, job)
			require.NoError(t, err)
		}

		retrieved, err := service.GetSyncJobs(ctx, "user2")
		require.NoError(t, err)
		assert.Len(t, retrieved, 2)
	})
}
