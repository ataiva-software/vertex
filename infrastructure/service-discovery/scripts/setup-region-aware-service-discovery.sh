#!/bin/bash
# Script to set up region-aware service discovery with Istio service mesh
# This script implements the configuration defined in region-aware-service-discovery.yaml

set -e

# Load configuration
CONFIG_FILE="${1:-../configs/region-aware-service-discovery.yaml}"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file $CONFIG_FILE not found"
    exit 1
fi

# Function to parse YAML (simplified)
parse_yaml() {
    local yaml_file=$1
    cat "$yaml_file" | grep -v '^#' | sed -e 's/:[^:\/\/]/="/g;s/$/"/g;s/ *=/=/g'
}

# Global settings
SERVICE_MESH=$(yq -r '.global.service_mesh' "$CONFIG_FILE")
DOMAIN=$(yq -r '.global.domain' "$CONFIG_FILE")
CROSS_REGION_ENABLED=$(yq -r '.global.cross_region_enabled' "$CONFIG_FILE")
CROSS_REGION_PROTOCOL=$(yq -r '.global.cross_region_protocol' "$CONFIG_FILE")
CROSS_REGION_PORT=$(yq -r '.global.cross_region_port' "$CONFIG_FILE")

# Get region information
REGION_1_NAME=$(yq -r '.regions[0].name' "$CONFIG_FILE")
REGION_1_CONTEXT=$(yq -r '.regions[0].kubernetes_context' "$CONFIG_FILE")
REGION_1_DNS_SUFFIX=$(yq -r '.regions[0].dns_suffix' "$CONFIG_FILE")

REGION_2_NAME=$(yq -r '.regions[1].name' "$CONFIG_FILE")
REGION_2_CONTEXT=$(yq -r '.regions[1].kubernetes_context' "$CONFIG_FILE")
REGION_2_DNS_SUFFIX=$(yq -r '.regions[1].dns_suffix' "$CONFIG_FILE")

# Istio settings
ISTIO_ENABLED=$(yq -r '.service_mesh.istio.enabled' "$CONFIG_FILE")
ISTIO_VERSION=$(yq -r '.service_mesh.istio.version' "$CONFIG_FILE")
ISTIO_MTLS_ENABLED=$(yq -r '.service_mesh.istio.mtls.enabled' "$CONFIG_FILE")
ISTIO_MTLS_MODE=$(yq -r '.service_mesh.istio.mtls.mode' "$CONFIG_FILE")
ISTIO_AUTO_INJECT=$(yq -r '.service_mesh.istio.auto_inject' "$CONFIG_FILE")

# Function to install Istio
install_istio() {
    local context=$1
    local version=$2
    
    echo "Installing Istio $version in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Check if Istio is already installed
    if kubectl get namespace istio-system &>/dev/null; then
        echo "Istio namespace already exists, checking version..."
        
        # Check installed version
        local installed_version=$(kubectl -n istio-system get pods -l app=istiod -o jsonpath='{.items[0].spec.containers[0].image}' | cut -d: -f2)
        
        if [ "$installed_version" == "$version" ]; then
            echo "Istio $version is already installed"
            return
        else
            echo "Upgrading Istio from $installed_version to $version..."
        fi
    else
        echo "Creating istio-system namespace..."
        kubectl create namespace istio-system
    fi
    
    # Download Istio if not already downloaded
    if [ ! -d "istio-$version" ]; then
        echo "Downloading Istio $version..."
        curl -L https://istio.io/downloadIstio | ISTIO_VERSION=$version sh -
    fi
    
    # Install Istio with demo profile (for simplicity)
    echo "Installing Istio with demo profile..."
    istio-$version/bin/istioctl install --set profile=demo -y
    
    echo "Istio $version installed successfully in context $context"
}

# Function to enable automatic sidecar injection
enable_auto_injection() {
    local context=$1
    local namespace=$2
    
    echo "Enabling automatic sidecar injection for namespace $namespace in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Check if namespace exists
    if ! kubectl get namespace "$namespace" &>/dev/null; then
        echo "Creating namespace $namespace..."
        kubectl create namespace "$namespace"
    fi
    
    # Label namespace for automatic sidecar injection
    kubectl label namespace "$namespace" istio-injection=enabled --overwrite
    
    echo "Automatic sidecar injection enabled for namespace $namespace in context $context"
}

# Function to configure mTLS
configure_mtls() {
    local context=$1
    local mode=$2
    
    echo "Configuring mTLS with mode $mode in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Create PeerAuthentication resource
    cat <<EOF | kubectl apply -f -
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: istio-system
spec:
  mtls:
    mode: $mode
EOF
    
    echo "mTLS configured with mode $mode in context $context"
}

