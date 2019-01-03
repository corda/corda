package net.corda.docs.java;

// DOCSTART 1
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.StartedMockNode;
import org.junit.After;
import org.junit.Before;

import static java.util.Collections.singletonList;
import static net.corda.testing.node.TestCordapp.findCordapp;

public class MockNetworkTestsTutorial {

    private final MockNetwork mockNet = new MockNetwork(new MockNetworkParameters(singletonList(findCordapp("com.mycordapp.package"))));

    @After
    public void cleanUp() {
        mockNet.stopNodes();
    }
// DOCEND 1

// DOCSTART 2
    private StartedMockNode nodeA;
    private StartedMockNode nodeB;

    @Before
    public void setUp() {
        nodeA = mockNet.createNode();
        // We can optionally give the node a name.
        nodeB = mockNet.createNode(new CordaX500Name("Bank B", "London", "GB"));
    }
// DOCEND 2
}
