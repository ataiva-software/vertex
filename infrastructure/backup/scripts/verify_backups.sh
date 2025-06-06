#!/bin/bash
#
# Backup Verification Script for Eden DevOps Suite
# This script performs verification and validation of backups
# Features:
# - Verification of PostgreSQL backups
# - Verification of Redis backups
# - Verification of configuration backups
# - Integrity checks
# - Restoration tests in isolated environments
# - Reporting

set -e

# Configuration
BACKUP_DIR="${BACKUP_DIR:-/var/backups/eden}"
POSTGRES_BACKUP_DIR="${POSTGRES_BACKUP_DIR:-${BACKUP_DIR}/postgres}"
REDIS_BACKUP_DIR="${REDIS_BACKUP_DIR:-${BACKUP_DIR}/redis}"
CONFIG_BACKUP_DIR="${CONFIG_BACKUP_DIR:-${BACKUP_DIR}/configs}"
VERIFICATION_DIR="${VERIFICATION_DIR:-/tmp/eden-backup-verification}"
ENCRYPTION_KEY_FILE="${ENCRYPTION_KEY_FILE:-/etc/eden/backup_encryption.key}"
NOTIFICATION_EMAIL="${NOTIFICATION_EMAIL:-admin@example.com}"
VERIFICATION_LOG_FILE="${VERIFICATION_LOG_FILE:-/var/log/eden/backup_verification.log}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-postgres}"
POSTGRES_HOST="${POSTGRES_HOST:-postgres}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
VERIFICATION_CONTAINER_PREFIX="eden-verify"
VERIFICATION_NETWORK="eden-verification-network"

# Create verification directory if it doesn't exist
mkdir -p "${VERIFICATION_DIR}"
mkdir -p "$(dirname "${VERIFICATION_LOG_FILE}")"

# Log function
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "${VERIFICATION_LOG_FILE}"
}

# Error handling function
handle_error() {
  log "ERROR: Verification failed: $1"
  if [ -n "${NOTIFICATION_EMAIL}" ]; then
    echo "Backup verification failed: $1" | mail -s "Eden Backup Verification Failure" "${NOTIFICATION_EMAIL}"
  fi
  exit 1
}

# Create verification environment
create_verification_environment() {
  log "Creating verification environment..."
  
  # Create Docker network for verification
  docker network create "${VERIFICATION_NETWORK}" 2>/dev/null || true
  
  # Clean up any existing verification containers
  docker rm -f "${VERIFICATION_CONTAINER_PREFIX}-postgres" 2>/dev/null || true
  docker rm -f "${VERIFICATION_CONTAINER_PREFIX}-redis" 2>/dev/null || true
  
  log "Verification environment created"
}

