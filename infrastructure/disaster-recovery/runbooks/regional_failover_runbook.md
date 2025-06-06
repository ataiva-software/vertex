# Regional Failover Runbook

## Overview

This runbook provides step-by-step procedures for performing a regional failover of the Eden DevOps Suite. It covers both planned failovers (for maintenance or testing) and emergency failovers (in response to a disaster).

## Prerequisites

- Access to both primary and secondary region infrastructure
- Administrator credentials for all systems
- Access to DNS management console
- Access to monitoring systems
- Communication channels for stakeholder notifications

## Failover Types

### Planned Failover

A planned failover is a controlled process performed during a maintenance window or for testing purposes. It allows for proper preparation, verification of replication status, and minimal service disruption.

### Emergency Failover

An emergency failover is performed in response to a disaster or major outage in the primary region. It prioritizes service restoration over perfect data synchronization and may result in some data loss within the defined RPO.

## Pre-Failover Checklist

Before initiating a failover, verify the following:

- [ ] Secondary region infrastructure is operational
- [ ] Database replication is current (check lag metrics)
- [ ] Configuration synchronization is up-to-date
- [ ] Secondary region has sufficient capacity to handle production load
- [ ] Monitoring systems are configured for the secondary region
- [ ] Team members are available for support during and after failover
- [ ] Stakeholders have been notified (for planned failovers)

## Planned Failover Procedure

### Phase 1: Preparation

1. **Schedule maintenance window**
   - Notify all stakeholders at least 48 hours in advance
   - Create a maintenance announcement in the status page
   - Schedule the team for the failover operation

2. **Verify replication status**

   ```bash
   # Check PostgreSQL replication lag
   ./infrastructure/backup/scripts/monitor_backups.sh --check-replication-lag
   
   # Verify Redis replication status
   redis-cli -h redis.secondary.eden.internal info replication
   ```

3. **Verify secondary region readiness**

   ```bash
   # Check secondary region infrastructure
   kubectl --context secondary get nodes
   kubectl --context secondary get pods -A
   
   # Verify secondary region capacity
   kubectl --context secondary describe nodes | grep -A 5 "Allocated resources"
   ```

4. **Prepare DNS changes**
   - Reduce TTL for DNS records to 60 seconds at least 24 hours before failover
   - Prepare DNS change commands or console access

### Phase 2: Pre-Switch Tasks

1. **Freeze configuration changes**
   - Implement change freeze for the duration of the failover
   - Notify development teams to pause deployments

2. **Perform final data synchronization**

   ```bash
   # Trigger final PostgreSQL backup and restore to secondary
   ./infrastructure/backup/scripts/postgres_backup.sh
   ./infrastructure/backup/scripts/postgres_restore.sh --host=postgres.secondary.eden.internal
   
   # Trigger final Redis backup and restore to secondary
   ./infrastructure/backup/scripts/redis_backup.sh
   ./infrastructure/backup/scripts/redis_restore.sh --host=redis.secondary.eden.internal
   
   # Sync configuration files
   ./infrastructure/backup/scripts/config_backup.sh
   ./infrastructure/backup/scripts/config_restore.sh --region=secondary
   ```

3. **Verify application readiness in secondary region**

   ```bash
   # Check service health endpoints
   for service in api-gateway vault-service hub-service flow-service task-service monitor-service sync-service insight-service; do
     curl -f https://$service.secondary.eden.internal/health || echo "$service health check failed"
   done
   ```

### Phase 3: Failover Execution

1. **Put primary region in read-only mode**

   ```bash
   # Set PostgreSQL to read-only
   PGPASSWORD=postgres psql -h postgres.primary.eden.internal -U postgres -c "ALTER SYSTEM SET default_transaction_read_only = on;"
   PGPASSWORD=postgres psql -h postgres.primary.eden.internal -U postgres -c "SELECT pg_reload_conf();"
   ```

