package net.corda.confidential.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.service.SignedPublicKey
import net.corda.confidential.service.createSignedPublicKey
import net.corda.core.CordaInternal
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.DigitalSignature
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.VisibleForTesting
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.node.services.api.IdentityServiceInternal
import java.security.SignatureException

class RequestKeyFlow
constructor(private val sessions: Set<FlowSession>, private val anonymousParty: AbstractParty, override val progressTracker: ProgressTracker) : FlowLogic<SignedPublicKey>() {

    @JvmOverloads
    constructor(sessions: Set<FlowSession>, otherParty: AbstractParty) : this(sessions, otherParty, tracker())

    companion object {
        object REQUESTING_KEY : ProgressTracker.Step("Generating a public key")
        object VERIFYING_KEY : ProgressTracker.Step("Verifying counterparty's signature")
        object KEY_VERIFIED : ProgressTracker.Step("Signature is correct")

        @JvmStatic
        fun tracker(): ProgressTracker = ProgressTracker(REQUESTING_KEY, VERIFYING_KEY, KEY_VERIFIED)

        @CordaInternal
        @VisibleForTesting
        internal fun validateSignature(signedKey: SignedPublicKey): SignedPublicKey {
            try {
                signedKey.signature.verify(signedKey.publicKeyToPartyMap.serialize().hash.bytes)
            } catch (ex: SignatureException) {
                throw SignatureException("The signature does not match the expected.", ex)
            }
            return signedKey
        }
    }

    @Suspendable
    @Throws(FlowException::class)
    override fun call(): SignedPublicKey {
        // TODO not sure how to get around this so we can return the SignedPublicKey
        var signedKey = SignedPublicKey(emptyMap(), DigitalSignature.WithKey(serviceHub.keyManagementService.freshKey(), ByteArray(1)))

        val party = serviceHub.identityService.wellKnownPartyFromAnonymous(anonymousParty)
        progressTracker.currentStep = REQUESTING_KEY
        sessions.forEach { session ->
            session.sendAndReceive<SignedPublicKey>(NewKeyRequest()).unwrap {
                progressTracker.currentStep = VERIFYING_KEY
                signedKey = validateSignature(it)
                progressTracker.currentStep = KEY_VERIFIED

                val isRegistered = (serviceHub.identityService as IdentityServiceInternal).registerIdentityMapping(party!!, signedKey.publicKeyToPartyMap.filter { identityName ->
                    identityName.value.name == party.name
                }.keys.first())
                if (!isRegistered) {
                    throw FlowException("Could not generate a new key for $party as the key is already registered or registered to a different party.")
                }
            }
        }
        return signedKey
    }
}

class RequestKeyFlowHandler(private val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        otherSession.receive<NewKeyRequest>().unwrap { it }
        val signedPK = createSignedPublicKey(serviceHub, UniqueIdentifier().id)
        otherSession.send(signedPK)
    }
}

@CordaSerializable
class NewKeyRequest