#!/bin/bash
#
# Configuration and Secrets Backup Script for Eden DevOps Suite
# This script performs automated backups of configuration files and secrets
# Features:
# - Configuration file backups
# - Kubernetes secrets backups
# - Vault secrets backups
# - Compression
# - Encryption
# - Retention policy

set -e

# Configuration
BACKUP_DIR="${BACKUP_DIR:-/var/backups/eden/configs}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
ENCRYPTION_KEY_FILE="${ENCRYPTION_KEY_FILE:-/etc/eden/backup_encryption.key}"
NOTIFICATION_EMAIL="${NOTIFICATION_EMAIL:-admin@example.com}"
BACKUP_LOG_FILE="${BACKUP_LOG_FILE:-/var/log/eden/config_backup.log}"
S3_BUCKET="${S3_BUCKET:-}"  # Optional: S3 bucket for offsite backup
S3_PREFIX="${S3_PREFIX:-config-backups}"
KUBERNETES_NAMESPACE="${KUBERNETES_NAMESPACE:-eden}"
VAULT_ADDR="${VAULT_ADDR:-http://vault-service:8081}"
VAULT_TOKEN="${VAULT_TOKEN:-}"
CONFIG_PATHS="${CONFIG_PATHS:-/etc/eden,/opt/eden/config}"

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
    echo "Configuration backup failed: $1" | mail -s "Eden Configuration Backup Failure" "${NOTIFICATION_EMAIL}"
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

# Backup configuration files
backup_config_files() {
  local timestamp=$(date +%Y%m%d_%H%M%S)
  local backup_file="${BACKUP_DIR}/config_files_${timestamp}.tar"
  
  log "Starting configuration files backup"
  
  # Create a temporary directory for configs
  local temp_dir=$(mktemp -d)
  
  # Copy configuration files to temporary directory
  for config_path in $(echo "${CONFIG_PATHS}" | tr ',' ' '); do
    if [ -d "${config_path}" ]; then
      log "Copying configuration files from ${config_path}..."
      mkdir -p "${temp_dir}$(dirname "${config_path}")"
      cp -r "${config_path}" "${temp_dir}$(dirname "${config_path}")/" || log "Warning: Failed to copy some files from ${config_path}"
    fi
  done
  
  # Backup docker-compose files
  log "Backing up docker-compose files..."
  mkdir -p "${temp_dir}/docker-compose"
  cp -f docker-compose.yml docker-compose.prod.yml "${temp_dir}/docker-compose/" 2>/dev/null || true
  
  # Create tar archive
  tar -cf "${backup_file}" -C "${temp_dir}" . || handle_error "Failed to create tar archive"
  
  # Clean up temporary directory
  rm -rf "${temp_dir}"
  
  # Compress the backup
  log "Compressing configuration backup..."
  gzip -9 "${backup_file}" || handle_error "Failed to compress configuration backup"
  
  # Encrypt the backup if encryption key is available
  if [ -f "${ENCRYPTION_KEY_FILE}" ]; then
    encrypt_file "${backup_file}.gz"
    backup_file="${backup_file}.gz.enc"
  else
    backup_file="${backup_file}.gz"
  fi
  
  # Upload to S3 if configured
  if [ -n "${S3_BUCKET}" ]; then
    log "Uploading configuration backup to S3..."
    aws s3 cp "${backup_file}" "s3://${S3_BUCKET}/${S3_PREFIX}/configs/" || log "Warning: S3 upload failed"
  fi
  
  log "Configuration files backup completed: ${backup_file}"
  return 0
}

