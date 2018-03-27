package net.corda.client.rpc

import co.paralleluniverse.fibers.Suspendable
import com.esotericsoftware.kryo.KryoException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.annotations.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test

class BlacklistKotlinClosureTest {
    companion object {
        const val EVIL: Long = 666
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
        driver(DriverParameters(startNodesInProcess = true)) {
            val rpc = startNode(providedName = ALICE_NAME).getOrThrow().rpc
            val packet = Packet { EVIL }
            assertThatExceptionOfType(KryoException::class.java)
                    .isThrownBy { rpc.startFlow(::FlowC, packet) }
                    .withMessageContaining("is not annotated or on the whitelist, so cannot be used in serialization")
        }
    }
}