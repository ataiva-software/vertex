#!/bin/bash
# Script to set up active-active Redis Enterprise replication between regions
# This script implements the configuration defined in redis-multi-region.yaml

set -e

# Load configuration
CONFIG_FILE="${1:-../configs/redis-multi-region.yaml}"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file $CONFIG_FILE not found"
    exit 1
fi

# Function to parse YAML (simplified)
parse_yaml() {
    local yaml_file=$1
    cat "$yaml_file" | grep -v '^#' | sed -e 's/:[^:\/\/]/="/g;s/$/"/g;s/ *=/=/g'
}

# Load secrets from environment or secret manager
REDIS_PASSWORD=$(aws secretsmanager get-secret-value --secret-id $(yq -r '.regions[0].instance.password_secret' "$CONFIG_FILE") --query SecretString --output text)
ADMIN_PASSWORD=$(aws secretsmanager get-secret-value --secret-id $(yq -r '.regions[0].enterprise.admin_password_secret' "$CONFIG_FILE") --query SecretString --output text)
LICENSE_FILE=$(aws secretsmanager get-secret-value --secret-id $(yq -r '.regions[0].enterprise.license_file_secret' "$CONFIG_FILE") --query SecretString --output text)

# Region information
REGION_1_NAME=$(yq -r '.regions[0].name' "$CONFIG_FILE")
REGION_1_HOST=$(yq -r '.regions[0].instance.host' "$CONFIG_FILE")
REGION_1_PORT=$(yq -r '.regions[0].instance.port' "$CONFIG_FILE")
REGION_1_ADMIN_USER=$(yq -r '.regions[0].enterprise.admin_user' "$CONFIG_FILE")
REGION_1_CLUSTER_NAME=$(yq -r '.regions[0].enterprise.cluster_name' "$CONFIG_FILE")
REGION_1_NODES=$(yq -r '.regions[0].enterprise.nodes[].host' "$CONFIG_FILE")
REGION_1_NODE_1=$(echo "$REGION_1_NODES" | head -n 1)
REGION_1_NODE_PORT=$(yq -r '.regions[0].enterprise.nodes[0].port' "$CONFIG_FILE")

REGION_2_NAME=$(yq -r '.regions[1].name' "$CONFIG_FILE")
REGION_2_HOST=$(yq -r '.regions[1].instance.host' "$CONFIG_FILE")
REGION_2_PORT=$(yq -r '.regions[1].instance.port' "$CONFIG_FILE")
REGION_2_ADMIN_USER=$(yq -r '.regions[1].enterprise.admin_user' "$CONFIG_FILE")
REGION_2_CLUSTER_NAME=$(yq -r '.regions[1].enterprise.cluster_name' "$CONFIG_FILE")
REGION_2_NODES=$(yq -r '.regions[1].enterprise.nodes[].host' "$CONFIG_FILE")
REGION_2_NODE_1=$(echo "$REGION_2_NODES" | head -n 1)
REGION_2_NODE_PORT=$(yq -r '.regions[1].enterprise.nodes[0].port' "$CONFIG_FILE")

# Function to execute Redis CLI command
execute_redis_cli() {
    local host=$1
    local port=$2
    local password=$3
    local command=$4
    
    redis-cli -h "$host" -p "$port" -a "$password" $command
}

# Function to execute Redis Enterprise REST API call
execute_rest_api() {
    local host=$1
    local port=$2
    local user=$3
    local password=$4
    local method=$5
    local endpoint=$6
    local data=$7
    
    if [ -z "$data" ]; then
        curl -k -s -u "$user:$password" -X "$method" "https://$host:$port/v1/$endpoint"
    else
        curl -k -s -u "$user:$password" -X "$method" "https://$host:$port/v1/$endpoint" \
            -H "Content-Type: application/json" -d "$data"
    fi
}

# Function to wait for Redis Enterprise cluster to be ready
wait_for_cluster() {
    local host=$1
    local port=$2
    local user=$3
    local password=$4
    
    echo "Waiting for Redis Enterprise cluster at $host to be ready..."
    
    while true; do
        status=$(execute_rest_api "$host" "$port" "$user" "$password" "GET" "cluster" | jq -r '.status')
        
        if [ "$status" == "active" ]; then
            echo "Cluster is ready!"
            break
        fi
        
        echo "Cluster status: $status. Waiting..."
        sleep 10
    done
}

