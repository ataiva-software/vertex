#!/bin/bash

# Eden Vault Service Test Script
# Comprehensive testing for the Eden Vault implementation

set -e

echo "🔐 Eden Vault Service - Comprehensive Test Suite"
echo "=============================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Function to run a test
run_test() {
    local test_name="$1"
    local test_command="$2"
    
    echo -e "\n${BLUE}Running: $test_name${NC}"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if eval "$test_command"; then
        echo -e "${GREEN}✅ PASSED: $test_name${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}❌ FAILED: $test_name${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
}

# Function to check if service is running
check_service() {
    local service_name="$1"
    local port="$2"
    
    if curl -s "http://localhost:$port/health" > /dev/null; then
        echo -e "${GREEN}✅ $service_name is running on port $port${NC}"
        return 0
    else
        echo -e "${RED}❌ $service_name is not running on port $port${NC}"
        return 1
    fi
}

# Function to test API endpoint
test_api_endpoint() {
    local method="$1"
    local endpoint="$2"
    local expected_status="$3"
    local data="$4"
    
    if [ -n "$data" ]; then
        response=$(curl -s -w "%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -d "$data" \
            "http://localhost:8080$endpoint")
    else
        response=$(curl -s -w "%{http_code}" -X "$method" \
            "http://localhost:8080$endpoint")
    fi
    
    status_code="${response: -3}"
    
    if [ "$status_code" = "$expected_status" ]; then
        return 0
    else
        echo "Expected status $expected_status, got $status_code"
        return 1
    fi
}

echo -e "\n${YELLOW}Phase 1: Build and Compilation Tests${NC}"
echo "===================================="

run_test "Gradle Build - Vault Service" \
    "./gradlew :services:vault:build --no-daemon"

run_test "Gradle Build - Database Module" \
    "./gradlew :shared:database:build --no-daemon"

run_test "Gradle Build - Integration Tests" \
    "./gradlew :integration-tests:build --no-daemon"

echo -e "\n${YELLOW}Phase 2: Unit Tests${NC}"
echo "=================="

run_test "Vault Service Unit Tests" \
    "./gradlew :services:vault:test --no-daemon"

run_test "Database Repository Tests" \
    "./gradlew :shared:database:test --no-daemon"

echo -e "\n${YELLOW}Phase 3: Integration Tests${NC}"
echo "========================="

run_test "Database Schema Integration Tests" \
    "./gradlew :integration-tests:test --tests '*DatabaseIntegrationTest' --no-daemon"

run_test "Vault Service Integration Tests" \
    "./gradlew :integration-tests:test --tests '*VaultServiceIntegrationTest' --no-daemon"

run_test "Complete Schema Integration Tests" \
    "./gradlew :integration-tests:test --tests '*CompleteSchemaIntegrationTest' --no-daemon"

echo -e "\n${YELLOW}Phase 4: Database Validation${NC}"
echo "============================"

run_test "Database Schema Validation" \
    "./scripts/validate-database-schema.sh"

run_test "Database Test Setup" \
    "./scripts/test-database-setup.sh"

echo -e "\n${YELLOW}Phase 5: Service Startup Tests${NC}"
echo "=============================="

# Start the vault service in background
echo "Starting Eden Vault Service..."
./gradlew :services:vault:run --no-daemon &
VAULT_PID=$!

# Wait for service to start
echo "Waiting for service to start..."
sleep 10

run_test "Vault Service Health Check" \
    "check_service 'Eden Vault Service' 8080"

echo -e "\n${YELLOW}Phase 6: API Endpoint Tests${NC}"
echo "=========================="

run_test "Service Info Endpoint" \
    "test_api_endpoint GET '/' 200"

run_test "Health Check Endpoint" \
    "test_api_endpoint GET '/health' 200"

run_test "Secrets List Endpoint (No Auth)" \
    "test_api_endpoint GET '/api/v1/secrets?userId=test-user' 400"

# Test secret creation
SECRET_DATA='{
    "name": "test-api-secret",
    "value": "test-secret-value",
    "type": "api-key",
    "description": "Test secret for API validation",
    "userId": "test-user-123",
    "userPassword": "test-password-123"
}'

