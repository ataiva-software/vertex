#!/bin/bash
# Automated Failover Script for Multi-Region Deployment
# This script monitors the health of regions and automatically initiates failover when needed

set -e

# Configuration
PRIMARY_REGION="us-east-1"
SECONDARY_REGION="us-west-2"
PRIMARY_CONTEXT="eks-us-east-1"
SECONDARY_CONTEXT="eks-us-west-2"
MONITORING_NAMESPACE="monitoring"
EDEN_NAMESPACE="eden"
PROMETHEUS_URL="http://prometheus-operator-kube-prometheus-prometheus.monitoring.svc.cluster.local:9090"
HEALTH_CHECK_INTERVAL=30  # seconds
FAILURE_THRESHOLD=3       # consecutive failures
MAX_REPLICATION_LAG_SECONDS=600
FAILOVER_COOLDOWN_MINUTES=15
TRAFFIC_SHIFT_PERCENTAGE=20
TRAFFIC_SHIFT_INTERVAL_SECONDS=30
LOG_FILE="/var/log/eden/failover.log"
SLACK_WEBHOOK_URL="https://hooks.slack.com/services/YOUR_SLACK_WEBHOOK_URL"
PAGERDUTY_SERVICE_KEY="YOUR_PAGERDUTY_SERVICE_KEY"

# Ensure log directory exists
mkdir -p "$(dirname "$LOG_FILE")"

# Function to log messages
log() {
    local message="$1"
    local timestamp=$(date +"%Y-%m-%d %H:%M:%S")
    echo "[$timestamp] $message" | tee -a "$LOG_FILE"
}

# Function to send Slack notification
send_slack_notification() {
    local message="$1"
    local severity="$2"  # info, warning, error
    local color="good"
    
    if [ "$severity" == "warning" ]; then
        color="warning"
    elif [ "$severity" == "error" ]; then
        color="danger"
    fi
    
    curl -s -X POST -H "Content-type: application/json" \
        --data "{
            \"attachments\": [
                {
                    \"color\": \"$color\",
                    \"title\": \"Eden Multi-Region Failover\",
                    \"text\": \"$message\",
                    \"footer\": \"Automated Failover System\",
                    \"ts\": $(date +%s)
                }
            ]
        }" \
        "$SLACK_WEBHOOK_URL"
}

# Function to send PagerDuty alert
send_pagerduty_alert() {
    local message="$1"
    local severity="$2"  # critical, error, warning, info
    
    curl -s -X POST -H "Content-type: application/json" \
        --data "{
            \"service_key\": \"$PAGERDUTY_SERVICE_KEY\",
            \"event_type\": \"trigger\",
            \"description\": \"$message\",
            \"client\": \"Eden Automated Failover\",
            \"client_url\": \"https://eden.example.com/status\",
            \"details\": {
                \"severity\": \"$severity\",
                \"timestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%SZ")\"
            }
        }" \
        "https://events.pagerduty.com/generic/2010-04-15/create_event.json"
}

