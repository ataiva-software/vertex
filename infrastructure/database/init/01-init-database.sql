-- Eden DevOps Suite Database Initialization
-- This script sets up the basic database structure for development

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "timescaledb";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "btree_gin";

-- Create schemas
CREATE SCHEMA IF NOT EXISTS eden;
CREATE SCHEMA IF NOT EXISTS audit;

-- Set default schema
SET search_path TO eden, public;

-- Organizations table
CREATE TABLE IF NOT EXISTS organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    description TEXT DEFAULT '',
    settings JSONB DEFAULT '{}',
    plan VARCHAR(50) DEFAULT 'FREE',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    mfa_secret VARCHAR(255),
    profile JSONB DEFAULT '{}',
    is_active BOOLEAN DEFAULT true,
    email_verified BOOLEAN DEFAULT false,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- User organization memberships
CREATE TABLE IF NOT EXISTS user_organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL DEFAULT 'viewer',
    permissions JSONB DEFAULT '[]',
    is_active BOOLEAN DEFAULT true,
    invited_by UUID REFERENCES users(id),
    invited_at TIMESTAMPTZ,
    joined_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, organization_id)
);

-- Organization invitations
CREATE TABLE IF NOT EXISTS organization_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'viewer',
    permissions JSONB DEFAULT '[]',
    invited_by UUID NOT NULL REFERENCES users(id),
    token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- User sessions
CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(500) UNIQUE NOT NULL,
    refresh_token VARCHAR(500) UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    ip_address INET,
    user_agent TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

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

-- Task definitions
CREATE TABLE IF NOT EXISTS task_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT DEFAULT '',
    image VARCHAR(255) NOT NULL,
    command JSONB,
    environment JSONB DEFAULT '{}',
    resources JSONB DEFAULT '{}',
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
    worker_node VARCHAR(255),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    exit_code INTEGER,
    logs TEXT
);

-- Monitor checks
CREATE TABLE IF NOT EXISTS monitor_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    config JSONB NOT NULL,
    interval_seconds INTEGER DEFAULT 300,
    enabled BOOLEAN DEFAULT true,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(organization_id, name)
);

-- Monitor results (TimescaleDB hypertable)
CREATE TABLE IF NOT EXISTS monitor_results (
    time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    check_id UUID NOT NULL REFERENCES monitor_checks(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    response_time_ms INTEGER,
    status_code INTEGER,
    error_message TEXT,
    metadata JSONB DEFAULT '{}'
);

-- Convert monitor_results to hypertable
SELECT create_hypertable('monitor_results', 'time', if_not_exists => TRUE);

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

-- Convert audit_logs to hypertable
SELECT create_hypertable('audit.audit_logs', 'timestamp', if_not_exists => TRUE);

-- Create indexes for performance
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_organizations_user_id ON user_organizations(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_organizations_org_id ON user_organizations(organization_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_sessions_token ON user_sessions(token);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_vault_secrets_org_name ON vault_secrets(organization_id, name);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_vault_access_logs_secret_id ON vault_access_logs(secret_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_flow_workflows_org_id ON flow_workflows(organization_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_flow_executions_workflow_id ON flow_executions(workflow_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_task_definitions_org_id ON task_definitions(organization_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_task_executions_task_def_id ON task_executions(task_definition_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_monitor_checks_org_id ON monitor_checks(organization_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_monitor_results_check_id_time ON monitor_results(check_id, time DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_org_id ON audit.audit_logs(organization_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_user_id ON audit.audit_logs(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_timestamp ON audit.audit_logs(timestamp DESC);

-- Full-text search indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_vault_secrets_search 
    ON vault_secrets USING gin(to_tsvector('english', name || ' ' || COALESCE(metadata->>'description', '')));

-- Insert default data
INSERT INTO organizations (id, name, slug, description) 
VALUES ('00000000-0000-0000-0000-000000000001', 'Default Organization', 'default', 'Default organization for development')
ON CONFLICT (slug) DO NOTHING;

-- Insert default admin user (password: admin123)
INSERT INTO users (id, email, password_hash, is_active, email_verified) 
VALUES (
    '00000000-0000-0000-0000-000000000001', 
    'admin@eden.local', 
    '$2a$10$rZ8Q2Q8Q8Q8Q8Q8Q8Q8Q8O8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q', 
    true, 
    true
)
ON CONFLICT (email) DO NOTHING;

-- Add admin user to default organization as owner
INSERT INTO user_organizations (user_id, organization_id, role, joined_at) 
VALUES (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'owner',
    NOW()
)
ON CONFLICT (user_id, organization_id) DO NOTHING;

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Add updated_at triggers
CREATE TRIGGER update_organizations_updated_at BEFORE UPDATE ON organizations FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_user_organizations_updated_at BEFORE UPDATE ON user_organizations FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_vault_secrets_updated_at BEFORE UPDATE ON vault_secrets FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_flow_workflows_updated_at BEFORE UPDATE ON flow_workflows FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_task_definitions_updated_at BEFORE UPDATE ON task_definitions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_monitor_checks_updated_at BEFORE UPDATE ON monitor_checks FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();