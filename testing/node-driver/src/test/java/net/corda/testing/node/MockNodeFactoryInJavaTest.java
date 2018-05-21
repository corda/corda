package net.corda.testing.node;

import static java.util.Collections.emptyList;

@SuppressWarnings("unused")
public class MockNodeFactoryInJavaTest {
    /**
     * Does not need to run, only compile.
     */
    @SuppressWarnings("unused")
    private static void factoryIsEasyToPassInUsingJava() {
        //noinspection Convert2MethodRef
        new MockNetwork(emptyList());
        new MockNetwork(emptyList(), new MockNetworkParameters().withThreadPerNode(true));
        //noinspection Convert2MethodRef
        new MockNetwork(emptyList()).createNode(new MockNodeParameters());
    }
}
