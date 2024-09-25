package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import org.mockito.kotlin.*
import net.corda.core.contracts.*
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.KeyManagementService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.SoftLockingCondition
import net.corda.core.node.services.vault.QueryCriteria.SoftLockingType.LOCKED_ONLY
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.api.VaultServiceInternal
import net.corda.nodeapi.internal.cordapp.CordappLoader
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.core.singleIdentity
import net.corda.testing.flows.registerCoreFlowFactory
import net.corda.coretesting.internal.rigorousMock
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Test
import java.security.PublicKey
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertEquals

class NodePair(private val mockNet: InternalMockNetwork) {
    private class ServerLogic(private val session: FlowSession, private val running: AtomicBoolean) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            running.set(true)
            session.receive<String>().unwrap { assertEquals("ping", it) }
            session.send("pong")
        }
    }

    @InitiatingFlow
    abstract class AbstractClientLogic<out T>(nodePair: NodePair) : FlowLogic<T>() {
        private val server = nodePair.server.info.singleIdentity()
        protected abstract fun callImpl(): T
        @Suspendable
        override fun call() = callImpl().also {
            initiateFlow(server).sendAndReceive<String>("ping").unwrap { assertEquals("pong", it) }
        }
    }

    private val serverRunning = AtomicBoolean()
    val server = mockNet.createNode()
    var client = mockNet.createNode().apply {
        internals.disableDBCloseOnStop() // Otherwise the in-memory database may disappear (taking the checkpoint with it) while we reboot the client.
    }
        private set

    fun <T> communicate(clientLogic: AbstractClientLogic<T>, rebootClient: Boolean): FlowStateMachine<T> {
        server.registerCoreFlowFactory(AbstractClientLogic::class.java, ServerLogic::class.java, { ServerLogic(it, serverRunning) }, false)
        client.services.startFlow(clientLogic)
        while (!serverRunning.get()) mockNet.runNetwork(1)
        if (rebootClient) {
            client = mockNet.restartNode(client)
        }
        return uncheckedCast(client.smm.allStateMachines.single().stateMachine)
    }
}

class VaultSoftLockManagerTest {
    private val mockVault = rigorousMock<VaultServiceInternal>().also {
        doNothing().whenever(it).softLockRelease(any(), anyOrNull())
    }

    private val mockNet = InternalMockNetwork(cordappsForAllNodes = listOf(enclosedCordapp()), defaultFactory = { args ->
        object : InternalMockNetwork.MockNode(args) {
            override fun makeVaultService(keyManagementService: KeyManagementService,
                                          database: CordaPersistence,
                                          cordappLoader: CordappLoader): VaultServiceInternal {
                val node = this
                val realVault = super.makeVaultService(keyManagementService, database, cordappLoader)
                return object : SingletonSerializeAsToken(), VaultServiceInternal by realVault {
                    override fun softLockRelease(lockId: UUID, stateRefs: NonEmptySet<StateRef>?) {
                        // Should be called before flow is removed
                        assertEquals(1, node.started!!.smm.allStateMachines.size)
                        mockVault.softLockRelease(lockId, stateRefs) // No need to also call the real one for these tests.
                    }
                }
            }
        }
    })

    private val nodePair = NodePair(mockNet)

    object CommandDataImpl : CommandData

    class ClientLogic(nodePair: NodePair, val state: ContractState) : NodePair.AbstractClientLogic<List<ContractState>>(nodePair) {
        override fun callImpl(): List<ContractState> {
            val notary = serviceHub.networkParameters.notaries.first().identity
            val stx = serviceHub.signInitialTransaction(TransactionBuilder(notary).apply {
                addOutputState(state, ContractImpl::class.jvmName)
                addCommand(CommandDataImpl, ourIdentity.owningKey)
            })
            subFlow(FinalityFlow(stx, emptyList()))
            return serviceHub.vaultService.queryBy<ContractState>(VaultQueryCriteria(softLockingCondition = SoftLockingCondition(LOCKED_ONLY))).states.map {
                it.state.data
            }
        }
    }

    private abstract class ParticipantState(override val participants: List<AbstractParty>) : ContractState

    @BelongsToContract(ContractImpl::class)
    private class PlainOldState(participants: List<AbstractParty>) : ParticipantState(participants) {
        constructor(nodePair: NodePair) : this(listOf(nodePair.client.info.singleIdentity()))
    }

    @BelongsToContract(ContractImpl::class)
    private class FungibleAssetImpl(participants: List<AbstractParty>) : ParticipantState(participants), FungibleAsset<Unit> {
        constructor(nodePair: NodePair) : this(listOf(nodePair.client.info.singleIdentity()))

        override val owner get() = participants[0]
        override fun withNewOwner(newOwner: AbstractParty) = throw UnsupportedOperationException()
        override val amount get() = Amount(1, Issued(PartyAndReference(owner, OpaqueBytes.of(1)), Unit))
        override val exitKeys get() = emptyList<PublicKey>()
        override fun withNewOwnerAndAmount(newAmount: Amount<Issued<Unit>>, newOwner: AbstractParty) = throw UnsupportedOperationException()
        override fun equals(other: Any?) = other is FungibleAssetImpl && participants == other.participants
        override fun hashCode() = participants.hashCode()
    }

    class ContractImpl : Contract {
        override fun verify(tx: LedgerTransaction) {}
    }

    private fun run(expectSoftLock: Boolean, state: ContractState, checkpoint: Boolean) {
        val fsm = nodePair.communicate(ClientLogic(nodePair, state), checkpoint)
        mockNet.runNetwork()
        if (expectSoftLock) {
            assertEquals(listOf(state), fsm.resultFuture.getOrThrow())
            verify(mockVault).softLockRelease(fsm.id.uuid, null)
        } else {
            assertEquals(emptyList(), fsm.resultFuture.getOrThrow())
            // In this case we don't want softLockRelease called so that we avoid its expensive query, even after restore from checkpoint.
        }
        verifyNoMoreInteractions(mockVault)
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test(timeout=300_000)
	fun `plain old state is not soft locked`() = run(false, PlainOldState(nodePair), false)

    @Test(timeout=300_000)
	fun `plain old state is not soft locked with checkpoint`() = run(false, PlainOldState(nodePair), true)

    @Test(timeout=300_000)
	fun `fungible asset is soft locked`() = run(true, FungibleAssetImpl(nodePair), false)

    @Test(timeout=300_000)
	fun `fungible asset is soft locked with checkpoint`() = run(true, FungibleAssetImpl(nodePair), true)
}
