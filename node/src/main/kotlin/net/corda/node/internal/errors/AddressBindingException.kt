package net.corda.node.internal.errors

import net.corda.core.utilities.NetworkHostAndPort
import java.net.BindException

class AddressBindingException(val addresses: Set<NetworkHostAndPort>) : BindException(message(addresses)) {

    constructor(address: NetworkHostAndPort) : this(setOf(address))

    private companion object {
        private fun message(addresses: Set<NetworkHostAndPort>): String {
            require(addresses.isNotEmpty())
            return if (addresses.size > 1) {
                "Failed to bind on an address in ${addresses.joinToString(", ", "[", "]")}."
            } else {
                "Failed to bind on address ${addresses.single()}."
            }
        }
    }
}