package net.corda.node

import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeUnloadHandlerTests {
    companion object {
        val latch = CountDownLatch(1)
    }

    @Test
    fun `should be able to register run on stop lambda`() {
        driver(DriverParameters(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.node"), notarySpecs = emptyList())) {
            startNode(providedName = DUMMY_BANK_A_NAME).getOrThrow()
            // just want to fall off the end of this for the mo...
        }
        assertTrue("Timed out waiting for AbstractNode to invoke the test service shutdown callback", latch.await(30, TimeUnit.SECONDS))
    }

    @Suppress("unused")
    @CordaService
    class RunOnStopTestService(serviceHub: ServiceHub) : SingletonSerializeAsToken() {
        companion object {
            private val log = contextLogger()
        }

        init {
            serviceHub.registerUnloadHandler(this::shutdown)
        }

        private fun shutdown() {
            log.info("shutting down")
            latch.countDown()
        }
    }
}
