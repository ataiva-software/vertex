-- Eden DevOps Suite - Core Business Schema Implementation
-- This migration implements the complete schema for core business entities

-- Vault secrets
CREATE TABLE IF NOT EXISTS vault_secrets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    encrypted_value TEXT NOT NULL,
    metadata JSONB DEFAULT '{}',
    version INTEGER DEFAULT 1,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(organization_id, name)
);

-- Vault access logs
CREATE TABLE IF NOT EXISTS vault_access_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    secret_id UUID NOT NULL REFERENCES vault_secrets(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Flow workflows
CREATE TABLE IF NOT EXISTS flow_workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT DEFAULT '',
    definition JSONB NOT NULL,
    status VARCHAR(50) DEFAULT 'active',
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(organization_id, name)
);

-- Flow executions
CREATE TABLE IF NOT EXISTS flow_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES flow_workflows(id) ON DELETE CASCADE,
    status VARCHAR(50) DEFAULT 'running',
    input_data JSONB,
    output_data JSONB,
    execution_log JSONB DEFAULT '[]',
    started_at TIMESTAMPTZ DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

-- Flow execution steps
CREATE TABLE IF NOT EXISTS flow_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID NOT NULL REFERENCES flow_executions(id) ON DELETE CASCADE,
    step_name VARCHAR(255) NOT NULL,
    step_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) DEFAULT 'pending',
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms INTEGER,
    step_order INTEGER NOT NULL
);

-- Task definitions
CREATE TABLE IF NOT EXISTS task_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT DEFAULT '',
    task_type VARCHAR(100) NOT NULL,
    configuration JSONB NOT NULL,
    schedule_cron VARCHAR(255),
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(organization_id, name)
);

-- Task executions
CREATE TABLE IF NOT EXISTS task_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_definition_id UUID NOT NULL REFERENCES task_definitions(id) ON DELETE CASCADE,
    status VARCHAR(50) DEFAULT 'pending',
    priority INTEGER DEFAULT 0,
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    progress_percentage INTEGER DEFAULT 0,
    queued_at TIMESTAMP DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms INTEGER
);

-- System events
CREATE TABLE IF NOT EXISTS system_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    source_service VARCHAR(100) NOT NULL,
    event_data JSONB NOT NULL,
    severity VARCHAR(50) DEFAULT 'info',
    user_id UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Audit logs
CREATE TABLE IF NOT EXISTS audit.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES eden.organizations(id),
    user_id UUID REFERENCES eden.users(id),
    action VARCHAR(100) NOT NULL,
    resource VARCHAR(100) NOT NULL,
    resource_id UUID,
    details JSONB DEFAULT '{}',
    ip_address INET,
    user_agent TEXT,
    timestamp TIMESTAMPTZ DEFAULT NOW(),
    severity VARCHAR(20) DEFAULT 'INFO'
);

-- Convert audit_logs to hypertable if TimescaleDB is available
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        PERFORM create_hypertable('audit.audit_logs', 'timestamp', if_not_exists => TRUE);
    END IF;
END
$$;

-- Create indexes for performance
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_vault_secrets_org_name ON vault_secrets(organization_id, name);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_vault_access_logs_secret_id ON vault_access_logs(secret_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_flow_workflows_org_id ON flow_workflows(organization_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_flow_executions_workflow_id ON flow_executions(workflow_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_flow_steps_execution_id ON flow_steps(execution_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_task_definitions_org_id ON task_definitions(organization_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_task_executions_task_def_id ON task_executions(task_definition_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_system_events_type ON system_events(event_type);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_org_id ON audit.audit_logs(organization_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_timestamp ON audit.audit_logs(timestamp DESC);

-- Full-text search indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_vault_secrets_search 
    ON vault_secrets USING gin(to_tsvector('english', name || ' ' || COALESCE(metadata->>'description', '')));

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_flow_workflows_search 
    ON flow_workflows USING gin(to_tsvector('english', name || ' ' || COALESCE(description, '')));

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_task_definitions_search 
    ON task_definitions USING gin(to_tsvector('english', name || ' ' || COALESCE(description, '')));

-- Add updated_at triggers
CREATE TRIGGER update_vault_secrets_updated_at BEFORE UPDATE ON vault_secrets FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_flow_workflows_updated_at BEFORE UPDATE ON flow_workflows FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_task_definitions_updated_at BEFORE UPDATE ON task_definitions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();