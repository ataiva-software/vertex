#!/bin/bash
#
# Redis Restore Script for Eden DevOps Suite
# This script performs restoration of Redis data from backups
# Features:
# - RDB snapshot restoration
# - AOF file restoration
# - Support for encrypted backups
# - Validation before restoration
# - Dry-run option

set -e

# Configuration
BACKUP_DIR="${BACKUP_DIR:-/var/backups/eden/redis}"
ENCRYPTION_KEY_FILE="${ENCRYPTION_KEY_FILE:-/etc/eden/backup_encryption.key}"
REDIS_HOST="${REDIS_HOST:-redis}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
REDIS_CONTAINER="${REDIS_CONTAINER:-eden-redis}"
RESTORE_LOG_FILE="${RESTORE_LOG_FILE:-/var/log/eden/redis_restore.log}"
NOTIFICATION_EMAIL="${NOTIFICATION_EMAIL:-admin@example.com}"
DRY_RUN="${DRY_RUN:-false}"
BACKUP_TYPE="${BACKUP_TYPE:-rdb}"  # Options: rdb, aof
BACKUP_FILE="${BACKUP_FILE:-}"  # Specific backup file to restore, if empty the latest backup will be used

# Create log directory if it doesn't exist
mkdir -p "$(dirname "${RESTORE_LOG_FILE}")"

# Log function
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "${RESTORE_LOG_FILE}"
}

# Error handling function
handle_error() {
  log "ERROR: Restore failed: $1"
  if [ -n "${NOTIFICATION_EMAIL}" ]; then
    echo "Redis restore failed: $1" | mail -s "Eden Redis Restore Failure" "${NOTIFICATION_EMAIL}"
  fi
  exit 1
}

# Display usage information
usage() {
  echo "Usage: $0 [options]"
  echo ""
  echo "Options:"
  echo "  --dry-run                 Perform a dry run without actually restoring"
  echo "  --type=<rdb|aof>          Type of backup to restore (default: rdb)"
  echo "  --backup-file=<file>      Use a specific backup file"
  echo "  --host=<hostname>         Redis host (default: redis)"
  echo "  --port=<port>             Redis port (default: 6379)"
  echo "  --password=<password>     Redis password"
  echo "  --container=<name>        Redis container name (default: eden-redis)"
  echo "  --help                    Display this help message"
  echo ""
  echo "Environment variables:"
  echo "  BACKUP_DIR                Directory containing backups (default: /var/backups/eden/redis)"
  echo "  ENCRYPTION_KEY_FILE       Path to encryption key file (default: /etc/eden/backup_encryption.key)"
  echo "  REDIS_HOST                Redis host (default: redis)"
  echo "  REDIS_PORT                Redis port (default: 6379)"
  echo "  REDIS_PASSWORD            Redis password"
  echo "  REDIS_CONTAINER           Redis container name (default: eden-redis)"
  echo "  DRY_RUN                   Perform a dry run (default: false)"
  echo "  BACKUP_TYPE               Type of backup to restore (default: rdb)"
  echo "  BACKUP_FILE               Specific backup file to restore"
  exit 1
}

# Parse command line arguments
parse_args() {
  for arg in "$@"; do
    case $arg in
      --dry-run)
        DRY_RUN="true"
        ;;
      --type=*)
        BACKUP_TYPE="${arg#*=}"
        ;;
      --backup-file=*)
        BACKUP_FILE="${arg#*=}"
        ;;
      --host=*)
        REDIS_HOST="${arg#*=}"
        ;;
      --port=*)
        REDIS_PORT="${arg#*=}"
        ;;
      --password=*)
        REDIS_PASSWORD="${arg#*=}"
        ;;
      --container=*)
        REDIS_CONTAINER="${arg#*=}"
        ;;
      --help)
        usage
        ;;
      *)
        echo "Unknown option: $arg"
        usage
        ;;
    esac
  done
}

