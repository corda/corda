package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.ServiceLifecycleEvent
import net.corda.core.node.services.ServiceLifecycleEvent.STATE_MACHINE_STARTED
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.Test
import kotlin.test.assertEquals

class CordaServiceLifecycleTests {

    private companion object {
        const val TEST_PHRASE = "testPhrase"

        private val eventsCaptured: MutableList<ServiceLifecycleEvent> = mutableListOf()
    }

    @Test(timeout=300_000)
	fun `corda service receives events`() {
        eventsCaptured.clear()
        val result = driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()),
                notarySpecs = emptyList())) {
            val node = startNode(providedName = ALICE_NAME).getOrThrow()
            node.rpc.startFlow(::ComputeTextLengthThroughCordaService, TEST_PHRASE).returnValue.getOrThrow()
        }
        assertEquals(TEST_PHRASE.length, result)
        assertEquals(1, eventsCaptured.size)
        assertEquals(listOf(STATE_MACHINE_STARTED), eventsCaptured)
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
    class TextLengthComputingService(services: AppServiceHub) : SingletonSerializeAsToken() {

        init {
            services.register { addEvent(it) }
        }

        private fun addEvent(event: ServiceLifecycleEvent) {
            when (event) {
                STATE_MACHINE_STARTED -> {
                    eventsCaptured.add(event)
                }
                else -> {
                    eventsCaptured.add(event)
                }
            }
        }

        fun computeLength(text: String): Int {
            require(text.isNotEmpty()) { "Length must be at least 1." }
            return text.length
        }
    }
}