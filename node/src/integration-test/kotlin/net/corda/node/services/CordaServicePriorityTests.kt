package net.corda.node.services

import net.corda.core.node.AppServiceHub
import net.corda.core.node.AppServiceHub.Companion.SERVICE_PRIORITY_HIGH
import net.corda.core.node.AppServiceHub.Companion.SERVICE_PRIORITY_LOW
import net.corda.core.node.AppServiceHub.Companion.SERVICE_PRIORITY_NORMAL
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.ServiceLifecycleEvent
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Test the priorities of Corda services when distributing/handlimg service events.
 * Services register themselves with a priority - an integer whereby higher numbers == higher priority.
 * There are a few pre-defined priorities provided by Corda:
 *  SERVICE_PRIORITY_HIGH
 *  SERVICE_PRIORITY_NORMAL
 *  SERVICE_PRIORITY_LOW
 *
 * but actually the priority can be ANY integer the Corda service desires.
 */

/**
 * This base class commonizes code for the subsequent real test classes, further down.
 */
open class CordaServicePriorityTests {
    companion object {
        val eventsCaptured: MutableMap<ServiceLifecycleEvent, MutableList<String>> = mutableMapOf()
    }

    /**
     * Services loaded by the node
     */
    open class PriorityService(private val services: AppServiceHub, private val priority : Int, private val name : String) : SingletonSerializeAsToken() {
        init {
            services.register(priority = priority) { addEvent(it) }
        }

        private fun addEvent(event: ServiceLifecycleEvent) {
            eventsCaptured.getOrPut(event) {
                mutableListOf()
            }.add(name)
        }
    }

    @Before
    fun startUp() {
        eventsCaptured.clear()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()),
                notarySpecs = emptyList())) {
            startNode(providedName = ALICE_NAME).getOrThrow()
        }
    }
}

/**
 * Test the priorities of Corda services when distributing/handlimg service events where services have different priorities.
 *
 * The expectation is that service events BEFORE_STATE_MACHINE_START and STATE_MACHINE_STARTED are delivered to Corda Services
 * in priority order. That is, services registered with a higher priority value are sent the events first.
 */
class CordaServiceDifferentPriorityTests : CordaServicePriorityTests() {

    @Test(timeout=300_000)
    @Suppress("unused")
    fun `startup service events are delivered to Corda Services in priority order`() {
        // expect events to be delivered to these Corda Services in this order
        val expectedCallList = listOf("John", "Paul", "George", "Ringo")

        assertEquals(expectedCallList, eventsCaptured[ServiceLifecycleEvent.BEFORE_STATE_MACHINE_START]?.toList())
        assertEquals(expectedCallList, eventsCaptured[ServiceLifecycleEvent.STATE_MACHINE_STARTED]?.toList())
    }


    /*
        These are the services with different priorities.
     */
    @CordaService
    class John(val services: AppServiceHub) : PriorityService(services, priority = SERVICE_PRIORITY_HIGH, name = "John")

    @CordaService
    class Paul(val services: AppServiceHub) : PriorityService(services, priority = SERVICE_PRIORITY_HIGH - 1, name = "Paul")

    @CordaService
    class George(val services: AppServiceHub) : PriorityService(services, priority = SERVICE_PRIORITY_NORMAL, name = "George")

    @CordaService
    class Ringo(val services: AppServiceHub) : PriorityService(services, priority = SERVICE_PRIORITY_LOW, name = "Ringo")
}



/**
 * Test the priorities of Corda services when distributing/handlimg service events where services have the same priorities.
 *
 * The expectation is that service events BEFORE_STATE_MACHINE_START and STATE_MACHINE_STARTED are delivered to all Corda Services
 * but the order of delivery is not defined, and may differ from run to run.
 */
class CordaServiceSamePriorityTests : CordaServicePriorityTests() {

    @Test(timeout=300_000)
    @Suppress("unused")
    fun `startup service events are delivered to all equal-priority services, the ordering is not fixed`() {
        // expect the service events to be delivered to these Corda Services, but don;t care about the order.
        val expectedCallList = listOf("Pete", "Roger", "John", "Keith")

        assertEquals(expectedCallList.sorted(), eventsCaptured[ServiceLifecycleEvent.BEFORE_STATE_MACHINE_START]?.toList()?.sorted())
        assertEquals(expectedCallList.sorted(), eventsCaptured[ServiceLifecycleEvent.STATE_MACHINE_STARTED]?.toList()?.sorted())
    }


    /*
        These are the services with all the same priority.
     */
    @CordaService
    class Pete(val services: AppServiceHub) : PriorityService(services, priority = SERVICE_PRIORITY_NORMAL, name = "Pete")

    @CordaService
    class Roger(val services: AppServiceHub) : PriorityService(services, priority = SERVICE_PRIORITY_NORMAL, name = "Roger")

    @CordaService
    class John(val services: AppServiceHub) : PriorityService(services, priority = SERVICE_PRIORITY_NORMAL, name = "John")

    @CordaService
    class Keith(val services: AppServiceHub) : PriorityService(services, priority = SERVICE_PRIORITY_NORMAL, name = "Keith")
}