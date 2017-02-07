package net.corda.core.node.services

/**
 * Identifier for service types a node can expose over the network to other peers. These types are placed into network
 * map advertisements. Services that are purely local and are not providing functionality to other parts of the network
 * don't need a declared service type.
 */
sealed class ServiceType(val id: String) {
    init {
        // Enforce:
        //
        //  * IDs must start with a lower case letter
        //  * IDs can only contain alphanumeric, full stop and underscore ASCII characters
        require(id.matches(Regex("[a-z][a-zA-Z0-9._]+"))) { id }
    }
    private class ServiceTypeImpl(baseId: String, subTypeId: String) : ServiceType("$baseId.$subTypeId")

    private class ServiceTypeDirect(id: String) : ServiceType(id)

    companion object {
        val corda: ServiceType
            get() {
                val stack = Throwable().stackTrace
                val caller = stack.first().className
                require(caller.startsWith("net.corda.")) { "Corda ServiceType namespace is reserved for Corda core components" }
                return ServiceTypeDirect("corda")
            }

        val notary: ServiceType = corda.getSubType("notary")
        val regulator: ServiceType = corda.getSubType("regulator")

        fun getServiceType(namespace: String, typeId: String): ServiceType {
            require(!namespace.startsWith("corda")) { "Corda namespace is protected" }
            return ServiceTypeImpl(namespace, typeId)
        }

        fun parse(id: String): ServiceType = ServiceTypeDirect(id)
    }

    fun getSubType(subTypeId: String): ServiceType = ServiceTypeImpl(id, subTypeId)

    override operator fun equals(other: Any?): Boolean = (other is ServiceType) && (other.id == this.id)

    fun isSubTypeOf(superType: ServiceType) = (id == superType.id) || id.startsWith(superType.id + ".")
    fun isNotary() = isSubTypeOf(notary)

    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id.toString()
}
