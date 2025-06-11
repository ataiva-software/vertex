package io.opentelemetry.instrumentation

import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.ApplicationResponse

/**
 * Stub implementation of HttpServerAttributesExtractor for compilation purposes
 */
abstract class HttpServerAttributesExtractor<REQUEST, RESPONSE> {
    abstract fun method(request: REQUEST): String?
    abstract fun scheme(request: REQUEST): String?
    abstract fun host(request: REQUEST): String?
    abstract fun port(request: REQUEST): Int?
    abstract fun path(request: REQUEST): String?
    abstract fun userAgent(request: REQUEST): String?
    abstract fun url(request: REQUEST): String?
    abstract fun target(request: REQUEST): String?
    abstract fun route(request: REQUEST): String?
    abstract fun clientIp(request: REQUEST): String?
    abstract fun flavor(request: REQUEST): String?
    abstract fun statusCode(response: RESPONSE): Int?
}

/**
 * Stub implementation of HttpSpanNameExtractor for compilation purposes
 */
abstract class HttpSpanNameExtractor<REQUEST> {
    abstract fun extract(request: REQUEST): String
}

/**
 * Stub implementation of HttpSpanStatusExtractor for compilation purposes
 */
abstract class HttpSpanStatusExtractor<REQUEST, RESPONSE> {
    abstract fun statusCode(request: REQUEST, response: RESPONSE, error: Throwable?): Int
}

/**
 * Stub implementation of Instrumenter for compilation purposes
 */
class Instrumenter<REQUEST, RESPONSE> private constructor() {
    companion object {
        fun <REQUEST, RESPONSE> builder(instrumentationName: String): Builder<REQUEST, RESPONSE> {
            return Builder()
        }
    }

    class Builder<REQUEST, RESPONSE> {
        fun addAttributesExtractor(extractor: HttpServerAttributesExtractor<REQUEST, RESPONSE>): Builder<REQUEST, RESPONSE> {
            return this
        }

        fun setSpanNameExtractor(extractor: HttpSpanNameExtractor<REQUEST>): Builder<REQUEST, RESPONSE> {
            return this
        }

        fun setSpanStatusExtractor(extractor: HttpSpanStatusExtractor<REQUEST, RESPONSE>): Builder<REQUEST, RESPONSE> {
            return this
        }

        fun buildServerInstrumenter(): Instrumenter<REQUEST, RESPONSE> {
            return Instrumenter()
        }
    }
}

/**
 * Stub implementation of HttpServerMetrics for compilation purposes
 */
class HttpServerMetrics {
    companion object {
        fun create(): HttpServerMetrics {
            return HttpServerMetrics()
        }
    }
}

/**
 * Stub implementation of HttpStatusCode for compilation purposes
 */
enum class HttpStatusCode(val value: Int) {
    OK(200),
    BAD_REQUEST(400),
    INTERNAL_SERVER_ERROR(500)
}