#!/bin/bash
# Script to set up cross-region monitoring and alerting with Prometheus, Alertmanager, and Grafana
# This script implements the configuration defined in cross-region-monitoring.yaml

set -e

# Load configuration
CONFIG_FILE="${1:-../configs/cross-region-monitoring.yaml}"
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
SCRAPE_INTERVAL=$(yq -r '.global.scrape_interval' "$CONFIG_FILE")
EVALUATION_INTERVAL=$(yq -r '.global.evaluation_interval' "$CONFIG_FILE")
SCRAPE_TIMEOUT=$(yq -r '.global.scrape_timeout' "$CONFIG_FILE")
RETENTION_DAYS=$(yq -r '.global.retention_days' "$CONFIG_FILE")

# Get region information
REGION_1_NAME=$(yq -r '.regions[0].name' "$CONFIG_FILE")
REGION_1_CONTEXT=$(yq -r '.regions[0].kubernetes_context' "$CONFIG_FILE")
REGION_1_PROMETHEUS_URL=$(yq -r '.regions[0].prometheus_url' "$CONFIG_FILE")
REGION_1_ALERTMANAGER_URL=$(yq -r '.regions[0].alertmanager_url' "$CONFIG_FILE")
REGION_1_GRAFANA_URL=$(yq -r '.regions[0].grafana_url' "$CONFIG_FILE")

REGION_2_NAME=$(yq -r '.regions[1].name' "$CONFIG_FILE")
REGION_2_CONTEXT=$(yq -r '.regions[1].kubernetes_context' "$CONFIG_FILE")
REGION_2_PROMETHEUS_URL=$(yq -r '.regions[1].prometheus_url' "$CONFIG_FILE")
REGION_2_ALERTMANAGER_URL=$(yq -r '.regions[1].alertmanager_url' "$CONFIG_FILE")
REGION_2_GRAFANA_URL=$(yq -r '.regions[1].grafana_url' "$CONFIG_FILE")

# Function to create namespace
create_namespace() {
    local context=$1
    local namespace=$2
    
    echo "Creating namespace $namespace in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Check if namespace exists
    if ! kubectl get namespace "$namespace" &>/dev/null; then
        echo "Creating namespace $namespace..."
        kubectl create namespace "$namespace"
    else
        echo "Namespace $namespace already exists"
    fi
}

# Function to install Prometheus Operator using Helm
install_prometheus_operator() {
    local context=$1
    local namespace=$2
    local region=$3
    
    echo "Installing Prometheus Operator in context $context, namespace $namespace..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Add Prometheus Operator Helm repository
    helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
    helm repo update
    
    # Install Prometheus Operator
    helm upgrade --install prometheus-operator prometheus-community/kube-prometheus-stack \
        --namespace "$namespace" \
        --set prometheus.prometheusSpec.scrapeInterval="$SCRAPE_INTERVAL" \
        --set prometheus.prometheusSpec.evaluationInterval="$EVALUATION_INTERVAL" \
        --set prometheus.prometheusSpec.scrapeTimeout="$SCRAPE_TIMEOUT" \
        --set prometheus.prometheusSpec.retention="${RETENTION_DAYS}d" \
        --set prometheus.prometheusSpec.externalLabels.region="$region" \
        --set prometheus.prometheusSpec.externalLabels.cluster="$context" \
        --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
        --set prometheus.prometheusSpec.serviceMonitorSelector="{}" \
        --set prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues=false \
        --set prometheus.prometheusSpec.podMonitorSelector="{}" \
        --set alertmanager.alertmanagerSpec.externalUrl="http://alertmanager.$namespace.svc.cluster.local:9093" \
        --set grafana.enabled=true \
        --set grafana.adminPassword="admin" \
        --wait
    
    echo "Prometheus Operator installed successfully in context $context"
}

