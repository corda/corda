package net.corda.behave.service.proxy

import net.corda.behave.network.Network
import net.corda.behave.node.Distribution.Companion.R3_MASTER
import net.corda.behave.node.Distribution.Type.CORDA_ENTERPRISE
import net.corda.behave.node.configuration.NotaryType
import org.junit.AfterClass
import org.junit.BeforeClass

class R3CordaNetworkTest : CordaRPCProxyClientTest() {

    /**
     * Ensure you have configured the environment correctly by running the "prepare.sh" script
     * and then use a JVM option to ensure that STAGING_ROOT points to corresponding location used in the above script.
     * eg.
     *  -ea -DSTAGING_ROOT=/home/staging
     *
     *  Use -DDISABLE_CLEANUP=true to prevent deletion of the run-time configuration directories.
     */

    companion object {
        private lateinit var network : Network

        @BeforeClass
        @JvmStatic fun setUp() {
            network = Network.new(CORDA_ENTERPRISE).addNode(distribution = R3_MASTER, name = "Notary", notaryType = NotaryType.NON_VALIDATING, withRPCProxy = true).generate()
            network.start()
            network.waitUntilRunning()
        }

        @AfterClass
        @JvmStatic fun tearDown() {
            network.stop()
        }
    }
}