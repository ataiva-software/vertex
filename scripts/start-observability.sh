#!/bin/bash

# Start Observability Stack for Eden DevOps Suite
# This script starts all the observability components using Docker Compose

set -e

# Print colored output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting Eden DevOps Suite Observability Stack...${NC}"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
  echo -e "${RED}Error: Docker is not running. Please start Docker and try again.${NC}"
  exit 1
fi

# Create necessary directories
echo -e "${YELLOW}Creating necessary directories...${NC}"
mkdir -p ./data/elasticsearch
mkdir -p ./data/grafana
mkdir -p ./data/prometheus
mkdir -p ./data/tempo
mkdir -p ./data/alertmanager

# Set permissions for Elasticsearch
echo -e "${YELLOW}Setting permissions for Elasticsearch data directory...${NC}"
chmod -R 777 ./data/elasticsearch

# Start the observability stack
echo -e "${YELLOW}Starting observability stack...${NC}"
docker-compose -f docker-compose.observability.yml up -d

# Wait for services to be ready
echo -e "${YELLOW}Waiting for services to be ready...${NC}"
sleep 10

# Check if services are running
echo -e "${YELLOW}Checking if services are running...${NC}"
SERVICES=(
  "otel-collector"
  "prometheus"
  "grafana"
  "elasticsearch"
  "kibana"
  "jaeger"
  "tempo"
  "alertmanager"
)

ALL_RUNNING=true
for SERVICE in "${SERVICES[@]}"; do
  if docker-compose -f docker-compose.observability.yml ps | grep $SERVICE | grep -q "Up"; then
    echo -e "${GREEN}✓ $SERVICE is running${NC}"
  else
    echo -e "${RED}✗ $SERVICE is not running${NC}"
    ALL_RUNNING=false
  fi
done

if [ "$ALL_RUNNING" = true ]; then
  echo -e "${GREEN}All observability services are running!${NC}"
  echo -e "${YELLOW}Access points:${NC}"
  echo -e "  - Grafana: http://localhost:3000 (admin/admin)"
  echo -e "  - Prometheus: http://localhost:9090"
  echo -e "  - Jaeger: http://localhost:16686"
  echo -e "  - Kibana: http://localhost:5601"
  echo -e "  - Elasticsearch: http://localhost:9200"
  echo -e "  - AlertManager: http://localhost:9093"
else
  echo -e "${RED}Some services failed to start. Check the logs with:${NC}"
  echo -e "  docker-compose -f docker-compose.observability.yml logs"
fi

echo -e "${GREEN}Done!${NC}"