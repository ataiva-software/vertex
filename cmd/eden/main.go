package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/ataiva-software/eden/internal/api-gateway"
	"github.com/ataiva-software/eden/internal/flow"
	"github.com/ataiva-software/eden/internal/hub"
	"github.com/ataiva-software/eden/internal/insight"
	"github.com/ataiva-software/eden/internal/monitor"
	syncservice "github.com/ataiva-software/eden/internal/sync"
	"github.com/ataiva-software/eden/internal/task"
	"github.com/ataiva-software/eden/internal/vault"
	"github.com/ataiva-software/eden/pkg/core"
	"github.com/ataiva-software/eden/pkg/database"
	"github.com/gin-gonic/gin"
	"github.com/spf13/cobra"
)

var (
	// Global flags
	dbHost     string
	dbPort     int
	dbName     string
	dbUser     string
	dbPassword string
	dbSSLMode  string
	basePort   int
	services   []string
)

func main() {
	var rootCmd = &cobra.Command{
		Use:   "eden",
		Short: "Eden DevOps Suite - All-in-One Binary",
		Long:  "A comprehensive DevOps platform with secrets management, workflow automation, and more.",
	}

	// Global flags
	rootCmd.PersistentFlags().StringVar(&dbHost, "db-host", getEnv("DB_HOST", "localhost"), "Database host")
	rootCmd.PersistentFlags().IntVar(&dbPort, "db-port", 5432, "Database port")
	rootCmd.PersistentFlags().StringVar(&dbName, "db-name", getEnv("DB_NAME", "eden"), "Database name")
	rootCmd.PersistentFlags().StringVar(&dbUser, "db-user", getEnv("DB_USER", "eden"), "Database user")
	rootCmd.PersistentFlags().StringVar(&dbPassword, "db-password", getEnv("DB_PASSWORD", "secret"), "Database password")
	rootCmd.PersistentFlags().StringVar(&dbSSLMode, "db-ssl-mode", getEnv("DB_SSL_MODE", "disable"), "Database SSL mode")
	rootCmd.PersistentFlags().IntVar(&basePort, "base-port", 8000, "Base port for services")

	// Add subcommands
	rootCmd.AddCommand(serverCmd())
	rootCmd.AddCommand(serviceCmd())
	rootCmd.AddCommand(statusCmd())
	rootCmd.AddCommand(vaultCmd())
	rootCmd.AddCommand(flowCmd())
	rootCmd.AddCommand(taskCmd())
	rootCmd.AddCommand(monitorCmd())
	rootCmd.AddCommand(syncCmd())
	rootCmd.AddCommand(insightCmd())
	rootCmd.AddCommand(hubCmd())

	if err := rootCmd.Execute(); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}
}

func serverCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "server",
		Short: "Run all Eden services",
		Long:  "Start all Eden services in a single process",
		Run:   runAllServices,
	}

	cmd.Flags().StringSliceVar(&services, "services", []string{"api-gateway", "vault", "flow", "task", "monitor", "sync", "insight", "hub"}, "Services to run")
	cmd.Flags().BoolP("all", "a", false, "Run all services (default)")

	return cmd
}

func serviceCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "service [service-name]",
		Short: "Run a specific service",
		Long:  "Run a single Eden service",
		Args:  cobra.ExactArgs(1),
		Run:   runSingleService,
	}

	cmd.Flags().IntP("port", "p", 0, "Port to run the service on (auto-assigned if not specified)")

	return cmd
}

func runAllServices(cmd *cobra.Command, args []string) {
	log.Println("🚀 Starting Eden DevOps Suite - All Services")
	
	// Database configuration
	dbConfig := &database.Config{
		Host:     dbHost,
		Port:     dbPort,
		Database: dbName,
		Username: dbUser,
		Password: dbPassword,
		SSLMode:  dbSSLMode,
	}

	// Create database connection pool
	pool := database.NewConnectionPool(dbConfig)
	if err := pool.Connect(); err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}
	defer pool.Close()

	// Auto-migrate all schemas
	if err := migrateAllSchemas(pool); err != nil {
		log.Fatalf("Failed to migrate database schemas: %v", err)
	}

	// Create service instances
	serviceInstances := createServiceInstances(pool)

	// Start all services concurrently
	var wg sync.WaitGroup
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Start each service in its own goroutine
	for i, serviceName := range services {
		wg.Add(1)
		go func(name string, port int) {
			defer wg.Done()
			startService(ctx, name, port, serviceInstances[name])
		}(serviceName, basePort+i)
	}

	// Wait for interrupt signal
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("🛑 Shutting down Eden DevOps Suite...")
	cancel()
	
	// Wait for all services to stop
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		log.Println("✅ All services stopped gracefully")
	case <-time.After(30 * time.Second):
		log.Println("⚠️  Timeout waiting for services to stop")
	}
}

