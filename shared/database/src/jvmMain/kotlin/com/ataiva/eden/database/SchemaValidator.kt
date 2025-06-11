package com.ataiva.eden.database

import java.sql.Connection
import java.sql.SQLException
import java.util.logging.Logger
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration

/**
 * Schema validator for database schemas
 *
 * This class provides comprehensive schema validation capabilities:
 * - Validates schema structure against expected schema
 * - Checks for missing tables, columns, indexes
 * - Validates data types and constraints
 * - Reports detailed validation errors
 *
 * @author Eden Database Team
 * @version 1.0.0
 */
class SchemaValidator(private val dataSource: DataSource) {
    private val logger = Logger.getLogger(SchemaValidator::class.java.name)
    
    /**
     * Validate the database schema using Flyway
     *
     * @return ValidationResult containing validation status and any issues
     */
    fun validateWithFlyway(): ValidationResult {
        try {
            // Create Flyway instance for validation
            val flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "filesystem:infrastructure/database/init")
                .load()
            
            // Validate the schema
            flyway.validate()
            
            // Check for pending migrations
            val pendingMigrations = flyway.info().pending()
            if (pendingMigrations.isNotEmpty()) {
                val pendingMigrationsList = pendingMigrations.map { migration ->
                    "${migration.version} - ${migration.description}"
                }
                
                return ValidationResult(
                    isValid = true,
                    warnings = listOf("Schema is valid but not up to date. Pending migrations: ${pendingMigrationsList.joinToString(", ")}"),
                    errors = emptyList()
                )
            }
            
            return ValidationResult(
                isValid = true,
                warnings = emptyList(),
                errors = emptyList()
            )
        } catch (e: Exception) {
            logger.warning("Schema validation with Flyway failed: ${e.message}")
            return ValidationResult(
                isValid = false,
                warnings = emptyList(),
                errors = listOf("Flyway validation failed: ${e.message}")
            )
        }
    }
    
    /**
     * Validate the database schema structure
     *
     * @return ValidationResult containing validation status and any issues
     */
    fun validateSchemaStructure(): ValidationResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        
        try {
            dataSource.connection.use { connection ->
                // Validate required schemas
                validateRequiredSchemas(connection, warnings, errors)
                
                // Validate required tables
                validateRequiredTables(connection, warnings, errors)
                
                // Validate required columns
                validateRequiredColumns(connection, warnings, errors)
                
                // Validate required indexes
                validateRequiredIndexes(connection, warnings, errors)
                
                // Validate required constraints
                validateRequiredConstraints(connection, warnings, errors)
            }
            
            return ValidationResult(
                isValid = errors.isEmpty(),
                warnings = warnings,
                errors = errors
            )
        } catch (e: Exception) {
            logger.warning("Schema structure validation failed: ${e.message}")
            return ValidationResult(
                isValid = false,
                warnings = warnings,
                errors = errors + "Schema structure validation failed: ${e.message}"
            )
        }
    }
    
    /**
     * Validate required schemas
     */
    private fun validateRequiredSchemas(
        connection: Connection,
        @Suppress("UNUSED_PARAMETER") _warnings: MutableList<String>,
        errors: MutableList<String>
    ) {
        val requiredSchemas = listOf("eden", "audit")
        
        val statement = connection.createStatement()
        val resultSet = statement.executeQuery(
            """
            SELECT schema_name 
            FROM information_schema.schemata 
            WHERE schema_name IN ('eden', 'audit')
            """
        )
        
        val existingSchemas = mutableListOf<String>()
        while (resultSet.next()) {
            existingSchemas.add(resultSet.getString("schema_name"))
        }
        
        val missingSchemas = requiredSchemas - existingSchemas.toSet()
        if (missingSchemas.isNotEmpty()) {
            errors.add("Missing required schemas: ${missingSchemas.joinToString(", ")}")
        }
    }
    
    /**
     * Validate required tables
     */
    private fun validateRequiredTables(
        connection: Connection,
        @Suppress("UNUSED_PARAMETER") _warnings: MutableList<String>,
        errors: MutableList<String>
    ) {
        val requiredTables = mapOf(
            "eden" to listOf(
                "organizations", 
                "users", 
                "user_organizations",
                "secrets",
                "workflows",
                "workflow_executions",
                "tasks",
                "task_executions",
                "system_events"
            ),
            "audit" to listOf(
                "audit_logs"
            )
        )
        
        for ((schema, tables) in requiredTables) {
            val statement = connection.createStatement()
            val resultSet = statement.executeQuery(
                """
                SELECT table_name 
                FROM information_schema.tables 
                WHERE table_schema = '$schema'
                """
            )
            
            val existingTables = mutableListOf<String>()
            while (resultSet.next()) {
                existingTables.add(resultSet.getString("table_name"))
            }
            
            val missingTables = tables - existingTables.toSet()
            if (missingTables.isNotEmpty()) {
                errors.add("Missing required tables in schema '$schema': ${missingTables.joinToString(", ")}")
            }
        }
    }
    
    /**
     * Validate required columns
     */
    private fun validateRequiredColumns(
        connection: Connection,
        @Suppress("UNUSED_PARAMETER") _warnings: MutableList<String>,
        errors: MutableList<String>
    ) {
        // Define required columns for key tables
        val requiredColumns = mapOf(
            "eden.users" to listOf("id", "email", "password_hash", "is_active", "created_at", "updated_at"),
            "eden.organizations" to listOf("id", "name", "slug", "created_at", "updated_at"),
            "eden.secrets" to listOf("id", "name", "encrypted_value", "created_at", "updated_at")
        )
        
        for ((tableKey, columns) in requiredColumns) {
            val parts = tableKey.split(".")
            val schema = parts[0]
            val table = parts[1]
            
            val statement = connection.createStatement()
            val resultSet = statement.executeQuery(
                """
                SELECT column_name 
                FROM information_schema.columns 
                WHERE table_schema = '$schema' AND table_name = '$table'
                """
            )
            
            val existingColumns = mutableListOf<String>()
            while (resultSet.next()) {
                existingColumns.add(resultSet.getString("column_name"))
            }
            
            val missingColumns = columns - existingColumns.toSet()
            if (missingColumns.isNotEmpty()) {
                errors.add("Missing required columns in table '$tableKey': ${missingColumns.joinToString(", ")}")
            }
        }
    }
    
    /**
     * Validate required indexes
     */
    private fun validateRequiredIndexes(
        connection: Connection,
        warnings: MutableList<String>,
        @Suppress("UNUSED_PARAMETER") _errors: MutableList<String>
    ) {
        // Define required indexes
        val requiredIndexes = mapOf(
            "eden.users" to listOf("idx_users_email"),
            "eden.user_organizations" to listOf("idx_user_organizations_user_id", "idx_user_organizations_org_id"),
            "eden.secrets" to listOf("idx_secrets_search")
        )
        
        for ((tableKey, indexes) in requiredIndexes) {
            val parts = tableKey.split(".")
            val schema = parts[0]
            val table = parts[1]
            
            val statement = connection.createStatement()
            val resultSet = statement.executeQuery(
                """
                SELECT indexname 
                FROM pg_indexes 
                WHERE schemaname = '$schema' AND tablename = '$table'
                """
            )
            
            val existingIndexes = mutableListOf<String>()
            while (resultSet.next()) {
                existingIndexes.add(resultSet.getString("indexname"))
            }
            
            val missingIndexes = indexes - existingIndexes.toSet()
            if (missingIndexes.isNotEmpty()) {
                warnings.add("Missing recommended indexes in table '$tableKey': ${missingIndexes.joinToString(", ")}")
            }
        }
    }
    
    /**
     * Validate required constraints
     */
    private fun validateRequiredConstraints(
        connection: Connection,
        warnings: MutableList<String>,
        errors: MutableList<String>
    ) {
        // Define required primary keys
        val requiredPrimaryKeys = mapOf(
            "eden.users" to "id",
            "eden.organizations" to "id",
            "eden.user_organizations" to "id",
            "eden.secrets" to "id",
            "eden.workflows" to "id",
            "eden.workflow_executions" to "id",
            "eden.tasks" to "id",
            "eden.task_executions" to "id",
            "eden.system_events" to "id",
            "audit.audit_logs" to "id"
        )
        
        for ((tableKey, pkColumn) in requiredPrimaryKeys) {
            val parts = tableKey.split(".")
            val schema = parts[0]
            val table = parts[1]
            
            val statement = connection.createStatement()
            val resultSet = statement.executeQuery(
                """
                SELECT c.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.constraint_column_usage AS ccu USING (constraint_schema, constraint_name)
                JOIN information_schema.columns AS c ON c.table_schema = tc.constraint_schema
                  AND tc.table_name = c.table_name AND ccu.column_name = c.column_name
                WHERE constraint_type = 'PRIMARY KEY' AND tc.table_schema = '$schema' AND tc.table_name = '$table'
                """
            )
            
            if (!resultSet.next()) {
                errors.add("Missing primary key constraint on table '$tableKey'")
            } else {
                val actualPkColumn = resultSet.getString("column_name")
                if (actualPkColumn != pkColumn) {
                    warnings.add("Primary key column on table '$tableKey' is '$actualPkColumn', expected '$pkColumn'")
                }
            }
        }
        
        // Define required foreign keys
        val requiredForeignKeys = mapOf(
            "eden.user_organizations" to listOf(
                ForeignKeyConstraint("user_id", "eden.users", "id"),
                ForeignKeyConstraint("organization_id", "eden.organizations", "id")
            ),
            "eden.secrets" to listOf(
                ForeignKeyConstraint("user_id", "eden.users", "id"),
                ForeignKeyConstraint("organization_id", "eden.organizations", "id")
            )
        )
        
        for ((tableKey, fkConstraints) in requiredForeignKeys) {
            val parts = tableKey.split(".")
            val schema = parts[0]
            val table = parts[1]
            
            for (fk in fkConstraints) {
                val statement = connection.createStatement()
                val resultSet = statement.executeQuery(
                    """
                    SELECT COUNT(*) as count
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.constraint_column_usage AS ccu USING (constraint_schema, constraint_name)
                    JOIN information_schema.referential_constraints rc USING (constraint_schema, constraint_name)
                    JOIN information_schema.key_column_usage kcu ON kcu.constraint_schema = rc.constraint_schema
                      AND kcu.constraint_name = rc.constraint_name
                    WHERE constraint_type = 'FOREIGN KEY' 
                      AND tc.table_schema = '$schema' 
                      AND tc.table_name = '$table'
                      AND kcu.column_name = '${fk.column}'
                      AND ccu.table_schema || '.' || ccu.table_name = '${fk.referencedTable}'
                      AND ccu.column_name = '${fk.referencedColumn}'
                    """
                )
                
                resultSet.next()
                val count = resultSet.getInt("count")
                if (count == 0) {
                    warnings.add("Missing foreign key constraint on table '$tableKey' column '${fk.column}' referencing '${fk.referencedTable}(${fk.referencedColumn})'")
                }
            }
        }
    }
    
    /**
     * Perform a comprehensive schema validation
     *
     * @return ValidationResult containing validation status and any issues
     */
    fun validateSchema(): ValidationResult {
        // First validate with Flyway
        val flywayResult = validateWithFlyway()
        if (!flywayResult.isValid) {
            return flywayResult
        }
        
        // Then validate schema structure
        val structureResult = validateSchemaStructure()
        
        // Combine results
        return ValidationResult(
            isValid = structureResult.isValid,
            warnings = flywayResult.warnings + structureResult.warnings,
            errors = flywayResult.errors + structureResult.errors
        )
    }
    
    /**
     * Data class representing a foreign key constraint
     */
    data class ForeignKeyConstraint(
        val column: String,
        val referencedTable: String,
        val referencedColumn: String
    )
    
    /**
     * Data class representing a validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val warnings: List<String>,
        val errors: List<String>
    )
}