# Function to configure Prometheus remote write
configure_prometheus_remote_write() {
    local context=$1
    local namespace=$2
    local remote_write_url=$3
    
    echo "Configuring Prometheus remote write in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Create a secret for remote write credentials if needed
    # kubectl create secret generic remote-write-credentials \
    #     --namespace "$namespace" \
    #     --from-literal=username=prometheus \
    #     --from-literal=password=your-password \
    #     --dry-run=client -o yaml | kubectl apply -f -
    
    # Update Prometheus CR to add remote write configuration
    kubectl patch prometheus prometheus-operator-kube-prometheus-prometheus \
        --namespace "$namespace" \
        --type=merge \
        --patch "{
            \"spec\": {
                \"remoteWrite\": [
                    {
                        \"url\": \"$remote_write_url\",
                        \"name\": \"central-prometheus\",
                        \"remoteTimeout\": \"30s\",
                        \"writeRelabelConfigs\": [
                            {
                                \"sourceLabels\": [\"__name__\"],
                                \"regex\": \"up|node_.*|kube_.*|container_.*|istio_.*|eden_.*\",
                                \"action\": \"keep\"
                            }
                        ]
                    }
                ]
            }
        }"
    
    echo "Prometheus remote write configured in context $context"
}

# Function to configure Prometheus remote read
configure_prometheus_remote_read() {
    local context=$1
    local namespace=$2
    local remote_read_url=$3
    
    echo "Configuring Prometheus remote read in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Update Prometheus CR to add remote read configuration
    kubectl patch prometheus prometheus-operator-kube-prometheus-prometheus \
        --namespace "$namespace" \
        --type=merge \
        --patch "{
            \"spec\": {
                \"remoteRead\": [
                    {
                        \"url\": \"$remote_read_url\",
                        \"name\": \"central-prometheus\",
                        \"readRecent\": true,
                        \"requiredMatchers\": {
                            \"monitor\": \"eden-production\"
                        }
                    }
                ]
            }
        }"
    
    echo "Prometheus remote read configured in context $context"
}

# Function to create Prometheus recording rules
create_prometheus_recording_rules() {
    local context=$1
    local namespace=$2
    
    echo "Creating Prometheus recording rules in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Create PrometheusRule CR for cross-region metrics
    cat <<EOF | kubectl apply -f -
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: cross-region-recording-rules
  namespace: $namespace
  labels:
    app: prometheus-operator
    release: prometheus-operator
spec:
  groups:
  - name: cross-region-metrics
    interval: 30s
    rules:
    - record: eden:service:availability:ratio
      expr: sum by(service, region) (up{job="eden-services"}) / count by(service, region) (up{job="eden-services"})
    - record: eden:service:error_rate:ratio
      expr: sum by(service, region) (rate(http_requests_total{job="eden-services",status_code=~"5.."}[5m])) / sum by(service, region) (rate(http_requests_total{job="eden-services"}[5m]))
    - record: eden:service:latency:p95
      expr: histogram_quantile(0.95, sum by(le, service, region) (rate(http_request_duration_seconds_bucket{job="eden-services"}[5m])))
    - record: eden:service:request_rate:qps
      expr: sum by(service, region) (rate(http_requests_total{job="eden-services"}[5m]))
EOF
    
    echo "Prometheus recording rules created in context $context"
}

