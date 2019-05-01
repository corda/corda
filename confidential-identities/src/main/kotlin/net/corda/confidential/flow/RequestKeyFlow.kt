package net.corda.confidential.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.service.*
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.toBase58String
import net.corda.core.utilities.unwrap
import java.util.*

class RequestKeyFlow(
        private val session: FlowSession,
        private val uuid: UUID) : FlowLogic<SignedPublicKey>() {

    companion object {
        object REQUESTING_KEY : ProgressTracker.Step("Generating a public key")
        object VERIFYING_KEY : ProgressTracker.Step("Verifying counterparty's signature")
        object KEY_VERIFIED : ProgressTracker.Step("Signature is correct")

        @JvmStatic
        fun tracker(): ProgressTracker = ProgressTracker(REQUESTING_KEY, VERIFYING_KEY, KEY_VERIFIED)
    }

    override val progressTracker = tracker()

    @Suspendable
    @Throws(FlowException::class)
    override fun call(): SignedPublicKey {
        progressTracker.currentStep = REQUESTING_KEY
        val signedKey = session.sendAndReceive<SignedPublicKey>(CreateKeyForAccount(uuid)).unwrap { it }

        // Ensure the counter party was the one that generated the key
        require(session.counterparty.owningKey == signedKey.signature.by) {
            "Expected a signature by ${session.counterparty.owningKey.toBase58String()}, but received by ${signedKey.signature.by.toBase58String()}}"
        }
        progressTracker.currentStep = VERIFYING_KEY
        validateSignature(signedKey)
        progressTracker.currentStep = KEY_VERIFIED

        val party = signedKey.publicKeyToPartyMap.values.first()
        val isRegistered = registerIdentityMapping(serviceHub, signedKey, party)
        if (!isRegistered) {
            throw FlowException("Could not generate a new key for $party as the key is already registered or registered to a different party.")
        }
        return signedKey
    }
}

class RequestKeyFlowHandler(private val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val request = otherSession.receive<CreateKeyForAccount>().unwrap { it }
        val signedPK = createSignedPublicKey(serviceHub, request.uuid)
        otherSession.send(signedPK)
    }
}