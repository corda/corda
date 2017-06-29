package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * Very basic flow which exchanges transaction key and certificate paths between two parties in a transaction.
 * This is intended for use as a subflow of another flow.
 */
object TransactionKeyFlow {
    abstract class AbstractIdentityFlow<out T>(val otherSide: Party, val revocationEnabled: Boolean): FlowLogic<T>() {
        fun validateIdentity(untrustedIdentity: AnonymisedIdentity): AnonymisedIdentity {
            val (certPath, theirCert, txIdentity) = untrustedIdentity
            if (theirCert.subject == otherSide.name) {
                serviceHub.identityService.registerAnonymousIdentity(txIdentity, otherSide, certPath)
                return AnonymisedIdentity(certPath, theirCert, txIdentity)
            } else
                throw IllegalStateException("Expected certificate subject to be ${otherSide.name} but found ${theirCert.subject}")
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class Requester(otherSide: Party,
                    override val progressTracker: ProgressTracker) : AbstractIdentityFlow<TxIdentities>(otherSide, false) {
        constructor(otherSide: Party) : this(otherSide, tracker())
        companion object {
            object AWAITING_KEY : ProgressTracker.Step("Awaiting key")

            fun tracker() = ProgressTracker(AWAITING_KEY)
        }

        @Suspendable
        override fun call(): TxIdentities {
            progressTracker.currentStep = AWAITING_KEY
            val legalIdentityAnonymous = serviceHub.keyManagementService.freshKeyAndCert(serviceHub.myInfo.legalIdentityAndCert, revocationEnabled)
            serviceHub.identityService.registerAnonymousIdentity(legalIdentityAnonymous.identity, serviceHub.myInfo.legalIdentity, legalIdentityAnonymous.certPath)

            // Special case that if we're both parties, a single identity is generated
            return if (otherSide == serviceHub.myInfo.legalIdentity) {
                TxIdentities(Pair(otherSide, legalIdentityAnonymous))
            } else {
                val otherSideAnonymous = receive<AnonymisedIdentity>(otherSide).unwrap { validateIdentity(it) }
                send(otherSide, legalIdentityAnonymous)
                TxIdentities(Pair(serviceHub.myInfo.legalIdentity, legalIdentityAnonymous),
                        Pair(otherSide, otherSideAnonymous))
            }
        }
    }

    /**
     * Flow which waits for a key request from a counterparty, generates a new key and then returns it to the
     * counterparty and as the result from the flow.
     */
    @InitiatedBy(Requester::class)
    class Provider(otherSide: Party) : AbstractIdentityFlow<TxIdentities>(otherSide, false) {
        companion object {
            object SENDING_KEY : ProgressTracker.Step("Sending key")
        }

        override val progressTracker: ProgressTracker = ProgressTracker(SENDING_KEY)

        @Suspendable
        override fun call(): TxIdentities {
            val revocationEnabled = false
            progressTracker.currentStep = SENDING_KEY
            val legalIdentityAnonymous = serviceHub.keyManagementService.freshKeyAndCert(serviceHub.myInfo.legalIdentityAndCert, revocationEnabled)
            val otherSideAnonymous = sendAndReceive<AnonymisedIdentity>(otherSide, legalIdentityAnonymous).unwrap { validateIdentity(it) }
            return TxIdentities(Pair(serviceHub.myInfo.legalIdentity, legalIdentityAnonymous),
                    Pair(otherSide, otherSideAnonymous))
        }
    }

    @CordaSerializable
    data class TxIdentities(val identities: List<Pair<Party, AnonymisedIdentity>>) {
        constructor(vararg identities: Pair<Party, AnonymisedIdentity>) : this(identities.toList())
        init {
            require(identities.size == identities.map { it.first }.toSet().size) { "Identities must be unique: ${identities.map { it.first }}" }
        }
        fun forParty(party: Party): AnonymisedIdentity = identities.single { it.first == party }.second
        fun toMap(): Map<Party, AnonymisedIdentity> = this.identities.toMap()
    }
}