run_test "Create Secret Endpoint" \
    "test_api_endpoint POST '/api/v1/secrets' 201 '$SECRET_DATA'"

run_test "List Secrets Endpoint" \
    "test_api_endpoint GET '/api/v1/secrets?userId=test-user-123' 200"

run_test "Get Secret Endpoint" \
    "test_api_endpoint GET '/api/v1/secrets/test-api-secret?userId=test-user-123&userPassword=test-password-123' 200"

# Test bulk operations
BULK_DATA='{
    "secrets": [
        {
            "name": "bulk-secret-1",
            "value": "bulk-value-1",
            "type": "bulk-test"
        },
        {
            "name": "bulk-secret-2", 
            "value": "bulk-value-2",
            "type": "bulk-test"
        }
    ],
    "userId": "test-user-123",
    "userPassword": "test-password-123"
}'

run_test "Bulk Secret Creation" \
    "test_api_endpoint POST '/api/v1/bulk/secrets' 200 '$BULK_DATA'"

# Test search
SEARCH_DATA='{
    "query": "bulk",
    "userId": "test-user-123",
    "type": "bulk-test",
    "limit": 10
}'

run_test "Secret Search Endpoint" \
    "test_api_endpoint POST '/api/v1/search/secrets' 200 '$SEARCH_DATA'"

run_test "Secret Statistics Endpoint" \
    "test_api_endpoint GET '/stats/secrets?userId=test-user-123' 200"

run_test "Access Logs Endpoint" \
    "test_api_endpoint GET '/logs/access?userId=test-user-123' 200"

echo -e "\n${YELLOW}Phase 7: Error Handling Tests${NC}"
echo "============================"

# Test error scenarios
INVALID_SECRET='{
    "name": "",
    "value": "test-value",
    "userId": "test-user",
    "userPassword": "test-password"
}'

run_test "Empty Secret Name Error" \
    "test_api_endpoint POST '/api/v1/secrets' 400 '$INVALID_SECRET'"

run_test "Non-existent Secret Error" \
    "test_api_endpoint GET '/api/v1/secrets/non-existent?userId=test-user&userPassword=test-password' 404"

run_test "Invalid Endpoint Error" \
    "test_api_endpoint GET '/api/v1/invalid-endpoint' 404"

echo -e "\n${YELLOW}Phase 8: Performance Tests${NC}"
echo "========================"

# Simple performance test - create multiple secrets
echo "Creating 10 secrets for performance testing..."
for i in {1..10}; do
    PERF_SECRET="{
        \"name\": \"perf-secret-$i\",
        \"value\": \"performance-test-value-$i\",
        \"type\": \"performance-test\",
        \"userId\": \"perf-user-123\",
        \"userPassword\": \"perf-password-123\"
    }"
    
    curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$PERF_SECRET" \
        "http://localhost:8080/api/v1/secrets" > /dev/null
done

run_test "Performance Test - List 10+ Secrets" \
    "test_api_endpoint GET '/api/v1/secrets?userId=perf-user-123' 200"

# Cleanup - stop the service
echo -e "\n${YELLOW}Cleanup${NC}"
echo "======="

if [ -n "$VAULT_PID" ]; then
    echo "Stopping Eden Vault Service (PID: $VAULT_PID)..."
    kill $VAULT_PID 2>/dev/null || true
    wait $VAULT_PID 2>/dev/null || true
fi

echo -e "\n${YELLOW}Test Results Summary${NC}"
echo "===================="
echo -e "Total Tests: ${BLUE}$TOTAL_TESTS${NC}"
echo -e "Passed: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Failed: ${RED}$FAILED_TESTS${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "\n${GREEN}🎉 All tests passed! Eden Vault Service is working correctly.${NC}"
    exit 0
else
    echo -e "\n${RED}❌ Some tests failed. Please check the output above.${NC}"
    exit 1
fi