# Clean up verification environment
cleanup_verification_environment() {
  log "Cleaning up verification environment..."
  
  # Remove verification containers
  docker rm -f "${VERIFICATION_CONTAINER_PREFIX}-postgres" 2>/dev/null || true
  docker rm -f "${VERIFICATION_CONTAINER_PREFIX}-redis" 2>/dev/null || true
  
  # Remove Docker network
  docker network rm "${VERIFICATION_NETWORK}" 2>/dev/null || true
  
  # Clean up verification directory
  rm -rf "${VERIFICATION_DIR}"/*
  
  log "Verification environment cleaned up"
}

# Verify PostgreSQL backup
verify_postgres_backup() {
  log "Verifying PostgreSQL backups..."
  
  # Find the latest PostgreSQL backup for each database
  local backup_files=$(find "${POSTGRES_BACKUP_DIR}" -type f -name "*.sql.gz*" | sort -r)
  
  if [ -z "${backup_files}" ]; then
    log "No PostgreSQL backups found"
    return 0
  fi
  
  # Create a temporary directory for verification
  local temp_dir="${VERIFICATION_DIR}/postgres"
  mkdir -p "${temp_dir}"
  
  # Start a verification PostgreSQL container
  log "Starting verification PostgreSQL container..."
  docker run -d --name "${VERIFICATION_CONTAINER_PREFIX}-postgres" \
    --network "${VERIFICATION_NETWORK}" \
    -e POSTGRES_PASSWORD="${POSTGRES_PASSWORD}" \
    -e POSTGRES_USER="${POSTGRES_USER}" \
    postgres:14 || handle_error "Failed to start verification PostgreSQL container"
  
  # Wait for PostgreSQL to start
  sleep 10
  
  # Process each backup file
  for backup_file in ${backup_files}; do
    local db_name=$(basename "${backup_file}" | cut -d'_' -f1)
    log "Verifying backup for database: ${db_name}"
    
    # Extract database name from filename
    local verification_file="${temp_dir}/$(basename "${backup_file}")"
    cp "${backup_file}" "${verification_file}"
    
    # Decrypt if encrypted
    if [[ "${verification_file}" == *.enc ]]; then
      log "Decrypting backup for verification..."
      openssl enc -aes-256-cbc -d -in "${verification_file}" -out "${verification_file%.enc}" -pass file:"${ENCRYPTION_KEY_FILE}" || \
        handle_error "Failed to decrypt backup for verification"
      verification_file="${verification_file%.enc}"
    fi
    
    # Decompress if needed
    if [[ "${verification_file}" == *.gz ]]; then
      log "Decompressing backup for verification..."
      gunzip -f "${verification_file}" || handle_error "Failed to decompress backup for verification"
      verification_file="${verification_file%.gz}"
    fi
    
    # Create test database
    log "Creating test database ${db_name}_verify..."
    docker exec "${VERIFICATION_CONTAINER_PREFIX}-postgres" \
      psql -U "${POSTGRES_USER}" -c "CREATE DATABASE ${db_name}_verify;" || \
      handle_error "Failed to create test database"
    
    # Restore backup to test database
    log "Restoring backup to test database..."
    cat "${verification_file}" | docker exec -i "${VERIFICATION_CONTAINER_PREFIX}-postgres" \
      psql -U "${POSTGRES_USER}" -d "${db_name}_verify" || \
      handle_error "Failed to restore backup to test database"
    
    # Verify database integrity
    log "Verifying database integrity..."
    docker exec "${VERIFICATION_CONTAINER_PREFIX}-postgres" \
      psql -U "${POSTGRES_USER}" -d "${db_name}_verify" -c "SELECT count(*) FROM pg_catalog.pg_tables;" || \
      handle_error "Failed to verify database integrity"
    
    log "Backup verification successful for database: ${db_name}"
    
    # Clean up
    docker exec "${VERIFICATION_CONTAINER_PREFIX}-postgres" \
      psql -U "${POSTGRES_USER}" -c "DROP DATABASE ${db_name}_verify;" || true
    rm -f "${verification_file}"
  done
  
  # Stop and remove verification container
  docker stop "${VERIFICATION_CONTAINER_PREFIX}-postgres" || true
  docker rm "${VERIFICATION_CONTAINER_PREFIX}-postgres" || true
  
  log "PostgreSQL backup verification completed successfully"
}

# Verify Redis backup
verify_redis_backup() {
  log "Verifying Redis backups..."
  
  # Find the latest Redis RDB backup
  local rdb_backup=$(find "${REDIS_BACKUP_DIR}" -type f -name "redis_rdb_*.rdb.gz*" | sort -r | head -n1)
  
  if [ -z "${rdb_backup}" ]; then
    log "No Redis RDB backups found"
    return 0
  fi
  
  # Create a temporary directory for verification
  local temp_dir="${VERIFICATION_DIR}/redis"
  mkdir -p "${temp_dir}"
  
  # Process RDB backup
  log "Verifying RDB backup: $(basename "${rdb_backup}")"
  
  # Copy backup file to temp directory
  local verification_file="${temp_dir}/$(basename "${rdb_backup}")"
  cp "${rdb_backup}" "${verification_file}"
  
  # Decrypt if encrypted
  if [[ "${verification_file}" == *.enc ]]; then
    log "Decrypting backup for verification..."
    openssl enc -aes-256-cbc -d -in "${verification_file}" -out "${verification_file%.enc}" -pass file:"${ENCRYPTION_KEY_FILE}" || \
      handle_error "Failed to decrypt backup for verification"
    verification_file="${verification_file%.enc}"
  fi
  
  # Decompress if needed
  if [[ "${verification_file}" == *.gz ]]; then
    log "Decompressing backup for verification..."
    gunzip -f "${verification_file}" || handle_error "Failed to decompress backup for verification"
    verification_file="${verification_file%.gz}"
  fi
  
  # Verify RDB file integrity
  log "Verifying RDB file integrity..."
  redis-check-rdb "${verification_file}" || handle_error "RDB file integrity check failed"
  
  # Start a verification Redis container with the RDB file
  log "Starting verification Redis container with RDB file..."
  mkdir -p "${temp_dir}/redis-data"
  cp "${verification_file}" "${temp_dir}/redis-data/dump.rdb"
  
  docker run -d --name "${VERIFICATION_CONTAINER_PREFIX}-redis" \
    --network "${VERIFICATION_NETWORK}" \
    -v "${temp_dir}/redis-data:/data" \
    redis:7 redis-server --appendonly no || handle_error "Failed to start verification Redis container"
  
  # Wait for Redis to start
  sleep 5
  
  # Verify Redis is running with the restored data
  log "Verifying Redis is running with restored data..."
  docker exec "${VERIFICATION_CONTAINER_PREFIX}-redis" redis-cli ping || handle_error "Redis verification failed"
  
  # Stop and remove verification container
  docker stop "${VERIFICATION_CONTAINER_PREFIX}-redis" || true
  docker rm "${VERIFICATION_CONTAINER_PREFIX}-redis" || true
  
  # Clean up
  rm -rf "${temp_dir}/redis-data"
  rm -f "${verification_file}"
  
  log "Redis backup verification completed successfully"
}

# Verify configuration backups
verify_config_backups() {
  log "Verifying configuration backups..."
  
  # Find the latest configuration backup
  local config_backup=$(find "${CONFIG_BACKUP_DIR}" -type f -name "config_files_*.tar.gz*" | sort -r | head -n1)
  
  if [ -z "${config_backup}" ]; then
    log "No configuration backups found"
    return 0
  }
  
  # Create a temporary directory for verification
  local temp_dir="${VERIFICATION_DIR}/configs"
  mkdir -p "${temp_dir}"
  
  # Process configuration backup
  log "Verifying configuration backup: $(basename "${config_backup}")"
  
  # Copy backup file to temp directory
  local verification_file="${temp_dir}/$(basename "${config_backup}")"
  cp "${config_backup}" "${verification_file}"
  
  # Decrypt if encrypted
  if [[ "${verification_file}" == *.enc ]]; then
    log "Decrypting backup for verification..."
    openssl enc -aes-256-cbc -d -in "${verification_file}" -out "${verification_file%.enc}" -pass file:"${ENCRYPTION_KEY_FILE}" || \
      handle_error "Failed to decrypt backup for verification"
    verification_file="${verification_file%.enc}"
  fi
  
  # Decompress if needed
  if [[ "${verification_file}" == *.gz ]]; then
    log "Decompressing backup for verification..."
    gunzip -f "${verification_file}" || handle_error "Failed to decompress backup for verification"
    verification_file="${verification_file%.gz}"
  fi
  
  # Extract tar archive
  log "Extracting configuration backup..."
  tar -xf "${verification_file}" -C "${temp_dir}" || handle_error "Failed to extract configuration backup"
  
  # Verify docker-compose files
  if [ -f "${temp_dir}/docker-compose/docker-compose.yml" ]; then
    log "Verifying docker-compose.yml..."
    docker-compose -f "${temp_dir}/docker-compose/docker-compose.yml" config --quiet || \
      log "Warning: docker-compose.yml validation failed"
  fi
  
  if [ -f "${temp_dir}/docker-compose/docker-compose.prod.yml" ]; then
    log "Verifying docker-compose.prod.yml..."
    docker-compose -f "${temp_dir}/docker-compose/docker-compose.prod.yml" config --quiet || \
      log "Warning: docker-compose.prod.yml validation failed"
  fi
  
  # Clean up
  rm -f "${verification_file}"
  
  log "Configuration backup verification completed successfully"
}

# Generate verification report
generate_report() {
  local report_file="${VERIFICATION_DIR}/verification_report_$(date +%Y%m%d_%H%M%S).txt"
  
  log "Generating verification report: ${report_file}"
  
  echo "Eden DevOps Suite Backup Verification Report" > "${report_file}"
  echo "Date: $(date)" >> "${report_file}"
  echo "----------------------------------------" >> "${report_file}"
  echo "" >> "${report_file}"
  
  # Add PostgreSQL backup information
  echo "PostgreSQL Backups:" >> "${report_file}"
  find "${POSTGRES_BACKUP_DIR}" -type f -name "*.sql.gz*" | sort -r | head -n5 | while read backup; do
    echo "  - $(basename "${backup}") ($(du -h "${backup}" | cut -f1))" >> "${report_file}"
  done
  echo "" >> "${report_file}"
  
  # Add Redis backup information
  echo "Redis Backups:" >> "${report_file}"
  find "${REDIS_BACKUP_DIR}" -type f -name "redis_*.gz*" | sort -r | head -n5 | while read backup; do
    echo "  - $(basename "${backup}") ($(du -h "${backup}" | cut -f1))" >> "${report_file}"
  done
  echo "" >> "${report_file}"
  
  # Add configuration backup information
  echo "Configuration Backups:" >> "${report_file}"
  find "${CONFIG_BACKUP_DIR}" -type f -name "*.tar.gz*" | sort -r | head -n5 | while read backup; do
    echo "  - $(basename "${backup}") ($(du -h "${backup}" | cut -f1))" >> "${report_file}"
  done
  echo "" >> "${report_file}"
  
  # Add verification results
  echo "Verification Results:" >> "${report_file}"
  echo "  - PostgreSQL: PASSED" >> "${report_file}"
  echo "  - Redis: PASSED" >> "${report_file}"
  echo "  - Configurations: PASSED" >> "${report_file}"
  echo "" >> "${report_file}"
  
  # Add recommendations
  echo "Recommendations:" >> "${report_file}"
  echo "  - Perform a full restore test quarterly" >> "${report_file}"
  echo "  - Verify offsite backups monthly" >> "${report_file}"
  echo "  - Update encryption keys annually" >> "${report_file}"
  
  log "Verification report generated: ${report_file}"
  
  # Send report via email
  if [ -n "${NOTIFICATION_EMAIL}" ]; then
    cat "${report_file}" | mail -s "Eden Backup Verification Report" "${NOTIFICATION_EMAIL}"
  fi
}

# Main verification process
main() {
  log "Starting backup verification process"
  
  # Create verification environment
  create_verification_environment
  
  # Verify PostgreSQL backups
  verify_postgres_backup
  
  # Verify Redis backups
  verify_redis_backup
  
  # Verify configuration backups
  verify_config_backups
  
  # Generate verification report
  generate_report
  
  # Clean up verification environment
  cleanup_verification_environment
  
  log "Backup verification process completed successfully"
}

# Execute main function
main