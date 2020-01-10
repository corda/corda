package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.ServiceLifecycleObserver
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CordaServiceLifecycleTests {

    private companion object {
        const val TEST_PHRASE = "testPhrase"

        @Volatile
        private var nodeStartedReceived = false
        @Volatile
        private var nodeShuttingDownReceived = false
    }

    @Test
    fun `corda service receives events`() {
        nodeStartedReceived = false
        nodeShuttingDownReceived = false
        val result = driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()),
                notarySpecs = emptyList())) {
            val node = startNode(providedName = ALICE_NAME).getOrThrow()
            assertTrue(nodeStartedReceived)
            node.rpc.startFlow(::ComputeTextLengthThroughCordaService, TEST_PHRASE).returnValue.getOrThrow()
        }
        assertEquals(TEST_PHRASE.length, result)
        assertTrue(nodeShuttingDownReceived)
    }

    @StartableByRPC
    class ComputeTextLengthThroughCordaService(private val text: String) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {
            val service = serviceHub.cordaService(TextLengthComputingService::class.java)
            return service.computeLength(text)
        }
    }

    @CordaService
    @Suppress("unused")
    class TextLengthComputingService(private val services: AppServiceHub) : SingletonSerializeAsToken(), ServiceLifecycleObserver {

        fun computeLength(text: String): Int {
            require(text.isNotEmpty()) { "Length must be at least 1." }
            return text.length
        }

        override fun onNodeStarted() {
            nodeStartedReceived = true
        }

        override fun onNodeShuttingDown() {
            nodeShuttingDownReceived = true
        }
    }
}