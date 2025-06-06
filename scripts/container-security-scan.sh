#!/bin/bash
set -e

# Container Security Scanning Script for Eden DevOps Suite
# Uses Trivy for vulnerability scanning and policy checking

# Configuration
IMAGE_NAME=${1:-"ataivadev/eden:latest"}
SEVERITY=${2:-"HIGH,CRITICAL"}
EXIT_ON_SEVERITY=${3:-"CRITICAL"}
TRIVY_CACHE_DIR="/tmp/trivy-cache"
REPORT_DIR="./security-reports"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Create report directory if it doesn't exist
mkdir -p ${REPORT_DIR}

echo "=== Eden DevOps Suite - Container Security Scanning ==="
echo "Image: ${IMAGE_NAME}"
echo "Scanning for severity levels: ${SEVERITY}"
echo "Will exit on severity: ${EXIT_ON_SEVERITY}"
echo "Report will be saved to: ${REPORT_DIR}"
echo ""

# Check if Trivy is installed
if ! command -v trivy &> /dev/null; then
    echo "Trivy is not installed. Installing..."
    
    # Install Trivy based on OS
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        brew install aquasecurity/trivy/trivy
    else
        echo "Unsupported OS for automatic installation. Please install Trivy manually."
        exit 1
    fi
fi

echo "=== Pulling latest image ==="
docker pull ${IMAGE_NAME}

echo "=== Scanning for vulnerabilities ==="
# Scan for vulnerabilities and generate JSON report
trivy image \
    --cache-dir ${TRIVY_CACHE_DIR} \
    --severity ${SEVERITY} \
    --format json \
    --output ${REPORT_DIR}/vulnerability-scan-${TIMESTAMP}.json \
    ${IMAGE_NAME}

# Generate HTML report for better readability
trivy image \
    --cache-dir ${TRIVY_CACHE_DIR} \
    --severity ${SEVERITY} \
    --format template \
    --template "@/contrib/html.tpl" \
    --output ${REPORT_DIR}/vulnerability-scan-${TIMESTAMP}.html \
    ${IMAGE_NAME}

echo "=== Scanning for misconfigurations ==="
# Scan for misconfigurations and generate JSON report
trivy config \
    --severity ${SEVERITY} \
    --format json \
    --output ${REPORT_DIR}/misconfig-scan-${TIMESTAMP}.json \
    ${IMAGE_NAME}

echo "=== Checking for secrets ==="
# Scan for secrets and generate JSON report
trivy image \
    --cache-dir ${TRIVY_CACHE_DIR} \
    --scanners secret \
    --format json \
    --output ${REPORT_DIR}/secrets-scan-${TIMESTAMP}.json \
    ${IMAGE_NAME}

echo "=== Checking for compliance issues ==="
# Scan for compliance issues (CIS Docker Benchmark)
trivy image \
    --cache-dir ${TRIVY_CACHE_DIR} \
    --compliance=cis-docker \
    --format json \
    --output ${REPORT_DIR}/compliance-scan-${TIMESTAMP}.json \
    ${IMAGE_NAME}

echo "=== Generating summary report ==="
# Generate a summary report
echo "# Security Scan Summary for ${IMAGE_NAME}" > ${REPORT_DIR}/summary-${TIMESTAMP}.md
echo "Scan date: $(date)" >> ${REPORT_DIR}/summary-${TIMESTAMP}.md
echo "" >> ${REPORT_DIR}/summary-${TIMESTAMP}.md

# Count vulnerabilities by severity
echo "## Vulnerability Summary" >> ${REPORT_DIR}/summary-${TIMESTAMP}.md
CRITICAL_COUNT=$(cat ${REPORT_DIR}/vulnerability-scan-${TIMESTAMP}.json | grep -c '"Severity":"CRITICAL"' || echo 0)
HIGH_COUNT=$(cat ${REPORT_DIR}/vulnerability-scan-${TIMESTAMP}.json | grep -c '"Severity":"HIGH"' || echo 0)
MEDIUM_COUNT=$(cat ${REPORT_DIR}/vulnerability-scan-${TIMESTAMP}.json | grep -c '"Severity":"MEDIUM"' || echo 0)
LOW_COUNT=$(cat ${REPORT_DIR}/vulnerability-scan-${TIMESTAMP}.json | grep -c '"Severity":"LOW"' || echo 0)

echo "- Critical: ${CRITICAL_COUNT}" >> ${REPORT_DIR}/summary-${TIMESTAMP}.md
echo "- High: ${HIGH_COUNT}" >> ${REPORT_DIR}/summary-${TIMESTAMP}.md
echo "- Medium: ${MEDIUM_COUNT}" >> ${REPORT_DIR}/summary-${TIMESTAMP}.md
echo "- Low: ${LOW_COUNT}" >> ${REPORT_DIR}/summary-${TIMESTAMP}.md
echo "" >> ${REPORT_DIR}/summary-${TIMESTAMP}.md

# Check if we should exit based on severity
if [[ ${CRITICAL_COUNT} -gt 0 && ${EXIT_ON_SEVERITY} == "CRITICAL" ]]; then
    echo "CRITICAL vulnerabilities found. Exiting with error."
    exit 1
fi

if [[ ${HIGH_COUNT} -gt 0 && ${EXIT_ON_SEVERITY} == "HIGH" ]]; then
    echo "HIGH vulnerabilities found. Exiting with error."
    exit 1
fi

echo "=== Security scan completed ==="
echo "Reports saved to ${REPORT_DIR}"
echo "Summary report: ${REPORT_DIR}/summary-${TIMESTAMP}.md"
echo "Vulnerability report: ${REPORT_DIR}/vulnerability-scan-${TIMESTAMP}.html"

# Make the reports accessible
chmod -R 755 ${REPORT_DIR}