# Function to create Prometheus alert rules
create_prometheus_alert_rules() {
    local context=$1
    local namespace=$2
    
    echo "Creating Prometheus alert rules in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Create PrometheusRule CR for cross-region alerts
    cat <<EOF | kubectl apply -f -
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: cross-region-alert-rules
  namespace: $namespace
  labels:
    app: prometheus-operator
    release: prometheus-operator
spec:
  groups:
  - name: cross-region-availability
    rules:
    - alert: CrossRegionServiceDown
      expr: sum by(service) (up{job="eden-services"}) < 2
      for: 5m
      labels:
        severity: critical
      annotations:
        summary: "Cross-region service down"
        description: "Service {{ \$labels.service }} is down in at least one region"
    - alert: AllRegionsServiceDown
      expr: sum by(service) (up{job="eden-services"}) == 0
      for: 1m
      labels:
        severity: critical
      annotations:
        summary: "Service down in all regions"
        description: "Service {{ \$labels.service }} is down in all regions"
    - alert: RegionDown
      expr: sum by(region) (up{job="kubernetes-apiservers"}) == 0
      for: 5m
      labels:
        severity: critical
      annotations:
        summary: "Region down"
        description: "Region {{ \$labels.region }} appears to be down"
  
  - name: cross-region-latency
    rules:
    - alert: CrossRegionHighLatency
      expr: histogram_quantile(0.95, sum by(le, service, region) (rate(http_request_duration_seconds_bucket{job="eden-services"}[5m]))) > 1
      for: 10m
      labels:
        severity: warning
      annotations:
        summary: "High cross-region latency"
        description: "Service {{ \$labels.service }} in region {{ \$labels.region }} has high latency (95th percentile > 1s)"
    - alert: CrossRegionLatencyImbalance
      expr: |
        max by(service) (
          histogram_quantile(0.95, sum by(le, service, region) (rate(http_request_duration_seconds_bucket{job="eden-services"}[5m])))
        ) 
        / 
        min by(service) (
          histogram_quantile(0.95, sum by(le, service, region) (rate(http_request_duration_seconds_bucket{job="eden-services"}[5m])))
        ) > 3
      for: 15m
      labels:
        severity: warning
      annotations:
        summary: "Cross-region latency imbalance"
        description: "Service {{ \$labels.service }} has significant latency imbalance between regions (ratio > 3x)"
  
  - name: cross-region-errors
    rules:
    - alert: CrossRegionHighErrorRate
      expr: sum by(service, region) (rate(http_requests_total{job="eden-services",status_code=~"5.."}[5m])) / sum by(service, region) (rate(http_requests_total{job="eden-services"}[5m])) > 0.05
      for: 5m
      labels:
        severity: warning
      annotations:
        summary: "High error rate"
        description: "Service {{ \$labels.service }} in region {{ \$labels.region }} has high error rate (>5%)"
    - alert: CrossRegionErrorRateImbalance
      expr: |
        max by(service) (
          sum by(service, region) (rate(http_requests_total{job="eden-services",status_code=~"5.."}[5m])) 
          / 
          sum by(service, region) (rate(http_requests_total{job="eden-services"}[5m]))
        ) 
        / 
        min by(service) (
          sum by(service, region) (rate(http_requests_total{job="eden-services",status_code=~"5.."}[5m])) 
          / 
          sum by(service, region) (rate(http_requests_total{job="eden-services"}[5m]))
        ) > 3
      for: 15m
      labels:
        severity: warning
      annotations:
        summary: "Cross-region error rate imbalance"
        description: "Service {{ \$labels.service }} has significant error rate imbalance between regions (ratio > 3x)"
  
  - name: database-replication
    rules:
    - alert: PostgreSQLReplicationLag
      expr: pg_replication_lag_seconds > 300
      for: 5m
      labels:
        severity: warning
      annotations:
        summary: "PostgreSQL replication lag"
        description: "PostgreSQL replication lag is {{ \$value }} seconds in region {{ \$labels.region }}"
    - alert: PostgreSQLReplicationStopped
      expr: pg_replication_is_replica == 0 and pg_replication_is_primary == 0
      for: 1m
      labels:
        severity: critical
      annotations:
        summary: "PostgreSQL replication stopped"
        description: "PostgreSQL replication has stopped in region {{ \$labels.region }}"
    - alert: RedisReplicationLag
      expr: redis_replication_lag_seconds > 60
      for: 5m
      labels:
        severity: warning
      annotations:
        summary: "Redis replication lag"
        description: "Redis replication lag is {{ \$value }} seconds in region {{ \$labels.region }}"
    - alert: RedisReplicationBroken
      expr: redis_replication_status == 0
      for: 1m
      labels:
        severity: critical
      annotations:
        summary: "Redis replication broken"
        description: "Redis replication is broken in region {{ \$labels.region }}"
EOF
    
    echo "Prometheus alert rules created in context $context"
}

