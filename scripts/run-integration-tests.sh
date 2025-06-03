#!/bin/bash

# Eden DevOps Suite - Integration Tests Script
# This script runs integration tests with proper service dependencies

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
GRADLE_CMD="./gradlew"

# Default options
START_SERVICES=true
STOP_SERVICES=true
CLEANUP_ON_EXIT=true
WAIT_FOR_SERVICES=true
VERBOSE=false
DATABASE_TESTS=true
REDIS_TESTS=true
SERVICE_TESTS=true

# Service configuration
POSTGRES_PORT=5433
REDIS_PORT=6380
API_GATEWAY_PORT=8080
HEALTH_CHECK_TIMEOUT=60
HEALTH_CHECK_INTERVAL=2

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to print usage
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Run integration tests for Eden DevOps Suite with service dependencies

OPTIONS:
    --no-services       Don't start/stop services (assume they're running)
    --no-cleanup        Don't cleanup services on exit
    --no-wait           Don't wait for services to be ready
    --database-only     Run only database integration tests
    --redis-only        Run only Redis integration tests
    --services-only     Run only service integration tests
    -v, --verbose       Enable verbose output
    -h, --help          Show this help message

EXAMPLES:
    $0                  # Run all integration tests with service management
    $0 --no-services    # Run tests assuming services are already running
    $0 --database-only  # Run only database integration tests
    $0 -v               # Run with verbose output

ENVIRONMENT VARIABLES:
    DATABASE_URL        Override database connection URL
    REDIS_URL           Override Redis connection URL
    API_GATEWAY_URL     Override API Gateway URL

EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --no-services)
            START_SERVICES=false
            STOP_SERVICES=false
            shift
            ;;
        --no-cleanup)
            CLEANUP_ON_EXIT=false
            shift
            ;;
        --no-wait)
            WAIT_FOR_SERVICES=false
            shift
            ;;
        --database-only)
            REDIS_TESTS=false
            SERVICE_TESTS=false
            shift
            ;;
        --redis-only)
            DATABASE_TESTS=false
            SERVICE_TESTS=false
            shift
            ;;
        --services-only)
            DATABASE_TESTS=false
            REDIS_TESTS=false
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Change to project root
cd "$PROJECT_ROOT"

# Check if gradlew exists
if [[ ! -f "$GRADLE_CMD" ]]; then
    print_error "Gradle wrapper not found at $GRADLE_CMD"
    exit 1
fi

# Make gradlew executable
chmod +x "$GRADLE_CMD"

# Build gradle options
GRADLE_OPTS=""
if [[ "$VERBOSE" == "true" ]]; then
    GRADLE_OPTS="$GRADLE_OPTS --info"
fi

# Function to cleanup on exit
cleanup_on_exit() {
    if [[ "$CLEANUP_ON_EXIT" == "true" && "$STOP_SERVICES" == "true" ]]; then
        print_status "Cleaning up services..."
        stop_services
    fi
}

# Set trap for cleanup
trap cleanup_on_exit EXIT

# Function to check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    # Check Java version
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
        print_status "Java version: $JAVA_VERSION"
    else
        print_error "Java not found. Please install Java 17 or later."
        exit 1
    fi
    
    # Check Docker if services need to be started
    if [[ "$START_SERVICES" == "true" ]]; then
        if command -v docker &> /dev/null; then
            if docker info &> /dev/null; then
                print_status "Docker is running"
            else
                print_error "Docker is not running. Please start Docker daemon."
                exit 1
            fi
        else
            print_error "Docker not found. Required for starting test services."
            exit 1
        fi
        
        # Check docker-compose
        if command -v docker-compose &> /dev/null; then
            print_status "Docker Compose is available"
        else
            print_error "Docker Compose not found. Required for starting test services."
            exit 1
        fi
    fi
    
    print_success "Prerequisites check passed"
    echo ""
}

# Function to start services
start_services() {
    if [[ "$START_SERVICES" == "false" ]]; then
        print_status "Skipping service startup (assuming services are running)"
        return 0
    fi
    
    print_status "Starting test services..."
    
    # Create docker-compose override for testing
    cat > docker-compose.test.override.yml << EOF
version: '3.8'
services:
  postgres:
    ports:
      - "${POSTGRES_PORT}:5432"
    environment:
      - POSTGRES_DB=eden_test
      - POSTGRES_USER=testuser
      - POSTGRES_PASSWORD=testpass
    
  redis:
    ports:
      - "${REDIS_PORT}:6379"
    
  api-gateway:
    ports:
      - "${API_GATEWAY_PORT}:8080"
    environment:
      - DATABASE_URL=jdbc:postgresql://postgres:5432/eden_test
      - DATABASE_USER=testuser
      - DATABASE_PASSWORD=testpass
      - REDIS_URL=redis://redis:6379
    depends_on:
      - postgres
      - redis
EOF
    
    # Start services
    if docker-compose -f docker-compose.yml -f docker-compose.test.override.yml up -d; then
        print_success "Services started successfully"
    else
        print_error "Failed to start services"
        return 1
    fi
    
    return 0
}

