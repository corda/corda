/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.docs.java.tutorial.twoparty;

// DOCSTART 01
// Add these imports:
import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.ContractState;
import net.corda.core.flows.*;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;

import static net.corda.core.contracts.ContractsDSL.requireThat;

// Define IOUFlowResponder:
@InitiatedBy(IOUFlow.class)
public class IOUFlowResponder extends FlowLogic<Void> {
    private final FlowSession otherPartySession;

    public IOUFlowResponder(FlowSession otherPartySession) {
        this.otherPartySession = otherPartySession;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        class SignTxFlow extends SignTransactionFlow {
            private SignTxFlow(FlowSession otherPartySession, ProgressTracker progressTracker) {
                super(otherPartySession, progressTracker);
            }

            @Override
            protected void checkTransaction(SignedTransaction stx) {
                requireThat(require -> {
                    ContractState output = stx.getTx().getOutputs().get(0).getData();
                    require.using("This must be an IOU transaction.", output instanceof IOUState);
                    IOUState iou = (IOUState) output;
                    require.using("The IOU's value can't be too high.", iou.getValue() < 100);
                    return null;
                });
            }
        }

        subFlow(new SignTxFlow(otherPartySession, SignTransactionFlow.Companion.tracker()));

        return null;
    }
}
// DOCEND 01