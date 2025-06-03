#!/bin/bash

# Eden DevOps Suite - Comprehensive Test Execution Script
# This script runs all tests with proper error handling and reporting

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
RUN_UNIT_TESTS=true
RUN_INTEGRATION_TESTS=true
RUN_E2E_TESTS=true
RUN_PERFORMANCE_TESTS=false
GENERATE_COVERAGE=true
PARALLEL=true
VERBOSE=false

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

Run comprehensive test suite for Eden DevOps Suite

OPTIONS:
    -u, --unit-only         Run only unit tests
    -i, --integration-only  Run only integration tests
    -e, --e2e-only         Run only end-to-end tests
    -p, --performance      Include performance tests
    --no-coverage          Skip coverage report generation
    --no-parallel          Disable parallel test execution
    -v, --verbose          Enable verbose output
    -h, --help             Show this help message

EXAMPLES:
    $0                     # Run all tests except performance
    $0 -u                  # Run only unit tests
    $0 -p                  # Run all tests including performance
    $0 --no-coverage       # Run tests without coverage
    $0 -v                  # Run with verbose output

EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -u|--unit-only)
            RUN_INTEGRATION_TESTS=false
            RUN_E2E_TESTS=false
            RUN_PERFORMANCE_TESTS=false
            shift
            ;;
        -i|--integration-only)
            RUN_UNIT_TESTS=false
            RUN_E2E_TESTS=false
            RUN_PERFORMANCE_TESTS=false
            shift
            ;;
        -e|--e2e-only)
            RUN_UNIT_TESTS=false
            RUN_INTEGRATION_TESTS=false
            RUN_PERFORMANCE_TESTS=false
            shift
            ;;
        -p|--performance)
            RUN_PERFORMANCE_TESTS=true
            shift
            ;;
        --no-coverage)
            GENERATE_COVERAGE=false
            shift
            ;;
        --no-parallel)
            PARALLEL=false
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
if [[ "$PARALLEL" == "true" ]]; then
    GRADLE_OPTS="$GRADLE_OPTS --parallel"
fi

if [[ "$VERBOSE" == "true" ]]; then
    GRADLE_OPTS="$GRADLE_OPTS --info"
fi

# Function to run tests with error handling
run_test_suite() {
    local test_name="$1"
    local gradle_task="$2"
    local description="$3"
    
    print_status "Starting $test_name..."
    echo "Description: $description"
    echo "Command: $GRADLE_CMD $gradle_task $GRADLE_OPTS"
    echo ""
    
    if $GRADLE_CMD $gradle_task $GRADLE_OPTS; then
        print_success "$test_name completed successfully"
    else
        print_error "$test_name failed"
        return 1
    fi
    echo ""
}

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
    
    # Check Docker for integration tests
    if [[ "$RUN_INTEGRATION_TESTS" == "true" || "$RUN_E2E_TESTS" == "true" ]]; then
        if command -v docker &> /dev/null; then
            if docker info &> /dev/null; then
                print_status "Docker is running"
            else
                print_error "Docker is not running. Please start Docker daemon."
                exit 1
            fi
        else
            print_error "Docker not found. Required for integration and E2E tests."
            exit 1
        fi
    fi
    
    print_success "Prerequisites check passed"
    echo ""
}

# Function to clean previous test results
clean_previous_results() {
    print_status "Cleaning previous test results..."
    
    if $GRADLE_CMD clean $GRADLE_OPTS; then
        print_success "Clean completed"
    else
        print_warning "Clean failed, continuing anyway"
    fi
    echo ""
}

# Function to generate test summary
generate_summary() {
    local start_time="$1"
    local end_time="$2"
    local duration=$((end_time - start_time))
    local minutes=$((duration / 60))
    local seconds=$((duration % 60))
    
    echo ""
    echo "=========================================="
    echo "           TEST EXECUTION SUMMARY"
    echo "=========================================="
    echo "Total Duration: ${minutes}m ${seconds}s"
    echo ""
    
    if [[ "$RUN_UNIT_TESTS" == "true" ]]; then
        echo "✅ Unit Tests: Executed"
    fi
    
    if [[ "$RUN_INTEGRATION_TESTS" == "true" ]]; then
        echo "✅ Integration Tests: Executed"
    fi
    
    if [[ "$RUN_E2E_TESTS" == "true" ]]; then
        echo "✅ End-to-End Tests: Executed"
    fi
    
    if [[ "$RUN_PERFORMANCE_TESTS" == "true" ]]; then
        echo "✅ Performance Tests: Executed"
    fi
    
    if [[ "$GENERATE_COVERAGE" == "true" ]]; then
        echo "✅ Coverage Report: Generated"
    fi
    
    echo ""
    echo "📊 Reports Available:"
    echo "   • Test Results: build/reports/tests/test/index.html"
    if [[ "$GENERATE_COVERAGE" == "true" ]]; then
        echo "   • Coverage Report: build/reports/kover/html/index.html"
    fi
    echo "   • Integration Test Results: integration-tests/build/reports/tests/test/index.html"
    echo "   • E2E Test Results: e2e-tests/build/reports/tests/test/index.html"
    if [[ "$RUN_PERFORMANCE_TESTS" == "true" ]]; then
        echo "   • Performance Test Results: performance-tests/build/reports/tests/test/index.html"
    fi
    echo "=========================================="
}

# Main execution
main() {
    local start_time=$(date +%s)
    
    print_status "Eden DevOps Suite - Comprehensive Test Execution"
    echo "Project Root: $PROJECT_ROOT"
    echo ""
    
    # Check prerequisites
    check_prerequisites
    
    # Clean previous results
    clean_previous_results
    
    # Track test results
    local test_failures=0
    
    # Run unit tests
    if [[ "$RUN_UNIT_TESTS" == "true" ]]; then
        if ! run_test_suite "Unit Tests" "test" "Run all unit tests across all modules"; then
            ((test_failures++))
        fi
    fi
    
    # Run integration tests
    if [[ "$RUN_INTEGRATION_TESTS" == "true" ]]; then
        if ! run_test_suite "Integration Tests" "integrationTest" "Run integration tests with real databases and services"; then
            ((test_failures++))
        fi
    fi
    
    # Run E2E tests
    if [[ "$RUN_E2E_TESTS" == "true" ]]; then
        if ! run_test_suite "End-to-End Tests" "e2eTest" "Run complete user workflow tests"; then
            ((test_failures++))
        fi
    fi
    
    # Run performance tests
    if [[ "$RUN_PERFORMANCE_TESTS" == "true" ]]; then
        if ! run_test_suite "Performance Tests" "performanceTest" "Run performance benchmarks and load tests"; then
            ((test_failures++))
        fi
    fi
    
    # Generate coverage report
    if [[ "$GENERATE_COVERAGE" == "true" ]]; then
        if ! run_test_suite "Coverage Report" "coverageReport" "Generate comprehensive coverage report"; then
            ((test_failures++))
        fi
    fi
    
    # Generate summary
    local end_time=$(date +%s)
    generate_summary "$start_time" "$end_time"
    
    # Exit with appropriate code
    if [[ $test_failures -eq 0 ]]; then
        print_success "All tests completed successfully!"
        exit 0
    else
        print_error "$test_failures test suite(s) failed"
        exit 1
    fi
}

# Run main function
main "$@"