# Validate backup file
validate_backup() {
  local backup_file="$1"
  local backup_type="$2"
  local temp_dir=$(mktemp -d)
  
  log "Validating backup file: $(basename "${backup_file}")"
  
  # Copy backup file to temp directory
  local verification_file="${temp_dir}/$(basename "${backup_file}")"
  cp "${backup_file}" "${verification_file}"
  
  # Decrypt if encrypted
  if [[ "${verification_file}" == *.enc ]]; then
    log "Decrypting backup for validation..."
    if [ ! -f "${ENCRYPTION_KEY_FILE}" ]; then
      handle_error "Encryption key file not found: ${ENCRYPTION_KEY_FILE}"
    fi
    openssl enc -aes-256-cbc -d -in "${verification_file}" -out "${verification_file%.enc}" -pass file:"${ENCRYPTION_KEY_FILE}" || \
      handle_error "Failed to decrypt backup for validation"
    verification_file="${verification_file%.enc}"
  fi
  
  # Decompress if needed
  if [[ "${verification_file}" == *.gz ]]; then
    log "Decompressing backup for validation..."
    gunzip -f "${verification_file}" || handle_error "Failed to decompress backup for validation"
    verification_file="${verification_file%.gz}"
  fi
  
  # Validate the backup
  if [ "${backup_type}" = "rdb" ]; then
    log "Testing RDB backup with redis-check-rdb..."
    redis-check-rdb "${verification_file}" || handle_error "RDB backup validation failed"
  elif [ "${backup_type}" = "aof" ]; then
    log "Testing AOF backup with redis-check-aof..."
    redis-check-aof --fix "${verification_file}" || handle_error "AOF backup validation failed"
  else
    handle_error "Unknown backup type: ${backup_type}"
  fi
  
  log "Backup validation successful"
  rm -rf "${temp_dir}"
}

# Find the latest backup of a specific type
find_latest_backup() {
  local backup_type="$1"
  local pattern
  
  if [ "${backup_type}" = "rdb" ]; then
    pattern="redis_rdb_*.rdb.gz*"
  elif [ "${backup_type}" = "aof" ]; then
    pattern="redis_aof_*.aof.gz*"
  else
    handle_error "Unknown backup type: ${backup_type}"
  fi
  
  local latest_backup=$(find "${BACKUP_DIR}" -type f -name "${pattern}" | sort -r | head -n1)
  
  if [ -z "${latest_backup}" ]; then
    handle_error "No ${backup_type} backup found"
  fi
  
  echo "${latest_backup}"
}

# Restore Redis from RDB backup
restore_rdb() {
  local backup_file="$1"
  local temp_dir=$(mktemp -d)
  
  log "Restoring Redis from RDB backup: $(basename "${backup_file}")"
  
  # Copy backup file to temp directory
  local restore_file="${temp_dir}/$(basename "${backup_file}")"
  cp "${backup_file}" "${restore_file}"
  
  # Decrypt if encrypted
  if [[ "${restore_file}" == *.enc ]]; then
    log "Decrypting backup..."
    if [ ! -f "${ENCRYPTION_KEY_FILE}" ]; then
      handle_error "Encryption key file not found: ${ENCRYPTION_KEY_FILE}"
    fi
    openssl enc -aes-256-cbc -d -in "${restore_file}" -out "${restore_file%.enc}" -pass file:"${ENCRYPTION_KEY_FILE}" || \
      handle_error "Failed to decrypt backup"
    restore_file="${restore_file%.enc}"
  fi
  
  # Decompress if needed
  if [[ "${restore_file}" == *.gz ]]; then
    log "Decompressing backup..."
    gunzip -f "${restore_file}" || handle_error "Failed to decompress backup"
    restore_file="${restore_file%.gz}"
  fi
  
  if [ "${DRY_RUN}" = "true" ]; then
    log "DRY RUN: Would restore Redis from RDB file ${restore_file}"
  else
    # Get Redis data directory
    local redis_data_dir
    if [ -n "${REDIS_PASSWORD}" ]; then
      redis_data_dir=$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -a "${REDIS_PASSWORD}" CONFIG GET dir | grep -v dir)
    else
      redis_data_dir=$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" CONFIG GET dir | grep -v dir)
    fi
    
    log "Redis data directory: ${redis_data_dir}"
    
    # Stop Redis
    log "Stopping Redis..."
    docker stop "${REDIS_CONTAINER}" || handle_error "Failed to stop Redis container"
    
    # Copy RDB file to Redis data directory
    log "Copying RDB file to Redis data directory..."
    docker cp "${restore_file}" "${REDIS_CONTAINER}:${redis_data_dir}/dump.rdb" || handle_error "Failed to copy RDB file"
    
    # Start Redis
    log "Starting Redis..."
    docker start "${REDIS_CONTAINER}" || handle_error "Failed to start Redis container"
    
    log "Redis restored successfully from RDB backup"
  fi
  
  # Clean up
  rm -rf "${temp_dir}"
}

