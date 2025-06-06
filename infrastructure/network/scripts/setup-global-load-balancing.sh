#!/bin/bash
# Script to set up global load balancing with AWS Route 53
# This script implements the configuration defined in global-load-balancing.yaml

set -e

# Load configuration
CONFIG_FILE="${1:-../configs/global-load-balancing.yaml}"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file $CONFIG_FILE not found"
    exit 1
fi

# AWS CLI profile to use
AWS_PROFILE="${2:-default}"

# Function to parse YAML (simplified)
parse_yaml() {
    local yaml_file=$1
    cat "$yaml_file" | grep -v '^#' | sed -e 's/:[^:\/\/]/="/g;s/$/"/g;s/ *=/=/g'
}

# Global settings
DOMAIN=$(yq -r '.global.domain' "$CONFIG_FILE")
TTL=$(yq -r '.global.ttl_seconds' "$CONFIG_FILE")
HEALTH_CHECK_INTERVAL=$(yq -r '.global.health_check.interval_seconds' "$CONFIG_FILE")
HEALTH_CHECK_FAILURE_THRESHOLD=$(yq -r '.global.health_check.failure_threshold' "$CONFIG_FILE")
HEALTH_CHECK_PATH=$(yq -r '.global.health_check.path' "$CONFIG_FILE")
HEALTH_CHECK_PORT=$(yq -r '.global.health_check.port' "$CONFIG_FILE")
HEALTH_CHECK_PROTOCOL=$(yq -r '.global.health_check.protocol' "$CONFIG_FILE")

# Get hosted zone ID
get_hosted_zone_id() {
    local domain=$1
    
    echo "Getting hosted zone ID for domain $domain..."
    
    aws route53 list-hosted-zones --profile "$AWS_PROFILE" --output json | \
        jq -r '.HostedZones[] | select(.Name == "'"$domain"'.") | .Id' | \
        sed 's/\/hostedzone\///'
}

# Create health check
create_health_check() {
    local name=$1
    local endpoint=$2
    local path=$3
    local port=$4
    local protocol=$5
    local interval=$6
    local failure_threshold=$7
    
    echo "Creating health check for $name at $endpoint..."
    
    local health_check_id=$(aws route53 create-health-check \
        --profile "$AWS_PROFILE" \
        --caller-reference "eden-$name-$(date +%s)" \
        --health-check-config "{
            \"FullyQualifiedDomainName\": \"$endpoint\",
            \"Port\": $port,
            \"Type\": \"$protocol\",
            \"ResourcePath\": \"$path\",
            \"RequestInterval\": $interval,
            \"FailureThreshold\": $failure_threshold,
            \"MeasureLatency\": true
        }" \
        --output json | jq -r '.HealthCheck.Id')
    
    # Tag the health check
    aws route53 change-tags-for-resource \
        --profile "$AWS_PROFILE" \
        --resource-type healthcheck \
        --resource-id "$health_check_id" \
        --add-tags Key=Name,Value="$name" Key=Environment,Value=production Key=Service,Value=eden
    
    echo "Created health check with ID: $health_check_id"
    echo "$health_check_id"
}

# Create latency-based routing record
create_latency_record() {
    local hosted_zone_id=$1
    local record_name=$2
    local record_type=$3
    local ttl=$4
    local region_1=$5
    local endpoint_1=$6
    local health_check_id_1=$7
    local region_2=$8
    local endpoint_2=$9
    local health_check_id_2=${10}
    
    echo "Creating latency-based routing record for $record_name..."
    
    aws route53 change-resource-record-sets \
        --profile "$AWS_PROFILE" \
        --hosted-zone-id "$hosted_zone_id" \
        --change-batch "{
            \"Changes\": [
                {
                    \"Action\": \"UPSERT\",
                    \"ResourceRecordSet\": {
                        \"Name\": \"$record_name\",
                        \"Type\": \"$record_type\",
                        \"SetIdentifier\": \"$region_1\",
                        \"Region\": \"$region_1\",
                        \"TTL\": $ttl,
                        \"ResourceRecords\": [
                            {
                                \"Value\": \"$endpoint_1\"
                            }
                        ],
                        \"HealthCheckId\": \"$health_check_id_1\"
                    }
                },
                {
                    \"Action\": \"UPSERT\",
                    \"ResourceRecordSet\": {
                        \"Name\": \"$record_name\",
                        \"Type\": \"$record_type\",
                        \"SetIdentifier\": \"$region_2\",
                        \"Region\": \"$region_2\",
                        \"TTL\": $ttl,
                        \"ResourceRecords\": [
                            {
                                \"Value\": \"$endpoint_2\"
                            }
                        ],
                        \"HealthCheckId\": \"$health_check_id_2\"
                    }
                }
            ]
        }"
    
    echo "Created latency-based routing record for $record_name"
}