# Function to configure Alertmanager
configure_alertmanager() {
    local context=$1
    local namespace=$2
    
    echo "Configuring Alertmanager in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Create secret for Slack webhook URL and PagerDuty service key
    kubectl create secret generic alertmanager-secrets \
        --namespace "$namespace" \
        --from-literal=slack_api_url="https://hooks.slack.com/services/YOUR_SLACK_WEBHOOK_URL" \
        --from-literal=pagerduty_service_key="YOUR_PAGERDUTY_SERVICE_KEY" \
        --dry-run=client -o yaml | kubectl apply -f -
    
    # Create Alertmanager configuration
    cat <<EOF | kubectl apply -f -
apiVersion: monitoring.coreos.com/v1
kind: AlertmanagerConfig
metadata:
  name: eden-alertmanager-config
  namespace: $namespace
  labels:
    app: prometheus-operator
    release: prometheus-operator
spec:
  route:
    groupBy: ['alertname', 'cluster', 'service', 'region']
    groupWait: 30s
    groupInterval: 5m
    repeatInterval: 4h
    receiver: 'team-eden'
    routes:
      - matchers:
        - name: severity
          value: critical
        receiver: 'team-eden-pagerduty'
        continue: true
      - matchers:
        - name: severity
          value: warning
        receiver: 'team-eden-slack'
        continue: true
      - matchers:
        - name: service
          regex: "api-gateway|vault-service"
        receiver: 'team-eden-security'
      - matchers:
        - name: service
          regex: "hub-service|flow-service|task-service"
        receiver: 'team-eden-core'
      - matchers:
        - name: service
          regex: "monitor-service|sync-service|insight-service"
        receiver: 'team-eden-data'
  
  inhibitRules:
    - sourceMatchers:
      - name: severity
        value: 'critical'
      targetMatchers:
      - name: severity
        value: 'warning'
      equal: ['alertname', 'cluster', 'service']
    - sourceMatchers:
      - name: severity
        value: 'critical'
      targetMatchers:
      - name: severity
        value: 'info'
      equal: ['alertname', 'cluster', 'service']
  
  receivers:
    - name: 'team-eden'
      emailConfigs:
        - to: 'team@eden.example.com'
          sendResolved: true
          from: 'alertmanager@eden.example.com'
          smarthost: 'smtp.eden.example.com:587'
          authUsername: 'alertmanager'
          authPassword:
            name: alertmanager-secrets
            key: smtp_password
      slackConfigs:
        - channel: '#eden-alerts'
          sendResolved: true
          apiURL:
            name: alertmanager-secrets
            key: slack_api_url
          title: '[{{ .Status | toUpper }}{{ if eq .Status "firing" }}:{{ .Alerts.Firing | len }}{{ end }}] {{ .CommonLabels.alertname }}'
          text: >-
            {{ range .Alerts }}
              *Alert:* {{ .Annotations.summary }}
              *Description:* {{ .Annotations.description }}
              *Region:* {{ .Labels.region }}
              *Severity:* {{ .Labels.severity }}
              *Service:* {{ .Labels.service }}
            {{ end }}
    
    - name: 'team-eden-pagerduty'
      pagerdutyConfigs:
        - serviceKey:
            name: alertmanager-secrets
            key: pagerduty_service_key
          sendResolved: true
    
    - name: 'team-eden-slack'
      slackConfigs:
        - channel: '#eden-alerts'
          sendResolved: true
          apiURL:
            name: alertmanager-secrets
            key: slack_api_url
          title: '[{{ .Status | toUpper }}{{ if eq .Status "firing" }}:{{ .Alerts.Firing | len }}{{ end }}] {{ .CommonLabels.alertname }}'
          text: >-
            {{ range .Alerts }}
              *Alert:* {{ .Annotations.summary }}
              *Description:* {{ .Annotations.description }}
              *Region:* {{ .Labels.region }}
              *Severity:* {{ .Labels.severity }}
              *Service:* {{ .Labels.service }}
            {{ end }}
    
    - name: 'team-eden-security'
      slackConfigs:
        - channel: '#eden-security'
          sendResolved: true
          apiURL:
            name: alertmanager-secrets
            key: slack_api_url
          title: '[{{ .Status | toUpper }}{{ if eq .Status "firing" }}:{{ .Alerts.Firing | len }}{{ end }}] {{ .CommonLabels.alertname }}'
          text: >-
            {{ range .Alerts }}
              *Alert:* {{ .Annotations.summary }}
              *Description:* {{ .Annotations.description }}
              *Region:* {{ .Labels.region }}
              *Severity:* {{ .Labels.severity }}
              *Service:* {{ .Labels.service }}
            {{ end }}
      emailConfigs:
        - to: 'security@eden.example.com'
          sendResolved: true
          from: 'alertmanager@eden.example.com'
          smarthost: 'smtp.eden.example.com:587'
          authUsername: 'alertmanager'
          authPassword:
            name: alertmanager-secrets
            key: smtp_password
    
    - name: 'team-eden-core'
      slackConfigs:
        - channel: '#eden-core'
          sendResolved: true
          apiURL:
            name: alertmanager-secrets
            key: slack_api_url
          title: '[{{ .Status | toUpper }}{{ if eq .Status "firing" }}:{{ .Alerts.Firing | len }}{{ end }}] {{ .CommonLabels.alertname }}'
          text: >-
            {{ range .Alerts }}
              *Alert:* {{ .Annotations.summary }}
              *Description:* {{ .Annotations.description }}
              *Region:* {{ .Labels.region }}
              *Severity:* {{ .Labels.severity }}
              *Service:* {{ .Labels.service }}
            {{ end }}
      emailConfigs:
        - to: 'core@eden.example.com'
          sendResolved: true
          from: 'alertmanager@eden.example.com'
          smarthost: 'smtp.eden.example.com:587'
          authUsername: 'alertmanager'
          authPassword:
            name: alertmanager-secrets
            key: smtp_password
    
    - name: 'team-eden-data'
      slackConfigs:
        - channel: '#eden-data'
          sendResolved: true
          apiURL:
            name: alertmanager-secrets
            key: slack_api_url
          title: '[{{ .Status | toUpper }}{{ if eq .Status "firing" }}:{{ .Alerts.Firing | len }}{{ end }}] {{ .CommonLabels.alertname }}'
          text: >-
            {{ range .Alerts }}
              *Alert:* {{ .Annotations.summary }}
              *Description:* {{ .Annotations.description }}
              *Region:* {{ .Labels.region }}
              *Severity:* {{ .Labels.severity }}
              *Service:* {{ .Labels.service }}
            {{ end }}
      emailConfigs:
        - to: 'data@eden.example.com'
          sendResolved: true
          from: 'alertmanager@eden.example.com'
          smarthost: 'smtp.eden.example.com:587'
          authUsername: 'alertmanager'
          authPassword:
            name: alertmanager-secrets
            key: smtp_password
EOF
    
    echo "Alertmanager configured in context $context"
}

