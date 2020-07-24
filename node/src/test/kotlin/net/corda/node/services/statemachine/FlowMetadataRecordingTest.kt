package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.context.InvocationContext
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.SchedulableState
import net.corda.core.contracts.ScheduledActivity
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowExternalAsyncOperation
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SchedulableFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.startFlow
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.minutes
import net.corda.node.services.Permissions
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.function.Supplier
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowMetadataRecordingTest {

    private val user = User("mark", "dadada", setOf(Permissions.all()))
    private val string = "I must be delivered for 4.5"
    private val someObject = SomeObject("Store me in the database please", 1234)

    @Before
    fun before() {
        MyFlow.hookAfterInitialCheckpoint = null
        MyFlow.hookAfterSuspend = null
        MyResponder.hookAfterInitialCheckpoint = null
        MyFlowWithoutParameters.hookAfterInitialCheckpoint = null
    }

    @Test(timeout = 300_000)
    fun `rpc started flows have metadata recorded`() {
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()

            var flowId: StateMachineRunId? = null
            var context: InvocationContext? = null
            var metadata: DBCheckpointStorage.DBFlowMetadata? = null
            MyFlow.hookAfterInitialCheckpoint =
                { flowIdFromHook: StateMachineRunId, contextFromHook: InvocationContext, metadataFromHook: DBCheckpointStorage.DBFlowMetadata ->
                    flowId = flowIdFromHook
                    context = contextFromHook
                    metadata = metadataFromHook
                }

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(
                    ::MyFlow,
                    nodeBHandle.nodeInfo.singleIdentity(),
                    string,
                    someObject
                ).returnValue.getOrThrow(1.minutes)
            }

            metadata!!.let {
                assertEquals(context!!.trace.invocationId.value, it.invocationId)
                assertEquals(flowId!!.uuid.toString(), it.flowId)
                assertEquals(MyFlow::class.java.name, it.flowName)
                // Should be changed when [userSuppliedIdentifier] gets filled in future changes
                assertNull(it.userSuppliedIdentifier)
                assertEquals(DBCheckpointStorage.StartReason.RPC, it.startType)
                assertEquals(
                    listOf(nodeBHandle.nodeInfo.singleIdentity(), string, someObject),
                    it.initialParameters.deserialize(context = SerializationDefaults.STORAGE_CONTEXT)
                )
                assertThat(it.launchingCordapp).contains("custom-cordapp")
                assertEquals(PLATFORM_VERSION, it.platformVersion)
                assertEquals(user.username, it.startedBy)
                assertEquals(context!!.trace.invocationId.timestamp, it.invocationInstant)
                assertTrue(it.startInstant >= it.invocationInstant)
                assertNull(it.finishInstant)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `rpc started flows have metadata recorded - no parameters`() {
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()

            var flowId: StateMachineRunId? = null
            var context: InvocationContext? = null
            var metadata: DBCheckpointStorage.DBFlowMetadata? = null
            MyFlowWithoutParameters.hookAfterInitialCheckpoint =
                { flowIdFromHook: StateMachineRunId, contextFromHook: InvocationContext, metadataFromHook: DBCheckpointStorage.DBFlowMetadata ->
                    flowId = flowIdFromHook
                    context = contextFromHook
                    metadata = metadataFromHook
                }

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(::MyFlowWithoutParameters).returnValue.getOrThrow(1.minutes)
            }

            metadata!!.let {
                assertEquals(context!!.trace.invocationId.value, it.invocationId)
                assertEquals(flowId!!.uuid.toString(), it.flowId)
                assertEquals(MyFlowWithoutParameters::class.java.name, it.flowName)
                // Should be changed when [userSuppliedIdentifier] gets filled in future changes
                assertNull(it.userSuppliedIdentifier)
                assertEquals(DBCheckpointStorage.StartReason.RPC, it.startType)
                assertEquals(
                    emptyList<Any?>(),
                    it.initialParameters.deserialize(context = SerializationDefaults.STORAGE_CONTEXT)
                )
                assertThat(it.launchingCordapp).contains("custom-cordapp")
                assertEquals(PLATFORM_VERSION, it.platformVersion)
                assertEquals(user.username, it.startedBy)
                assertEquals(context!!.trace.invocationId.timestamp, it.invocationInstant)
                assertTrue(it.startInstant >= it.invocationInstant)
                assertNull(it.finishInstant)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `rpc started flows have their arguments removed from in-memory checkpoint after zero'th checkpoint`() {
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()

            var context: InvocationContext? = null
            var metadata: DBCheckpointStorage.DBFlowMetadata? = null
            MyFlow.hookAfterInitialCheckpoint =
                { _, contextFromHook: InvocationContext, metadataFromHook: DBCheckpointStorage.DBFlowMetadata ->
                    context = contextFromHook
                    metadata = metadataFromHook
                }

            var context2: InvocationContext? = null
            var metadata2: DBCheckpointStorage.DBFlowMetadata? = null
            MyFlow.hookAfterSuspend =
                { contextFromHook: InvocationContext, metadataFromHook: DBCheckpointStorage.DBFlowMetadata ->
                    context2 = contextFromHook
                    metadata2 = metadataFromHook
                }

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(
                    ::MyFlow,
                    nodeBHandle.nodeInfo.singleIdentity(),
                    string,
                    someObject
                ).returnValue.getOrThrow(1.minutes)
            }

            assertEquals(
                listOf(nodeBHandle.nodeInfo.singleIdentity(), string, someObject),
                uncheckedCast<Any?, Array<Any?>>(context!!.arguments[1]).toList()
            )
            assertEquals(
                listOf(nodeBHandle.nodeInfo.singleIdentity(), string, someObject),
                metadata!!.initialParameters.deserialize(context = SerializationDefaults.STORAGE_CONTEXT)
            )

            assertEquals(
                emptyList(),
                context2!!.arguments
            )
            assertEquals(
                listOf(nodeBHandle.nodeInfo.singleIdentity(), string, someObject),
                metadata2!!.initialParameters.deserialize(context = SerializationDefaults.STORAGE_CONTEXT)
            )
        }
    }

    @Test(timeout = 300_000)
    fun `initiated flows have metadata recorded`() {
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()

            var flowId: StateMachineRunId? = null
            var context: InvocationContext? = null
            var metadata: DBCheckpointStorage.DBFlowMetadata? = null
            MyResponder.hookAfterInitialCheckpoint =
                { flowIdFromHook: StateMachineRunId, contextFromHook: InvocationContext, metadataFromHook: DBCheckpointStorage.DBFlowMetadata ->
                    flowId = flowIdFromHook
                    context = contextFromHook
                    metadata = metadataFromHook
                }

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(
                    ::MyFlow,
                    nodeBHandle.nodeInfo.singleIdentity(),
                    string,
                    someObject
                ).returnValue.getOrThrow(1.minutes)
            }

            metadata!!.let {
                assertEquals(context!!.trace.invocationId.value, it.invocationId)
                assertEquals(flowId!!.uuid.toString(), it.flowId)
                assertEquals(MyResponder::class.java.name, it.flowName)
                assertNull(it.userSuppliedIdentifier)
                assertEquals(DBCheckpointStorage.StartReason.INITIATED, it.startType)
                assertEquals(
                    emptyList<Any?>(),
                    it.initialParameters.deserialize(context = SerializationDefaults.STORAGE_CONTEXT)
                )
                assertThat(it.launchingCordapp).contains("custom-cordapp")
                assertEquals(8, it.platformVersion)
                assertEquals(nodeAHandle.nodeInfo.singleIdentity().name.toString(), it.startedBy)
                assertEquals(context!!.trace.invocationId.timestamp, it.invocationInstant)
                assertTrue(it.startInstant >= it.invocationInstant)
                assertNull(it.finishInstant)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `service started flows have metadata recorded`() {
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()

            var flowId: StateMachineRunId? = null
            var context: InvocationContext? = null
            var metadata: DBCheckpointStorage.DBFlowMetadata? = null
            MyFlow.hookAfterInitialCheckpoint =
                { flowIdFromHook: StateMachineRunId, contextFromHook: InvocationContext, metadataFromHook: DBCheckpointStorage.DBFlowMetadata ->
                    flowId = flowIdFromHook
                    context = contextFromHook
                    metadata = metadataFromHook
                }

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(
                    ::MyServiceStartingFlow,
                    nodeBHandle.nodeInfo.singleIdentity(),
                    string,
                    someObject
                ).returnValue.getOrThrow(1.minutes)
            }

            metadata!!.let {
                assertEquals(context!!.trace.invocationId.value, it.invocationId)
                assertEquals(flowId!!.uuid.toString(), it.flowId)
                assertEquals(MyFlow::class.java.name, it.flowName)
                assertNull(it.userSuppliedIdentifier)
                assertEquals(DBCheckpointStorage.StartReason.SERVICE, it.startType)
                assertEquals(
                    emptyList<Any?>(),
                    it.initialParameters.deserialize(context = SerializationDefaults.STORAGE_CONTEXT)
                )
                assertThat(it.launchingCordapp).contains("custom-cordapp")
                assertEquals(PLATFORM_VERSION, it.platformVersion)
                assertEquals(MyService::class.java.name, it.startedBy)
                assertEquals(context!!.trace.invocationId.timestamp, it.invocationInstant)
                assertTrue(it.startInstant >= it.invocationInstant)
                assertNull(it.finishInstant)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `scheduled flows have metadata recorded`() {
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()

            val lock = Semaphore(0)

            var flowId: StateMachineRunId? = null
            var context: InvocationContext? = null
            var metadata: DBCheckpointStorage.DBFlowMetadata? = null
            MyFlow.hookAfterInitialCheckpoint =
                { flowIdFromHook: StateMachineRunId, contextFromHook: InvocationContext, metadataFromHook: DBCheckpointStorage.DBFlowMetadata ->
                    flowId = flowIdFromHook
                    context = contextFromHook
                    metadata = metadataFromHook
                    // Release the lock so the asserts can be processed
                    lock.release()
                }

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(
                    ::MyStartedScheduledFlow,
                    nodeBHandle.nodeInfo.singleIdentity(),
                    string,
                    someObject
                ).returnValue.getOrThrow(1.minutes)
            }

            // Block here until released in the hook
            lock.acquire()

            metadata!!.let {
                assertEquals(context!!.trace.invocationId.value, it.invocationId)
                assertEquals(flowId!!.uuid.toString(), it.flowId)
                assertEquals(MyFlow::class.java.name, it.flowName)
                assertNull(it.userSuppliedIdentifier)
                assertEquals(DBCheckpointStorage.StartReason.SCHEDULED, it.startType)
                assertEquals(
                    emptyList<Any?>(),
                    it.initialParameters.deserialize(context = SerializationDefaults.STORAGE_CONTEXT)
                )
                assertThat(it.launchingCordapp).contains("custom-cordapp")
                assertEquals(PLATFORM_VERSION, it.platformVersion)
                assertEquals("Scheduler", it.startedBy)
                assertEquals(context!!.trace.invocationId.timestamp, it.invocationInstant)
                assertTrue(it.startInstant >= it.invocationInstant)
                assertNull(it.finishInstant)
            }
        }
    }

    // Ignoring test since completed flows are not currently keeping their checkpoints in the database
    @Ignore
    @Test(timeout = 300_000)
    fun `flows have their finish time recorded when completed`() {
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()

            var flowId: StateMachineRunId? = null
            var metadata: DBCheckpointStorage.DBFlowMetadata? = null
            MyFlow.hookAfterInitialCheckpoint =
                { flowIdFromHook: StateMachineRunId, _, metadataFromHook: DBCheckpointStorage.DBFlowMetadata ->
                    flowId = flowIdFromHook
                    metadata = metadataFromHook
                }

            val finishTime = CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(
                    ::MyFlow,
                    nodeBHandle.nodeInfo.singleIdentity(),
                    string,
                    someObject
                ).returnValue.getOrThrow(1.minutes)
                it.proxy.startFlow(
                    ::GetFlowFinishTimeFlow,
                    flowId!!
                ).returnValue.getOrThrow(1.minutes)
            }

            metadata!!.let {
                assertNull(it.finishInstant)
                assertNotNull(finishTime)
                assertTrue(finishTime!! >= it.startInstant)
            }
        }
    }

    @InitiatingFlow
    @StartableByRPC
    @StartableByService
    @SchedulableFlow
    @Suppress("UNUSED_PARAMETER")
    class MyFlow(private val party: Party, string: String, someObject: SomeObject) :
        FlowLogic<Unit>() {

        companion object {
            var hookAfterInitialCheckpoint: ((
                flowId: StateMachineRunId,
                context: InvocationContext,
                metadata: DBCheckpointStorage.DBFlowMetadata
            ) -> Unit)? = null
            var hookAfterSuspend: ((
                context: InvocationContext,
                metadata: DBCheckpointStorage.DBFlowMetadata
            ) -> Unit)? = null
        }

        @Suspendable
        override fun call() {
            hookAfterInitialCheckpoint?.let {
                it(
                    stateMachine.id,
                    stateMachine.context,
                    serviceHub.cordaService(MyService::class.java).findMetadata(stateMachine.id)
                )
            }
            initiateFlow(party).sendAndReceive<String>("Hello there")
            hookAfterSuspend?.let {
                it(
                    stateMachine.context,
                    serviceHub.cordaService(MyService::class.java).findMetadata(stateMachine.id)
                )
            }
        }
    }

    @InitiatedBy(MyFlow::class)
    class MyResponder(private val session: FlowSession) : FlowLogic<Unit>() {

        companion object {
            var hookAfterInitialCheckpoint: ((
                flowId: StateMachineRunId,
                context: InvocationContext,
                metadata: DBCheckpointStorage.DBFlowMetadata
            ) -> Unit)? = null
        }

        @Suspendable
        override fun call() {
            session.receive<String>()
            hookAfterInitialCheckpoint?.let {
                it(
                    stateMachine.id,
                    stateMachine.context,
                    serviceHub.cordaService(MyService::class.java).findMetadata(stateMachine.id)
                )
            }
            session.send("Hello there")
        }
    }

    @StartableByRPC
    class MyFlowWithoutParameters : FlowLogic<Unit>() {

        companion object {
            var hookAfterInitialCheckpoint: ((
                flowId: StateMachineRunId,
                context: InvocationContext,
                metadata: DBCheckpointStorage.DBFlowMetadata
            ) -> Unit)? = null
        }

        @Suspendable
        override fun call() {
            hookAfterInitialCheckpoint?.let {
                it(
                    stateMachine.id,
                    stateMachine.context,
                    serviceHub.cordaService(MyService::class.java).findMetadata(stateMachine.id)
                )
            }
        }
    }

    @StartableByRPC
    class MyServiceStartingFlow(private val party: Party, private val string: String, private val someObject: SomeObject) :
        FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            await(object : FlowExternalAsyncOperation<Unit> {
                override fun execute(deduplicationId: String): CompletableFuture<Unit> {
                    return serviceHub.cordaService(MyService::class.java).startFlow(party, string, someObject)
                }
            })
        }
    }

    @StartableByRPC
    class MyStartedScheduledFlow(private val party: Party, private val string: String, private val someObject: SomeObject) :
        FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first()).apply {
                addOutputState(ScheduledState(party, string, someObject, listOf(ourIdentity)))
                addCommand(DummyContract.Commands.Create(), ourIdentity.owningKey)
            }
            val stx = serviceHub.signInitialTransaction(tx)
            serviceHub.recordTransactions(stx)
        }
    }

    @StartableByRPC
    class GetFlowFinishTimeFlow(private val flowId: StateMachineRunId) : FlowLogic<Instant?>() {
        @Suspendable
        override fun call(): Instant? {
            return serviceHub.cordaService(MyService::class.java).findMetadata(flowId).finishInstant
        }
    }

    @CordaService
    class MyService(private val services: AppServiceHub) : SingletonSerializeAsToken() {

        private val executorService = Executors.newFixedThreadPool(1)

        fun findMetadata(flowId: StateMachineRunId): DBCheckpointStorage.DBFlowMetadata {
            return services.database.transaction {
                session.find(DBCheckpointStorage.DBFlowMetadata::class.java, flowId.uuid.toString())
            }
        }

        fun startFlow(party: Party, string: String, someObject: SomeObject): CompletableFuture<Unit> {
            return CompletableFuture.supplyAsync(
                Supplier<Unit> { services.startFlow(MyFlow(party, string, someObject)).returnValue.getOrThrow() },
                executorService
            )
        }
    }

    @CordaSerializable
    data class SomeObject(private val string: String, private val number: Int)

    @BelongsToContract(DummyContract::class)
    data class ScheduledState(
        val party: Party,
        val string: String,
        val someObject: SomeObject,
        override val participants: List<Party>,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
    ) : SchedulableState, LinearState {

        override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
            val logicRef = flowLogicRefFactory.create(MyFlow::class.jvmName, party, string, someObject)
            return ScheduledActivity(logicRef, Instant.now())
        }
    }
}