2. **Scale down write-heavy services in primary region**

   ```bash
   kubectl --context primary scale deployment task-service --replicas=0 -n eden
   kubectl --context primary scale deployment flow-service --replicas=0 -n eden
   ```

3. **Execute DNS failover**

   ```bash
   # Update DNS records to point to secondary region
   ./infrastructure/disaster-recovery/scripts/update_dns.sh --region=secondary
   ```

4. **Scale up all services in secondary region**

   ```bash
   kubectl --context secondary scale deployment api-gateway --replicas=3 -n eden
   kubectl --context secondary scale deployment vault-service --replicas=3 -n eden
   kubectl --context secondary scale deployment hub-service --replicas=3 -n eden
   kubectl --context secondary scale deployment flow-service --replicas=3 -n eden
   kubectl --context secondary scale deployment task-service --replicas=3 -n eden
   kubectl --context secondary scale deployment monitor-service --replicas=2 -n eden
   kubectl --context secondary scale deployment sync-service --replicas=2 -n eden
   kubectl --context secondary scale deployment insight-service --replicas=2 -n eden
   ```

5. **Promote secondary databases to primary role**

   ```bash
   # Promote PostgreSQL replica to primary
   PGPASSWORD=postgres psql -h postgres.secondary.eden.internal -U postgres -c "SELECT pg_promote();"
   
   # Promote Redis replica to primary
   redis-cli -h redis-sentinel-1.eden.internal -p 26379 sentinel failover eden-master
   ```

### Phase 4: Post-Failover Verification

1. **Verify DNS propagation**

   ```bash
   dig api.eden.example.com +short
   dig vault.eden.example.com +short
   ```

2. **Verify service health in secondary region**

   ```bash
   # Check all service health endpoints
   for service in api-gateway vault-service hub-service flow-service task-service monitor-service sync-service insight-service; do
     curl -f https://$service.eden.example.com/health || echo "$service health check failed"
   done
   ```

3. **Verify database write operations**

   ```bash
   # Test PostgreSQL write operations
   PGPASSWORD=postgres psql -h postgres.secondary.eden.internal -U postgres -d eden_vault -c "CREATE TABLE failover_test (id serial, test_time timestamp default now()); INSERT INTO failover_test DEFAULT VALUES; SELECT * FROM failover_test; DROP TABLE failover_test;"
   
   # Test Redis write operations
   redis-cli -h redis.secondary.eden.internal SET failover_test "$(date)"
   redis-cli -h redis.secondary.eden.internal GET failover_test
   redis-cli -h redis.secondary.eden.internal DEL failover_test
   ```

4. **Verify application functionality**
   - Test critical user workflows
   - Check for error rates in monitoring
   - Verify data consistency

### Phase 5: Cleanup and Documentation

1. **Configure reverse replication**

   ```bash
   # Configure PostgreSQL replication from secondary (now primary) to primary (now secondary)
   ./infrastructure/disaster-recovery/scripts/configure_reverse_replication.sh
   ```

2. **Update documentation**
   - Update system documentation to reflect the new primary region
   - Document any issues encountered during failover
   - Update runbooks with lessons learned

3. **Notify stakeholders of completion**
   - Send email to all stakeholders
   - Update status page
   - Schedule post-mortem meeting (if needed)

## Emergency Failover Procedure

### Phase 1: Disaster Assessment

1. **Confirm primary region outage**
   - Verify alerts from monitoring systems
   - Attempt to access services in primary region
   - Check cloud provider status page

2. **Declare disaster situation**
   - Notify DR coordinator and team
   - Establish communication channel (e.g., dedicated Slack channel)
   - Begin incident documentation

3. **Assess replication status**
   - Check last successful replication timestamp
   - Estimate potential data loss (compare with RPO)

### Phase 2: Emergency Failover Execution

