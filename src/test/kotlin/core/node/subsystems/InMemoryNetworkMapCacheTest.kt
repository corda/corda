package core.node.subsystems

import core.testing.MockNetwork
import org.junit.Before
import org.junit.Test

class InMemoryNetworkMapCacheTest {
    lateinit var network: MockNetwork

    @Before
    fun setup() {
        network = MockNetwork()
    }

    @Test
    fun registerWithNetwork() {
        val (n0, n1) = network.createTwoNodes()

        val future = n1.services.networkMapCache.addMapService(n1.smm, n1.net, n0.info, false, null)
        network.runNetwork()
        future.get()
    }
}
