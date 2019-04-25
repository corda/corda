package net.corda.confidential.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.service.SignedPublicKey
import net.corda.confidential.service.createSignedPublicKey
import net.corda.core.CordaInternal
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.internal.VisibleForTesting
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.security.SignatureException

class RequestKeyFlow
    constructor(private val sessions: Set<FlowSession>, private val otherParty: Party?, override val progressTracker: ProgressTracker) : FlowLogic<Unit>() {

    @JvmOverloads
    constructor(sessions: Set<FlowSession>, otherParty: Party) : this(sessions, otherParty, tracker())

    @JvmOverloads
    constructor(sessions: Set<FlowSession>, progressTracker: ProgressTracker = tracker()) : this(sessions, null, progressTracker)

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
                throw SignatureException("The signature does not match expected blah blah", ex)
            }
            return signedKey
        }
    }

    @Suspendable
    override fun call() {
        progressTracker.currentStep = REQUESTING_KEY
        sessions.forEach { session ->
            session.sendAndReceive<SignedPublicKey>(NewKeyRequest()).unwrap {
                progressTracker.currentStep = VERIFYING_KEY
                val signedKey = validateSignature(it)
                progressTracker.currentStep = KEY_VERIFIED

                if (signedKey != null) {
                    (serviceHub.identityService as IdentityServiceInternal).registerIdentityMapping(otherParty!!, signedKey.publicKeyToPartyMap.filter {
                        identityName -> identityName.value.name == otherParty.name
                    }.keys.first())
                }
            }
        }
    }
}

//cannot be initatedBy RequestKeyFlow as RequestKeyFlow is not a topLevel Flow (it takes sessions into it's constructor)
class RequestKeyFlowHandler(private val otherSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        otherSession.receive<NewKeyRequest>().unwrap { it }
        // Generate key
        val signedPK = createSignedPublicKey(serviceHub, UniqueIdentifier().id)
        otherSession.send(signedPK)
    }
}

@CordaSerializable
class NewKeyRequest