package net.corda.confidential

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.DigitalSignature
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AnonymousParty
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
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
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
        val ASSERTION_VERSION: ByteArray = ByteArray(1) { 1 }
        val ASSERTION_PREFIX: ByteArray = "Assertion of identity ownership ".toByteArray(Charset.forName("US-ASCII"))
        val ASSERTION_POSTFIX: ByteArray = "Identity assertion ends".toByteArray(Charset.forName("US-ASCII"))

        fun tracker() = ProgressTracker(AWAITING_KEY)
        /**
         * Generate the data blob the confidential identity's key holder signs to indicate they want to represent the
         * subject named in the X.509 certificate.
         */
        fun buildDataToSign(confidentialIdentity: PartyAndCertificate): ByteArray {
            // We build a blob with fixed header/footer to make it harder to trick a system into signing one of these
            // inappropriately. The blob contains the core of a certificate signing request, however we avoid using
            // actual certificate signing requests as these could theoretically be resubmitted to a signing service.
            val cert = confidentialIdentity.certificate.toX509CertHolder()
            val certReqInfo = CertificationRequestInfo(cert.subject, cert.subjectPublicKeyInfo, DERSet())
            val data = ByteArrayOutputStream(1024).use { out ->
                // We put a fixed version byte on for future expansion
                out.write(ASSERTION_VERSION)
                out.write(ASSERTION_PREFIX)
                out.write(certReqInfo.encoded)
                out.write(ASSERTION_POSTFIX)
                out.toByteArray()
            }
            return data
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
                throw SwapIdentitiesException("Signature does not match the given identity and nonce.", ex)
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

data class CertificateOwnershipAssertion(val certReqInfo: CertificationRequestInfo,
                                         val certPath: CertPath)

open class SwapIdentitiesException @JvmOverloads constructor(message: String, cause: Throwable? = null)
    : FlowException(message, cause)