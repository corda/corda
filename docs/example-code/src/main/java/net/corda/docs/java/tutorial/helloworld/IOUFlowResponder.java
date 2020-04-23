package net.corda.docs.java.tutorial.helloworld;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.*;

@SuppressWarnings("unused")
// DOCSTART 01
// Replace Responder's definition with:
@InitiatedBy(IOUFlow.class)
public class IOUFlowResponder extends FlowLogic<Void> {
    private final FlowSession otherPartySession;

    public IOUFlowResponder(FlowSession otherPartySession) {
        this.otherPartySession = otherPartySession;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        subFlow(new ReceiveFinalityFlow(otherPartySession));

        return null;
    }
}
// DOCEND 01
