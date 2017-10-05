package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.chooseIdentity
import net.corda.testing.node.network
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.reflect.KClass

class PrimitivesInFlowsTests {
    private val primitives: Map<KClass<out Any>, Any> = mapOf(
            Double::class to 15.0,
            Boolean::class to true,
            Float::class to 3.0F,
            Character::class to 'A',
            Byte::class to 1.toByte(),
            Int::class to 230024,
            Long::class to 999L,
            Short::class to 5.toShort()
    )

    @Test
    fun `receive primitives from flows`() {
        for ((type, value) in primitives) {
            network(3) { nodes, _ ->
                val initiator = nodes[0]
                val responder = nodes[1]
                responder.registerInitiatedFlow(AskValue::class, { session -> Answer(session, value) })

                val flow = initiator.services.startFlow(AskValue(responder.info.chooseIdentity(), type.java))
                runNetwork()
                val result = flow.resultFuture.getOrThrow()

                assertThat(result).isEqualTo(value)
            }
        }
    }

    @InitiatingFlow
    class AskValue<out R : Any>(private val responder: Party, private val type: Class<R>) : FlowLogic<R>() {
        @Suspendable
        override fun call(): R {
            val session = initiateFlow(responder)
            val data = session.receive(type)
            return data.unwrap { it }
        }
    }
}