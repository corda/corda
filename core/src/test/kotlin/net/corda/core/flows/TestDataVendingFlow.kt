/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.internal.FetchDataFlow
import net.corda.core.utilities.UntrustworthyData

// Flow to start data vending without sending transaction. For testing only.
class TestDataVendingFlow(otherSideSession: FlowSession) : SendStateAndRefFlow(otherSideSession, emptyList()) {
    @Suspendable
    override fun sendPayloadAndReceiveDataRequest(otherSideSession: FlowSession, payload: Any): UntrustworthyData<FetchDataFlow.Request> {
        return if (payload is List<*> && payload.isEmpty()) {
            // Hack to not send the first message.
            otherSideSession.receive()
        } else {
            super.sendPayloadAndReceiveDataRequest(this.otherSideSession, payload)
        }
    }
}