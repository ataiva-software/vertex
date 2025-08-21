# Database Recovery Runbook

## Overview

This runbook provides step-by-step procedures for recovering PostgreSQL and Redis databases in the Vertex DevOps Suite. It covers various recovery scenarios including complete database recovery, point-in-time recovery, and handling of corrupted data.

## Prerequisites

- Access to backup storage location
- Database administrator crvertextials
- Access to the Vertex DevOps Suite infrastructure
- Encryption keys for backup decryption (if applicable)

## PostgreSQL Recovery Procedures

### Scenario 1: Complete Database Recovery

Use this procedure when a database is completely lost or corrupted and needs to be restored from the latest backup.

#### Steps:

1. **Verify the latest backup**

   ```bash
   # List available backups for the database
   find /var/backups/vertex/postgres -name "vertex_vault_*.sql.gz*" | sort -r | head -5
   ```

2. **Stop applications accessing the database**

   ```bash
   # Scale down applications to prevent data access during recovery
   kubectl scale deployment vault-service --replicas=0 -n vertex
   ```

3. **Restore the database**

   ```bash
   # For a specific database
   ./infrastructure/backup/scripts/postgres_restore.sh --db=vertex_vault
   
   # For all databases
   ./infrastructure/backup/scripts/postgres_restore.sh
   ```

4. **Verify database integrity**

   ```bash
   # Connect to the database and run integrity checks
   PGPASSWORD=postgres psql -h postgres -U postgres -d vertex_vault -c "SELECT count(*) FROM pg_catalog.pg_tables;"
   
   # Check for specific tables
   PGPASSWORD=postgres psql -h postgres -U postgres -d vertex_vault -c "SELECT count(*) FROM secrets;"
   ```

5. **Restart applications**

   ```bash
   # Scale up applications
   kubectl scale deployment vault-service --replicas=3 -n vertex
   ```

6. **Monitor application logs for errors**

   ```bash
   kubectl logs -f deployment/vault-service -n vertex
   ```

### Scenario 2: Point-in-Time Recovery

Use this procedure when you need to recover a database to a specific point in time, such as before a data corruption event or accidental deletion.

#### Steps:

1. **Identify the recovery timestamp**

   Determine the exact timestamp to which you want to recover. Format: `YYYY-MM-DD HH:MM:SS`

2. **Stop applications accessing the database**

   ```bash
   # Scale down applications to prevent data access during recovery
   kubectl scale deployment vault-service --replicas=0 -n vertex
   ```

3. **Perform point-in-time recovery**

   ```bash
   ./infrastructure/backup/scripts/postgres_restore.sh --db=vertex_vault --point-in-time="2025-06-05 14:30:00"
   ```

4. **Verify database state at the recovered point**

   ```bash
   # Connect to the database and verify data
   PGPASSWORD=postgres psql -h postgres -U postgres -d vertex_vault -c "SELECT created_at, count(*) FROM audit_log GROUP BY created_at ORDER BY created_at DESC LIMIT 10;"
   ```

5. **Restart applications**

   ```bash
   # Scale up applications
   kubectl scale deployment vault-service --replicas=3 -n vertex
   ```

6. **Monitor application logs for errors**

   ```bash
   kubectl logs -f deployment/vault-service -n vertex
   ```

### Scenario 3: Recovery to a Different Database

Use this procedure when you want to restore a database to a different name, such as for data verification or migration.

#### Steps:

1. **Restore to a different database name**

   ```bash
   ./infrastructure/backup/scripts/postgres_restore.sh --db=vertex_vault --to-db=vertex_vault_verify
   ```

2. **Verify the restored database**

   ```bash
   PGPASSWORD=postgres psql -h postgres -U postgres -d vertex_vault_verify -c "SELECT count(*) FROM secrets;"
   ```

3. **Compare with the original database (if available)**

   ```bash
   PGPASSWORD=postgres psql -h postgres -U postgres -c "
   SELECT 'original' as source, count(*) FROM vertex_vault.secrets
   UNION ALL
   SELECT 'restored' as source, count(*) FROM vertex_vault_verify.secrets;
   "
   ```

## Redis Recovery Procedures

### Scenario 1: RDB Snapshot Recovery

Use this procedure when Redis data is lost or corrupted and needs to be restored from the latest RDB snapshot.

#### Steps:

1. **Verify the latest RDB backup**

   ```bash
   # List available RDB backups
   find /var/backups/vertex/redis -name "redis_rdb_*.rdb.gz*" | sort -r | head -5
   ```

