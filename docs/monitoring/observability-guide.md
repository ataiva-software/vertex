# Eden DevOps Suite Observability Guide

This guide provides comprehensive information on the monitoring and observability features implemented in the Eden DevOps Suite.

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Distributed Tracing](#distributed-tracing)
4. [Metrics Collection](#metrics-collection)
5. [Logging](#logging)
6. [Alerting](#alerting)
7. [Health Checks](#health-checks)
8. [Service Dependency Maps](#service-dependency-maps)
9. [Performance Monitoring](#performance-monitoring)
10. [Audit Logging](#audit-logging)
11. [Dashboards](#dashboards)
12. [Troubleshooting](#troubleshooting)

## Overview

The Eden DevOps Suite implements a comprehensive observability solution based on the three pillars of observability:

1. **Metrics**: Quantitative measurements of system behavior over time
2. **Logs**: Detailed records of events that occurred in the system
3. **Traces**: Information about request flows through distributed systems

Our implementation uses industry-standard tools and follows best practices to provide a production-ready observability solution.

## Architecture

The observability stack consists of the following components:

- **OpenTelemetry**: For instrumentation and data collection
- **OpenTelemetry Collector**: For processing and exporting telemetry data
- **Prometheus**: For metrics storage and querying
- **Grafana**: For metrics visualization and dashboards
- **Tempo**: For distributed trace storage and analysis
- **Jaeger**: For distributed trace visualization
- **Elasticsearch**: For log storage and search
- **Logstash**: For log processing and enrichment
- **Kibana**: For log visualization and analysis
- **Filebeat**: For log collection
- **AlertManager**: For alert management and notifications

![Observability Architecture](../images/observability-architecture.png)

## Distributed Tracing

Distributed tracing is implemented using OpenTelemetry and provides end-to-end visibility into request flows across services.

### How It Works

1. Each service is instrumented with OpenTelemetry to capture spans
2. Spans are sent to the OpenTelemetry Collector
3. The collector exports spans to Jaeger and Tempo
4. Traces can be visualized in Jaeger UI or Grafana

### Usage

To view traces:

1. Access the Jaeger UI at `http://localhost:16686`
2. Select a service from the dropdown
3. Configure the search parameters (time range, tags, etc.)
4. Click "Find Traces"

Alternatively, you can view traces in Grafana:

1. Access Grafana at `http://localhost:3000`
2. Navigate to the "Explore" section
3. Select "Tempo" as the data source
4. Search for traces by trace ID, service name, or span name

### Adding Custom Spans

You can add custom spans to your code using the OpenTelemetry API:

```go
// Using the PerformanceMonitor utility
val performanceMonitor = PerformanceMonitor(openTelemetry, "my-service")

// Track a synchronous operation
val result = performanceMonitor.trackOperation("operation-name") {
    // Your code here
    doSomething()
}

// Track a suspending operation
val result = performanceMonitor.trackSuspendOperation("operation-name") {
    // Your suspending code here
    doSomethingAsync()
}

// Or use the extension functions
val result = performanceMonitor.measure("operation-name") {
    // Your code here
    doSomething()
}

val result = performanceMonitor.measureSuspend("operation-name") {
    // Your suspending code here
    doSomethingAsync()
}
```

## Metrics Collection

Metrics are collected using OpenTelemetry and stored in Prometheus.

### Default Metrics

The following metrics are collected by default:

- **System metrics**: CPU, memory, disk, network
- **JVM metrics**: Heap memory, GC, threads
- **HTTP metrics**: Request count, duration, errors
- **Database metrics**: Query count, duration
- **Custom application metrics**: As defined by your application

### Adding Custom Metrics

You can add custom metrics using the MetricsRegistry utility:

```go
val metricsRegistry = MetricsRegistry(openTelemetry, "my-service")

// Create a counter
val requestCounter = metricsRegistry.counter(
    name = "my_requests_total",
    description = "Total number of requests"
)

// Increment the counter
requestCounter.add(1, Attributes.builder().put("endpoint", "/api/users").build())

// Or use the convenience methods
metricsRegistry.incrementCounter("my_requests_total", "endpoint" to "/api/users")
```

### Viewing Metrics

Metrics can be viewed in Grafana dashboards or directly in Prometheus:

1. Access Prometheus at `http://localhost:9090`
2. Enter a query in the expression field
3. Click "Execute"

## Logging

Logs are collected using Filebeat, processed by Logstash, and stored in Elasticsearch.

### Log Format

Logs should be formatted as JSON with the following fields:

```json
{
  "timestamp": "2025-06-06T00:42:00.000Z",
  "level": "INFO",
  "service": "my-service",
  "message": "Request processed successfully",
  "trace_id": "1234567890abcdef",
  "span_id": "fedcba0987654321",
  "additional_field": "value"
}
```

### Viewing Logs

Logs can be viewed in Kibana:

1. Access Kibana at `http://localhost:5601`
2. Navigate to "Discover"
3. Select the "eden-logs-*" index pattern
4. Configure the time range and search parameters

### Correlation with Traces

Logs include trace and span IDs, allowing correlation with distributed traces. In Kibana, you can click on a trace ID to view the corresponding trace in Jaeger or Grafana.

## Alerting

Alerts are configured in Prometheus and managed by AlertManager.

### Default Alerts

The following alerts are configured by default:

- **InstanceDown**: Triggers when a service instance is down
- **HighCPUUsage**: Triggers when CPU usage is above 80% for 5 minutes
- **HighMemoryUsage**: Triggers when memory usage is above 80% for 5 minutes
- **HighDiskUsage**: Triggers when disk usage is above 80%
- **HighErrorRate**: Triggers when the error rate is above 5% for 5 minutes

### Adding Custom Alerts

Custom alerts can be added to the Prometheus configuration:

```yaml
groups:
  - name: custom-alerts
    rules:
      - alert: CustomAlert
        expr: my_metric > 100
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Custom alert triggered"
          description: "My metric is above 100 for 5 minutes"
```

### Alert Notifications

Alerts can be sent to various channels:

- Email
- Slack
- PagerDuty
- Webhook

Configure notification channels in the AlertManager configuration.

## Health Checks

Health checks are implemented using the HealthCheck utility.

### Default Health Checks

The following health checks are implemented by default:

- **Database**: Checks database connectivity
- **Redis**: Checks Redis connectivity
- **Dependencies**: Checks connectivity to dependent services
- **Disk Space**: Checks available disk space
- **Memory**: Checks available memory

### Adding Custom Health Checks

You can add custom health checks:

```go
val healthCheckRegistry = HealthCheckRegistry()

// Add a custom health check
healthCheckRegistry.register(object : HealthCheck {
    override val name = "custom-check"
    override val timeout = 5.seconds
    
    override suspend fun check(): HealthCheckResult {
        val isHealthy = checkSomething()
        return if (isHealthy) {
            HealthCheckResult(
                name = name,
                status = HealthStatus.UP,
                message = "Custom check passed"
            )
        } else {
            HealthCheckResult(
                name = name,
                status = HealthStatus.DOWN,
                message = "Custom check failed"
            )
        }
    }
})

// Run all health checks
val result = healthCheckRegistry.runHealthChecks()
```

### Health Check Endpoints

Each service exposes a health check endpoint at `/health` that returns the status of all health checks.

## Service Dependency Maps

Service dependency maps are generated using the ServiceDependencyMapper utility and OpenTelemetry trace data.

### Viewing Dependency Maps

Dependency maps can be viewed in Grafana:

1. Access Grafana at `http://localhost:3000`
2. Navigate to the "Service Dependencies" dashboard

### Generating Dependency Maps

You can also generate dependency maps programmatically:

```go
val dependencyMapper = ServiceDependencyMapper(openTelemetry, "my-service")

// Record a dependency call
dependencyMapper.recordDependencyCall(
    targetService = "other-service",
    endpoint = "/api/resource",
    latencyMs = 42,
    isError = false
)

// Generate a dependency graph
val graph = dependencyMapper.generateDependencyGraph()

// Export the graph as DOT format for visualization with Graphviz
dependencyMapper.exportDependencyGraphAsDot("dependencies.dot")
```

## Performance Monitoring

Performance monitoring is implemented using the PerformanceMonitor utility.

### Monitoring Operations

You can monitor the performance of operations:

```go
val performanceMonitor = PerformanceMonitor(openTelemetry, "my-service")

// Track an operation
val result = performanceMonitor.trackOperation("operation-name") {
    // Your code here
    doSomething()
}

// Get performance data for an operation
val data = performanceMonitor.getOperationPerformanceData("operation-name")
println("Average time: ${data.averageTimeMs} ms")
println("Error rate: ${data.errorCount.toDouble() / data.invocationCount}")

// Get system performance data
val systemData = performanceMonitor.getSystemPerformanceData()
println("CPU usage: ${systemData.cpuUsagePercent}%")
println("Memory usage: ${systemData.heapMemoryUsagePercent}%")
```

### Viewing Performance Data

Performance data can be viewed in Grafana dashboards.

## Audit Logging

Audit logging is implemented using the AuditLogger utility.

### Logging Audit Events

You can log audit events:

```go
val auditLogger = AuditLogger(openTelemetry, "my-service")

// Log a user login event
auditLogger.logUserLogin(
    userId = "user123",
    username = "john.doe",
    outcome = "success",
    ipAddress = "192.168.1.1",
    userAgent = "Mozilla/5.0 ..."
)

// Log a resource access event
auditLogger.logResourceAccess(
    userId = "user123",
    username = "john.doe",
    resourceType = "document",
    resourceId = "doc123",
    action = "view",
    outcome = "success",
    ipAddress = "192.168.1.1",
    userAgent = "Mozilla/5.0 ..."
)
```

### Viewing Audit Logs

Audit logs can be viewed in Kibana:

1. Access Kibana at `http://localhost:5601`
2. Navigate to "Discover"
3. Select the "eden-logs-*" index pattern
4. Filter by `type: audit`

## Dashboards

The following Grafana dashboards are available:

- **System Overview**: System-level metrics (CPU, memory, disk, network)
- **Service Health**: Service-level metrics (request rate, error rate, response time)
- **Distributed Tracing**: Trace metrics and service dependencies
- **JVM Metrics**: JVM-specific metrics (heap memory, GC, threads)
- **Database Performance**: Database metrics (query rate, error rate, response time)
- **Redis Performance**: Redis metrics (command rate, error rate, response time)
- **API Gateway**: API Gateway metrics (request rate, error rate, response time by endpoint)

### Accessing Dashboards

1. Access Grafana at `http://localhost:3000`
2. Log in with the default credentials (admin/admin)
3. Navigate to the "Dashboards" section
4. Select a dashboard from the list

## Troubleshooting

### Common Issues

#### No Data in Grafana

- Check that Prometheus is running: `docker-compose ps prometheus`
- Check that services are exposing metrics: `curl http://service:port/metrics`
- Check Prometheus targets: `http://localhost:9090/targets`

#### No Traces in Jaeger

- Check that the OpenTelemetry Collector is running: `docker-compose ps otel-collector`
- Check that services are sending traces: Look for log messages about trace export
- Check Jaeger collector status: `http://localhost:16686/api/services`

#### No Logs in Kibana

- Check that Elasticsearch is running: `docker-compose ps elasticsearch`
- Check that Logstash is running: `docker-compose ps logstash`
- Check that Filebeat is running: `docker-compose ps filebeat`
- Check Elasticsearch indices: `curl http://localhost:9200/_cat/indices`

### Getting Help

If you encounter issues with the observability stack, please:

1. Check the logs of the relevant components
2. Consult the documentation of the specific tool
3. Contact the Eden DevOps team for assistance