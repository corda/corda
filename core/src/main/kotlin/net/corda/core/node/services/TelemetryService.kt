package net.corda.core.node.services

import net.corda.core.DoNotImplement
import net.corda.core.internal.telemetry.OpenTelemetryHandle

@DoNotImplement
interface TelemetryService {
    fun getOpenTelemetry(): OpenTelemetryHandle?
}