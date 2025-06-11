package com.ataiva.eden.flow.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

/**
 * Request to create a new workflow
 */
@Serializable
data class CreateWorkflowRequest(
    val name: String,
    val description: String,
    val definition: String, // JSON string representation of the workflow definition
    val metadata: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList()
)

/**
 * Request to update a workflow
 */
@Serializable
data class UpdateWorkflowRequest(
    val name: String? = null,
    val description: String? = null,
    val definition: String? = null, // JSON string representation of the workflow definition
    val metadata: Map<String, String>? = null,
    val tags: List<String>? = null
)

/**
 * Request to list workflows
 */
@Serializable
data class ListWorkflowsRequest(
    val name: String? = null,
    val status: String? = null,
    val tags: List<String>? = null,
    val page: Int = 0,
    val size: Int = 20
)

/**
 * Request to execute a workflow
 */
@Serializable
data class ExecuteWorkflowRequest(
    val workflowId: String,
    val inputs: String = "{}", // JSON string representation of the inputs
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Request to list executions
 */
@Serializable
data class ListExecutionsRequest(
    val workflowId: String? = null,
    val status: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val page: Int = 0,
    val size: Int = 20
)