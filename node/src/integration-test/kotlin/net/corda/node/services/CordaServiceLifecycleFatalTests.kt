package net.corda.node.services

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.CordaServiceCriticalFailureException
import net.corda.core.node.services.ServiceLifecycleEvent
import net.corda.core.node.services.ServiceLifecycleObserver
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.testing.common.internal.eventually
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CordaServiceLifecycleFatalTests {

    companion object {

        private val logger = contextLogger()

        // It is important to disarm throwing of the exception as unfortunately this service may be packaged to many
        // test cordaps, e.g. the one used by [net.corda.node.CordappScanningDriverTest]
        // If service remains "armed" to throw exceptions this will fail node start-up sequence.
        // The problem is caused by the fact that test from  `net.corda.node` package also hoovers all the sub-packages.
        // Since this is done as a separate process, the trigger is passed through the system property.
        private val SECRET_PROPERTY_NAME = this::class.java.enclosingClass.name + "-armed"

        // Temporaty file used as a latch between two processes
        private val tempFilePropertyName = this::class.java.enclosingClass.name + "-tmpFile"
        private val tmpFile = File.createTempFile(tempFilePropertyName, null)
        private const val readyToThrowMarker = "ReadyToThrow"
        private const val goodToThrowMarker = "GoodToThrow"

        @CordaService
        @Suppress("unused")
        class FatalService(services: AppServiceHub) : SingletonSerializeAsToken() {

            init {
                if(java.lang.Boolean.getBoolean(SECRET_PROPERTY_NAME)) {
                    services.register(observer = FailingObserver)
                }
            }
        }

        object FailingObserver : ServiceLifecycleObserver {
            override fun onServiceLifecycleEvent(event: ServiceLifecycleEvent) {
                val tmpFile = File(System.getProperty(tempFilePropertyName))
                tmpFile.appendText("\n" + readyToThrowMarker)
                eventually(duration = 30.seconds) {
                    assertEquals(goodToThrowMarker, tmpFile.readLines().last())
                }
                throw CordaServiceCriticalFailureException("controlled failure")
            }
        }
    }

    @Test(timeout=300_000)
    fun `JVM terminates on critical failure`() {
        // Scenario terminates JVM - node should be running out of process
        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = listOf(enclosedCordapp()),
                notarySpecs = emptyList(),
                systemProperties = mapOf(SECRET_PROPERTY_NAME to "true", tempFilePropertyName to tmpFile.absolutePath))) {
            val nodeHandle = startNode(providedName = ALICE_NAME).getOrThrow()

            val rpcInterface = nodeHandle.rpc
            eventually(duration = 60.seconds) {
                assertEquals(readyToThrowMarker, tmpFile.readLines().last())
            }

            rpcInterface.protocolVersion

            tmpFile.appendText("\n" + goodToThrowMarker)

            // We signalled that it is good to throw which will eventually trigger node shutdown and RPC interface no longer working.
            eventually(duration = 30.seconds) {
                assertFailsWith(Exception::class) {
                    try {
                        rpcInterface.protocolVersion
                    } catch (ex: Exception) {
                        logger.info("Thrown as expected", ex)
                        throw ex
                    }
                }
            }
        }
    }
}