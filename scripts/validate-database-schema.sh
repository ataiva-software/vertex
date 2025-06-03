#!/bin/bash

# Eden DevOps Suite - Database Schema Validation Script
# This script validates that the new database schema is properly implemented

set -e

echo "🗄️ Eden Database Schema Validation"
echo "=================================="

# Configuration
DB_HOST=${DATABASE_HOST:-localhost}
DB_PORT=${DATABASE_PORT:-5432}
DB_NAME=${DATABASE_NAME:-eden_dev}
DB_USER=${DATABASE_USERNAME:-eden}
DB_PASSWORD=${DATABASE_PASSWORD:-dev_password}

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

# Function to execute SQL and check result
execute_sql() {
    local sql=$1
    local expected_result=$2
    local description=$3
    
    result=$(PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c "$sql" 2>/dev/null | xargs)
    
    if [ "$result" = "$expected_result" ]; then
        print_status "SUCCESS" "$description"
        return 0
    else
        print_status "ERROR" "$description (Expected: $expected_result, Got: $result)"
        return 1
    fi
}

# Function to check if table exists
check_table_exists() {
    local table_name=$1
    local sql="SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '$table_name');"
    execute_sql "$sql" "t" "Table '$table_name' exists"
}

# Function to check if index exists
check_index_exists() {
    local index_name=$1
    local sql="SELECT EXISTS (SELECT FROM pg_indexes WHERE schemaname = 'public' AND indexname = '$index_name');"
    execute_sql "$sql" "t" "Index '$index_name' exists"
}

# Function to check column exists in table
check_column_exists() {
    local table_name=$1
    local column_name=$2
    local sql="SELECT EXISTS (SELECT FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '$table_name' AND column_name = '$column_name');"
    execute_sql "$sql" "t" "Column '$column_name' exists in table '$table_name'"
}

# Check database connectivity
print_status "INFO" "Checking database connectivity..."
if PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT 1;" > /dev/null 2>&1; then
    print_status "SUCCESS" "Database connection established"
else
    print_status "ERROR" "Cannot connect to database"
    exit 1
fi

echo ""
print_status "INFO" "Validating core tables..."

# Check core tables exist
CORE_TABLES=(
    "users"
    "user_sessions" 
    "organizations"
    "secrets"
    "secret_access_logs"
    "workflows"
    "workflow_executions"
    "workflow_steps"
    "tasks"
    "task_executions"
    "system_events"
    "audit_logs"
)

failed_checks=0

for table in "${CORE_TABLES[@]}"; do
    if ! check_table_exists "$table"; then
        ((failed_checks++))
    fi
done

echo ""
print_status "INFO" "Validating table structures..."

# Check critical columns exist
check_column_exists "users" "email" || ((failed_checks++))
check_column_exists "users" "password_hash" || ((failed_checks++))
check_column_exists "users" "is_active" || ((failed_checks++))
check_column_exists "users" "is_verified" || ((failed_checks++))

check_column_exists "secrets" "name" || ((failed_checks++))
check_column_exists "secrets" "encrypted_value" || ((failed_checks++))
check_column_exists "secrets" "encryption_key_id" || ((failed_checks++))
check_column_exists "secrets" "user_id" || ((failed_checks++))

check_column_exists "workflows" "definition" || ((failed_checks++))
check_column_exists "workflows" "status" || ((failed_checks++))

check_column_exists "tasks" "task_type" || ((failed_checks++))
check_column_exists "tasks" "configuration" || ((failed_checks++))

echo ""
print_status "INFO" "Validating indexes..."

# Check critical indexes exist
CRITICAL_INDEXES=(
    "idx_users_email"
    "idx_secrets_user_id"
    "idx_secrets_name"
    "idx_workflows_user_id"
    "idx_tasks_user_id"
    "idx_workflow_executions_workflow_id"
    "idx_task_executions_task_id"
    "idx_system_events_type"
    "idx_audit_logs_user_id"
)

for index in "${CRITICAL_INDEXES[@]}"; do
    if ! check_index_exists "$index"; then
        ((failed_checks++))
    fi
done

echo ""
print_status "INFO" "Validating foreign key constraints..."

