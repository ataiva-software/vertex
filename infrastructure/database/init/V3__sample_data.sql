-- Eden DevOps Suite - Sample Data for Development and Testing
-- This migration adds sample data for development and testing purposes

-- Insert default organization
INSERT INTO organizations (id, name, slug, description, plan) 
VALUES (
    '00000000-0000-0000-0000-000000000001', 
    'Default Organization', 
    'default', 
    'Default organization for development and testing',
    'PROFESSIONAL'
) ON CONFLICT (slug) DO NOTHING;

-- Insert sample users
INSERT INTO users (id, email, password_hash, full_name, is_active, is_verified) 
VALUES 
    (
        '00000000-0000-0000-0000-000000000001', 
        'admin@eden.local', 
        '$2a$10$rZ8Q2Q8Q8Q8Q8Q8Q8Q8Q8O8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q', 
        'Eden Administrator',
        true, 
        true
    ),
    (
        '00000000-0000-0000-0000-000000000002', 
        'developer@eden.local', 
        '$2a$10$rZ8Q2Q8Q8Q8Q8Q8Q8Q8Q8O8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q', 
        'Eden Developer',
        true, 
        true
    ),
    (
        '00000000-0000-0000-0000-000000000003', 
        'user@eden.local', 
        '$2a$10$rZ8Q2Q8Q8Q8Q8Q8Q8Q8Q8O8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q8Q', 
        'Eden User',
        true, 
        false
    )
ON CONFLICT (email) DO NOTHING;

-- Insert sample secrets for testing Vault functionality
INSERT INTO secrets (id, name, encrypted_value, encryption_key_id, secret_type, description, user_id, organization_id, version) 
VALUES 
    (
        '10000000-0000-0000-0000-000000000001',
        'database-password',
        'encrypted_db_password_placeholder',
        'key-001',
        'database',
        'Production database password',
        '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',
        1
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        'api-key-github',
        'encrypted_github_token_placeholder',
        'key-002',
        'api_token',
        'GitHub API access token',
        '00000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000001',
        1
    ),
    (
        '10000000-0000-0000-0000-000000000003',
        'ssl-certificate',
        'encrypted_ssl_cert_placeholder',
        'key-003',
        'certificate',
        'SSL certificate for production',
        '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',
        1
    )
ON CONFLICT (name, user_id, version) DO NOTHING;

-- Insert sample workflows for testing Flow functionality
INSERT INTO workflows (id, name, description, definition, user_id, status, version) 
VALUES 
    (
        '20000000-0000-0000-0000-000000000001',
        'deploy-to-staging',
        'Deploy application to staging environment',
        '{
            "name": "deploy-to-staging",
            "description": "Deploy application to staging environment",
            "version": "1.0",
            "steps": [
                {
                    "name": "checkout-code",
                    "type": "git",
                    "configuration": {
                        "repository": "https://github.com/example/app.git",
                        "branch": "develop"
                    }
                },
                {
                    "name": "run-tests",
                    "type": "test",
                    "configuration": {
                        "command": "npm test",
                        "timeout": "300s"
                    }
                },
                {
                    "name": "build-image",
                    "type": "docker",
                    "configuration": {
                        "dockerfile": "Dockerfile",
                        "tag": "staging-${BUILD_NUMBER}"
                    }
                },
                {
                    "name": "deploy",
                    "type": "kubernetes",
                    "configuration": {
                        "namespace": "staging",
                        "manifest": "k8s/staging.yaml"
                    }
                }
            ]
        }',
        '00000000-0000-0000-0000-000000000001',
        'active',
        1
    ),
    (
        '20000000-0000-0000-0000-000000000002',
        'backup-database',
        'Daily database backup workflow',
        '{
            "name": "backup-database",
            "description": "Daily database backup workflow",
            "version": "1.0",
            "steps": [
                {
                    "name": "create-backup",
                    "type": "database",
                    "configuration": {
                        "type": "postgresql",
                        "host": "${DB_HOST}",
                        "database": "${DB_NAME}",
                        "output": "/backups/db-${DATE}.sql"
                    }
                },
                {
                    "name": "upload-to-s3",
                    "type": "storage",
                    "configuration": {
                        "provider": "aws-s3",
                        "bucket": "eden-backups",
                        "path": "database/${DATE}/"
                    }
                },
                {
                    "name": "cleanup-old-backups",
                    "type": "cleanup",
                    "configuration": {
                        "retention_days": 30,
                        "path": "/backups/"
                    }
                }
            ]
        }',
        '00000000-0000-0000-0000-000000000002',
        'active',
        1
    )
ON CONFLICT (name, user_id) DO NOTHING;

