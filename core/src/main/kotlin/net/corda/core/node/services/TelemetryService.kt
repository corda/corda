package net.corda.core.node.services

import io.opentelemetry.api.OpenTelemetry
import net.corda.core.DoNotImplement

@DoNotImplement
interface TelemetryService {
    fun getOpenTelemetry(): OpenTelemetry?
}