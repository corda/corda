package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.identity.Party
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * Very basic flow which exchanges transaction key and certificate paths between two parties in a transaction.
 * This is intended for use as a subflow of another flow.
 *
 * @property counterparties a list of the well known identities of the counterparties in a transaction.
 * @param revocationEnabled whether generated certificates should have revocation enabled. Currently this is unsupported
 * by the validation logic and and must be false.
 */
@StartableByRPC
@InitiatingFlow
class TransactionKeyFlow(val counterparties: List<Party>,
                         private val revocationEnabled: Boolean,
                         override val progressTracker: ProgressTracker) : FlowLogic<LinkedHashMap<Party, AnonymousParty>>() {
    constructor(otherSide: Party) : this(listOf(otherSide), false, tracker())

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
        val legalIdentityAnonymous = serviceHub.keyManagementService.freshKeyAndCert(serviceHub.myInfo.legalIdentityAndCert, revocationEnabled)

        // Special case that if we're both parties, a single identity is generated
        val identities = LinkedHashMap<Party, AnonymousParty>()
        identities.put(serviceHub.myInfo.legalIdentity, legalIdentityAnonymous.party.anonymise())
        counterparties.filter { it != serviceHub.myInfo.legalIdentity }.forEach { otherSide ->
            val anonymousOtherSide = sendAndReceive<PartyAndCertificate>(otherSide, legalIdentityAnonymous).unwrap { confidentialIdentity ->
                validateAndRegisterIdentity(serviceHub.identityService, otherSide, confidentialIdentity)
            }
            identities.put(otherSide, anonymousOtherSide.party.anonymise())
        }
        return identities
    }

}
