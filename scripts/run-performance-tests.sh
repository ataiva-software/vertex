#!/bin/bash

# Eden DevOps Suite - Performance Testing Script
# This script runs performance tests, benchmarks, and load tests

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
RUN_BENCHMARKS=true
RUN_LOAD_TESTS=true
RUN_STRESS_TESTS=false
RUN_ENDURANCE_TESTS=false
GENERATE_REPORTS=true
VERBOSE=false

# Performance test configuration
STRESS_TEST_DURATION=${STRESS_TEST_DURATION:-300}
STRESS_TEST_CONCURRENT_USERS=${STRESS_TEST_CONCURRENT_USERS:-1000}
ENDURANCE_TEST_DURATION=${ENDURANCE_TEST_DURATION:-3600}

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

Run performance tests for Eden DevOps Suite

OPTIONS:
    --benchmarks-only   Run only micro-benchmarks
    --load-only         Run only load tests
    --stress            Include stress tests (high resource usage)
    --endurance         Include endurance tests (very long running)
    --no-reports        Skip report generation
    -v, --verbose       Enable verbose output
    -h, --help          Show this help message

ENVIRONMENT VARIABLES:
    STRESS_TEST_DURATION          Duration for stress tests in seconds (default: 300)
    STRESS_TEST_CONCURRENT_USERS  Concurrent users for stress tests (default: 1000)
    ENDURANCE_TEST_DURATION       Duration for endurance tests in seconds (default: 3600)

EXAMPLES:
    $0                          # Run benchmarks and load tests
    $0 --benchmarks-only        # Run only micro-benchmarks
    $0 --stress                 # Include stress tests
    $0 --endurance              # Include endurance tests
    $0 --stress --endurance     # Run all performance tests

EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --benchmarks-only)
            RUN_LOAD_TESTS=false
            RUN_STRESS_TESTS=false
            RUN_ENDURANCE_TESTS=false
            shift
            ;;
        --load-only)
            RUN_BENCHMARKS=false
            RUN_STRESS_TESTS=false
            RUN_ENDURANCE_TESTS=false
            shift
            ;;
        --stress)
            RUN_STRESS_TESTS=true
            shift
            ;;
        --endurance)
            RUN_ENDURANCE_TESTS=true
            shift
            ;;
        --no-reports)
            GENERATE_REPORTS=false
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

# Function to run gradle task with error handling
run_performance_test() {
    local test_name="$1"
    local gradle_task="$2"
    local description="$3"
    local duration="$4"
    
    print_status "Starting $test_name..."
    echo "Description: $description"
    if [[ -n "$duration" ]]; then
        echo "Estimated Duration: $duration"
    fi
    echo "Command: $GRADLE_CMD $gradle_task $GRADLE_OPTS"
    echo ""
    
    local start_time=$(date +%s)
    
    if $GRADLE_CMD $gradle_task $GRADLE_OPTS; then
        local end_time=$(date +%s)
        local test_duration=$((end_time - start_time))
        local minutes=$((test_duration / 60))
        local seconds=$((test_duration % 60))
        print_success "$test_name completed successfully in ${minutes}m ${seconds}s"
    else
        print_error "$test_name failed"
        return 1
    fi
    echo ""
}

# Function to check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites for performance testing..."
    
    # Check Java version
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
        print_status "Java version: $JAVA_VERSION"
    else
        print_error "Java not found. Please install Java 17 or later."
        exit 1
    fi
    
    # Check available memory
    if command -v free &> /dev/null; then
        local available_mem=$(free -m | awk 'NR==2{printf "%.1f", $7/1024}')
        print_status "Available memory: ${available_mem}GB"
        
        if (( $(echo "$available_mem < 2.0" | bc -l) )); then
            print_warning "Low available memory. Performance tests may be affected."
        fi
    elif [[ "$(uname)" == "Darwin" ]]; then
        local total_mem=$(sysctl -n hw.memsize | awk '{print $1/1024/1024/1024}')
        print_status "Total memory: ${total_mem}GB"
    fi
    
    # Check CPU cores
    if command -v nproc &> /dev/null; then
        local cpu_cores=$(nproc)
        print_status "CPU cores: $cpu_cores"
    elif [[ "$(uname)" == "Darwin" ]]; then
        local cpu_cores=$(sysctl -n hw.ncpu)
        print_status "CPU cores: $cpu_cores"
    fi
    
    # Warn about resource-intensive tests
    if [[ "$RUN_STRESS_TESTS" == "true" ]]; then
        print_warning "Stress tests will consume significant system resources"
        print_warning "Duration: ${STRESS_TEST_DURATION}s with ${STRESS_TEST_CONCURRENT_USERS} concurrent users"
    fi
    
    if [[ "$RUN_ENDURANCE_TESTS" == "true" ]]; then
        print_warning "Endurance tests will run for ${ENDURANCE_TEST_DURATION}s ($(($ENDURANCE_TEST_DURATION / 60)) minutes)"
    fi
    
    print_success "Prerequisites check completed"
    echo ""
}

