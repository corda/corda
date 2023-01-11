package net.corda.client.rpc.internal

import net.corda.core.internal.telemetry.OpenTelemetryComponent
import net.corda.core.internal.telemetry.SimpleLogTelemetryComponent
import net.corda.core.internal.telemetry.TelemetryServiceImpl
import net.corda.core.utilities.contextLogger

class RPCClientTelemetry(val serviceName: String, val openTelemetryEnabled: Boolean,
                         val simpleLogTelemetryEnabled: Boolean, val spanStartEndEventsEnabled: Boolean,
                         val copyBaggageToTags: Boolean) {

    companion object {
        private val log = contextLogger()
    }

    val telemetryService = TelemetryServiceImpl()

    init {
        if (openTelemetryEnabled) {
            try {
                val openTelemetryComponent = OpenTelemetryComponent(serviceName, spanStartEndEventsEnabled, copyBaggageToTags)
                if (openTelemetryComponent.isEnabled()) {
                    telemetryService.addTelemetryComponent(openTelemetryComponent)
                    log.debug("OpenTelemetry enabled")
                }
            }
            catch (ex: NoClassDefFoundError) {
                // Do nothing api or sdk not available on classpath
                log.debug("OpenTelemetry not enabled, api or sdk not found on classpath")
            }
        }
        if (simpleLogTelemetryEnabled) {
            val simpleLogTelemetryComponent = SimpleLogTelemetryComponent()
            telemetryService.addTelemetryComponent(simpleLogTelemetryComponent)
            log.debug("SimpleLogTelemetry enabled")
        }
    }

    fun <T> getTelemetryHandle(telemetryClass: Class<T>): T? {
        return telemetryService.getTelemetryHandle(telemetryClass)
    }
}