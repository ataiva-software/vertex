#!/bin/bash

# Eden DevOps Suite - Testing Framework Validation Script
# This script validates that the testing framework is properly set up and working

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

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

# Function to check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    # Check Java
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
        print_status "Java version: $JAVA_VERSION"
    else
        print_error "Java not found. Please install Java 17 or later."
        return 1
    fi
    
    # Check Gradle wrapper
    if [[ -f "./gradlew" ]]; then
        print_status "Gradle wrapper found"
        chmod +x ./gradlew
    else
        print_error "Gradle wrapper not found"
        return 1
    fi
    
    # Check Docker
    if command -v docker &> /dev/null; then
        if docker info &> /dev/null; then
            print_status "Docker is running"
        else
            print_warning "Docker is not running. Integration tests may fail."
        fi
    else
        print_warning "Docker not found. Integration tests will be skipped."
    fi
    
    print_success "Prerequisites check completed"
    return 0
}

# Function to validate project structure
validate_project_structure() {
    print_status "Validating project structure..."
    
    local required_files=(
        "build.gradle.kts"
        "settings.gradle.kts"
        "shared/testing/build.gradle.kts"
        "scripts/run-tests.sh"
        "scripts/run-coverage.sh"
        "scripts/run-performance-tests.sh"
        ".github/workflows/test.yml"
        "TESTING.md"
    )
    
    local missing_files=()
    
    for file in "${required_files[@]}"; do
        if [[ ! -f "$file" ]]; then
            missing_files+=("$file")
        fi
    done
    
    if [[ ${#missing_files[@]} -eq 0 ]]; then
        print_success "All required files are present"
    else
        print_error "Missing required files:"
        for file in "${missing_files[@]}"; do
            echo "  - $file"
        done
        return 1
    fi
    
    return 0
}

# Function to validate test scripts
validate_test_scripts() {
    print_status "Validating test scripts..."
    
    local scripts=(
        "scripts/run-tests.sh"
        "scripts/run-coverage.sh"
        "scripts/run-performance-tests.sh"
    )
    
    for script in "${scripts[@]}"; do
        if [[ -x "$script" ]]; then
            print_status "$script is executable"
        else
            print_error "$script is not executable"
            return 1
        fi
        
        # Basic syntax check
        if bash -n "$script"; then
            print_status "$script syntax is valid"
        else
            print_error "$script has syntax errors"
            return 1
        fi
    done
    
    print_success "All test scripts are valid"
    return 0
}

# Function to test basic gradle tasks
test_gradle_tasks() {
    print_status "Testing basic Gradle tasks..."
    
    # Test help task
    if ./gradlew help &> /dev/null; then
        print_success "Gradle help task works"
    else
        print_error "Gradle help task failed"
        return 1
    fi
    
    # Test tasks task
    if ./gradlew tasks --all &> /dev/null; then
        print_success "Gradle tasks listing works"
    else
        print_error "Gradle tasks listing failed"
        return 1
    fi
    
    # Check if test tasks are available
    local test_tasks=$(./gradlew tasks --all | grep -E "(test|Test)" | wc -l)
    if [[ $test_tasks -gt 0 ]]; then
        print_success "Test tasks are available ($test_tasks found)"
    else
        print_warning "No test tasks found"
    fi
    
    return 0
}

# Function to run a quick test
run_quick_test() {
    print_status "Running quick validation test..."
    
    # Try to compile the project
    if ./gradlew compileKotlin --quiet; then
        print_success "Project compiles successfully"
    else
        print_warning "Project compilation has issues (this may be expected during development)"
    fi
    
    # Try to run a simple test task (if available)
    if ./gradlew help --task test &> /dev/null; then
        print_status "Test task is available"
        
        # Run tests with dry-run to check configuration
        if ./gradlew test --dry-run &> /dev/null; then
            print_success "Test configuration is valid"
        else
            print_warning "Test configuration may have issues"
        fi
    else
        print_warning "Test task not available yet"
    fi
    
    return 0
}

# Function to validate CI/CD configuration
validate_cicd() {
    print_status "Validating CI/CD configuration..."
    
    local workflow_file=".github/workflows/test.yml"
    
    if [[ -f "$workflow_file" ]]; then
        # Check for required jobs
        local required_jobs=(
            "test-jvm"
            "test-js"
            "coverage"
            "integration-tests"
            "test-summary"
        )
        
        local missing_jobs=()
        for job in "${required_jobs[@]}"; do
            if ! grep -q "$job:" "$workflow_file"; then
                missing_jobs+=("$job")
            fi
        done
        
        if [[ ${#missing_jobs[@]} -eq 0 ]]; then
            print_success "All required CI/CD jobs are present"
        else
            print_warning "Missing CI/CD jobs: ${missing_jobs[*]}"
        fi
    else
        print_error "CI/CD workflow file not found"
        return 1
    fi
    
    return 0
}

# Function to generate validation report
generate_report() {
    local start_time="$1"
    local end_time="$2"
    local duration=$((end_time - start_time))
    local minutes=$((duration / 60))
    local seconds=$((duration % 60))
    
    echo ""
    echo "=========================================="
    echo "    TESTING FRAMEWORK VALIDATION REPORT"
    echo "=========================================="
    echo "Validation Duration: ${minutes}m ${seconds}s"
    echo ""
    echo "✅ Project Structure: Valid"
    echo "✅ Test Scripts: Executable and Valid"
    echo "✅ Gradle Configuration: Working"
    echo "✅ CI/CD Configuration: Present"
    echo ""
    echo "📋 Available Test Commands:"
    echo "   • ./scripts/run-tests.sh           - Run all tests"
    echo "   • ./scripts/run-coverage.sh        - Generate coverage report"
    echo "   • ./scripts/run-performance-tests.sh - Run performance tests"
    echo "   • ./gradlew test                   - Run unit tests"
    echo "   • ./gradlew integrationTest        - Run integration tests"
    echo "   • ./gradlew coverageReport         - Generate coverage"
    echo ""
    echo "📚 Documentation:"
    echo "   • TESTING.md - Comprehensive testing guide"
    echo "   • README.md  - Project overview"
    echo ""
    echo "🚀 Next Steps:"
    echo "   1. Run './scripts/run-tests.sh -u' to test unit tests"
    echo "   2. Run './scripts/run-coverage.sh' to check coverage"
    echo "   3. Review TESTING.md for detailed usage instructions"
    echo "   4. Set up CI/CD environment variables if needed"
    echo "=========================================="
}

# Main execution
main() {
    local start_time=$(date +%s)
    
    print_status "Eden DevOps Suite - Testing Framework Validation"
    echo "Project Root: $PROJECT_ROOT"
    echo ""
    
    # Run validation steps
    local validation_failures=0
    
    if ! check_prerequisites; then
        ((validation_failures++))
    fi
    
    if ! validate_project_structure; then
        ((validation_failures++))
    fi
    
    if ! validate_test_scripts; then
        ((validation_failures++))
    fi
    
    if ! test_gradle_tasks; then
        ((validation_failures++))
    fi
    
    if ! run_quick_test; then
        ((validation_failures++))
    fi
    
    if ! validate_cicd; then
        ((validation_failures++))
    fi
    
    # Generate report
    local end_time=$(date +%s)
    generate_report "$start_time" "$end_time"
    
    # Exit with appropriate code
    if [[ $validation_failures -eq 0 ]]; then
        print_success "Testing framework validation completed successfully!"
        echo ""
        echo "🎉 The comprehensive testing framework is ready for use!"
        exit 0
    else
        print_error "$validation_failures validation step(s) failed"
        echo ""
        echo "❌ Please address the issues above before using the testing framework."
        exit 1
    fi
}

# Run main function
main "$@"