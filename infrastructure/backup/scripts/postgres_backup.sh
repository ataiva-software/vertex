#!/bin/bash
#
# PostgreSQL Backup Script for Eden DevOps Suite
# This script performs automated backups of PostgreSQL databases
# Features:
# - Full database dumps
# - WAL archiving for point-in-time recovery
# - Compression
# - Encryption
# - Retention policy
# - Backup verification

set -e

# Configuration
BACKUP_DIR="${BACKUP_DIR:-/var/backups/eden/postgres}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
ENCRYPTION_KEY_FILE="${ENCRYPTION_KEY_FILE:-/etc/eden/backup_encryption.key}"
POSTGRES_HOST="${POSTGRES_HOST:-postgres}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-postgres}"
POSTGRES_DATABASES="${POSTGRES_DATABASES:-eden_vault,eden_flow,eden_task,eden_hub,eden_sync,eden_insight}"
WAL_ARCHIVE_DIR="${WAL_ARCHIVE_DIR:-/var/backups/eden/postgres/wal_archive}"
BACKUP_TYPE="${BACKUP_TYPE:-full}"  # Options: full, incremental
NOTIFICATION_EMAIL="${NOTIFICATION_EMAIL:-admin@example.com}"
BACKUP_LOG_FILE="${BACKUP_LOG_FILE:-/var/log/eden/postgres_backup.log}"
S3_BUCKET="${S3_BUCKET:-}"  # Optional: S3 bucket for offsite backup
S3_PREFIX="${S3_PREFIX:-postgres-backups}"

# Create backup directories if they don't exist
mkdir -p "${BACKUP_DIR}"
mkdir -p "${WAL_ARCHIVE_DIR}"
mkdir -p "$(dirname "${BACKUP_LOG_FILE}")"

# Log function
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "${BACKUP_LOG_FILE}"
}

# Error handling function
handle_error() {
  log "ERROR: Backup failed: $1"
  if [ -n "${NOTIFICATION_EMAIL}" ]; then
    echo "PostgreSQL backup failed: $1" | mail -s "Eden PostgreSQL Backup Failure" "${NOTIFICATION_EMAIL}"
  fi
  exit 1
}

# Check if encryption key exists
check_encryption() {
  if [ ! -f "${ENCRYPTION_KEY_FILE}" ]; then
    log "Generating new encryption key..."
    openssl rand -base64 32 > "${ENCRYPTION_KEY_FILE}"
    chmod 600 "${ENCRYPTION_KEY_FILE}"
  fi
}

# Encrypt a file
encrypt_file() {
  local file="$1"
  log "Encrypting ${file}..."
  openssl enc -aes-256-cbc -salt -in "${file}" -out "${file}.enc" -pass file:"${ENCRYPTION_KEY_FILE}"
  rm "${file}"
}

# Backup a single database
backup_database() {
  local db="$1"
  local timestamp=$(date +%Y%m%d_%H%M%S)
  local backup_file="${BACKUP_DIR}/${db}_${timestamp}.sql"
  
  log "Starting backup of database: ${db}"
  
  # Perform the database dump
  PGPASSWORD="${POSTGRES_PASSWORD}" pg_dump -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" \
    -F c -b -v -f "${backup_file}" "${db}" || handle_error "Failed to backup ${db}"
  
  # Compress the backup
  log "Compressing backup..."
  gzip -9 "${backup_file}" || handle_error "Failed to compress backup"
  
  # Encrypt the backup if encryption key is available
  if [ -f "${ENCRYPTION_KEY_FILE}" ]; then
    encrypt_file "${backup_file}.gz"
    backup_file="${backup_file}.gz.enc"
  else
    backup_file="${backup_file}.gz"
  fi
  
  # Upload to S3 if configured
  if [ -n "${S3_BUCKET}" ]; then
    log "Uploading backup to S3..."
    aws s3 cp "${backup_file}" "s3://${S3_BUCKET}/${S3_PREFIX}/${db}/" || log "Warning: S3 upload failed"
  fi
  
  log "Backup of ${db} completed: ${backup_file}"
  return 0
}

# Configure WAL archiving
configure_wal_archiving() {
  log "Configuring WAL archiving..."
  
  # Check if WAL archiving is already enabled
  local archive_mode=$(PGPASSWORD="${POSTGRES_PASSWORD}" psql -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" \
    -t -c "SHOW archive_mode;")
  
  if [[ "${archive_mode}" != *"on"* ]]; then
    log "WAL archiving needs to be enabled in postgresql.conf"
    log "Please add the following to postgresql.conf and restart PostgreSQL:"
    log "archive_mode = on"
    log "archive_command = 'test ! -f ${WAL_ARCHIVE_DIR}/%f && cp %p ${WAL_ARCHIVE_DIR}/%f'"
    log "archive_timeout = 60"
  else
    log "WAL archiving is already enabled"
  fi
}

# Verify backup integrity
verify_backup() {
  local backup_file="$1"
  local db="$2"
  local temp_dir=$(mktemp -d)
  
  log "Verifying backup integrity for ${db}..."
  
  # Decrypt if encrypted
  if [[ "${backup_file}" == *.enc ]]; then
    log "Decrypting backup for verification..."
    openssl enc -aes-256-cbc -d -in "${backup_file}" -out "${temp_dir}/backup.gz" -pass file:"${ENCRYPTION_KEY_FILE}" || \
      handle_error "Failed to decrypt backup for verification"
    backup_file="${temp_dir}/backup.gz"
  fi
  
  # Decompress if needed
  if [[ "${backup_file}" == *.gz ]]; then
    log "Decompressing backup for verification..."
    gunzip -c "${backup_file}" > "${temp_dir}/backup.sql" || handle_error "Failed to decompress backup for verification"
    backup_file="${temp_dir}/backup.sql"
  fi
  
  # Verify the backup using pg_restore
  log "Testing backup with pg_restore..."
  pg_restore -l "${backup_file}" > /dev/null || handle_error "Backup verification failed for ${db}"
  
  log "Backup verification successful for ${db}"
  rm -rf "${temp_dir}"
}

# Clean up old backups
cleanup_old_backups() {
  log "Cleaning up backups older than ${BACKUP_RETENTION_DAYS} days..."
  find "${BACKUP_DIR}" -type f -name "*.sql.gz*" -mtime +${BACKUP_RETENTION_DAYS} -delete
  
  # Also clean up old WAL archives
  find "${WAL_ARCHIVE_DIR}" -type f -mtime +${BACKUP_RETENTION_DAYS} -delete
}

# Main backup process
main() {
  log "Starting PostgreSQL backup process"
  
  # Check encryption setup
  check_encryption
  
  # Configure WAL archiving for point-in-time recovery
  if [ "${BACKUP_TYPE}" = "incremental" ]; then
    configure_wal_archiving
  fi
  
  # Backup each database
  for db in $(echo "${POSTGRES_DATABASES}" | tr ',' ' '); do
    backup_database "${db}"
    
    # Get the latest backup file for verification
    latest_backup=$(ls -t "${BACKUP_DIR}/${db}_"*.sql.gz* 2>/dev/null | head -n1)
    if [ -n "${latest_backup}" ]; then
      verify_backup "${latest_backup}" "${db}"
    fi
  done
  
  # Clean up old backups
  cleanup_old_backups
  
  log "PostgreSQL backup process completed successfully"
  
  # Send success notification
  if [ -n "${NOTIFICATION_EMAIL}" ]; then
    echo "PostgreSQL backup completed successfully" | mail -s "Eden PostgreSQL Backup Success" "${NOTIFICATION_EMAIL}"
  fi
}

# Execute main function
main