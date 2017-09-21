package net.corda.core.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.primitives.Primitives;
import net.corda.core.identity.Party;
import net.corda.node.internal.StartedNode;
import net.corda.testing.node.MockNetwork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static net.corda.testing.CoreTestUtils.chooseIdentity;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.fail;

public class FlowsInJavaTest {

    private final MockNetwork mockNet = new MockNetwork();
    private StartedNode<MockNetwork.MockNode> node1;
    private StartedNode<MockNetwork.MockNode> node2;

    @Before
    public void setUp() throws Exception {
        MockNetwork.BasketOfNodes someNodes = mockNet.createSomeNodes(2);
        node1 = someNodes.getPartyNodes().get(0);
        node2 = someNodes.getPartyNodes().get(1);
        mockNet.runNetwork();
        // Ensure registration was successful
        node1.getInternals().getNodeReadyFuture().get();
    }

    @After
    public void cleanUp() {
        mockNet.stopNodes();
    }

    @Test
    public void suspendableActionInsideUnwrap() throws Exception {
        node2.getInternals().registerInitiatedFlow(SendHelloAndThenReceive.class);
        Future<String> result = node1.getServices().startFlow(new SendInUnwrapFlow(chooseIdentity(node2.getInfo()))).getResultFuture();
        mockNet.runNetwork();
        assertThat(result.get()).isEqualTo("Hello");
    }

    @Test
    public void primitiveClassForReceiveType() throws InterruptedException {
        // Using the primitive classes causes problems with the checkpointing so we use the wrapper classes and convert
        // to the primitive class at callsite.
        for (Class<?> receiveType : Primitives.allWrapperTypes()) {
            primitiveReceiveTypeTest(receiveType);
        }
    }

    private void primitiveReceiveTypeTest(Class<?> receiveType) throws InterruptedException {
        PrimitiveReceiveFlow flow = new PrimitiveReceiveFlow(chooseIdentity(node2.getInfo()), receiveType);
        Future<?> result = node1.getServices().startFlow(flow).getResultFuture();
        mockNet.runNetwork();
        try {
            result.get();
            fail("ExecutionException should have been thrown");
        } catch (ExecutionException e) {
            assertThat(e.getCause())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("primitive")
                    .hasMessageContaining(receiveType.getName());
        }
    }

    @InitiatingFlow
    private static class SendInUnwrapFlow extends InitiatingFlowLogic<String> {
        private final Party otherParty;

        private SendInUnwrapFlow(Party otherParty) {
            this.otherParty = otherParty;
        }

        @Suspendable
        @Override
        public String call() throws FlowException {
            FlowSession session = initiateFlow(otherParty);
            return session.receive(String.class).unwrap(data -> {
                session.send("Something");
                return data;
            });
        }
    }

    @InitiatedBy(SendInUnwrapFlow.class)
    private static class SendHelloAndThenReceive extends FlowLogic<String> {
        private final FlowSession otherSide;

        private SendHelloAndThenReceive(FlowSession otherParty) {
            this.otherSide = otherParty;
        }

        @Suspendable
        @Override
        public String call() throws FlowException {
            return otherSide.sendAndReceive(String.class, "Hello").unwrap(data -> data);
        }
    }

    @InitiatingFlow
    private static class PrimitiveReceiveFlow extends InitiatingFlowLogic<Void> {
        private final Party otherParty;
        private final Class<?> receiveType;

        private PrimitiveReceiveFlow(Party otherParty, Class<?> receiveType) {
            this.otherParty = otherParty;
            this.receiveType = receiveType;
        }

        @Suspendable
        @Override
        public Void call() throws FlowException {
            FlowSession session = initiateFlow(otherParty);
            session.receive(Primitives.unwrap(receiveType));
            return null;
        }
    }
}
