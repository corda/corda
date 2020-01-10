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
        @CordaService
        @Suppress("unused")
        class FatalService(services: AppServiceHub) : SingletonSerializeAsToken() {

            init {
                services.register(FailingObserver)
            }

            fun computeLength(text: String): Int {
                require(text.isNotEmpty()) { "Length must be at least 1." }
                return text.length
            }
        }

        object FailingObserver : ServiceLifecycleObserver {
            override fun onServiceLifecycleEvent(event: ServiceLifecycleEvent) {
                throw CordaServiceCriticalFailureException("failure")
            }
        }
    }

    @Test
    fun `JVM terminates on critical failure`() {
        // Scenario terminates JVM - node should be running out of process
        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = listOf(enclosedCordapp()),
                notarySpecs = emptyList())) {
            val nodeHandle = startNode(providedName = ALICE_NAME)
            assertFailsWith(ListenProcessDeathException::class) {
                nodeHandle.getOrThrow()
            }
        }
    }
}