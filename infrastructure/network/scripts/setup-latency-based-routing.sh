#!/bin/bash
# Script to set up latency-based routing with AWS Global Accelerator
# This script implements the configuration defined in latency-based-routing.yaml

set -e

# Load configuration
CONFIG_FILE="${1:-../configs/latency-based-routing.yaml}"
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
ACCELERATOR_NAME=$(yq -r '.global.name' "$CONFIG_FILE")
FLOW_LOGS_ENABLED=$(yq -r '.global.flow_logs_enabled' "$CONFIG_FILE")
FLOW_LOGS_S3_BUCKET=$(yq -r '.global.flow_logs_s3_bucket' "$CONFIG_FILE")
FLOW_LOGS_S3_PREFIX=$(yq -r '.global.flow_logs_s3_prefix' "$CONFIG_FILE")

# Function to create Global Accelerator
create_accelerator() {
    local name=$1
    local enabled=$2
    local ip_address_type=$3
    local flow_logs_enabled=$4
    local flow_logs_s3_bucket=$5
    local flow_logs_s3_prefix=$6
    
    echo "Creating Global Accelerator: $name..."
    
    # Check if accelerator already exists
    existing_accelerator=$(aws globalaccelerator list-accelerators \
        --profile "$AWS_PROFILE" \
        --output json | \
        jq -r ".Accelerators[] | select(.Name == \"$name\") | .AcceleratorArn")
    
    if [ -n "$existing_accelerator" ]; then
        echo "Global Accelerator $name already exists with ARN: $existing_accelerator"
        echo "$existing_accelerator"
        return
    fi
    
    # Create flow logs config if enabled
    flow_logs_config=""
    if [ "$flow_logs_enabled" == "true" ]; then
        flow_logs_config=",\"FlowLogsEnabled\":true,\"FlowLogsS3Bucket\":\"$flow_logs_s3_bucket\",\"FlowLogsS3Prefix\":\"$flow_logs_s3_prefix\""
    fi
    
    # Create accelerator
    accelerator_arn=$(aws globalaccelerator create-accelerator \
        --profile "$AWS_PROFILE" \
        --name "$name" \
        --enabled $enabled \
        --ip-address-type "$ip_address_type" \
        --tags Key=Environment,Value=production Key=Service,Value=eden Key=ManagedBy,Value=terraform \
        --output json | \
        jq -r '.Accelerator.AcceleratorArn')
    
    echo "Created Global Accelerator with ARN: $accelerator_arn"
    echo "$accelerator_arn"
}

# Function to create listener
create_listener() {
    local accelerator_arn=$1
    local name=$2
    local protocol=$3
    local port_ranges=$4
    local client_affinity=$5
    
    echo "Creating listener: $name..."
    
    # Check if listener already exists
    existing_listeners=$(aws globalaccelerator list-listeners \
        --profile "$AWS_PROFILE" \
        --accelerator-arn "$accelerator_arn" \
        --output json | \
        jq -r '.Listeners[].ListenerArn')
    
    for listener_arn in $existing_listeners; do
        listener_name=$(aws globalaccelerator describe-listener \
            --profile "$AWS_PROFILE" \
            --listener-arn "$listener_arn" \
            --output json | \
            jq -r '.Listener.Name')
        
        if [ "$listener_name" == "$name" ]; then
            echo "Listener $name already exists with ARN: $listener_arn"
            echo "$listener_arn"
            return
        fi
    done
    
    # Create listener
    listener_arn=$(aws globalaccelerator create-listener \
        --profile "$AWS_PROFILE" \
        --accelerator-arn "$accelerator_arn" \
        --name "$name" \
        --protocol "$protocol" \
        --port-ranges "$port_ranges" \
        --client-affinity "$client_affinity" \
        --output json | \
        jq -r '.Listener.ListenerArn')
    
    echo "Created listener with ARN: $listener_arn"
    echo "$listener_arn"
}

