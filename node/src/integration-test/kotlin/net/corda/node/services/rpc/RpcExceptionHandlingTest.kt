package net.corda.node.services.rpc

import co.paralleluniverse.fibers.Suspendable
import net.corda.ClientRelevantException
import net.corda.core.CordaRuntimeException
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.nodeapi.exceptions.InternalNodeException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.User
import net.corda.testing.node.internal.startNode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.hibernate.exception.GenericJDBCException
import org.junit.ClassRule
import org.junit.Test
import java.sql.SQLException

class RpcExceptionHandlingTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(*listOf(ALICE_NAME, BOB_NAME, DUMMY_BANK_A_NAME, DUMMY_NOTARY_NAME)
                .map { it.toDatabaseSchemaName() }.toTypedArray())
    }

    private val user = User("mark", "dadada", setOf(Permissions.all()))
    private val users = listOf(user)

    @Test
    fun `rpc client receive client-relevant exceptions regardless of devMode`() {
        val params = NodeParameters(rpcUsers = users)
        val clientRelevantMessage = "This is for the players!"

        fun NodeHandle.throwExceptionFromFlow() {
            rpc.startFlow(::ClientRelevantErrorFlow, clientRelevantMessage).returnValue.getOrThrow()
        }

        fun assertThatThrownExceptionIsReceivedUnwrapped(node: NodeHandle) {
            assertThatThrownBy { node.throwExceptionFromFlow() }.isInstanceOfSatisfying(ClientRelevantException::class.java) { exception ->

                assertThat(exception).hasNoCause()
                assertThat(exception.stackTrace).isEmpty()
                assertThat(exception.message).isEqualTo(clientRelevantMessage)
            }
        }

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val devModeNode = startNode(params).getOrThrow()
            val node = startNode(ALICE_NAME, devMode = false, parameters = params).getOrThrow()

            assertThatThrownExceptionIsReceivedUnwrapped(devModeNode)
            assertThatThrownExceptionIsReceivedUnwrapped(node)
        }
    }

    @Test
    fun `FlowException is received by the RPC client only if in devMode`() {
        val params = NodeParameters(rpcUsers = users)
        val expectedMessage = "Flow error!"
        val expectedErrorId = 123L

        fun NodeHandle.throwExceptionFromFlow() {
            rpc.startFlow(::FlowExceptionFlow, expectedMessage, expectedErrorId).returnValue.getOrThrow()
        }

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val devModeNode = startNode(params).getOrThrow()
            val node = startNode(ALICE_NAME, devMode = false, parameters = params).getOrThrow()

            assertThatThrownBy { devModeNode.throwExceptionFromFlow() }.isInstanceOfSatisfying(FlowException::class.java) { exception ->

                assertThat(exception).hasNoCause()
                assertThat(exception.stackTrace).isEmpty()
                assertThat(exception.message).isEqualTo(expectedMessage)
                assertThat(exception.errorId).isEqualTo(expectedErrorId)
            }
            assertThatThrownBy { node.throwExceptionFromFlow() }.isInstanceOfSatisfying(InternalNodeException::class.java) { exception ->

                assertThat(exception).hasNoCause()
                assertThat(exception.stackTrace).isEmpty()
                assertThat(exception.message).isEqualTo(InternalNodeException.message)
                assertThat(exception.errorId).isEqualTo(expectedErrorId)
            }
        }
    }

    @Test
    fun `rpc client handles exceptions thrown on counter-party side`() {
        val params = NodeParameters(rpcUsers = users)

        fun DriverDSL.scenario(devMode: Boolean) {

            val nodeA = startNode(ALICE_NAME, devMode, params).getOrThrow()
            val nodeB = startNode(BOB_NAME, devMode, params).getOrThrow()

            nodeA.rpc.startFlow(::InitFlow, nodeB.nodeInfo.singleIdentity()).returnValue.getOrThrow()
        }

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {

            assertThatThrownBy { scenario(true) }.isInstanceOfSatisfying(CordaRuntimeException::class.java) { exception ->

                assertThat(exception).hasNoCause()
                assertThat(exception.stackTrace).isEmpty()
            }
        }
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            assertThatThrownBy { scenario(false) }.isInstanceOfSatisfying(InternalNodeException::class.java) { exception ->

                assertThat(exception).hasNoCause()
                assertThat(exception.stackTrace).isEmpty()
                assertThat(exception.message).isEqualTo(InternalNodeException.message)
            }
        }
    }
}

@StartableByRPC
@InitiatingFlow
class InitFlow(private val party: Party) : FlowLogic<String>() {
    @Suspendable
    override fun call(): String {
        val session = initiateFlow(party)
        return session.sendAndReceive<String>("hey").unwrap { it }
    }
}

@InitiatedBy(InitFlow::class)
class InitiatedFlow(private val initiatingSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        initiatingSession.receive<String>().unwrap { it }
        throw GenericJDBCException("Something went wrong!", SQLException("Oops!"))
    }
}

@StartableByRPC
class ClientRelevantErrorFlow(private val message: String) : FlowLogic<String>() {
    @Suspendable
    override fun call(): String = throw ClientRelevantException(message, SQLException("Oops!"))
}

@StartableByRPC
class FlowExceptionFlow(private val message: String, private val errorId: Long? = null) : FlowLogic<String>() {
    @Suspendable
    override fun call(): String {
        val exception = FlowException(message)
        errorId?.let { exception.originalErrorId = it }
        throw exception
    }
}