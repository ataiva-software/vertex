# Eden DevOps Suite - Go Implementation
# Makefile for building, testing, and managing the project

.PHONY: help build test clean run-vault docker-build docker-run deps lint fmt vet

# Default target
help:
	@echo "Eden DevOps Suite - Go Implementation"
	@echo ""
	@echo "Available targets:"
	@echo "  build        - Build all services"
	@echo "  test         - Run all tests"
	@echo "  test-cover   - Run tests with coverage"
	@echo "  clean        - Clean build artifacts"
	@echo "  run-vault    - Run the vault service"
	@echo "  docker-build - Build Docker images"
	@echo "  docker-run   - Run services with Docker Compose"
	@echo "  deps         - Download dependencies"
	@echo "  lint         - Run linter"
	@echo "  fmt          - Format code"
	@echo "  vet          - Run go vet"
	@echo "  remove-kotlin - Remove all Kotlin code (DESTRUCTIVE)"

# Build targets
build: deps
	@echo "Building Eden single binary..."
	@mkdir -p bin
	go build -o bin/vertex ./cmd/vertex/
	@echo "✅ Eden single binary built successfully!"
	@echo ""
	@echo "Usage:"
	@echo "  ./bin/vertex server           # Run all services"
	@echo "  ./bin/vertex service vault    # Run specific service"
	@echo "  ./bin/vertex status           # Check service status"
	@echo "  ./bin/vertex vault list       # CLI commands"

build-all: build
	@echo "All services built successfully"

# Test targets
test:
	@echo "Running tests..."
	go test ./... -v

test-cover:
	@echo "Running tests with coverage..."
	go test ./... -cover -coverprofile=coverage.out
	go tool cover -html=coverage.out -o coverage.html
	@echo "Coverage report generated: coverage.html"

test-short:
	@echo "Running short tests..."
	go test ./... -short

# Development targets
run-all: build
	@echo "Starting all Eden services..."
	./bin/vertex server

run-vault: build
	@echo "Starting Vault service..."
	./bin/vertex service vault

run-api-gateway: build
	@echo "Starting API Gateway..."
	./bin/vertex service api-gateway

run-flow: build
	@echo "Starting Flow service..."
	./bin/vertex service flow

run-task: build
	@echo "Starting Task service..."
	./bin/vertex service task

run-monitor: build
	@echo "Starting Monitor service..."
	./bin/vertex service monitor

run-sync: build
	@echo "Starting Sync service..."
	./bin/vertex service sync

run-insight: build
	@echo "Starting Insight service..."
	./bin/vertex service insight

run-hub: build
	@echo "Starting Hub service..."
	./bin/vertex service hub

deps:
	@echo "Downloading dependencies..."
	go mod download
	go mod tidy

# Code quality targets
fmt:
	@echo "Formatting code..."
	go fmt ./...

vet:
	@echo "Running go vet..."
	go vet ./...

lint: fmt vet
	@echo "Code quality checks complete"

# Docker targets
docker-build:
	@echo "Building Docker images..."
	docker build -t eden-vault -f deployments/docker/Dockerfile.vault .

docker-run:
	@echo "Starting services with Docker Compose..."
	docker-compose up -d

docker-stop:
	@echo "Stopping Docker services..."
	docker-compose down

# Cleanup targets
clean:
	@echo "Cleaning build artifacts..."
	rm -rf bin/
	rm -f coverage.out coverage.html
	go clean -cache
	@echo "Clean complete!"

# Development environment
dev-setup:
	@echo "Setting up development environment..."
	go install golang.org/x/tools/cmd/goimports@latest
	go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest
	@echo "Development tools installed"

# Database operations
db-migrate:
	@echo "Running database migrations..."
	# This would run actual migrations in a real implementation
	@echo "Migrations complete"

# DESTRUCTIVE: Remove all Kotlin code
remove-kotlin:
	@echo "WARNING: This will permanently delete all Kotlin code!"
	@echo "Press Ctrl+C to cancel, or wait 10 seconds to continue..."
	@sleep 10
	@echo "Removing Kotlin code..."
	rm -rf services/
	rm -rf shared/
	rm -rf clients/
	rm -rf gradle/
	rm -rf .gradle/
	rm -rf build/
	rm -rf kotlin-js-store/
	rm -rf .idea/
	rm -f build.gradle.kts
	rm -f settings.gradle.kts
	rm -f gradle.properties
	rm -f gradlew
	rm -f gradlew.bat
	find . -name "*.kt" -type f -delete
	find . -name "*.kts" -type f -delete
	find . -name "build.gradle*" -type f -delete
	@echo "Kotlin code removed. Go implementation is now the primary codebase."

# CI/CD targets
ci: deps lint test
	@echo "CI pipeline complete"

# Show project status
status:
	@echo "Eden DevOps Suite - Go Implementation Status"
	@echo "============================================="
	@echo "Go version: $(shell go version)"
	@echo "Project structure:"
	@find . -name "*.go" -type f | head -10
	@echo "..."
	@echo "Total Go files: $(shell find . -name "*.go" -type f | wc -l)"
	@echo "Test coverage: $(shell go test ./... -cover 2>/dev/null | grep coverage | tail -1 || echo 'Run make test-cover')"

# Docker targets
docker-build: build
	@echo "Building Eden Docker image..."
	docker build -t eden:latest .
	@echo "✅ Docker image built successfully!"

docker-run: docker-build
	@echo "Starting Eden with Docker Compose..."
	docker-compose -f docker-compose-single.yml up -d
	@echo "✅ Eden services started!"
	@echo ""
	@echo "Services available at:"
	@echo "  API Gateway: http://localhost:8000"
	@echo "  Vault:       http://localhost:8080"
	@echo "  Flow:        http://localhost:8081"
	@echo "  Task:        http://localhost:8082"
	@echo "  Monitor:     http://localhost:8083"
	@echo "  Sync:        http://localhost:8084"
	@echo "  Insight:     http://localhost:8085"
	@echo "  Hub:         http://localhost:8086"

docker-stop:
	@echo "Stopping Eden services..."
	docker-compose -f docker-compose-single.yml down
	@echo "✅ Eden services stopped!"

docker-logs:
	@echo "Showing Eden service logs..."
	docker-compose -f docker-compose-single.yml logs -f eden
