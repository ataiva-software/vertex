#!/bin/bash

# Eden DevOps Suite - Development Environment Setup Script
# This script sets up the complete development environment for Eden

set -e

echo "🌱 Setting up Eden DevOps Suite development environment..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# Check if required tools are installed
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    # Check Java
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed. Please install Java 17 or higher."
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        print_error "Java 17 or higher is required. Current version: $JAVA_VERSION"
        exit 1
    fi
    print_success "Java $JAVA_VERSION detected"
    
    # Check Docker
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed. Please install Docker."
        exit 1
    fi
    print_success "Docker detected"
    
    # Check Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose is not installed. Please install Docker Compose."
        exit 1
    fi
    print_success "Docker Compose detected"
    
    # Check Git
    if ! command -v git &> /dev/null; then
        print_error "Git is not installed. Please install Git."
        exit 1
    fi
    print_success "Git detected"
}

# Setup Gradle wrapper
setup_gradle() {
    print_status "Setting up Gradle wrapper..."
    
    if [ ! -f "./gradlew" ]; then
        print_error "Gradle wrapper not found. Please ensure gradlew is in the project root."
        exit 1
    fi
    
    chmod +x ./gradlew
    print_success "Gradle wrapper is ready"
}

# Build shared libraries first
build_shared_libraries() {
    print_status "Building shared libraries..."
    
    ./gradlew :shared:core:build -x test
    ./gradlew :shared:auth:build -x test
    ./gradlew :shared:crypto:build -x test
    ./gradlew :shared:database:build -x test
    ./gradlew :shared:events:build -x test
    ./gradlew :shared:config:build -x test
    
    print_success "Shared libraries built successfully"
}

# Start infrastructure services
start_infrastructure() {
    print_status "Starting infrastructure services..."
    
    # Stop any existing containers
    docker-compose down 2>/dev/null || true
    
    # Start PostgreSQL and Redis
    docker-compose up -d postgres redis
    
    # Wait for services to be ready
    print_status "Waiting for database to be ready..."
    timeout=60
    while ! docker-compose exec -T postgres pg_isready -U eden -d eden_dev &>/dev/null; do
        sleep 2
        timeout=$((timeout - 2))
        if [ $timeout -le 0 ]; then
            print_error "Database failed to start within 60 seconds"
            exit 1
        fi
    done
    
    print_status "Waiting for Redis to be ready..."
    timeout=30
    while ! docker-compose exec -T redis redis-cli ping &>/dev/null; do
        sleep 2
        timeout=$((timeout - 2))
        if [ $timeout -le 0 ]; then
            print_error "Redis failed to start within 30 seconds"
            exit 1
        fi
    done
    
    print_success "Infrastructure services are ready"
}

# Build and start services
build_and_start_services() {
    print_status "Building and starting Eden services..."
    
    # Build services
    ./gradlew :services:api-gateway:build -x test
    
    # Start all services
    docker-compose up -d --build
    
    print_status "Waiting for services to be ready..."
    sleep 30
    
    # Check if API Gateway is responding
    timeout=60
    while ! curl -f http://localhost:8080/health &>/dev/null; do
        sleep 5
        timeout=$((timeout - 5))
        if [ $timeout -le 0 ]; then
            print_warning "API Gateway health check failed, but continuing..."
            break
        fi
    done
    
    print_success "Eden services are starting up"
}

# Build CLI
build_cli() {
    print_status "Building Eden CLI..."
    
    # Build CLI for current platform
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        ./gradlew :clients:cli:linkReleaseExecutableLinuxX64
        CLI_PATH="./clients/cli/build/bin/linuxX64/releaseExecutable/eden"
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        if [[ $(uname -m) == "arm64" ]]; then
            ./gradlew :clients:cli:linkReleaseExecutableMacosArm64
            CLI_PATH="./clients/cli/build/bin/macosArm64/releaseExecutable/eden"
        else
            ./gradlew :clients:cli:linkReleaseExecutableMacosX64
            CLI_PATH="./clients/cli/build/bin/macosX64/releaseExecutable/eden"
        fi
    else
        print_warning "CLI build skipped for unsupported platform: $OSTYPE"
        CLI_PATH=""
    fi
    
    if [ -n "$CLI_PATH" ] && [ -f "$CLI_PATH" ]; then
        print_success "Eden CLI built successfully at $CLI_PATH"
    fi
}

# Setup development data
setup_dev_data() {
    print_status "Setting up development data..."
    
    # The database initialization script already creates default data
    # Additional setup can be added here if needed
    
    print_success "Development data is ready"
}

# Print service information
print_service_info() {
    echo ""
    echo "🎉 Eden DevOps Suite development environment is ready!"
    echo ""
    echo "📋 Service URLs:"
    echo "   • API Gateway:    http://localhost:8080"
    echo "   • Web UI:         http://localhost:3000 (when built)"
    echo "   • Database:       localhost:5432 (eden/dev_password)"
    echo "   • Redis:          localhost:6379"
    echo ""
    echo "🔐 Default Credentials:"
    echo "   • Email:    admin@eden.local"
    echo "   • Password: admin123"
    echo ""
    echo "🛠️  Useful Commands:"
    echo "   • View logs:           docker-compose logs -f"
    echo "   • Stop services:       docker-compose down"
    echo "   • Restart services:    docker-compose restart"
    echo "   • Build project:       ./gradlew build"
    echo "   • Run tests:           ./gradlew test"
    
    if [ -n "$CLI_PATH" ] && [ -f "$CLI_PATH" ]; then
        echo "   • Use CLI:             $CLI_PATH --help"
    fi
    
    echo ""
    echo "📚 Documentation:"
    echo "   • README:              ./README.md"
    echo "   • Architecture:        ./TECHNICAL_ARCHITECTURE.md"
    echo "   • Roadmap:             ./IMPLEMENTATION_ROADMAP.md"
    echo ""
}

# Cleanup function
cleanup() {
    if [ $? -ne 0 ]; then
        print_error "Setup failed. Cleaning up..."
        docker-compose down 2>/dev/null || true
    fi
}

# Set trap for cleanup
trap cleanup EXIT

# Main execution
main() {
    echo "🌱 Eden DevOps Suite - Development Setup"
    echo "========================================"
    echo ""
    
    check_prerequisites
    setup_gradle
    build_shared_libraries
    start_infrastructure
    build_and_start_services
    build_cli
    setup_dev_data
    print_service_info
    
    # Remove trap since we succeeded
    trap - EXIT
}

# Run main function
main "$@"