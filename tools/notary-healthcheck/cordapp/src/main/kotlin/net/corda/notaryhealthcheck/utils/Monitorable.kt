package net.corda.notaryhealthcheck.utils

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

/**
 * Utility class to describe the target for a notary health check - one of
 * * notary service
 * * member node of a distributed notary
 * * simple (single node) notary
 *
 * @param notary the (possibly shared) notary identity.
 * @param party the target node, which is the same as [notary] in the non-distributed case.
 */
@CordaSerializable
class Monitorable(val notary: Party, val party: Party) {
    /** Whether this is a constituent node of a distributed notary. */
    private val slave = party != notary

    override fun toString(): String {
        return if (slave) "Notary: ${notary.name} Node ${party.name}" else "Notary: ${notary.name}"
    }
}