# Function to create endpoint group
create_endpoint_group() {
    local listener_arn=$1
    local region=$2
    local health_check_port=$3
    local health_check_protocol=$4
    local health_check_path=$5
    local health_check_interval=$6
    local threshold_count=$7
    local traffic_dial_percentage=$8
    
    echo "Creating endpoint group for region: $region..."
    
    # Check if endpoint group already exists
    existing_endpoint_groups=$(aws globalaccelerator list-endpoint-groups \
        --profile "$AWS_PROFILE" \
        --listener-arn "$listener_arn" \
        --output json | \
        jq -r '.EndpointGroups[].EndpointGroupArn')
    
    for endpoint_group_arn in $existing_endpoint_groups; do
        endpoint_group_region=$(aws globalaccelerator describe-endpoint-group \
            --profile "$AWS_PROFILE" \
            --endpoint-group-arn "$endpoint_group_arn" \
            --output json | \
            jq -r '.EndpointGroup.EndpointGroupRegion')
        
        if [ "$endpoint_group_region" == "$region" ]; then
            echo "Endpoint group for region $region already exists with ARN: $endpoint_group_arn"
            echo "$endpoint_group_arn"
            return
        fi
    done
    
    # Create endpoint group
    endpoint_group_arn=$(aws globalaccelerator create-endpoint-group \
        --profile "$AWS_PROFILE" \
        --listener-arn "$listener_arn" \
        --endpoint-group-region "$region" \
        --health-check-port $health_check_port \
        --health-check-protocol "$health_check_protocol" \
        --health-check-path "$health_check_path" \
        --health-check-interval-seconds $health_check_interval \
        --threshold-count $threshold_count \
        --traffic-dial-percentage $traffic_dial_percentage \
        --output json | \
        jq -r '.EndpointGroup.EndpointGroupArn')
    
    echo "Created endpoint group with ARN: $endpoint_group_arn"
    echo "$endpoint_group_arn"
}

# Function to add endpoint to endpoint group
add_endpoint() {
    local endpoint_group_arn=$1
    local endpoint_id=$2
    local endpoint_type=$3
    local weight=$4
    local preserve_client_ip=$5
    local health_check_port=$6
    local health_check_path=$7
    
    echo "Adding endpoint $endpoint_id to endpoint group..."
    
    # Get current endpoints
    current_endpoints=$(aws globalaccelerator describe-endpoint-group \
        --profile "$AWS_PROFILE" \
        --endpoint-group-arn "$endpoint_group_arn" \
        --output json | \
        jq -r '.EndpointGroup.EndpointDescriptions[].EndpointId')
    
    # Check if endpoint already exists
    for current_endpoint in $current_endpoints; do
        if [ "$current_endpoint" == "$endpoint_id" ]; then
            echo "Endpoint $endpoint_id already exists in endpoint group"
            return
        fi
    done
    
    # Create endpoint configuration
    endpoint_config="{\"EndpointId\":\"$endpoint_id\",\"Weight\":$weight,\"ClientIPPreservationEnabled\":$preserve_client_ip"
    
    if [ -n "$health_check_port" ]; then
        endpoint_config="$endpoint_config,\"HealthCheckPort\":$health_check_port"
    fi
    
    if [ -n "$health_check_path" ]; then
        endpoint_config="$endpoint_config,\"HealthCheckPath\":\"$health_check_path\""
    fi
    
    endpoint_config="$endpoint_config}"
    
    # Add endpoint to endpoint group
    aws globalaccelerator update-endpoint-group \
        --profile "$AWS_PROFILE" \
        --endpoint-group-arn "$endpoint_group_arn" \
        --endpoint-configurations "$endpoint_config" \
        --output json
    
    echo "Added endpoint $endpoint_id to endpoint group"
}

# Function to get ALB ARN from name
get_alb_arn() {
    local alb_name=$1
    local region=$2
    
    aws elbv2 describe-load-balancers \
        --profile "$AWS_PROFILE" \
        --region "$region" \
        --names "$alb_name" \
        --output json | \
        jq -r '.LoadBalancers[0].LoadBalancerArn'
}

# Main execution

echo "Starting latency-based routing setup with AWS Global Accelerator..."

# Create Global Accelerator
ACCELERATOR_ENABLED=$(yq -r '.global.enabled' "$CONFIG_FILE")
IP_ADDRESS_TYPE=$(yq -r '.global.ip_address_type' "$CONFIG_FILE")

ACCELERATOR_ARN=$(create_accelerator \
    "$ACCELERATOR_NAME" \
    "$ACCELERATOR_ENABLED" \
    "$IP_ADDRESS_TYPE" \
    "$FLOW_LOGS_ENABLED" \
    "$FLOW_LOGS_S3_BUCKET" \
    "$FLOW_LOGS_S3_PREFIX")

# Wait for accelerator to be ready
echo "Waiting for accelerator to be ready..."
aws globalaccelerator wait accelerator-deployed \
    --profile "$AWS_PROFILE" \
    --accelerator-arn "$ACCELERATOR_ARN"

