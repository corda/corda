/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.confidential

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
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
        val revocationEnabled = false
        progressTracker.currentStep = SENDING_KEY
        val ourConfidentialIdentity = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, revocationEnabled)
        val serializedIdentity = SerializedBytes<PartyAndCertificate>(ourConfidentialIdentity.serialize().bytes)
        val data = SwapIdentitiesFlow.buildDataToSign(ourConfidentialIdentity)
        val ourSig = serviceHub.keyManagementService.sign(data, ourConfidentialIdentity.owningKey)
        otherSideSession.sendAndReceive<SwapIdentitiesFlow.IdentityWithSignature>(SwapIdentitiesFlow.IdentityWithSignature(serializedIdentity, ourSig.withoutKey()))
                .unwrap { (theirConfidentialIdentityBytes, theirSigBytes) ->
                    val theirConfidentialIdentity = theirConfidentialIdentityBytes.deserialize()
                    SwapIdentitiesFlow.validateAndRegisterIdentity(serviceHub.identityService, otherSideSession.counterparty, theirConfidentialIdentity, theirSigBytes)
                }
    }
}