# Function to create Redis Enterprise cluster
create_cluster() {
    local node=$1
    local port=$2
    local admin_user=$3
    local admin_password=$4
    local cluster_name=$5
    local license_file=$6
    
    echo "Creating Redis Enterprise cluster on $node..."
    
    # Check if cluster already exists
    cluster_status=$(execute_rest_api "$node" "$port" "$admin_user" "$admin_password" "GET" "cluster" | jq -r '.status' 2>/dev/null || echo "not_found")
    
    if [ "$cluster_status" == "active" ]; then
        echo "Cluster already exists and is active."
        return
    fi
    
    # Create cluster
    cluster_data='{
        "name": "'$cluster_name'",
        "username": "'$admin_user'",
        "password": "'$admin_password'"
    }'
    
    execute_rest_api "$node" "$port" "$admin_user" "$admin_password" "POST" "cluster" "$cluster_data"
    
    # Wait for cluster to be ready
    wait_for_cluster "$node" "$port" "$admin_user" "$admin_password"
    
    # Apply license
    license_data='{
        "license": "'$license_file'"
    }'
    
    execute_rest_api "$node" "$port" "$admin_user" "$admin_password" "PUT" "license" "$license_data"
    
    echo "Cluster created successfully!"
}

# Function to add node to cluster
add_node_to_cluster() {
    local master_node=$1
    local master_port=$2
    local admin_user=$3
    local admin_password=$4
    local node_to_add=$5
    
    echo "Adding node $node_to_add to cluster..."
    
    # Check if node is already part of the cluster
    nodes=$(execute_rest_api "$master_node" "$master_port" "$admin_user" "$admin_password" "GET" "nodes" | jq -r '.[].addr')
    
    if echo "$nodes" | grep -q "$node_to_add"; then
        echo "Node $node_to_add is already part of the cluster."
        return
    fi
    
    # Add node to cluster
    node_data='{
        "addr": "'$node_to_add'"
    }'
    
    execute_rest_api "$master_node" "$master_port" "$admin_user" "$admin_password" "POST" "nodes" "$node_data"
    
    echo "Node added successfully!"
}

