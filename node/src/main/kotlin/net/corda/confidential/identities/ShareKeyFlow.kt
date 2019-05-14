package net.corda.confidential.identities

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.SignedKeyToPartyMapping
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.toBase58String
import net.corda.core.utilities.unwrap
import java.util.*

class ShareKeyFlow(private val session: FlowSession, private val uuid: UUID) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val signedKey = createSignedPublicKey(serviceHub, uuid)
        session.send(signedKey)
    }
}

class ShareKeyFlowHandler(private val otherSession: FlowSession) : FlowLogic<SignedKeyToPartyMapping>() {

    companion object {
        object VERIFYING_SIGNATURE : ProgressTracker.Step("Verifying counterparty's signature")
        object SIGNATURE_VERIFIED : ProgressTracker.Step("Signature is correct")

        @JvmStatic
        fun tracker(): ProgressTracker = ProgressTracker(VERIFYING_SIGNATURE, SIGNATURE_VERIFIED)
    }

    override val progressTracker = tracker()

    @Suspendable
    @Throws(FlowException::class)
    override fun call(): SignedKeyToPartyMapping {
        val signedKey = otherSession.receive<SignedKeyToPartyMapping>().unwrap { it }
        // Ensure the counter party was the one that generated the key
        require(otherSession.counterparty.owningKey == signedKey.signature.by) {
            "Expected a signature by ${otherSession.counterparty.owningKey.toBase58String()}, but received by ${signedKey.signature.by.toBase58String()}}"
        }
        progressTracker.currentStep = VERIFYING_SIGNATURE
        validateSignature(signedKey)
        progressTracker.currentStep = SIGNATURE_VERIFIED

        val isRegistered = serviceHub.identityService.registerPublicKeyToPartyMapping(signedKey)
        val party = signedKey.mapping.party
        if (!isRegistered) {
            throw FlowException("Could not generate a new key for $party as the key is already registered or registered to a different party.")
        }
        return signedKey
    }
}