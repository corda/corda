package net.corda.client.rpc.internal

import net.corda.core.internal.telemetry.OpenTelemetryComponent
import net.corda.core.internal.telemetry.SimpleLogTelemetryComponent
import net.corda.core.internal.telemetry.TelemetryServiceImpl

class RPCClientTelemetry(val serviceName: String, val openTelemetryEnabled: Boolean, val simpleLogTelemetryEnabled: Boolean, val spanStartEndEventsEnabled: Boolean) {
    val telemetryService = TelemetryServiceImpl()

    init {
        if (openTelemetryEnabled) {
            val openTelemetryComponent = OpenTelemetryComponent(serviceName, spanStartEndEventsEnabled)
            if (openTelemetryComponent.isEnabled()) {
                telemetryService.addTelemetryComponent(openTelemetryComponent)
            }
        }
        if (simpleLogTelemetryEnabled) {
            val simpleLogTelemetryComponent = SimpleLogTelemetryComponent()
            telemetryService.addTelemetryComponent(simpleLogTelemetryComponent)
        }
    }
}