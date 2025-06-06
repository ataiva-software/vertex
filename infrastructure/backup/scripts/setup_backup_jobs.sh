#!/bin/bash
#
# Setup Script for Eden DevOps Suite Backup Jobs
# This script makes all backup scripts executable and sets up cron jobs for automated backups

set -e

# Configuration
BACKUP_SCRIPTS_DIR="$(dirname "$(readlink -f "$0")")"
CRON_FILE="/etc/cron.d/eden-backups"
LOG_DIR="/var/log/eden"
BACKUP_USER="${BACKUP_USER:-root}"

# Log function
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# Make backup scripts executable
make_scripts_executable() {
  log "Making backup scripts executable..."
  chmod +x "${BACKUP_SCRIPTS_DIR}"/*.sh
  log "All scripts are now executable"
}

# Create log directory
create_log_directory() {
  log "Creating log directory..."
  mkdir -p "${LOG_DIR}"
  chmod 755 "${LOG_DIR}"
  log "Log directory created: ${LOG_DIR}"
}

# Setup cron jobs
setup_cron_jobs() {
  log "Setting up cron jobs..."
  
  # Create cron file
  cat > "${CRON_FILE}" << EOF
# Eden DevOps Suite Backup Jobs
# Created by setup_backup_jobs.sh on $(date)
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin
MAILTO=${NOTIFICATION_EMAIL:-root}

# PostgreSQL backups
0 1 * * * ${BACKUP_USER} ${BACKUP_SCRIPTS_DIR}/postgres_backup.sh >> ${LOG_DIR}/postgres_backup.log 2>&1

# Redis backups
0 3 * * * ${BACKUP_USER} ${BACKUP_SCRIPTS_DIR}/redis_backup.sh >> ${LOG_DIR}/redis_backup.log 2>&1

# Configuration backups
0 2 * * * ${BACKUP_USER} ${BACKUP_SCRIPTS_DIR}/config_backup.sh >> ${LOG_DIR}/config_backup.log 2>&1

# Backup verification
0 5 * * * ${BACKUP_USER} ${BACKUP_SCRIPTS_DIR}/verify_backups.sh >> ${LOG_DIR}/verify_backups.log 2>&1

# Backup monitoring
*/15 * * * * ${BACKUP_USER} ${BACKUP_SCRIPTS_DIR}/monitor_backups.sh >> ${LOG_DIR}/monitor_backups.log 2>&1

# Cleanup old logs (keep 30 days)
0 0 * * * ${BACKUP_USER} find ${LOG_DIR} -name "*.log" -type f -mtime +30 -delete
EOF

  # Set permissions on cron file
  chmod 644 "${CRON_FILE}"
  
  log "Cron jobs have been set up in ${CRON_FILE}"
}

