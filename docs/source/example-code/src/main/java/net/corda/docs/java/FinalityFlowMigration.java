package net.corda.docs.java;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

import static java.util.Collections.singletonList;

@SuppressWarnings("ALL")
public class FinalityFlowMigration {
    public static SignedTransaction dummyTransactionWithParticipant(Party party) {
        throw new UnsupportedOperationException();
    }

    // DOCSTART SimpleFlowUsingOldApi
    public static class SimpleFlowUsingOldApi extends FlowLogic<SignedTransaction> {
        private final Party counterparty;

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            SignedTransaction stx = dummyTransactionWithParticipant(counterparty);
            return subFlow(new FinalityFlow(stx));
        }
        // DOCEND SimpleFlowUsingOldApi

        public SimpleFlowUsingOldApi(Party counterparty) {
            this.counterparty = counterparty;
        }
    }

    // DOCSTART SimpleFlowUsingNewApi
    // Notice how the flow *must* now be an initiating flow even when it wasn't before.
    @InitiatingFlow
    public static class SimpleFlowUsingNewApi extends FlowLogic<SignedTransaction> {
        private final Party counterparty;

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            SignedTransaction stx = dummyTransactionWithParticipant(counterparty);
            // For each non-local participant in the transaction we must initiate a flow session with them.
            FlowSession session = initiateFlow(counterparty);
            return subFlow(new FinalityFlow(stx, session));
        }
        // DOCEND SimpleFlowUsingNewApi

        public SimpleFlowUsingNewApi(Party counterparty) {
            this.counterparty = counterparty;
        }
    }
    // DOCSTART SimpleNewResponderFlow
    // All participants will run this flow to receive and record the finalised transaction into their vault.
    @InitiatedBy(SimpleFlowUsingNewApi.class)
    public static class SimpleNewResponderFlow extends FlowLogic<Void> {
        private final FlowSession otherSide;

        @Suspendable
        @Override
        public Void call() throws FlowException {
            subFlow(new ReceiveFinalityFlow(otherSide));
            return null;
        }
        // DOCEND SimpleNewResponderFlow

        public SimpleNewResponderFlow(FlowSession otherSide) {
            this.otherSide = otherSide;
        }
    }

    // DOCSTART ExistingInitiatingFlow
    // Assuming the previous version of the flow was 1 (the default if none is specified), we increment the version number to 2
    // to allow for backwards compatibility with nodes running the old CorDapp.
    @InitiatingFlow(version = 2)
    public static class ExistingInitiatingFlow extends FlowLogic<SignedTransaction> {
        private final Party counterparty;

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            SignedTransaction partiallySignedTx = dummyTransactionWithParticipant(counterparty);
            FlowSession session = initiateFlow(counterparty);
            SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(partiallySignedTx, singletonList(session)));
            // Determine which version of the flow that other side is using.
            if (session.getCounterpartyFlowInfo().getFlowVersion() == 1) {
                // Use the old API if the other side is using the previous version of the flow.
                return subFlow(new FinalityFlow(fullySignedTx));
            } else {
                // Otherwise they're at least on version 2 and so we can send the finalised transaction on the existing session.
                return subFlow(new FinalityFlow(fullySignedTx, session));
            }
        }
        // DOCEND ExistingInitiatingFlow

        public ExistingInitiatingFlow(Party counterparty) {
            this.counterparty = counterparty;
        }
    }

    @InitiatedBy(ExistingInitiatingFlow.class)
    public static class ExistingResponderFlow extends FlowLogic<Void> {
        private final FlowSession otherSide;

        public ExistingResponderFlow(FlowSession otherSide) {
            this.otherSide = otherSide;
        }

        @Suspendable
        @Override
        public Void call() throws FlowException {
            SignedTransaction txWeJustSigned = subFlow(new SignTransactionFlow(otherSide) {
                @Suspendable
                @Override
                protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                    // Do checks here
                }
            });
            // DOCSTART ExistingResponderFlow
            if (otherSide.getCounterpartyFlowInfo().getFlowVersion() >= 2) {
                // The other side is not using the old CorDapp so call ReceiveFinalityFlow to record the finalised transaction.
                // If SignTransactionFlow is used then we can verify the tranaction we receive for recording is the same one
                // that was just signed.
                subFlow(new ReceiveFinalityFlow(otherSide, txWeJustSigned.getId()));
            } else {
                // Otherwise the other side is running the old CorDapp and so we don't need to do anything further. The node
                // will automatically record the finalised transaction using the old insecure mechanism.
            }
            // DOCEND ExistingResponderFlow
            return null;
        }
    }
}
