package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaRuntimeException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.core.utilities.toNonEmptySet
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.node.services.statemachine.transitions.PrematureSessionCloseException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.sql.SQLTransientConnectionException
import kotlin.test.assertEquals

class FlowSessionCloseTest {

    private val user = User("user", "pwd", setOf(Permissions.all()))

    @Test(timeout=300_000)
    fun `flow cannot close uninitialised session`() {
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()), notarySpecs = emptyList())) {
            val (nodeAHandle, nodeBHandle) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)),
                    startNode(providedName = BOB_NAME, rpcUsers = listOf(user))
            ).transpose().getOrThrow()

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                assertThatThrownBy { it.proxy.startFlow(::InitiatorFlow, nodeBHandle.nodeInfo.legalIdentities.first(), true, null, false).returnValue.getOrThrow() }
                        .isInstanceOf(CordaRuntimeException::class.java)
                        .hasMessageContaining(PrematureSessionCloseException::class.java.name)
                        .hasMessageContaining("The following session was closed before it was initialised")
            }
        }
    }

    @Test(timeout=300_000)
    fun `flow cannot access closed session`() {
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()), notarySpecs = emptyList())) {
            val (nodeAHandle, nodeBHandle) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)),
                    startNode(providedName = BOB_NAME, rpcUsers = listOf(user))
            ).transpose().getOrThrow()

            InitiatorFlow.SessionAPI.values().forEach { sessionAPI ->
                CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                    assertThatThrownBy { it.proxy.startFlow(::InitiatorFlow, nodeBHandle.nodeInfo.legalIdentities.first(), false, sessionAPI, false).returnValue.getOrThrow() }
                            .isInstanceOf(UnexpectedFlowEndException::class.java)
                            .hasMessageContaining("Tried to access ended session")
                }
            }

        }
    }

    @Test(timeout=300_000)
    fun `flow can close initialised session successfully`() {
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()), notarySpecs = emptyList())) {
            val (nodeAHandle, nodeBHandle) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)),
                    startNode(providedName = BOB_NAME, rpcUsers = listOf(user))
            ).transpose().getOrThrow()

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(::InitiatorFlow, nodeBHandle.nodeInfo.legalIdentities.first(), false, null, false).returnValue.getOrThrow()
            }
        }
    }

    @Test(timeout=300_000)
    fun `flow can close initialised session successfully even in case of failures and replays`() {
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()), notarySpecs = emptyList())) {
            val (nodeAHandle, nodeBHandle) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)),
                    startNode(providedName = BOB_NAME, rpcUsers = listOf(user))
            ).transpose().getOrThrow()

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(::InitiatorFlow, nodeBHandle.nodeInfo.legalIdentities.first(), false, null, true).returnValue.getOrThrow()
            }
        }
    }

    @Test(timeout=300_000)
    fun `flow can close multiple sessions successfully`() {
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()), notarySpecs = emptyList())) {
            val (nodeAHandle, nodeBHandle) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)),
                    startNode(providedName = BOB_NAME, rpcUsers = listOf(user))
            ).transpose().getOrThrow()

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(::InitiatorMultipleSessionsFlow, nodeBHandle.nodeInfo.legalIdentities.first()).returnValue.getOrThrow()
            }
        }
    }

    /**
     * This test ensures that when sessions are closed, the associated resources are eagerly cleaned up.
     * If sessions are not closed, then the node will crash with an out-of-memory error.
     * This can be confirmed by commenting out [FlowSession.close] operation in the invoked flow and re-run the test.
     */
    @Test(timeout=300_000)
    fun `flow looping over sessions can close them to release resources and avoid out-of-memory failures, when the other side does not finish early`() {
        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = listOf(enclosedCordapp()), notarySpecs = emptyList())) {
            val (nodeAHandle, nodeBHandle) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), maximumHeapSize = "256m"),
                    startNode(providedName = BOB_NAME, rpcUsers = listOf(user), maximumHeapSize = "256m")
            ).transpose().getOrThrow()

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(::InitiatorLoopingFlow, nodeBHandle.nodeInfo.legalIdentities.first(), true).returnValue.getOrThrow()
            }
        }
    }

    @Test(timeout=300_000)
    fun `flow looping over sessions will close sessions automatically, when the other side finishes early`() {
        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = listOf(enclosedCordapp()), notarySpecs = emptyList())) {
            val (nodeAHandle, nodeBHandle) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), maximumHeapSize = "256m"),
                    startNode(providedName = BOB_NAME, rpcUsers = listOf(user), maximumHeapSize = "256m")
            ).transpose().getOrThrow()

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(::InitiatorLoopingFlow, nodeBHandle.nodeInfo.legalIdentities.first(), false).returnValue.getOrThrow()
            }
        }
    }



    @InitiatingFlow
    @StartableByRPC
    class InitiatorFlow(val party: Party, private val prematureClose: Boolean = false,
                        private val accessClosedSessionWithApi: SessionAPI? = null,
                        private val retryClose: Boolean = false): FlowLogic<Unit>() {

        @CordaSerializable
        enum class SessionAPI {
            SEND,
            SEND_AND_RECEIVE,
            RECEIVE,
            GET_FLOW_INFO
        }

        @Suspendable
        override fun call() {
            val session = initiateFlow(party)

            if (prematureClose) {
                session.close()
            }

            session.send(retryClose)
            sleep(1.seconds)

            if (accessClosedSessionWithApi != null) {
                when(accessClosedSessionWithApi) {
                    SessionAPI.SEND -> session.send("dummy payload ")
                    SessionAPI.RECEIVE -> session.receive<String>()
                    SessionAPI.SEND_AND_RECEIVE -> session.sendAndReceive<String>("dummy payload")
                    SessionAPI.GET_FLOW_INFO -> session.getCounterpartyFlowInfo()
                }
            }
        }
    }

    @InitiatedBy(InitiatorFlow::class)
    class InitiatedFlow(private val otherSideSession: FlowSession): FlowLogic<Unit>() {

        companion object {
            var thrown = false
        }

        @Suspendable
        override fun call() {
            val retryClose = otherSideSession.receive<Boolean>()
                    .unwrap{ it }

            otherSideSession.close()

            // failing with a transient exception to force a replay of the close.
            if (retryClose) {
                if (!thrown) {
                    thrown = true
                    throw SQLTransientConnectionException("Connection is not available")
                }
            }
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class InitiatorLoopingFlow(val party: Party, val blockingCounterparty: Boolean = false): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            for (i in 1..1_000) {
                val session = initiateFlow(party)
                session.sendAndReceive<String>(blockingCounterparty ).unwrap{ assertEquals("Got it", it) }

                /**
                 * If the counterparty blocks, we need to eagerly close the session and release resources to avoid running out of memory.
                 * Otherwise, the session end messages from the other side will do that automatically.
                 */
                if (blockingCounterparty) {
                    session.close()
                }

                logger.info("Completed iteration $i")
            }
        }
    }

    @InitiatedBy(InitiatorLoopingFlow::class)
    class InitiatedLoopingFlow(private val otherSideSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val shouldBlock = otherSideSession.receive<Boolean>()
                    .unwrap{ it }
            otherSideSession.send("Got it")

            if (shouldBlock) {
                otherSideSession.receive<String>()
            }
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class InitiatorMultipleSessionsFlow(val party: Party): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            for (round in 1 .. 2) {
                val sessions = mutableListOf<FlowSession>()
                for (session_number in 1 .. 5) {
                    val session = initiateFlow(party)
                    sessions.add(session)
                    session.sendAndReceive<String>("What's up?").unwrap{ assertEquals("All good!", it) }
                }
                close(sessions.toNonEmptySet())
            }
        }
    }

    @InitiatedBy(InitiatorMultipleSessionsFlow::class)
    class InitiatedMultipleSessionsFlow(private val otherSideSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            otherSideSession.receive<String>()
                    .unwrap{ assertEquals("What's up?", it) }
            otherSideSession.send("All good!")
        }
    }

}