# Create listeners
LISTENERS=$(yq -r '.listeners[].name' "$CONFIG_FILE")

for listener_name in $LISTENERS; do
    echo "Processing listener: $listener_name"
    
    # Get listener configuration
    protocol=$(yq -r ".listeners[] | select(.name == \"$listener_name\") | .protocol" "$CONFIG_FILE")
    client_affinity=$(yq -r ".listeners[] | select(.name == \"$listener_name\") | .client_affinity" "$CONFIG_FILE")
    
    # Get port ranges
    port_ranges=$(yq -r ".listeners[] | select(.name == \"$listener_name\") | .port_ranges[] | {FromPort: .from_port, ToPort: .to_port}" "$CONFIG_FILE" | jq -s '.')
    
    # Create listener
    LISTENER_ARN=$(create_listener \
        "$ACCELERATOR_ARN" \
        "$listener_name" \
        "$protocol" \
        "$port_ranges" \
        "$client_affinity")
    
    # Create endpoint groups for this listener
    ENDPOINT_GROUPS=$(yq -r '.endpoint_groups[].name' "$CONFIG_FILE")
    
    for endpoint_group_name in $ENDPOINT_GROUPS; do
        echo "Processing endpoint group: $endpoint_group_name"
        
        # Get endpoint group configuration
        region=$(yq -r ".endpoint_groups[] | select(.name == \"$endpoint_group_name\") | .region" "$CONFIG_FILE")
        health_check_port=$(yq -r ".endpoint_groups[] | select(.name == \"$endpoint_group_name\") | .health_check_port" "$CONFIG_FILE")
        health_check_protocol=$(yq -r ".endpoint_groups[] | select(.name == \"$endpoint_group_name\") | .health_check_protocol" "$CONFIG_FILE")
        health_check_path=$(yq -r ".endpoint_groups[] | select(.name == \"$endpoint_group_name\") | .health_check_path" "$CONFIG_FILE")
        health_check_interval=$(yq -r ".endpoint_groups[] | select(.name == \"$endpoint_group_name\") | .health_check_interval_seconds" "$CONFIG_FILE")
        threshold_count=$(yq -r ".endpoint_groups[] | select(.name == \"$endpoint_group_name\") | .threshold_count" "$CONFIG_FILE")
        traffic_dial_percentage=$(yq -r ".endpoint_groups[] | select(.name == \"$endpoint_group_name\") | .traffic_dial_percentage" "$CONFIG_FILE")
        
        # Create endpoint group
        ENDPOINT_GROUP_ARN=$(create_endpoint_group \
            "$LISTENER_ARN" \
            "$region" \
            "$health_check_port" \
            "$health_check_protocol" \
            "$health_check_path" \
            "$health_check_interval" \
            "$threshold_count" \
            "$traffic_dial_percentage")
        
        # Add endpoints to this endpoint group
        ENDPOINTS=$(yq -r ".endpoint_groups[] | select(.name == \"$endpoint_group_name\") | .endpoints[].name" "$CONFIG_FILE")
        
        for endpoint_name in $ENDPOINTS; do
            echo "Processing endpoint: $endpoint_name"
            
            # Get endpoint configuration
            endpoint_id=$(yq -r ".endpoint_groups[] | select(.name == \"$endpoint_group_name\") | .endpoints[] | select(.name == \"$endpoint_name\") | .endpoint_id" "$CONFIG_FILE")
            endpoint_type=$(yq -r ".endpoint_groups[] | select(.name == \"$endpoint_group_name\") | .endpoints[] | select(.name == \"$endpoint_name\") | .endpoint_type" "$CONFIG_FILE")
            weight=$(yq -r ".endpoint_groups[] | select(.name == \"$endpoint_group_name\") | .endpoints[] | select(.name == \"$endpoint_name\") | .weight" "$CONFIG_FILE")
            preserve_client_ip=$(yq -r ".endpoint_groups[] | select(.name == \"$endpoint_group_name\") | .endpoints[] | select(.name == \"$endpoint_name\") | .preserve_client_ip" "$CONFIG_FILE")
            endpoint_health_check_port=$(yq -r ".endpoint_groups[] | select(.name == \"$endpoint_group_name\") | .endpoints[] | select(.name == \"$endpoint_name\") | .health_check_port" "$CONFIG_FILE")
            endpoint_health_check_path=$(yq -r ".endpoint_groups[] | select(.name == \"$endpoint_group_name\") | .endpoints[] | select(.name == \"$endpoint_name\") | .health_check_path" "$CONFIG_FILE")
            
            # If endpoint is ALB, get ARN
            if [ "$endpoint_type" == "ALB" ]; then
                endpoint_id=$(get_alb_arn "$endpoint_id" "$region")
            fi
            
            # Add endpoint to endpoint group
            add_endpoint \
                "$ENDPOINT_GROUP_ARN" \
                "$endpoint_id" \
                "$endpoint_type" \
                "$weight" \
                "$preserve_client_ip" \
                "$endpoint_health_check_port" \
                "$endpoint_health_check_path"
        done
    done
