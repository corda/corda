package net.corda.nodeapi.internal

import net.corda.core.serialization.CordaSerializable

/**
 * Identifier for service types a node can expose over the network to other peers. These types are placed into network
 * map advertisements. Services that are purely local and are not providing functionality to other parts of the network
 * don't need a declared service type.
 */
@CordaSerializable
class ServiceType private constructor(val id: String) {
    init {
        // Enforce:
        //
        //  * IDs must start with a lower case letter
        //  * IDs can only contain alphanumeric, full stop and underscore ASCII characters
        require(id.matches(Regex("[a-z][a-zA-Z0-9._]+"))) { id }
    }

    companion object {
        val corda: ServiceType
            get() {
                val stack = Throwable().stackTrace
                val caller = stack.first().className
                require(caller.startsWith("net.corda.")) { "Corda ServiceType namespace is reserved for Corda core components" }
                return ServiceType("corda")
            }

        val notary: ServiceType = corda.getSubType("notary")

        fun parse(id: String): ServiceType = ServiceType(id)

        private fun baseWithSubType(baseId: String, subTypeId: String) = ServiceType("$baseId.$subTypeId")
    }

    fun getSubType(subTypeId: String): ServiceType = baseWithSubType(id, subTypeId)

    fun isSubTypeOf(superType: ServiceType) = (id == superType.id) || id.startsWith(superType.id + ".")
    fun isNotary() = isSubTypeOf(notary)

    override fun equals(other: Any?): Boolean = other === this || other is ServiceType && other.id == this.id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id
}
