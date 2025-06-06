#!/bin/bash
#
# Backup Monitoring Script for Eden DevOps Suite
# This script monitors backup jobs and reports their status
# Features:
# - Monitors backup job execution
# - Checks backup file integrity
# - Verifies backup freshness
# - Sends alerts for failed or missing backups
# - Integrates with Prometheus for metrics

set -e

# Configuration
BACKUP_DIR="${BACKUP_DIR:-/var/backups/eden}"
POSTGRES_BACKUP_DIR="${POSTGRES_BACKUP_DIR:-${BACKUP_DIR}/postgres}"
REDIS_BACKUP_DIR="${REDIS_BACKUP_DIR:-${BACKUP_DIR}/redis}"
CONFIG_BACKUP_DIR="${CONFIG_BACKUP_DIR:-${BACKUP_DIR}/configs}"
MONITORING_LOG_FILE="${MONITORING_LOG_FILE:-/var/log/eden/backup_monitoring.log}"
NOTIFICATION_EMAIL="${NOTIFICATION_EMAIL:-admin@example.com}"
SLACK_WEBHOOK_URL="${SLACK_WEBHOOK_URL:-}"
MAX_BACKUP_AGE_HOURS="${MAX_BACKUP_AGE_HOURS:-25}"  # Slightly more than 24h to account for timing variations
PROMETHEUS_TEXTFILE_DIR="${PROMETHEUS_TEXTFILE_DIR:-/var/lib/prometheus/node-exporter}"
DATABASES="${DATABASES:-eden_vault,eden_flow,eden_task,eden_hub,eden_sync,eden_insight}"

# Create log directory if it doesn't exist
mkdir -p "$(dirname "${MONITORING_LOG_FILE}")"
mkdir -p "${PROMETHEUS_TEXTFILE_DIR}" 2>/dev/null || true

# Log function
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "${MONITORING_LOG_FILE}"
}

# Send notification
send_notification() {
  local subject="$1"
  local message="$2"
  local priority="$3"  # info, warning, critical
  
  log "${priority}: ${subject} - ${message}"
  
  # Send email notification
  if [ -n "${NOTIFICATION_EMAIL}" ]; then
    echo "${message}" | mail -s "[${priority}] ${subject}" "${NOTIFICATION_EMAIL}"
  fi
  
  # Send Slack notification
  if [ -n "${SLACK_WEBHOOK_URL}" ]; then
    local color="good"
    if [ "${priority}" = "warning" ]; then
      color="warning"
    elif [ "${priority}" = "critical" ]; then
      color="danger"
    fi
    
    curl -s -X POST -H 'Content-type: application/json' --data "{
      \"attachments\": [
        {
          \"color\": \"${color}\",
          \"title\": \"${subject}\",
          \"text\": \"${message}\",
          \"footer\": \"Eden Backup Monitoring\"
        }
      ]
    }" "${SLACK_WEBHOOK_URL}" > /dev/null || log "Failed to send Slack notification"
  fi
}

# Check backup freshness
check_backup_freshness() {
  local backup_type="$1"
  local backup_dir="$2"
  local file_pattern="$3"
  
  log "Checking ${backup_type} backup freshness..."
  
  # Find the latest backup file
  local latest_backup=$(find "${backup_dir}" -type f -name "${file_pattern}" | sort -r | head -n1)
  
  if [ -z "${latest_backup}" ]; then
    send_notification "Missing ${backup_type} Backup" "No ${backup_type} backup files found in ${backup_dir}" "critical"
    echo "eden_backup_status{type=\"${backup_type}\",status=\"missing\"} 0" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
    return 1
  fi
  
  # Check file age
  local file_age_seconds=$(( $(date +%s) - $(stat -c %Y "${latest_backup}") ))
  local file_age_hours=$(( ${file_age_seconds} / 3600 ))
  
  log "Latest ${backup_type} backup is ${file_age_hours} hours old: $(basename "${latest_backup}")"
  
  # Export metrics
  echo "eden_backup_age_seconds{type=\"${backup_type}\",file=\"$(basename "${latest_backup}")\"} ${file_age_seconds}" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
  echo "eden_backup_size_bytes{type=\"${backup_type}\",file=\"$(basename "${latest_backup}")\"} $(stat -c %s "${latest_backup}")" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
  
  # Check if backup is too old
  if [ ${file_age_hours} -gt ${MAX_BACKUP_AGE_HOURS} ]; then
    send_notification "Stale ${backup_type} Backup" "Latest ${backup_type} backup is ${file_age_hours} hours old (max age: ${MAX_BACKUP_AGE_HOURS} hours)" "critical"
    echo "eden_backup_status{type=\"${backup_type}\",status=\"stale\"} 0" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
    return 1
  else
    echo "eden_backup_status{type=\"${backup_type}\",status=\"fresh\"} 1" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
    return 0
  fi
}

