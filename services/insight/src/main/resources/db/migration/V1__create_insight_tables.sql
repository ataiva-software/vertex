-- Migration script for Insight Service tables

-- Analytics Queries table
CREATE TABLE IF NOT EXISTS analytics_queries (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    query_text TEXT NOT NULL,
    query_type VARCHAR(50) NOT NULL,
    parameters TEXT NOT NULL, -- JSON serialized
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_modified TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    tags TEXT NOT NULL -- JSON serialized
);

-- Query Executions table
CREATE TABLE IF NOT EXISTS query_executions (
    id VARCHAR(255) PRIMARY KEY,
    query_id VARCHAR(255) NOT NULL,
    executed_by VARCHAR(255) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    result_count INTEGER NOT NULL DEFAULT 0,
    execution_time_ms BIGINT NOT NULL DEFAULT 0,
    error_message TEXT,
    parameters TEXT NOT NULL, -- JSON serialized
    FOREIGN KEY (query_id) REFERENCES analytics_queries(id)
);

-- Report Templates table
CREATE TABLE IF NOT EXISTS report_templates (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    template_content TEXT NOT NULL,
    required_parameters TEXT NOT NULL, -- JSON serialized
    supported_formats TEXT NOT NULL, -- JSON serialized
    category VARCHAR(100) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    version VARCHAR(50) NOT NULL
);

-- Reports table
CREATE TABLE IF NOT EXISTS reports (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    template_id VARCHAR(255) NOT NULL,
    parameters TEXT NOT NULL, -- JSON serialized
    schedule TEXT, -- JSON serialized
    recipients TEXT NOT NULL, -- JSON serialized
    format VARCHAR(50) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_generated TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (template_id) REFERENCES report_templates(id)
);

-- Report Executions table
CREATE TABLE IF NOT EXISTS report_executions (
    id VARCHAR(255) PRIMARY KEY,
    report_id VARCHAR(255) NOT NULL,
    executed_by VARCHAR(255) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    output_path VARCHAR(500),
    file_size BIGINT NOT NULL DEFAULT 0,
    error_message TEXT,
    parameters TEXT NOT NULL, -- JSON serialized
    FOREIGN KEY (report_id) REFERENCES reports(id)
);

-- Dashboards table
CREATE TABLE IF NOT EXISTS dashboards (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    widgets TEXT NOT NULL, -- JSON serialized
    layout TEXT NOT NULL, -- JSON serialized
    permissions TEXT NOT NULL, -- JSON serialized
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_modified TIMESTAMP NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    tags TEXT NOT NULL -- JSON serialized
);

-- Metrics table
CREATE TABLE IF NOT EXISTS metrics (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    unit VARCHAR(50),
    aggregation_type VARCHAR(50) NOT NULL,
    query_id VARCHAR(255) NOT NULL,
    thresholds TEXT NOT NULL, -- JSON serialized
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL
);

-- Metric Values table
CREATE TABLE IF NOT EXISTS metric_values (
    id VARCHAR(255) PRIMARY KEY,
    metric_id VARCHAR(255) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    dimensions TEXT NOT NULL, -- JSON serialized
    metadata TEXT NOT NULL, -- JSON serialized
    FOREIGN KEY (metric_id) REFERENCES metrics(id)
);

-- KPIs table
CREATE TABLE IF NOT EXISTS kpis (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    target_value DOUBLE PRECISION NOT NULL,
    current_value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(50),
    trend VARCHAR(50) NOT NULL,
    category VARCHAR(100) NOT NULL,
    last_updated TIMESTAMP NOT NULL,
    historical_data TEXT NOT NULL -- JSON serialized
);

-- Create indexes for better performance
-- Basic indexes
CREATE INDEX idx_analytics_queries_created_by ON analytics_queries(created_by);
CREATE INDEX idx_analytics_queries_query_type ON analytics_queries(query_type);
CREATE INDEX idx_analytics_queries_is_active ON analytics_queries(is_active);
CREATE INDEX idx_analytics_queries_name ON analytics_queries(name);
CREATE INDEX idx_analytics_queries_last_modified ON analytics_queries(last_modified);
CREATE INDEX idx_analytics_queries_composite1 ON analytics_queries(query_type, is_active);

CREATE INDEX idx_query_executions_query_id ON query_executions(query_id);
CREATE INDEX idx_query_executions_status ON query_executions(status);
CREATE INDEX idx_query_executions_start_time ON query_executions(start_time);
CREATE INDEX idx_query_executions_executed_by ON query_executions(executed_by);
CREATE INDEX idx_query_executions_composite1 ON query_executions(query_id, status);
CREATE INDEX idx_query_executions_composite2 ON query_executions(start_time, end_time);

CREATE INDEX idx_reports_created_by ON reports(created_by);
CREATE INDEX idx_reports_template_id ON reports(template_id);
CREATE INDEX idx_reports_is_active ON reports(is_active);
CREATE INDEX idx_reports_name ON reports(name);
CREATE INDEX idx_reports_last_generated ON reports(last_generated);
CREATE INDEX idx_reports_composite1 ON reports(template_id, is_active);

CREATE INDEX idx_report_templates_category ON report_templates(category);
CREATE INDEX idx_report_templates_name ON report_templates(name);
CREATE INDEX idx_report_templates_created_by ON report_templates(created_by);
CREATE INDEX idx_report_templates_version ON report_templates(version);

CREATE INDEX idx_report_executions_report_id ON report_executions(report_id);
CREATE INDEX idx_report_executions_status ON report_executions(status);
CREATE INDEX idx_report_executions_start_time ON report_executions(start_time);
CREATE INDEX idx_report_executions_executed_by ON report_executions(executed_by);
CREATE INDEX idx_report_executions_composite1 ON report_executions(report_id, status);

CREATE INDEX idx_dashboards_created_by ON dashboards(created_by);
CREATE INDEX idx_dashboards_is_public ON dashboards(is_public);
CREATE INDEX idx_dashboards_name ON dashboards(name);
CREATE INDEX idx_dashboards_last_modified ON dashboards(last_modified);
CREATE INDEX idx_dashboards_composite1 ON dashboards(created_by, is_public);

CREATE INDEX idx_metrics_category ON metrics(category);
CREATE INDEX idx_metrics_is_active ON metrics(is_active);
CREATE INDEX idx_metrics_name ON metrics(name);
CREATE INDEX idx_metrics_query_id ON metrics(query_id);
CREATE INDEX idx_metrics_composite1 ON metrics(category, is_active);

CREATE INDEX idx_metric_values_metric_id ON metric_values(metric_id);
CREATE INDEX idx_metric_values_timestamp ON metric_values(timestamp);
CREATE INDEX idx_metric_values_composite1 ON metric_values(metric_id, timestamp);

CREATE INDEX idx_kpis_category ON kpis(category);
CREATE INDEX idx_kpis_name ON kpis(name);
CREATE INDEX idx_kpis_last_updated ON kpis(last_updated);

-- Add partial indexes for frequently accessed filtered data
CREATE INDEX idx_active_queries ON analytics_queries(id, name, query_type) WHERE is_active = TRUE;
CREATE INDEX idx_recent_executions ON query_executions(query_id, start_time, status) WHERE start_time > (CURRENT_TIMESTAMP - INTERVAL '7 days');
CREATE INDEX idx_active_reports ON reports(id, name, template_id) WHERE is_active = TRUE;
CREATE INDEX idx_public_dashboards ON dashboards(id, name, created_by) WHERE is_public = TRUE;
CREATE INDEX idx_active_metrics ON metrics(id, name, category) WHERE is_active = TRUE;