func runSingleService(cmd *cobra.Command, args []string) {
	serviceName := args[0]
	port, _ := cmd.Flags().GetInt("port")
	
	if port == 0 {
		port = getDefaultPort(serviceName)
	}

	log.Printf("🚀 Starting Eden %s service on port %d", strings.Title(serviceName), port)

	// Database configuration
	dbConfig := &database.Config{
		Host:     dbHost,
		Port:     dbPort,
		Database: dbName,
		Username: dbUser,
		Password: dbPassword,
		SSLMode:  dbSSLMode,
	}

	// Create database connection pool
	pool := database.NewConnectionPool(dbConfig)
	if err := pool.Connect(); err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}
	defer pool.Close()

	// Migrate schema for this service
	if err := migrateServiceSchema(pool, serviceName); err != nil {
		log.Fatalf("Failed to migrate %s schema: %v", serviceName, err)
	}

	// Create service instances
	serviceInstances := createServiceInstances(pool)

	// Start the specific service
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	go startService(ctx, serviceName, port, serviceInstances[serviceName])

	// Wait for interrupt signal
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Printf("🛑 Shutting down %s service...", serviceName)
	cancel()
	
	time.Sleep(2 * time.Second)
	log.Printf("✅ %s service stopped", serviceName)
}

func createServiceInstances(pool *database.ConnectionPool) map[string]interface{} {
	instances := make(map[string]interface{})

	// Create API Gateway
	gatewayService := apigateway.NewService()
	instances["api-gateway"] = gatewayService

	// Create Vault service
	vaultService := vault.NewService()
	vaultService.SetDB(pool.DB)
	instances["vault"] = vaultService

	// Create Flow service
	flowService := flow.NewService()
	flowService.SetDB(pool.DB)
	instances["flow"] = flowService

	// Create Task service
	taskService := task.NewService()
	taskService.SetDB(pool.DB)
	instances["task"] = taskService

	// Create Monitor service
	monitorService := monitor.NewService()
	monitorService.SetDB(pool.DB)
	instances["monitor"] = monitorService

	// Create Sync service
	syncService := syncservice.NewService()
	syncService.SetDB(pool.DB)
	instances["sync"] = syncService

	// Create Insight service
	insightService := insight.NewService()
	insightService.SetDB(pool.DB)
	instances["insight"] = insightService

	// Create Hub service
	hubService := hub.NewService()
	hubService.SetDB(pool.DB)
	instances["hub"] = hubService

	return instances
}

func startService(ctx context.Context, serviceName string, port int, serviceInstance interface{}) {
	serviceInfo := core.NewServiceInfo(serviceName, "1.0.0", port)
	router := gin.Default()

	// Add common middleware
	router.Use(corsMiddleware())
	router.Use(loggingMiddleware())

	// Health check endpoint
	router.GET("/health", func(c *gin.Context) {
		serviceInfo.SetStatus(core.ServiceStatusHealthy)
		c.JSON(http.StatusOK, gin.H{
			"status":  "healthy",
			"service": serviceInfo,
		})
	})

	// Service info endpoint
	router.GET("/info", func(c *gin.Context) {
		c.JSON(http.StatusOK, serviceInfo)
	})

	// Add service-specific routes
	addServiceRoutes(router, serviceName, serviceInstance)

	// Create HTTP server
	server := &http.Server{
		Addr:    fmt.Sprintf(":%d", port),
		Handler: router,
	}

	// Start server in a goroutine
	go func() {
		log.Printf("✅ %s service started on port %d", strings.Title(serviceName), port)
		serviceInfo.SetStatus(core.ServiceStatusHealthy)
		
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Printf("❌ %s service failed: %v", serviceName, err)
		}
	}()

	// Wait for context cancellation
	<-ctx.Done()

	// Graceful shutdown
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	serviceInfo.SetStatus(core.ServiceStatusStopping)
	if err := server.Shutdown(shutdownCtx); err != nil {
		log.Printf("⚠️  %s service forced shutdown: %v", serviceName, err)
	}
	serviceInfo.SetStatus(core.ServiceStatusStopped)
}

