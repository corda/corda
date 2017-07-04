package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
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
class TransactionKeyFlow(val otherSide: Party,
                         val revocationEnabled: Boolean,
                         override val progressTracker: ProgressTracker) : FlowLogic<LinkedHashMap<Party, AnonymisedIdentity>>() {
    constructor(otherSide: Party) : this(otherSide, false, tracker())

    companion object {
        object AWAITING_KEY : ProgressTracker.Step("Awaiting key")

        fun tracker() = ProgressTracker(AWAITING_KEY)
        fun validateIdentity(otherSide: Party, anonymousOtherSide: AnonymisedIdentity): AnonymisedIdentity {
            require(anonymousOtherSide.certificate.subject == otherSide.name)
            return anonymousOtherSide
        }
    }

    @Suspendable
    override fun call(): LinkedHashMap<Party, AnonymisedIdentity> {
        progressTracker.currentStep = AWAITING_KEY
        val legalIdentityAnonymous = serviceHub.keyManagementService.freshKeyAndCert(serviceHub.myInfo.legalIdentityAndCert, revocationEnabled)
        serviceHub.identityService.registerAnonymousIdentity(legalIdentityAnonymous.identity, serviceHub.myInfo.legalIdentity, legalIdentityAnonymous.certPath)

        // Special case that if we're both parties, a single identity is generated
        val identities = LinkedHashMap<Party, AnonymisedIdentity>()
        if (otherSide == serviceHub.myInfo.legalIdentity) {
            identities.put(otherSide, legalIdentityAnonymous)
        } else {
            val otherSideAnonymous = sendAndReceive<AnonymisedIdentity>(otherSide, legalIdentityAnonymous).unwrap { validateIdentity(otherSide, it) }
            identities.put(serviceHub.myInfo.legalIdentity, legalIdentityAnonymous)
            identities.put(otherSide, otherSideAnonymous)
        }
        return identities
    }

}
