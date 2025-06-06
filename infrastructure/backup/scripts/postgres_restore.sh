#!/bin/bash
#
# PostgreSQL Restore Script for Eden DevOps Suite
# This script performs restoration of PostgreSQL databases from backups
# Features:
# - Full database restoration
# - Point-in-time recovery using WAL archives
# - Support for encrypted backups
# - Validation before restoration
# - Dry-run option

set -e

# Configuration
BACKUP_DIR="${BACKUP_DIR:-/var/backups/eden/postgres}"
WAL_ARCHIVE_DIR="${WAL_ARCHIVE_DIR:-/var/backups/eden/postgres/wal_archive}"
ENCRYPTION_KEY_FILE="${ENCRYPTION_KEY_FILE:-/etc/eden/backup_encryption.key}"
POSTGRES_HOST="${POSTGRES_HOST:-postgres}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-postgres}"
RESTORE_LOG_FILE="${RESTORE_LOG_FILE:-/var/log/eden/postgres_restore.log}"
NOTIFICATION_EMAIL="${NOTIFICATION_EMAIL:-admin@example.com}"
DRY_RUN="${DRY_RUN:-false}"
POINT_IN_TIME="${POINT_IN_TIME:-}"  # Format: YYYY-MM-DD HH:MM:SS
RESTORE_DB="${RESTORE_DB:-}"  # Database to restore, if empty all databases will be restored
RESTORE_TO_DB="${RESTORE_TO_DB:-}"  # Restore to a different database name (only used when RESTORE_DB is set)
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
    echo "PostgreSQL restore failed: $1" | mail -s "Eden PostgreSQL Restore Failure" "${NOTIFICATION_EMAIL}"
  fi
  exit 1
}

# Display usage information
usage() {
  echo "Usage: $0 [options]"
  echo ""
  echo "Options:"
  echo "  --dry-run                 Perform a dry run without actually restoring"
  echo "  --db=<database>           Restore only the specified database"
  echo "  --to-db=<database>        Restore to a different database name"
  echo "  --backup-file=<file>      Use a specific backup file"
  echo "  --point-in-time=<time>    Perform point-in-time recovery (format: YYYY-MM-DD HH:MM:SS)"
  echo "  --host=<hostname>         PostgreSQL host (default: postgres)"
  echo "  --port=<port>             PostgreSQL port (default: 5432)"
  echo "  --user=<username>         PostgreSQL user (default: postgres)"
  echo "  --password=<password>     PostgreSQL password"
  echo "  --help                    Display this help message"
  echo ""
  echo "Environment variables:"
  echo "  BACKUP_DIR                Directory containing backups (default: /var/backups/eden/postgres)"
  echo "  WAL_ARCHIVE_DIR           Directory containing WAL archives (default: /var/backups/eden/postgres/wal_archive)"
  echo "  ENCRYPTION_KEY_FILE       Path to encryption key file (default: /etc/eden/backup_encryption.key)"
  echo "  POSTGRES_HOST             PostgreSQL host (default: postgres)"
  echo "  POSTGRES_PORT             PostgreSQL port (default: 5432)"
  echo "  POSTGRES_USER             PostgreSQL user (default: postgres)"
  echo "  POSTGRES_PASSWORD         PostgreSQL password"
  echo "  DRY_RUN                   Perform a dry run (default: false)"
  echo "  POINT_IN_TIME             Point-in-time recovery timestamp"
  echo "  RESTORE_DB                Database to restore"
  echo "  RESTORE_TO_DB             Restore to a different database name"
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
      --db=*)
        RESTORE_DB="${arg#*=}"
        ;;
      --to-db=*)
        RESTORE_TO_DB="${arg#*=}"
        ;;
      --backup-file=*)
        BACKUP_FILE="${arg#*=}"
        ;;
      --point-in-time=*)
        POINT_IN_TIME="${arg#*=}"
        ;;
      --host=*)
        POSTGRES_HOST="${arg#*=}"
        ;;
      --port=*)
        POSTGRES_PORT="${arg#*=}"
        ;;
      --user=*)
        POSTGRES_USER="${arg#*=}"
        ;;
      --password=*)
        POSTGRES_PASSWORD="${arg#*=}"
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
  
  # Validate the backup using pg_restore
  log "Testing backup with pg_restore..."
  pg_restore -l "${verification_file}" > /dev/null || handle_error "Backup validation failed"
  
  log "Backup validation successful"
  rm -rf "${temp_dir}"
}

