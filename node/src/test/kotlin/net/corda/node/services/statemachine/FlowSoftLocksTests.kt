package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.FlowIORequest
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.core.utilities.toNonEmptySet
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.node.services.vault.NodeVaultServiceTest
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOC_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.vault.VaultFiller
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.FINANCE_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.sql.SQLTransientConnectionException
import java.util.*
import kotlin.test.assertTrue

class FlowSoftLocksTests {

    companion object {
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
    }

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: TestStartedNode
    private lateinit var notaryIdentity: Party

    @Before
    fun setUpMockNet() {
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP, FINANCE_CONTRACTS_CORDAPP)
        )
        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        notaryIdentity = mockNet.defaultNotaryIdentity
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test(timeout=300_000)
    fun `flow correctly soft locks and unlocks states - at the end keeps locked states reserved by random id`() {
        val vaultStates = fillVault(aliceNode, 10)!!.states.toList()
        val completedSuccessfully =  aliceNode.services.startFlow(SoftLocks.LockingUnlockingFlow(vaultStates, false)).resultFuture.getOrThrow(30.seconds)
        assertTrue(completedSuccessfully)
        Assert.assertEquals(5, SoftLocks.LockingUnlockingFlow.queryCashStates(QueryCriteria.SoftLockingType.UNLOCKED_ONLY, aliceNode.services.vaultService).size)
    }

    @Test(timeout=300_000)
    fun `flow correctly soft locks and unlocks states - at the end releases states reserved by random id`() {
        val vaultStates = fillVault(aliceNode, 10)!!.states.toList()
        val completedSuccessfully =  aliceNode.services.startFlow(SoftLocks.LockingUnlockingFlow(vaultStates, true)).resultFuture.getOrThrow(30.seconds)
        assertTrue(completedSuccessfully)
        Assert.assertEquals(10, SoftLocks.LockingUnlockingFlow.queryCashStates(QueryCriteria.SoftLockingType.UNLOCKED_ONLY, aliceNode.services.vaultService).size)
    }

    @Test(timeout=300_000)
    fun `flow soft locks fungible state upon creation`() {
        var lockedStates = 0
        SoftLocks.CreateFungibleStateFLow.hook = { vaultService ->
            lockedStates = vaultService.queryBy<NodeVaultServiceTest.FungibleFoo>(
                    QueryCriteria.VaultQueryCriteria(
                            softLockingCondition = QueryCriteria.SoftLockingCondition(QueryCriteria.SoftLockingType.LOCKED_ONLY)
                    )
            ).states.size
        }
        aliceNode.services.startFlow(SoftLocks.CreateFungibleStateFLow()).resultFuture.getOrThrow(30.seconds)
        Assert.assertEquals(1, lockedStates)
    }

    @Test(timeout=300_000)
    fun `when flow soft locks, then errors and retries from previous checkpoint, softLockedStates are reverted back correctly`() {
        var firstRun = true
        SoftLocks.LockingUnlockingFlow.checkpointAfterReserves = true

        SoftLocks.LockingUnlockingFlow.hookAfterReleases = {
            if (firstRun) {
                firstRun = false
                throw SQLTransientConnectionException("connection is not available")
            }
        }

        val vaultStates = fillVault(aliceNode, 10)!!.states.toList()
        val completedSuccessfully = aliceNode.services.startFlow(SoftLocks.LockingUnlockingFlow(vaultStates, true)).resultFuture.getOrThrow(30.seconds)
        assertTrue(completedSuccessfully)
    }

    private fun fillVault(node: TestStartedNode, thisManyStates: Int): Vault<Cash.State>? {
        val bankNode = mockNet.createPartyNode(BOC_NAME)
        val bank = bankNode.info.singleIdentity()
        val cashIssuer = bank.ref(1)
        return node.database.transaction {
            VaultFiller(node.services, dummyNotary, notaryIdentity, ::Random).fillWithSomeTestCash(
                    100.DOLLARS,
                    bankNode.services,
                    thisManyStates,
                    thisManyStates,
                    cashIssuer
            )
        }
    }
}

object SoftLocks {