1. **Activate secondary region**

   ```bash
   # Execute emergency failover script
   ./infrastructure/disaster-recovery/scripts/emergency_failover.sh
   ```

   This script performs the following actions:
   - Promotes secondary databases to primary role
   - Scales up all services in secondary region
   - Updates DNS records
   - Configures monitoring for secondary region

2. **Verify service availability**

   ```bash
   # Check all service health endpoints
   for service in api-gateway vault-service hub-service flow-service task-service monitor-service sync-service insight-service; do
     curl -f https://$service.eden.example.com/health || echo "$service health check failed"
   done
   ```

### Phase 3: Post-Failover Actions

1. **Notify stakeholders**
   - Send emergency notification to all stakeholders
   - Update status page with outage information
   - Provide estimated time to full service restoration

2. **Assess data loss**
   - Compare last backup timestamps with outage time
   - Identify any transactions lost during failover
   - Prepare data reconciliation plan if needed

3. **Monitor system performance**
   - Watch for resource constraints in secondary region
   - Monitor error rates and response times
   - Be prepared to scale services as needed

4. **Begin recovery planning for primary region**
   - Assess damage to primary region
   - Develop plan for restoring primary region
   - Determine timeline for returning to primary region

## Failback Procedure (Returning to Primary Region)

### Phase 1: Primary Region Recovery

1. **Restore infrastructure in primary region**
   - Ensure all infrastructure components are operational
   - Verify network connectivity
   - Deploy all required services

2. **Configure replication from secondary to primary**

   ```bash
   # Configure PostgreSQL replication
   ./infrastructure/disaster-recovery/scripts/configure_replication.sh --source=secondary --target=primary
   
   # Configure Redis replication
   ./infrastructure/disaster-recovery/scripts/configure_redis_replication.sh --source=secondary --target=primary
   ```

3. **Wait for replication to catch up**
   - Monitor replication lag
   - Ensure all data is synchronized

### Phase 2: Failback Execution

1. **Schedule maintenance window for failback**
   - Notify all stakeholders
   - Create a maintenance announcement in the status page

2. **Follow the Planned Failover Procedure** with primary and secondary regions reversed

## Troubleshooting

### Common Issues and Solutions

#### Issue: DNS not propagating

**Solution:**
1. Verify DNS changes were applied correctly:
   ```bash
   dig api.eden.example.com
   ```

2. Check if DNS provider's API is functioning:
   ```bash
   curl -s https://api.dns-provider.com/v1/zones --header "Authorization: Bearer $TOKEN" | jq
   ```

3. Manually update DNS records through provider's console

#### Issue: Database replication lag too high

**Solution:**
1. Check replication status:
   ```bash
   PGPASSWORD=postgres psql -h postgres.secondary.eden.internal -U postgres -c "SELECT * FROM pg_stat_replication;"
   ```

2. Increase replication resources:
   ```bash
   kubectl --context secondary scale statefulset postgres --replicas=0 -n eden
   kubectl --context secondary edit statefulset postgres -n eden  # Increase CPU/memory
   kubectl --context secondary scale statefulset postgres --replicas=1 -n eden
   ```

#### Issue: Services not starting in secondary region

**Solution:**
1. Check pod status:
   ```bash
   kubectl --context secondary get pods -n eden
   kubectl --context secondary describe pod <pod-name> -n eden
   ```

2. Check logs:
   ```bash
   kubectl --context secondary logs <pod-name> -n eden
   ```

3. Verify configuration:
   ```bash
   kubectl --context secondary get configmaps -n eden
   kubectl --context secondary get secrets -n eden
   ```

## References

- [Eden DevOps Suite Disaster Recovery Plan](../disaster_recovery_plan.md)
- [Multi-Region Replication Configuration](../../backup/configs/multi_region_replication.yaml)
- [PostgreSQL Documentation on Replication](https://www.postgresql.org/docs/current/runtime-config-replication.html)
- [Redis Sentinel Documentation](https://redis.io/topics/sentinel)