# Backup Kubernetes secrets
backup_kubernetes_secrets() {
  local timestamp=$(date +%Y%m%d_%H%M%S)
  local backup_file="${BACKUP_DIR}/kubernetes_secrets_${timestamp}.yaml"
  
  log "Starting Kubernetes secrets backup"
  
  # Check if kubectl is available
  if ! command -v kubectl &> /dev/null; then
    log "Warning: kubectl not found, skipping Kubernetes secrets backup"
    return 0
  fi
  
  # Check if we can access the Kubernetes API
  if ! kubectl get ns "${KUBERNETES_NAMESPACE}" &> /dev/null; then
    log "Warning: Cannot access Kubernetes namespace ${KUBERNETES_NAMESPACE}, skipping Kubernetes secrets backup"
    return 0
  fi
  
  # Export all secrets in the namespace
  kubectl get secrets -n "${KUBERNETES_NAMESPACE}" -o yaml > "${backup_file}" || handle_error "Failed to export Kubernetes secrets"
  
  # Compress the backup
  log "Compressing Kubernetes secrets backup..."
  gzip -9 "${backup_file}" || handle_error "Failed to compress Kubernetes secrets backup"
  
  # Encrypt the backup if encryption key is available
  if [ -f "${ENCRYPTION_KEY_FILE}" ]; then
    encrypt_file "${backup_file}.gz"
    backup_file="${backup_file}.gz.enc"
  else
    backup_file="${backup_file}.gz"
  fi
  
  # Upload to S3 if configured
  if [ -n "${S3_BUCKET}" ]; then
    log "Uploading Kubernetes secrets backup to S3..."
    aws s3 cp "${backup_file}" "s3://${S3_BUCKET}/${S3_PREFIX}/kubernetes/" || log "Warning: S3 upload failed"
  fi
  
  log "Kubernetes secrets backup completed: ${backup_file}"
  return 0
}

# Backup Vault secrets
backup_vault_secrets() {
  local timestamp=$(date +%Y%m%d_%H%M%S)
  local backup_file="${BACKUP_DIR}/vault_secrets_${timestamp}.json"
  
  log "Starting Vault secrets backup"
  
  # Check if vault CLI is available
  if ! command -v vault &> /dev/null; then
    log "Warning: vault CLI not found, skipping Vault secrets backup"
    return 0
  fi
  
  # Check if VAULT_TOKEN is provided
  if [ -z "${VAULT_TOKEN}" ]; then
    log "Warning: VAULT_TOKEN not provided, skipping Vault secrets backup"
    return 0
  fi
  
  # Export Vault secrets
  VAULT_ADDR="${VAULT_ADDR}" VAULT_TOKEN="${VAULT_TOKEN}" vault operator raft snapshot save "${backup_file}" || \
    handle_error "Failed to export Vault secrets"
  
  # Compress the backup
  log "Compressing Vault secrets backup..."
  gzip -9 "${backup_file}" || handle_error "Failed to compress Vault secrets backup"
  
  # Encrypt the backup if encryption key is available
  if [ -f "${ENCRYPTION_KEY_FILE}" ]; then
    encrypt_file "${backup_file}.gz"
    backup_file="${backup_file}.gz.enc"
  else
    backup_file="${backup_file}.gz"
  fi
  
  # Upload to S3 if configured
  if [ -n "${S3_BUCKET}" ]; then
    log "Uploading Vault secrets backup to S3..."
    aws s3 cp "${backup_file}" "s3://${S3_BUCKET}/${S3_PREFIX}/vault/" || log "Warning: S3 upload failed"
  fi
  
  log "Vault secrets backup completed: ${backup_file}"
  return 0
}

# Clean up old backups
cleanup_old_backups() {
  log "Cleaning up backups older than ${BACKUP_RETENTION_DAYS} days..."
  find "${BACKUP_DIR}" -type f -name "*.gz*" -mtime +${BACKUP_RETENTION_DAYS} -delete
}

# Main backup process
main() {
  log "Starting configuration and secrets backup process"
  
  # Check encryption setup
  check_encryption
  
  # Backup configuration files
  backup_config_files
  
  # Backup Kubernetes secrets
  backup_kubernetes_secrets
  
  # Backup Vault secrets
  backup_vault_secrets
  
  # Clean up old backups
  cleanup_old_backups
  
  log "Configuration and secrets backup process completed successfully"
  
  # Send success notification
  if [ -n "${NOTIFICATION_EMAIL}" ]; then
    echo "Configuration and secrets backup completed successfully" | mail -s "Eden Configuration Backup Success" "${NOTIFICATION_EMAIL}"
  fi
}

# Execute main function
main