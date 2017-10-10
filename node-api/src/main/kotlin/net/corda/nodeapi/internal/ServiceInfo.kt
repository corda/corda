package net.corda.nodeapi.internal

import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable

/**
 * A container for additional information for an advertised service.
 *
 * @param type the ServiceType identifier
 * @param name the service name, used for differentiating multiple services of the same type. Can also be used as a
 *             grouping identifier for nodes collectively running a distributed service.
 */
@CordaSerializable
data class ServiceInfo(val type: ServiceType, val name: CordaX500Name? = null) {
    companion object {
        fun parse(encoded: String): ServiceInfo {
            val parts = encoded.split("|")
            require(parts.size in 1..2) { "Invalid number of elements found" }
            val type = ServiceType.parse(parts[0])
            val name = parts.getOrNull(1)
            val principal = name?.let { CordaX500Name.parse(it) }
            return ServiceInfo(type, principal)
        }
    }

    override fun toString() = if (name != null) "$type|$name" else type.toString()
}
