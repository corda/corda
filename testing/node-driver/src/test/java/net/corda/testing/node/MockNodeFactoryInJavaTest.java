package net.corda.testing.node;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class MockNodeFactoryInJavaTest {
    private static class CustomNode extends MockNetwork.MockNode {
        private CustomNode(@NotNull MockNodeArgs args) {
            super(args);
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