# Function to create active-active database
create_active_active_database() {
    local region_1_node=$1
    local region_1_port=$2
    local region_2_node=$3
    local region_2_port=$4
    local admin_user=$5
    local admin_password=$6
    local db_name=$7
    local memory_size=$8
    local shards=$9
    local causal_consistency=${10}
    local oss_cluster_api=${11}
    local source_ttl=${12}
    local persistence=${13}
    local eviction_policy=${14}
    
    echo "Creating active-active database $db_name..."
    
    # Check if database already exists in region 1
    db_exists=$(execute_rest_api "$region_1_node" "$region_1_port" "$admin_user" "$admin_password" "GET" "bdbs" | jq -r '.[] | select(.name=="'$db_name'") | .name' 2>/dev/null || echo "")
    
    if [ "$db_exists" == "$db_name" ]; then
        echo "Database $db_name already exists in region 1."
    else
        # Create database in region 1
        db_data='{
            "name": "'$db_name'",
            "type": "redis",
            "memory_size": '$memory_size',
            "sharding": true,
            "shards_count": '$shards',
            "replication": true,
            "data_persistence": "'$persistence'",
            "aof_policy": "appendfsync-every-sec",
            "eviction_policy": "'$eviction_policy'",
            "crdt": true,
            "crdt_sources": [],
            "crdt_causal_consistency": '$causal_consistency',
            "crdt_xadd_id": true,
            "oss_cluster": '$oss_cluster_api',
            "oss_cluster_api_compatible": '$oss_cluster_api',
            "crdt_sync_dist": true,
            "proxy_policy": "all-nodes",
            "password": "'$REDIS_PASSWORD'"
        }'
        
        region_1_db=$(execute_rest_api "$region_1_node" "$region_1_port" "$admin_user" "$admin_password" "POST" "bdbs" "$db_data")
        region_1_db_id=$(echo "$region_1_db" | jq -r '.uid')
        region_1_db_endpoint=$(echo "$region_1_db" | jq -r '.endpoints[0].addr')
        region_1_db_port=$(echo "$region_1_db" | jq -r '.endpoints[0].port')
        
        echo "Database created in region 1 with ID: $region_1_db_id, endpoint: $region_1_db_endpoint:$region_1_db_port"
    fi
    
    # Check if database already exists in region 2
    db_exists=$(execute_rest_api "$region_2_node" "$region_2_port" "$admin_user" "$admin_password" "GET" "bdbs" | jq -r '.[] | select(.name=="'$db_name'") | .name' 2>/dev/null || echo "")
    
    if [ "$db_exists" == "$db_name" ]; then
        echo "Database $db_name already exists in region 2."
    else
        # Create database in region 2
        db_data='{
            "name": "'$db_name'",
            "type": "redis",
            "memory_size": '$memory_size',
            "sharding": true,
            "shards_count": '$shards',
            "replication": true,
            "data_persistence": "'$persistence'",
            "aof_policy": "appendfsync-every-sec",
            "eviction_policy": "'$eviction_policy'",
            "crdt": true,
            "crdt_sources": [],
            "crdt_causal_consistency": '$causal_consistency',
            "crdt_xadd_id": true,
            "oss_cluster": '$oss_cluster_api',
            "oss_cluster_api_compatible": '$oss_cluster_api',
            "crdt_sync_dist": true,
            "proxy_policy": "all-nodes",
            "password": "'$REDIS_PASSWORD'"
        }'
        
        region_2_db=$(execute_rest_api "$region_2_node" "$region_2_port" "$admin_user" "$admin_password" "POST" "bdbs" "$db_data")
        region_2_db_id=$(echo "$region_2_db" | jq -r '.uid')
        region_2_db_endpoint=$(echo "$region_2_db" | jq -r '.endpoints[0].addr')
        region_2_db_port=$(echo "$region_2_db" | jq -r '.endpoints[0].port')
        
        echo "Database created in region 2 with ID: $region_2_db_id, endpoint: $region_2_db_endpoint:$region_2_db_port"
    fi
    
    # Get database IDs if they weren't created just now
    if [ -z "$region_1_db_id" ]; then
        region_1_db_id=$(execute_rest_api "$region_1_node" "$region_1_port" "$admin_user" "$admin_password" "GET" "bdbs" | jq -r '.[] | select(.name=="'$db_name'") | .uid')
        region_1_db_endpoint=$(execute_rest_api "$region_1_node" "$region_1_port" "$admin_user" "$admin_password" "GET" "bdbs/$region_1_db_id" | jq -r '.endpoints[0].addr')
        region_1_db_port=$(execute_rest_api "$region_1_node" "$region_1_port" "$admin_user" "$admin_password" "GET" "bdbs/$region_1_db_id" | jq -r '.endpoints[0].port')
    fi
    
    if [ -z "$region_2_db_id" ]; then
        region_2_db_id=$(execute_rest_api "$region_2_node" "$region_2_port" "$admin_user" "$admin_password" "GET" "bdbs" | jq -r '.[] | select(.name=="'$db_name'") | .uid')
        region_2_db_endpoint=$(execute_rest_api "$region_2_node" "$region_2_port" "$admin_user" "$admin_password" "GET" "bdbs/$region_2_db_id" | jq -r '.endpoints[0].addr')
        region_2_db_port=$(execute_rest_api "$region_2_node" "$region_2_port" "$admin_user" "$admin_password" "GET" "bdbs/$region_2_db_id" | jq -r '.endpoints[0].port')
    fi
    
    # Configure CRDT links between regions
    echo "Configuring CRDT links between regions for database $db_name..."
    
    # Add region 2 as CRDT source to region 1
    crdt_source_data='{
        "crdt_sources": [
            {
                "uid": "'$region_2_db_id'",
                "endpoint": "'$region_2_db_endpoint'",
                "port": '$region_2_db_port',
                "credentials": {
                    "username": "",
                    "password": "'$REDIS_PASSWORD'"
                },
                "replication_timeout": 60
            }
        ]
    }'
    
    execute_rest_api "$region_1_node" "$region_1_port" "$admin_user" "$admin_password" "PUT" "bdbs/$region_1_db_id" "$crdt_source_data"
    
    # Add region 1 as CRDT source to region 2
    crdt_source_data='{
        "crdt_sources": [
            {
                "uid": "'$region_1_db_id'",
                "endpoint": "'$region_1_db_endpoint'",
                "port": '$region_1_db_port',
                "credentials": {
                    "username": "",
                    "password": "'$REDIS_PASSWORD'"
                },
                "replication_timeout": 60
            }
        ]
    }'
    
    execute_rest_api "$region_2_node" "$region_2_port" "$admin_user" "$admin_password" "PUT" "bdbs/$region_2_db_id" "$crdt_source_data"
    
    echo "Active-active database $db_name created and configured successfully!"
}

