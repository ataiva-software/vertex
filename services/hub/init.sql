-- Eden Hub Service Database Initialization Script
-- This script creates the necessary tables and indexes for the Hub Service

-- Enable UUID extension for PostgreSQL
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Integrations table
CREATE TABLE IF NOT EXISTS integrations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    description TEXT,
    configuration JSONB NOT NULL DEFAULT '{}',
    credentials_type VARCHAR(50) NOT NULL,
    encrypted_credentials TEXT NOT NULL,
    encryption_key_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    user_id VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_tested_at TIMESTAMP WITH TIME ZONE,
    last_test_result JSONB
);

-- Webhooks table
CREATE TABLE IF NOT EXISTS webhooks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    url TEXT NOT NULL,
    events TEXT[] NOT NULL DEFAULT '{}',
    description TEXT,
    secret VARCHAR(255),
    headers JSONB DEFAULT '{}',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    user_id VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_delivery_at TIMESTAMP WITH TIME ZONE,
    delivery_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    failure_count INTEGER DEFAULT 0
);

-- Webhook deliveries table
CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    webhook_id UUID NOT NULL REFERENCES webhooks(id) ON DELETE CASCADE,
    event VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    response_status INTEGER,
    response_body TEXT,
    response_headers JSONB,
    attempt_count INTEGER DEFAULT 0,
    max_attempts INTEGER DEFAULT 5,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT
);

-- Notification templates table
CREATE TABLE IF NOT EXISTS notification_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    subject VARCHAR(500),
    body TEXT NOT NULL,
    variables TEXT[] DEFAULT '{}',
    metadata JSONB DEFAULT '{}',
    user_id VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Notification deliveries table
CREATE TABLE IF NOT EXISTS notification_deliveries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    template_id UUID REFERENCES notification_templates(id) ON DELETE SET NULL,
    type VARCHAR(50) NOT NULL,
    recipients JSONB NOT NULL,
    subject VARCHAR(500),
    body TEXT NOT NULL,
    variables JSONB DEFAULT '{}',
    priority VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    user_id VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    metadata JSONB DEFAULT '{}'
);

-- Event subscriptions table
CREATE TABLE IF NOT EXISTS event_subscriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_types TEXT[] NOT NULL DEFAULT '{}',
    endpoint TEXT NOT NULL,
    secret VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    user_id VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_delivery_at TIMESTAMP WITH TIME ZONE,
    delivery_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    failure_count INTEGER DEFAULT 0
);

-- Events table (for audit and history)
CREATE TABLE IF NOT EXISTS events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type VARCHAR(255) NOT NULL,
    source VARCHAR(255) NOT NULL,
    data JSONB NOT NULL DEFAULT '{}',
    user_id VARCHAR(255),
    organization_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'
);

-- Integration operations log
CREATE TABLE IF NOT EXISTS integration_operations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    integration_id UUID NOT NULL REFERENCES integrations(id) ON DELETE CASCADE,
    operation VARCHAR(255) NOT NULL,
    parameters JSONB DEFAULT '{}',
    status VARCHAR(50) NOT NULL,
    result JSONB,
    error_message TEXT,
    duration_ms INTEGER,
    user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_integrations_user_id ON integrations(user_id);
CREATE INDEX IF NOT EXISTS idx_integrations_organization_id ON integrations(organization_id);
CREATE INDEX IF NOT EXISTS idx_integrations_type ON integrations(type);
CREATE INDEX IF NOT EXISTS idx_integrations_status ON integrations(status);
CREATE INDEX IF NOT EXISTS idx_integrations_created_at ON integrations(created_at);

CREATE INDEX IF NOT EXISTS idx_webhooks_user_id ON webhooks(user_id);
CREATE INDEX IF NOT EXISTS idx_webhooks_organization_id ON webhooks(organization_id);
CREATE INDEX IF NOT EXISTS idx_webhooks_status ON webhooks(status);
CREATE INDEX IF NOT EXISTS idx_webhooks_events ON webhooks USING GIN(events);
CREATE INDEX IF NOT EXISTS idx_webhooks_created_at ON webhooks(created_at);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_webhook_id ON webhook_deliveries(webhook_id);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_status ON webhook_deliveries(status);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_event ON webhook_deliveries(event);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_created_at ON webhook_deliveries(created_at);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_next_retry_at ON webhook_deliveries(next_retry_at);

