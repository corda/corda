package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.messaging.startFlow
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.ServiceLifecycleEvent
import net.corda.core.node.services.ServiceLifecycleEvent.BEFORE_STATE_MACHINE_START
import net.corda.core.node.services.ServiceLifecycleEvent.STATE_MACHINE_STARTED
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.InProcess
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.Before
import org.junit.Test

import kotlin.test.assertEquals

class CordaServiceLifecycleTests {

    private companion object {
        const val TEST_PHRASE = "testPhrase"

        // the number of times to register a service callback
        private var numServiceCallbacks = 0
        // the set of events a test wants to capture
        private var eventsToBeCaptured: MutableSet<ServiceLifecycleEvent> = mutableSetOf()
        // the events that were actually captured in a test
        private val eventsCaptured: MutableList<ServiceLifecycleEvent> = mutableListOf()

    }

    @Before
    fun setup() {
        numServiceCallbacks = 1
        eventsCaptured.clear()
        eventsToBeCaptured = setOf(BEFORE_STATE_MACHINE_START, STATE_MACHINE_STARTED).toMutableSet()
    }

    @Test(timeout=300_000)
	fun `corda service receives events`() {
        val result = driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()),
                notarySpecs = emptyList())) {
            val node = startNode(providedName = ALICE_NAME).getOrThrow()
            node.rpc.startFlow(::ComputeTextLengthThroughCordaService, TEST_PHRASE).returnValue.getOrThrow()
        }
        val expectedEventsAndTheOrderTheyOccurIn = listOf(BEFORE_STATE_MACHINE_START, STATE_MACHINE_STARTED)
        assertEquals(TEST_PHRASE.length, result)
        assertEquals(numServiceCallbacks * 2, eventsCaptured.size)
        assertEquals(expectedEventsAndTheOrderTheyOccurIn, eventsCaptured)
    }

    @Test(timeout=300_000)
    fun `corda service receives BEFORE_STATE_MACHINE_START before the state machine is started`() {
        testStateMachineManagerStatusWhenServiceEventOccurs(
                event = BEFORE_STATE_MACHINE_START,
                expectedResult = TestSmmStateService.STATE_MACHINE_MANAGER_WAS_NOT_STARTED
        )
    }

    @Test(timeout=300_000)
    fun `corda service receives STATE_MACHINE_STARTED after the state machine is started`() {
        testStateMachineManagerStatusWhenServiceEventOccurs(
                event = STATE_MACHINE_STARTED,
                expectedResult = TestSmmStateService.STATE_MACHINE_MANAGER_WAS_STARTED
        )
    }

    /**
     * Commonised
     */
    private fun testStateMachineManagerStatusWhenServiceEventOccurs(event: ServiceLifecycleEvent, expectedResult : Int) {
        val result = driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()),
                notarySpecs = emptyList())) {
            val node = startNode(providedName = ALICE_NAME).getOrThrow()
            if (node is InProcess) {    // assuming the node-handle is always one of these
                val svc = node.services.cordaService(TestSmmStateService::class.java)
                svc.getSmmStartedForEvent(event)
            } else {
                TestSmmStateService.STATE_MACHINE_MANAGER_UNKNOWN_STATUS
            }
        }
        assertEquals(expectedResult, result)
    }

    @StartableByRPC
    @StartableByService
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
            for (n in 1..numServiceCallbacks) {
                services.register { addEvent(it) }
            }
        }

        private fun addEvent(event: ServiceLifecycleEvent) {
            if (event in eventsToBeCaptured) {
                eventsCaptured.add(event)
            }
        }

        fun computeLength(text: String): Int {
            require(text.isNotEmpty()) { "Length must be at least 1." }
            return text.length
        }
    }

    /**
     * Service that checks the State Machine Manager state (started, not started) when service events are received.
     */
    @CordaService
    class TestSmmStateService(private val services: AppServiceHub) : SingletonSerializeAsToken() {

        companion object {
            const val STATE_MACHINE_MANAGER_UNKNOWN_STATUS = -1
            const val STATE_MACHINE_MANAGER_WAS_NOT_STARTED = 0
            const val STATE_MACHINE_MANAGER_WAS_STARTED = 1
        }

        var smmStateAtEvent = mutableMapOf<ServiceLifecycleEvent, Int>()

        init {
            services.register { addEvent(it) }
        }

        private fun addEvent(event: ServiceLifecycleEvent) {
            smmStateAtEvent[event] = checkSmmStarted()
        }

        private fun checkSmmStarted() : Int {
            // try to start a flow; success == SMM started
            try {
                services.startFlow(ComputeTextLengthThroughCordaService(TEST_PHRASE)).returnValue.getOrThrow()
                return STATE_MACHINE_MANAGER_WAS_STARTED
            } catch (ex : UninitializedPropertyAccessException) {
                return STATE_MACHINE_MANAGER_WAS_NOT_STARTED
            }
        }

        /**
         * Given an event, was the SMM started when the event was received?
         */
        fun getSmmStartedForEvent(event: ServiceLifecycleEvent) : Int = smmStateAtEvent.getOrDefault(event, STATE_MACHINE_MANAGER_UNKNOWN_STATUS)
    }
}