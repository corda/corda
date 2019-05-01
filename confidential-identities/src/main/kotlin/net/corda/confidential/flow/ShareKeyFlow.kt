package net.corda.confidential.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.service.*
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.toBase58String
import net.corda.core.utilities.unwrap
import java.util.*

class ShareKeyFlow(private val session: FlowSession, private val uuid: UUID) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        session.sendAndReceive<UUIDReceived>(uuid)
        val signedKey = createSignedPublicKey(serviceHub, uuid)
        registerIdentityMapping(serviceHub, signedKey, signedKey.publicKeyToPartyMap.values.first())
        session.send(signedKey)
    }
}

class ShareKeyFlowHandler(private val otherSession: FlowSession) : FlowLogic<SignedPublicKey>() {

    companion object {
        object VERIFYING_KEY : ProgressTracker.Step("Verifying counterparty's signature")
        object KEY_VERIFIED : ProgressTracker.Step("Signature is correct")

        @JvmStatic
        fun tracker(): ProgressTracker = ProgressTracker(VERIFYING_KEY, KEY_VERIFIED)
    }

    override val progressTracker = tracker()

    @Suspendable
    @Throws(FlowException::class)
    override fun call(): SignedPublicKey {
        val uuid = otherSession.receive<UUID>().unwrap { it }
        otherSession.send(UUIDReceived())
        val signedKey = otherSession.sendAndReceive<SignedPublicKey>(CreateKeyForAccount(uuid)).unwrap { it }
        // Ensure the counter party was the one that generated the key
        require(otherSession.counterparty.owningKey == signedKey.signature.by) {
            "Expected a signature by ${otherSession.counterparty.owningKey.toBase58String()}, but received by ${signedKey.signature.by.toBase58String()}}"
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

@CordaSerializable
class UUIDReceived