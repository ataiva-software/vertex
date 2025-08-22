# Vertex Web Portal

The Vertex Web Portal provides a comprehensive web-based interface for managing all Vertex services through a single, intuitive dashboard.

## Overview

The web portal is automatically included with every Vertex installation and provides:

- **Unified Dashboard**: Access all 8 Vertex services from one interface
- **Real-time Operations**: Live interaction with all services
- **Visual Management**: Intuitive forms and displays for complex operations
- **Cross-Platform**: Works in any modern web browser
- **No Additional Setup**: Automatically served by the API Gateway

## Accessing the Web Portal

### Quick Start

```bash
# 1. Set master password
export VERTEX_MASTER_PASSWORD="your-secure-password"

# 2. Start Vertex server
./bin/vertex server

# 3. Open web portal
open http://localhost:8000
```

### Alternative URLs

- **Primary**: `http://localhost:8000`
- **Direct**: `http://localhost:8000/portal`
- **Static**: `http://localhost:8000/static/index.html`

## Service Interfaces

### 🔐 Vault Management

**Store Secrets**
- Key-value secret storage
- Optional descriptions and tags
- Encrypted storage with AES-256-GCM

**List & Manage Secrets**
- View all stored secrets
- Search and filter capabilities
- One-click deletion

**Retrieve Secrets**
- Secure secret retrieval
- Decrypted values displayed safely

### 🔄 Flow Management

**Workflow Creation**
- Visual workflow builder
- Name and description fields
- Template-based workflows

**Workflow Execution**
- Start/stop workflows
- Monitor execution status
- View workflow history

### ⚡ Task Management

**Task Overview**
- View all active tasks
- Task status monitoring
- Execution history

**Task Operations**
- Create new tasks
- Cancel running tasks
- View task details

### 📊 System Monitoring

**Service Health**
- Real-time health checks
- Service status indicators
- Performance metrics

**Metrics Dashboard**
- Service-specific metrics
- Historical data views
- Alert status

### 🔄 Sync Management

**Sync Jobs**
- View active sync operations
- Create new sync jobs
- Monitor sync progress

**Multi-Cloud Operations**
- AWS, GCP, Azure integration
- Cross-cloud synchronization
- Status monitoring

### 📈 Analytics & Insights

**Reports Dashboard**
- System analytics
- Usage reports
- Performance insights

**Data Visualization**
- Charts and graphs
- Trend analysis
- Export capabilities

### 🔗 Integration Hub

**Integration Management**
- View active integrations
- Add new integrations
- Configuration management

**Service Discovery**
- Available services
- Connection status
- Health monitoring

## Features

### Security

- **Encrypted Communication**: All API calls use HTTPS-ready endpoints
- **User Authentication**: Consistent user identification across services
- **Audit Logging**: All operations logged for compliance
- **Secure Storage**: Secrets encrypted at rest

### User Experience

- **Responsive Design**: Works on desktop, tablet, and mobile
- **Real-time Updates**: Live data refresh without page reload
- **Error Handling**: Clear error messages and recovery guidance
- **Intuitive Navigation**: Tab-based interface for easy service switching

### Performance

- **Direct API Calls**: Each service called directly for optimal performance
- **Minimal Overhead**: Lightweight interface with fast load times
- **Efficient Updates**: Only necessary data refreshed
- **Browser Caching**: Static assets cached for speed

## API Integration

The web portal communicates directly with each service:

```javascript
// Service endpoints
const API_ENDPOINTS = {
    vault: 'http://localhost:8080/api/v1',
    flow: 'http://localhost:8081/api/v1', 
    task: 'http://localhost:8082/api/v1',
    monitor: 'http://localhost:8083/api/v1',
    sync: 'http://localhost:8084/api/v1',
    insight: 'http://localhost:8085/api/v1',
    hub: 'http://localhost:8086/api/v1'
};
```

### Authentication

All requests include consistent user identification:

```javascript
headers: {
    'Content-Type': 'application/json',
    'X-User-ID': 'web-user'
}
```

## Troubleshooting

### Common Issues

**Portal Not Loading**
```bash
# Check if API Gateway is running
curl http://localhost:8000/health

# Restart server if needed
./bin/vertex server
```

**Service Unavailable Errors**
```bash
# Check individual service status
./bin/vertex status

# Start specific service
./bin/vertex service vault --port 8080
```

**Empty Data Displays**
- Ensure services are running on correct ports
- Check service health endpoints
- Verify master password is set

### Port Configuration

Default service ports:
- **API Gateway**: 8000 (Web Portal)
- **Vault**: 8080
- **Flow**: 8081
- **Task**: 8082
- **Monitor**: 8083
- **Sync**: 8084
- **Insight**: 8085
- **Hub**: 8086

## Development

### Customization

The web portal is served from `./web/index.html` and can be customized:

```bash
# Edit the portal
vim web/index.html

# Restart to see changes
./bin/vertex server
```

### API Testing

Test individual service APIs:

```bash
# Test vault service
curl -H "X-User-ID: web-user" http://localhost:8080/api/v1/secrets

# Test flow service
curl -H "X-User-ID: web-user" http://localhost:8081/api/v1/workflows
```

## Best Practices

### Security

1. **Always set VERTEX_MASTER_PASSWORD** before starting
2. **Use HTTPS in production** environments
3. **Regularly rotate master password**
4. **Monitor access logs** for suspicious activity

### Operations

1. **Check service health** before operations
2. **Use descriptive names** for secrets and workflows
3. **Tag resources** for better organization
4. **Monitor system metrics** regularly

### Performance

1. **Close unused tabs** to reduce browser load
2. **Refresh data periodically** for accuracy
3. **Use CLI for bulk operations**
4. **Monitor service resource usage**

## Integration with CLI

The web portal complements the CLI interface:

```bash
# CLI operations
./bin/vertex vault list --format yaml
./bin/vertex flow run my-workflow
./bin/vertex status

# Web portal provides visual interface for same operations
open http://localhost:8000
```

Both interfaces work with the same data and services, providing flexibility for different use cases and user preferences.
