package com.ataiva.eden.insight.engine

import com.ataiva.eden.insight.model.ReportFormat
import com.ataiva.eden.insight.model.ReportTemplate
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Manages report templates, including loading from files, validation,
 * and providing default templates for different report types.
 */
class TemplateManager(private val templateDirectory: String) {
    private val logger = LoggerFactory.getLogger(TemplateManager::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    init {
        // Ensure template directory exists
        val dir = File(templateDirectory)
        if (!dir.exists()) {
            dir.mkdirs()
            initializeDefaultTemplates()
        }
    }
    
    /**
     * Load a template from file
     */
    fun loadTemplate(templatePath: String): String {
        return try {
            val path = Paths.get(templateDirectory, templatePath)
            String(Files.readAllBytes(path))
        } catch (e: Exception) {
            logger.error("Error loading template: $templatePath", e)
            throw TemplateException("Failed to load template: ${e.message}")
        }
    }
    
    /**
     * Save a template to file
     */
    fun saveTemplate(templateName: String, content: String): String {
        val filename = "${templateName.replace(" ", "_").lowercase()}.template"
        val path = Paths.get(templateDirectory, filename)
        
        try {
            Files.write(path, content.toByteArray())
            return filename
        } catch (e: Exception) {
            logger.error("Error saving template: $templateName", e)
            throw TemplateException("Failed to save template: ${e.message}")
        }
    }
    
    /**
     * Get default template for a specific report type
     */
    fun getDefaultTemplate(reportType: String, format: ReportFormat): String {
        return when (reportType.lowercase()) {
            "performance" -> getPerformanceReportTemplate(format)
            "usage" -> getUsageReportTemplate(format)
            "security" -> getSecurityReportTemplate(format)
            "audit" -> getAuditReportTemplate(format)
            "executive" -> getExecutiveReportTemplate(format)
            else -> getGenericReportTemplate(format)
        }
    }
    
    /**
     * Validate a template for syntax and required elements
     */
    fun validateTemplate(content: String): List<String> {
        val errors = mutableListOf<String>()
        
        // Check for basic structure
        if (!content.contains("## SECTION:")) {
            errors.add("Template must contain at least one section (## SECTION:)")
        }
        
        // Check for balanced sections
        val sectionStarts = content.lines().count { it.startsWith("## SECTION:") }
        val sectionEnds = content.lines().count { it.startsWith("## END SECTION") }
        
        if (sectionStarts != sectionEnds) {
            errors.add("Unbalanced sections: $sectionStarts starts vs $sectionEnds ends")
        }
        
        // Check for query references
        val queryRefs = content.lines()
            .filter { it.contains("{{QUERY:") }
            .map { it.substringAfter("{{QUERY:").substringBefore("}}").trim() }
        
        if (queryRefs.isEmpty()) {
            errors.add("Template should contain at least one query reference ({{QUERY:...}})")
        }
        
        // Check for chart references
        val chartRefs = content.lines()
            .filter { it.contains("{{CHART:") }
            .map { it.substringAfter("{{CHART:").substringBefore("}}").trim() }
        
        for (chartRef in chartRefs) {
            val parts = chartRef.split(",")
            if (parts.size < 3) {
                errors.add("Chart reference should have format: {{CHART:type,title,dataSource}}")
            }
        }
        
        return errors
    }
    
    /**
     * Initialize default templates
     */
    private fun initializeDefaultTemplates() {
        try {
            // Create default templates for different report types
            saveTemplate("performance_report", getPerformanceReportTemplate(ReportFormat.PDF))
            saveTemplate("usage_report", getUsageReportTemplate(ReportFormat.PDF))
            saveTemplate("security_report", getSecurityReportTemplate(ReportFormat.PDF))
            saveTemplate("audit_report", getAuditReportTemplate(ReportFormat.PDF))
            saveTemplate("executive_report", getExecutiveReportTemplate(ReportFormat.PDF))
            saveTemplate("generic_report", getGenericReportTemplate(ReportFormat.PDF))
            
            logger.info("Default templates initialized")
        } catch (e: Exception) {
            logger.error("Error initializing default templates", e)
        }
    }
    
    /**
     * Get performance report template
     */
    private fun getPerformanceReportTemplate(format: ReportFormat): String {
        return """
            # Performance Report
            
            Generated: {{timestamp}}
            
            ## SECTION: Overview
            This report provides a comprehensive analysis of system performance metrics over the specified time period.
            
            ## QUERY: performance_overview
            
            ## END SECTION
            
            ## SECTION: Response Time Analysis
            
            The following chart shows the average response time trend:
            
            {{CHART:line,Response Time Trend,response_time_data}}
            
            ## QUERY: response_time_data
            
            ## END SECTION
            
            ## SECTION: Throughput Analysis
            
            System throughput over time:
            
            {{CHART:bar,Throughput by Service,throughput_data}}
            
            ## QUERY: throughput_data
            
            ## END SECTION
            
            ## SECTION: Resource Utilization
            
            Resource utilization by component:
            
            {{CHART:pie,Resource Utilization,resource_data}}
            
            ## QUERY: resource_data
            
            ## END SECTION
            
            ## SECTION: Recommendations
            
            Based on the performance data, the following recommendations are provided:
            
            1. Monitor services with response times exceeding thresholds
            2. Optimize database queries for high-load operations
            3. Consider scaling resources for components with high utilization
            
            ## END SECTION
        """.trimIndent()
    }
    
    /**
     * Get usage report template
     */
    private fun getUsageReportTemplate(format: ReportFormat): String {
        return """
            # Usage Report
            
            Generated: {{timestamp}}
            
            ## SECTION: Overview
            This report provides insights into system usage patterns and user activity.
            
            ## QUERY: usage_overview
            
            ## END SECTION
            
            ## SECTION: User Activity
            
            User activity over time:
            
            {{CHART:line,User Activity Trend,user_activity_data}}
            
            ## QUERY: user_activity_data
            
            ## END SECTION
            
            ## SECTION: Feature Usage
            
            Most used features:
            
            {{CHART:bar,Feature Usage Distribution,feature_usage_data}}
            
            ## QUERY: feature_usage_data
            
            ## END SECTION
            
            ## SECTION: User Demographics
            
            User distribution by role:
            
            {{CHART:pie,User Roles Distribution,user_roles_data}}
            
            ## QUERY: user_roles_data
            
            ## END SECTION
            
            ## SECTION: Recommendations
            
            Based on the usage data, the following recommendations are provided:
            
            1. Focus development on most-used features
            2. Consider user training for underutilized features
            3. Optimize user flows for common patterns
            
            ## END SECTION
        """.trimIndent()
    }
    
    /**
     * Get security report template
     */
    private fun getSecurityReportTemplate(format: ReportFormat): String {
        return """
            # Security Report
            
            Generated: {{timestamp}}
            
            ## SECTION: Overview
            This report provides an analysis of security events and potential vulnerabilities.
            
            ## QUERY: security_overview
            
            ## END SECTION
            
            ## SECTION: Security Events
            
            Security events by severity:
            
            {{CHART:bar,Security Events by Severity,security_events_data}}
            
            ## QUERY: security_events_data
            
            ## END SECTION
            
            ## SECTION: Authentication Analysis
            
            Authentication attempts:
            
            {{CHART:line,Authentication Attempts Over Time,auth_data}}
            
            ## QUERY: auth_data
            
            ## END SECTION
            
            ## SECTION: Vulnerability Assessment
            
            Identified vulnerabilities by risk level:
            
            {{CHART:pie,Vulnerabilities by Risk Level,vulnerability_data}}
            
            ## QUERY: vulnerability_data
            
            ## END SECTION
            
            ## SECTION: Recommendations
            
            Based on the security analysis, the following recommendations are provided:
            
            1. Address high-risk vulnerabilities immediately
            2. Implement additional authentication controls
            3. Review security policies for identified risk areas
            
            ## END SECTION
        """.trimIndent()
    }
    
    /**
     * Get audit report template
     */
    private fun getAuditReportTemplate(format: ReportFormat): String {
        return """
            # Audit Report
            
            Generated: {{timestamp}}
            
            ## SECTION: Overview
            This report provides a comprehensive audit of system activities and compliance status.
            
            ## QUERY: audit_overview
            
            ## END SECTION
            
            ## SECTION: User Activity Audit
            
            User activity by action type:
            
            {{CHART:bar,User Actions Distribution,user_actions_data}}
            
            ## QUERY: user_actions_data
            
            ## END SECTION
            
            ## SECTION: Resource Access Audit
            
            Resource access patterns:
            
            {{CHART:line,Resource Access Over Time,resource_access_data}}
            
            ## QUERY: resource_access_data
            
            ## END SECTION
            
            ## SECTION: Compliance Status
            
            Compliance status by requirement:
            
            {{CHART:pie,Compliance Status,compliance_data}}
            
            ## QUERY: compliance_data
            
            ## END SECTION
            
            ## SECTION: Recommendations
            
            Based on the audit findings, the following recommendations are provided:
            
            1. Address compliance gaps in identified areas
            2. Implement additional audit trails for sensitive operations
            3. Review access controls for critical resources
            
            ## END SECTION
        """.trimIndent()
    }
    
    /**
     * Get executive report template
     */
    private fun getExecutiveReportTemplate(format: ReportFormat): String {
        return """
            # Executive Summary Report
            
            Generated: {{timestamp}}
            
            ## SECTION: Overview
            This executive summary provides high-level insights into system performance, usage, and business metrics.
            
            ## QUERY: executive_overview
            
            ## END SECTION
            
            ## SECTION: Key Performance Indicators
            
            Key performance indicators:
            
            {{CHART:bar,Key Performance Indicators,kpi_data}}
            
            ## QUERY: kpi_data
            
            ## END SECTION
            
            ## SECTION: Business Metrics
            
            Business metrics over time:
            
            {{CHART:line,Business Metrics Trend,business_metrics_data}}
            
            ## QUERY: business_metrics_data
            
            ## END SECTION
            
            ## SECTION: Resource Allocation
            
            Resource allocation by department:
            
            {{CHART:pie,Resource Allocation,resource_allocation_data}}
            
            ## QUERY: resource_allocation_data
            
            ## END SECTION
            
            ## SECTION: Strategic Recommendations
            
            Based on the analysis, the following strategic recommendations are provided:
            
            1. Focus resources on high-performing areas
            2. Address underperforming metrics with targeted initiatives
            3. Align resource allocation with business priorities
            
            ## END SECTION
        """.trimIndent()
    }
    
    /**
     * Get generic report template
     */
    private fun getGenericReportTemplate(format: ReportFormat): String {
        return """
            # Report
            
            Generated: {{timestamp}}
            
            ## SECTION: Overview
            This report provides an analysis of system data.
            
            ## QUERY: data_overview
            
            ## END SECTION
            
            ## SECTION: Data Analysis
            
            Data analysis results:
            
            {{CHART:bar,Data Distribution,analysis_data}}
            
            ## QUERY: analysis_data
            
            ## END SECTION
            
            ## SECTION: Trend Analysis
            
            Trend analysis over time:
            
            {{CHART:line,Trend Analysis,trend_data}}
            
            ## QUERY: trend_data
            
            ## END SECTION
            
            ## SECTION: Category Distribution
            
            Distribution by category:
            
            {{CHART:pie,Category Distribution,category_data}}
            
            ## QUERY: category_data
            
            ## END SECTION
            
            ## SECTION: Recommendations
            
            Based on the analysis, the following recommendations are provided:
            
            1. Review areas with significant deviations
            2. Monitor trends for potential issues
            3. Consider further analysis for identified patterns
            
            ## END SECTION
        """.trimIndent()
    }
}

/**
 * Custom exception for template operations
 */
class TemplateException(message: String, cause: Throwable? = null) : Exception(message, cause)