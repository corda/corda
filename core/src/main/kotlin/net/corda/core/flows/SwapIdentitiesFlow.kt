package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * Very basic flow which generates new confidential identities for parties in a transaction and exchanges the transaction
 * key and certificate paths between the parties. This is intended for use as a subflow of another flow which builds a
 * transaction.
 */
@StartableByRPC
@InitiatingFlow
class SwapIdentitiesFlow(val otherParty: Party,
                         val revocationEnabled: Boolean,
                         override val progressTracker: ProgressTracker) : FlowLogic<LinkedHashMap<Party, AnonymousParty>>() {
    constructor(otherParty: Party) : this(otherParty, false, tracker())

    companion object {
        object AWAITING_KEY : ProgressTracker.Step("Awaiting key")

        fun tracker() = ProgressTracker(AWAITING_KEY)
        fun validateAndRegisterIdentity(identityService: IdentityService, otherSide: Party, anonymousOtherSide: PartyAndCertificate): PartyAndCertificate {
            require(anonymousOtherSide.name == otherSide.name)
            // Validate then store their identity so that we can prove the key in the transaction is owned by the
            // counterparty.
            identityService.verifyAndRegisterIdentity(anonymousOtherSide)
            return anonymousOtherSide
        }
    }

    @Suspendable
    override fun call(): LinkedHashMap<Party, AnonymousParty> {
        progressTracker.currentStep = AWAITING_KEY
        val legalIdentityAnonymous = serviceHub.keyManagementService.freshKeyAndCert(ourIdentity, revocationEnabled)

        // Special case that if we're both parties, a single identity is generated
        val otherSession = initiateFlow(otherParty)
        val identities = LinkedHashMap<Party, AnonymousParty>()
        if (otherSession.counterparty in serviceHub.myInfo.legalIdentities) {
            identities.put(otherSession.counterparty, legalIdentityAnonymous.party.anonymise())
        } else {
            val anonymousOtherSide = otherSession.sendAndReceive<PartyAndCertificate>(legalIdentityAnonymous).unwrap { confidentialIdentity ->
                validateAndRegisterIdentity(serviceHub.identityService, otherSession.counterparty, confidentialIdentity)
            }
            identities.put(ourIdentity.party, legalIdentityAnonymous.party.anonymise())
            identities.put(otherSession.counterparty, anonymousOtherSide.party.anonymise())
        }
        return identities
    }

}
