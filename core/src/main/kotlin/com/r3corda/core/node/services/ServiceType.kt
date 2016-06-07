package com.r3corda.core.node.services

/**
 * Identifier for service types a node can expose over the network to other peers. These types are placed into network
 * map advertisements. Services that are purely local and are not providing functionality to other parts of the network
 * don't need a declared service type.
 */
abstract class ServiceType(val id: String) {
    init {
        // Enforce:
        //
        //  * IDs must start with a lower case letter
        //  * IDs can only contain alphanumeric, full stop and underscore ASCII characters
        require(id.matches(Regex("[a-z][a-zA-Z0-9._]+")))
    }

    override operator fun equals(other: Any?): Boolean =
            if (other is ServiceType) {
                id == other.id
            } else {
                false
            }

    fun isSubTypeOf(superType: ServiceType) = (id == superType.id) || id.startsWith(superType.id + ".")

    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id.toString()
}