# Check PostgreSQL backups
check_postgres_backups() {
  log "Checking PostgreSQL backups..."
  local overall_status=0
  
  # Check each database backup
  for db in $(echo "${DATABASES}" | tr ',' ' '); do
    if ! check_backup_freshness "postgres_${db}" "${POSTGRES_BACKUP_DIR}" "${db}_*.sql.gz*"; then
      overall_status=1
    fi
  done
  
  return ${overall_status}
}

# Check Redis backups
check_redis_backups() {
  log "Checking Redis backups..."
  local overall_status=0
  
  # Check RDB backups
  if ! check_backup_freshness "redis_rdb" "${REDIS_BACKUP_DIR}" "redis_rdb_*.rdb.gz*"; then
    overall_status=1
  fi
  
  # Check AOF backups if they exist
  if [ -n "$(find "${REDIS_BACKUP_DIR}" -type f -name "redis_aof_*.aof.gz*" | head -n1)" ]; then
    if ! check_backup_freshness "redis_aof" "${REDIS_BACKUP_DIR}" "redis_aof_*.aof.gz*"; then
      overall_status=1
    fi
  fi
  
  return ${overall_status}
}

# Check configuration backups
check_config_backups() {
  log "Checking configuration backups..."
  
  # Check config file backups
  if ! check_backup_freshness "config_files" "${CONFIG_BACKUP_DIR}" "config_files_*.tar.gz*"; then
    return 1
  fi
  
  # Check Kubernetes secrets backups if they exist
  if [ -n "$(find "${CONFIG_BACKUP_DIR}" -type f -name "kubernetes_secrets_*.yaml.gz*" | head -n1)" ]; then
    if ! check_backup_freshness "kubernetes_secrets" "${CONFIG_BACKUP_DIR}" "kubernetes_secrets_*.yaml.gz*"; then
      return 1
    fi
  fi
  
  # Check Vault secrets backups if they exist
  if [ -n "$(find "${CONFIG_BACKUP_DIR}" -type f -name "vault_secrets_*.json.gz*" | head -n1)" ]; then
    if ! check_backup_freshness "vault_secrets" "${CONFIG_BACKUP_DIR}" "vault_secrets_*.json.gz*"; then
      return 1
    fi
  fi
  
  return 0
}

# Check backup job logs
check_backup_logs() {
  log "Checking backup job logs..."
  local overall_status=0
  
  # Check PostgreSQL backup logs
  local postgres_log="/var/log/eden/postgres_backup.log"
  if [ -f "${postgres_log}" ]; then
    if grep -q "ERROR" "${postgres_log}" | tail -n 100; then
      send_notification "PostgreSQL Backup Errors" "Errors found in PostgreSQL backup log" "warning"
      echo "eden_backup_log_errors{type=\"postgres\"} 1" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
      overall_status=1
    else
      echo "eden_backup_log_errors{type=\"postgres\"} 0" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
    fi
  fi
  
  # Check Redis backup logs
  local redis_log="/var/log/eden/redis_backup.log"
  if [ -f "${redis_log}" ]; then
    if grep -q "ERROR" "${redis_log}" | tail -n 100; then
      send_notification "Redis Backup Errors" "Errors found in Redis backup log" "warning"
      echo "eden_backup_log_errors{type=\"redis\"} 1" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
      overall_status=1
    else
      echo "eden_backup_log_errors{type=\"redis\"} 0" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
    fi
  fi
  
  # Check configuration backup logs
  local config_log="/var/log/eden/config_backup.log"
  if [ -f "${config_log}" ]; then
    if grep -q "ERROR" "${config_log}" | tail -n 100; then
      send_notification "Configuration Backup Errors" "Errors found in configuration backup log" "warning"
      echo "eden_backup_log_errors{type=\"config\"} 1" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
      overall_status=1
    else
      echo "eden_backup_log_errors{type=\"config\"} 0" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
    fi
  fi
  
  return ${overall_status}
}

