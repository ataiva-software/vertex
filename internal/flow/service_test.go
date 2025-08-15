package flow

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

	// Auto-migrate the schema
	err = db.AutoMigrate(&Workflow{}, &WorkflowExecution{}, &WorkflowStep{}, &StepExecution{})
	require.NoError(t, err)

	return db
}

func TestFlowService(t *testing.T) {
	t.Run("should create new flow service", func(t *testing.T) {
		service := NewService()
		assert.NotNil(t, service)
	})
}

func TestWorkflowOperations(t *testing.T) {
	db := setupTestDB(t)
	service := NewService()
	service.SetDB(db)
	ctx := context.Background()

	t.Run("should create workflow", func(t *testing.T) {
		workflow := &Workflow{
			Name:        "Deploy Application",
			Description: "Deploy application to production",
			UserID:      "user1",
			Steps: []WorkflowStep{
				{
					Name:        "Build",
					Type:        StepTypeCommand,
					Config:      map[string]interface{}{"command": "make build"},
					Order:       1,
				},
				{
					Name:        "Test",
					Type:        StepTypeCommand,
					Config:      map[string]interface{}{"command": "make test"},
					Order:       2,
				},
				{
					Name:        "Deploy",
					Type:        StepTypeHTTP,
					Config:      map[string]interface{}{"url": "https://api.deploy.com/deploy", "method": "POST"},
					Order:       3,
				},
			},
		}

		err := service.CreateWorkflow(ctx, workflow)
		require.NoError(t, err)
		assert.NotZero(t, workflow.ID)
		assert.WithinDuration(t, time.Now(), workflow.CreatedAt, time.Second)
	})

	t.Run("should get workflow by ID", func(t *testing.T) {
		workflow := &Workflow{
			Name:        "Test Workflow",
			Description: "Test workflow description",
			UserID:      "user1",
			Steps: []WorkflowStep{
				{Name: "Step 1", Type: StepTypeCommand, Order: 1},
			},
		}

		err := service.CreateWorkflow(ctx, workflow)
		require.NoError(t, err)

		retrieved, err := service.GetWorkflow(ctx, "user1", workflow.ID)
		require.NoError(t, err)
		assert.Equal(t, workflow.Name, retrieved.Name)
		assert.Equal(t, workflow.Description, retrieved.Description)
		assert.Len(t, retrieved.Steps, 1)
	})

	t.Run("should list user workflows", func(t *testing.T) {
		workflows := []*Workflow{
			{Name: "Workflow 1", UserID: "user2", Steps: []WorkflowStep{{Name: "Step 1", Type: StepTypeCommand, Order: 1}}},
			{Name: "Workflow 2", UserID: "user2", Steps: []WorkflowStep{{Name: "Step 1", Type: StepTypeCommand, Order: 1}}},
		}

		for _, wf := range workflows {
			err := service.CreateWorkflow(ctx, wf)
			require.NoError(t, err)
		}

		list, err := service.ListWorkflows(ctx, "user2")
		require.NoError(t, err)
		assert.Len(t, list, 2)
	})

	t.Run("should update workflow", func(t *testing.T) {
		workflow := &Workflow{
			Name:        "Original Name",
			Description: "Original Description",
			UserID:      "user3",
			Steps: []WorkflowStep{
				{Name: "Step 1", Type: StepTypeCommand, Order: 1},
			},
		}

		err := service.CreateWorkflow(ctx, workflow)
		require.NoError(t, err)

		workflow.Name = "Updated Name"
		workflow.Description = "Updated Description"
		workflow.Steps = append(workflow.Steps, WorkflowStep{
			Name:  "Step 2",
			Type:  StepTypeHTTP,
			Order: 2,
		})

		err = service.UpdateWorkflow(ctx, "user3", workflow)
		require.NoError(t, err)

		updated, err := service.GetWorkflow(ctx, "user3", workflow.ID)
		require.NoError(t, err)
		assert.Equal(t, "Updated Name", updated.Name)
		assert.Equal(t, "Updated Description", updated.Description)
		assert.Len(t, updated.Steps, 2)
	})

	t.Run("should delete workflow", func(t *testing.T) {
		workflow := &Workflow{
			Name:   "Delete Me",
			UserID: "user4",
			Steps:  []WorkflowStep{{Name: "Step 1", Type: StepTypeCommand, Order: 1}},
		}

		err := service.CreateWorkflow(ctx, workflow)
		require.NoError(t, err)

		err = service.DeleteWorkflow(ctx, "user4", workflow.ID)
		require.NoError(t, err)

		_, err = service.GetWorkflow(ctx, "user4", workflow.ID)
		assert.Error(t, err)
		assert.Contains(t, err.Error(), "not found")
	})

	t.Run("should isolate workflows between users", func(t *testing.T) {
		workflow := &Workflow{
			Name:   "User1 Workflow",
			UserID: "user1",
			Steps:  []WorkflowStep{{Name: "Step 1", Type: StepTypeCommand, Order: 1}},
		}

		err := service.CreateWorkflow(ctx, workflow)
		require.NoError(t, err)

		_, err = service.GetWorkflow(ctx, "user2", workflow.ID)
		assert.Error(t, err)
		assert.Contains(t, err.Error(), "not found")
	})
}

