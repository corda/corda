package net.corda.finance.compat

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.finance.flows.CashException
import net.corda.node.services.Permissions.Companion.all
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.ClassRule
import org.junit.Test

class CashExceptionSerialisationTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(*listOf(ALICE_NAME, BOB_NAME, DUMMY_BANK_A_NAME, DUMMY_NOTARY_NAME)
                .map { it.toDatabaseSchemaName() }.toTypedArray())
    }

    @Test
    fun `cash exception with a cause can be serialised with AMQP`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode(rpcUsers = listOf(User("mark", "dadada", setOf(all())))).getOrThrow()
            val action = { node.rpc.startFlow(::CashExceptionThrowingFlow).returnValue.getOrThrow() }
            assertThatThrownBy(action).isInstanceOfSatisfying(CashException::class.java) { thrown ->
                assertThat(thrown).hasNoCause()
                assertThat(thrown.stackTrace).isEmpty()
            }
        }
    }
}

@StartableByRPC
class CashExceptionThrowingFlow : FlowLogic<Unit>() {
    override fun call() {
        throw CashException("BOOM!", IllegalStateException("Nope dude!"))
    }
}