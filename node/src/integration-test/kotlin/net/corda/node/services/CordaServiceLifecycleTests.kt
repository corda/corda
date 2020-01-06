package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.Try
import net.corda.core.utilities.getOrThrow
import net.corda.ext.api.lifecycle.NodeLifecycleEvent
import net.corda.ext.api.lifecycle.NodeLifecycleObserver
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals

class CordaServiceLifecycleTests : IntegrationTest() {

    private companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME)

        const val TEST_PHRASE = "testPhrase"

        private val eventsCaptured: MutableList<NodeLifecycleEvent> = mutableListOf()
    }

    @Test
    fun `corda service receives events`() {
        eventsCaptured.clear()
        val result = driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()),
                notarySpecs = emptyList())) {
            val node = startNode(providedName = ALICE_NAME).getOrThrow()
            node.rpc.startFlow(::ComputeTextLengthThroughCordaService, TEST_PHRASE).returnValue.getOrThrow()
        }
        assertEquals(TEST_PHRASE.length, result)
        // BeforeStart will not be delivered as it is dispatched even before the service is added.
        assertEquals(3, eventsCaptured.size)
        assertEquals(listOf(NodeLifecycleEvent.AfterStart::class.java, NodeLifecycleEvent.BeforeStop::class.java,
                NodeLifecycleEvent.AfterStop::class.java), eventsCaptured.map { it.javaClass })
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
    class TextLengthComputingService(private val services: AppServiceHub) : SingletonSerializeAsToken(), NodeLifecycleObserver {
        fun computeLength(text: String): Int {
            require(text.isNotEmpty()) { "Length must be at least 1." }
            return text.length
        }

        override fun update(nodeLifecycleEvent: NodeLifecycleEvent): Try<String> {
            eventsCaptured.add(nodeLifecycleEvent)
            return super.update(nodeLifecycleEvent)
        }
    }
}