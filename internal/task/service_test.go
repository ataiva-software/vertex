package task

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

	err = db.AutoMigrate(&Task{})
	require.NoError(t, err)

	return db
}

func TestTaskService(t *testing.T) {
	t.Run("should create new task service", func(t *testing.T) {
		service := NewService()
		assert.NotNil(t, service)
	})
}

func TestTaskOperations(t *testing.T) {
	db := setupTestDB(t)
	service := NewService()
	service.SetDB(db)
	ctx := context.Background()

	t.Run("should create task", func(t *testing.T) {
		task := &Task{
			Name:        "Test Task",
			Description: "Test task description",
			Type:        "command",
			UserID:      "user1",
			Config:      JSONMap{"command": "echo hello"},
		}

		err := service.CreateTask(ctx, task)
		require.NoError(t, err)
		assert.NotZero(t, task.ID)
		assert.Equal(t, TaskStatusPending, task.Status)
	})

	t.Run("should get task by ID", func(t *testing.T) {
		task := &Task{
			Name:   "Get Task Test",
			Type:   "http",
			UserID: "user1",
		}

		err := service.CreateTask(ctx, task)
		require.NoError(t, err)

		retrieved, err := service.GetTask(ctx, "user1", task.ID)
		require.NoError(t, err)
		assert.Equal(t, task.Name, retrieved.Name)
		assert.Equal(t, task.Type, retrieved.Type)
	})

	t.Run("should list user tasks", func(t *testing.T) {
		tasks := []*Task{
			{Name: "Task 1", Type: "command", UserID: "user2"},
			{Name: "Task 2", Type: "http", UserID: "user2"},
		}

		for _, task := range tasks {
			err := service.CreateTask(ctx, task)
			require.NoError(t, err)
		}

		list, err := service.ListTasks(ctx, "user2")
		require.NoError(t, err)
		assert.Len(t, list, 2)
	})

	t.Run("should update task status", func(t *testing.T) {
		task := &Task{
			Name:   "Status Update Test",
			Type:   "command",
			UserID: "user3",
		}

		err := service.CreateTask(ctx, task)
		require.NoError(t, err)

		err = service.UpdateTaskStatus(ctx, "user3", task.ID, TaskStatusCompleted)
		require.NoError(t, err)

		updated, err := service.GetTask(ctx, "user3", task.ID)
		require.NoError(t, err)
		assert.Equal(t, TaskStatusCompleted, updated.Status)
		assert.NotNil(t, updated.CompletedAt)
	})

	t.Run("should delete task", func(t *testing.T) {
		task := &Task{
			Name:   "Delete Test",
			Type:   "command",
			UserID: "user4",
		}

		err := service.CreateTask(ctx, task)
		require.NoError(t, err)

		err = service.DeleteTask(ctx, "user4", task.ID)
		require.NoError(t, err)

		_, err = service.GetTask(ctx, "user4", task.ID)
		assert.Error(t, err)
		assert.Contains(t, err.Error(), "not found")
	})
}
