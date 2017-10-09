package net.corda.confidential

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.DigitalSignature
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.toX509CertHolder
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.pkcs.CertificationRequestInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.security.PublicKey
import java.security.SignatureException
import java.security.cert.CertPath
import java.util.*

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

        fun tracker() = ProgressTracker(AWAITING_KEY)
        /**
         * Generate the determinstic data blob the confidential identity's key holder signs to indicate they want to
         * represent the subject named in the X.509 certificate. Note that this is never actually sent between nodes,
         * but only the signature is sent. The blob is built independently and the received signature verified against
         * the expected blob.
         */
        fun buildDataToSign(confidentialIdentity: PartyAndCertificate): ByteArray {
            val certReqInfo = CertificateOwnershipAssertion(confidentialIdentity.name, confidentialIdentity.owningKey)
            return certReqInfo.serialize().bytes
        }

        @Throws(SwapIdentitiesException::class)
        fun validateAndRegisterIdentity(identityService: IdentityService,
                                        otherSide: Party,
                                        anonymousOtherSideBytes: PartyAndCertificate,
                                        sigBytes: DigitalSignature): PartyAndCertificate {
            val anonymousOtherSide: PartyAndCertificate = anonymousOtherSideBytes
            if (anonymousOtherSide.name != otherSide.name) {
                throw SwapIdentitiesException("Certificate subject must match counterparty's well known identity.")
            }
            val signature = DigitalSignature.WithKey(anonymousOtherSide.owningKey, sigBytes.bytes)
            try {
                signature.verify(buildDataToSign(anonymousOtherSideBytes))
            } catch(ex: SignatureException) {
                throw SwapIdentitiesException("Signature does not match the expected identity ownership assertion.", ex)
            }
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
        val serializedIdentity = SerializedBytes<PartyAndCertificate>(legalIdentityAnonymous.serialize().bytes)

        // Special case that if we're both parties, a single identity is generated
        val identities = LinkedHashMap<Party, AnonymousParty>()
        if (serviceHub.myInfo.isLegalIdentity(otherParty)) {
            identities.put(otherParty, legalIdentityAnonymous.party.anonymise())
        } else {
            val otherSession = initiateFlow(otherParty)
            val data = buildDataToSign(legalIdentityAnonymous)
            val ourSig: DigitalSignature.WithKey = serviceHub.keyManagementService.sign(data, legalIdentityAnonymous.owningKey)
            val ourIdentWithSig = IdentityWithSignature(serializedIdentity, ourSig.withoutKey())
            val anonymousOtherSide = otherSession.sendAndReceive<IdentityWithSignature>(ourIdentWithSig)
                    .unwrap { (confidentialIdentityBytes, theirSigBytes) ->
                        val confidentialIdentity: PartyAndCertificate = confidentialIdentityBytes.bytes.deserialize()
                        validateAndRegisterIdentity(serviceHub.identityService, otherParty, confidentialIdentity, theirSigBytes)
                    }
            identities.put(ourIdentity, legalIdentityAnonymous.party.anonymise())
            identities.put(otherParty, anonymousOtherSide.party.anonymise())
        }
        return identities
    }

    @CordaSerializable
    data class IdentityWithSignature(val identity: SerializedBytes<PartyAndCertificate>, val signature: DigitalSignature)
}

/**
 * Data class used only in the context of asserting the owner of the private key for the listed key wants to use it
 * to represent the named entity. This is pairs with an X.509 certificate (which asserts the signing identity says
 * the key represents the named entity), but protects against a certificate authority incorrectly claiming others'
 * keys.
 */
data class CertificateOwnershipAssertion(val x500Name: CordaX500Name,
                                         val publicKey: PublicKey)

open class SwapIdentitiesException @JvmOverloads constructor(message: String, cause: Throwable? = null)
    : FlowException(message, cause)