done

# Get Global Accelerator DNS name
ACCELERATOR_DNS=$(aws globalaccelerator describe-accelerator \
    --profile "$AWS_PROFILE" \
    --accelerator-arn "$ACCELERATOR_ARN" \
    --output json | \
    jq -r '.Accelerator.DnsName')

echo "Global Accelerator setup completed successfully!"
echo "Global Accelerator DNS name: $ACCELERATOR_DNS"

# Create DNS records for Global Accelerator
DOMAIN=$(yq -r '.dns.domain' "$CONFIG_FILE")
DNS_RECORDS=$(yq -r '.dns.records[].name' "$CONFIG_FILE")

echo "Creating DNS records for Global Accelerator..."

# Get hosted zone ID
HOSTED_ZONE_ID=$(aws route53 list-hosted-zones \
    --profile "$AWS_PROFILE" \
    --output json | \
    jq -r ".HostedZones[] | select(.Name == \"$DOMAIN.\") | .Id" | \
    sed 's/\/hostedzone\///')

if [ -z "$HOSTED_ZONE_ID" ]; then
    echo "Warning: Could not find hosted zone for domain $DOMAIN"
    echo "DNS records were not created. Please create them manually."
else
    for record_name in $DNS_RECORDS; do
        echo "Creating DNS record: $record_name"
        
        # Get record configuration
        record_type=$(yq -r ".dns.records[] | select(.name == \"$record_name\") | .type" "$CONFIG_FILE")
        record_ttl=$(yq -r ".dns.records[] | select(.name == \"$record_name\") | .ttl" "$CONFIG_FILE")
        record_alias=$(yq -r ".dns.records[] | select(.name == \"$record_name\") | .alias" "$CONFIG_FILE")
        
        # Create record
        if [ "$record_alias" == "true" ]; then
            # Create alias record
            aws route53 change-resource-record-sets \
                --profile "$AWS_PROFILE" \
                --hosted-zone-id "$HOSTED_ZONE_ID" \
                --change-batch "{
                    \"Changes\": [
                        {
                            \"Action\": \"UPSERT\",
                            \"ResourceRecordSet\": {
                                \"Name\": \"$record_name\",
                                \"Type\": \"$record_type\",
                                \"AliasTarget\": {
                                    \"HostedZoneId\": \"Z2BJ6XQ5FK7U4H\",
                                    \"DNSName\": \"$ACCELERATOR_DNS.\",
                                    \"EvaluateTargetHealth\": true
                                }
                            }
                        }
                    ]
                }"
        else
            # Create standard record
            aws route53 change-resource-record-sets \
                --profile "$AWS_PROFILE" \
                --hosted-zone-id "$HOSTED_ZONE_ID" \
                --change-batch "{
                    \"Changes\": [
                        {
                            \"Action\": \"UPSERT\",
                            \"ResourceRecordSet\": {
                                \"Name\": \"$record_name\",
                                \"Type\": \"$record_type\",
                                \"TTL\": $record_ttl,
                                \"ResourceRecords\": [
                                    {
                                        \"Value\": \"$ACCELERATOR_DNS\"
                                    }
                                ]
                            }
                        }
                    ]
                }"
        fi
        
        echo "Created DNS record: $record_name"
    done
fi

# Create monitoring script
cat > monitor-global-accelerator.sh << 'EOF'
#!/bin/bash
# Script to monitor AWS Global Accelerator performance

CONFIG_FILE="${1:-../configs/latency-based-routing.yaml}"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file $CONFIG_FILE not found"
    exit 1
fi

# AWS CLI profile to use
AWS_PROFILE="${2:-default}"

# Get accelerator name
ACCELERATOR_NAME=$(yq -r '.global.name' "$CONFIG_FILE")