# Restore Redis from AOF backup
restore_aof() {
  local backup_file="$1"
  local temp_dir=$(mktemp -d)
  
  log "Restoring Redis from AOF backup: $(basename "${backup_file}")"
  
  # Copy backup file to temp directory
  local restore_file="${temp_dir}/$(basename "${backup_file}")"
  cp "${backup_file}" "${restore_file}"
  
  # Decrypt if encrypted
  if [[ "${restore_file}" == *.enc ]]; then
    log "Decrypting backup..."
    if [ ! -f "${ENCRYPTION_KEY_FILE}" ]; then
      handle_error "Encryption key file not found: ${ENCRYPTION_KEY_FILE}"
    fi
    openssl enc -aes-256-cbc -d -in "${restore_file}" -out "${restore_file%.enc}" -pass file:"${ENCRYPTION_KEY_FILE}" || \
      handle_error "Failed to decrypt backup"
    restore_file="${restore_file%.enc}"
  fi
  
  # Decompress if needed
  if [[ "${restore_file}" == *.gz ]]; then
    log "Decompressing backup..."
    gunzip -f "${restore_file}" || handle_error "Failed to decompress backup"
    restore_file="${restore_file%.gz}"
  fi
  
  if [ "${DRY_RUN}" = "true" ]; then
    log "DRY RUN: Would restore Redis from AOF file ${restore_file}"
  else
    # Get Redis configuration
    local redis_data_dir
    local appendfilename
    
    if [ -n "${REDIS_PASSWORD}" ]; then
      redis_data_dir=$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -a "${REDIS_PASSWORD}" CONFIG GET dir | grep -v dir)
      appendfilename=$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -a "${REDIS_PASSWORD}" CONFIG GET appendfilename | grep -v appendfilename)
    else
      redis_data_dir=$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" CONFIG GET dir | grep -v dir)
      appendfilename=$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" CONFIG GET appendfilename | grep -v appendfilename)
    fi
    
    log "Redis data directory: ${redis_data_dir}"
    log "Redis AOF filename: ${appendfilename}"
    
    # Check if AOF is enabled
    local aof_enabled
    if [ -n "${REDIS_PASSWORD}" ]; then
      aof_enabled=$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -a "${REDIS_PASSWORD}" CONFIG GET appendonly | grep -v appendonly)
    else
      aof_enabled=$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" CONFIG GET appendonly | grep -v appendonly)
    fi
    
    if [ "${aof_enabled}" != "yes" ]; then
      log "Warning: AOF is not enabled on Redis server. Enabling it now..."
      if [ -n "${REDIS_PASSWORD}" ]; then
        redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -a "${REDIS_PASSWORD}" CONFIG SET appendonly yes || \
          handle_error "Failed to enable AOF"
      else
        redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" CONFIG SET appendonly yes || \
          handle_error "Failed to enable AOF"
      fi
    fi
    
    # Stop Redis
    log "Stopping Redis..."
    docker stop "${REDIS_CONTAINER}" || handle_error "Failed to stop Redis container"
    
    # Copy AOF file to Redis data directory
    log "Copying AOF file to Redis data directory..."
    docker cp "${restore_file}" "${REDIS_CONTAINER}:${redis_data_dir}/${appendfilename}" || handle_error "Failed to copy AOF file"
    
    # Start Redis
    log "Starting Redis..."
    docker start "${REDIS_CONTAINER}" || handle_error "Failed to start Redis container"
    
    log "Redis restored successfully from AOF backup"
  fi
  
  # Clean up
  rm -rf "${temp_dir}"
}

# Main restore process
main() {
  # Parse command line arguments
  parse_args "$@"
  
  log "Starting Redis restore process"
  
  if [ "${DRY_RUN}" = "true" ]; then
    log "Running in DRY RUN mode, no actual changes will be made"
  fi
  
  # Determine which backup file to restore
  local backup_file
  
  if [ -n "${BACKUP_FILE}" ]; then
    # Use specified backup file
    backup_file="${BACKUP_FILE}"
    if [ ! -f "${backup_file}" ]; then
      handle_error "Specified backup file not found: ${backup_file}"
    fi
  else
    # Find the latest backup of the specified type
    backup_file=$(find_latest_backup "${BACKUP_TYPE}")
  fi
  
  # Validate the backup
  validate_backup "${backup_file}" "${BACKUP_TYPE}"
  
  # Restore Redis based on backup type
  if [ "${BACKUP_TYPE}" = "rdb" ]; then
    restore_rdb "${backup_file}"
  elif [ "${BACKUP_TYPE}" = "aof" ]; then
    restore_aof "${backup_file}"
  else
    handle_error "Unknown backup type: ${BACKUP_TYPE}"
  fi
  
  log "Redis restore process completed successfully"
  
  # Send success notification
  if [ -n "${NOTIFICATION_EMAIL}" ] && [ "${DRY_RUN}" != "true" ]; then
    echo "Redis restore completed successfully" | mail -s "Eden Redis Restore Success" "${NOTIFICATION_EMAIL}"
  fi
}

# Execute main function with all arguments
main "$@"