CREATE INDEX IF NOT EXISTS idx_notification_templates_user_id ON notification_templates(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_templates_organization_id ON notification_templates(organization_id);
CREATE INDEX IF NOT EXISTS idx_notification_templates_type ON notification_templates(type);
CREATE INDEX IF NOT EXISTS idx_notification_templates_created_at ON notification_templates(created_at);

CREATE INDEX IF NOT EXISTS idx_notification_deliveries_template_id ON notification_deliveries(template_id);
CREATE INDEX IF NOT EXISTS idx_notification_deliveries_user_id ON notification_deliveries(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_deliveries_organization_id ON notification_deliveries(organization_id);
CREATE INDEX IF NOT EXISTS idx_notification_deliveries_type ON notification_deliveries(type);
CREATE INDEX IF NOT EXISTS idx_notification_deliveries_status ON notification_deliveries(status);
CREATE INDEX IF NOT EXISTS idx_notification_deliveries_priority ON notification_deliveries(priority);
CREATE INDEX IF NOT EXISTS idx_notification_deliveries_created_at ON notification_deliveries(created_at);

CREATE INDEX IF NOT EXISTS idx_event_subscriptions_user_id ON event_subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_event_subscriptions_organization_id ON event_subscriptions(organization_id);
CREATE INDEX IF NOT EXISTS idx_event_subscriptions_status ON event_subscriptions(status);
CREATE INDEX IF NOT EXISTS idx_event_subscriptions_event_types ON event_subscriptions USING GIN(event_types);
CREATE INDEX IF NOT EXISTS idx_event_subscriptions_created_at ON event_subscriptions(created_at);

CREATE INDEX IF NOT EXISTS idx_events_type ON events(type);
CREATE INDEX IF NOT EXISTS idx_events_source ON events(source);
CREATE INDEX IF NOT EXISTS idx_events_user_id ON events(user_id);
CREATE INDEX IF NOT EXISTS idx_events_organization_id ON events(organization_id);
CREATE INDEX IF NOT EXISTS idx_events_created_at ON events(created_at);
CREATE INDEX IF NOT EXISTS idx_events_data ON events USING GIN(data);

CREATE INDEX IF NOT EXISTS idx_integration_operations_integration_id ON integration_operations(integration_id);
CREATE INDEX IF NOT EXISTS idx_integration_operations_user_id ON integration_operations(user_id);
CREATE INDEX IF NOT EXISTS idx_integration_operations_operation ON integration_operations(operation);
CREATE INDEX IF NOT EXISTS idx_integration_operations_status ON integration_operations(status);
CREATE INDEX IF NOT EXISTS idx_integration_operations_created_at ON integration_operations(created_at);

-- Create triggers for updating updated_at timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_integrations_updated_at BEFORE UPDATE ON integrations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_webhooks_updated_at BEFORE UPDATE ON webhooks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notification_templates_updated_at BEFORE UPDATE ON notification_templates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_event_subscriptions_updated_at BEFORE UPDATE ON event_subscriptions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert some sample data for development/testing
INSERT INTO integrations (name, type, description, configuration, credentials_type, encrypted_credentials, user_id, organization_id)
VALUES 
    ('Sample GitHub Integration', 'GITHUB', 'Sample GitHub integration for testing', 
     '{"baseUrl": "https://api.github.com", "owner": "sampleorg"}', 
     'TOKEN', 'encrypted-sample-token', 'dev-user-1', 'dev-org-1'),
    ('Sample Slack Integration', 'SLACK', 'Sample Slack integration for testing',
     '{"baseUrl": "https://slack.com/api"}',
     'TOKEN', 'encrypted-sample-slack-token', 'dev-user-1', 'dev-org-1')
ON CONFLICT DO NOTHING;

INSERT INTO notification_templates (name, type, subject, body, variables, user_id, organization_id)
VALUES 
    ('Welcome Email', 'EMAIL', 'Welcome to {{platform}}!', 
     'Hello {{name}},\n\nWelcome to {{platform}}! We''re excited to have you on board.\n\nBest regards,\nThe {{platform}} Team',
     ARRAY['name', 'platform'], 'dev-user-1', 'dev-org-1'),
    ('Deployment Notification', 'SLACK', 'Deployment {{status}}',
     'Deployment {{status}} for {{repository}} by {{author}}. {{#if success}}✅ Success{{else}}❌ Failed{{/if}}',
     ARRAY['status', 'repository', 'author', 'success'], 'dev-user-1', 'dev-org-1')
ON CONFLICT DO NOTHING;

-- Create a view for integration statistics
CREATE OR REPLACE VIEW integration_stats AS
SELECT 
    i.id,
    i.name,
    i.type,
    i.status,
    i.user_id,
    i.organization_id,
    COUNT(io.id) as total_operations,
    COUNT(CASE WHEN io.status = 'SUCCESS' THEN 1 END) as successful_operations,
    COUNT(CASE WHEN io.status = 'FAILED' THEN 1 END) as failed_operations,
    AVG(io.duration_ms) as avg_duration_ms,
    MAX(io.created_at) as last_operation_at
FROM integrations i
LEFT JOIN integration_operations io ON i.id = io.integration_id
GROUP BY i.id, i.name, i.type, i.status, i.user_id, i.organization_id;

-- Create a view for webhook statistics
CREATE OR REPLACE VIEW webhook_stats AS
SELECT 
    w.id,
    w.name,
    w.url,
    w.status,
    w.user_id,
    w.organization_id,
    COUNT(wd.id) as total_deliveries,
    COUNT(CASE WHEN wd.status = 'DELIVERED' THEN 1 END) as successful_deliveries,
    COUNT(CASE WHEN wd.status = 'FAILED' THEN 1 END) as failed_deliveries,
    AVG(wd.attempt_count) as avg_attempts,
    MAX(wd.created_at) as last_delivery_at
FROM webhooks w
LEFT JOIN webhook_deliveries wd ON w.id = wd.webhook_id
GROUP BY w.id, w.name, w.url, w.status, w.user_id, w.organization_id;

-- Create a view for notification statistics
CREATE OR REPLACE VIEW notification_stats AS
SELECT 
    nt.id,
    nt.name,
    nt.type,
    nt.user_id,
    nt.organization_id,
    COUNT(nd.id) as total_notifications,
    COUNT(CASE WHEN nd.status = 'DELIVERED' THEN 1 END) as successful_notifications,
    COUNT(CASE WHEN nd.status = 'FAILED' THEN 1 END) as failed_notifications,
    COUNT(CASE WHEN nd.priority = 'HIGH' THEN 1 END) as high_priority_notifications,
    MAX(nd.created_at) as last_notification_at
FROM notification_templates nt
LEFT JOIN notification_deliveries nd ON nt.id = nd.template_id
GROUP BY nt.id, nt.name, nt.type, nt.user_id, nt.organization_id;

COMMIT;