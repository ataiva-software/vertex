#!/bin/bash

# Eden DevOps Suite - Coverage Reporting Script
# This script generates comprehensive coverage reports across all modules

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
OPEN_REPORT=false
VERIFY_THRESHOLD=true
GENERATE_XML=true
GENERATE_HTML=true
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

Generate comprehensive coverage reports for Eden DevOps Suite

OPTIONS:
    --open              Open HTML report in browser after generation
    --no-verify         Skip coverage threshold verification
    --no-xml            Skip XML report generation
    --no-html           Skip HTML report generation
    -v, --verbose       Enable verbose output
    -h, --help          Show this help message

EXAMPLES:
    $0                  # Generate all coverage reports
    $0 --open           # Generate reports and open in browser
    $0 --no-verify      # Generate reports without threshold check
    $0 -v               # Generate with verbose output

EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --open)
            OPEN_REPORT=true
            shift
            ;;
        --no-verify)
            VERIFY_THRESHOLD=false
            shift
            ;;
        --no-xml)
            GENERATE_XML=false
            shift
            ;;
        --no-html)
            GENERATE_HTML=false
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
run_gradle_task() {
    local task_name="$1"
    local gradle_task="$2"
    local description="$3"
    
    print_status "Running $task_name..."
    echo "Description: $description"
    echo "Command: $GRADLE_CMD $gradle_task $GRADLE_OPTS"
    echo ""
    
    if $GRADLE_CMD $gradle_task $GRADLE_OPTS; then
        print_success "$task_name completed successfully"
    else
        print_error "$task_name failed"
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
    
    print_success "Prerequisites check passed"
    echo ""
}

# Function to run tests for coverage
run_tests_for_coverage() {
    print_status "Running tests to collect coverage data..."
    
    # Run all tests to ensure coverage data is collected
    if ! run_gradle_task "Test Execution" "test" "Run all tests to collect coverage data"; then
        print_error "Tests failed. Coverage report may be incomplete."
        return 1
    fi
    
    return 0
}

# Function to generate coverage reports
generate_coverage_reports() {
    local reports_generated=0
    
    # Generate HTML report
    if [[ "$GENERATE_HTML" == "true" ]]; then
        if run_gradle_task "HTML Coverage Report" "koverHtmlReport" "Generate HTML coverage report"; then
            ((reports_generated++))
            print_status "HTML report location: build/reports/kover/html/index.html"
        fi
    fi
    
    # Generate XML report
    if [[ "$GENERATE_XML" == "true" ]]; then
        if run_gradle_task "XML Coverage Report" "koverXmlReport" "Generate XML coverage report for CI/CD"; then
            ((reports_generated++))
            print_status "XML report location: build/reports/kover/report.xml"
        fi
    fi
    
    if [[ $reports_generated -eq 0 ]]; then
        print_error "No coverage reports were generated successfully"
        return 1
    fi
    
    return 0
}

# Function to verify coverage threshold
verify_coverage_threshold() {
    if [[ "$VERIFY_THRESHOLD" == "false" ]]; then
        print_status "Skipping coverage threshold verification"
        return 0
    fi
    
    print_status "Verifying coverage threshold (100% required)..."
    
    if run_gradle_task "Coverage Verification" "koverVerify" "Verify 100% coverage threshold"; then
        print_success "Coverage threshold verification passed"
        return 0
    else
        print_error "Coverage threshold verification failed"
        print_error "Required: 100% coverage"
        return 1
    fi
}

# Function to extract coverage statistics
extract_coverage_stats() {
    local html_report="build/reports/kover/html/index.html"
    local xml_report="build/reports/kover/report.xml"
    
    print_status "Extracting coverage statistics..."
    
    # Try to extract from XML report first (more reliable)
    if [[ -f "$xml_report" && "$GENERATE_XML" == "true" ]]; then
        if command -v xmllint &> /dev/null; then
            local line_coverage=$(xmllint --xpath "string(//counter[@type='LINE']/@covered)" "$xml_report" 2>/dev/null || echo "N/A")
            local line_total=$(xmllint --xpath "string(//counter[@type='LINE']/@missed)" "$xml_report" 2>/dev/null || echo "N/A")
            local branch_coverage=$(xmllint --xpath "string(//counter[@type='BRANCH']/@covered)" "$xml_report" 2>/dev/null || echo "N/A")
            local branch_total=$(xmllint --xpath "string(//counter[@type='BRANCH']/@missed)" "$xml_report" 2>/dev/null || echo "N/A")
            
            if [[ "$line_coverage" != "N/A" && "$line_total" != "N/A" ]]; then
                local total_lines=$((line_coverage + line_total))
                if [[ $total_lines -gt 0 ]]; then
                    local line_percentage=$(( (line_coverage * 100) / total_lines ))
                    print_status "Line Coverage: $line_coverage/$total_lines ($line_percentage%)"
                else
                    print_status "Line Coverage: $line_coverage/$total_lines (0%)"
                fi
            fi
            
            if [[ "$branch_coverage" != "N/A" && "$branch_total" != "N/A" ]]; then
                local total_branches=$((branch_coverage + branch_total))
                if [[ $total_branches -gt 0 ]]; then
                    local branch_percentage=$(( (branch_coverage * 100) / total_branches ))
                    print_status "Branch Coverage: $branch_coverage/$total_branches ($branch_percentage%)"
                else
                    print_status "Branch Coverage: $branch_coverage/$total_branches (0%)"
                fi
            fi
        else
            print_warning "xmllint not available. Install libxml2-utils for detailed coverage stats."
        fi
    fi
    
    # Fallback to HTML parsing
    if [[ -f "$html_report" && "$GENERATE_HTML" == "true" ]]; then
        if command -v grep &> /dev/null; then
            local coverage_line=$(grep -o '[0-9]\+\.[0-9]\+%' "$html_report" | head -1 2>/dev/null || echo "")
            if [[ -n "$coverage_line" ]]; then
                print_status "Overall Coverage: $coverage_line"
            fi
        fi
    fi
}

