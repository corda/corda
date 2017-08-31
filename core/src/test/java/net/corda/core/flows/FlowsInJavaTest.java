package net.corda.core.flows;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.identity.Party;
import net.corda.testing.node.MockNetwork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class FlowsInJavaTest {

    private final MockNetwork mockNet = new MockNetwork();
    private MockNetwork.MockNode node1;
    private MockNetwork.MockNode node2;

    @Before
    public void setUp() throws Exception {
        MockNetwork.BasketOfNodes someNodes = mockNet.createSomeNodes(2);
        node1 = someNodes.getPartyNodes().get(0);
        node2 = someNodes.getPartyNodes().get(1);
        mockNet.runNetwork();
        // Ensure registration was successful
        node1.getNodeReadyFuture().get();
    }

    @After
    public void cleanUp() {
        mockNet.stopNodes();
    }

    @Test
    public void suspendableActionInsideUnwrap() throws Exception {
        node2.registerInitiatedFlow(SendHelloAndThenReceive.class);
        Future<String> result = node1.getServices().startFlow(new SendInUnwrapFlow(node2.getInfo().getLegalIdentity())).getResultFuture();
        mockNet.runNetwork();
        assertThat(result.get()).isEqualTo("Hello");
    }

    @InitiatingFlow
    private static class SendInUnwrapFlow extends FlowLogic<String> {
        private final Party otherParty;

        private SendInUnwrapFlow(Party otherParty) {
            this.otherParty = otherParty;
        }

        @Suspendable
        @Override
        public String call() throws FlowException {
            return receive(String.class, otherParty).unwrap(data -> {
                send(otherParty, "Something");
                return data;
            });
        }
    }

    @InitiatedBy(SendInUnwrapFlow.class)
    private static class SendHelloAndThenReceive extends FlowLogic<String> {
        private final Party otherParty;

        private SendHelloAndThenReceive(Party otherParty) {
            this.otherParty = otherParty;
        }

        @Suspendable
        @Override
        public String call() throws FlowException {
            return sendAndReceive(String.class, otherParty, "Hello").unwrap(data -> data);
        }
    }

}