func addServiceRoutes(router *gin.Engine, serviceName string, serviceInstance interface{}) {
	v1 := router.Group("/api/v1")

	switch serviceName {
	case "api-gateway":
		// API Gateway routes are handled differently
		addAPIGatewayRoutes(v1, serviceInstance.(*apigateway.Service))
	case "vault":
		addVaultRoutes(v1, serviceInstance.(*vault.Service))
	case "flow":
		addFlowRoutes(v1, serviceInstance.(*flow.Service))
	case "task":
		addTaskRoutes(v1, serviceInstance.(*task.Service))
	case "monitor":
		addMonitorRoutes(v1, serviceInstance.(*monitor.Service))
	case "sync":
		addSyncRoutes(v1, serviceInstance.(*syncservice.Service))
	case "insight":
		addInsightRoutes(v1, serviceInstance.(*insight.Service))
	case "hub":
		addHubRoutes(v1, serviceInstance.(*hub.Service))
	}
}

// Service route handlers (simplified versions of the individual service mains)
func addAPIGatewayRoutes(v1 *gin.RouterGroup, service *apigateway.Service) {
	v1.GET("/routes", func(c *gin.Context) {
		routes := service.GetRoutes()
		c.JSON(http.StatusOK, gin.H{"routes": routes})
	})
}

func addVaultRoutes(v1 *gin.RouterGroup, service *vault.Service) {
	v1.GET("/secrets", func(c *gin.Context) {
		userID := getUserID(c)
		if userID == "" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "User ID required"})
			return
		}
		secrets, err := service.ListSecrets(c.Request.Context(), userID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		c.JSON(http.StatusOK, gin.H{"secrets": secrets})
	})

	v1.GET("/secrets/:key", func(c *gin.Context) {
		userID := getUserID(c)
		if userID == "" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "User ID required"})
			return
		}
		key := c.Param("key")
		secret, err := service.GetSecret(c.Request.Context(), userID, key)
		if err != nil {
			if strings.Contains(err.Error(), "not found") {
				c.JSON(http.StatusNotFound, gin.H{"error": err.Error()})
			} else {
				c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			}
			return
		}
		c.JSON(http.StatusOK, secret)
	})
}

func addFlowRoutes(v1 *gin.RouterGroup, service *flow.Service) {
	v1.GET("/workflows", func(c *gin.Context) {
		userID := getUserID(c)
		if userID == "" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "User ID required"})
			return
		}
		workflows, err := service.ListWorkflows(c.Request.Context(), userID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		c.JSON(http.StatusOK, gin.H{"workflows": workflows})
	})
}

func addTaskRoutes(v1 *gin.RouterGroup, service *task.Service) {
	v1.GET("/tasks", func(c *gin.Context) {
		userID := getUserID(c)
		if userID == "" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "User ID required"})
			return
		}
		tasks, err := service.ListTasks(c.Request.Context(), userID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		c.JSON(http.StatusOK, gin.H{"tasks": tasks})
	})
}

func addMonitorRoutes(v1 *gin.RouterGroup, service *monitor.Service) {
	v1.GET("/metrics/:service", func(c *gin.Context) {
		serviceName := c.Param("service")
		metrics, err := service.GetMetrics(c.Request.Context(), serviceName)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		c.JSON(http.StatusOK, gin.H{"metrics": metrics})
	})
}

func addSyncRoutes(v1 *gin.RouterGroup, service *syncservice.Service) {
	v1.GET("/sync-jobs", func(c *gin.Context) {
		userID := getUserID(c)
		if userID == "" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "User ID required"})
			return
		}
		jobs, err := service.GetSyncJobs(c.Request.Context(), userID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		c.JSON(http.StatusOK, gin.H{"sync_jobs": jobs})
	})
}

func addInsightRoutes(v1 *gin.RouterGroup, service *insight.Service) {
	v1.GET("/reports", func(c *gin.Context) {
		userID := getUserID(c)
		if userID == "" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "User ID required"})
			return
		}
		reports, err := service.GetReports(c.Request.Context(), userID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		c.JSON(http.StatusOK, gin.H{"reports": reports})
	})
}

func addHubRoutes(v1 *gin.RouterGroup, service *hub.Service) {
	v1.GET("/integrations", func(c *gin.Context) {
		userID := getUserID(c)
		if userID == "" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "User ID required"})
			return
		}
		integrations, err := service.GetIntegrations(c.Request.Context(), userID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		c.JSON(http.StatusOK, gin.H{"integrations": integrations})
	})
}

