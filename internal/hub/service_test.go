package hub

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

	err = db.AutoMigrate(&Integration{})
	require.NoError(t, err)

	return db
}

func TestHubService(t *testing.T) {
	t.Run("should create new hub service", func(t *testing.T) {
		service := NewService()
		assert.NotNil(t, service)
	})
}

func TestIntegrationOperations(t *testing.T) {
	db := setupTestDB(t)
	service := NewService()
	service.SetDB(db)
	ctx := context.Background()

	t.Run("should create integration", func(t *testing.T) {
		integration := &Integration{
			Name:        "GitHub Integration",
			Description: "GitHub webhook integration",
			UserID:      "user1",
			Type:        "github",
		}

		err := service.CreateIntegration(ctx, integration)
		require.NoError(t, err)
		assert.NotZero(t, integration.ID)
		assert.Equal(t, IntegrationStatusActive, integration.Status)
	})

	t.Run("should get integrations", func(t *testing.T) {
		integrations := []*Integration{
			{Name: "Slack Integration", UserID: "user2", Type: "slack"},
			{Name: "JIRA Integration", UserID: "user2", Type: "jira"},
		}

		for _, integration := range integrations {
			err := service.CreateIntegration(ctx, integration)
			require.NoError(t, err)
		}

		retrieved, err := service.GetIntegrations(ctx, "user2")
		require.NoError(t, err)
		assert.Len(t, retrieved, 2)
	})
}