func TestWorkflowExecution(t *testing.T) {
	db := setupTestDB(t)
	service := NewService()
	service.SetDB(db)
	ctx := context.Background()

	// Create a test workflow
	workflow := &Workflow{
		Name:   "Test Execution",
		UserID: "user1",
		Steps: []WorkflowStep{
			{Name: "Step 1", Type: StepTypeCommand, Config: map[string]interface{}{"command": "echo hello"}, Order: 1},
			{Name: "Step 2", Type: StepTypeCommand, Config: map[string]interface{}{"command": "echo world"}, Order: 2},
		},
	}
	err := service.CreateWorkflow(ctx, workflow)
	require.NoError(t, err)

	t.Run("should start workflow execution", func(t *testing.T) {
		execution, err := service.ExecuteWorkflow(ctx, "user1", workflow.ID, map[string]interface{}{
			"environment": "test",
		})
		require.NoError(t, err)
		assert.NotZero(t, execution.ID)
		assert.Equal(t, workflow.ID, execution.WorkflowID)
		assert.Equal(t, ExecutionStatusRunning, execution.Status)
		assert.WithinDuration(t, time.Now(), execution.StartedAt, time.Second)
	})

	t.Run("should get execution status", func(t *testing.T) {
		execution, err := service.ExecuteWorkflow(ctx, "user1", workflow.ID, nil)
		require.NoError(t, err)

		status, err := service.GetExecutionStatus(ctx, "user1", execution.ID)
		require.NoError(t, err)
		assert.Equal(t, execution.ID, status.ID)
		assert.Equal(t, ExecutionStatusRunning, status.Status)
	})

	t.Run("should list workflow executions", func(t *testing.T) {
		// Create multiple executions
		for i := 0; i < 3; i++ {
			_, err := service.ExecuteWorkflow(ctx, "user1", workflow.ID, nil)
			require.NoError(t, err)
		}

		executions, err := service.ListExecutions(ctx, "user1", workflow.ID)
		require.NoError(t, err)
		assert.True(t, len(executions) >= 3)
	})

	t.Run("should cancel workflow execution", func(t *testing.T) {
		execution, err := service.ExecuteWorkflow(ctx, "user1", workflow.ID, nil)
		require.NoError(t, err)

		err = service.CancelExecution(ctx, "user1", execution.ID)
		require.NoError(t, err)

		status, err := service.GetExecutionStatus(ctx, "user1", execution.ID)
		require.NoError(t, err)
		assert.Equal(t, ExecutionStatusCancelled, status.Status)
	})
}

func TestWorkflowValidation(t *testing.T) {
	db := setupTestDB(t)
	service := NewService()
	service.SetDB(db)
	ctx := context.Background()

	t.Run("should validate workflow fields", func(t *testing.T) {
		tests := []struct {
			name     string
			workflow *Workflow
			error    string
		}{
			{
				name:     "empty name",
				workflow: &Workflow{UserID: "user1", Steps: []WorkflowStep{{Name: "Step 1", Type: StepTypeCommand, Order: 1}}},
				error:    "name is required",
			},
			{
				name:     "empty user ID",
				workflow: &Workflow{Name: "Test", Steps: []WorkflowStep{{Name: "Step 1", Type: StepTypeCommand, Order: 1}}},
				error:    "user ID is required",
			},
			{
				name:     "no steps",
				workflow: &Workflow{Name: "Test", UserID: "user1", Steps: []WorkflowStep{}},
				error:    "at least one step is required",
			},
		}

		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				err := service.CreateWorkflow(ctx, tt.workflow)
				require.Error(t, err)
				assert.Contains(t, err.Error(), tt.error)
			})
		}
	})

	t.Run("should validate step configuration", func(t *testing.T) {
		workflow := &Workflow{
			Name:   "Test",
			UserID: "user1",
			Steps: []WorkflowStep{
				{Name: "", Type: StepTypeCommand, Order: 1}, // Empty name
			},
		}

		err := service.CreateWorkflow(ctx, workflow)
		require.Error(t, err)
		assert.Contains(t, err.Error(), "step name is required")
	})
}