# Setup systemd timers (alternative to cron)
setup_systemd_timers() {
  log "Setting up systemd timers..."
  
  # Create systemd service directory
  mkdir -p /etc/systemd/system
  
  # PostgreSQL backup service and timer
  cat > /etc/systemd/system/eden-postgres-backup.service << EOF
[Unit]
Description=Eden PostgreSQL Backup Service
After=network.target

[Service]
Type=oneshot
User=${BACKUP_USER}
ExecStart=${BACKUP_SCRIPTS_DIR}/postgres_backup.sh
StandardOutput=append:${LOG_DIR}/postgres_backup.log
StandardError=append:${LOG_DIR}/postgres_backup.log

[Install]
WantedBy=multi-user.target
EOF

  cat > /etc/systemd/system/eden-postgres-backup.timer << EOF
[Unit]
Description=Run Eden PostgreSQL Backup daily at 1:00 AM

[Timer]
OnCalendar=*-*-* 01:00:00
Persistent=true

[Install]
WantedBy=timers.target
EOF

  # Redis backup service and timer
  cat > /etc/systemd/system/eden-redis-backup.service << EOF
[Unit]
Description=Eden Redis Backup Service
After=network.target

[Service]
Type=oneshot
User=${BACKUP_USER}
ExecStart=${BACKUP_SCRIPTS_DIR}/redis_backup.sh
StandardOutput=append:${LOG_DIR}/redis_backup.log
StandardError=append:${LOG_DIR}/redis_backup.log

[Install]
WantedBy=multi-user.target
EOF

  cat > /etc/systemd/system/eden-redis-backup.timer << EOF
[Unit]
Description=Run Eden Redis Backup daily at 3:00 AM

[Timer]
OnCalendar=*-*-* 03:00:00
Persistent=true

[Install]
WantedBy=timers.target
EOF

  # Configuration backup service and timer
  cat > /etc/systemd/system/eden-config-backup.service << EOF
[Unit]
Description=Eden Configuration Backup Service
After=network.target

[Service]
Type=oneshot
User=${BACKUP_USER}
ExecStart=${BACKUP_SCRIPTS_DIR}/config_backup.sh
StandardOutput=append:${LOG_DIR}/config_backup.log
StandardError=append:${LOG_DIR}/config_backup.log

[Install]
WantedBy=multi-user.target
EOF

  cat > /etc/systemd/system/eden-config-backup.timer << EOF
[Unit]
Description=Run Eden Configuration Backup daily at 2:00 AM

[Timer]
OnCalendar=*-*-* 02:00:00
Persistent=true

[Install]
WantedBy=timers.target
EOF

  # Backup verification service and timer
  cat > /etc/systemd/system/eden-verify-backups.service << EOF
[Unit]
Description=Eden Backup Verification Service
After=network.target

[Service]
Type=oneshot
User=${BACKUP_USER}
ExecStart=${BACKUP_SCRIPTS_DIR}/verify_backups.sh
StandardOutput=append:${LOG_DIR}/verify_backups.log
StandardError=append:${LOG_DIR}/verify_backups.log

[Install]
WantedBy=multi-user.target
EOF

  cat > /etc/systemd/system/eden-verify-backups.timer << EOF
[Unit]
Description=Run Eden Backup Verification daily at 5:00 AM

[Timer]
OnCalendar=*-*-* 05:00:00
Persistent=true

[Install]
WantedBy=timers.target
EOF

  # Backup monitoring service and timer
  cat > /etc/systemd/system/eden-monitor-backups.service << EOF
[Unit]
Description=Eden Backup Monitoring Service
After=network.target

[Service]
Type=oneshot
User=${BACKUP_USER}
ExecStart=${BACKUP_SCRIPTS_DIR}/monitor_backups.sh
StandardOutput=append:${LOG_DIR}/monitor_backups.log
StandardError=append:${LOG_DIR}/monitor_backups.log

[Install]
WantedBy=multi-user.target
EOF

  cat > /etc/systemd/system/eden-monitor-backups.timer << EOF
[Unit]
Description=Run Eden Backup Monitoring every 15 minutes

[Timer]
OnBootSec=5min
OnUnitActiveSec=15min
Persistent=true

[Install]
WantedBy=timers.target
EOF

  # Reload systemd and enable timers
  systemctl daemon-reload
  
  for service in postgres redis config verify-backups monitor-backups; do
    systemctl enable eden-${service}-backup.timer
    systemctl start eden-${service}-backup.timer
    log "Enabled and started eden-${service}-backup.timer"
  done
  
  log "Systemd timers have been set up and enabled"
}

