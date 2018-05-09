/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.vega.flows

import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.AbstractStateReplacementFlow
import net.corda.core.flows.FlowSession
import net.corda.core.flows.StateReplacementException
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.seconds
import net.corda.vega.contracts.RevisionedState

/**
 * Flow that generates an update on a mutable deal state and commits the resulting transaction reaching consensus
 * on the update between two parties.
 */
object StateRevisionFlow {
    open class Requester<T>(curStateRef: StateAndRef<RevisionedState<T>>,
                            updatedData: T) : AbstractStateReplacementFlow.Instigator<RevisionedState<T>, RevisionedState<T>, T>(curStateRef, updatedData) {
        override fun assembleTx(): AbstractStateReplacementFlow.UpgradeTx {
            val state = originalState.state.data
            val tx = state.generateRevision(originalState.state.notary, originalState, modification)
            tx.setTimeWindow(serviceHub.clock.instant(), 30.seconds)
            val privacySalt = PrivacySalt()
            tx.setPrivacySalt(privacySalt)
            val stx = serviceHub.signInitialTransaction(tx)
            return AbstractStateReplacementFlow.UpgradeTx(stx)
        }
    }

    open class Receiver<in T>(initiatingSession: FlowSession) : AbstractStateReplacementFlow.Acceptor<T>(initiatingSession) {
        override fun verifyProposal(stx: SignedTransaction, proposal: AbstractStateReplacementFlow.Proposal<T>) {
            val proposedTx = stx.tx
            val state = proposal.stateRef
            if (state !in proposedTx.inputs) {
                throw StateReplacementException("The proposed state $state is not in the proposed transaction inputs")
            }
        }
    }
}
