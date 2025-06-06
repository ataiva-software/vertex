# Multi-Region Deployment and Operations Guide

## Table of Contents
1. [Introduction](#introduction)
2. [Architecture Overview](#architecture-overview)
3. [Prerequisites](#prerequisites)
4. [Deployment](#deployment)
5. [Configuration](#configuration)
6. [Operations](#operations)
7. [Monitoring](#monitoring)
8. [Troubleshooting](#troubleshooting)
9. [Best Practices](#best-practices)
10. [References](#references)

## Introduction

This guide provides comprehensive instructions for deploying and operating the Eden DevOps Suite in a multi-region configuration. The multi-region deployment ensures high availability, disaster recovery capabilities, and optimal performance for users across different geographic locations.

### Purpose

The purpose of this document is to:
- Provide step-by-step instructions for deploying Eden DevOps Suite across multiple regions
- Document configuration requirements for multi-region operation
- Outline operational procedures for maintaining a multi-region deployment
- Establish best practices for monitoring and troubleshooting

### Scope

This document covers the deployment and operation of Eden DevOps Suite across AWS regions (primarily us-east-1 and us-west-2). It includes:

- Infrastructure setup in multiple regions
- Database replication configuration
- Cache replication configuration
- Network and load balancing setup
- Service deployment and configuration
- Monitoring and alerting setup
- Operational procedures
- Troubleshooting guidelines

## Architecture Overview

The Eden DevOps Suite multi-region architecture is designed for active-active operation, where both regions serve traffic simultaneously. This approach provides several benefits:

1. **Improved Availability**: The system continues to operate even if one region experiences an outage
2. **Reduced Latency**: Users are directed to the closest region
3. **Increased Capacity**: Total system capacity is the sum of both regions
4. **Seamless Failover**: Traffic can be shifted between regions with minimal disruption

### High-Level Architecture Diagram

```
                                  ┌─────────────────┐
                                  │   Global DNS    │
                                  │  (Route 53)     │
                                  └────────┬────────┘
                                           │
                 ┌─────────────────────────┴─────────────────────────┐
                 │                                                   │
        ┌────────▼───────┐                               ┌───────────▼────────┐
        │  Region A      │                               │  Region B          │
        │  (us-east-1)   │                               │  (us-west-2)       │
        └────────┬───────┘                               └───────────┬────────┘
                 │                                                   │
        ┌────────▼───────┐                               ┌───────────▼────────┐
        │  Load Balancer │                               │  Load Balancer     │
        │  (ALB)         │                               │  (ALB)             │
        └────────┬───────┘                               └───────────┬────────┘
                 │                                                   │
        ┌────────▼───────┐                               ┌───────────▼────────┐
        │  API Gateway   │                               │  API Gateway       │
        │                │                               │                    │
        └────────┬───────┘                               └───────────┬────────┘
                 │                                                   │
        ┌────────▼───────┐                               ┌───────────▼────────┐
        │  Services      │◄──────┐              ┌───────►│  Services          │
        │                │       │              │        │                    │
        └────────┬───────┘       │              │        └───────────┬────────┘
                 │               │              │                    │
        ┌────────▼───────┐       │              │        ┌───────────▼────────┐
        │  Database      │◄──────┼──────────────┼───────►│  Database          │
        │  (PostgreSQL)  │       │              │        │  (PostgreSQL)      │
        └────────┬───────┘       │              │        └───────────┬────────┘
                 │               │              │                    │
        ┌────────▼───────┐       │              │        ┌───────────▼────────┐
        │  Cache         │◄──────┴──────────────┴───────►│  Cache             │
        │  (Redis)       │                               │  (Redis)           │
        └────────────────┘                               └────────────────────┘
```

### Key Components

1. **Global DNS and Load Balancing**:
   - AWS Route 53 with latency-based routing
   - AWS Global Accelerator for edge-optimized entry points
   - Health checks for automatic failover

2. **Data Layer**:
   - PostgreSQL with bi-directional logical replication
   - Redis with active-active CRDT replication
   - Conflict resolution mechanisms

3. **Application Layer**:
   - Kubernetes clusters in each region
   - Region-aware service configuration
   - Service mesh for cross-region communication

4. **Monitoring and Alerting**:
   - Cross-region metrics aggregation
   - Region-aware alerting
   - Automated failover system

## Prerequisites

Before deploying Eden DevOps Suite in a multi-region configuration, ensure the following prerequisites are met:

### AWS Account and Permissions

- AWS account with administrative access
- IAM roles and policies for cross-region operations
- Service quotas increased for multi-region deployment

### Infrastructure

- VPC in each target region with appropriate subnets
- VPC peering or Transit Gateway for cross-region connectivity
- S3 buckets for backups and artifacts in each region
- KMS keys for encryption in each region

### Tools and Software

- AWS CLI configured with appropriate credentials
- kubectl configured for multiple contexts
- Helm 3.x or later
- Terraform 1.0.x or later
- jq for JSON processing
- yq for YAML processing

### Domain and Certificates

- Registered domain for the application
- ACM certificates for each region
- Route 53 hosted zone for the domain

## Deployment

The deployment process follows these high-level steps:

1. Infrastructure provisioning in each region
2. Database deployment and replication setup
3. Cache deployment and replication setup
4. Service deployment in each region
5. Global load balancing configuration
6. Monitoring and alerting setup

### Step 1: Infrastructure Provisioning

Use Terraform to provision the infrastructure in each region:

```bash
# Clone the repository
git clone https://github.com/ataivadev/eden.git
cd eden

# Deploy infrastructure in primary region
cd infrastructure/terraform
terraform init
terraform workspace new us-east-1
terraform apply -var-file=environments/us-east-1.tfvars

# Deploy infrastructure in secondary region
terraform workspace new us-west-2
terraform apply -var-file=environments/us-west-2.tfvars
```

### Step 2: Database Deployment and Replication

Deploy PostgreSQL and configure bi-directional replication:

```bash
# Deploy PostgreSQL in primary region
kubectl config use-context eks-us-east-1
kubectl apply -f kubernetes/base/postgres.yaml

# Deploy PostgreSQL in secondary region
kubectl config use-context eks-us-west-2
kubectl apply -f kubernetes/base/postgres.yaml

# Set up bi-directional replication
./infrastructure/database/scripts/setup-multi-region-replication.sh
```

### Step 3: Cache Deployment and Replication

Deploy Redis and configure active-active replication:

```bash
# Deploy Redis in primary region
kubectl config use-context eks-us-east-1
kubectl apply -f kubernetes/base/redis.yaml

# Deploy Redis in secondary region
kubectl config use-context eks-us-west-2
kubectl apply -f kubernetes/base/redis.yaml

# Set up active-active replication
./infrastructure/cache/scripts/setup-multi-region-redis.sh
```

### Step 4: Service Deployment

Deploy services in each region:

```bash
# Deploy services in primary region
kubectl config use-context eks-us-east-1
kubectl apply -f kubernetes/base/

# Deploy services in secondary region
kubectl config use-context eks-us-west-2
kubectl apply -f kubernetes/base/
```

### Step 5: Global Load Balancing

Configure global load balancing:

```bash
# Set up Route 53 latency-based routing
./infrastructure/network/scripts/setup-global-load-balancing.sh

# Set up AWS Global Accelerator
./infrastructure/network/scripts/setup-latency-based-routing.sh
```

### Step 6: Monitoring and Alerting

Set up monitoring and alerting:

```bash
# Set up cross-region monitoring
./infrastructure/monitoring/scripts/setup-cross-region-monitoring.sh

# Set up automated failover
./infrastructure/failover/scripts/setup-automated-failover.sh
```

## Configuration

### Region-Specific Configuration

Each region requires specific configuration to operate correctly in a multi-region setup:

#### Database Configuration

The PostgreSQL configuration for multi-region operation:

```yaml
# postgresql-multi-region.yaml
global:
  replication_user: replication_user
  max_replication_lag_seconds: 300
  monitoring_interval_seconds: 60
  conflict_resolution: last_write_wins

regions:
  - name: us-east-1
    role: active
    instance:
      host: postgres.us-east-1.eden.internal
      port: 5432
      parameters:
        wal_level: logical
        max_replication_slots: 20
        max_wal_senders: 20
        track_commit_timestamp: on
  
  - name: us-west-2
    role: active
    instance:
      host: postgres.us-west-2.eden.internal
      port: 5432
      parameters:
        wal_level: logical
        max_replication_slots: 20
        max_wal_senders: 20
        track_commit_timestamp: on

replication:
  provider: pglogical
  node_groups:
    - name: eden_vault_group
      database: eden_vault
      conflict_resolution_tables:
        - table: vault_secrets
          resolution: last_write_wins
          tracking_column: last_updated_at
    # Additional node groups...
```

#### Cache Configuration

The Redis configuration for multi-region operation:

```yaml
# redis-multi-region.yaml
global:
  replication_type: active-active
  technology: redis-enterprise
  crdt_enabled: true
  sync_interval_ms: 100

regions:
  - name: us-east-1
    role: active
    instance:
      host: redis.us-east-1.eden.internal
      port: 6379
  
  - name: us-west-2
    role: active
    instance:
      host: redis.us-west-2.eden.internal
      port: 6379

databases:
  - name: eden_cache
    memory_size_gb: 4
    replication_enabled: true
    shards: 3
    regions:
      - us-east-1
      - us-west-2
    crdt_options:
      causal_consistency: true
      oss_cluster_api_compatible: true
      source_ttl: true
  # Additional databases...
```

#### Service Configuration

Services need to be configured for multi-region awareness:

```yaml
# service-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: eden-service-config
  namespace: eden
data:
  application.properties: |
    # Region-aware configuration
    eden.region=${EDEN_REGION}
    eden.multi-region.enabled=true
    eden.multi-region.regions=us-east-1,us-west-2
    
    # Database configuration
    spring.datasource.url=jdbc:postgresql://postgres.${EDEN_REGION}.eden.internal:5432/eden_db
    
    # Cache configuration
    spring.redis.host=redis.${EDEN_REGION}.eden.internal
    spring.redis.port=6379
    
    # Cross-region service discovery
    eden.service-discovery.cross-region.enabled=true
    eden.service-discovery.domain=eden.svc.cluster.local
```

### Global Load Balancing Configuration

Configure global load balancing for optimal traffic distribution:

```yaml
# global-load-balancing.yaml
global:
  provider: aws-route53
  domain: eden.example.com
  ttl_seconds: 60

regions:
  - name: us-east-1
    weight: 100
    endpoints:
      api:
        hostname: api.us-east-1.eden.example.com
        health_check_path: /health
  
  - name: us-west-2
    weight: 100
    endpoints:
      api:
        hostname: api.us-west-2.eden.example.com
        health_check_path: /health

dns_records:
  - name: api.eden.example.com
    type: global
    routing_policy: latency
    endpoints:
      - region: us-east-1
        target: api
      - region: us-west-2
        target: api
```

### Monitoring Configuration

Configure cross-region monitoring:

```yaml
# cross-region-monitoring.yaml
global:
  provider: prometheus-grafana
  scrape_interval: 15s
  evaluation_interval: 15s

regions:
  - name: us-east-1
    prometheus_url: http://prometheus.monitoring.svc.cluster.local:9090
  
  - name: us-west-2
    prometheus_url: http://prometheus.monitoring.svc.cluster.local:9090

alert_rules:
  cross_region:
    - name: CrossRegionAvailability
      rules:
        - alert: CrossRegionServiceDown
          expr: sum by(service) (up{job="eden-services"}) < 2
          for: 5m
          labels:
            severity: critical
```

## Operations

### Routine Operations

#### Health Checks

Regularly check the health of the multi-region deployment:

```bash
# Check overall health
./infrastructure/scripts/check-multi-region-health.sh

# Check database replication status
./infrastructure/database/scripts/check-replication-status.sh

# Check Redis replication status
./infrastructure/cache/scripts/check-redis-replication.sh
```

#### Backup and Restore

Perform regular backups across regions:

```bash
# Backup PostgreSQL in all regions
./infrastructure/backup/scripts/postgres_backup.sh --all-regions

# Backup Redis in all regions
./infrastructure/backup/scripts/redis_backup.sh --all-regions

# Verify backups
./infrastructure/backup/scripts/verify_backups.sh
```

#### Configuration Updates

Apply configuration updates across regions:

```bash
# Update configuration in primary region
kubectl config use-context eks-us-east-1
kubectl apply -f kubernetes/base/configmap.yaml

# Update configuration in secondary region
kubectl config use-context eks-us-west-2
kubectl apply -f kubernetes/base/configmap.yaml
```

### Maintenance Procedures

#### Planned Failover

Perform a planned failover for maintenance:

```bash
# Initiate planned failover to secondary region
./infrastructure/failover/scripts/planned-failover.sh us-east-1 us-west-2

# Perform maintenance in primary region
# ...

# Failback to primary region
./infrastructure/failover/scripts/planned-failover.sh us-west-2 us-east-1
```

#### Database Maintenance

Perform database maintenance:

```bash
# Perform maintenance on primary region database
kubectl config use-context eks-us-east-1
kubectl exec -it deploy/postgres -c postgres -- psql -U postgres -c "VACUUM ANALYZE;"

# Perform maintenance on secondary region database
kubectl config use-context eks-us-west-2
kubectl exec -it deploy/postgres -c postgres -- psql -U postgres -c "VACUUM ANALYZE;"
```

#### Service Updates

Update services across regions:

```bash
# Update services in primary region
kubectl config use-context eks-us-east-1
kubectl set image deployment/api-gateway api-gateway=eden/api-gateway:v1.2.3

# Update services in secondary region
kubectl config use-context eks-us-west-2
kubectl set image deployment/api-gateway api-gateway=eden/api-gateway:v1.2.3
```

### Scaling Procedures

#### Horizontal Scaling

Scale services horizontally:

```bash
# Scale services in primary region
kubectl config use-context eks-us-east-1
kubectl scale deployment api-gateway --replicas=5

# Scale services in secondary region
kubectl config use-context eks-us-west-2
kubectl scale deployment api-gateway --replicas=5
```

#### Vertical Scaling

Scale services vertically:

```bash
# Update resource requests and limits in primary region
kubectl config use-context eks-us-east-1
kubectl set resources deployment api-gateway -c api-gateway --requests=cpu=500m,memory=1Gi --limits=cpu=1,memory=2Gi

# Update resource requests and limits in secondary region
kubectl config use-context eks-us-west-2
kubectl set resources deployment api-gateway -c api-gateway --requests=cpu=500m,memory=1Gi --limits=cpu=1,memory=2Gi
```

### Traffic Management

#### Traffic Shifting

Shift traffic between regions:

```bash
# Shift 80% traffic to us-east-1 and 20% to us-west-2
./infrastructure/network/scripts/update-route53-weights.sh us-east-1 80 us-west-2 20

# Shift 50% traffic to each region
./infrastructure/network/scripts/update-route53-weights.sh us-east-1 50 us-west-2 50
```

#### Regional Isolation

Isolate a region for testing or maintenance:

```bash
# Isolate us-east-1 (direct all traffic to us-west-2)
./infrastructure/network/scripts/update-route53-weights.sh us-west-2 100 us-east-1 0

# Restore normal traffic distribution
./infrastructure/network/scripts/update-route53-weights.sh us-east-1 50 us-west-2 50
```

## Monitoring

### Key Metrics

Monitor these key metrics for multi-region deployments:

#### Availability Metrics

- Service availability in each region
- Cross-region service availability
- Regional API gateway availability

#### Performance Metrics

- Request latency by region
- Database query performance by region
- Cache hit/miss rates by region

#### Replication Metrics

- PostgreSQL replication lag
- Redis replication lag
- Replication conflicts

#### Traffic Metrics

- Request distribution by region
- Error rates by region
- Regional capacity utilization

### Dashboards

The following dashboards are available for monitoring:

1. **Cross-Region Overview**: High-level view of all regions
2. **Regional Comparison**: Side-by-side comparison of regions
3. **Database Replication**: Replication status and performance
4. **Service Health**: Health metrics for all services across regions

### Alerts

Key alerts for multi-region deployments:

1. **CrossRegionServiceDown**: Service is down in at least one region
2. **AllRegionsServiceDown**: Service is down in all regions
3. **RegionDown**: An entire region appears to be down
4. **CrossRegionHighLatency**: High latency detected in a region
5. **CrossRegionLatencyImbalance**: Significant latency difference between regions
6. **CrossRegionHighErrorRate**: High error rate in a region
7. **PostgreSQLReplicationLag**: PostgreSQL replication lag exceeds threshold
8. **RedisReplicationLag**: Redis replication lag exceeds threshold

## Troubleshooting

### Common Issues and Solutions

#### Replication Lag

**Symptoms**:
- PostgreSQL replication lag alerts
- Data inconsistency between regions
- Slow write propagation

**Solutions**:
1. Check network connectivity between regions
2. Verify replication slot status:
   ```bash
   kubectl exec -it deploy/postgres -c postgres -- psql -U postgres -c "SELECT * FROM pg_replication_slots;"
   ```
3. Check for blocking queries:
   ```bash
   kubectl exec -it deploy/postgres -c postgres -- psql -U postgres -c "SELECT * FROM pg_stat_activity WHERE state = 'active';"
   ```
4. Increase replication resources if needed

#### Data Inconsistency

**Symptoms**:
- Different query results between regions
- Application errors related to data integrity
- Replication conflict alerts

**Solutions**:
1. Check replication conflict logs:
   ```bash
   kubectl exec -it deploy/postgres -c postgres -- psql -U postgres -c "SELECT * FROM replication_conflicts ORDER BY created_at DESC LIMIT 10;"
   ```
2. Run data consistency check:
   ```bash
   ./infrastructure/database/scripts/check-data-consistency.sh
   ```
3. Reconcile data if needed:
   ```bash
   ./infrastructure/database/scripts/reconcile-data.sh
   ```

#### Network Partition

**Symptoms**:
- Replication lag alerts
- Cross-region communication failures
- Split-brain scenarios

**Solutions**:
1. Check AWS Transit Gateway status
2. Verify VPC peering connections:
   ```bash
   aws ec2 describe-vpc-peering-connections --region us-east-1
   ```
3. Check security group rules:
   ```bash
   aws ec2 describe-security-groups --group-ids sg-12345678 --region us-east-1
   ```
4. Implement partition resolution procedure if needed

#### DNS and Routing Issues

**Symptoms**:
- Uneven traffic distribution
- Routing to unhealthy regions
- Latency issues

**Solutions**:
1. Check Route 53 health check status:
   ```bash
   aws route53 list-health-checks
   ```
2. Verify DNS record configuration:
   ```bash
   aws route53 list-resource-record-sets --hosted-zone-id Z1234567890
   ```
3. Check Global Accelerator endpoint status:
   ```bash
   aws globalaccelerator list-endpoints --endpoint-group-arn arn:aws:globalaccelerator::123456789012:accelerator/1234abcd-abcd-1234-abcd-1234abcdefgh/listener/abcdef1234/endpoint-group/ab12cd34
   ```

### Diagnostic Tools

#### Database Diagnostics

```bash
# Check PostgreSQL replication status
kubectl exec -it deploy/postgres -c postgres -- psql -U postgres -c "SELECT * FROM pg_stat_replication;"

# Check PostgreSQL replication lag
kubectl exec -it deploy/postgres -c postgres -- psql -U postgres -c "SELECT now() - pg_last_xact_replay_timestamp() AS replication_lag;"

# Check for replication conflicts
kubectl exec -it deploy/postgres -c postgres -- psql -U postgres -c "SELECT * FROM replication_conflicts ORDER BY created_at DESC LIMIT 10;"
```

#### Redis Diagnostics

```bash
# Check Redis replication status
kubectl exec -it statefulset/redis -c redis -- redis-cli info replication

# Check Redis CRDT metrics
kubectl exec -it statefulset/redis -c redis -- redis-cli info crdt

# Check Redis memory usage
kubectl exec -it statefulset/redis -c redis -- redis-cli info memory
```

#### Network Diagnostics

```bash
# Check connectivity between regions
kubectl run -it --rm --restart=Never network-test --image=busybox -- ping postgres.us-west-2.eden.internal

# Check DNS resolution
kubectl run -it --rm --restart=Never dns-test --image=busybox -- nslookup api-gateway.us-east-1.eden.internal

# Check service endpoints
kubectl get endpoints -n eden
```

## Best Practices

### Data Management

1. **Conflict Resolution Strategy**:
   - Define clear conflict resolution rules for each data type
   - Use timestamp-based resolution for most data
   - Implement custom resolution for critical business entities
   - Log and review conflicts regularly

2. **Data Partitioning**:
   - Partition data by region where possible
   - Use global tables for reference data
   - Consider regional sharding for high-volume data

3. **Consistency Management**:
   - Define consistency requirements for each data type
   - Use eventual consistency for most data
   - Implement strong consistency for critical transactions
   - Regularly verify data consistency between regions

### Performance Optimization

1. **Latency Reduction**:
   - Use read-local, write-global patterns
   - Cache frequently accessed data in each region
   - Implement request routing based on data locality
   - Use asynchronous processing for cross-region operations

2. **Resource Allocation**:
   - Allocate resources based on regional traffic patterns
   - Scale services independently in each region
   - Monitor resource utilization and adjust as needed
   - Implement auto-scaling based on regional metrics

3. **Network Optimization**:
   - Use AWS Global Accelerator for edge optimization
   - Implement connection pooling for cross-region communication
   - Use compression for cross-region data transfer
   - Monitor and optimize cross-region bandwidth usage

### Operational Excellence

1. **Deployment Strategy**:
   - Use blue-green deployments across regions
   - Implement canary releases in one region first
   - Automate deployment verification
   - Maintain consistent configurations across regions

2. **Monitoring and Alerting**:
   - Implement region-specific and cross-region monitoring
   - Set appropriate thresholds for multi-region metrics
   - Establish clear escalation paths for regional issues
   - Regularly review and refine alerting rules

3. **Disaster Recovery Testing**:
   - Regularly test failover procedures
   - Simulate regional outages
   - Validate data recovery procedures
   - Document and improve recovery processes

### Security

1. **Data Protection**:
   - Encrypt data at rest in all regions
   - Encrypt data in transit between regions
   - Implement region-specific access controls
   - Regularly audit security configurations

2. **Compliance**:
   - Understand regional compliance requirements
   - Implement data residency controls where needed
   - Document compliance measures for each region
   - Regularly audit compliance status

## References

### Internal Documentation

- [Multi-Region Architecture Design](docs/multi-region-architecture.md)
- [Database Replication Guide](infrastructure/database/README.md)
- [Disaster Recovery Plan](infrastructure/disaster-recovery/disaster_recovery_plan.md)
- [Regional Failover Runbook](infrastructure/disaster-recovery/runbooks/regional_failover_runbook.md)

### External Resources

- [AWS Multi-Region Application Architecture](https://aws.amazon.com/solutions/implementations/multi-region-application-architecture/)
- [PostgreSQL Logical Replication](https://www.postgresql.org/docs/current/logical-replication.html)
- [Redis Enterprise Active-Active](https://redis.com/redis-enterprise/technology/active-active-geo-distribution/)
- [Kubernetes Federation](https://kubernetes.io/docs/concepts/cluster-administration/federation/)
- [Istio Multi-Cluster Deployment](https://istio.io/latest/docs/setup/install/multicluster/)