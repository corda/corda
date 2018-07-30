package net.corda.testing.node;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.utilities.UntrustworthyData;
import net.corda.node.internal.InitiatedFlowFactory;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.Future;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.instanceOf;

/**
 * Java version of test based on the example given as an answer to this SO question:
 *
 * https://stackoverflow.com/questions/48166626/how-can-an-acceptor-flow-in-corda-be-isolated-for-unit-testing/
 *
 * but using the `registerFlowFactory` method implemented in response to https://r3-cev.atlassian.net/browse/CORDA-916
 */
public class TestResponseFlowInIsolationInJava {

    // An InitiatedFlowFactory that initiates a Responder, given a FlowSession.
    private static final InitiatedFlowFactory.CorDapp<Responder> FLOW_FACTORY = new InitiatedFlowFactory.CorDapp<>(
            0,
            "",
            Responder::new
    );

    private final MockNetwork network = new MockNetwork(singletonList("com.template"));
    private final StartedMockNode a = network.createNode();
    private final StartedMockNode b = network.createNode();

    @After
    public void tearDown() {
        network.stopNodes();
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void test() throws Exception {
        // This method returns the Responder flow object used by node B.
        Future<Responder> initiatedResponderFlowFuture = b.registerResponderFlow(
                // We tell node B to respond to BadInitiator with Responder.
                // We want to observe the Responder flow object to check for errors.
                BadInitiator.class, FLOW_FACTORY, Responder.class);

        // We run the BadInitiator flow on node A.
        BadInitiator flow = new BadInitiator(b.getInfo().getLegalIdentities().get(0));
        CordaFuture<Void> future = a.startFlow(flow);
        network.runNetwork();
        future.get();

        // We check that the invocation of the Responder flow object has caused an ExecutionException.
        Responder initiatedResponderFlow = initiatedResponderFlowFuture.get();
        CordaFuture initiatedResponderFlowResultFuture = initiatedResponderFlow.getStateMachine().getResultFuture();
        exception.expectCause(instanceOf(FlowException.class));
        exception.expectMessage("String did not contain the expected message.");
        initiatedResponderFlowResultFuture.get();
    }

    // This is the real implementation of Initiator.
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<Void> {
        private Party counterparty;

        public Initiator(Party counterparty) {
            this.counterparty = counterparty;
        }

        @Suspendable
        @Override public Void call() {
            FlowSession session = initiateFlow(counterparty);
            session.send("goodString");
            return null;
        }
    }

    // This is the response flow that we want to isolate for testing.
    @InitiatedBy(Initiator.class)
    public static class Responder extends FlowLogic<Void> {
        private final FlowSession counterpartySession;

        Responder(FlowSession counterpartySession) {
            this.counterpartySession = counterpartySession;
        }

        @Suspendable
        @Override
        public Void call() throws FlowException {
            UntrustworthyData<String> packet = counterpartySession.receive(String.class);
            String string = packet.unwrap(data -> data);
            if (!string.equals("goodString")) {
                throw new FlowException("String did not contain the expected message.");
            }
            return null;
        }
    }

    @InitiatingFlow
    public static final class BadInitiator extends FlowLogic<Void> {
        private final Party counterparty;

        BadInitiator(Party counterparty) {
            this.counterparty = counterparty;
        }

        @Suspendable
        @Override public Void call() {
            FlowSession session = initiateFlow(counterparty);
            session.send("badString");
            return null;
        }
    }
}
