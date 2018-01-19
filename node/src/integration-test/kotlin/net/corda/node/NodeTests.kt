package net.corda.node

import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.testing.DUMMY_BANK_A_NAME
import net.corda.testing.driver.driver
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private fun checkQuasarAgent() {
    if (!(ManagementFactory.getRuntimeMXBean().inputArguments.any { it.contains("quasar") })) {
        throw IllegalStateException("No quasar agent")
    }
}

val latch = CountDownLatch(1)

@CordaService
class RunOnStopTestService(private val serviceHub: ServiceHub) : SingletonSerializeAsToken() {

    companion object {
        private val log = loggerFor<RunOnStopTestService>()
    }

    init {
        serviceHub.addRunOnStop(this::shutdown)
    }

    fun shutdown() {
        log.info("shutting down")
        latch.countDown()
    }

}

class NodeTests {

    @Before
    fun before() {
        checkQuasarAgent()
    }

    @Test
    fun `should be able to register run on stop lambda`() {
        driver(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.node"), isDebug = true) {
            startNode(providedName = DUMMY_BANK_A_NAME).getOrThrow()
            // just want to fall off the end of this for the mo...
        }
        Assert.assertTrue("Timed out waiting for AbstractNode to invoke the test service shutdown callback",latch.await(30, TimeUnit.SECONDS))
    }

}