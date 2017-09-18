package net.corda.confidential;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

class SwapIdentitiesHandler(val otherSide: Party, val revocationEnabled: Boolean) : FlowLogic<Unit>() {
    constructor(otherSide: Party) : this(otherSide, false)
    companion object {
        object SENDING_KEY : ProgressTracker.Step("Sending key")
    }

    override val progressTracker: ProgressTracker = ProgressTracker(SENDING_KEY)

    @Suspendable
    override fun call() {
        val revocationEnabled = false
        progressTracker.currentStep = SENDING_KEY
        val legalIdentityAnonymous = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, revocationEnabled)
        sendAndReceive<PartyAndCertificate>(otherSide, legalIdentityAnonymous).unwrap { confidentialIdentity ->
            SwapIdentitiesFlow.validateAndRegisterIdentity(serviceHub.identityService, otherSide, confidentialIdentity)
        }
    }
}