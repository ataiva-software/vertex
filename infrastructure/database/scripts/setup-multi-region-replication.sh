#!/bin/bash
# Script to set up bi-directional PostgreSQL replication between regions
# This script implements the configuration defined in postgresql-multi-region.yaml

set -e

# Load configuration
CONFIG_FILE="${1:-../configs/postgresql-multi-region.yaml}"
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
REPLICATION_USER=$(yq -r '.global.replication_user' "$CONFIG_FILE")
REPLICATION_PASSWORD=$(aws secretsmanager get-secret-value --secret-id $(yq -r '.global.replication_password_secret' "$CONFIG_FILE") --query SecretString --output text)
ADMIN_PASSWORD=$(aws secretsmanager get-secret-value --secret-id $(yq -r '.regions[0].instance.admin_password_secret' "$CONFIG_FILE") --query SecretString --output text)

# Region information
REGION_1_NAME=$(yq -r '.regions[0].name' "$CONFIG_FILE")
REGION_1_HOST=$(yq -r '.regions[0].instance.host' "$CONFIG_FILE")
REGION_1_PORT=$(yq -r '.regions[0].instance.port' "$CONFIG_FILE")
REGION_1_ADMIN=$(yq -r '.regions[0].instance.admin_user' "$CONFIG_FILE")

REGION_2_NAME=$(yq -r '.regions[1].name' "$CONFIG_FILE")
REGION_2_HOST=$(yq -r '.regions[1].instance.host' "$CONFIG_FILE")
REGION_2_PORT=$(yq -r '.regions[1].instance.port' "$CONFIG_FILE")
REGION_2_ADMIN=$(yq -r '.regions[1].instance.admin_user' "$CONFIG_FILE")

# Function to execute SQL on a specific PostgreSQL instance
execute_sql() {
    local host=$1
    local port=$2
    local user=$3
    local password=$4
    local database=$5
    local sql=$6

    PGPASSWORD="$password" psql -h "$host" -p "$port" -U "$user" -d "$database" -c "$sql"
}

# Function to create replication user
create_replication_user() {
    local host=$1
    local port=$2
    local admin_user=$3
    local admin_password=$4
    
    echo "Creating replication user on $host..."
    
    # Create replication user if it doesn't exist
    execute_sql "$host" "$port" "$admin_user" "$admin_password" "postgres" "
        DO \$\$
        BEGIN
            IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$REPLICATION_USER') THEN
                CREATE ROLE $REPLICATION_USER WITH LOGIN REPLICATION PASSWORD '$REPLICATION_PASSWORD';
            END IF;
        END \$\$;
    "
    
    # Grant necessary permissions
    execute_sql "$host" "$port" "$admin_user" "$admin_password" "postgres" "
        ALTER ROLE $REPLICATION_USER WITH SUPERUSER;
    "
}

# Function to install pglogical extension
install_pglogical() {
    local host=$1
    local port=$2
    local admin_user=$3
    local admin_password=$4
    local database=$5
    
    echo "Installing pglogical extension on $host for database $database..."
    
    # Create extension if it doesn't exist
    execute_sql "$host" "$port" "$admin_user" "$admin_password" "$database" "
        CREATE EXTENSION IF NOT EXISTS pglogical;
    "
}

# Function to create pglogical node
create_pglogical_node() {
    local host=$1
    local port=$2
    local admin_user=$3
    local admin_password=$4
    local database=$5
    local node_name=$6
    local region_name=$7
    
    echo "Creating pglogical node on $host for database $database..."
    
    # Create node if it doesn't exist
    execute_sql "$host" "$port" "$admin_user" "$admin_password" "$database" "
        SELECT pglogical.create_node(
            node_name := '${node_name}',
            dsn := 'host=$host port=$port dbname=$database user=$REPLICATION_USER password=$REPLICATION_PASSWORD'
        );
    "
}

