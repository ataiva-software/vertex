package apigateway

import (
	"context"
	"time"
)

// ServiceRoute represents a route configuration for a service
type ServiceRoute struct {
	ID          string            `json:"id"`
	ServiceName string            `json:"service_name"`
	Path        string            `json:"path"`
	Target      string            `json:"target"`
	Methods     []string          `json:"methods"`
	Middleware  []string          `json:"middleware"`
	Metadata    map[string]string `json:"metadata"`
	CreatedAt   time.Time         `json:"created_at"`
	UpdatedAt   time.Time         `json:"updated_at"`
}

// ServiceInstance represents a service instance in the registry
type ServiceInstance struct {
	ID          string            `json:"id"`
	ServiceName string            `json:"service_name"`
	Address     string            `json:"address"`
	Port        int               `json:"port"`
	Health      HealthStatus      `json:"health"`
	Metadata    map[string]string `json:"metadata"`
	LastSeen    time.Time         `json:"last_seen"`
	RegisteredAt time.Time        `json:"registered_at"`
}

// HealthStatus represents the health status of a service instance
type HealthStatus int

const (
	HealthStatusHealthy HealthStatus = iota
	HealthStatusUnhealthy
	HealthStatusUnknown
)

// String returns the string representation of HealthStatus
func (h HealthStatus) String() string {
	switch h {
	case HealthStatusHealthy:
		return "healthy"
	case HealthStatusUnhealthy:
		return "unhealthy"
	case HealthStatusUnknown:
		return "unknown"
	default:
		return "unknown"
	}
}

// RateLimiter represents a rate limiter for a user or IP
type RateLimiter struct {
	ID       string    `json:"id"`
	Limit    int       `json:"limit"`
	Window   time.Duration `json:"window"`
	Requests int       `json:"requests"`
	ResetAt  time.Time `json:"reset_at"`
}

// Allow checks if a request is allowed under the rate limit
func (r *RateLimiter) Allow() bool {
	now := time.Now()
	
	// Reset if window has passed
	if now.After(r.ResetAt) {
		r.Requests = 0
		r.ResetAt = now.Add(r.Window)
	}
	
	if r.Requests >= r.Limit {
		return false
	}
	
	r.Requests++
	return true
}

// Status returns the current rate limit status
func (r *RateLimiter) Status() *RateLimitStatus {
	remaining := r.Limit - r.Requests
	if remaining < 0 {
		remaining = 0
	}
	
	return &RateLimitStatus{
		Limit:     r.Limit,
		Remaining: remaining,
		ResetAt:   r.ResetAt,
	}
}

// RateLimitStatus represents the current rate limit status
type RateLimitStatus struct {
	Limit     int       `json:"limit"`
	Remaining int       `json:"remaining"`
	ResetAt   time.Time `json:"reset_at"`
}

// Middleware represents a middleware function
type Middleware struct {
	Name     string                                                    `json:"name"`
	Priority int                                                       `json:"priority"`
	Handler  func(ctx context.Context, req *Request) (*Request, error) `json:"-"`
}

// Request represents an HTTP request in the gateway
type Request struct {
	ID      string            `json:"id"`
	Method  string            `json:"method"`
	Path    string            `json:"path"`
	Headers map[string]string `json:"headers"`
	Body    []byte            `json:"body,omitempty"`
	UserID  string            `json:"user_id,omitempty"`
	ClientIP string           `json:"client_ip"`
}

// Response represents an HTTP response from the gateway
type Response struct {
	StatusCode int               `json:"status_code"`
	Headers    map[string]string `json:"headers"`
	Body       []byte            `json:"body,omitempty"`
	Duration   time.Duration     `json:"duration"`
}

// ProxyConfig represents proxy configuration
type ProxyConfig struct {
	Timeout         time.Duration `json:"timeout"`
	RetryAttempts   int           `json:"retry_attempts"`
	RetryDelay      time.Duration `json:"retry_delay"`
	CircuitBreaker  bool          `json:"circuit_breaker"`
	LoadBalancer    string        `json:"load_balancer"` // round_robin, least_connections, etc.
}

// CircuitBreaker represents a circuit breaker for a service
type CircuitBreaker struct {
	ServiceName    string        `json:"service_name"`
	State          CircuitState  `json:"state"`
	FailureCount   int           `json:"failure_count"`
	FailureThreshold int         `json:"failure_threshold"`
	Timeout        time.Duration `json:"timeout"`
	LastFailure    time.Time     `json:"last_failure"`
}

// CircuitState represents the state of a circuit breaker
type CircuitState int

const (
	CircuitStateClosed CircuitState = iota
	CircuitStateOpen
	CircuitStateHalfOpen
)

// String returns the string representation of CircuitState
func (c CircuitState) String() string {
	switch c {
	case CircuitStateClosed:
		return "closed"
	case CircuitStateOpen:
		return "open"
	case CircuitStateHalfOpen:
		return "half_open"
	default:
		return "unknown"
	}
}
