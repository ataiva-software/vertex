#!/bin/bash

# Script to run performance tests for the Eden DevOps Suite
# This script runs Gatling load tests against the Insight Service

# Default values
BASE_URL="http://localhost:8080"
DURATION=5
RAMP_UP=1
USERS=1000
CONSTANT_USERS=100
TEST_PROFILE="default"

# Display help
function show_help {
  echo "Usage: $0 [options]"
  echo ""
  echo "Options:"
  echo "  -u, --url URL           Base URL for the service (default: $BASE_URL)"
  echo "  -d, --duration MINUTES  Test duration in minutes (default: $DURATION)"
  echo "  -r, --ramp MINUTES      Ramp-up time in minutes (default: $RAMP_UP)"
  echo "  -n, --users COUNT       Maximum number of users (default: $USERS)"
  echo "  -c, --constant COUNT    Constant users after ramp-up (default: $CONSTANT_USERS)"
  echo "  -p, --profile PROFILE   Test profile: light, medium, heavy, extreme (default: $TEST_PROFILE)"
  echo "  -h, --help              Show this help message"
  echo ""
  echo "Examples:"
  echo "  $0 --profile light"
  echo "  $0 --url http://insight-service:8080 --duration 10 --users 2000"
  echo ""
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  key="$1"
  case $key in
    -u|--url)
      BASE_URL="$2"
      shift
      shift
      ;;
    -d|--duration)
      DURATION="$2"
      shift
      shift
      ;;
    -r|--ramp)
      RAMP_UP="$2"
      shift
      shift
      ;;
    -n|--users)
      USERS="$2"
      shift
      shift
      ;;
    -c|--constant)
      CONSTANT_USERS="$2"
      shift
      shift
      ;;
    -p|--profile)
      TEST_PROFILE="$2"
      shift
      shift
      ;;
    -h|--help)
      show_help
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      show_help
      exit 1
      ;;
  esac
done

# Set test parameters based on profile
case $TEST_PROFILE in
  light)
    USERS=100
    CONSTANT_USERS=10
    DURATION=2
    RAMP_UP=1
    ;;
  medium)
    USERS=500
    CONSTANT_USERS=50
    DURATION=5
    RAMP_UP=2
    ;;
  heavy)
    USERS=2000
    CONSTANT_USERS=200
    DURATION=10
    RAMP_UP=3
    ;;
  extreme)
    USERS=5000
    CONSTANT_USERS=500
    DURATION=15
    RAMP_UP=5
    ;;
esac

echo "Running performance tests with the following parameters:"
echo "Base URL: $BASE_URL"
echo "Duration: $DURATION minutes"
echo "Ramp-up time: $RAMP_UP minutes"
echo "Maximum users: $USERS"
echo "Constant users: $CONSTANT_USERS"
echo "Test profile: $TEST_PROFILE"
echo ""

# Check if the service is running
echo "Checking if the service is running..."
if curl -s -f "$BASE_URL/health" > /dev/null; then
  echo "Service is running."
else
  echo "Error: Service is not running at $BASE_URL"
  echo "Please start the service before running the tests."
  exit 1
fi

# Create results directory
RESULTS_DIR="performance-tests/results/$(date +%Y-%m-%d_%H-%M-%S)"
mkdir -p "$RESULTS_DIR"

# Run the tests
echo "Starting performance tests..."
cd "$(dirname "$0")/.." || exit 1

# Run Gatling tests
./gradlew :performance-tests:runCustomLoadTest \
  -PbaseUrl="$BASE_URL" \
  -Pduration="$DURATION" \
  -PrampUp="$RAMP_UP" \
  -Pusers="$USERS" \
  -PconstantUsers="$CONSTANT_USERS"

# Copy results to results directory
echo "Copying results to $RESULTS_DIR..."
cp -r performance-tests/build/reports/gatling/* "$RESULTS_DIR/"

echo ""
echo "Performance tests completed."
echo "Results are available in $RESULTS_DIR"
echo ""
echo "Summary:"
echo "--------"

# Extract summary from the results
SUMMARY_FILE=$(find "$RESULTS_DIR" -name "*.json" | head -n 1)
if [ -f "$SUMMARY_FILE" ]; then
  TOTAL_REQUESTS=$(grep -o '"numberOfRequests":[0-9]*' "$SUMMARY_FILE" | cut -d':' -f2)
  SUCCESS_RATE=$(grep -o '"percentiles95":[0-9.]*' "$SUMMARY_FILE" | cut -d':' -f2)
  MEAN_RESPONSE_TIME=$(grep -o '"meanResponseTime":[0-9.]*' "$SUMMARY_FILE" | cut -d':' -f2)
  
  echo "Total requests: $TOTAL_REQUESTS"
  echo "95th percentile response time: $SUCCESS_RATE ms"
  echo "Mean response time: $MEAN_RESPONSE_TIME ms"
else
  echo "No summary file found."
fi

echo ""
echo "To view detailed results, open the HTML report in a browser:"
echo "file://$(pwd)/$RESULTS_DIR/index.html"