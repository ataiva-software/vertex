package database

import (
	"context"
	"fmt"
	"time"

	"github.com/ataiva-software/vertex/pkg/core"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// Config holds database connection configuration
type Config struct {
	Host     string `json:"host" yaml:"host"`
	Port     int    `json:"port" yaml:"port"`
	Database string `json:"database" yaml:"database"`
	Username string `json:"username" yaml:"username"`
	Password string `json:"password" yaml:"password"`
	SSLMode  string `json:"ssl_mode" yaml:"ssl_mode"`
}

// Validate validates the database configuration
func (c *Config) Validate() error {
	if err := core.ValidateRequired(c.Host, "host"); err != nil {
		return err
	}
	if err := core.ValidateRequired(c.Database, "database"); err != nil {
		return err
	}
	if err := core.ValidateRequired(c.Username, "username"); err != nil {
		return err
	}
	if err := core.ValidateRequired(c.Password, "password"); err != nil {
		return err
	}
	if err := core.ValidatePort(c.Port); err != nil {
		return err
	}
	return nil
}

// DSN returns the PostgreSQL data source name
func (c *Config) DSN() string {
	sslMode := c.SSLMode
	if sslMode == "" {
		sslMode = "require"
	}
	
	return fmt.Sprintf("host=%s user=%s password=%s dbname=%s port=%d sslmode=%s",
		c.Host, c.Username, c.Password, c.Database, c.Port, sslMode)
}

// ConnectionPool manages database connections
type ConnectionPool struct {
	Config          *Config
	DB              *gorm.DB
	MaxOpenConns    int
	MaxIdleConns    int
	ConnMaxLifetime time.Duration
}

// NewConnectionPool creates a new connection pool
func NewConnectionPool(config *Config) *ConnectionPool {
	return &ConnectionPool{
		Config:          config,
		MaxOpenConns:    10,
		MaxIdleConns:    5,
		ConnMaxLifetime: 5 * time.Minute,
	}
}

// Connect establishes a connection to the database
func (p *ConnectionPool) Connect() error {
	if err := p.Config.Validate(); err != nil {
		return fmt.Errorf("invalid config: %w", err)
	}

	db, err := gorm.Open(postgres.Open(p.Config.DSN()), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Info),
	})
	if err != nil {
		return fmt.Errorf("failed to connect to database: %w", err)
	}

	sqlDB, err := db.DB()
	if err != nil {
		return fmt.Errorf("failed to get underlying sql.DB: %w", err)
	}

	// Configure connection pool
	sqlDB.SetMaxOpenConns(p.MaxOpenConns)
	sqlDB.SetMaxIdleConns(p.MaxIdleConns)
	sqlDB.SetConnMaxLifetime(p.ConnMaxLifetime)

	p.DB = db
	return nil
}

// Close closes the database connection
func (p *ConnectionPool) Close() error {
	if p.DB == nil {
		return nil
	}

	sqlDB, err := p.DB.DB()
	if err != nil {
		return fmt.Errorf("failed to get underlying sql.DB: %w", err)
	}

	return sqlDB.Close()
}

// HealthCheck provides database health checking functionality
type HealthCheck struct {
	Pool    *ConnectionPool
	Timeout time.Duration
}

// NewHealthCheck creates a new health check
func NewHealthCheck(pool *ConnectionPool) *HealthCheck {
	return &HealthCheck{
		Pool:    pool,
		Timeout: 30 * time.Second,
	}
}

// Check performs a health check on the database
func (h *HealthCheck) Check(ctx context.Context) *core.HealthStatus {
	if h.Pool.DB == nil {
		return core.NewHealthStatus(false, "Database not connected")
	}

	// Create context with timeout
	checkCtx, cancel := context.WithTimeout(ctx, h.Timeout)
	defer cancel()

	sqlDB, err := h.Pool.DB.DB()
	if err != nil {
		return core.NewHealthStatus(false, fmt.Sprintf("Failed to get database instance: %v", err))
	}

	// Ping the database
	if err := sqlDB.PingContext(checkCtx); err != nil {
		return core.NewHealthStatus(false, fmt.Sprintf("Database ping failed: %v", err))
	}

	// Get database stats
	stats := sqlDB.Stats()
	details := map[string]interface{}{
		"open_connections":     stats.OpenConnections,
		"in_use":              stats.InUse,
		"idle":                stats.Idle,
		"wait_count":          stats.WaitCount,
		"wait_duration":       stats.WaitDuration.String(),
		"max_idle_closed":     stats.MaxIdleClosed,
		"max_lifetime_closed": stats.MaxLifetimeClosed,
	}

	return core.NewHealthStatusWithDetails(true, "Database is healthy", details)
}

// Repository provides base repository functionality
type Repository struct {
	DB *gorm.DB
}

// NewRepository creates a new repository
func NewRepository(db *gorm.DB) *Repository {
	return &Repository{DB: db}
}

// Transaction executes a function within a database transaction
func (r *Repository) Transaction(fn func(*gorm.DB) error) error {
	return r.DB.Transaction(fn)
}

// Migrate runs database migrations for the given models
func (r *Repository) Migrate(models ...interface{}) error {
	return r.DB.AutoMigrate(models...)
}
