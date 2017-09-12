package net.corda.testing.driver

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.testing.DUMMY_MAP

sealed class NetworkMapStartStrategy {
    internal abstract val startDedicated: Boolean
    internal abstract val legalName: CordaX500Name
    internal fun serviceConfig(address: NetworkHostAndPort) = mapOf(
            "address" to address.toString(),
            "legalName" to legalName.toString()
    )

    class Dedicated(startAutomatically: Boolean) : NetworkMapStartStrategy() {
        override val startDedicated = startAutomatically
        override val legalName = DUMMY_MAP.name
    }

    class Nominated(override val legalName: CordaX500Name) : NetworkMapStartStrategy() {
        override val startDedicated = false
    }
}