# Create failover routing record
create_failover_record() {
    local hosted_zone_id=$1
    local record_name=$2
    local record_type=$3
    local ttl=$4
    local primary_endpoint=$5
    local primary_health_check_id=$6
    local secondary_endpoint=$7
    local secondary_health_check_id=$8
    
    echo "Creating failover routing record for $record_name..."
    
    aws route53 change-resource-record-sets \
        --profile "$AWS_PROFILE" \
        --hosted-zone-id "$hosted_zone_id" \
        --change-batch "{
            \"Changes\": [
                {
                    \"Action\": \"UPSERT\",
                    \"ResourceRecordSet\": {
                        \"Name\": \"$record_name\",
                        \"Type\": \"$record_type\",
                        \"SetIdentifier\": \"Primary\",
                        \"Failover\": \"PRIMARY\",
                        \"TTL\": $ttl,
                        \"ResourceRecords\": [
                            {
                                \"Value\": \"$primary_endpoint\"
                            }
                        ],
                        \"HealthCheckId\": \"$primary_health_check_id\"
                    }
                },
                {
                    \"Action\": \"UPSERT\",
                    \"ResourceRecordSet\": {
                        \"Name\": \"$record_name\",
                        \"Type\": \"$record_type\",
                        \"SetIdentifier\": \"Secondary\",
                        \"Failover\": \"SECONDARY\",
                        \"TTL\": $ttl,
                        \"ResourceRecords\": [
                            {
                                \"Value\": \"$secondary_endpoint\"
                            }
                        ],
                        \"HealthCheckId\": \"$secondary_health_check_id\"
                    }
                }
            ]
        }"
    
    echo "Created failover routing record for $record_name"
}

# Main execution

echo "Starting global load balancing setup with AWS Route 53..."

# Get hosted zone ID
HOSTED_ZONE_ID=$(get_hosted_zone_id "$DOMAIN")
if [ -z "$HOSTED_ZONE_ID" ]; then
    echo "Error: Could not find hosted zone for domain $DOMAIN"
    exit 1
fi

echo "Using hosted zone ID: $HOSTED_ZONE_ID"

# Get region information
REGION_1_NAME=$(yq -r '.regions[0].name' "$CONFIG_FILE")
REGION_2_NAME=$(yq -r '.regions[1].name' "$CONFIG_FILE")

echo "Setting up global load balancing between regions: $REGION_1_NAME and $REGION_2_NAME"

# Create health checks for each endpoint
declare -A HEALTH_CHECK_IDS

# Get all DNS records
DNS_RECORDS=$(yq -r '.dns_records[].name' "$CONFIG_FILE")

for record in $DNS_RECORDS; do
    echo "Processing DNS record: $record"
    
    # Get routing policy
    routing_policy=$(yq -r ".dns_records[] | select(.name == \"$record\") | .routing_policy" "$CONFIG_FILE")
    
    # Get endpoints for this record
    endpoints=$(yq -r ".dns_records[] | select(.name == \"$record\") | .endpoints[].target" "$CONFIG_FILE")
    regions=$(yq -r ".dns_records[] | select(.name == \"$record\") | .endpoints[].region" "$CONFIG_FILE")
    
    # Create health checks for each endpoint
    endpoint_1=$(echo "$endpoints" | head -n 1)
    region_1=$(echo "$regions" | head -n 1)
    endpoint_2=$(echo "$endpoints" | tail -n 1)
    region_2=$(echo "$regions" | tail -n 1)
    
    # Get endpoint hostnames
    endpoint_1_hostname=$(yq -r ".regions[] | select(.name == \"$region_1\") | .endpoints.$endpoint_1.hostname" "$CONFIG_FILE")
    endpoint_2_hostname=$(yq -r ".regions[] | select(.name == \"$region_2\") | .endpoints.$endpoint_2.hostname" "$CONFIG_FILE")
    
    # Get endpoint health check paths
    endpoint_1_path=$(yq -r ".regions[] | select(.name == \"$region_1\") | .endpoints.$endpoint_1.health_check_path" "$CONFIG_FILE")
    endpoint_2_path=$(yq -r ".regions[] | select(.name == \"$region_2\") | .endpoints.$endpoint_2.health_check_path" "$CONFIG_FILE")
    
    # Get endpoint ALB DNS names
    endpoint_1_alb=$(yq -r ".regions[] | select(.name == \"$region_1\") | .endpoints.$endpoint_1.alb_dns_name" "$CONFIG_FILE")
    endpoint_2_alb=$(yq -r ".regions[] | select(.name == \"$region_2\") | .endpoints.$endpoint_2.alb_dns_name" "$CONFIG_FILE")
    
    # Create health checks if they don't exist yet
    health_check_key_1="${region_1}_${endpoint_1}"
    health_check_key_2="${region_2}_${endpoint_2}"
    
    if [ -z "${HEALTH_CHECK_IDS[$health_check_key_1]}" ]; then
        HEALTH_CHECK_IDS[$health_check_key_1]=$(create_health_check \
            "${endpoint_1}-${region_1}" \
            "$endpoint_1_hostname" \
            "${endpoint_1_path:-$HEALTH_CHECK_PATH}" \
            "$HEALTH_CHECK_PORT" \
            "$HEALTH_CHECK_PROTOCOL" \
            "$HEALTH_CHECK_INTERVAL" \
            "$HEALTH_CHECK_FAILURE_THRESHOLD")
    fi
    
    if [ -z "${HEALTH_CHECK_IDS[$health_check_key_2]}" ]; then
        HEALTH_CHECK_IDS[$health_check_key_2]=$(create_health_check \
            "${endpoint_2}-${region_2}" \
            "$endpoint_2_hostname" \
            "${endpoint_2_path:-$HEALTH_CHECK_PATH}" \
            "$HEALTH_CHECK_PORT" \
            "$HEALTH_CHECK_PROTOCOL" \
            "$HEALTH_CHECK_INTERVAL" \
            "$HEALTH_CHECK_FAILURE_THRESHOLD")
    fi
    
    # Create DNS record based on routing policy
    if [ "$routing_policy" == "latency" ]; then
        create_latency_record \
            "$HOSTED_ZONE_ID" \
            "$record" \
            "CNAME" \
            "$TTL" \
            "$region_1" \
            "$endpoint_1_alb" \
            "${HEALTH_CHECK_IDS[$health_check_key_1]}" \
            "$region_2" \
            "$endpoint_2_alb" \
            "${HEALTH_CHECK_IDS[$health_check_key_2]}"
    elif [ "$routing_policy" == "failover" ]; then
        create_failover_record \
            "$HOSTED_ZONE_ID" \
            "$record" \
            "CNAME" \
            "$TTL" \
            "$endpoint_1_alb" \
            "${HEALTH_CHECK_IDS[$health_check_key_1]}" \
            "$endpoint_2_alb" \
            "${HEALTH_CHECK_IDS[$health_check_key_2]}"
    else
        echo "Warning: Unsupported routing policy '$routing_policy' for record $record"
    fi
