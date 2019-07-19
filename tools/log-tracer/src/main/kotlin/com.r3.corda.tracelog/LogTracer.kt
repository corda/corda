package com.r3.corda.tracelog

import io.jaegertracing.Configuration
import io.jaegertracing.Configuration.*
import io.jaegertracing.internal.samplers.ConstSampler
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer

object LogTracer {
    val INSTANCE: Tracer by lazy {
        val sampler = SamplerConfiguration.fromEnv()
                .withType(ConstSampler.TYPE)
                .withParam(1)
        val sender = SenderConfiguration.fromEnv()
                .withEndpoint("http://localhost:14268/api/traces")
        val reporter = ReporterConfiguration.fromEnv()
                .withSender(sender)
                .withLogSpans(true)
                .withFlushInterval(200)
        val tracer: Tracer = Configuration("Corda")
                .withSampler(sampler)
                .withReporter(reporter)
                .tracer
        GlobalTracer.register(tracer)
        tracer
    }
}
