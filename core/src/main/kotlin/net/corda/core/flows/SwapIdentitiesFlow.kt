package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * Flow for swapping anonymous identities with a counter-party involved in the creation of a new transaction. The anonymous
 * identities created will be stored in each party's identity store with the mapping to their known identities.
 *
 * This is to be executed within both the flows dealing with the creation of the new transaction as a sub-flow call.
 */
class SwapIdentitiesFlow(val otherSide: Party,
                         val revocationEnabled: Boolean,
                         override val progressTracker: ProgressTracker) : FlowLogic<SwapIdentitiesFlow.Result>() {
    constructor(otherSide: Party) : this(otherSide, false, tracker())

    companion object {
        object GENERATING_ID : ProgressTracker.Step("Generating anonymous identity")
        object AWAITING_ID : ProgressTracker.Step("Awaiting counter-party's anonymous identity")

        fun tracker() = ProgressTracker(GENERATING_ID, AWAITING_ID)
    }

    @Suspendable
    override fun call(): Result {
        progressTracker.currentStep = GENERATING_ID
        val us = serviceHub.keyManagementService.freshKeyAndCert(serviceHub.myInfo.legalIdentityAndCert, revocationEnabled)
        val usAnonymised = us.party.anonymise()

        val theirIdentity = if (otherSide != serviceHub.myInfo.legalIdentity) {
            progressTracker.currentStep = AWAITING_ID
            sendAndReceive<PartyAndCertificate>(otherSide, us).unwrap {
                // TODO We should assert ownership before registering?
                serviceHub.identityService.verifyAndRegisterIdentity(it)
                val themAnonymised = it.party.anonymise()
                serviceHub.identityService.assertOwnership(otherSide, themAnonymised)
                themAnonymised
            }
        } else {
            // Special case that if we're both parties, a single identity is generated
            usAnonymised
        }

        return Result(usAnonymised, theirIdentity)
    }

    @CordaSerializable
    data class Result(val ourIdentity: AnonymousParty, val theirIdentity: AnonymousParty)
}

