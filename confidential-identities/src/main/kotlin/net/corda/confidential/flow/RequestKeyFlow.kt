package net.corda.confidential.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.service.SignedPublicKey
import net.corda.confidential.service.createSignedPublicKey
import net.corda.core.CordaInternal
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.identity.Party
import net.corda.core.internal.VisibleForTesting
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.node.services.api.IdentityServiceInternal
import java.security.SignatureException

class RequestKeyFlow

    constructor(
            private val sessions: Set<FlowSession>,
            private val otherParty: Party,
            override val progressTracker: ProgressTracker) : FlowLogic<Unit>() {

    companion object{
        object REQUESTING_KEY : ProgressTracker.Step("Generating a public key")
        object VERIFYING_KEY : ProgressTracker.Step("Verifying counterparty's signature")
        object KEY_VERIFIED : ProgressTracker.Step("Signature is correct")

        @JvmStatic
        fun tracker(): ProgressTracker = ProgressTracker(REQUESTING_KEY, VERIFYING_KEY, KEY_VERIFIED)

        @CordaInternal
        @VisibleForTesting
        internal fun validateSignature(signedKey: SignedPublicKey) : SignedPublicKey {
            try {
                signedKey.signature.verify(signedKey.publicKeyToPartyMap.serialize().hash.bytes)
            } catch (ex: SignatureException) {
                throw SignatureException("The signature does not match expected blah blah", ex)
            }
            return  signedKey
        }
    }

    @Suspendable
    override fun call() {
        progressTracker.currentStep = REQUESTING_KEY
        sessions.forEach { session ->
            session.receive<SignedPublicKey>().unwrap {
                subFlow(Responder(session))
                progressTracker.currentStep = VERIFYING_KEY
                val signedKey = validateSignature(it)
                progressTracker.currentStep = KEY_VERIFIED

            if (signedKey != null) {
                (serviceHub.identityService as IdentityServiceInternal).registerIdentityMapping(otherParty, signedKey.publicKeyToPartyMap.filter {
                    it.value.name == otherParty.name
                }.keys.first())
            }
            }
        }
    }
}

@InitiatedBy(RequestKeyFlow::class)
class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {

    companion object {
        object KEY_CREATED : ProgressTracker.Step("Public key has been created. Sending to the counterparty")
        @JvmStatic
        fun tracker(): ProgressTracker = ProgressTracker(KEY_CREATED)
    }

    override fun call() {
        // Generate key
        val signedPK = createSignedPublicKey(serviceHub, UniqueIdentifier().id)
        progressTracker!!.currentStep = KEY_CREATED
        otherSession.send(signedPK)
    }
}