# Check backup disk space
check_disk_space() {
  log "Checking backup disk space..."
  
  # Get disk usage for backup directory
  local disk_usage=$(df -h "${BACKUP_DIR}" | tail -n 1)
  local usage_percent=$(echo "${disk_usage}" | awk '{print $5}' | tr -d '%')
  local available=$(echo "${disk_usage}" | awk '{print $4}')
  
  log "Backup disk usage: ${usage_percent}% (${available} available)"
  
  # Export metrics
  echo "eden_backup_disk_usage_percent ${usage_percent}" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
  
  # Check if disk usage is too high
  if [ ${usage_percent} -gt 90 ]; then
    send_notification "Backup Disk Space Critical" "Backup disk usage is ${usage_percent}% (${available} available)" "critical"
    return 1
  elif [ ${usage_percent} -gt 80 ]; then
    send_notification "Backup Disk Space Warning" "Backup disk usage is ${usage_percent}% (${available} available)" "warning"
  fi
  
  return 0
}

# Main monitoring process
main() {
  log "Starting backup monitoring process"
  
  # Initialize Prometheus metrics file
  echo "# HELP eden_backup_status Status of Eden backups (1=OK, 0=Failed)" > "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
  echo "# TYPE eden_backup_status gauge" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
  echo "# HELP eden_backup_age_seconds Age of Eden backups in seconds" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
  echo "# TYPE eden_backup_age_seconds gauge" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
  echo "# HELP eden_backup_size_bytes Size of Eden backups in bytes" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
  echo "# TYPE eden_backup_size_bytes gauge" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
  echo "# HELP eden_backup_log_errors Errors in Eden backup logs (1=Errors found, 0=No errors)" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
  echo "# TYPE eden_backup_log_errors gauge" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
  echo "# HELP eden_backup_disk_usage_percent Disk usage percentage for Eden backups" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
  echo "# TYPE eden_backup_disk_usage_percent gauge" >> "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$"
  
  local overall_status=0
  
  # Check PostgreSQL backups
  if ! check_postgres_backups; then
    overall_status=1
  fi
  
  # Check Redis backups
  if ! check_redis_backups; then
    overall_status=1
  fi
  
  # Check configuration backups
  if ! check_config_backups; then
    overall_status=1
  fi
  
  # Check backup logs
  if ! check_backup_logs; then
    overall_status=1
  fi
  
  # Check disk space
  if ! check_disk_space; then
    overall_status=1
  fi
  
  # Finalize Prometheus metrics file
  mv "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom.$$" "${PROMETHEUS_TEXTFILE_DIR}/eden_backup_status.prom"
  
  # Send overall status notification
  if [ ${overall_status} -eq 0 ]; then
    log "All backup checks passed"
    # Only send a notification if there was a previous failure
    if [ -f "/tmp/eden_backup_monitoring_failed" ]; then
      send_notification "Backup Monitoring Recovered" "All backup checks are now passing" "info"
      rm -f "/tmp/eden_backup_monitoring_failed"
    fi
  else
    log "Some backup checks failed"
    send_notification "Backup Monitoring Alert" "One or more backup checks failed. Check the monitoring log for details." "critical"
    touch "/tmp/eden_backup_monitoring_failed"
  fi
  
  log "Backup monitoring process completed"
  return ${overall_status}
}

# Execute main function
main