-- Insert sample tasks for testing Task functionality
INSERT INTO tasks (id, name, description, task_type, configuration, schedule_cron, user_id) 
VALUES 
    (
        '30000000-0000-0000-0000-000000000001',
        'health-check-api',
        'Monitor API health endpoints',
        'http_check',
        '{
            "url": "https://api.eden.local/health",
            "method": "GET",
            "expected_status": 200,
            "timeout": 30,
            "retry_count": 3
        }',
        '*/5 * * * *',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '30000000-0000-0000-0000-000000000002',
        'cleanup-temp-files',
        'Clean up temporary files older than 7 days',
        'file_cleanup',
        '{
            "path": "/tmp/eden",
            "pattern": "*.tmp",
            "max_age_days": 7,
            "recursive": true
        }',
        '0 2 * * *',
        '00000000-0000-0000-0000-000000000002'
    ),
    (
        '30000000-0000-0000-0000-000000000003',
        'sync-user-data',
        'Synchronize user data with external systems',
        'data_sync',
        '{
            "source": "ldap://company.local",
            "destination": "database",
            "mapping": {
                "email": "mail",
                "name": "displayName",
                "department": "department"
            }
        }',
        '0 1 * * *',
        '00000000-0000-0000-0000-000000000001'
    )
ON CONFLICT DO NOTHING;

-- Insert sample workflow executions for testing
INSERT INTO workflow_executions (id, workflow_id, triggered_by, status, input_data, started_at) 
VALUES 
    (
        '40000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',
        'completed',
        '{"branch": "develop", "build_number": "123"}',
        NOW() - INTERVAL '1 hour'
    ),
    (
        '40000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000002',
        'running',
        '{"date": "2024-12-03"}',
        NOW() - INTERVAL '10 minutes'
    )
ON CONFLICT DO NOTHING;

-- Insert sample task executions for testing
INSERT INTO task_executions (id, task_id, status, priority, input_data, queued_at) 
VALUES 
    (
        '50000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        'completed',
        1,
        '{"timestamp": "2024-12-03T00:00:00Z"}',
        NOW() - INTERVAL '5 minutes'
    ),
    (
        '50000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000002',
        'queued',
        0,
        '{"scheduled_time": "2024-12-03T02:00:00Z"}',
        NOW()
    )
ON CONFLICT DO NOTHING;

-- Insert sample system events for testing monitoring
INSERT INTO system_events (id, event_type, source_service, event_data, severity, user_id) 
VALUES 
    (
        '60000000-0000-0000-0000-000000000001',
        'user_login',
        'api-gateway',
        '{"ip": "192.168.1.100", "user_agent": "Eden CLI/1.0"}',
        'info',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '60000000-0000-0000-0000-000000000002',
        'secret_accessed',
        'vault',
        '{"secret_name": "database-password", "action": "read"}',
        'info',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '60000000-0000-0000-0000-000000000003',
        'workflow_failed',
        'flow',
        '{"workflow_id": "20000000-0000-0000-0000-000000000001", "error": "Connection timeout"}',
        'error',
        '00000000-0000-0000-0000-000000000002'
    )
ON CONFLICT DO NOTHING;

-- Insert sample audit logs for testing
INSERT INTO audit_logs (id, user_id, organization_id, action, resource, resource_id, details, ip_address, user_agent) 
VALUES 
    (
        '70000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',
        'CREATE',
        'secret',
        '10000000-0000-0000-0000-000000000001',
        '{"secret_name": "database-password", "secret_type": "database"}',
        '192.168.1.100',
        'Eden CLI/1.0'
    ),
    (
        '70000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000001',
        'EXECUTE',
        'workflow',
        '20000000-0000-0000-0000-000000000001',
        '{"workflow_name": "deploy-to-staging", "execution_id": "40000000-0000-0000-0000-000000000001"}',
        '192.168.1.101',
        'Eden Web/1.0'
    ),
    (
        '70000000-0000-0000-0000-000000000003',
        '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',
        'READ',
        'secret',
        '10000000-0000-0000-0000-000000000002',
        '{"secret_name": "api-key-github", "access_method": "api"}',
        '192.168.1.100',
        'Eden API/1.0'
    )
ON CONFLICT DO NOTHING;

-- Insert sample secret access logs
INSERT INTO secret_access_logs (id, secret_id, user_id, action, ip_address, user_agent) 
VALUES 
    (
        '80000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',
        'read',
        '192.168.1.100',
        'Eden CLI/1.0'
    ),
    (
        '80000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000002',
        'read',
        '192.168.1.101',
        'Eden Web/1.0'
    ),
    (
        '80000000-0000-0000-0000-000000000003',
        '10000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',
        'write',
        '192.168.1.100',
        'Eden CLI/1.0'
    )
ON CONFLICT DO NOTHING;