package net.corda.client.rpc

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.ClassRule
import org.junit.Test

class BlacklistKotlinClosureTest : IntegrationTest() {
    companion object {
        const val EVIL: Long = 666

        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME, DUMMY_NOTARY_NAME)
    }

    @StartableByRPC
    class FlowC(@Suppress("unused") private val data: Packet) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = Unit
    }

    @CordaSerializable
    data class Packet(val x: () -> Long)

    @Test
    fun `closure sent via RPC`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val rpc = startNode(providedName = ALICE_NAME).getOrThrow().rpc
            val packet = Packet { EVIL }
            assertThatExceptionOfType(RPCException::class.java)
                    .isThrownBy { rpc.startFlow(::FlowC, packet) }
                    .withMessageContaining("is not on the whitelist or annotated with @CordaSerializable")
        }
    }
}
