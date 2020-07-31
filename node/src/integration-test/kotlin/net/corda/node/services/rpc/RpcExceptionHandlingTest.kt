package net.corda.node.services.rpc

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaRuntimeException
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.testing.core.*
import net.corda.testing.driver.*
import net.corda.testing.node.User
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.startNode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.Test
import java.sql.SQLException
import kotlin.test.assertEquals

class RpcExceptionHandlingTest {
    private val user = User("mark", "dadada", setOf(Permissions.all()))
    private val users = listOf(user)

    @Test(timeout=300_000)
	fun `rpc client receives relevant exceptions`() {
        val params = NodeParameters(rpcUsers = users)
        val clientRelevantMessage = "This is for the players!"

        fun NodeHandle.throwExceptionFromFlow() {
            rpc.startFlow(::ClientRelevantErrorFlow, clientRelevantMessage).returnValue.getOrThrow()
        }

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val devModeNode = startNode(params, BOB_NAME).getOrThrow()
            assertThatThrownBy { devModeNode.throwExceptionFromFlow() }.isInstanceOfSatisfying(CordaRuntimeException::class.java) { exception ->
                assertEquals((exception.cause as CordaRuntimeException).originalExceptionClassName, SQLException::class.qualifiedName)
                assertThat(exception.originalMessage).isEqualTo(clientRelevantMessage)
            }
        }
    }

    @Test(timeout=300_000)
	fun `rpc client receives client-relevant message`() {
        val params = NodeParameters(rpcUsers = users)
        val clientRelevantMessage = "This is for the players!"

        fun NodeHandle.throwExceptionFromFlow() {
            rpc.startFlow(::ClientRelevantErrorFlow, clientRelevantMessage).returnValue.getOrThrow()
        }

        fun assertThatThrownExceptionIsReceivedUnwrapped(node: NodeHandle) {
            assertThatThrownBy { node.throwExceptionFromFlow() }.isInstanceOfSatisfying(CordaRuntimeException::class.java) { exception ->
                assertThat(exception.originalMessage).isEqualTo(clientRelevantMessage)
            }
        }

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val (devModeNode, node) = listOf(startNode(params, BOB_NAME),
                    startNode(ALICE_NAME, devMode = false, parameters = params))
                    .transpose()
                    .getOrThrow()

            assertThatThrownExceptionIsReceivedUnwrapped(devModeNode)
            assertThatThrownExceptionIsReceivedUnwrapped(node)
        }
    }

    @Test(timeout=300_000)
	fun `FlowException is received by the RPC client`() {
        val params = NodeParameters(rpcUsers = users)
        val expectedMessage = "Flow error!"
        val expectedErrorId = 123L

        fun NodeHandle.throwExceptionFromFlow() {
            rpc.startFlow(::FlowExceptionFlow, expectedMessage, expectedErrorId).returnValue.getOrThrow()
        }

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val (devModeNode, node) = listOf(startNode(params, BOB_NAME),
                    startNode(ALICE_NAME, devMode = false, parameters = params))
                    .transpose()
                    .getOrThrow()

            assertThatThrownBy { devModeNode.throwExceptionFromFlow() }.isInstanceOfSatisfying(FlowException::class.java) { exception ->
                assertThat(exception).hasNoCause()
                assertThat(exception.stackTrace).isEmpty()
                assertThat(exception.message).isEqualTo(expectedMessage)
                assertThat(exception.errorId).isEqualTo(expectedErrorId)
            }

            assertThatThrownBy { node.throwExceptionFromFlow() }.isInstanceOfSatisfying(FlowException::class.java) { exception ->
                assertThat(exception).hasNoCause()
                assertThat(exception.stackTrace).isEmpty()
                assertThat(exception.message).isEqualTo(expectedMessage)
                assertThat(exception.errorId).isEqualTo(expectedErrorId)
            }
        }
    }

    @Test(timeout=300_000)
	fun `rpc client handles exceptions thrown on counter-party side`() {
        val params = NodeParameters(rpcUsers = users)

        fun DriverDSL.scenario(nameA: CordaX500Name, nameB: CordaX500Name, devMode: Boolean) {

            val (nodeA, nodeB) = listOf(nameA, nameB)
                    .map { startNode(it, devMode, params) }
                    .transpose()
                    .getOrThrow()

            nodeA.rpc.startFlow(::InitFlow, nodeB.nodeInfo.singleIdentity()).returnValue.getOrThrow()
        }

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = listOf(enclosedCordapp()))) {

            assertThatThrownBy { scenario(ALICE_NAME, BOB_NAME,true) }.isInstanceOfSatisfying(CordaRuntimeException::class.java) { exception ->

                assertThat(exception).hasNoCause()
                assertThat(exception.stackTrace).isEmpty()
            }

            assertThatThrownBy { scenario(
                    DUMMY_BANK_A_NAME,
                    DUMMY_BANK_B_NAME,
                    false)
            }.isInstanceOf(UnexpectedFlowEndException::class.java)
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

    @Suppress("TooGenericExceptionThrown")
    @InitiatedBy(InitFlow::class)
    class InitiatedFlow(private val initiatingSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            initiatingSession.receive<String>().unwrap { it }
            throw Exception("Something went wrong!", SQLException("Oops!"))
        }
    }

    @StartableByRPC
    class ClientRelevantErrorFlow(private val message: String) : FlowLogic<String>() {
        @Suspendable
        override fun call(): String = throw Exception(message, SQLException("Oops!"))
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


}
