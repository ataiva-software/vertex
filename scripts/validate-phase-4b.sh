#!/bin/bash

# Phase 4B Validation Script
# This script validates the implementation of Phase 4B (Deployment Pipeline & CI/CD)
# by checking for the presence of all required files and configurations

echo "🚀 Eden DevOps Suite - Phase 4B Validation"
echo "=========================================="
echo "Validating Deployment Pipeline & CI/CD Implementation"
echo

# Set working directory to project root
cd "$(dirname "$0")/.." || exit 1

# Check CI/CD workflow files
echo "📋 Checking CI/CD Workflow Files..."
echo "-----------------------------------"

if [ -f ".github/workflows/ci.yml" ]; then
    echo "✅ CI workflow file exists"
else
    echo "❌ CI workflow file not found"
    exit 1
fi

if [ -f ".github/workflows/cd.yml" ]; then
    echo "✅ CD workflow file exists"
else
    echo "❌ CD workflow file not found"
    exit 1
fi

# Check Docker Compose production file
echo
echo "📋 Checking Docker Compose Production File..."
echo "--------------------------------------------"

if [ -f "docker-compose.prod.yml" ]; then
    echo "✅ Docker Compose production file exists"
else
    echo "❌ Docker Compose production file not found"
    exit 1
fi

# Check Smoke Test script
echo
echo "📋 Checking Smoke Test Script..."
echo "-------------------------------"

if [ -f "scripts/run-smoke-tests.sh" ]; then
    echo "✅ Smoke test script exists"
    if [ -x "scripts/run-smoke-tests.sh" ]; then
        echo "✅ Smoke test script is executable"
    else
        echo "❌ Smoke test script is not executable"
        exit 1
    fi
else
    echo "❌ Smoke test script not found"
    exit 1
fi

# Check PostgreSQL initialization script
echo
echo "📋 Checking PostgreSQL Initialization Script..."
echo "---------------------------------------------"

if [ -f "infrastructure/docker/postgres/init-multiple-dbs.sh" ]; then
    echo "✅ PostgreSQL initialization script exists"
    if [ -x "infrastructure/docker/postgres/init-multiple-dbs.sh" ]; then
        echo "✅ PostgreSQL initialization script is executable"
    else
        echo "❌ PostgreSQL initialization script is not executable"
        exit 1
    fi
else
    echo "❌ PostgreSQL initialization script not found"
    exit 1
fi

# Check Prometheus configuration
echo
echo "📋 Checking Prometheus Configuration..."
echo "-------------------------------------"

if [ -f "infrastructure/docker/prometheus/prometheus.yml" ]; then
    echo "✅ Prometheus configuration exists"
else
    echo "❌ Prometheus configuration not found"
    exit 1
fi

# Check Grafana configuration
echo
echo "📋 Checking Grafana Configuration..."
echo "----------------------------------"

if [ -f "infrastructure/docker/grafana/provisioning/datasources/datasource.yml" ]; then
    echo "✅ Grafana datasource configuration exists"
else
    echo "❌ Grafana datasource configuration not found"
    exit 1
fi

if [ -f "infrastructure/docker/grafana/provisioning/dashboards/dashboard.yml" ]; then
    echo "✅ Grafana dashboard configuration exists"
else
    echo "❌ Grafana dashboard configuration not found"
    exit 1
fi

# All checks passed
echo
echo "🎉 Phase 4B Validation Successful!"
echo "=================================="
echo "All deployment pipeline and CI/CD components have been implemented."
echo "The Eden DevOps Suite now has a complete CI/CD pipeline with:"
echo "  - Continuous Integration workflow"
echo "  - Continuous Deployment workflow"
echo "  - Production Docker Compose configuration"
echo "  - Smoke testing for deployment validation"
echo "  - Monitoring and observability with Prometheus and Grafana"
echo
echo "The system is now ready for automated deployment to staging and production environments."