# Function to check region health
check_region_health() {
    local context="$1"
    local region="$2"
    
    log "Checking health of region $region..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Check Kubernetes API server health
    if ! kubectl get nodes &>/dev/null; then
        log "ERROR: Kubernetes API server in region $region is not responding"
        return 1
    fi
    
    # Check critical services health
    local critical_services=("api-gateway" "vault-service" "hub-service" "flow-service" "task-service")
    local failed_services=()
    
    for service in "${critical_services[@]}"; do
        if ! kubectl get deployment "$service" -n "$EDEN_NAMESPACE" &>/dev/null; then
            log "WARNING: Service $service not found in region $region"
            failed_services+=("$service")
            continue
        fi
        
        local ready_replicas=$(kubectl get deployment "$service" -n "$EDEN_NAMESPACE" -o jsonpath='{.status.readyReplicas}')
        local desired_replicas=$(kubectl get deployment "$service" -n "$EDEN_NAMESPACE" -o jsonpath='{.spec.replicas}')
        
        if [ -z "$ready_replicas" ] || [ "$ready_replicas" -lt "$desired_replicas" ]; then
            log "WARNING: Service $service in region $region has $ready_replicas/$desired_replicas ready replicas"
            failed_services+=("$service")
        fi
    done
    
    # Check Prometheus health
    if ! kubectl get deployment prometheus-operator-kube-prometheus-prometheus -n "$MONITORING_NAMESPACE" &>/dev/null; then
        log "WARNING: Prometheus not found in region $region"
        failed_services+=("prometheus")
    fi
    
    # If any critical services failed, return failure
    if [ ${#failed_services[@]} -gt 0 ]; then
        log "ERROR: Region $region has ${#failed_services[@]} failed services: ${failed_services[*]}"
        return 1
    fi
    
    log "Region $region is healthy"
    return 0
}

# Function to check database replication health
check_database_replication() {
    local context="$1"
    local region="$2"
    
    log "Checking database replication health in region $region..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Check PostgreSQL replication lag
    local pg_lag=$(kubectl exec -n "$EDEN_NAMESPACE" deploy/postgres-exporter -- curl -s "$PROMETHEUS_URL/api/v1/query?query=pg_replication_lag_seconds" | jq -r '.data.result[0].value[1]')
    
    if [ -z "$pg_lag" ] || [ "$pg_lag" == "null" ]; then
        log "WARNING: Could not get PostgreSQL replication lag in region $region"
    elif [ "$(echo "$pg_lag > $MAX_REPLICATION_LAG_SECONDS" | bc -l)" -eq 1 ]; then
        log "ERROR: PostgreSQL replication lag in region $region is $pg_lag seconds, exceeding threshold of $MAX_REPLICATION_LAG_SECONDS seconds"
        return 1
    else
        log "PostgreSQL replication lag in region $region is $pg_lag seconds"
    fi
    
    # Check Redis replication lag
    local redis_lag=$(kubectl exec -n "$EDEN_NAMESPACE" deploy/redis-exporter -- curl -s "$PROMETHEUS_URL/api/v1/query?query=redis_replication_lag_seconds" | jq -r '.data.result[0].value[1]')
    
    if [ -z "$redis_lag" ] || [ "$redis_lag" == "null" ]; then
        log "WARNING: Could not get Redis replication lag in region $region"
    elif [ "$(echo "$redis_lag > 60" | bc -l)" -eq 1 ]; then
        log "ERROR: Redis replication lag in region $region is $redis_lag seconds, exceeding threshold of 60 seconds"
        return 1
    else
        log "Redis replication lag in region $region is $redis_lag seconds"
    fi
    
    return 0
}

# Function to check service error rates
check_service_error_rates() {
    local context="$1"
    local region="$2"
    local threshold=0.05  # 5% error rate threshold
    
    log "Checking service error rates in region $region..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Query Prometheus for error rates
    local error_rates=$(kubectl exec -n "$MONITORING_NAMESPACE" deploy/prometheus-operator-kube-prometheus-prometheus -- curl -s "$PROMETHEUS_URL/api/v1/query?query=sum%20by(service)%20(rate(http_requests_total%7Bjob%3D%22eden-services%22%2Cstatus_code%3D~%225..%22%7D%5B5m%5D))%20%2F%20sum%20by(service)%20(rate(http_requests_total%7Bjob%3D%22eden-services%22%7D%5B5m%5D))")
    
    # Parse error rates and check against threshold
    local high_error_services=()
    
    while read -r service error_rate; do
        if [ -z "$error_rate" ] || [ "$error_rate" == "null" ]; then
            continue
        fi
        
        if [ "$(echo "$error_rate > $threshold" | bc -l)" -eq 1 ]; then
            log "WARNING: Service $service in region $region has error rate $error_rate, exceeding threshold of $threshold"
            high_error_services+=("$service")
        fi
    done < <(echo "$error_rates" | jq -r '.data.result[] | "\(.metric.service) \(.value[1])"')
    
    # If any services have high error rates, return failure
    if [ ${#high_error_services[@]} -gt 0 ]; then
        log "ERROR: Region $region has ${#high_error_services[@]} services with high error rates: ${high_error_services[*]}"
        return 1
    fi
    
    log "All services in region $region have acceptable error rates"
    return 0
}

# Function to initiate failover from primary to secondary region
initiate_failover_to_secondary() {
    log "CRITICAL: Initiating failover from $PRIMARY_REGION to $SECONDARY_REGION..."
    
    # Send notifications
    send_slack_notification "🚨 CRITICAL: Initiating failover from $PRIMARY_REGION to $SECONDARY_REGION due to detected issues" "error"
    send_pagerduty_alert "Initiating failover from $PRIMARY_REGION to $SECONDARY_REGION due to detected issues" "critical"
    
    # Update Route 53 for global load balancing
    log "Updating Route 53 for global load balancing..."
    ./infrastructure/network/scripts/update-route53-weights.sh "$SECONDARY_REGION" 100 "$PRIMARY_REGION" 0
    
    # Update Global Accelerator endpoint weights
    log "Updating Global Accelerator endpoint weights..."
    ./infrastructure/network/scripts/update-global-accelerator-weights.sh "$SECONDARY_REGION" 100 "$PRIMARY_REGION" 0
    
    # Promote PostgreSQL in secondary region to primary
    log "Promoting PostgreSQL in $SECONDARY_REGION to primary..."
    kubectl config use-context "$SECONDARY_CONTEXT"
    kubectl exec -n "$EDEN_NAMESPACE" deploy/postgres-operator -- patronictl switchover --force
    
    # Promote Redis in secondary region to primary
    log "Promoting Redis in $SECONDARY_REGION to primary..."
    kubectl exec -n "$EDEN_NAMESPACE" statefulset/redis-sentinel -c sentinel -- redis-cli -p 26379 sentinel failover eden-master
    
    # Scale up services in secondary region
    log "Scaling up services in $SECONDARY_REGION..."
    kubectl scale deployment -n "$EDEN_NAMESPACE" --replicas=3 --all
    
    # Scale down write-heavy services in primary region
    log "Scaling down write-heavy services in $PRIMARY_REGION..."
    kubectl config use-context "$PRIMARY_CONTEXT"
    kubectl scale deployment -n "$EDEN_NAMESPACE" --replicas=1 task-service flow-service hub-service
    
    log "Failover to $SECONDARY_REGION completed"
    send_slack_notification "✅ Failover to $SECONDARY_REGION completed. $PRIMARY_REGION is now in reduced capacity mode." "warning"
}

# Function to initiate failback from secondary to primary region
initiate_failback_to_primary() {
    log "Initiating failback from $SECONDARY_REGION to $PRIMARY_REGION..."
    
    # Send notifications
    send_slack_notification "🔄 Initiating failback from $SECONDARY_REGION to $PRIMARY_REGION" "warning"
    
    # Check if primary region is healthy
    if ! check_region_health "$PRIMARY_CONTEXT" "$PRIMARY_REGION"; then
        log "ERROR: Primary region $PRIMARY_REGION is not healthy, aborting failback"
        send_slack_notification "❌ Failback aborted: Primary region $PRIMARY_REGION is not healthy" "error"
        return 1
    fi
    
    # Gradually shift traffic back to primary region
    log "Gradually shifting traffic back to $PRIMARY_REGION..."
    
    for weight in $(seq "$TRAFFIC_SHIFT_PERCENTAGE" "$TRAFFIC_SHIFT_PERCENTAGE" 100); do
        secondary_weight=$((100 - weight))
        
        log "Shifting traffic: $PRIMARY_REGION=$weight%, $SECONDARY_REGION=$secondary_weight%"
        
        # Update Route 53 for global load balancing
        ./infrastructure/network/scripts/update-route53-weights.sh "$PRIMARY_REGION" "$weight" "$SECONDARY_REGION" "$secondary_weight"
        
        # Update Global Accelerator endpoint weights
        ./infrastructure/network/scripts/update-global-accelerator-weights.sh "$PRIMARY_REGION" "$weight" "$SECONDARY_REGION" "$secondary_weight"
        
        # Wait for the specified interval
        sleep "$TRAFFIC_SHIFT_INTERVAL_SECONDS"
        
        # Check if primary region is still healthy
        if ! check_region_health "$PRIMARY_CONTEXT" "$PRIMARY_REGION"; then
            log "ERROR: Primary region $PRIMARY_REGION became unhealthy during failback, reverting to secondary region"
            ./infrastructure/network/scripts/update-route53-weights.sh "$SECONDARY_REGION" 100 "$PRIMARY_REGION" 0
            ./infrastructure/network/scripts/update-global-accelerator-weights.sh "$SECONDARY_REGION" 100 "$PRIMARY_REGION" 0
            send_slack_notification "❌ Failback aborted: Primary region $PRIMARY_REGION became unhealthy during traffic shift" "error"
            return 1
        fi
        
        # Check error rates in primary region
        if ! check_service_error_rates "$PRIMARY_CONTEXT" "$PRIMARY_REGION"; then
            log "ERROR: High error rates detected in primary region $PRIMARY_REGION during failback, reverting to secondary region"
            ./infrastructure/network/scripts/update-route53-weights.sh "$SECONDARY_REGION" 100 "$PRIMARY_REGION" 0
            ./infrastructure/network/scripts/update-global-accelerator-weights.sh "$SECONDARY_REGION" 100 "$PRIMARY_REGION" 0
            send_slack_notification "❌ Failback aborted: High error rates detected in primary region $PRIMARY_REGION" "error"
            return 1
        fi
    done
    
    # Promote PostgreSQL in primary region back to primary
    log "Promoting PostgreSQL in $PRIMARY_REGION back to primary..."
    kubectl config use-context "$PRIMARY_CONTEXT"
    kubectl exec -n "$EDEN_NAMESPACE" deploy/postgres-operator -- patronictl switchover --force
    
    # Promote Redis in primary region back to primary
    log "Promoting Redis in $PRIMARY_REGION back to primary..."
    kubectl exec -n "$EDEN_NAMESPACE" statefulset/redis-sentinel -c sentinel -- redis-cli -p 26379 sentinel failover eden-master
    
    # Scale up services in primary region
    log "Scaling up services in $PRIMARY_REGION..."
    kubectl scale deployment -n "$EDEN_NAMESPACE" --replicas=3 --all
    
    # Scale down services in secondary region to normal levels
    log "Scaling down services in $SECONDARY_REGION to normal levels..."
    kubectl config use-context "$SECONDARY_CONTEXT"
    kubectl scale deployment -n "$EDEN_NAMESPACE" --replicas=2 --all
    
    log "Failback to $PRIMARY_REGION completed"
    send_slack_notification "✅ Failback to $PRIMARY_REGION completed successfully. Normal operations resumed." "info"
    
    return 0
}

# Main monitoring loop
main() {
    log "Starting automated failover monitoring..."
    send_slack_notification "🚀 Automated failover monitoring started" "info"
    
    local primary_failure_count=0
    local secondary_failure_count=0
    local failover_active=false
    local last_failover_time=0
    
    while true; do
        log "=== Monitoring Cycle: $(date) ==="
        
        # Check primary region health
        if check_region_health "$PRIMARY_CONTEXT" "$PRIMARY_REGION" && \
           check_database_replication "$PRIMARY_CONTEXT" "$PRIMARY_REGION" && \
           check_service_error_rates "$PRIMARY_CONTEXT" "$PRIMARY_REGION"; then
            log "Primary region $PRIMARY_REGION is healthy"
            primary_failure_count=0
            
            # If we're in failover mode and primary is healthy for a while, consider failback
            if [ "$failover_active" = true ]; then
                current_time=$(date +%s)
                failover_duration_minutes=$(( (current_time - last_failover_time) / 60 ))
                
                if [ "$failover_duration_minutes" -ge "$FAILOVER_COOLDOWN_MINUTES" ]; then
                    log "Primary region $PRIMARY_REGION has been healthy for $failover_duration_minutes minutes, initiating failback"
                    if initiate_failback_to_primary; then
                        failover_active=false
                    fi
                else
                    log "Primary region $PRIMARY_REGION is healthy, but waiting for cooldown period ($failover_duration_minutes/$FAILOVER_COOLDOWN_MINUTES minutes)"
                fi
            fi
        else
            primary_failure_count=$((primary_failure_count + 1))
            log "Primary region $PRIMARY_REGION health check failed ($primary_failure_count/$FAILURE_THRESHOLD)"
            
            # If we're not already in failover mode and failure threshold is reached, initiate failover
            if [ "$failover_active" = false ] && [ "$primary_failure_count" -ge "$FAILURE_THRESHOLD" ]; then
                initiate_failover_to_secondary
                failover_active=true
                last_failover_time=$(date +%s)
                primary_failure_count=0
            fi
        fi
        
        # Check secondary region health (always monitor both regions)
        if check_region_health "$SECONDARY_CONTEXT" "$SECONDARY_REGION" && \
           check_database_replication "$SECONDARY_CONTEXT" "$SECONDARY_REGION" && \
           check_service_error_rates "$SECONDARY_CONTEXT" "$SECONDARY_REGION"; then
            log "Secondary region $SECONDARY_REGION is healthy"
            secondary_failure_count=0
        else
            secondary_failure_count=$((secondary_failure_count + 1))
            log "Secondary region $SECONDARY_REGION health check failed ($secondary_failure_count/$FAILURE_THRESHOLD)"
            
            # If both regions are unhealthy, this is a critical situation
            if [ "$primary_failure_count" -ge "$FAILURE_THRESHOLD" ] && [ "$secondary_failure_count" -ge "$FAILURE_THRESHOLD" ]; then
                log "CRITICAL: Both primary and secondary regions are unhealthy!"
                send_slack_notification "🚨 CRITICAL: Both primary and secondary regions are unhealthy! Manual intervention required!" "error"
                send_pagerduty_alert "CRITICAL: Both primary and secondary regions are unhealthy! Manual intervention required!" "critical"
            fi
        fi
        
        # Sleep before next check
        log "Sleeping for $HEALTH_CHECK_INTERVAL seconds..."
        sleep "$HEALTH_CHECK_INTERVAL"
    done
}

# Run the main function
main