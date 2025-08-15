package apigateway

import (
	"errors"
	"fmt"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
)

// Service provides API gateway functionality
type Service struct {
	routes      map[string]*ServiceRoute
	instances   map[string][]*ServiceInstance
	rateLimiters map[string]*RateLimiter
	middlewares []*Middleware
	config      *ProxyConfig
	mu          sync.RWMutex
}

// NewService creates a new API gateway service
func NewService() *Service {
	return &Service{
		routes:       make(map[string]*ServiceRoute),
		instances:    make(map[string][]*ServiceInstance),
		rateLimiters: make(map[string]*RateLimiter),
		middlewares:  make([]*Middleware, 0),
		config: &ProxyConfig{
			Timeout:         30 * time.Second,
			RetryAttempts:   3,
			RetryDelay:      1 * time.Second,
			CircuitBreaker:  true,
			LoadBalancer:    "round_robin",
		},
	}
}

// RegisterRoute registers a new service route
func (s *Service) RegisterRoute(route *ServiceRoute) error {
	if err := s.validateRoute(route); err != nil {
		return err
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	// Check for duplicate routes
	for _, existingRoute := range s.routes {
		if existingRoute.Path == route.Path {
			return fmt.Errorf("route with path '%s' already registered", route.Path)
		}
	}

	// Generate ID if not provided
	if route.ID == "" {
		route.ID = uuid.New().String()
	}

	// Set timestamps
	now := time.Now()
	route.CreatedAt = now
	route.UpdatedAt = now

	s.routes[route.ID] = route
	return nil
}

// GetRoutes returns all registered routes
func (s *Service) GetRoutes() []*ServiceRoute {
	s.mu.RLock()
	defer s.mu.RUnlock()

	routes := make([]*ServiceRoute, 0, len(s.routes))
	for _, route := range s.routes {
		routes = append(routes, route)
	}
	return routes
}

// RegisterInstance registers a service instance
func (s *Service) RegisterInstance(instance *ServiceInstance) error {
	if err := s.validateInstance(instance); err != nil {
		return err
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	// Generate ID if not provided
	if instance.ID == "" {
		instance.ID = uuid.New().String()
	}

	// Set timestamps
	now := time.Now()
	instance.RegisteredAt = now
	instance.LastSeen = now

	// Add to instances map
	if s.instances[instance.ServiceName] == nil {
		s.instances[instance.ServiceName] = make([]*ServiceInstance, 0)
	}

	// Check if instance already exists
	for i, existing := range s.instances[instance.ServiceName] {
		if existing.ID == instance.ID {
			s.instances[instance.ServiceName][i] = instance
			return nil
		}
	}

	s.instances[instance.ServiceName] = append(s.instances[instance.ServiceName], instance)
	return nil
}

// GetInstances returns all instances for a service
func (s *Service) GetInstances(serviceName string) []*ServiceInstance {
	s.mu.RLock()
	defer s.mu.RUnlock()

	instances := s.instances[serviceName]
	if instances == nil {
		return []*ServiceInstance{}
	}

	// Return copy to avoid race conditions
	result := make([]*ServiceInstance, len(instances))
	copy(result, instances)
	return result
}

// UpdateInstanceHealth updates the health status of an instance
func (s *Service) UpdateInstanceHealth(instanceID string, health HealthStatus) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	for serviceName, instances := range s.instances {
		for i, instance := range instances {
			if instance.ID == instanceID {
				s.instances[serviceName][i].Health = health
				s.instances[serviceName][i].LastSeen = time.Now()
				return nil
			}
		}
	}

	return fmt.Errorf("instance '%s' not found", instanceID)
}

// DeregisterInstance removes an instance from the registry
func (s *Service) DeregisterInstance(instanceID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	for serviceName, instances := range s.instances {
		for i, instance := range instances {
			if instance.ID == instanceID {
				// Remove instance from slice
				s.instances[serviceName] = append(instances[:i], instances[i+1:]...)
				return nil
			}
		}
	}

	return fmt.Errorf("instance '%s' not found", instanceID)
}

// SelectInstance selects a healthy instance using load balancing
func (s *Service) SelectInstance(serviceName string) *ServiceInstance {
	s.mu.RLock()
	defer s.mu.RUnlock()

	instances := s.instances[serviceName]
	if len(instances) == 0 {
		return nil
	}

	// Filter healthy instances
	healthyInstances := make([]*ServiceInstance, 0)
	for _, instance := range instances {
		if instance.Health == HealthStatusHealthy {
			healthyInstances = append(healthyInstances, instance)
		}
	}

	if len(healthyInstances) == 0 {
		return nil
	}

	// Simple round-robin selection (in a real implementation, this would be more sophisticated)
	// For now, just return the first healthy instance
	return healthyInstances[0]
}

// GetRateLimiter gets or creates a rate limiter for a user/IP
func (s *Service) GetRateLimiter(identifier string) *RateLimiter {
	s.mu.Lock()
	defer s.mu.Unlock()

	limiter, exists := s.rateLimiters[identifier]
	if !exists {
		limiter = &RateLimiter{
			ID:       identifier,
			Limit:    100,                // 100 requests
			Window:   time.Minute,        // per minute
			Requests: 0,
			ResetAt:  time.Now().Add(time.Minute),
		}
		s.rateLimiters[identifier] = limiter
	}

	return limiter
}

// AddMiddleware adds a middleware to the gateway
func (s *Service) AddMiddleware(middleware *Middleware) {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.middlewares = append(s.middlewares, middleware)

	// Sort middlewares by priority (lower priority first)
	sort.Slice(s.middlewares, func(i, j int) bool {
		return s.middlewares[i].Priority < s.middlewares[j].Priority
	})
}

// GetMiddlewares returns all registered middlewares
func (s *Service) GetMiddlewares() []*Middleware {
	s.mu.RLock()
	defer s.mu.RUnlock()

	// Return copy to avoid race conditions
	result := make([]*Middleware, len(s.middlewares))
	copy(result, s.middlewares)
	return result
}

// validateRoute validates a service route
func (s *Service) validateRoute(route *ServiceRoute) error {
	if strings.TrimSpace(route.ServiceName) == "" {
		return errors.New("service name is required")
	}
	if strings.TrimSpace(route.Path) == "" {
		return errors.New("path is required")
	}
	if strings.TrimSpace(route.Target) == "" {
		return errors.New("target is required")
	}
	return nil
}

// validateInstance validates a service instance
func (s *Service) validateInstance(instance *ServiceInstance) error {
	if strings.TrimSpace(instance.ServiceName) == "" {
		return errors.New("service name is required")
	}
	if strings.TrimSpace(instance.Address) == "" {
		return errors.New("address is required")
	}
	if instance.Port <= 0 || instance.Port > 65535 {
		return errors.New("port must be between 1 and 65535")
	}
	return nil
}
