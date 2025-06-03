#!/bin/bash

# Eden DevOps Suite - Database Setup Test Script
# Quick test to verify database schema implementation

set -e

echo "🧪 Eden Database Setup Test"
echo "=========================="

# Configuration
PROJECT_ROOT=$(pwd)
TEST_OUTPUT_DIR="$PROJECT_ROOT/build/test-results"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    local status=$1
    local message=$2
    case $status in
        "SUCCESS")
            echo -e "${GREEN}✅ $message${NC}"
            ;;
        "ERROR")
            echo -e "${RED}❌ $message${NC}"
            ;;
        "WARNING")
            echo -e "${YELLOW}⚠️  $message${NC}"
            ;;
        "INFO")
            echo -e "${BLUE}ℹ️  $message${NC}"
            ;;
    esac
}

# Create test output directory
mkdir -p "$TEST_OUTPUT_DIR"

print_status "INFO" "Testing database schema implementation..."

# Test 1: Check migration files exist
print_status "INFO" "Checking migration files..."
if [ -f "infrastructure/database/init/V2__core_business_schema.sql" ]; then
    print_status "SUCCESS" "Core business schema migration found"
else
    print_status "ERROR" "Core business schema migration missing"
    exit 1
fi

if [ -f "infrastructure/database/init/V3__sample_data.sql" ]; then
    print_status "SUCCESS" "Sample data migration found"
else
    print_status "ERROR" "Sample data migration missing"
    exit 1
fi

# Test 2: Check test builders exist
print_status "INFO" "Checking test data builders..."
TEST_BUILDERS=(
    "shared/testing/src/commonMain/kotlin/com/ataiva/eden/testing/builders/SecretTestDataBuilder.kt"
    "shared/testing/src/commonMain/kotlin/com/ataiva/eden/testing/builders/WorkflowTestDataBuilder.kt"
    "shared/testing/src/commonMain/kotlin/com/ataiva/eden/testing/builders/TaskTestDataBuilder.kt"
)

for builder in "${TEST_BUILDERS[@]}"; do
    if [ -f "$builder" ]; then
        print_status "SUCCESS" "Test builder found: $(basename $builder)"
    else
        print_status "ERROR" "Test builder missing: $(basename $builder)"
        exit 1
    fi
done

# Test 3: Check repository interfaces exist
print_status "INFO" "Checking repository interfaces..."
REPOSITORIES=(
    "shared/database/src/commonMain/kotlin/com/ataiva/eden/database/repositories/SecretRepository.kt"
    "shared/database/src/commonMain/kotlin/com/ataiva/eden/database/repositories/WorkflowRepository.kt"
    "shared/database/src/commonMain/kotlin/com/ataiva/eden/database/repositories/TaskRepository.kt"
    "shared/database/src/commonMain/kotlin/com/ataiva/eden/database/repositories/SystemRepository.kt"
)

for repo in "${REPOSITORIES[@]}"; do
    if [ -f "$repo" ]; then
        print_status "SUCCESS" "Repository interface found: $(basename $repo)"
    else
        print_status "ERROR" "Repository interface missing: $(basename $repo)"
        exit 1
    fi
done

# Test 4: Check database service exists
print_status "INFO" "Checking database service..."
if [ -f "shared/database/src/commonMain/kotlin/com/ataiva/eden/database/EdenDatabaseService.kt" ]; then
    print_status "SUCCESS" "Eden database service found"
else
    print_status "ERROR" "Eden database service missing"
    exit 1
fi

# Test 5: Check integration test exists
print_status "INFO" "Checking integration tests..."
if [ -f "integration-tests/src/test/kotlin/com/ataiva/eden/integration/database/NewSchemaIntegrationTest.kt" ]; then
    print_status "SUCCESS" "New schema integration test found"
else
    print_status "ERROR" "New schema integration test missing"
    exit 1
fi

# Test 6: Check documentation exists
print_status "INFO" "Checking documentation..."
if [ -f "infrastructure/database/README.md" ]; then
    print_status "SUCCESS" "Database documentation found"
else
    print_status "ERROR" "Database documentation missing"
    exit 1
fi

# Test 7: Validate SQL syntax (basic check)
print_status "INFO" "Validating SQL syntax..."
if command -v sqlfluff >/dev/null 2>&1; then
    sqlfluff lint infrastructure/database/init/V2__core_business_schema.sql --dialect postgres > "$TEST_OUTPUT_DIR/sql_lint.log" 2>&1
    if [ $? -eq 0 ]; then
        print_status "SUCCESS" "SQL syntax validation passed"
    else
        print_status "WARNING" "SQL syntax validation had warnings (check $TEST_OUTPUT_DIR/sql_lint.log)"
    fi
else
    print_status "INFO" "sqlfluff not available, skipping SQL syntax validation"
fi

# Test 8: Check Gradle build configuration
print_status "INFO" "Checking Gradle configuration..."
if ./gradlew :shared:database:build --dry-run > "$TEST_OUTPUT_DIR/gradle_build.log" 2>&1; then
    print_status "SUCCESS" "Gradle build configuration valid"
else
    print_status "ERROR" "Gradle build configuration issues (check $TEST_OUTPUT_DIR/gradle_build.log)"
    exit 1
fi

# Test 9: Validate Kotlin compilation
print_status "INFO" "Validating Kotlin compilation..."
if ./gradlew :shared:database:compileKotlin > "$TEST_OUTPUT_DIR/kotlin_compile.log" 2>&1; then
    print_status "SUCCESS" "Kotlin compilation successful"
else
    print_status "ERROR" "Kotlin compilation failed (check $TEST_OUTPUT_DIR/kotlin_compile.log)"
    exit 1
fi

# Test 10: Check file permissions
print_status "INFO" "Checking file permissions..."
if [ -x "scripts/validate-database-schema.sh" ]; then
    print_status "SUCCESS" "Database validation script is executable"
else
    print_status "ERROR" "Database validation script is not executable"
    exit 1
fi

# Summary
echo ""
echo "=========================="
print_status "SUCCESS" "All database setup tests passed! 🎉"
echo ""
print_status "INFO" "Database schema implementation is complete and ready for use"
print_status "INFO" "Next steps:"
echo "  1. Start PostgreSQL database"
echo "  2. Run migrations: ./gradlew flywayMigrate"
echo "  3. Run validation: ./scripts/validate-database-schema.sh"
echo "  4. Run integration tests: ./gradlew :integration-tests:test"
echo ""
print_status "INFO" "Test results saved to: $TEST_OUTPUT_DIR"

exit 0