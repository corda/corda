package net.corda.node.services

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.CordaServiceCriticalFailureException
import net.corda.core.node.services.ServiceLifecycleEvent
import net.corda.core.node.services.ServiceLifecycleObserver
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.ListenProcessDeathException
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.Test
import kotlin.test.assertFailsWith

class CordaServiceLifecycleFatalTests {

    companion object {

        // It is important to disarm throwing of the exception as unfortunately this service may be packaged to many
        // test cordaps, e.g. the one used by [net.corda.node.CordappScanningDriverTest]
        // If service remains "armed" to throw exceptions this will fail node start-up sequence.
        // The problem is caused by the fact that test from  `net.corda.node` package also hoovers all the sub-packages.
        // Since this is done as a separate process, the trigger is passed through the system property.
        const val SECRET_PROPERTY_NAME = "CordaServiceLifecycleFatalTests.armed"

        @CordaService
        @Suppress("unused")
        class FatalService(services: AppServiceHub) : SingletonSerializeAsToken() {

            init {
                services.register(observer = FailingObserver)
            }

            fun computeLength(text: String): Int {
                require(text.isNotEmpty()) { "Length must be at least 1." }
                return text.length
            }
        }

        object FailingObserver : ServiceLifecycleObserver {
            override fun onServiceLifecycleEvent(event: ServiceLifecycleEvent) {
                if(java.lang.Boolean.getBoolean(SECRET_PROPERTY_NAME)) {
                    throw CordaServiceCriticalFailureException("failure")
                }
            }
        }
    }

    @Test
    fun `JVM terminates on critical failure`() {
        // Scenario terminates JVM - node should be running out of process
        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = listOf(enclosedCordapp()),
                notarySpecs = emptyList(), systemProperties = mapOf(Pair(SECRET_PROPERTY_NAME, "true")))) {
            val nodeHandle = startNode(providedName = ALICE_NAME)
            // Ensure ample time for all the stat-up lifecycle events to be processed
            Thread.sleep(2000)
            assertFailsWith(ListenProcessDeathException::class) {
                nodeHandle.getOrThrow()
            }
        }
    }
}