# Function to create replication set
create_replication_set() {
    local host=$1
    local port=$2
    local admin_user=$3
    local admin_password=$4
    local database=$5
    local set_name=$6
    
    echo "Creating replication set on $host for database $database..."
    
    # Create replication set if it doesn't exist
    execute_sql "$host" "$port" "$admin_user" "$admin_password" "$database" "
        SELECT pglogical.create_replication_set(
            set_name := '$set_name',
            replicate_insert := true,
            replicate_update := true,
            replicate_delete := true,
            replicate_truncate := true
        );
    "
}

# Function to add tables to replication set
add_tables_to_replication_set() {
    local host=$1
    local port=$2
    local admin_user=$3
    local admin_password=$4
    local database=$5
    local set_name=$6
    local node_group=$7
    
    echo "Adding tables to replication set on $host for database $database..."
    
    # Get tables for this node group
    local tables=$(yq -r ".replication.node_groups[] | select(.name == \"$node_group\") | .tables[]" "$CONFIG_FILE")
    
    # Add each table to the replication set
    for table in $tables; do
        echo "Adding table $table to replication set $set_name..."
        execute_sql "$host" "$port" "$admin_user" "$admin_password" "$database" "
            SELECT pglogical.replication_set_add_table(
                set_name := '$set_name',
                relation := '$table'::regclass,
                synchronize_data := true
            );
        "
    done
}

# Function to create subscription
create_subscription() {
    local host=$1
    local port=$2
    local admin_user=$3
    local admin_password=$4
    local database=$5
    local subscription_name=$6
    local provider_dsn=$7
    local replication_sets=$8
    
    echo "Creating subscription on $host for database $database..."
    
    # Create subscription if it doesn't exist
    execute_sql "$host" "$port" "$admin_user" "$admin_password" "$database" "
        SELECT pglogical.create_subscription(
            subscription_name := '$subscription_name',
            provider_dsn := '$provider_dsn',
            replication_sets := array[$replication_sets],
            synchronize_structure := false,
            synchronize_data := true,
            forward_origins := '{}',
            apply_delay := '0'
        );
    "
}

# Function to configure conflict resolution
configure_conflict_resolution() {
    local host=$1
    local port=$2
    local admin_user=$3
    local admin_password=$4
    local database=$5
    local node_group=$6
    
    echo "Configuring conflict resolution on $host for database $database..."
    
    # Get conflict resolution tables for this node group
    local conflict_tables=$(yq -r ".replication.node_groups[] | select(.name == \"$node_group\") | .conflict_resolution_tables[].table" "$CONFIG_FILE")
    local tracking_columns=$(yq -r ".replication.node_groups[] | select(.name == \"$node_group\") | .conflict_resolution_tables[].tracking_column" "$CONFIG_FILE")
    
    # Configure conflict resolution for each table
    local i=0
    for table in $conflict_tables; do
        local tracking_column=$(echo "$tracking_columns" | sed -n "$((i+1))p")
        echo "Configuring conflict resolution for table $table using column $tracking_column..."
        
        # Create trigger function for conflict resolution
        execute_sql "$host" "$port" "$admin_user" "$admin_password" "$database" "
            CREATE OR REPLACE FUNCTION resolve_conflict_${table}()
            RETURNS TRIGGER AS \$\$
            BEGIN
                IF OLD.${tracking_column} > NEW.${tracking_column} THEN
                    RETURN OLD;
                ELSE
                    RETURN NEW;
                END IF;
            END;
            \$\$ LANGUAGE plpgsql;
            
            DROP TRIGGER IF EXISTS conflict_${table} ON ${table};
            
            CREATE TRIGGER conflict_${table}
            BEFORE UPDATE ON ${table}
            FOR EACH ROW
            EXECUTE FUNCTION resolve_conflict_${table}();
        "
        
        i=$((i+1))
    done
}