2. **Stop applications accessing Redis**

   ```bash
   # Scale down applications to prevent data access during recovery
   kubectl scale deployment api-gateway --replicas=0 -n vertex
   kubectl scale deployment task-service --replicas=0 -n vertex
   # Scale down other services as needed
   ```

3. **Restore Redis from RDB backup**

   ```bash
   ./infrastructure/backup/scripts/redis_restore.sh --type=rdb
   ```

4. **Verify Redis data**

   ```bash
   # Connect to Redis and check keys
   redis-cli -h redis info keyspace
   redis-cli -h redis dbsize
   ```

5. **Restart applications**

   ```bash
   # Scale up applications
   kubectl scale deployment api-gateway --replicas=3 -n vertex
   kubectl scale deployment task-service --replicas=3 -n vertex
   # Scale up other services as needed
   ```

6. **Monitor application logs for errors**

   ```bash
   kubectl logs -f deployment/api-gateway -n vertex
   ```

### Scenario 2: AOF Recovery

Use this procedure when you need to recover Redis data with the highest level of durability using AOF files.

#### Steps:

1. **Verify the latest AOF backup**

   ```bash
   # List available AOF backups
   find /var/backups/vertex/redis -name "redis_aof_*.aof.gz*" | sort -r | head -5
   ```

2. **Stop applications accessing Redis**

   ```bash
   # Scale down applications to prevent data access during recovery
   kubectl scale deployment api-gateway --replicas=0 -n vertex
   kubectl scale deployment task-service --replicas=0 -n vertex
   # Scale down other services as needed
   ```

3. **Restore Redis from AOF backup**

   ```bash
   ./infrastructure/backup/scripts/redis_restore.sh --type=aof
   ```

4. **Verify Redis data**

   ```bash
   # Connect to Redis and check keys
   redis-cli -h redis info keyspace
   redis-cli -h redis dbsize
   ```

5. **Restart applications**

   ```bash
   # Scale up applications
   kubectl scale deployment api-gateway --replicas=3 -n vertex
   kubectl scale deployment task-service --replicas=3 -n vertex
   # Scale up other services as needed
   ```

6. **Monitor application logs for errors**

   ```bash
   kubectl logs -f deployment/api-gateway -n vertex
   ```

## Troubleshooting

### Common Issues and Solutions

#### Issue: Backup file is corrupted

**Solution:**
1. Try an older backup file:
   ```bash
   ./infrastructure/backup/scripts/postgres_restore.sh --db=vertex_vault --backup-file=/var/backups/vertex/postgres/vertex_vault_20250605_010000.sql.gz
   ```

2. Verify backup integrity:
   ```bash
   ./infrastructure/backup/scripts/verify_backups.sh
   ```

#### Issue: Insufficient disk space for restore

**Solution:**
1. Check available disk space:
   ```bash
   df -h
   ```

2. Clean up temporary files:
   ```bash
   find /tmp -name "vertex_*" -type d -mtime +1 -exec rm -rf {} \;
   ```

3. Expand disk if necessary:
   ```bash
   # AWS example
   aws ec2 modify-volume --volume-id vol-1234567890abcdef0 --size 100
   ```

#### Issue: Database service won't start after restore

**Solution:**
1. Check database logs:
   ```bash
   docker logs vertex-postgres
   ```

2. Verify database configuration:
   ```bash
   docker exec vertex-postgres cat /var/lib/postgresql/data/postgresql.conf | grep listen
   ```

3. Check for permission issues:
   ```bash
   docker exec vertex-postgres ls -la /var/lib/postgresql/data
   ```

## Post-Recovery Tasks

1. **Verify application functionality**
   - Test critical workflows
   - Check for error logs
   - Verify data consistency

2. **Create new backups**
   ```bash
   ./infrastructure/backup/scripts/postgres_backup.sh
   ./infrastructure/backup/scripts/redis_backup.sh
   ```

3. **Document the recovery process**
   - Record the issue that caused the need for recovery
   - Document the steps taken
   - Note any deviations from the runbook
   - Suggest improvements to the recovery process

4. **Notify stakeholders of successful recovery**
   - Send email to the team
   - Update status page
   - Close any related incident tickets

## References

- [PostgreSQL Documentation on Backup and Restore](https://www.postgresql.org/docs/current/backup.html)
- [Redis Documentation on Persistence](https://redis.io/topics/persistence)
- [Vertex DevOps Suite Disaster Recovery Plan](../disaster_recovery_plan.md)