package net.corda.confidential

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.secureRandomBytes
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.io.ByteArrayOutputStream

/**
 * Very basic flow which generates new confidential identities for parties in a transaction and exchanges the transaction
 * key and certificate paths between the parties. This is intended for use as a subflow of another flow which builds a
 * transaction.
 */
@StartableByRPC
@InitiatingFlow
class SwapIdentitiesFlow(private val otherParty: Party,
                         private val revocationEnabled: Boolean,
                         override val progressTracker: ProgressTracker) : FlowLogic<LinkedHashMap<Party, AnonymousParty>>() {
    constructor(otherParty: Party) : this(otherParty, false, tracker())

    companion object {
        object AWAITING_KEY : ProgressTracker.Step("Awaiting key")
        val NONCE_SIZE_BYTES = 16

        fun tracker() = ProgressTracker(AWAITING_KEY)
        fun buildDataToSign(identity: PartyAndCertificate, nonce: ByteArray): ByteArray {
            val buffer = ByteArrayOutputStream(1024)
            buffer.write(identity.serialize().bytes)
            buffer.write(nonce)
            return buffer.toByteArray()
        }

        fun validateAndRegisterIdentity(identityService: IdentityService, otherSide: Party, anonymousOtherSide: PartyAndCertificate, nonce: ByteArray, signature: DigitalSignature): PartyAndCertificate {
            require(anonymousOtherSide.name == otherSide.name)
            val sigWithKey = DigitalSignature.WithKey(anonymousOtherSide.owningKey, signature.bytes)
            require(sigWithKey.verify(buildDataToSign(anonymousOtherSide, nonce)))
            // Validate then store their identity so that we can prove the key in the transaction is owned by the
            // counterparty.
            identityService.verifyAndRegisterIdentity(anonymousOtherSide)
            return anonymousOtherSide
        }
    }

    @Suspendable
    override fun call(): LinkedHashMap<Party, AnonymousParty> {
        progressTracker.currentStep = AWAITING_KEY
        val legalIdentityAnonymous = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, revocationEnabled)

        // Special case that if we're both parties, a single identity is generated
        val identities = LinkedHashMap<Party, AnonymousParty>()
        if (serviceHub.myInfo.isLegalIdentity(otherParty)) {
            identities.put(otherParty, legalIdentityAnonymous.party.anonymise())
        } else {
            val otherSession = initiateFlow(otherParty)
            val ourNonce = secureRandomBytes(NONCE_SIZE_BYTES)
            val theirNonce = otherSession.sendAndReceive<ByteArray>(ourNonce).unwrap { nonce ->
                require(nonce.size == NONCE_SIZE_BYTES)
                nonce
            }
            val data = buildDataToSign(legalIdentityAnonymous, theirNonce)
            val ourSig: DigitalSignature = serviceHub.keyManagementService.sign(data, legalIdentityAnonymous.owningKey)
            val anonymousOtherSide = otherSession.sendAndReceive<IdentityWithSignature>(IdentityWithSignature(legalIdentityAnonymous, ourSig.bytes)).unwrap { (confidentialIdentity, theirSigBytes) ->
                val theirSig = DigitalSignature.WithKey(confidentialIdentity.owningKey, theirSigBytes)
                validateAndRegisterIdentity(serviceHub.identityService, otherParty, confidentialIdentity, ourNonce, theirSig)
            }
            identities.put(ourIdentity, legalIdentityAnonymous.party.anonymise())
            identities.put(otherSession.counterparty, anonymousOtherSide.party.anonymise())
        }
        return identities
    }

    @CordaSerializable
    data class IdentityWithSignature(val identity: PartyAndCertificate, val signature: ByteArray)
}