# Restore a single database
restore_database() {
  local db="$1"
  local backup_file="$2"
  local target_db="${RESTORE_TO_DB:-$db}"
  local temp_dir=$(mktemp -d)
  
  log "Restoring database ${db} to ${target_db} from $(basename "${backup_file}")"
  
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
    log "DRY RUN: Would restore ${db} to ${target_db} from ${restore_file}"
  else
    # Check if target database exists
    if PGPASSWORD="${POSTGRES_PASSWORD}" psql -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" \
      -lqt | cut -d \| -f 1 | grep -qw "${target_db}"; then
      log "Target database ${target_db} exists, dropping..."
      PGPASSWORD="${POSTGRES_PASSWORD}" psql -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" \
        -c "DROP DATABASE IF EXISTS ${target_db};" || handle_error "Failed to drop existing database ${target_db}"
    fi
    
    # Create target database
    log "Creating target database ${target_db}..."
    PGPASSWORD="${POSTGRES_PASSWORD}" psql -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" \
      -c "CREATE DATABASE ${target_db};" || handle_error "Failed to create target database ${target_db}"
    
    # Restore the database
    log "Restoring database ${target_db}..."
    PGPASSWORD="${POSTGRES_PASSWORD}" pg_restore -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" \
      -d "${target_db}" -v "${restore_file}" || handle_error "Failed to restore database ${target_db}"
    
    log "Database ${target_db} restored successfully"
  fi
  
  # Clean up
  rm -rf "${temp_dir}"
}

# Perform point-in-time recovery
perform_pitr() {
  local target_time="$1"
  
  log "Performing point-in-time recovery to ${target_time}"
  
  if [ "${DRY_RUN}" = "true" ]; then
    log "DRY RUN: Would perform point-in-time recovery to ${target_time}"
    return 0
  fi
  
  # Check if WAL archives exist
  if [ ! -d "${WAL_ARCHIVE_DIR}" ] || [ -z "$(ls -A "${WAL_ARCHIVE_DIR}")" ]; then
    handle_error "WAL archive directory is empty or does not exist: ${WAL_ARCHIVE_DIR}"
  fi
  
  # Create recovery configuration
  log "Creating recovery configuration..."
  cat > /tmp/recovery.conf <<EOF
restore_command = 'cp ${WAL_ARCHIVE_DIR}/%f %p'
recovery_target_time = '${target_time}'
recovery_target_inclusive = true
EOF
  
  # Copy recovery configuration to PostgreSQL data directory
  log "Copying recovery configuration to PostgreSQL data directory..."
  # This step would typically require direct access to the PostgreSQL data directory
  # In a containerized environment, this might require additional steps
  log "NOTE: In a containerized environment, you may need to manually copy the recovery configuration"
  log "Recovery configuration created at /tmp/recovery.conf"
  
  log "Point-in-time recovery configuration completed"
  log "To complete the recovery process:"
  log "1. Stop the PostgreSQL server"
  log "2. Copy /tmp/recovery.conf to the PostgreSQL data directory"
  log "3. Start the PostgreSQL server"
  log "4. Monitor the PostgreSQL logs for recovery progress"
}

# Find the latest backup for a database
find_latest_backup() {
  local db="$1"
  local latest_backup=$(find "${BACKUP_DIR}" -type f -name "${db}_*.sql.gz*" | sort -r | head -n1)
  
  if [ -z "${latest_backup}" ]; then
    handle_error "No backup found for database ${db}"
  fi
  
  echo "${latest_backup}"
}

# Main restore process
main() {
  # Parse command line arguments
  parse_args "$@"
  
  log "Starting PostgreSQL restore process"
  
  if [ -n "${POINT_IN_TIME}" ]; then
    log "Point-in-time recovery requested: ${POINT_IN_TIME}"
  fi
  
  if [ "${DRY_RUN}" = "true" ]; then
    log "Running in DRY RUN mode, no actual changes will be made"
  fi
  
  # Determine which databases to restore
  local databases
  if [ -n "${RESTORE_DB}" ]; then
    databases="${RESTORE_DB}"
  else
    # Get list of databases from backup directory
    databases=$(find "${BACKUP_DIR}" -type f -name "*.sql.gz*" | sed -E 's/.*\/([^_]+)_.*/\1/' | sort -u)
  fi
  
  # Restore each database
  for db in ${databases}; do
    local backup_file
    
    if [ -n "${BACKUP_FILE}" ]; then
      # Use specified backup file
      backup_file="${BACKUP_FILE}"
      if [ ! -f "${backup_file}" ]; then
        handle_error "Specified backup file not found: ${backup_file}"
      fi
    else
      # Find the latest backup for this database
      backup_file=$(find_latest_backup "${db}")
    fi
    
    # Validate the backup
    validate_backup "${backup_file}"
    
    # Restore the database
    restore_database "${db}" "${backup_file}"
  done
  
  # Perform point-in-time recovery if requested
  if [ -n "${POINT_IN_TIME}" ]; then
    perform_pitr "${POINT_IN_TIME}"
  fi
  
  log "PostgreSQL restore process completed successfully"
  
  # Send success notification
  if [ -n "${NOTIFICATION_EMAIL}" ] && [ "${DRY_RUN}" != "true" ]; then
    echo "PostgreSQL restore completed successfully" | mail -s "Eden PostgreSQL Restore Success" "${NOTIFICATION_EMAIL}"
  fi
}

# Execute main function with all arguments
main "$@"