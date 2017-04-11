package net.corda.core.flows;

import co.paralleluniverse.fibers.*;
import net.corda.core.crypto.*;
import net.corda.testing.node.*;
import org.junit.*;

import java.util.concurrent.*;

import static org.assertj.core.api.AssertionsForClassTypes.*;

public class FlowsInJavaTest {

    private final MockNetwork net = new MockNetwork();
    private MockNetwork.MockNode node1;
    private MockNetwork.MockNode node2;

    @Before
    public void setUp() {
        MockNetwork.BasketOfNodes someNodes = net.createSomeNodes(2);
        node1 = someNodes.getPartyNodes().get(0);
        node2 = someNodes.getPartyNodes().get(1);
        net.runNetwork();
    }

    @After
    public void cleanUp() {
        net.stopNodes();
    }

    @Test
    public void suspendableActionInsideUnwrap() throws Exception {
        node2.getServices().registerFlowInitiator(SendInUnwrapFlow.class, (otherParty) -> new OtherFlow(otherParty, "Hello"));
        Future<String> result = node1.getServices().startFlow(new SendInUnwrapFlow(node2.getInfo().getLegalIdentity())).getResultFuture();
        net.runNetwork();
        assertThat(result.get()).isEqualTo("Hello");
    }

    @SuppressWarnings("unused")
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

    private static class OtherFlow extends FlowLogic<String> {
        private final Party otherParty;
        private final String payload;

        private OtherFlow(Party otherParty, String payload) {
            this.otherParty = otherParty;
            this.payload = payload;
        }

        @Suspendable
        @Override
        public String call() throws FlowException {
            return sendAndReceive(String.class, otherParty, payload).unwrap(data -> data);
        }
    }

}
