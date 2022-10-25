package net.corda.opentelemetrydriver

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes

object OpenTelemetryDriver {
    private fun buildAndGetOpenTelemetry(serviceName: String): OpenTelemetry {
        val resource: Resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, serviceName)))

        val sdkTracerProvider: SdkTracerProvider = SdkTracerProvider.builder()
                 .addSpanProcessor(BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder().build()).build())
                .setResource(resource)
                .build()

        val sdkMeterProvider: SdkMeterProvider = SdkMeterProvider.builder()
                .registerMetricReader(PeriodicMetricReader.builder(OtlpGrpcMetricExporter.builder().build()).build())
                .setResource(resource)
                .build()

        return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setMeterProvider(sdkMeterProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal()
    }

    @Volatile
    private var OPENTELEMETRY_INSTANCE: OpenTelemetry? = null

    fun getOpenTelemetry(serviceName: String): OpenTelemetry {
        return OPENTELEMETRY_INSTANCE ?: synchronized(this) {
            OPENTELEMETRY_INSTANCE ?: buildAndGetOpenTelemetry(serviceName).also {
                OPENTELEMETRY_INSTANCE = it
            }
        }
    }
}
