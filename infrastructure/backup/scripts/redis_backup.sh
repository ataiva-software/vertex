#!/bin/bash
#
# Redis Backup Script for Eden DevOps Suite
# This script performs automated backups of Redis data
# Features:
# - RDB snapshot backups
# - AOF backups
# - Compression
# - Encryption
# - Retention policy
# - Backup verification

set -e

# Configuration
BACKUP_DIR="${BACKUP_DIR:-/var/backups/eden/redis}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
ENCRYPTION_KEY_FILE="${ENCRYPTION_KEY_FILE:-/etc/eden/backup_encryption.key}"
REDIS_HOST="${REDIS_HOST:-redis}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
REDIS_BACKUP_METHOD="${REDIS_BACKUP_METHOD:-both}"  # Options: rdb, aof, both
NOTIFICATION_EMAIL="${NOTIFICATION_EMAIL:-admin@example.com}"
BACKUP_LOG_FILE="${BACKUP_LOG_FILE:-/var/log/eden/redis_backup.log}"
S3_BUCKET="${S3_BUCKET:-}"  # Optional: S3 bucket for offsite backup
S3_PREFIX="${S3_PREFIX:-redis-backups}"

# Create backup directories if they don't exist
mkdir -p "${BACKUP_DIR}"
mkdir -p "$(dirname "${BACKUP_LOG_FILE}")"

# Log function
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "${BACKUP_LOG_FILE}"
}

# Error handling function
handle_error() {
  log "ERROR: Backup failed: $1"
  if [ -n "${NOTIFICATION_EMAIL}" ]; then
    echo "Redis backup failed: $1" | mail -s "Eden Redis Backup Failure" "${NOTIFICATION_EMAIL}"
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

# Backup Redis using RDB
backup_rdb() {
  local timestamp=$(date +%Y%m%d_%H%M%S)
  local backup_file="${BACKUP_DIR}/redis_rdb_${timestamp}.rdb"
  
  log "Starting Redis RDB backup"
  
  # Trigger SAVE command to create RDB snapshot
  if [ -n "${REDIS_PASSWORD}" ]; then
    redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -a "${REDIS_PASSWORD}" SAVE || handle_error "Failed to trigger Redis SAVE"
  else
    redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" SAVE || handle_error "Failed to trigger Redis SAVE"
  fi
  
  # Copy the RDB file
  log "Copying RDB file..."
  if [ -n "${REDIS_PASSWORD}" ]; then
    redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -a "${REDIS_PASSWORD}" CONFIG GET dir | grep -v dir | xargs -I {} \
      docker cp eden-redis:{}dump.rdb "${backup_file}" || handle_error "Failed to copy RDB file"
  else
    redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" CONFIG GET dir | grep -v dir | xargs -I {} \
      docker cp eden-redis:{}dump.rdb "${backup_file}" || handle_error "Failed to copy RDB file"
  fi
  
  # Compress the backup
  log "Compressing RDB backup..."
  gzip -9 "${backup_file}" || handle_error "Failed to compress RDB backup"
  
  # Encrypt the backup if encryption key is available
  if [ -f "${ENCRYPTION_KEY_FILE}" ]; then
    encrypt_file "${backup_file}.gz"
    backup_file="${backup_file}.gz.enc"
  else
    backup_file="${backup_file}.gz"
  fi
  
  # Upload to S3 if configured
  if [ -n "${S3_BUCKET}" ]; then
    log "Uploading RDB backup to S3..."
    aws s3 cp "${backup_file}" "s3://${S3_BUCKET}/${S3_PREFIX}/rdb/" || log "Warning: S3 upload failed"
  fi
  
  log "Redis RDB backup completed: ${backup_file}"
  return 0
}

# Backup Redis using AOF
backup_aof() {
  local timestamp=$(date +%Y%m%d_%H%M%S)
  local backup_file="${BACKUP_DIR}/redis_aof_${timestamp}.aof"
  
  log "Starting Redis AOF backup"
  
  # Check if AOF is enabled
  local aof_enabled
  if [ -n "${REDIS_PASSWORD}" ]; then
    aof_enabled=$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -a "${REDIS_PASSWORD}" CONFIG GET appendonly | grep -v appendonly)
  else
    aof_enabled=$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" CONFIG GET appendonly | grep -v appendonly)
  fi
  
  if [ "${aof_enabled}" != "yes" ]; then
    log "Warning: AOF is not enabled on Redis server. Skipping AOF backup."
    return 0
  fi
  
  # Copy the AOF file
  log "Copying AOF file..."
  if [ -n "${REDIS_PASSWORD}" ]; then
    redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -a "${REDIS_PASSWORD}" CONFIG GET dir | grep -v dir | xargs -I {} \
      redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -a "${REDIS_PASSWORD}" CONFIG GET appendfilename | grep -v appendfilename | xargs -I [] \
      docker cp eden-redis:{}/[] "${backup_file}" || handle_error "Failed to copy AOF file"
  else
    redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" CONFIG GET dir | grep -v dir | xargs -I {} \
      redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" CONFIG GET appendfilename | grep -v appendfilename | xargs -I [] \
      docker cp eden-redis:{}/[] "${backup_file}" || handle_error "Failed to copy AOF file"
  fi
  
  # Compress the backup
  log "Compressing AOF backup..."
  gzip -9 "${backup_file}" || handle_error "Failed to compress AOF backup"
  
  # Encrypt the backup if encryption key is available
  if [ -f "${ENCRYPTION_KEY_FILE}" ]; then
    encrypt_file "${backup_file}.gz"
    backup_file="${backup_file}.gz.enc"
  else
    backup_file="${backup_file}.gz"
  fi
  
  # Upload to S3 if configured
  if [ -n "${S3_BUCKET}" ]; then
    log "Uploading AOF backup to S3..."
    aws s3 cp "${backup_file}" "s3://${S3_BUCKET}/${S3_PREFIX}/aof/" || log "Warning: S3 upload failed"
  fi
  
  log "Redis AOF backup completed: ${backup_file}"
  return 0
}