# Function to setup monitoring for Redis Enterprise
setup_monitoring() {
    local region_1_node=$1
    local region_1_port=$2
    local region_2_node=$3
    local region_2_port=$4
    local admin_user=$5
    local admin_password=$6
    
    echo "Setting up monitoring for Redis Enterprise clusters..."
    
    # Enable metrics export in region 1
    metrics_data='{
        "metrics_exporter_enable": true,
        "metrics_exporter_address": "0.0.0.0",
        "metrics_exporter_port": 8070
    }'
    
    execute_rest_api "$region_1_node" "$region_1_port" "$admin_user" "$admin_password" "PUT" "cluster" "$metrics_data"
    
    # Enable metrics export in region 2
    execute_rest_api "$region_2_node" "$region_2_port" "$admin_user" "$admin_password" "PUT" "cluster" "$metrics_data"
    
    echo "Monitoring setup completed!"
}

# Main execution

echo "Starting multi-region Redis Enterprise setup..."

# Create Redis Enterprise clusters
create_cluster "$REGION_1_NODE_1" "$REGION_1_NODE_PORT" "$REGION_1_ADMIN_USER" "$ADMIN_PASSWORD" "$REGION_1_CLUSTER_NAME" "$LICENSE_FILE"
create_cluster "$REGION_2_NODE_1" "$REGION_2_NODE_PORT" "$REGION_2_ADMIN_USER" "$ADMIN_PASSWORD" "$REGION_2_CLUSTER_NAME" "$LICENSE_FILE"

# Add additional nodes to clusters
for node in $(echo "$REGION_1_NODES" | tail -n +2); do
    add_node_to_cluster "$REGION_1_NODE_1" "$REGION_1_NODE_PORT" "$REGION_1_ADMIN_USER" "$ADMIN_PASSWORD" "$node"
done

for node in $(echo "$REGION_2_NODES" | tail -n +2); do
    add_node_to_cluster "$REGION_2_NODE_1" "$REGION_2_NODE_PORT" "$REGION_2_ADMIN_USER" "$ADMIN_PASSWORD" "$node"
done

# Create active-active databases
echo "Creating active-active databases..."

# Get all databases
DATABASES=$(yq -r '.databases[].name' "$CONFIG_FILE")

# Setup each database
for db_name in $DATABASES; do
    memory_size=$(yq -r ".databases[] | select(.name == \"$db_name\") | .memory_size_gb" "$CONFIG_FILE")
    memory_size_bytes=$((memory_size * 1024 * 1024 * 1024))
    shards=$(yq -r ".databases[] | select(.name == \"$db_name\") | .shards" "$CONFIG_FILE")
    causal_consistency=$(yq -r ".databases[] | select(.name == \"$db_name\") | .crdt_options.causal_consistency" "$CONFIG_FILE")
    oss_cluster_api=$(yq -r ".databases[] | select(.name == \"$db_name\") | .crdt_options.oss_cluster_api_compatible" "$CONFIG_FILE")
    source_ttl=$(yq -r ".databases[] | select(.name == \"$db_name\") | .crdt_options.source_ttl" "$CONFIG_FILE")
    persistence=$(yq -r ".databases[] | select(.name == \"$db_name\") | .data_persistence" "$CONFIG_FILE")
    eviction_policy=$(yq -r ".databases[] | select(.name == \"$db_name\") | .data_eviction" "$CONFIG_FILE")
    
    create_active_active_database \
        "$REGION_1_NODE_1" "$REGION_1_NODE_PORT" \
        "$REGION_2_NODE_1" "$REGION_2_NODE_PORT" \
        "$REGION_1_ADMIN_USER" "$ADMIN_PASSWORD" \
        "$db_name" "$memory_size_bytes" "$shards" \
        "$causal_consistency" "$oss_cluster_api" "$source_ttl" \
        "$persistence" "$eviction_policy"
done

# Setup monitoring
setup_monitoring \
    "$REGION_1_NODE_1" "$REGION_1_NODE_PORT" \
    "$REGION_2_NODE_1" "$REGION_2_NODE_PORT" \
    "$REGION_1_ADMIN_USER" "$ADMIN_PASSWORD"

echo "Multi-region Redis Enterprise setup completed successfully!"

# Create monitoring script
cat > monitor-redis-replication.sh << 'EOF'
#!/bin/bash
# Script to monitor Redis Enterprise CRDT replication between regions

CONFIG_FILE="${1:-../configs/redis-multi-region.yaml}"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file $CONFIG_FILE not found"
    exit 1
fi

