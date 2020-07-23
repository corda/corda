package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaRuntimeException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StateMachineRunId
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.Party
import net.corda.core.internal.IdempotentFlow
import net.corda.core.messaging.StateMachineTransactionMapping
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.node.services.Permissions
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.ReloadFlowFromCheckpointException
import net.corda.node.services.statemachine.StateTransitionException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlowReloadAfterCheckpointTest {

    private companion object {
        val cordapps = listOf(enclosedCordapp())
    }

    @Test(timeout = 300_000)
    fun `flow will reload from its checkpoint after suspending when reloadCheckpointAfterSuspend is true`() {
        val reloadCounts = mutableMapOf<StateMachineRunId, Int>()
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = { id ->
            reloadCounts.compute(id) { _, value -> value?.plus(1) ?: 1 }
        }
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = cordapps)) {

            val nodeAHandle = startNode(
                providedName = ALICE_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()
            val nodeBHandle = startNode(
                providedName = BOB_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()

            val handle = nodeAHandle.rpc.startFlow(::ReloadFromCheckpointFlow, nodeBHandle.nodeInfo.singleIdentity(), false, false, false)
            val flowStartedByAlice = handle.id
            handle.returnValue.getOrThrow()
            assertEquals(5, reloadCounts[flowStartedByAlice])
            assertEquals(6, reloadCounts[ReloadFromCheckpointResponder.flowId])
        }
    }

    @Test(timeout = 300_000)
    fun `flow will not reload from its checkpoint after suspending when reloadCheckpointAfterSuspend is false`() {
        val reloadCounts = mutableMapOf<StateMachineRunId, Int>()
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = { id ->
            reloadCounts.compute(id) { _, value -> value?.plus(1) ?: 1 }
        }
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = cordapps)) {

            val nodeAHandle = startNode(
                providedName = ALICE_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to false)
            ).getOrThrow()
            val nodeBHandle = startNode(
                providedName = BOB_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to false)
            ).getOrThrow()

            val handle = nodeAHandle.rpc.startFlow(::ReloadFromCheckpointFlow, nodeBHandle.nodeInfo.singleIdentity(), false, false, false)
            val flowStartedByAlice = handle.id
            handle.returnValue.getOrThrow()
            assertEquals(null, reloadCounts[flowStartedByAlice])
            assertEquals(null, reloadCounts[ReloadFromCheckpointResponder.flowId])
        }
    }

    @Test(timeout = 300_000)
    fun `flow will reload from its checkpoint after suspending when reloadCheckpointAfterSuspend is true and throw an error for failed deserialization`() {
        val reloadCounts = mutableMapOf<StateMachineRunId, Int>()
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = { id ->
            reloadCounts.compute(id) { _, value -> value?.plus(1) ?: 1 }
        }
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = cordapps)) {

            val nodeAHandle = startNode(
                providedName = ALICE_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()
            val nodeBHandle = startNode(
                providedName = BOB_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()

            val handle = nodeAHandle.rpc.startFlow(::ReloadFromCheckpointFlow, nodeBHandle.nodeInfo.singleIdentity(), true, false, false)
            val flowStartedByAlice = handle.id
            Assertions.assertThatExceptionOfType(StateTransitionException::class.java).isThrownBy { handle.returnValue.getOrThrow() }
                .withCauseExactlyInstanceOf(ReloadFlowFromCheckpointException::class.java)
                .withRootCauseExactlyInstanceOf(CordaRuntimeException::class.java)
                .withMessageContaining(
                    "Could not reload flow from checkpoint. This is likely due to a discrepancy " +
                            "between the serialization and deserialization of an object in the flow's checkpoint"
                )
            assertEquals(4, reloadCounts[flowStartedByAlice])
            assertEquals(4, reloadCounts[ReloadFromCheckpointResponder.flowId])
        }
    }

    @Test(timeout = 300_000)
    fun `flow will reload from a previous checkpoint after calling suspending function and skipping the persisting the checkpoint when reloadCheckpointAfterSuspend is true`() {
        val reloadCounts = mutableMapOf<StateMachineRunId, Int>()
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = { id ->
            reloadCounts.compute(id) { _, value -> value?.plus(1) ?: 1 }
        }
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = cordapps)) {

            val nodeAHandle = startNode(
                providedName = ALICE_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()
            val nodeBHandle = startNode(
                providedName = BOB_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()

            val handle = nodeAHandle.rpc.startFlow(::ReloadFromCheckpointFlow, nodeBHandle.nodeInfo.singleIdentity(), false, false, true)
            val flowStartedByAlice = handle.id
            handle.returnValue.getOrThrow()
            assertEquals(5, reloadCounts[flowStartedByAlice])
            assertEquals(6, reloadCounts[ReloadFromCheckpointResponder.flowId])
        }
    }

    @Test(timeout = 300_000)
    fun `counterparty flow will reload from its checkpoint after suspending when reloadCheckpointAfterSuspend is true and throw an error for failed deserialization to other nodes`() {
        val reloadCounts = mutableMapOf<StateMachineRunId, Int>()
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = { id ->
            reloadCounts.compute(id) { _, value -> value?.plus(1) ?: 1 }
        }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = cordapps)) {

            val nodeAHandle = startNode(
                providedName = ALICE_NAME,
                rpcUsers = listOf(user),
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()
            val nodeBHandle = startNode(
                providedName = BOB_NAME,
                rpcUsers = listOf(user),
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()

            val handle = nodeAHandle.rpc.startFlow(::ReloadFromCheckpointFlow, nodeBHandle.nodeInfo.singleIdentity(), false, true, false)
            val flowStartedByAlice = handle.id
            assertFailsWith<UnexpectedFlowEndException> { handle.returnValue.getOrThrow() }
            assertEquals(4, reloadCounts[flowStartedByAlice])
            assertEquals(4, reloadCounts[ReloadFromCheckpointResponder.flowId])
        }
    }

    @Test(timeout = 300_000)
    fun `idempotent flow will reload from initial checkpoint after calling a suspending function when reloadCheckpointAfterSuspend is true`() {
        var reloadCount = 0
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = { _ -> reloadCount += 1 }
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = cordapps)) {

            val nodeAHandle = startNode(
                providedName = ALICE_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()

            nodeAHandle.rpc.startFlow(::MyIdempotentFlow, false).returnValue.getOrThrow()
            assertEquals(5, reloadCount)
        }
    }

    @Test(timeout = 300_000)
    fun `idempotent flow will reload from initial checkpoint after calling a suspending function when reloadCheckpointAfterSuspend is true but can't throw deserialization error from objects in the call function`() {
        var reloadCount = 0
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = { _ -> reloadCount += 1 }
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = cordapps)) {

            val nodeAHandle = startNode(
                providedName = ALICE_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()

            nodeAHandle.rpc.startFlow(::MyIdempotentFlow, false).returnValue.getOrThrow()
            assertEquals(5, reloadCount)
        }
    }

    @Test(timeout = 300_000)
    fun `flow continues reloading from checkpoints after node restart when reloadCheckpointAfterSuspend is true`() {
        var reloadCount = 0
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = { _ -> reloadCount += 1 }
        driver(
            DriverParameters(
                inMemoryDB = false,
                startNodesInProcess = true,
                notarySpecs = emptyList(),
                cordappsForAllNodes = cordapps
            )
        ) {

            val nodeAHandle = startNode(
                providedName = ALICE_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()

            nodeAHandle.rpc.startFlow(::MyRestartingFlow)
            Thread.sleep(10.seconds.toMillis())

            nodeAHandle.stop()

            startNode(
                providedName = ALICE_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()

            Thread.sleep(20.seconds.toMillis())

            assertEquals(5, reloadCount)
        }
    }

    @Test(timeout = 300_000)
    fun `idempotent flow continues reloading from checkpoints after node restart when reloadCheckpointAfterSuspend is true`() {
        var reloadCount = 0
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = { _ -> reloadCount += 1 }
        driver(
            DriverParameters(
                inMemoryDB = false,
                startNodesInProcess = true,
                notarySpecs = emptyList(),
                cordappsForAllNodes = cordapps
            )
        ) {

            val nodeAHandle = startNode(
                providedName = ALICE_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()

            nodeAHandle.rpc.startFlow(::IdempotentRestartingFlow)
            Thread.sleep(10.seconds.toMillis())

            nodeAHandle.stop()

            startNode(
                providedName = ALICE_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()

            Thread.sleep(20.seconds.toMillis())

            // restarts completely from the beginning and forgets the in-memory reload count therefore
            // it reloads an extra 2 times for checkpoints it had already reloaded before the node shutdown
            assertEquals(7, reloadCount)
        }
    }

    @Test(timeout = 300_000)
    fun `more complicated flow will reload from its checkpoint after suspending when reloadCheckpointAfterSuspend is true`() {
        val reloadCounts = mutableMapOf<StateMachineRunId, Int>()
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = { id ->
            reloadCounts.compute(id) { _, value -> value?.plus(1) ?: 1 }
        }
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = FINANCE_CORDAPPS)) {

            val nodeAHandle = startNode(
                providedName = ALICE_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()
            val nodeBHandle = startNode(
                providedName = BOB_NAME,
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()

            val handle = nodeAHandle.rpc.startFlow(
                ::CashIssueAndPaymentFlow,
                500.DOLLARS,
                OpaqueBytes.of(0x01),
                nodeBHandle.nodeInfo.singleIdentity(),
                false,
                defaultNotaryIdentity
            )
            val flowStartedByAlice = handle.id
            handle.returnValue.getOrThrow(30.seconds)
            val flowStartedByBob = nodeBHandle.rpc.stateMachineRecordedTransactionMappingSnapshot()
                .map(StateMachineTransactionMapping::stateMachineRunId)
                .toSet()
                .single()
            Thread.sleep(10.seconds.toMillis())
            assertEquals(7, reloadCounts[flowStartedByAlice])
            assertEquals(6, reloadCounts[flowStartedByBob])
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class ReloadFromCheckpointFlow(
        private val party: Party,
        private val shouldHaveDeserializationError: Boolean,
        private val counterPartyHasDeserializationError: Boolean,
        private val skipCheckpoints: Boolean
    ) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val session = initiateFlow(party)
            session.send(counterPartyHasDeserializationError, skipCheckpoints)
            logger.info("completed the send")
            val s = session.receive(String::class.java, skipCheckpoints).unwrap { it }
            logger.info("received your message = $s")
            sleep(1.seconds, skipCheckpoints)
            val map = if (shouldHaveDeserializationError) {
                BrokenMap(mutableMapOf("i dont want" to "this to work"))
            } else {
                mapOf("i dont want" to "this to work")
            }
            session.sendAndReceive<String>("hey I made it this far")
        }
    }

    @InitiatedBy(ReloadFromCheckpointFlow::class)
    class ReloadFromCheckpointResponder(private val session: FlowSession) : FlowLogic<Unit>() {

        companion object {
            var flowId: StateMachineRunId? = null
        }

        @Suspendable
        override fun call() {
            flowId = runId
            val counterPartyHasDeserializationError = session.receive<Boolean>().unwrap { it }
            logger.info("completed the receive = $counterPartyHasDeserializationError")
            session.send("hello there 12312311")
            logger.info("completed the send 2")
            sleep(1.seconds)
            val map = if (counterPartyHasDeserializationError) {
                BrokenMap(mutableMapOf("i dont want" to "this to work"))
            } else {
                mapOf("i dont want" to "this to work")
            }
            session.receive<String>().unwrap { it }
            session.send("sending back a message")
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class MyIdempotentFlow(private val shouldHaveDeserializationError: Boolean) : FlowLogic<Unit>(), IdempotentFlow {

        @Suspendable
        override fun call() {
            sleep(1.seconds)
            sleep(1.seconds)
            val map = if (shouldHaveDeserializationError) {
                BrokenMap(mutableMapOf("i dont want" to "this to work"))
            } else {
                mapOf("i dont want" to "this to work")
            }
            sleep(1.seconds)
            sleep(1.seconds)
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class MyRestartingFlow : FlowLogic<Unit>() {

        companion object {
            var thrown = false
        }

        @Suspendable
        override fun call() {
            sleep(1.seconds)
            sleep(1.seconds)
            if (!thrown) {
                thrown = true
                logger.info("throwing exception")
                throw HospitalizeFlowException("i want to try again")
            }
            sleep(1.seconds)
            sleep(1.seconds)
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class IdempotentRestartingFlow : FlowLogic<Unit>(), IdempotentFlow {

        companion object {
            var thrown = false
        }

        @Suspendable
        override fun call() {
            sleep(1.seconds)
            sleep(1.seconds)
            if (!thrown) {
                thrown = true
                throw HospitalizeFlowException("i want to try again")
            }
            sleep(1.seconds)
            sleep(1.seconds)
        }
    }
}