package net.corda.confidential

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.secureRandomBytes
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

class SwapIdentitiesHandler(val otherSideSession: FlowSession, val revocationEnabled: Boolean) : FlowLogic<Unit>() {
    constructor(otherSideSession: FlowSession) : this(otherSideSession, false)

    companion object {
        object SENDING_KEY : ProgressTracker.Step("Sending key")
    }

    override val progressTracker: ProgressTracker = ProgressTracker(SENDING_KEY)

    @Suspendable
    override fun call() {
        val ourNonce = secureRandomBytes(SwapIdentitiesFlow.NONCE_SIZE_BYTES)
        val theirNonce = otherSideSession.sendAndReceive<ByteArray>(ourNonce).unwrap(SwapIdentitiesFlow.NonceVerifier)
        val revocationEnabled = false
        progressTracker.currentStep = SENDING_KEY
        val legalIdentityAnonymous = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, revocationEnabled)
        val serializedIdentity = SerializedBytes<PartyAndCertificate>(legalIdentityAnonymous.serialize().bytes)
        val data = SwapIdentitiesFlow.buildDataToSign(serializedIdentity, ourNonce, theirNonce)
        val ourSig = serviceHub.keyManagementService.sign(data, legalIdentityAnonymous.owningKey)
        otherSideSession.sendAndReceive<SwapIdentitiesFlow.IdentityWithSignature>(SwapIdentitiesFlow.IdentityWithSignature(serializedIdentity, ourSig.withoutKey()))
                .unwrap { (confidentialIdentity, theirSigBytes) ->
                    SwapIdentitiesFlow.validateAndRegisterIdentity(serviceHub.identityService, otherSideSession.counterparty, confidentialIdentity, ourNonce, theirNonce, theirSigBytes)
                }
    }
}