# Load secrets from environment or secret manager
ADMIN_PASSWORD=$(aws secretsmanager get-secret-value --secret-id $(yq -r '.regions[0].enterprise.admin_password_secret' "$CONFIG_FILE") --query SecretString --output text)

# Region information
REGION_1_NODE=$(yq -r '.regions[0].enterprise.nodes[0].host' "$CONFIG_FILE")
REGION_1_PORT=$(yq -r '.regions[0].enterprise.nodes[0].port' "$CONFIG_FILE")
REGION_1_ADMIN_USER=$(yq -r '.regions[0].enterprise.admin_user' "$CONFIG_FILE")

REGION_2_NODE=$(yq -r '.regions[1].enterprise.nodes[0].host' "$CONFIG_FILE")
REGION_2_PORT=$(yq -r '.regions[1].enterprise.nodes[0].port' "$CONFIG_FILE")
REGION_2_ADMIN_USER=$(yq -r '.regions[1].enterprise.admin_user' "$CONFIG_FILE")

# Function to execute Redis Enterprise REST API call
execute_rest_api() {
    local host=$1
    local port=$2
    local user=$3
    local password=$4
    local method=$5
    local endpoint=$6
    
    curl -k -s -u "$user:$password" -X "$method" "https://$host:$port/v1/$endpoint"
}

echo "=== Redis Enterprise CRDT Replication Status ==="
echo "Timestamp: $(date)"
echo

# Get all databases
DATABASES=$(yq -r '.databases[].name' "$CONFIG_FILE")

for db_name in $DATABASES; do
    echo "Database: $db_name"
    echo "----------------------------------------"
    
    # Get database IDs
    region_1_db_id=$(execute_rest_api "$REGION_1_NODE" "$REGION_1_PORT" "$REGION_1_ADMIN_USER" "$ADMIN_PASSWORD" "GET" "bdbs" | jq -r '.[] | select(.name=="'$db_name'") | .uid')
    region_2_db_id=$(execute_rest_api "$REGION_2_NODE" "$REGION_2_PORT" "$REGION_2_ADMIN_USER" "$ADMIN_PASSWORD" "GET" "bdbs" | jq -r '.[] | select(.name=="'$db_name'") | .uid')
    
    # Get CRDT stats for region 1
    echo "Region 1 -> Region 2 CRDT Stats:"
    region_1_stats=$(execute_rest_api "$REGION_1_NODE" "$REGION_1_PORT" "$REGION_1_ADMIN_USER" "$ADMIN_PASSWORD" "GET" "bdbs/$region_1_db_id/crdt_stats")
    echo "$region_1_stats" | jq '.'
    
    # Get CRDT stats for region 2
    echo "Region 2 -> Region 1 CRDT Stats:"
    region_2_stats=$(execute_rest_api "$REGION_2_NODE" "$REGION_2_PORT" "$REGION_2_ADMIN_USER" "$ADMIN_PASSWORD" "GET" "bdbs/$region_2_db_id/crdt_stats")
    echo "$region_2_stats" | jq '.'
    
    # Check for lag and conflicts
    region_1_lag=$(echo "$region_1_stats" | jq -r '.local_ingested_syncs - .remote_ingested_syncs')
    region_2_lag=$(echo "$region_2_stats" | jq -r '.local_ingested_syncs - .remote_ingested_syncs')
    
    echo "Replication Lag:"
    echo "  Region 1 -> Region 2: $region_1_lag syncs"
    echo "  Region 2 -> Region 1: $region_2_lag syncs"
    
    region_1_conflicts=$(echo "$region_1_stats" | jq -r '.resolved_conflicts')
    region_2_conflicts=$(echo "$region_2_stats" | jq -r '.resolved_conflicts')
    
    echo "Resolved Conflicts:"
    echo "  Region 1: $region_1_conflicts"
    echo "  Region 2: $region_2_conflicts"
    
    echo
done

# Check cluster health
echo "=== Cluster Health ==="

echo "Region 1 Cluster Health:"
execute_rest_api "$REGION_1_NODE" "$REGION_1_PORT" "$REGION_1_ADMIN_USER" "$ADMIN_PASSWORD" "GET" "cluster/status" | jq '.'

echo "Region 2 Cluster Health:"
execute_rest_api "$REGION_2_NODE" "$REGION_2_PORT" "$REGION_2_ADMIN_USER" "$ADMIN_PASSWORD" "GET" "cluster/status" | jq '.'
EOF

chmod +x monitor-redis-replication.sh

echo "Created monitoring script: monitor-redis-replication.sh"