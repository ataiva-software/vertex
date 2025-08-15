package database

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestDatabaseConfig(t *testing.T) {
	t.Run("should create valid database config", func(t *testing.T) {
		config := &Config{
			Host:     "localhost",
			Port:     5432,
			Database: "eden_test",
			Username: "eden",
			Password: "secret",
			SSLMode:  "disable",
		}

		err := config.Validate()
		assert.NoError(t, err)
	})

	t.Run("should fail validation with missing host", func(t *testing.T) {
		config := &Config{
			Port:     5432,
			Database: "eden_test",
			Username: "eden",
			Password: "secret",
		}

		err := config.Validate()
		require.Error(t, err)
		assert.Contains(t, err.Error(), "host is required")
	})

	t.Run("should fail validation with invalid port", func(t *testing.T) {
		config := &Config{
			Host:     "localhost",
			Port:     0,
			Database: "eden_test",
			Username: "eden",
			Password: "secret",
		}

		err := config.Validate()
		require.Error(t, err)
		assert.Contains(t, err.Error(), "port must be between 1 and 65535")
	})

	t.Run("should generate correct DSN", func(t *testing.T) {
		config := &Config{
			Host:     "localhost",
			Port:     5432,
			Database: "eden_test",
			Username: "eden",
			Password: "secret",
			SSLMode:  "disable",
		}

		dsn := config.DSN()
		expected := "host=localhost user=eden password=secret dbname=eden_test port=5432 sslmode=disable"
		assert.Equal(t, expected, dsn)
	})

	t.Run("should generate DSN with default SSL mode", func(t *testing.T) {
		config := &Config{
			Host:     "localhost",
			Port:     5432,
			Database: "eden_test",
			Username: "eden",
			Password: "secret",
		}

		dsn := config.DSN()
		assert.Contains(t, dsn, "sslmode=require")
	})
}

func TestConnectionPool(t *testing.T) {
	t.Run("should create connection pool with default settings", func(t *testing.T) {
		config := &Config{
			Host:     "localhost",
			Port:     5432,
			Database: "eden_test",
			Username: "eden",
			Password: "secret",
			SSLMode:  "disable",
		}

		pool := NewConnectionPool(config)
		assert.NotNil(t, pool)
		assert.Equal(t, config, pool.Config)
		assert.Equal(t, 10, pool.MaxOpenConns)
		assert.Equal(t, 5, pool.MaxIdleConns)
		assert.Equal(t, 5*time.Minute, pool.ConnMaxLifetime)
	})

	t.Run("should create connection pool with custom settings", func(t *testing.T) {
		config := &Config{
			Host:     "localhost",
			Port:     5432,
			Database: "eden_test",
			Username: "eden",
			Password: "secret",
			SSLMode:  "disable",
		}

		pool := NewConnectionPool(config)
		pool.MaxOpenConns = 20
		pool.MaxIdleConns = 10
		pool.ConnMaxLifetime = 10 * time.Minute

		assert.Equal(t, 20, pool.MaxOpenConns)
		assert.Equal(t, 10, pool.MaxIdleConns)
		assert.Equal(t, 10*time.Minute, pool.ConnMaxLifetime)
	})
}

func TestHealthCheck(t *testing.T) {
	t.Run("should create health check", func(t *testing.T) {
		config := &Config{
			Host:     "localhost",
			Port:     5432,
			Database: "eden_test",
			Username: "eden",
			Password: "secret",
			SSLMode:  "disable",
		}

		pool := NewConnectionPool(config)
		healthCheck := NewHealthCheck(pool)
		
		assert.NotNil(t, healthCheck)
		assert.Equal(t, pool, healthCheck.Pool)
		assert.Equal(t, 30*time.Second, healthCheck.Timeout)
	})
}

// Note: These tests don't actually connect to a database
// Integration tests with real database connections would be in a separate file
// with build tag "integration"