# Check foreign key constraints
FK_CHECKS=(
    "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_type = 'FOREIGN KEY' AND table_name = 'secrets' AND constraint_name LIKE '%user_id%';"
    "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_type = 'FOREIGN KEY' AND table_name = 'workflows' AND constraint_name LIKE '%user_id%';"
    "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_type = 'FOREIGN KEY' AND table_name = 'tasks' AND constraint_name LIKE '%user_id%';"
)

for fk_check in "${FK_CHECKS[@]}"; do
    result=$(PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c "$fk_check" 2>/dev/null | xargs)
    if [ "$result" -gt "0" ]; then
        print_status "SUCCESS" "Foreign key constraint validated"
    else
        print_status "ERROR" "Missing foreign key constraint"
        ((failed_checks++))
    fi
done

echo ""
print_status "INFO" "Validating data types..."

# Check JSONB columns
JSONB_CHECKS=(
    "SELECT data_type FROM information_schema.columns WHERE table_name = 'secrets' AND column_name = 'metadata';"
    "SELECT data_type FROM information_schema.columns WHERE table_name = 'workflows' AND column_name = 'definition';"
    "SELECT data_type FROM information_schema.columns WHERE table_name = 'tasks' AND column_name = 'configuration';"
)

for jsonb_check in "${JSONB_CHECKS[@]}"; do
    result=$(PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c "$jsonb_check" 2>/dev/null | xargs)
    if [ "$result" = "jsonb" ]; then
        print_status "SUCCESS" "JSONB column type validated"
    else
        print_status "WARNING" "Expected JSONB column type, got: $result"
    fi
done

echo ""
print_status "INFO" "Testing basic CRUD operations..."

# Test basic insert/select operations
TEST_USER_ID="test-validation-$(date +%s)"
TEST_SECRET_ID="secret-validation-$(date +%s)"

# Insert test user
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "
INSERT INTO users (id, email, password_hash, full_name, is_active, is_verified) 
VALUES ('$TEST_USER_ID', 'validation@test.local', 'test-hash', 'Validation User', true, true);
" > /dev/null 2>&1

if [ $? -eq 0 ]; then
    print_status "SUCCESS" "User insert operation"
else
    print_status "ERROR" "User insert operation failed"
    ((failed_checks++))
fi

# Insert test secret
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "
INSERT INTO secrets (id, name, encrypted_value, encryption_key_id, user_id) 
VALUES ('$TEST_SECRET_ID', 'validation-secret', 'encrypted-test-value', 'test-key', '$TEST_USER_ID');
" > /dev/null 2>&1

if [ $? -eq 0 ]; then
    print_status "SUCCESS" "Secret insert operation"
else
    print_status "ERROR" "Secret insert operation failed"
    ((failed_checks++))
fi

# Test join query
join_result=$(PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c "
SELECT COUNT(*) FROM secrets s JOIN users u ON s.user_id = u.id WHERE s.id = '$TEST_SECRET_ID';
" 2>/dev/null | xargs)

if [ "$join_result" = "1" ]; then
    print_status "SUCCESS" "Join query operation"
else
    print_status "ERROR" "Join query operation failed"
    ((failed_checks++))
fi

# Cleanup test data
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "
DELETE FROM secrets WHERE id = '$TEST_SECRET_ID';
DELETE FROM users WHERE id = '$TEST_USER_ID';
" > /dev/null 2>&1

echo ""
print_status "INFO" "Validating sample data..."

# Check if sample data exists
sample_users=$(PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM users WHERE email LIKE '%@eden.local';" 2>/dev/null | xargs)
sample_secrets=$(PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM secrets WHERE id LIKE '1%';" 2>/dev/null | xargs)

if [ "$sample_users" -gt "0" ]; then
    print_status "SUCCESS" "Sample users found ($sample_users users)"
else
    print_status "WARNING" "No sample users found"
fi

if [ "$sample_secrets" -gt "0" ]; then
    print_status "SUCCESS" "Sample secrets found ($sample_secrets secrets)"
else
    print_status "WARNING" "No sample secrets found"
fi

echo ""
echo "=================================="
if [ $failed_checks -eq 0 ]; then
    print_status "SUCCESS" "All database schema validations passed! ✨"
    echo ""
    print_status "INFO" "Database is ready for Eden DevOps Suite Phase 1b implementation"
    exit 0
else
    print_status "ERROR" "$failed_checks validation(s) failed"
    echo ""
    print_status "ERROR" "Please review and fix the database schema issues before proceeding"
    exit 1
fi