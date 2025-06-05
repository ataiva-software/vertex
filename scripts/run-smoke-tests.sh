#!/bin/bash

# Smoke Test Script for Eden DevOps Suite
# This script performs basic health checks on all services after deployment
# to ensure the system is functioning correctly.

set -e  # Exit immediately if a command exits with a non-zero status

echo "🔍 Running Eden DevOps Suite Smoke Tests"
echo "========================================"
echo "Start Time: $(date)"
echo

# Configuration
TIMEOUT=5  # Timeout in seconds for curl requests
MAX_RETRIES=3  # Maximum number of retries for each service
RETRY_DELAY=5  # Delay between retries in seconds

# Service endpoints to check
declare -A SERVICES=(
  ["API Gateway"]="http://localhost:8000/health"
  ["Vault Service"]="http://localhost:8081/health"
  ["Flow Service"]="http://localhost:8082/health"
  ["Task Service"]="http://localhost:8083/health"
  ["Hub Service"]="http://localhost:8080/health"
  ["Sync Service"]="http://localhost:8084/health"
  ["Insight Service"]="http://localhost:8085/health"
)

# Function to check a service with retries
check_service() {
  local service_name=$1
  local endpoint=$2
  local attempt=1
  
  echo -n "Checking $service_name... "
  
  while [ $attempt -le $MAX_RETRIES ]; do
    response=$(curl -s -o /dev/null -w "%{http_code}" --max-time $TIMEOUT $endpoint 2>/dev/null || echo "000")
    
    if [ "$response" = "200" ]; then
      echo "✅ OK"
      return 0
    else
      if [ $attempt -lt $MAX_RETRIES ]; then
        echo -n "Retry $attempt/$MAX_RETRIES... "
        sleep $RETRY_DELAY
      fi
      attempt=$((attempt + 1))
    fi
  done
  
  echo "❌ FAILED (HTTP $response)"
  return 1
}

# Function to check database connectivity
check_database() {
  echo -n "Checking Database Connectivity... "
  
  response=$(curl -s -o /dev/null -w "%{http_code}" --max-time $TIMEOUT "http://localhost:8081/api/v1/health/database" 2>/dev/null || echo "000")
  
  if [ "$response" = "200" ]; then
    echo "✅ OK"
    return 0
  else
    echo "❌ FAILED (HTTP $response)"
    return 1
  fi
}

# Function to check service-to-service communication
check_service_integration() {
  echo -n "Checking Service Integration... "
  
  response=$(curl -s -o /dev/null -w "%{http_code}" --max-time $TIMEOUT "http://localhost:8000/services" 2>/dev/null || echo "000")
  
  if [ "$response" = "200" ]; then
    echo "✅ OK"
    return 0
  else
    echo "❌ FAILED (HTTP $response)"
    return 1
  fi
}

# Main execution
echo "1. Checking Individual Services"
echo "------------------------------"
failed_services=0

for service in "${!SERVICES[@]}"; do
  if ! check_service "$service" "${SERVICES[$service]}"; then
    failed_services=$((failed_services + 1))
  fi
done

echo
echo "2. Checking Database Connectivity"
echo "--------------------------------"
if ! check_database; then
  failed_services=$((failed_services + 1))
fi

echo
echo "3. Checking Service Integration"
echo "------------------------------"
if ! check_service_integration; then
  failed_services=$((failed_services + 1))
fi

echo
echo "========================================"
echo "Smoke Test Results:"
if [ $failed_services -eq 0 ]; then
  echo "✅ All tests passed! The system is healthy."
  exit 0
else
  echo "❌ $failed_services tests failed. The system may not be functioning correctly."
  exit 1
fi