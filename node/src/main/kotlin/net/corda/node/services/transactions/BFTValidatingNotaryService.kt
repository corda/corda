package net.corda.node.services.transactions

import net.corda.core.crypto.Party
import net.corda.core.node.services.TimestampChecker
import net.corda.flows.ValidatingNotaryFlow
import net.corda.node.services.api.ServiceHubInternal

/**
 * A validating notary service operated by a group of parties that don't necessarily trust each other.
 *
 * To validate a transaction, this service collects proofs that the transaction has been validated and committed by a
 * specified number of notary nodes.
 *
 * Based on the [bft-smart library](https://github.com/bft-smart/library).
 */
class BFTValidatingNotaryService(services: ServiceHubInternal,
                                  val timestampChecker: TimestampChecker,
                                  val uniquenessProvider: BFTSmartUniquenessProvider) : NotaryService(services) {
    companion object {
        val type = ValidatingNotaryService.type.getSubType("bft")
    }

    override fun createFlow(otherParty: Party): ValidatingNotaryFlow {
        return ValidatingNotaryFlow(otherParty, timestampChecker, uniquenessProvider)
    }
}
