package net.corda.node.services

import net.corda.core.messaging.startFlow
import net.corda.core.node.services.ServiceLifecycleEvent
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.JavaCordaServiceLifecycle.JavaComputeTextLengthThroughCordaService
import net.corda.node.services.JavaCordaServiceLifecycle.eventsCaptured
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test
import kotlin.test.assertEquals

class JavaCordaServiceLifecycleTests {

    private companion object {
        const val TEST_PHRASE = "javaTestPhrase"
    }

    @Test(timeout=300_000)
	fun `corda service receives events`() {
        eventsCaptured.clear()
        val result = driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode(providedName = ALICE_NAME).getOrThrow()
            node.rpc.startFlow(::JavaComputeTextLengthThroughCordaService, TEST_PHRASE).returnValue.getOrThrow()
        }
        assertEquals(TEST_PHRASE.length, result)
        assertEquals(1, eventsCaptured.size)
        assertEquals(listOf(ServiceLifecycleEvent.STATE_MACHINE_STARTED), eventsCaptured)
    }
}