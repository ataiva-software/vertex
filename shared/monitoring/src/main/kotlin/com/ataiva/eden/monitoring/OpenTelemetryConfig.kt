package com.ataiva.eden.monitoring

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.ResourceAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for OpenTelemetry in Eden services
 */
class OpenTelemetryConfig(
    private val serviceName: String,
    private val serviceVersion: String,
    private val environment: String,
    private val otlpEndpoint: String = "http://otel-collector:4317",
    private val prometheusPort: Int = 9464
) {
    private lateinit var openTelemetry: OpenTelemetry
    private lateinit var tracer: Tracer
    
    /**
     * Initialize OpenTelemetry for the service
     */
    fun initialize(): OpenTelemetry {
        val resource = Resource.getDefault()
            .merge(
                Resource.create(
                    Attributes.builder()
                        .put(ResourceAttributes.SERVICE_NAME, serviceName)
                        .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
                        .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, environment)
                        .build()
                )
            )
        
        // Set up tracing
        val tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(
                BatchSpanProcessor.builder(
                    OtlpGrpcSpanExporter.builder()
                        .setEndpoint(otlpEndpoint)
                        .setTimeout(5, TimeUnit.SECONDS)
                        .build()
                ).build()
            )
            .build()
        
        // Set up metrics
        val meterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(
                PeriodicMetricReader.builder(
                    OtlpGrpcMetricExporter.builder()
                        .setEndpoint(otlpEndpoint)
                        .setTimeout(5, TimeUnit.SECONDS)
                        .build()
                ).build()
            )
            .registerMetricReader(
                PrometheusHttpServer.builder()
                    .setPort(prometheusPort)
                    .build()
            )
            .build()
        
        // Set up logging
        val loggerProvider = SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(
                BatchLogRecordProcessor.builder(
                    OtlpGrpcLogRecordExporter.builder()
                        .setEndpoint(otlpEndpoint)
                        .setTimeout(5, TimeUnit.SECONDS)
                        .build()
                ).build()
            )
            .build()
        
        // Build the OpenTelemetry SDK
        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build()
        
        // Create a tracer
        tracer = openTelemetry.getTracer(serviceName, serviceVersion)
        
        return openTelemetry
    }
    
    /**
     * Get the OpenTelemetry instance
     */
    fun getOpenTelemetry(): OpenTelemetry {
        if (!::openTelemetry.isInitialized) {
            initialize()
        }
        return openTelemetry
    }
    
    /**
     * Get the tracer
     */
    fun getTracer(): Tracer {
        if (!::tracer.isInitialized) {
            initialize()
        }
        return tracer
    }
    
    /**
     * Shutdown OpenTelemetry
     */
    suspend fun shutdown() {
        if (::openTelemetry.isInitialized) {
            withContext(Dispatchers.IO) {
                (openTelemetry as OpenTelemetrySdk).shutdown().join(10, TimeUnit.SECONDS)
            }
        }
    }
}