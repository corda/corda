/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Primitives;
import net.corda.core.identity.Party;
import net.corda.testing.core.TestConstants;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.StartedMockNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static net.corda.testing.core.TestUtils.singleIdentity;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.fail;

public class FlowsInJavaTest {
    private final MockNetwork mockNet = new MockNetwork(ImmutableList.of("net.corda.core.flows"));
    private StartedMockNode aliceNode;
    private StartedMockNode bobNode;
    private Party bob;

    @Before
    public void setUp() {
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
        Future<String> result = aliceNode.startFlow(new SendInUnwrapFlow(bob));
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
        Future<?> result = aliceNode.startFlow(flow);
        mockNet.runNetwork();
        try {
            result.get();
            fail("ExecutionException should have been thrown");
        } catch (ExecutionException e) {
            assertThat(e.getCause())
                    .hasMessageContaining("primitive")
                    .hasMessageContaining(Primitives.unwrap(receiveType).getName());
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

    @InitiatedBy(PrimitiveReceiveFlow.class)
    private static class PrimitiveSendFlow extends FlowLogic<Void> {
        public PrimitiveSendFlow(FlowSession session) {
        }

        @Suspendable
        @Override
        public Void call() throws FlowException {
            return null;
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
        public Void call() {
            FlowSession session = initiateFlow(otherParty);
            session.receive(Primitives.unwrap(receiveType));
            return null;
        }
    }
}
