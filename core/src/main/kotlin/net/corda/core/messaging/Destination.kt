package net.corda.core.messaging

import net.corda.core.identity.CordaX500Name
import java.security.PublicKey

interface Destination

interface NetworkDestination : Destination {
    val owningKey: PublicKey
    fun nameOrNull(): CordaX500Name?
}

interface WellKnownNetworkDestination : NetworkDestination {
    override fun nameOrNull(): CordaX500Name
}