# Function to create cross-region gateway
create_cross_region_gateway() {
    local context=$1
    local name=$2
    local namespace=$3
    local selector=$4
    local port=$5
    local protocol=$6
    local hosts=$7
    local cert_name=$8
    
    echo "Creating cross-region gateway $name in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Create Gateway resource
    cat <<EOF | kubectl apply -f -
apiVersion: networking.istio.io/v1alpha3
kind: Gateway
metadata:
  name: $name
  namespace: $namespace
spec:
  selector:
    istio: ingressgateway
  servers:
  - port:
      number: $port
      name: $protocol
      protocol: $(echo "$protocol" | tr '[:lower:]' '[:upper:]')
    hosts:
    - "$hosts"
    tls:
      mode: SIMPLE
      credentialName: $cert_name
EOF
    
    echo "Cross-region gateway $name created in context $context"
}

# Function to create self-signed certificate for cross-region communication
create_self_signed_cert() {
    local context=$1
    local namespace=$2
    local cert_name=$3
    local domain=$4
    
    echo "Creating self-signed certificate $cert_name for domain $domain in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Create temporary directory for certificate files
    local temp_dir=$(mktemp -d)
    
    # Generate private key
    openssl genrsa -out "$temp_dir/key.pem" 2048
    
    # Generate certificate signing request
    openssl req -new -key "$temp_dir/key.pem" -out "$temp_dir/csr.pem" -subj "/CN=*.$domain/O=Eden"
    
    # Generate self-signed certificate
    openssl x509 -req -in "$temp_dir/csr.pem" -signkey "$temp_dir/key.pem" -out "$temp_dir/cert.pem" -days 365
    
    # Create Kubernetes secret
    kubectl create -n "$namespace" secret tls "$cert_name" \
        --key="$temp_dir/key.pem" \
        --cert="$temp_dir/cert.pem" \
        --dry-run=client -o yaml | kubectl apply -f -
    
    # Clean up temporary directory
    rm -rf "$temp_dir"
    
    echo "Self-signed certificate $cert_name created in context $context"
}

# Function to create virtual service for cross-region communication
create_virtual_service() {
    local context=$1
    local name=$2
    local namespace=$3
    local hosts=$4
    local gateway=$5
    local services=$6
    
    echo "Creating virtual service $name in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Create VirtualService resource
    cat <<EOF | kubectl apply -f -
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: $name
  namespace: $namespace
spec:
  hosts:
  - "$hosts"
  gateways:
  - $gateway
  http:
EOF
    
    # Add route for each service
    for service in $services; do
        local service_name=$(echo "$service" | cut -d: -f1)
        local service_port=$(echo "$service" | cut -d: -f2)
        
        cat <<EOF | kubectl apply -f -
  - match:
    - uri:
        prefix: /$service_name/
    route:
    - destination:
        host: $service_name.$namespace.svc.cluster.local
        port:
          number: $service_port
EOF
    done
    
    echo "Virtual service $name created in context $context"
}

# Function to create destination rule for traffic policies
create_destination_rule() {
    local context=$1
    local name=$2
    local namespace=$3
    local host=$4
    
    echo "Creating destination rule $name in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Create DestinationRule resource
    cat <<EOF | kubectl apply -f -
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: $name
  namespace: $namespace
spec:
  host: $host
  trafficPolicy:
    loadBalancer:
      simple: ROUND_ROBIN
    connectionPool:
      http:
        http1MaxPendingRequests: 100
        maxRequestsPerConnection: 10
      tcp:
        maxConnections: 100
    outlierDetection:
      consecutive5xxErrors: 5
      interval: 30s
      baseEjectionTime: 30s
      maxEjectionPercent: 100
EOF
    
    echo "Destination rule $name created in context $context"
}

# Function to configure CoreDNS for cross-region service discovery
configure_coredns() {
    local context=$1
    local config=$2
    local region_1_dns_suffix=$3
    local region_2_dns_suffix=$4
    
    echo "Configuring CoreDNS for cross-region service discovery in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Get current CoreDNS ConfigMap
    local coredns_cm=$(kubectl -n kube-system get configmap coredns -o yaml)
    
    # Extract current Corefile
    local current_corefile=$(echo "$coredns_cm" | yq -r '.data.Corefile')
    
    # Replace placeholders in the new config
    local dns_server=$(kubectl -n kube-system get service kube-dns -o jsonpath='{.spec.clusterIP}')
    local new_config=$(echo "$config" | sed "s/{{dns_server}}/$dns_server/g")
    
    # Get DNS server IPs for each region
    # In a real scenario, these would be the external IPs of the Istio ingress gateways
    # For this example, we'll use placeholder values
    local us_east_1_dns_server="10.0.0.1"
    local us_west_2_dns_server="10.0.0.2"
    
    new_config=$(echo "$new_config" | sed "s/{{us_east_1_dns_server}}/$us_east_1_dns_server/g")
    new_config=$(echo "$new_config" | sed "s/{{us_west_2_dns_server}}/$us_west_2_dns_server/g")
    
    # Update CoreDNS ConfigMap
    kubectl -n kube-system patch configmap coredns --type=merge -p "{\"data\":{\"Corefile\":\"$new_config\"}}"
    
    # Restart CoreDNS pods to apply changes
    kubectl -n kube-system rollout restart deployment coredns
    
    echo "CoreDNS configured for cross-region service discovery in context $context"
}

