package io.opentelemetry.semconv

/**
 * Stub implementation of SemanticAttributes for compilation purposes
 */
object SemanticAttributes {
    val HTTP_REQUEST_METHOD = "http.request.method"
    val HTTP_RESPONSE_STATUS_CODE = "http.response.status_code"
    val URL_FULL = "url.full"
    val URL_PATH = "url.path"
    val URL_QUERY = "url.query"
    val USER_AGENT_ORIGINAL = "user_agent.original"
}

/**
 * Stub implementation of ResourceAttributes for compilation purposes
 */
object ResourceAttributes {
    val SERVICE_NAME = "service.name"
    val SERVICE_VERSION = "service.version"
    val SERVICE_NAMESPACE = "service.namespace"
}