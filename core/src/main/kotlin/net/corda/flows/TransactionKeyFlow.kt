package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * Very basic flow which exchanges transaction key and certificate paths between two parties in a transaction.
 * This is intended for use as a subflow of another flow.
 */
@StartableByRPC
@InitiatingFlow
class TransactionKeyFlow(otherSide: Party,
                         override val progressTracker: ProgressTracker) : AbstractIdentityFlow(otherSide, false) {
    constructor(otherSide: Party) : this(otherSide, tracker())

    companion object {
        object AWAITING_KEY : ProgressTracker.Step("Awaiting key")

        fun tracker() = ProgressTracker(AWAITING_KEY)
    }

    @Suspendable
    override fun call(): TransactionIdentities {
        progressTracker.currentStep = AWAITING_KEY
        val legalIdentityAnonymous = serviceHub.keyManagementService.freshKeyAndCert(serviceHub.myInfo.legalIdentityAndCert, revocationEnabled)
        serviceHub.identityService.registerAnonymousIdentity(legalIdentityAnonymous.identity, serviceHub.myInfo.legalIdentity, legalIdentityAnonymous.certPath)

        // Special case that if we're both parties, a single identity is generated
        return if (otherSide == serviceHub.myInfo.legalIdentity) {
            TransactionIdentities(Pair(otherSide, legalIdentityAnonymous))
        } else {
            val otherSideAnonymous = receive<AnonymisedIdentity>(otherSide).unwrap { validateIdentity(it) }
            send(otherSide, legalIdentityAnonymous)
            TransactionIdentities(Pair(serviceHub.myInfo.legalIdentity, legalIdentityAnonymous),
                    Pair(otherSide, otherSideAnonymous))
        }
    }

}