# Function to set performance test environment
setup_performance_environment() {
    print_status "Setting up performance test environment..."
    
    # Set environment variables for performance tests
    export STRESS_TEST_ENABLED=$RUN_STRESS_TESTS
    export ENDURANCE_TEST_ENABLED=$RUN_ENDURANCE_TESTS
    export STRESS_TEST_DURATION=$STRESS_TEST_DURATION
    export STRESS_TEST_CONCURRENT_USERS=$STRESS_TEST_CONCURRENT_USERS
    export ENDURANCE_TEST_DURATION=$ENDURANCE_TEST_DURATION
    
    # JVM tuning for performance tests
    export GRADLE_OPTS="$GRADLE_OPTS -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
    
    print_success "Performance environment configured"
    echo ""
}

# Function to generate performance summary
generate_performance_summary() {
    local start_time="$1"
    local end_time="$2"
    local duration=$((end_time - start_time))
    local minutes=$((duration / 60))
    local seconds=$((duration % 60))
    
    echo ""
    echo "=========================================="
    echo "       PERFORMANCE TEST SUMMARY"
    echo "=========================================="
    echo "Total Duration: ${minutes}m ${seconds}s"
    echo ""
    
    if [[ "$RUN_BENCHMARKS" == "true" ]]; then
        echo "✅ Micro-benchmarks: Executed"
    fi
    
    if [[ "$RUN_LOAD_TESTS" == "true" ]]; then
        echo "✅ Load Tests: Executed"
    fi
    
    if [[ "$RUN_STRESS_TESTS" == "true" ]]; then
        echo "✅ Stress Tests: Executed"
    fi
    
    if [[ "$RUN_ENDURANCE_TESTS" == "true" ]]; then
        echo "✅ Endurance Tests: Executed"
    fi
    
    echo ""
    echo "📊 Performance Reports:"
    echo "   • JMH Benchmarks: performance-tests/build/reports/jmh/"
    echo "   • Load Test Results: performance-tests/build/reports/gatling/"
    echo "   • Performance Metrics: performance-tests/build/reports/performance/"
    echo ""
    echo "🔍 Key Metrics to Review:"
    echo "   • Response times (p50, p95, p99)"
    echo "   • Throughput (requests/second)"
    echo "   • Error rates"
    echo "   • Resource utilization"
    echo "   • Memory usage patterns"
    echo "=========================================="
}

# Main execution
main() {
    local start_time=$(date +%s)
    
    print_status "Eden DevOps Suite - Performance Testing"
    echo "Project Root: $PROJECT_ROOT"
    echo ""
    
    # Check prerequisites
    check_prerequisites
    
    # Setup performance environment
    setup_performance_environment
    
    # Track test results
    local test_failures=0
    
    # Run micro-benchmarks
    if [[ "$RUN_BENCHMARKS" == "true" ]]; then
        if ! run_performance_test "Micro-benchmarks" ":performance-tests:runBenchmarks" "JMH micro-benchmarks for critical code paths" "5-10 minutes"; then
            ((test_failures++))
        fi
    fi
    
    # Run load tests
    if [[ "$RUN_LOAD_TESTS" == "true" ]]; then
        if ! run_performance_test "Load Tests" ":performance-tests:runGatlingTests" "Gatling load tests for API endpoints" "10-15 minutes"; then
            ((test_failures++))
        fi
    fi
    
    # Run stress tests
    if [[ "$RUN_STRESS_TESTS" == "true" ]]; then
        local stress_duration_min=$((STRESS_TEST_DURATION / 60))
        if ! run_performance_test "Stress Tests" ":performance-tests:stressTest" "High-load stress testing with $STRESS_TEST_CONCURRENT_USERS concurrent users" "${stress_duration_min} minutes"; then
            ((test_failures++))
        fi
    fi
    
    # Run endurance tests
    if [[ "$RUN_ENDURANCE_TESTS" == "true" ]]; then
        local endurance_duration_min=$((ENDURANCE_TEST_DURATION / 60))
        if ! run_performance_test "Endurance Tests" ":performance-tests:enduranceTest" "Long-running endurance testing" "${endurance_duration_min} minutes"; then
            ((test_failures++))
        fi
    fi
    
    # Generate reports
    if [[ "$GENERATE_REPORTS" == "true" ]]; then
        if ! run_performance_test "Performance Reports" ":performance-tests:generatePerformanceReport" "Generate comprehensive performance analysis" "2-3 minutes"; then
            ((test_failures++))
        fi
    fi
    
    # Generate summary
    local end_time=$(date +%s)
    generate_performance_summary "$start_time" "$end_time"
    
    # Exit with appropriate code
    if [[ $test_failures -eq 0 ]]; then
        print_success "All performance tests completed successfully!"
        exit 0
    else
        print_error "$test_failures performance test suite(s) failed"
        exit 1
    fi
}

# Run main function
main "$@"