# Function to setup replication for a database
setup_database_replication() {
    local database=$1
    local node_group=$2
    
    echo "Setting up replication for database $database (node group $node_group)..."
    
    # Install pglogical extension on both regions
    install_pglogical "$REGION_1_HOST" "$REGION_1_PORT" "$REGION_1_ADMIN" "$ADMIN_PASSWORD" "$database"
    install_pglogical "$REGION_2_HOST" "$REGION_2_PORT" "$REGION_2_ADMIN" "$ADMIN_PASSWORD" "$database"
    
    # Create pglogical nodes
    create_pglogical_node "$REGION_1_HOST" "$REGION_1_PORT" "$REGION_1_ADMIN" "$ADMIN_PASSWORD" "$database" "${database}_${REGION_1_NAME}" "$REGION_1_NAME"
    create_pglogical_node "$REGION_2_HOST" "$REGION_2_PORT" "$REGION_2_ADMIN" "$ADMIN_PASSWORD" "$database" "${database}_${REGION_2_NAME}" "$REGION_2_NAME"
    
    # Create replication sets
    create_replication_set "$REGION_1_HOST" "$REGION_1_PORT" "$REGION_1_ADMIN" "$ADMIN_PASSWORD" "$database" "${database}_${REGION_1_NAME}_set"
    create_replication_set "$REGION_2_HOST" "$REGION_2_PORT" "$REGION_2_ADMIN" "$ADMIN_PASSWORD" "$database" "${database}_${REGION_2_NAME}_set"
    
    # Add tables to replication sets
    add_tables_to_replication_set "$REGION_1_HOST" "$REGION_1_PORT" "$REGION_1_ADMIN" "$ADMIN_PASSWORD" "$database" "${database}_${REGION_1_NAME}_set" "$node_group"
    add_tables_to_replication_set "$REGION_2_HOST" "$REGION_2_PORT" "$REGION_2_ADMIN" "$ADMIN_PASSWORD" "$database" "${database}_${REGION_2_NAME}_set" "$node_group"
    
    # Create subscriptions
    create_subscription "$REGION_1_HOST" "$REGION_1_PORT" "$REGION_1_ADMIN" "$ADMIN_PASSWORD" "$database" \
        "${database}_${REGION_2_NAME}_sub" \
        "host=$REGION_2_HOST port=$REGION_2_PORT dbname=$database user=$REPLICATION_USER password=$REPLICATION_PASSWORD" \
        "'${database}_${REGION_2_NAME}_set'"
    
    create_subscription "$REGION_2_HOST" "$REGION_2_PORT" "$REGION_2_ADMIN" "$ADMIN_PASSWORD" "$database" \
        "${database}_${REGION_1_NAME}_sub" \
        "host=$REGION_1_HOST port=$REGION_1_PORT dbname=$database user=$REPLICATION_USER password=$REPLICATION_PASSWORD" \
        "'${database}_${REGION_1_NAME}_set'"
    
    # Configure conflict resolution
    configure_conflict_resolution "$REGION_1_HOST" "$REGION_1_PORT" "$REGION_1_ADMIN" "$ADMIN_PASSWORD" "$database" "$node_group"
    configure_conflict_resolution "$REGION_2_HOST" "$REGION_2_PORT" "$REGION_2_ADMIN" "$ADMIN_PASSWORD" "$database" "$node_group"
}

# Main execution

echo "Starting multi-region PostgreSQL replication setup..."

# Create replication users on both regions
create_replication_user "$REGION_1_HOST" "$REGION_1_PORT" "$REGION_1_ADMIN" "$ADMIN_PASSWORD"
create_replication_user "$REGION_2_HOST" "$REGION_2_PORT" "$REGION_2_ADMIN" "$ADMIN_PASSWORD"

# Get all node groups
NODE_GROUPS=$(yq -r '.replication.node_groups[].name' "$CONFIG_FILE")

# Setup replication for each node group
for node_group in $NODE_GROUPS; do
    database=$(yq -r ".replication.node_groups[] | select(.name == \"$node_group\") | .database" "$CONFIG_FILE")
    setup_database_replication "$database" "$node_group"
done

echo "Multi-region PostgreSQL replication setup completed successfully!"

# Create monitoring script
cat > monitor-replication.sh << 'EOF'
#!/bin/bash
# Script to monitor PostgreSQL replication lag between regions