# Verify backup integrity
verify_backup() {
  local backup_file="$1"
  local backup_type="$2"
  local temp_dir=$(mktemp -d)
  
  log "Verifying backup integrity for ${backup_type}..."
  
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
    gunzip -c "${backup_file}" > "${temp_dir}/backup.${backup_type}" || handle_error "Failed to decompress backup for verification"
    backup_file="${temp_dir}/backup.${backup_type}"
  fi
  
  # Verify the backup
  if [ "${backup_type}" = "rdb" ]; then
    log "Testing RDB backup with redis-check-rdb..."
    redis-check-rdb "${backup_file}" || handle_error "RDB backup verification failed"
  elif [ "${backup_type}" = "aof" ]; then
    log "Testing AOF backup with redis-check-aof..."
    redis-check-aof --fix "${backup_file}" || handle_error "AOF backup verification failed"
  fi
  
  log "Backup verification successful for ${backup_type}"
  rm -rf "${temp_dir}"
}

# Clean up old backups
cleanup_old_backups() {
  log "Cleaning up backups older than ${BACKUP_RETENTION_DAYS} days..."
  find "${BACKUP_DIR}" -type f -name "redis_*.gz*" -mtime +${BACKUP_RETENTION_DAYS} -delete
}

# Main backup process
main() {
  log "Starting Redis backup process"
  
  # Check encryption setup
  check_encryption
  
  # Perform backups based on configured method
  if [ "${REDIS_BACKUP_METHOD}" = "rdb" ] || [ "${REDIS_BACKUP_METHOD}" = "both" ]; then
    backup_rdb
    
    # Get the latest RDB backup file for verification
    latest_rdb=$(ls -t "${BACKUP_DIR}/redis_rdb_"*.rdb.gz* 2>/dev/null | head -n1)
    if [ -n "${latest_rdb}" ]; then
      verify_backup "${latest_rdb}" "rdb"
    fi
  fi
  
  if [ "${REDIS_BACKUP_METHOD}" = "aof" ] || [ "${REDIS_BACKUP_METHOD}" = "both" ]; then
    backup_aof
    
    # Get the latest AOF backup file for verification
    latest_aof=$(ls -t "${BACKUP_DIR}/redis_aof_"*.aof.gz* 2>/dev/null | head -n1)
    if [ -n "${latest_aof}" ]; then
      verify_backup "${latest_aof}" "aof"
    fi
  fi
  
  # Clean up old backups
  cleanup_old_backups
  
  log "Redis backup process completed successfully"
  
  # Send success notification
  if [ -n "${NOTIFICATION_EMAIL}" ]; then
    echo "Redis backup completed successfully" | mail -s "Eden Redis Backup Success" "${NOTIFICATION_EMAIL}"
  fi
}

# Execute main function
main