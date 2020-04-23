package net.corda.docs.kotlin

// DOCSTART 1
import net.corda.core.identity.CordaX500Name
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp.Companion.findCordapp
import org.junit.After
import org.junit.Before

class MockNetworkTestsTutorial {

    private val mockNet = MockNetwork(MockNetworkParameters(listOf(findCordapp("com.mycordapp.package"))))

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }
// DOCEND 1

// DOCSTART 2
    private lateinit var nodeA: StartedMockNode
    private lateinit var nodeB: StartedMockNode

    @Before
    fun setUp() {
        nodeA = mockNet.createNode()
        // We can optionally give the node a name.
        nodeB = mockNet.createNode(CordaX500Name("Bank B", "London", "GB"))
    }
// DOCEND 2
}
