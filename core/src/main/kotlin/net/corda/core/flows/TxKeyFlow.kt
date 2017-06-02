package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import org.bouncycastle.cert.X509CertificateHolder
import java.security.cert.CertPath

/**
 * Very basic flow which exchanges transaction key and certificate paths between two parties in a transaction.
 * This is intended for use as a subflow of another flow.
 */
object TxKeyFlow {
    abstract class AbstractIdentityFlow<out T>(val otherSide: Party, val revocationEnabled: Boolean): FlowLogic<T>() {
        fun validateIdentity(untrustedIdentity: AnonymousIdentity): AnonymousIdentity {
            val (certPath, theirCert, txIdentity) = untrustedIdentity
            if (theirCert.subject == otherSide.name) {
                serviceHub.identityService.registerAnonymousIdentity(txIdentity, otherSide, certPath)
                return AnonymousIdentity(certPath, theirCert, txIdentity)
            } else
                throw IllegalStateException("Expected certificate subject to be ${otherSide.name} but found ${theirCert.subject}")
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class Requester(otherSide: Party,
                    override val progressTracker: ProgressTracker) : AbstractIdentityFlow<Map<Party, AnonymousIdentity>>(otherSide, false) {
        constructor(otherSide: Party) : this(otherSide, tracker())
        companion object {
            object AWAITING_KEY : ProgressTracker.Step("Awaiting key")

            fun tracker() = ProgressTracker(AWAITING_KEY)
        }

        @Suspendable
        override fun call(): Map<Party, AnonymousIdentity> {
            progressTracker.currentStep = AWAITING_KEY
            val myIdentityFragment = serviceHub.keyManagementService.freshKeyAndCert(serviceHub.myInfo.legalIdentityAndCert, revocationEnabled)
            val myIdentity = AnonymousIdentity(myIdentityFragment)
            val theirIdentity = receive<AnonymousIdentity>(otherSide).unwrap { validateIdentity(it) }
            send(otherSide, myIdentity)
            return mapOf(Pair(otherSide, myIdentity),
                    Pair(serviceHub.myInfo.legalIdentity, theirIdentity))
        }
    }

    /**
     * Flow which waits for a key request from a counterparty, generates a new key and then returns it to the
     * counterparty and as the result from the flow.
     */
    @InitiatedBy(Requester::class)
    class Provider(otherSide: Party) : AbstractIdentityFlow<Map<Party, AnonymousIdentity>>(otherSide, false) {
        companion object {
            object SENDING_KEY : ProgressTracker.Step("Sending key")
        }

        override val progressTracker: ProgressTracker = ProgressTracker(SENDING_KEY)

        @Suspendable
        override fun call(): Map<Party, AnonymousIdentity> {
            val revocationEnabled = false
            progressTracker.currentStep = SENDING_KEY
            val myIdentityFragment = serviceHub.keyManagementService.freshKeyAndCert(serviceHub.myInfo.legalIdentityAndCert, revocationEnabled)
            val myIdentity = AnonymousIdentity(myIdentityFragment)
            send(otherSide, myIdentity)
            val theirIdentity = receive<AnonymousIdentity>(otherSide).unwrap { validateIdentity(it) }
            return mapOf(Pair(otherSide, myIdentity),
                    Pair(serviceHub.myInfo.legalIdentity, theirIdentity))
        }
    }

    @CordaSerializable
    data class AnonymousIdentity(
            val certPath: CertPath,
            val certificate: X509CertificateHolder,
            val identity: AnonymousParty) {
        constructor(myIdentity: Pair<X509CertificateHolder, CertPath>) : this(myIdentity.second,
                myIdentity.first,
                AnonymousParty(myIdentity.second.certificates.last().publicKey))
    }
}