# Function to open report in browser
open_report_in_browser() {
    if [[ "$OPEN_REPORT" == "false" ]]; then
        return 0
    fi
    
    local html_report="build/reports/kover/html/index.html"
    
    if [[ ! -f "$html_report" ]]; then
        print_warning "HTML report not found at $html_report"
        return 1
    fi
    
    print_status "Opening coverage report in browser..."
    
    # Detect OS and open accordingly
    case "$(uname -s)" in
        Darwin)
            open "$html_report"
            ;;
        Linux)
            if command -v xdg-open &> /dev/null; then
                xdg-open "$html_report"
            elif command -v firefox &> /dev/null; then
                firefox "$html_report" &
            elif command -v google-chrome &> /dev/null; then
                google-chrome "$html_report" &
            else
                print_warning "No suitable browser found to open report"
                return 1
            fi
            ;;
        CYGWIN*|MINGW32*|MSYS*|MINGW*)
            start "$html_report"
            ;;
        *)
            print_warning "Unsupported OS for opening browser"
            return 1
            ;;
    esac
    
    print_success "Coverage report opened in browser"
}

# Function to generate summary
generate_summary() {
    local start_time="$1"
    local end_time="$2"
    local duration=$((end_time - start_time))
    local minutes=$((duration / 60))
    local seconds=$((duration % 60))
    
    echo ""
    echo "=========================================="
    echo "         COVERAGE REPORT SUMMARY"
    echo "=========================================="
    echo "Total Duration: ${minutes}m ${seconds}s"
    echo ""
    
    if [[ "$GENERATE_HTML" == "true" ]]; then
        echo "✅ HTML Report: Generated"
        echo "   📄 Location: build/reports/kover/html/index.html"
    fi
    
    if [[ "$GENERATE_XML" == "true" ]]; then
        echo "✅ XML Report: Generated"
        echo "   📄 Location: build/reports/kover/report.xml"
    fi
    
    if [[ "$VERIFY_THRESHOLD" == "true" ]]; then
        echo "✅ Threshold Verification: Completed"
    fi
    
    echo ""
    echo "📊 Module Coverage Reports:"
    find . -name "index.html" -path "*/reports/kover/html/*" | while read -r report; do
        local module=$(echo "$report" | sed 's|./||' | sed 's|/build/reports/kover/html/index.html||')
        echo "   • $module: $report"
    done
    
    echo ""
    echo "🔗 Quick Access:"
    echo "   • View HTML Report: file://$(pwd)/build/reports/kover/html/index.html"
    if [[ "$GENERATE_XML" == "true" ]]; then
        echo "   • XML Report: $(pwd)/build/reports/kover/report.xml"
    fi
    echo "=========================================="
}

# Main execution
main() {
    local start_time=$(date +%s)
    
    print_status "Eden DevOps Suite - Coverage Report Generation"
    echo "Project Root: $PROJECT_ROOT"
    echo ""
    
    # Check prerequisites
    check_prerequisites
    
    # Track failures
    local failures=0
    
    # Run tests for coverage
    if ! run_tests_for_coverage; then
        ((failures++))
    fi
    
    # Generate coverage reports
    if ! generate_coverage_reports; then
        ((failures++))
    fi
    
    # Verify coverage threshold
    if ! verify_coverage_threshold; then
        ((failures++))
    fi
    
    # Extract coverage statistics
    extract_coverage_stats
    
    # Open report in browser
    open_report_in_browser
    
    # Generate summary
    local end_time=$(date +%s)
    generate_summary "$start_time" "$end_time"
    
    # Exit with appropriate code
    if [[ $failures -eq 0 ]]; then
        print_success "Coverage report generation completed successfully!"
        exit 0
    else
        print_error "Coverage report generation completed with $failures error(s)"
        exit 1
    fi
}

# Run main function
main "$@"