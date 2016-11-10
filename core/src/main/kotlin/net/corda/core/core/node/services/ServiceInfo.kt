package net.corda.core.node.services

/**
 * A container for additional information for an advertised service.
 *
 * @param type the ServiceType identifier
 * @param name the service name, used for differentiating multiple services of the same type. Can also be used as a
 *             grouping identifier for nodes collectively running a distributed service.
 */
data class ServiceInfo(val type: ServiceType, val name: String? = null) {
    companion object {
        fun parse(encoded: String): ServiceInfo {
            val parts = encoded.split("|")
            require(parts.size > 0 && parts.size <= 2) { "Invalid number of elements found" }
            val type = ServiceType.parse(parts[0])
            val name = parts.getOrNull(1)
            return ServiceInfo(type, name)
        }
    }

    override fun toString() = if (name != null) "$type|$name" else type.toString()
}

fun Iterable<ServiceInfo>.containsType(type: ServiceType) = any { it.type == type }