# Setup Kubernetes CronJobs (for Kubernetes deployments)
setup_kubernetes_cronjobs() {
  log "Setting up Kubernetes CronJobs..."
  
  # Create Kubernetes namespace if it doesn't exist
  kubectl get namespace eden-backup &>/dev/null || kubectl create namespace eden-backup
  
  # Create ConfigMap for scripts
  kubectl -n eden-backup create configmap backup-scripts \
    --from-file="${BACKUP_SCRIPTS_DIR}/postgres_backup.sh" \
    --from-file="${BACKUP_SCRIPTS_DIR}/redis_backup.sh" \
    --from-file="${BACKUP_SCRIPTS_DIR}/config_backup.sh" \
    --from-file="${BACKUP_SCRIPTS_DIR}/verify_backups.sh" \
    --from-file="${BACKUP_SCRIPTS_DIR}/monitor_backups.sh" \
    --dry-run=client -o yaml | kubectl apply -f -
  
  # Create PostgreSQL backup CronJob
  cat > /tmp/postgres-backup-cronjob.yaml << EOF
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
  namespace: eden-backup
spec:
  schedule: "0 1 * * *"
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:14
            command:
            - /bin/bash
            - /scripts/postgres_backup.sh
            volumeMounts:
            - name: backup-scripts
              mountPath: /scripts
            - name: backup-volume
              mountPath: /var/backups/eden
            - name: log-volume
              mountPath: /var/log/eden
            env:
            - name: POSTGRES_HOST
              value: postgres.eden.svc.cluster.local
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef:
                  name: postgres-credentials
                  key: username
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-credentials
                  key: password
          restartPolicy: OnFailure
          volumes:
          - name: backup-scripts
            configMap:
              name: backup-scripts
              defaultMode: 0755
          - name: backup-volume
            persistentVolumeClaim:
              claimName: eden-backup-pvc
          - name: log-volume
            persistentVolumeClaim:
              claimName: eden-backup-logs-pvc
EOF

  # Create Redis backup CronJob
  cat > /tmp/redis-backup-cronjob.yaml << EOF
apiVersion: batch/v1
kind: CronJob
metadata:
  name: redis-backup
  namespace: eden-backup
spec:
  schedule: "0 3 * * *"
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: redis:7
            command:
            - /bin/bash
            - /scripts/redis_backup.sh
            volumeMounts:
            - name: backup-scripts
              mountPath: /scripts
            - name: backup-volume
              mountPath: /var/backups/eden
            - name: log-volume
              mountPath: /var/log/eden
            env:
            - name: REDIS_HOST
              value: redis.eden.svc.cluster.local
            - name: REDIS_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: redis-credentials
                  key: password
          restartPolicy: OnFailure
          volumes:
          - name: backup-scripts
            configMap:
              name: backup-scripts
              defaultMode: 0755
          - name: backup-volume
            persistentVolumeClaim:
              claimName: eden-backup-pvc
          - name: log-volume
            persistentVolumeClaim:
              claimName: eden-backup-logs-pvc
EOF

  # Create Config backup CronJob
  cat > /tmp/config-backup-cronjob.yaml << EOF
apiVersion: batch/v1
kind: CronJob
metadata:
  name: config-backup
  namespace: eden-backup
spec:
  schedule: "0 2 * * *"
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: alpine:3.16
            command:
            - /bin/sh
            - /scripts/config_backup.sh
            volumeMounts:
            - name: backup-scripts
              mountPath: /scripts
            - name: backup-volume
              mountPath: /var/backups/eden
            - name: log-volume
              mountPath: /var/log/eden
            - name: config-volume
              mountPath: /etc/eden
              readOnly: true
          restartPolicy: OnFailure
          volumes:
          - name: backup-scripts
            configMap:
              name: backup-scripts
              defaultMode: 0755
          - name: backup-volume
            persistentVolumeClaim:
              claimName: eden-backup-pvc
          - name: log-volume
            persistentVolumeClaim:
              claimName: eden-backup-logs-pvc
          - name: config-volume
            configMap:
              name: eden-config
EOF

  # Create Backup Verification CronJob
  cat > /tmp/verify-backups-cronjob.yaml << EOF
apiVersion: batch/v1
kind: CronJob
metadata:
  name: verify-backups
  namespace: eden-backup
spec:
  schedule: "0 5 * * *"
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: verify
            image: postgres:14
            command:
            - /bin/bash
            - /scripts/verify_backups.sh
            volumeMounts:
            - name: backup-scripts
              mountPath: /scripts
            - name: backup-volume
              mountPath: /var/backups/eden
            - name: log-volume
              mountPath: /var/log/eden
          restartPolicy: OnFailure
          volumes:
          - name: backup-scripts
            configMap:
              name: backup-scripts
              defaultMode: 0755
          - name: backup-volume
            persistentVolumeClaim:
              claimName: eden-backup-pvc
          - name: log-volume
            persistentVolumeClaim:
              claimName: eden-backup-logs-pvc
EOF

  # Create Backup Monitoring CronJob
  cat > /tmp/monitor-backups-cronjob.yaml << EOF
apiVersion: batch/v1
kind: CronJob
metadata:
  name: monitor-backups
  namespace: eden-backup
spec:
  schedule: "*/15 * * * *"
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: monitor
            image: alpine:3.16
            command:
            - /bin/sh
            - /scripts/monitor_backups.sh
            volumeMounts:
            - name: backup-scripts
              mountPath: /scripts
            - name: backup-volume
              mountPath: /var/backups/eden
            - name: log-volume
              mountPath: /var/log/eden
            - name: prometheus-volume
              mountPath: /var/lib/prometheus/node-exporter
          restartPolicy: OnFailure
          volumes:
          - name: backup-scripts
            configMap:
              name: backup-scripts
              defaultMode: 0755
          - name: backup-volume
            persistentVolumeClaim:
              claimName: eden-backup-pvc
          - name: log-volume
            persistentVolumeClaim:
              claimName: eden-backup-logs-pvc
          - name: prometheus-volume
            persistentVolumeClaim:
              claimName: prometheus-data-pvc
EOF

  # Apply CronJob configurations
  kubectl apply -f /tmp/postgres-backup-cronjob.yaml
  kubectl apply -f /tmp/redis-backup-cronjob.yaml
  kubectl apply -f /tmp/config-backup-cronjob.yaml
  kubectl apply -f /tmp/verify-backups-cronjob.yaml
  kubectl apply -f /tmp/monitor-backups-cronjob.yaml
  
  # Clean up temporary files
  rm -f /tmp/*-backup-cronjob.yaml
  
  log "Kubernetes CronJobs have been set up in the eden-backup namespace"
}

# Main function
main() {
  log "Starting setup of Eden DevOps Suite backup jobs"
  
  # Make scripts executable
  make_scripts_executable
  
  # Create log directory
  create_log_directory
  
  # Determine the environment and set up appropriate scheduling
  if [ -n "${KUBERNETES_SERVICE_HOST}" ] || kubectl get nodes &>/dev/null; then
    log "Kubernetes environment detected"
    setup_kubernetes_cronjobs
  elif command -v systemctl &>/dev/null; then
    log "Systemd environment detected"
    setup_systemd_timers
  else
    log "Standard environment detected"
    setup_cron_jobs
  fi
  
  log "Setup completed successfully"
}

# Execute main function
main