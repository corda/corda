package net.corda.finance.workflows

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.finance.flows.CashException
import net.corda.node.services.Permissions.Companion.all
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class CashExceptionSerialisationTest {
    @Test
    fun `cash exception with a cause can be serialised with AMQP`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode(rpcUsers = listOf(User("mark", "dadada", setOf(all())))).getOrThrow()
            val action = { node.rpc.startFlow(CashExceptionSerialisationTest::CashExceptionThrowingFlow).returnValue.getOrThrow() }
            assertThatThrownBy(action).isInstanceOfSatisfying(CashException::class.java) { thrown ->
                assertThat(thrown.stackTrace).isEmpty()
            }
        }
    }

    @StartableByRPC
    class CashExceptionThrowingFlow : FlowLogic<Unit>() {
        override fun call(): Unit = throw CashException("BOOM!", IllegalStateException("Nope dude!"))
    }
}
