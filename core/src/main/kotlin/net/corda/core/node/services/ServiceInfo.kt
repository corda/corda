package net.corda.core.node.services

import net.corda.core.flows.AdvertisedFlow
import java.util.*
import net.corda.core.serialization.CordaSerializable

/**
 * A container for additional information for an advertised service.
 *
 * @param type the ServiceType identifier
 * @param name the service name, used for differentiating multiple services of the same type. Can also be used as a
 *             grouping identifier for nodes collectively running a distributed service.
 * @param advertisedFlows flows advertised by this service.
 */
// TODO We should advertise services or plugins? Or maybe just service version, not flows.
// TODO I am not sure if there should be service -> flows correspondence. However, there is a case when we can have a distributed service and
//  different flows on service nodes, I guess we don't want situations like that.
//  Checking of that situation is performed in NetworkMapCache when is queried for advertised flows for a given service
//  (for now, because it's more NetworkMapService design question?).
@CordaSerializable
data class ServiceInfo(val type: ServiceType, val name: String? = null, val advertisedFlows: ArrayList<AdvertisedFlow> = ArrayList()) {
    init {
        // TODO Sanity check.
        require(advertisedFlows.groupBy { it.genericFlowName }.size == advertisedFlows.size)
    }
    companion object {
        fun parse(encoded: String): ServiceInfo {
            val parts = encoded.split("|")
            require(parts.isNotEmpty() && parts.size <= 2) { "Invalid number of elements found" }
            val type = ServiceType.parse(parts[0])
            val name = parts.getOrNull(1)
            return ServiceInfo(type, name)
        }
    }

    override fun toString() = if (name != null) "$type|$name" else type.toString()
}

fun Iterable<ServiceInfo>.containsType(type: ServiceType) = any { it.type == type }