# Function to create service entries for cross-region services
create_service_entries() {
    local context=$1
    local namespace=$2
    local services=$3
    local remote_region=$4
    local remote_dns_suffix=$5
    
    echo "Creating service entries for cross-region services in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Create ServiceEntry for each service
    for service in $services; do
        local service_name=$(echo "$service" | cut -d: -f1)
        local service_port=$(echo "$service" | cut -d: -f2)
        local service_protocol=$(echo "$service" | cut -d: -f3)
        
        cat <<EOF | kubectl apply -f -
apiVersion: networking.istio.io/v1alpha3
kind: ServiceEntry
metadata:
  name: $service_name-$remote_region
  namespace: $namespace
spec:
  hosts:
  - $service_name.$remote_dns_suffix
  location: MESH_EXTERNAL
  ports:
  - number: $service_port
    name: $service_protocol
    protocol: $(echo "$service_protocol" | tr '[:lower:]' '[:upper:]')
  resolution: DNS
  endpoints:
  - address: $service_name.$remote_dns_suffix
EOF
    done
    
    echo "Service entries created for cross-region services in context $context"
}

# Main execution

echo "Starting region-aware service discovery setup..."

if [ "$SERVICE_MESH" == "istio" ] && [ "$ISTIO_ENABLED" == "true" ]; then
    # Install Istio in both regions
    install_istio "$REGION_1_CONTEXT" "$ISTIO_VERSION"
    install_istio "$REGION_2_CONTEXT" "$ISTIO_VERSION"
    
    # Get all services
    SERVICES=$(yq -r '.services[].name' "$CONFIG_FILE")
    SERVICE_PORTS=$(yq -r '.services[].port' "$CONFIG_FILE")
    SERVICE_PROTOCOLS=$(yq -r '.services[].protocol' "$CONFIG_FILE")
    
    # Combine service information
    SERVICE_INFO=""
    for i in $(seq 1 $(echo "$SERVICES" | wc -l)); do
        service=$(echo "$SERVICES" | sed -n "${i}p")
        port=$(echo "$SERVICE_PORTS" | sed -n "${i}p")
        protocol=$(echo "$SERVICE_PROTOCOLS" | sed -n "${i}p")
        SERVICE_INFO="$SERVICE_INFO $service:$port:$protocol"
    done
    
    # Enable auto-injection for the eden namespace in both regions
    enable_auto_injection "$REGION_1_CONTEXT" "eden"
    enable_auto_injection "$REGION_2_CONTEXT" "eden"
    
    # Configure mTLS in both regions
    if [ "$ISTIO_MTLS_ENABLED" == "true" ]; then
        configure_mtls "$REGION_1_CONTEXT" "$ISTIO_MTLS_MODE"
        configure_mtls "$REGION_2_CONTEXT" "$ISTIO_MTLS_MODE"
    fi
    
    # Create self-signed certificates for cross-region communication
    create_self_signed_cert "$REGION_1_CONTEXT" "istio-system" "eden-cross-region-cert" "$DOMAIN"
    create_self_signed_cert "$REGION_2_CONTEXT" "istio-system" "eden-cross-region-cert" "$DOMAIN"
    
    # Create cross-region gateways in both regions
    create_cross_region_gateway "$REGION_1_CONTEXT" "cross-region-gateway" "istio-system" "istio=ingressgateway" "$CROSS_REGION_PORT" "$CROSS_REGION_PROTOCOL" "*.$DOMAIN" "eden-cross-region-cert"
    create_cross_region_gateway "$REGION_2_CONTEXT" "cross-region-gateway" "istio-system" "istio=ingressgateway" "$CROSS_REGION_PORT" "$CROSS_REGION_PROTOCOL" "*.$DOMAIN" "eden-cross-region-cert"
    
    # Create virtual services for cross-region communication
    create_virtual_service "$REGION_1_CONTEXT" "cross-region-services" "eden" "*.$DOMAIN" "istio-system/cross-region-gateway" "$SERVICE_INFO"
    create_virtual_service "$REGION_2_CONTEXT" "cross-region-services" "eden" "*.$DOMAIN" "istio-system/cross-region-gateway" "$SERVICE_INFO"
    
    # Create destination rules for traffic policies
    create_destination_rule "$REGION_1_CONTEXT" "cross-region-services" "eden" "*.$DOMAIN"
    create_destination_rule "$REGION_2_CONTEXT" "cross-region-services" "eden" "*.$DOMAIN"
    
    # Create service entries for cross-region services
    create_service_entries "$REGION_1_CONTEXT" "eden" "$SERVICE_INFO" "$REGION_2_NAME" "$REGION_2_DNS_SUFFIX"
    create_service_entries "$REGION_2_CONTEXT" "eden" "$SERVICE_INFO" "$REGION_1_NAME" "$REGION_1_DNS_SUFFIX"
    
    # Configure CoreDNS for cross-region service discovery
    COREDNS_CONFIG=$(yq -r '.dns.coredns.config' "$CONFIG_FILE")
    configure_coredns "$REGION_1_CONTEXT" "$COREDNS_CONFIG" "$REGION_1_DNS_SUFFIX" "$REGION_2_DNS_SUFFIX"
    configure_coredns "$REGION_2_CONTEXT" "$COREDNS_CONFIG" "$REGION_1_DNS_SUFFIX" "$REGION_2_DNS_SUFFIX"
    
    echo "Region-aware service discovery setup completed successfully!"
