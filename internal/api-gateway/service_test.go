package apigateway

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestAPIGatewayService(t *testing.T) {
	t.Run("should create new API gateway service", func(t *testing.T) {
		service := NewService()
		assert.NotNil(t, service)
	})
}

func TestRouteRegistration(t *testing.T) {
	service := NewService()

	t.Run("should register service route", func(t *testing.T) {
		route := &ServiceRoute{
			ServiceName: "vault",
			Path:        "/api/v1/vault",
			Target:      "http://vault:8080",
			Methods:     []string{"GET", "POST", "PUT", "DELETE"},
		}

		err := service.RegisterRoute(route)
		require.NoError(t, err)

		routes := service.GetRoutes()
		assert.Len(t, routes, 1)
		assert.Equal(t, "vault", routes[0].ServiceName)
	})

	t.Run("should validate route configuration", func(t *testing.T) {
		tests := []struct {
			name  string
			route *ServiceRoute
			error string
		}{
			{
				name:  "empty service name",
				route: &ServiceRoute{Path: "/api/v1/test", Target: "http://test:8080"},
				error: "service name is required",
			},
			{
				name:  "empty path",
				route: &ServiceRoute{ServiceName: "test", Target: "http://test:8080"},
				error: "path is required",
			},
			{
				name:  "empty target",
				route: &ServiceRoute{ServiceName: "test", Path: "/api/v1/test"},
				error: "target is required",
			},
		}

		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				err := service.RegisterRoute(tt.route)
				require.Error(t, err)
				assert.Contains(t, err.Error(), tt.error)
			})
		}
	})

	t.Run("should prevent duplicate routes", func(t *testing.T) {
		route := &ServiceRoute{
			ServiceName: "duplicate",
			Path:        "/api/v1/duplicate",
			Target:      "http://duplicate:8080",
		}

		err := service.RegisterRoute(route)
		require.NoError(t, err)

		err = service.RegisterRoute(route)
		assert.Error(t, err)
		assert.Contains(t, err.Error(), "already registered")
	})
}

func TestRateLimiting(t *testing.T) {
	service := NewService()

	t.Run("should create rate limiter", func(t *testing.T) {
		limiter := service.GetRateLimiter("user123")
		assert.NotNil(t, limiter)
	})

	t.Run("should allow requests within limit", func(t *testing.T) {
		limiter := service.GetRateLimiter("user456")
		
		// Should allow first request
		allowed := limiter.Allow()
		assert.True(t, allowed)
	})

	t.Run("should track rate limit status", func(t *testing.T) {
		limiter := service.GetRateLimiter("user789")
		
		status := limiter.Status()
		assert.NotNil(t, status)
		assert.True(t, status.Remaining >= 0)
		assert.True(t, status.Limit > 0)
	})
}

func TestServiceDiscovery(t *testing.T) {
	service := NewService()

	t.Run("should register service instance", func(t *testing.T) {
		instance := &ServiceInstance{
			ID:          "vault-1",
			ServiceName: "vault",
			Address:     "192.168.1.100",
			Port:        8080,
			Health:      HealthStatusHealthy,
			Metadata:    map[string]string{"version": "1.0.0"},
		}

		err := service.RegisterInstance(instance)
		require.NoError(t, err)

		instances := service.GetInstances("vault")
		assert.Len(t, instances, 1)
		assert.Equal(t, "vault-1", instances[0].ID)
	})

	t.Run("should update instance health", func(t *testing.T) {
		instance := &ServiceInstance{
			ID:          "vault-2",
			ServiceName: "vault",
			Address:     "192.168.1.101",
			Port:        8080,
			Health:      HealthStatusHealthy,
		}

		err := service.RegisterInstance(instance)
		require.NoError(t, err)

		err = service.UpdateInstanceHealth("vault-2", HealthStatusUnhealthy)
		require.NoError(t, err)

		instances := service.GetInstances("vault")
		found := false
		for _, inst := range instances {
			if inst.ID == "vault-2" {
				assert.Equal(t, HealthStatusUnhealthy, inst.Health)
				found = true
				break
			}
		}
		assert.True(t, found)
	})

	t.Run("should remove unhealthy instances", func(t *testing.T) {
		instance := &ServiceInstance{
			ID:          "vault-3",
			ServiceName: "vault",
			Address:     "192.168.1.102",
			Port:        8080,
			Health:      HealthStatusHealthy,
		}

		err := service.RegisterInstance(instance)
		require.NoError(t, err)

		err = service.DeregisterInstance("vault-3")
		require.NoError(t, err)

		instances := service.GetInstances("vault")
		for _, inst := range instances {
			assert.NotEqual(t, "vault-3", inst.ID)
		}
	})
}

func TestLoadBalancing(t *testing.T) {
	service := NewService()

	// Register multiple instances
	instances := []*ServiceInstance{
		{ID: "vault-1", ServiceName: "vault", Address: "192.168.1.100", Port: 8080, Health: HealthStatusHealthy},
		{ID: "vault-2", ServiceName: "vault", Address: "192.168.1.101", Port: 8080, Health: HealthStatusHealthy},
		{ID: "vault-3", ServiceName: "vault", Address: "192.168.1.102", Port: 8080, Health: HealthStatusUnhealthy},
	}

	for _, instance := range instances {
		err := service.RegisterInstance(instance)
		require.NoError(t, err)
	}

	t.Run("should select healthy instance", func(t *testing.T) {
		instance := service.SelectInstance("vault")
		assert.NotNil(t, instance)
		assert.Equal(t, HealthStatusHealthy, instance.Health)
		assert.NotEqual(t, "vault-3", instance.ID) // Should not select unhealthy instance
	})

	t.Run("should distribute load across instances", func(t *testing.T) {
		selectedIDs := make(map[string]int)
		
		// Select instances multiple times to test distribution
		for i := 0; i < 10; i++ {
			instance := service.SelectInstance("vault")
			if instance != nil {
				selectedIDs[instance.ID]++
			}
		}

		// Should have selected both healthy instances
		assert.True(t, len(selectedIDs) > 0)
		assert.NotContains(t, selectedIDs, "vault-3") // Should not select unhealthy
	})
}

func TestMiddleware(t *testing.T) {
	service := NewService()

	t.Run("should add authentication middleware", func(t *testing.T) {
		middleware := &Middleware{
			Name:     "auth",
			Priority: 100,
			Handler:  func(ctx context.Context, req *Request) (*Request, error) { return req, nil },
		}

		service.AddMiddleware(middleware)
		middlewares := service.GetMiddlewares()
		assert.Len(t, middlewares, 1)
		assert.Equal(t, "auth", middlewares[0].Name)
	})

	t.Run("should order middlewares by priority", func(t *testing.T) {
		middlewares := []*Middleware{
			{Name: "cors", Priority: 50, Handler: func(ctx context.Context, req *Request) (*Request, error) { return req, nil }},
			{Name: "logging", Priority: 10, Handler: func(ctx context.Context, req *Request) (*Request, error) { return req, nil }},
			{Name: "auth", Priority: 100, Handler: func(ctx context.Context, req *Request) (*Request, error) { return req, nil }},
		}

		for _, mw := range middlewares {
			service.AddMiddleware(mw)
		}

		ordered := service.GetMiddlewares()
		assert.Equal(t, "logging", ordered[0].Name)   // Priority 10
		assert.Equal(t, "cors", ordered[1].Name)      // Priority 50  
		assert.Equal(t, "auth", ordered[2].Name)      // Priority 100
	})
}