# Function to wait for services
wait_for_services() {
    if [[ "$WAIT_FOR_SERVICES" == "false" ]]; then
        print_status "Skipping service health checks"
        return 0
    fi
    
    print_status "Waiting for services to be ready..."
    
    # Wait for PostgreSQL
    if [[ "$DATABASE_TESTS" == "true" ]]; then
        print_status "Waiting for PostgreSQL on port $POSTGRES_PORT..."
        local postgres_ready=false
        for ((i=1; i<=HEALTH_CHECK_TIMEOUT; i+=HEALTH_CHECK_INTERVAL)); do
            if nc -z localhost $POSTGRES_PORT 2>/dev/null; then
                postgres_ready=true
                break
            fi
            sleep $HEALTH_CHECK_INTERVAL
        done
        
        if [[ "$postgres_ready" == "true" ]]; then
            print_success "PostgreSQL is ready"
        else
            print_error "PostgreSQL failed to start within $HEALTH_CHECK_TIMEOUT seconds"
            return 1
        fi
    fi
    
    # Wait for Redis
    if [[ "$REDIS_TESTS" == "true" ]]; then
        print_status "Waiting for Redis on port $REDIS_PORT..."
        local redis_ready=false
        for ((i=1; i<=HEALTH_CHECK_TIMEOUT; i+=HEALTH_CHECK_INTERVAL)); do
            if nc -z localhost $REDIS_PORT 2>/dev/null; then
                redis_ready=true
                break
            fi
            sleep $HEALTH_CHECK_INTERVAL
        done
        
        if [[ "$redis_ready" == "true" ]]; then
            print_success "Redis is ready"
        else
            print_error "Redis failed to start within $HEALTH_CHECK_TIMEOUT seconds"
            return 1
        fi
    fi
    
    # Wait for API Gateway
    if [[ "$SERVICE_TESTS" == "true" ]]; then
        print_status "Waiting for API Gateway on port $API_GATEWAY_PORT..."
        local api_ready=false
        for ((i=1; i<=HEALTH_CHECK_TIMEOUT; i+=HEALTH_CHECK_INTERVAL)); do
            if curl -f http://localhost:$API_GATEWAY_PORT/health &>/dev/null; then
                api_ready=true
                break
            fi
            sleep $HEALTH_CHECK_INTERVAL
        done
        
        if [[ "$api_ready" == "true" ]]; then
            print_success "API Gateway is ready"
        else
            print_warning "API Gateway not ready, some tests may fail"
        fi
    fi
    
    return 0
}

# Function to stop services
stop_services() {
    if [[ "$STOP_SERVICES" == "false" ]]; then
        print_status "Skipping service shutdown"
        return 0
    fi
    
    print_status "Stopping test services..."
    
    # Stop services
    if docker-compose -f docker-compose.yml -f docker-compose.test.override.yml down; then
        print_success "Services stopped successfully"
    else
        print_warning "Some services may not have stopped cleanly"
    fi
    
    # Clean up override file
    if [[ -f "docker-compose.test.override.yml" ]]; then
        rm -f docker-compose.test.override.yml
    fi
    
    return 0
}

# Function to run integration tests
run_integration_tests() {
    print_status "Running integration tests..."
    
    # Set environment variables
    export DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:$POSTGRES_PORT/eden_test}"
    export DATABASE_USER="${DATABASE_USER:-testuser}"
    export DATABASE_PASSWORD="${DATABASE_PASSWORD:-testpass}"
    export REDIS_URL="${REDIS_URL:-redis://localhost:$REDIS_PORT}"
    export API_GATEWAY_URL="${API_GATEWAY_URL:-http://localhost:$API_GATEWAY_PORT}"
    
    # Build test command based on options
    local test_tasks=()
    
    if [[ "$DATABASE_TESTS" == "true" ]]; then
        test_tasks+=("integrationTest")
    fi
    
    if [[ "${#test_tasks[@]}" -eq 0 ]]; then
        test_tasks+=("integrationTest")
    fi
    
    # Run tests
    local test_failures=0
    for task in "${test_tasks[@]}"; do
        print_status "Running $task..."
        