done

echo "Global load balancing setup completed successfully!"

# Create monitoring script
cat > monitor-global-load-balancing.sh << 'EOF'
#!/bin/bash
# Script to monitor global load balancing health and traffic distribution

CONFIG_FILE="${1:-../configs/global-load-balancing.yaml}"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file $CONFIG_FILE not found"
    exit 1
fi

# AWS CLI profile to use
AWS_PROFILE="${2:-default}"

# Get all health checks
echo "=== Health Check Status ==="
echo "Timestamp: $(date)"
echo

health_checks=$(aws route53 list-health-checks --profile "$AWS_PROFILE" --output json | \
    jq -r '.HealthChecks[] | select(.HealthCheckConfig.FullyQualifiedDomainName | contains("eden.example.com")) | .Id')

for health_check_id in $health_checks; do
    health_check=$(aws route53 get-health-check --profile "$AWS_PROFILE" --health-check-id "$health_check_id" --output json)
    endpoint=$(echo "$health_check" | jq -r '.HealthCheck.HealthCheckConfig.FullyQualifiedDomainName')
    path=$(echo "$health_check" | jq -r '.HealthCheck.HealthCheckConfig.ResourcePath')
    
    # Get health check status
    status=$(aws route53 get-health-check-status --profile "$AWS_PROFILE" --health-check-id "$health_check_id" --output json | \
        jq -r '.HealthCheckObservations[0].StatusReport.Status')
    
    # Get health check tags
    tags=$(aws route53 list-tags-for-resource --profile "$AWS_PROFILE" --resource-type healthcheck --resource-id "$health_check_id" --output json | \
        jq -r '.ResourceTagSet.Tags[] | select(.Key=="Name") | .Value')
    
    echo "Health Check: $tags"
    echo "  ID: $health_check_id"
    echo "  Endpoint: $endpoint"
    echo "  Path: $path"
    echo "  Status: $status"
    echo
done

# Get traffic distribution
echo "=== Traffic Distribution ==="

# This would typically be done by analyzing CloudWatch metrics or logs
# For demonstration purposes, we'll just show how to get the DNS records

dns_records=$(yq -r '.dns_records[].name' "$CONFIG_FILE")

for record in $dns_records; do
    echo "Record: $record"
    
    # Get hosted zone ID
    hosted_zone_id=$(aws route53 list-hosted-zones --profile "$AWS_PROFILE" --output json | \
        jq -r '.HostedZones[] | select(.Name | contains("eden.example.com")) | .Id' | \
        sed 's/\/hostedzone\///')
    
    # Get record sets
    record_sets=$(aws route53 list-resource-record-sets --profile "$AWS_PROFILE" --hosted-zone-id "$hosted_zone_id" --output json | \
        jq -r '.ResourceRecordSets[] | select(.Name == "'"$record"'.")')
    
    echo "$record_sets" | jq '.'
    echo
done

echo "=== CloudWatch Metrics ==="
echo "For detailed traffic distribution metrics, check CloudWatch dashboards."
echo "You can use the following AWS CLI command to get metrics:"
echo "aws cloudwatch get-metric-statistics --namespace \"AWS/Route53\" --metric-name \"HealthCheckPercentageHealthy\" --dimensions Name=HealthCheckId,Value=<health-check-id> --start-time <start-time> --end-time <end-time> --period 300 --statistics Average"
EOF

chmod +x monitor-global-load-balancing.sh

echo "Created monitoring script: monitor-global-load-balancing.sh"