func migrateAllSchemas(pool *database.ConnectionPool) error {
	// Migrate all service schemas
	if err := pool.DB.AutoMigrate(&vault.Secret{}); err != nil {
		return fmt.Errorf("vault migration failed: %w", err)
	}
	if err := pool.DB.AutoMigrate(&flow.Workflow{}, &flow.WorkflowExecution{}, &flow.WorkflowStep{}, &flow.StepExecution{}, &flow.WorkflowTemplate{}); err != nil {
		return fmt.Errorf("flow migration failed: %w", err)
	}
	if err := pool.DB.AutoMigrate(&task.Task{}); err != nil {
		return fmt.Errorf("task migration failed: %w", err)
	}
	if err := pool.DB.AutoMigrate(&monitor.Metric{}, &monitor.Alert{}); err != nil {
		return fmt.Errorf("monitor migration failed: %w", err)
	}
	if err := pool.DB.AutoMigrate(&syncservice.SyncJob{}); err != nil {
		return fmt.Errorf("sync migration failed: %w", err)
	}
	if err := pool.DB.AutoMigrate(&insight.Report{}); err != nil {
		return fmt.Errorf("insight migration failed: %w", err)
	}
	if err := pool.DB.AutoMigrate(&hub.Integration{}); err != nil {
		return fmt.Errorf("hub migration failed: %w", err)
	}
	return nil
}

func migrateServiceSchema(pool *database.ConnectionPool, serviceName string) error {
	switch serviceName {
	case "vault":
		return pool.DB.AutoMigrate(&vault.Secret{})
	case "flow":
		return pool.DB.AutoMigrate(&flow.Workflow{}, &flow.WorkflowExecution{}, &flow.WorkflowStep{}, &flow.StepExecution{}, &flow.WorkflowTemplate{})
	case "task":
		return pool.DB.AutoMigrate(&task.Task{})
	case "monitor":
		return pool.DB.AutoMigrate(&monitor.Metric{}, &monitor.Alert{})
	case "sync":
		return pool.DB.AutoMigrate(&syncservice.SyncJob{})
	case "insight":
		return pool.DB.AutoMigrate(&insight.Report{})
	case "hub":
		return pool.DB.AutoMigrate(&hub.Integration{})
	}
	return nil
}

func getDefaultPort(serviceName string) int {
	ports := map[string]int{
		"api-gateway": 8000,
		"vault":       8080,
		"flow":        8081,
		"task":        8082,
		"monitor":     8083,
		"sync":        8084,
		"insight":     8085,
		"hub":         8086,
	}
	if port, exists := ports[serviceName]; exists {
		return port
	}
	return 8000
}

// CLI command implementations (from the original CLI)
func statusCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "status",
		Short: "Show system status",
		Run: func(cmd *cobra.Command, args []string) {
			fmt.Println("Eden DevOps Suite Status")
			fmt.Println("========================")
			
			services := []struct {
				name string
				port int
			}{
				{"API Gateway", 8000},
				{"Vault", 8080},
				{"Flow", 8081},
				{"Task", 8082},
				{"Monitor", 8083},
				{"Sync", 8084},
				{"Insight", 8085},
				{"Hub", 8086},
			}

			for _, service := range services {
				url := fmt.Sprintf("http://localhost:%d/health", service.port)
				status := checkServiceHealth(url)
				fmt.Printf("%-12s: %s\n", service.name, status)
			}
		},
	}
}

// Include the CLI commands from the original implementation
func vaultCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "vault",
		Short: "Manage secrets",
	}

	cmd.AddCommand(&cobra.Command{
		Use:   "list",
		Short: "List secrets",
		Run: func(cmd *cobra.Command, args []string) {
			url := fmt.Sprintf("http://localhost:8080/api/v1/secrets")
			resp, err := makeRequest("GET", url, nil)
			if err != nil {
				fmt.Printf("Error: %v\n", err)
				return
			}
			fmt.Println(resp)
		},
	})

	cmd.AddCommand(&cobra.Command{
		Use:   "get [key]",
		Short: "Get a secret",
		Args:  cobra.ExactArgs(1),
		Run: func(cmd *cobra.Command, args []string) {
			key := args[0]
			url := fmt.Sprintf("http://localhost:8080/api/v1/secrets/%s", key)
			resp, err := makeRequest("GET", url, nil)
			if err != nil {
				fmt.Printf("Error: %v\n", err)
				return
			}
			fmt.Println(resp)
		},
	})

	return cmd
}

func flowCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "flow",
		Short: "Manage workflows",
	}

	cmd.AddCommand(&cobra.Command{
		Use:   "list",
		Short: "List workflows",
		Run: func(cmd *cobra.Command, args []string) {
			url := fmt.Sprintf("http://localhost:8081/api/v1/workflows")
			resp, err := makeRequest("GET", url, nil)
			if err != nil {
				fmt.Printf("Error: %v\n", err)
				return
			}
			fmt.Println(resp)
		},
	})

	return cmd
}

func taskCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "task",
		Short: "Manage tasks",
	}

	cmd.AddCommand(&cobra.Command{
		Use:   "list",
		Short: "List tasks",
		Run: func(cmd *cobra.Command, args []string) {
			url := fmt.Sprintf("http://localhost:8082/api/v1/tasks")
			resp, err := makeRequest("GET", url, nil)
			if err != nil {
				fmt.Printf("Error: %v\n", err)
				return
			}
			fmt.Println(resp)
		},
	})

	return cmd
}

func monitorCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "monitor",
		Short: "Monitor services",
	}

	cmd.AddCommand(&cobra.Command{
		Use:   "metrics [service]",
		Short: "Get service metrics",
		Args:  cobra.ExactArgs(1),
		Run: func(cmd *cobra.Command, args []string) {
			service := args[0]
			url := fmt.Sprintf("http://localhost:8083/api/v1/metrics/%s", service)
			resp, err := makeRequest("GET", url, nil)
			if err != nil {
				fmt.Printf("Error: %v\n", err)
				return
			}
			fmt.Println(resp)
		},
	})

	return cmd
}

func syncCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "sync",
		Short: "Manage sync jobs",
	}

	cmd.AddCommand(&cobra.Command{
		Use:   "list",
		Short: "List sync jobs",
		Run: func(cmd *cobra.Command, args []string) {
			url := fmt.Sprintf("http://localhost:8084/api/v1/sync-jobs")
			resp, err := makeRequest("GET", url, nil)
			if err != nil {
				fmt.Printf("Error: %v\n", err)
				return
			}
			fmt.Println(resp)
		},
	})

	return cmd
}

func insightCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "insight",
		Short: "Manage reports",
	}

	cmd.AddCommand(&cobra.Command{
		Use:   "list",
		Short: "List reports",
		Run: func(cmd *cobra.Command, args []string) {
			url := fmt.Sprintf("http://localhost:8085/api/v1/reports")
			resp, err := makeRequest("GET", url, nil)
			if err != nil {
				fmt.Printf("Error: %v\n", err)
				return
			}
			fmt.Println(resp)
		},
	})

	return cmd
}

func hubCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "hub",
		Short: "Manage integrations",
	}

	cmd.AddCommand(&cobra.Command{
		Use:   "list",
		Short: "List integrations",
		Run: func(cmd *cobra.Command, args []string) {
			url := fmt.Sprintf("http://localhost:8086/api/v1/integrations")
			resp, err := makeRequest("GET", url, nil)
			if err != nil {
				fmt.Printf("Error: %v\n", err)
				return
			}
			fmt.Println(resp)
		},
	})

	return cmd
}

// Helper functions
func corsMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Origin, Content-Type, Authorization, X-User-ID")

		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}

		c.Next()
	}
}

func loggingMiddleware() gin.HandlerFunc {
	return gin.LoggerWithFormatter(func(param gin.LogFormatterParams) string {
		return fmt.Sprintf("%s - [%s] \"%s %s %s %d %s \"%s\" %s\"\n",
			param.ClientIP,
			param.TimeStamp.Format(time.RFC1123),
			param.Method,
			param.Path,
			param.Request.Proto,
			param.StatusCode,
			param.Latency,
			param.Request.UserAgent(),
			param.ErrorMessage,
		)
	})
}

func getUserID(c *gin.Context) string {
	return c.GetHeader("X-User-ID")
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// Include helper functions from original CLI
func checkServiceHealth(url string) string {
	client := &http.Client{Timeout: 5 * time.Second}
	resp, err := client.Get(url)
	if err != nil {
		return "❌ Unhealthy"
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusOK {
		return "✅ Healthy"
	}
	return "⚠️  Degraded"
}

func makeRequest(method, url string, body interface{}) (string, error) {
	client := &http.Client{Timeout: 30 * time.Second}
	req, err := http.NewRequest(method, url, nil)
	if err != nil {
		return "", err
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-User-ID", "cli-user")

	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		return "", fmt.Errorf("HTTP %d", resp.StatusCode)
	}

	return fmt.Sprintf("✅ Success (HTTP %d)", resp.StatusCode), nil
}
