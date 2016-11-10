package net.corda.node.services.api

import net.corda.core.node.services.ServiceType

/**
 * Placeholder interface for regulator services.
 */
interface RegulatorService {
    companion object {
        val type = ServiceType.regulator
    }
}