CONFIG_FILE="${1:-../configs/postgresql-multi-region.yaml}"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file $CONFIG_FILE not found"
    exit 1
fi

# Load secrets from environment or secret manager
ADMIN_PASSWORD=$(aws secretsmanager get-secret-value --secret-id $(yq -r '.regions[0].instance.admin_password_secret' "$CONFIG_FILE") --query SecretString --output text)

# Region information
REGION_1_HOST=$(yq -r '.regions[0].instance.host' "$CONFIG_FILE")
REGION_1_PORT=$(yq -r '.regions[0].instance.port' "$CONFIG_FILE")
REGION_1_ADMIN=$(yq -r '.regions[0].instance.admin_user' "$CONFIG_FILE")

REGION_2_HOST=$(yq -r '.regions[1].instance.host' "$CONFIG_FILE")
REGION_2_PORT=$(yq -r '.regions[1].instance.port' "$CONFIG_FILE")
REGION_2_ADMIN=$(yq -r '.regions[1].instance.admin_user' "$CONFIG_FILE")

# Get all databases
DATABASES=$(yq -r '.replication.node_groups[].database' "$CONFIG_FILE" | sort | uniq)

echo "=== PostgreSQL Replication Status ==="
echo "Timestamp: $(date)"
echo

for database in $DATABASES; do
    echo "Database: $database"
    echo "----------------------------------------"
    
    # Check replication lag from Region 1 to Region 2
    echo "Region 1 -> Region 2:"
    PGPASSWORD="$ADMIN_PASSWORD" psql -h "$REGION_2_HOST" -p "$REGION_2_PORT" -U "$REGION_2_ADMIN" -d "$database" -c "
        SELECT 
            subscription_name,
            status,
            provider_node,
            replication_sets,
            pg_size_pretty(pg_xlog_location_diff(pg_current_xlog_location(), replay_location)) AS lag_bytes,
            EXTRACT(EPOCH FROM (now() - latest_end_time)) AS lag_seconds
        FROM 
            pglogical.show_subscription_status();
    "
    
    # Check replication lag from Region 2 to Region 1
    echo "Region 2 -> Region 1:"
    PGPASSWORD="$ADMIN_PASSWORD" psql -h "$REGION_1_HOST" -p "$REGION_1_PORT" -U "$REGION_1_ADMIN" -d "$database" -c "
        SELECT 
            subscription_name,
            status,
            provider_node,
            replication_sets,
            pg_size_pretty(pg_xlog_location_diff(pg_current_xlog_location(), replay_location)) AS lag_bytes,
            EXTRACT(EPOCH FROM (now() - latest_end_time)) AS lag_seconds
        FROM 
            pglogical.show_subscription_status();
    "
    
    echo
done

# Check for conflicts
echo "=== Replication Conflicts ==="
for database in $DATABASES; do
    echo "Database: $database"
    echo "----------------------------------------"
    
    # Check conflicts in Region 1
    echo "Conflicts in Region 1:"
    PGPASSWORD="$ADMIN_PASSWORD" psql -h "$REGION_1_HOST" -p "$REGION_1_PORT" -U "$REGION_1_ADMIN" -d "$database" -c "
        SELECT 
            relname AS table_name,
            count(*) AS conflict_count
        FROM 
            pg_stat_user_tables
        WHERE 
            conflicts > 0
        GROUP BY 
            relname
        ORDER BY 
            conflict_count DESC;
    "
    
    # Check conflicts in Region 2
    echo "Conflicts in Region 2:"
    PGPASSWORD="$ADMIN_PASSWORD" psql -h "$REGION_2_HOST" -p "$REGION_2_PORT" -U "$REGION_2_ADMIN" -d "$database" -c "
        SELECT 
            relname AS table_name,
            count(*) AS conflict_count
        FROM 
            pg_stat_user_tables
        WHERE 
            conflicts > 0
        GROUP BY 
            relname
        ORDER BY 
            conflict_count DESC;
    "
    
    echo
done
EOF

chmod +x monitor-replication.sh

echo "Created monitoring script: monitor-replication.sh"