# Get accelerator ARN
ACCELERATOR_ARN=$(aws globalaccelerator list-accelerators \
    --profile "$AWS_PROFILE" \
    --output json | \
    jq -r ".Accelerators[] | select(.Name == \"$ACCELERATOR_NAME\") | .AcceleratorArn")

if [ -z "$ACCELERATOR_ARN" ]; then
    echo "Error: Could not find Global Accelerator with name $ACCELERATOR_NAME"
    exit 1
fi

echo "=== Global Accelerator Status ==="
echo "Timestamp: $(date)"
echo

# Get accelerator details
accelerator=$(aws globalaccelerator describe-accelerator \
    --profile "$AWS_PROFILE" \
    --accelerator-arn "$ACCELERATOR_ARN" \
    --output json)

echo "Accelerator: $ACCELERATOR_NAME"
echo "  ARN: $ACCELERATOR_ARN"
echo "  Status: $(echo "$accelerator" | jq -r '.Accelerator.Status')"
echo "  DNS Name: $(echo "$accelerator" | jq -r '.Accelerator.DnsName')"
echo "  Enabled: $(echo "$accelerator" | jq -r '.Accelerator.Enabled')"
echo "  IP Addresses: $(echo "$accelerator" | jq -r '.Accelerator.IpSets[].IpAddresses[]' | tr '\n' ' ')"
echo

# Get listeners
listeners=$(aws globalaccelerator list-listeners \
    --profile "$AWS_PROFILE" \
    --accelerator-arn "$ACCELERATOR_ARN" \
    --output json | \
    jq -r '.Listeners[]')

echo "=== Listeners ==="
echo "$listeners" | jq -r '.ListenerArn + " | " + .Protocol + " | " + (.PortRanges | map(.FromPort|tostring + "-" + .ToPort|tostring) | join(", "))' | \
    while read line; do
        echo "  $line"
    done
echo

# Get endpoint groups and endpoints
listener_arns=$(echo "$listeners" | jq -r '.ListenerArn')

echo "=== Endpoint Groups and Endpoints ==="
for listener_arn in $listener_arns; do
    listener_name=$(aws globalaccelerator describe-listener \
        --profile "$AWS_PROFILE" \
        --listener-arn "$listener_arn" \
        --output json | \
        jq -r '.Listener.Name')
    
    echo "Listener: $listener_name"
    
    endpoint_groups=$(aws globalaccelerator list-endpoint-groups \
        --profile "$AWS_PROFILE" \
        --listener-arn "$listener_arn" \
        --output json | \
        jq -r '.EndpointGroups[]')
    
    echo "$endpoint_groups" | jq -r '.EndpointGroupRegion + " | Traffic Dial: " + (.TrafficDialPercentage|tostring) + "% | Health: " + (.HealthCheckProtocol + ":" + (.HealthCheckPort|tostring) + .HealthCheckPath)' | \
        while read line; do
            echo "  Region: $line"
            
            endpoint_group_arn=$(echo "$endpoint_groups" | jq -r "select(.EndpointGroupRegion == \"$(echo "$line" | cut -d' ' -f1)\") | .EndpointGroupArn")
            
            endpoints=$(aws globalaccelerator describe-endpoint-group \
                --profile "$AWS_PROFILE" \
                --endpoint-group-arn "$endpoint_group_arn" \
                --output json | \
                jq -r '.EndpointGroup.EndpointDescriptions[]')
            
            echo "$endpoints" | jq -r '.EndpointId + " | Weight: " + (.Weight|tostring) + " | Client IP Preserved: " + (.ClientIPPreservationEnabled|tostring) + " | Health: " + (.HealthState // "unknown")' | \
                while read endpoint; do
                    echo "    Endpoint: $endpoint"
                done
        done
    echo
done

echo "=== CloudWatch Metrics ==="
echo "For detailed Global Accelerator metrics, check CloudWatch dashboards."
echo "You can use the following AWS CLI command to get metrics:"
echo "aws cloudwatch get-metric-statistics --namespace \"AWS/GlobalAccelerator\" --metric-name \"ProcessedBytesIn\" --dimensions Name=Accelerator,Value=$ACCELERATOR_ARN --start-time <start-time> --end-time <end-time> --period 300 --statistics Sum"
EOF

chmod +x monitor-global-accelerator.sh

echo "Created monitoring script: monitor-global-accelerator.sh"