    internal class LockingUnlockingFlow(
            private val unlockedStates: List<StateAndRef<Cash.State>>,
            private val releaseRandomId: Boolean = true
    ) : FlowLogic<Boolean>() {

        companion object {
            fun queryCashStates(softLockingType: QueryCriteria.SoftLockingType, vaultService: VaultService) =
                    vaultService.queryBy<Cash.State>(
                            QueryCriteria.VaultQueryCriteria(
                                    softLockingCondition = QueryCriteria.SoftLockingCondition(
                                            softLockingType
                                    )
                            )
                    ).states

            var checkpointAfterReserves: Boolean = false
            var hookAfterReleases: () -> Unit = {}
        }

        @Suspendable
        override fun call(): Boolean {
            val unlockedStatesSize = unlockedStates.size
            val emptySet = emptySet<StateRef>()
            val lockSetFlowId = unlockedStates.subList(0, unlockedStatesSize / 2).map { it.ref }.toNonEmptySet()
            val lockSetRandomId = unlockedStates.subList(unlockedStatesSize / 2, unlockedStatesSize).map { it.ref }.toNonEmptySet()

            // lock and release with our flow Id
            serviceHub.vaultService.softLockReserve(stateMachine.id.uuid, lockSetFlowId)
            Assert.assertEquals(
                    lockSetRandomId,
                    queryCashStates(QueryCriteria.SoftLockingType.UNLOCKED_ONLY, serviceHub.vaultService).map { it.ref }.toNonEmptySet()
            )
            // states locked with our flow id are held by the fiber
            Assert.assertEquals(lockSetFlowId, (stateMachine as? FlowStateMachineImpl<*>)!!.softLockedStates)
            serviceHub.vaultService.softLockRelease(stateMachine.id.uuid, lockSetFlowId)
            Assert.assertEquals(
                    lockSetFlowId + lockSetRandomId,
                    queryCashStates(QueryCriteria.SoftLockingType.UNLOCKED_ONLY, serviceHub.vaultService).map { it.ref }.toNonEmptySet()
            )
            Assert.assertEquals(emptySet, (stateMachine as? FlowStateMachineImpl<*>)!!.softLockedStates)

            // lock and release with a random Id
            val randomUUID = UUID.randomUUID()
            serviceHub.vaultService.softLockReserve(randomUUID, lockSetRandomId)
            Assert.assertEquals(
                    lockSetFlowId,
                    queryCashStates(QueryCriteria.SoftLockingType.UNLOCKED_ONLY, serviceHub.vaultService).map { it.ref }.toNonEmptySet()
            )
            // states locked with a random Id are NOT held by the fiber
            Assert.assertEquals(emptySet, (stateMachine as? FlowStateMachineImpl<*>)!!.softLockedStates) // in memory locked states held by flow
            serviceHub.vaultService.softLockRelease(randomUUID, lockSetRandomId)
            Assert.assertEquals(
                    lockSetFlowId + lockSetRandomId,
                    queryCashStates(QueryCriteria.SoftLockingType.UNLOCKED_ONLY, serviceHub.vaultService).map { it.ref }.toNonEmptySet()
            )
            Assert.assertEquals(emptySet, (stateMachine as? FlowStateMachineImpl<*>)!!.softLockedStates)

            // lock with our flow Id, lock with random Id and then unlock passing in only flow Id
            serviceHub.vaultService.softLockReserve(stateMachine.id.uuid, lockSetFlowId)
            serviceHub.vaultService.softLockReserve(randomUUID, lockSetRandomId)
            if (checkpointAfterReserves) {
                stateMachine.suspend(FlowIORequest.ForceCheckpoint, false)
            }
            // only states locked with our flow id are held by the fiber
            Assert.assertEquals(lockSetFlowId, (stateMachine as? FlowStateMachineImpl<*>)!!.softLockedStates)
            Assert.assertEquals(
                    lockSetFlowId + lockSetRandomId,
                    queryCashStates(QueryCriteria.SoftLockingType.LOCKED_ONLY, serviceHub.vaultService).map { it.ref }.toNonEmptySet()
            )
            // the following if-block is intentionally put in the following order. We need to assure that while states are locked with the flowId,
            // and with random Ids, when unlocking with random Id it will not make use of [flowStateMachineImpl.softLockedStates]
            // i.e. a. it will successfully remove these states (otherwise it would not, because it would include in the sql IN clause states that are not under this random Id)
            //      b. it will leave [flowStateMachineImpl.softLockedStates] untouched (it will not remove any states in there since they do not belong to the random Id)
            if (releaseRandomId) {
                serviceHub.vaultService.softLockRelease(randomUUID)
            }
            serviceHub.vaultService.softLockRelease(stateMachine.id.uuid)
            Assert.assertEquals(emptySet, (stateMachine as? FlowStateMachineImpl<*>)!!.softLockedStates)
            hookAfterReleases()
            return true
        }
    }

    internal class CreateFungibleStateFLow : FlowLogic<Unit>() {

        companion object {
            var hook: ((VaultService) -> Unit)? = null
        }

        @Suspendable
        override fun call() {
            val issuer = serviceHub.myInfo.legalIdentities.first()
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val fungibleState = NodeVaultServiceTest.FungibleFoo(100.DOLLARS, listOf(issuer))
            val txCommand = Command(DummyContract.Commands.Create(), issuer.owningKey)
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(fungibleState, DummyContract.PROGRAM_ID)
                    .addCommand(txCommand)
            val signedTx = serviceHub.signInitialTransaction(txBuilder)
            serviceHub.recordTransactions(signedTx)
            hook?.invoke(serviceHub.vaultService)
        }
    }
}