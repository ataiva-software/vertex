# Vertex DevOps Suite - Kubernetes Deployment

This directory contains Kubernetes manifests and Helm charts for deploying the Vertex DevOps Suite to Kubernetes clusters.

## Directory Structure

- `base/`: Contains base Kubernetes manifests for all services
- `charts/`: Contains Helm charts for the Vertex DevOps Suite
- `environments/`: Contains environment-specific values files for different Kubernetes environments

## Prerequisites

- Kubernetes cluster (v1.22+)
- Helm v3.8+
- kubectl configured to access your cluster
- Container registry access (Docker Hub, ECR, GCR, ACR, etc.)

## Deployment Options

The Vertex DevOps Suite can be deployed using either:

1. **Kustomize**: Using the base manifests in the `base/` directory
2. **Helm**: Using the Helm chart in the `charts/` directory (recommended)

## Deploying with Helm

### Adding Required Helm Repositories

```bash
# Add Bitnami repository for PostgreSQL and Redis
helm repo add bitnami https://charts.bitnami.com/bitnami

# Add Prometheus repository
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts

# Add Grafana repository
helm repo add grafana https://grafana.github.io/helm-charts

# Update repositories
helm repo update
```

### Local Development Environment (Minikube, Kind, etc.)

```bash
# Create namespace
kubectl create namespace vertex

# Deploy using Helm
helm upgrade --install vertex ./charts/vertex \
  --namespace vertex \
  --values ./environments/values-local.yaml
```

### AWS EKS Deployment

1. **Prerequisites**:
   - EKS cluster up and running
   - AWS Load Balancer Controller installed
   - External DNS controller installed (optional)
   - AWS RDS PostgreSQL instance
   - AWS ElastiCache Redis instance
   - ACM Certificate for TLS

2. **Set up environment variables**:

```bash
export AWS_ACCOUNT_ID="your-aws-account-id"
export AWS_REGION="your-aws-region"
export AWS_RDS_ENDPOINT="your-rds-endpoint"
export AWS_ELASTICACHE_ENDPOINT="your-elasticache-endpoint"
export DB_PASSWORD="your-db-password"
export JWT_SECRET="your-jwt-secret"
export ENCRYPTION_KEY="your-encryption-key"
export ACM_CERTIFICATE_ARN="your-acm-certificate-arn"
```

3. **Deploy using Helm**:

```bash
# Create namespace
kubectl create namespace vertex

# Deploy using Helm with AWS EKS values
envsubst < ./environments/values-aws-eks.yaml > ./environments/values-aws-eks-resolved.yaml

helm upgrade --install vertex ./charts/vertex \
  --namespace vertex \
  --values ./environments/values-aws-eks-resolved.yaml
```

### GCP GKE Deployment

1. **Prerequisites**:
   - GKE cluster up and running
   - Cloud SQL PostgreSQL instance
   - Memorystore Redis instance
   - Google-managed SSL certificate

2. **Set up environment variables**:

```bash
export GCP_PROJECT_ID="your-gcp-project-id"
export GCP_REGION="your-gcp-region"
export DB_PASSWORD="your-db-password"
export JWT_SECRET="your-jwt-secret"
export ENCRYPTION_KEY="your-encryption-key"
export REDIS_HOST="your-redis-host"
```

3. **Deploy using Helm**:

```bash
# Create namespace
kubectl create namespace vertex

# Deploy using Helm with GCP GKE values
envsubst < ./environments/values-gcp-gke.yaml > ./environments/values-gcp-gke-resolved.yaml

helm upgrade --install vertex ./charts/vertex \
  --namespace vertex \
  --values ./environments/values-gcp-gke-resolved.yaml
```

### Azure AKS Deployment

1. **Prerequisites**:
   - AKS cluster up and running
   - Application Gateway Ingress Controller installed
   - Azure Database for PostgreSQL instance
   - Azure Cache for Redis instance
   - TLS certificate in Azure Key Vault

2. **Set up environment variables**:

```bash
export ACR_NAME="your-acr-name"
export DB_PASSWORD="your-db-password"
export JWT_SECRET="your-jwt-secret"
export ENCRYPTION_KEY="your-encryption-key"
```

3. **Deploy using Helm**:

```bash
# Create namespace
kubectl create namespace vertex

# Deploy using Helm with Azure AKS values
envsubst < ./environments/values-azure-aks.yaml > ./environments/values-azure-aks-resolved.yaml

helm upgrade --install vertex ./charts/vertex \
  --namespace vertex \
  --values ./environments/values-azure-aks-resolved.yaml
```

## Accessing the Application

After deployment, the Vertex DevOps Suite can be accessed through:

- **Local Development**: Port-forward to access the services
  ```bash
  kubectl port-forward -n vertex svc/web-ui 3000:80
  kubectl port-forward -n vertex svc/api-gateway 8080:8080
  ```

- **Production Environments**: Access through the configured Ingress/Load Balancer
  ```
  https://vertex.example.com
  ```

## Monitoring and Logging

- **AWS**: Use CloudWatch for logs and metrics
- **GCP**: Use Cloud Monitoring and Cloud Logging
- **Azure**: Use Azure Monitor and Log Analytics

## Scaling

The Vertex DevOps Suite is configured with Horizontal Pod Autoscaling (HPA) for all services. The autoscaling is based on CPU and memory utilization.

## Troubleshooting

1. **Check pod status**:
   ```bash
   kubectl get pods -n vertex
   ```

2. **Check pod logs**:
   ```bash
   kubectl logs -n vertex <pod-name>
   ```

3. **Check service status**:
   ```bash
   kubectl get svc -n vertex
   ```

4. **Check ingress status**:
   ```bash
   kubectl get ingress -n vertex
   ```

5. **Check HPA status**:
   ```bash
   kubectl get hpa -n vertex
   ```

## Maintenance

### Upgrading

```bash
# Update the Helm chart
helm upgrade vertex ./charts/vertex \
  --namespace vertex \
  --values ./environments/values-<environment>.yaml
```

### Rollback

```bash
# Rollback to a previous release
helm rollback vertex <revision> -n vertex
```

### Uninstalling

```bash
# Uninstall the Helm release
helm uninstall vertex -n vertex