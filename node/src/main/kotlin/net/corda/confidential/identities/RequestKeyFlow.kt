package net.corda.confidential.identities

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.identity.SignedKeyToPartyMapping
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.toBase58String
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.util.*

class RequestKeyFlow(
        private val session: FlowSession,
        private val uuid: UUID,
        private val key: PublicKey?) : FlowLogic<SignedKeyToPartyMapping>() {
    constructor(session: FlowSession, uuid: UUID) : this(session, uuid, null)
    constructor(session: FlowSession, key: PublicKey) : this(session, UniqueIdentifier().id, key)

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
    override fun call(): SignedKeyToPartyMapping {
        progressTracker.currentStep = REQUESTING_KEY
        var accountData = CreateKeyForAccount(uuid)
        if (key != null) {
           accountData = CreateKeyForAccount(key)
        }
        val signedKeyMapping = session.sendAndReceive<SignedKeyToPartyMapping>(accountData).unwrap { it }

        // Ensure the counter party was the one that generated the key
        require(session.counterparty.owningKey == signedKeyMapping.signature.by) {
            "Expected a signature by ${session.counterparty.owningKey.toBase58String()}, but received by ${signedKeyMapping.signature.by.toBase58String()}}"
        }
        progressTracker.currentStep = VERIFYING_KEY
        validateSignature(signedKeyMapping)
        progressTracker.currentStep = KEY_VERIFIED

        val isRegistered = serviceHub.identityService.registerPublicKeyToPartyMapping(signedKeyMapping)
        val party = signedKeyMapping.mapping.party
        if (!isRegistered) {
            throw FlowException("Could not generate a new key for $party as the key is already registered or registered to a different party.")
        }
        return signedKeyMapping
    }
}

class RequestKeyFlowHandler(private val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val request = otherSession.receive<CreateKeyForAccount>().unwrap { it }
        when {
            request.uuid != null -> otherSession.send(createSignedPublicKey(serviceHub, request.uuid!!))
            request.knownKey != null -> otherSession.send(createSignedPublicKeyMappingFromKnownKey(serviceHub, request.knownKey!!))
            else -> FlowException("Unable to generate a signed key mapping from the data provided.")
        }
    }
}

@InitiatingFlow
class RequestKeyFlowWrapper(private val party: Party, private val key: PublicKey): FlowLogic<SignedKeyToPartyMapping>() {
    @Suspendable
    override fun call(): SignedKeyToPartyMapping {
        return subFlow(RequestKeyFlow(initiateFlow(party), key))
    }
}

@InitiatedBy(RequestKeyFlowWrapper::class)
class RequestKeyFlowWrapperHandler(private val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        return subFlow(RequestKeyFlowHandler(otherSession))
    }
}
