package net.corda.core.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.primitives.Primitives;
import net.corda.core.identity.Party;
import net.corda.node.internal.StartedNode;
import net.corda.testing.TestConstants;
import net.corda.testing.node.MockNetwork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static java.util.Collections.emptyList;
import static net.corda.testing.CoreTestUtils.singleIdentity;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.fail;
import static net.corda.testing.node.NodeTestUtils.startFlow;

public class FlowsInJavaTest {
    private final MockNetwork mockNet = new MockNetwork(emptyList());
    private StartedNode<MockNetwork.MockNode> aliceNode;
    private StartedNode<MockNetwork.MockNode> bobNode;
    private Party bob;

    @Before
    public void setUp() throws Exception {
        aliceNode = mockNet.createPartyNode(TestConstants.ALICE_NAME);
        bobNode = mockNet.createPartyNode(TestConstants.BOB_NAME);
        bob = singleIdentity(bobNode.getInfo());
    }

    @After
    public void cleanUp() {
        mockNet.stopNodes();
    }

    @Test
    public void suspendableActionInsideUnwrap() throws Exception {
        bobNode.registerInitiatedFlow(SendHelloAndThenReceive.class);
        Future<String> result = startFlow(aliceNode.getServices(), new SendInUnwrapFlow(bob)).getResultFuture();
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
        PrimitiveReceiveFlow flow = new PrimitiveReceiveFlow(bob, receiveType);
        Future<?> result = startFlow(aliceNode.getServices(), flow).getResultFuture();
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
    private static class SendInUnwrapFlow extends FlowLogic<String> {
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
    private static class PrimitiveReceiveFlow extends FlowLogic<Void> {
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
