package com.ataiva.eden.monitoring

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor
import io.opentelemetry.semconv.SemanticAttributes
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_REQUEST_METHOD
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_RESPONSE_STATUS_CODE
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.URL_FULL
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.URL_PATH
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.URL_QUERY
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.USER_AGENT_ORIGINAL

/**
 * OpenTelemetry instrumentation for Ktor applications
 */
class KtorOpenTelemetry(private val openTelemetry: OpenTelemetry) {

    private val instrumenter: Instrumenter<ApplicationRequest, ApplicationResponse>

    init {
        val httpAttributesExtractor = object : HttpServerAttributesExtractor<ApplicationRequest, ApplicationResponse> {
            override fun method(request: ApplicationRequest): String = request.httpMethod.value
            override fun scheme(request: ApplicationRequest): String = request.origin.scheme
            override fun host(request: ApplicationRequest): String = request.host()
            override fun port(request: ApplicationRequest): Int = request.port()
            override fun path(request: ApplicationRequest): String = request.path()
            override fun userAgent(request: ApplicationRequest): String? = request.headers["User-Agent"]
            override fun url(request: ApplicationRequest): String = request.uri
            override fun target(request: ApplicationRequest): String? = null
            override fun route(request: ApplicationRequest): String? = null
            override fun clientIp(request: ApplicationRequest, response: ApplicationResponse): String? = request.origin.remoteHost
            override fun flavor(request: ApplicationRequest): String? = request.httpVersion
            override fun statusCode(request: ApplicationRequest, response: ApplicationResponse, error: Throwable?): Int = response.status()?.value ?: 0
        }

        val spanNameExtractor = HttpSpanNameExtractor.create(httpAttributesExtractor)
        val spanStatusExtractor = HttpSpanStatusExtractor.create(httpAttributesExtractor)

        val builder = Instrumenter.builder<ApplicationRequest, ApplicationResponse>(
            openTelemetry,
            "io.ktor.server",
            spanNameExtractor
        )

        builder.setSpanStatusExtractor(spanStatusExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addOperationMetrics(HttpServerMetrics.get())

        instrumenter = builder.buildServerInstrumenter(HeadersGetter)
    }

    /**
     * Install OpenTelemetry in a Ktor application
     */
    fun install(app: Application) {
        app.environment.monitor.subscribe(ApplicationStarted) {
            app.log.info("OpenTelemetry instrumentation installed")
        }

        app.intercept(ApplicationCallPipeline.Monitoring) {
            val call = this.context
            val request = call.request
            val requestContext = Context.current()

            if (!instrumenter.shouldStart(requestContext, request)) {
                proceed()
                return@intercept
            }

            val context = instrumenter.start(requestContext, request)
            val span = Span.fromContext(context)

            try {
                // Add additional attributes
                span.setAttribute(URL_FULL, request.uri)
                span.setAttribute(URL_PATH, request.path())
                request.queryParameters.entries().forEach { entry ->
                    span.setAttribute("$URL_QUERY.${entry.key}", entry.value.joinToString(","))
                }
                span.setAttribute(HTTP_REQUEST_METHOD, request.httpMethod.value)
                request.headers["User-Agent"]?.let { span.setAttribute(USER_AGENT_ORIGINAL, it) }

                // Add trace ID to response headers for debugging
                call.response.headers.append("X-Trace-ID", span.spanContext.traceId)

                // Execute the call
                proceed()

                // Record response information
                val response = call.response
                span.setAttribute(HTTP_RESPONSE_STATUS_CODE, response.status()?.value ?: 0)

                instrumenter.end(context, request, response, null)
            } catch (e: Throwable) {
                span.recordException(e)
                span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
                instrumenter.end(context, request, call.response, e)
                throw e
            }
        }
    }

    private object HeadersGetter : TextMapGetter<ApplicationRequest> {
        override fun keys(carrier: ApplicationRequest): Iterable<String> = carrier.headers.names()
        override fun get(carrier: ApplicationRequest, key: String): String? = carrier.headers[key]
    }
}

/**
 * Extension function to get the status code from a response
 */
private fun ApplicationResponse.status(): HttpStatusCode? = 
    try {
        status()
    } catch (e: Exception) {
        null
    }