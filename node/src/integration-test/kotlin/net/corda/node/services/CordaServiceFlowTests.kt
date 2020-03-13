package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.messaging.startFlow
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.getOrThrow
import net.corda.node.OutOfProcessSecurityRule
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test

@Suppress("FunctionName")
class CordaServiceFlowTests {
    companion object {
        @ClassRule
        @JvmField
        val security = OutOfProcessSecurityRule()
    }

    @Test(timeout=300_000)
	fun `corda service can start a flow and wait for it`() {
        driver(DriverParameters(
            startNodesInProcess = false,
            systemProperties = security.systemProperties,
            cordappsForAllNodes = listOf(enclosedCordapp())
        )) {
            val node = startNode().getOrThrow()
            val text = "191ejodaimadc8i"

            val length = node.rpc.startFlow(::ComputeTextLengthThroughCordaService, text).returnValue.getOrThrow()

            assertThat(length).isEqualTo(text.length)
        }
    }

    @StartableByRPC
    class ComputeTextLengthThroughCordaService(private val text: String) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {
            val service = serviceHub.cordaService(TextLengthComputingService::class.java)
            return service.computeLength(text)
        }
    }

    @StartableByService
    class ActuallyComputeTextLength(private val text: String) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {
            return text.length
        }
    }

    @CordaService
    class TextLengthComputingService(private val services: AppServiceHub) : SingletonSerializeAsToken() {
        fun computeLength(text: String): Int {
            // Just to check this works with Quasar.
            require(text.isNotEmpty()) { "Length must be at least 1." }
            return services.startFlow(ActuallyComputeTextLength(text)).returnValue.toCompletableFuture().getOrThrow()
        }
    }
}