else
    echo "Istio service mesh is not enabled in the configuration"
    exit 1
fi

# Create monitoring script
cat > monitor-service-discovery.sh << 'EOF'
#!/bin/bash
# Script to monitor region-aware service discovery

CONFIG_FILE="${1:-../configs/region-aware-service-discovery.yaml}"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file $CONFIG_FILE not found"
    exit 1
fi

# Get region information
REGION_1_CONTEXT=$(yq -r '.regions[0].kubernetes_context' "$CONFIG_FILE")
REGION_2_CONTEXT=$(yq -r '.regions[1].kubernetes_context' "$CONFIG_FILE")

echo "=== Service Discovery Status ==="
echo "Timestamp: $(date)"
echo

# Check Istio status in both regions
echo "Istio Status in Region 1:"
kubectl --context "$REGION_1_CONTEXT" -n istio-system get pods

echo "Istio Status in Region 2:"
kubectl --context "$REGION_2_CONTEXT" -n istio-system get pods

echo

# Check cross-region gateways
echo "Cross-Region Gateways in Region 1:"
kubectl --context "$REGION_1_CONTEXT" -n istio-system get gateway cross-region-gateway -o yaml | yq -r '.spec'

echo "Cross-Region Gateways in Region 2:"
kubectl --context "$REGION_2_CONTEXT" -n istio-system get gateway cross-region-gateway -o yaml | yq -r '.spec'

echo

# Check virtual services
echo "Virtual Services in Region 1:"
kubectl --context "$REGION_1_CONTEXT" -n eden get virtualservice cross-region-services -o yaml | yq -r '.spec'

echo "Virtual Services in Region 2:"
kubectl --context "$REGION_2_CONTEXT" -n eden get virtualservice cross-region-services -o yaml | yq -r '.spec'

echo

# Check service entries
echo "Service Entries in Region 1:"
kubectl --context "$REGION_1_CONTEXT" -n eden get serviceentry -o wide

echo "Service Entries in Region 2:"
kubectl --context "$REGION_2_CONTEXT" -n eden get serviceentry -o wide

echo

# Test cross-region service discovery
echo "Testing Cross-Region Service Discovery:"
echo "From Region 1 to Region 2:"
kubectl --context "$REGION_1_CONTEXT" -n eden run test-curl --image=curlimages/curl --rm -it --restart=Never -- \
    curl -s api-gateway.us-west-2.eden.svc.cluster.local:8080/health || echo "Failed to connect"

echo "From Region 2 to Region 1:"
kubectl --context "$REGION_2_CONTEXT" -n eden run test-curl --image=curlimages/curl --rm -it --restart=Never -- \
    curl -s api-gateway.us-east-1.eden.svc.cluster.local:8080/health || echo "Failed to connect"

echo

echo "=== CoreDNS Configuration ==="
echo "CoreDNS Config in Region 1:"
kubectl --context "$REGION_1_CONTEXT" -n kube-system get configmap coredns -o yaml | yq -r '.data.Corefile'

echo "CoreDNS Config in Region 2:"
kubectl --context "$REGION_2_CONTEXT" -n kube-system get configmap coredns -o yaml | yq -r '.data.Corefile'
EOF

chmod +x monitor-service-discovery.sh

echo "Created monitoring script: monitor-service-discovery.sh"