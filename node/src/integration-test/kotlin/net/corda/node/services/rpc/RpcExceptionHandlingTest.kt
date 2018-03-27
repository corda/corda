package net.corda.node.services.rpc

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.RPCException
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.User
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.hibernate.exception.GenericJDBCException
import org.junit.Test
import java.sql.SQLException
import java.util.concurrent.TimeUnit
import kotlin.test.fail

class RpcExceptionHandlingTest {

    private val user = User("mark", "dadada", setOf(Permissions.all()))
    private val users = listOf(user)

    @Test
    fun `rpc client handles exceptions thrown on node side`() {

        driver(DriverParameters(startNodesInProcess = true)) {

            val node = startNode(NodeParameters(rpcUsers = users)).getOrThrow()

            // TODO MS: change exception type to InternalNodeException
            assertThatCode { node.rpc.startFlow(::Flow).returnValue.getOrThrow() }.isInstanceOfSatisfying(GenericJDBCException::class.java) { exception ->

                assertThat(exception).hasNoCause()
                assertThat(exception.stackTrace).isEmpty()
            }
        }
    }

    @Test
    fun `rpc client handles exceptions thrown on counter-party side`() {

        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeA = startNode(NodeParameters(rpcUsers = users)).getOrThrow()
            val nodeB = startNode(NodeParameters(rpcUsers = users)).getOrThrow()

            // TODO MS: change exception type to InternalNodeException
            assertThatCode { nodeA.rpc.startFlow(::InitFlow, nodeB.nodeInfo.singleIdentity()).returnValue.getOrThrow() }.isInstanceOfSatisfying(UnexpectedFlowEndException::class.java) { exception ->

                assertThat(exception).hasNoCause()
                assertThat(exception.stackTrace).isEmpty()
                // TODO MS: make this stricter (check each part, if not null)
                assertThat(exception.message).doesNotContain(nodeB.nodeInfo.singleIdentity().name.x500Principal.toString())
            }
        }
    }
}

@StartableByRPC
class Flow : FlowLogic<String>() {

    @Suspendable
    override fun call(): String {

        throw GenericJDBCException("Something went wrong!", SQLException("Oops!"))
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

        val message = initiatingSession.receive<String>().unwrap { it }
        throw GenericJDBCException("Something went wrong!", SQLException("Oops!"))
    }
}