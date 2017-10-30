package net.corda.testing.node;

import net.corda.node.internal.StartedNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
public class MockNodeFactoryInJavaTest {
    private static class CustomNode extends MockNetwork.MockNode {
        private CustomNode(@NotNull MockNodeArgs args) {
            super(args);
        }

        @NotNull
        @Override
        public StartedNode<MockNetwork.MockNode> start() {
            return super.start();
        }

        @Nullable
        @Override
        public StartedNode<MockNetwork.MockNode> getStarted() {
            return super.getStarted();
        }
    }

    /**
     * Does not need to run, only compile.
     */
    @SuppressWarnings("unused")
    private static void factoryIsEasyToPassInUsingJava() {
        //noinspection Convert2MethodRef
        new MockNetwork(new MockNetworkParameters().setDefaultFactory(args -> new CustomNode(args)));
        new MockNetwork(new MockNetworkParameters().setDefaultFactory(CustomNode::new));
        //noinspection Convert2MethodRef
        new MockNetwork().createNode(new MockNodeParameters(), args -> new CustomNode(args));
        new MockNetwork().createNode(new MockNodeParameters(), CustomNode::new);
    }
}
