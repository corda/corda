package net.corda.confidential

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaInternal
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.verify
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.VisibleForTesting
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.security.SignatureException

/**
 * Very basic flow which generates new confidential identities for parties in a transaction and exchanges the transaction
 * key and certificate paths between the parties. This is intended for use as a sub-flow of another flow which builds a
 * transaction. The flow running on the other side must also call this flow at the correct location.
 */
class SwapIdentitiesFlow @JvmOverloads constructor(private val otherSideSession: FlowSession,
                                                   override val progressTracker: ProgressTracker = tracker()) : FlowLogic<SwapIdentitiesFlow.AnonymousResult>() {
    companion object {
        object GENERATING_IDENTITY : ProgressTracker.Step("Generating our anonymous identity")
        object SIGNING_IDENTITY : ProgressTracker.Step("Signing our anonymous identity")
        object AWAITING_IDENTITY : ProgressTracker.Step("Awaiting counterparty's anonymous identity")
        object VERIFYING_IDENTITY : ProgressTracker.Step("Verifying counterparty's anonymous identity")

        @JvmStatic
        fun tracker(): ProgressTracker = ProgressTracker(GENERATING_IDENTITY, SIGNING_IDENTITY, AWAITING_IDENTITY, VERIFYING_IDENTITY)

        /**
         * Generate the deterministic data blob the confidential identity's key holder signs to indicate they want to
         * represent the subject named in the X.509 certificate. Note that this is never actually sent between nodes,
         * but only the signature is sent. The blob is built independently on each node and the received signature
         * verified against the expected blob, rather than exchanging the blob.
         */
        @CordaInternal
        @VisibleForTesting
        internal fun buildDataToSign(identity: PartyAndCertificate): ByteArray {
            return CertificateOwnershipAssertion(identity.name, identity.owningKey).serialize().bytes
        }

        @CordaInternal
        @VisibleForTesting
        internal fun validateAndRegisterIdentity(serviceHub: ServiceHub,
                                                 otherSide: Party,
                                                 theirAnonymousIdentity: PartyAndCertificate,
                                                 signature: DigitalSignature): PartyAndCertificate {
            if (theirAnonymousIdentity.name != otherSide.name) {
                throw SwapIdentitiesException("Certificate subject must match counterparty's well known identity.")
            }
            try {
                theirAnonymousIdentity.owningKey.verify(buildDataToSign(theirAnonymousIdentity), signature)
            } catch (ex: SignatureException) {
                throw SwapIdentitiesException("Signature does not match the expected identity ownership assertion.", ex)
            }
            // Validate then store their identity so that we can prove the key in the transaction is owned by the counterparty.
            serviceHub.identityService.verifyAndRegisterIdentity(theirAnonymousIdentity)
            return theirAnonymousIdentity
        }
    }

    @Suspendable
    override fun call(): AnonymousResult {
        progressTracker.currentStep = GENERATING_IDENTITY
        val ourAnonymousIdentity = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false)
        val data = buildDataToSign(ourAnonymousIdentity)
        progressTracker.currentStep = SIGNING_IDENTITY
        val signature = serviceHub.keyManagementService.sign(data, ourAnonymousIdentity.owningKey).withoutKey()
        val ourIdentWithSig = IdentityWithSignature(ourAnonymousIdentity, signature)
        progressTracker.currentStep = AWAITING_IDENTITY
        val theirAnonymousIdentity = otherSideSession.sendAndReceive<IdentityWithSignature>(ourIdentWithSig).unwrap { theirIdentWithSig ->
            progressTracker.currentStep = VERIFYING_IDENTITY
            validateAndRegisterIdentity(serviceHub, otherSideSession.counterparty, theirIdentWithSig.identity, theirIdentWithSig.signature)
        }
        return AnonymousResult(ourAnonymousIdentity.party.anonymise(), theirAnonymousIdentity.party.anonymise())
    }

    /**
     * Result class containing the caller's anonymous identity ([ourIdentity]) and the counterparty's ([theirIdentity]).
     */
    @CordaSerializable
    data class AnonymousResult(val ourIdentity: AnonymousParty, val theirIdentity: AnonymousParty)

    @CordaSerializable
    private data class IdentityWithSignature(val identity: PartyAndCertificate, val signature: DigitalSignature)

    /**
     * Data class used only in the context of asserting that the owner of the private key for the listed key wants to use it
     * to represent the named entity. This is paired with an X.509 certificate (which asserts the signing identity says
     * the key represents the named entity) and protects against a malicious party incorrectly claiming others'
     * keys.
     */
    @CordaSerializable
    private data class CertificateOwnershipAssertion(val name: CordaX500Name, val owningKey: PublicKey)
}

open class SwapIdentitiesException @JvmOverloads constructor(message: String, cause: Throwable? = null) : FlowException(message, cause)
