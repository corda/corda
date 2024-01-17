package net.corda.core.node.services

import net.corda.core.DoNotImplement


@DoNotImplement
interface TelemetryService {
    fun <T> getTelemetryHandle(telemetryClass: Class<T>): T?
}