# Function to configure Grafana
configure_grafana() {
    local context=$1
    local namespace=$2
    local region=$3
    local other_region=$4
    local other_prometheus_url=$5
    
    echo "Configuring Grafana in context $context..."
    
    # Switch to the correct context
    kubectl config use-context "$context"
    
    # Create ConfigMap for Grafana datasources
    cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-datasources
  namespace: $namespace
  labels:
    app: prometheus-operator
    release: prometheus-operator
data:
  prometheus-datasources.yaml: |
    apiVersion: 1
    datasources:
    - name: Prometheus
      type: prometheus
      url: http://prometheus-operator-kube-prometheus-prometheus.$namespace.svc.cluster.local:9090
      access: proxy
      isDefault: true
      editable: false
    - name: Prometheus-$other_region
      type: prometheus
      url: $other_prometheus_url
      access: proxy
      isDefault: false
      editable: false
EOF
    
    # Create ConfigMap for Grafana dashboards provider
    cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-dashboards-provider
  namespace: $namespace
  labels:
    app: prometheus-operator
    release: prometheus-operator
data:
  dashboards.yaml: |
    apiVersion: 1
    providers:
    - name: 'default'
      orgId: 1
      folder: ''
      type: file
      disableDeletion: false
      editable: true
      options:
        path: /var/lib/grafana/dashboards
EOF
    
    # Create ConfigMap for cross-region overview dashboard
    cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-dashboard-cross-region-overview
  namespace: $namespace
  labels:
    app: prometheus-operator
    release: prometheus-operator
    grafana_dashboard: "1"
data:
  cross-region-overview.json: |
    {
      "annotations": {
        "list": []
      },
      "editable": true,
      "gnetId": null,
      "graphTooltip": 0,
      "id": null,
      "links": [],
      "panels": [
        {
          "datasource": "Prometheus",
          "fieldConfig": {
            "defaults": {
              "color": {
                "mode": "thresholds"
              },
              "mappings": [],
              "thresholds": {
                "mode": "absolute",
                "steps": [
                  {
                    "color": "red",
                    "value": null
                  },
                  {
                    "color": "green",
                    "value": 1
                  }
                ]
              }
            },
            "overrides": []
          },
          "gridPos": {
            "h": 8,
            "w": 12,
            "x": 0,
            "y": 0
          },
          "id": 1,
          "options": {
            "colorMode": "value",
            "graphMode": "area",
            "justifyMode": "auto",
            "orientation": "auto",
            "reduceOptions": {
              "calcs": [
                "lastNotNull"
              ],
              "fields": "",
              "values": false
            },
            "text": {},
            "textMode": "auto"
          },
          "pluginVersion": "8.0.6",
          "targets": [
            {
              "expr": "up{job=\"eden-services\", region=\"$region\"}",
              "interval": "",
              "legendFormat": "{{service}}",
              "refId": "A"
            }
          ],
          "title": "Service Availability - $region",
          "type": "stat"
        },
        {
          "datasource": "Prometheus-$other_region",
          "fieldConfig": {
            "defaults": {
              "color": {
                "mode": "thresholds"
              },
              "mappings": [],
              "thresholds": {
                "mode": "absolute",
                "steps": [
                  {
                    "color": "red",
                    "value": null
                  },
                  {
                    "color": "green",
                    "value": 1
                  }
                ]
              }
            },
            "overrides": []
          },
          "gridPos": {
            "h": 8,
            "w": 12,
            "x": 12,
            "y": 0
          },
          "id": 2,
          "options": {
            "colorMode": "value",
            "graphMode": "area",
            "justifyMode": "auto",
            "orientation": "auto",
            "reduceOptions": {
              "calcs": [
                "lastNotNull"
              ],
              "fields": "",
              "values": false
            },
            "text": {},
            "textMode": "auto"
          },
          "pluginVersion": "8.0.6",
          "targets": [
            {
              "expr": "up{job=\"eden-services\", region=\"$other_region\"}",
              "interval": "",
              "legendFormat": "{{service}}",
              "refId": "A"
            }
          ],
          "title": "Service Availability - $other_region",
          "type": "stat"
        },
        {
          "aliasColors": {},
          "bars": false,
          "dashLength": 10,
          "dashes": false,
          "datasource": "Prometheus",
          "fill": 1,
          "fillGradient": 0,
          "gridPos": {
            "h": 8,
            "w": 12,
            "x": 0,
            "y": 8
          },
          "hiddenSeries": false,
          "id": 3,
          "legend": {
            "avg": false,
            "current": false,
            "max": false,
            "min": false,
            "show": true,
            "total": false,
            "values": false
          },
          "lines": true,
          "linewidth": 1,
          "nullPointMode": "null",
          "options": {
            "alertThreshold": true
          },
          "percentage": false,
          "pluginVersion": "8.0.6",
          "pointradius": 2,
          "points": false,
          "renderer": "flot",
          "seriesOverrides": [],
          "spaceLength": 10,
          "stack": false,
          "steppedLine": false,
          "targets": [
            {
              "expr": "sum by(service) (rate(http_requests_total{job=\"eden-services\", region=\"$region\"}[5m]))",
              "interval": "",
              "legendFormat": "{{service}}",
              "refId": "A"
            }
          ],
          "thresholds": [],
          "timeFrom": null,
          "timeRegions": [],
          "timeShift": null,
          "title": "Request Rate - $region",
          "tooltip": {
            "shared": true,
            "sort": 0,
            "value_type": "individual"
          },
          "type": "graph",
          "xaxis": {
            "buckets": null,
            "mode": "time",
            "name": null,
            "show": true,
            "values": []
          },
          "yaxes": [
            {
              "format": "short",
              "label": null,
              "logBase": 1,
              "max": null,
              "min": null,
              "show": true
            },
            {
              "format": "short",
              "label": null,
              "logBase": 1,
              "max": null,
              "min": null,
              "show": true
            }
          ],
          "yaxis": {
            "align": false,
            "alignLevel": null
          }
        },
        {
          "aliasColors": {},
          "bars": false,
          "dashLength": 10,
          "dashes": false,
          "datasource": "Prometheus-$other_region",
          "fill": 1,
          "fillGradient": 0,
          "gridPos": {
            "h": 8,
            "w": 12,
            "x": 12,
            "y": 8
          },
          "hiddenSeries": false,
          "id": 4,
          "legend": {
            "avg": false,
            "current": false,
            "max": false,