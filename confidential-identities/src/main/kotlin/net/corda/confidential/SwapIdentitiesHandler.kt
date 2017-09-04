package net.corda.confidential

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.secureRandomBytes
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
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
        val revocationEnabled = false
        val ourNonce = secureRandomBytes(SwapIdentitiesFlow.NONCE_SIZE_BYTES)
        val theirNonce = otherSideSession.sendAndReceive<ByteArray>(ourNonce).unwrap { nonce ->
            require(nonce.size == SwapIdentitiesFlow.NONCE_SIZE_BYTES)
            nonce
        }
        progressTracker.currentStep = SENDING_KEY
        val legalIdentityAnonymous = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, revocationEnabled)
        val data = SwapIdentitiesFlow.buildDataToSign(legalIdentityAnonymous, theirNonce)
        val ourSig: DigitalSignature = serviceHub.keyManagementService.sign(data, legalIdentityAnonymous.owningKey)
        otherSideSession.sendAndReceive<SwapIdentitiesFlow.IdentityWithSignature>(SwapIdentitiesFlow.IdentityWithSignature(legalIdentityAnonymous, ourSig.bytes)).unwrap { (confidentialIdentity, theirSigBytes) ->
            val theirSig = DigitalSignature.WithKey(confidentialIdentity.owningKey, theirSigBytes)
            SwapIdentitiesFlow.validateAndRegisterIdentity(serviceHub.identityService, otherSideSession.counterparty, confidentialIdentity, ourNonce, theirSig)
        }
    }
}