package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.FlowIORequest
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.node.services.statemachine.FlowSoftLocksTests.Companion.queryCashStates
import net.corda.node.services.vault.NodeVaultServiceTest
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOC_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.vault.VaultFiller
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.FINANCE_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.IllegalStateException
import java.sql.SQLTransientConnectionException
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowSoftLocksTests {

    companion object {
        fun queryCashStates(softLockingType: QueryCriteria.SoftLockingType, vaultService: VaultService) =
                vaultService.queryBy<Cash.State>(
                        QueryCriteria.VaultQueryCriteria(
                                softLockingCondition = QueryCriteria.SoftLockingCondition(
                                        softLockingType
                                )
                        )
                ).states.map { it.ref }.toSet()

        val EMPTY_SET = emptySet<StateRef>()
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
    fun `flow reserves fungible states with its own flow id and then manually releases them`() {
        val vaultStates = fillVault(aliceNode, 10)!!.states.map { it.ref }.toSet()
        val softLockActions = arrayOf(
            SoftLockAction(SoftLockingAction.LOCK, null, vaultStates, ExpectedSoftLocks(vaultStates, QueryCriteria.SoftLockingType.LOCKED_ONLY), expectedSoftLockedStates = vaultStates),
            SoftLockAction(SoftLockingAction.UNLOCK, null, vaultStates, ExpectedSoftLocks(vaultStates, QueryCriteria.SoftLockingType.UNLOCKED_ONLY), expectedSoftLockedStates = EMPTY_SET)
        )
        val flowCompleted = aliceNode.services.startFlow(LockingUnlockingFlow(softLockActions)).resultFuture.getOrThrow(30.seconds)
        assertTrue(flowCompleted)
        assertEquals(vaultStates, queryCashStates(QueryCriteria.SoftLockingType.UNLOCKED_ONLY, aliceNode.services.vaultService))
    }

    @Test(timeout=300_000)
    fun `flow reserves fungible states with its own flow id and by default releases them when completing`() {
        val vaultStates = fillVault(aliceNode, 10)!!.states.map { it.ref }.toSet()
        val softLockActions = arrayOf(
            SoftLockAction(SoftLockingAction.LOCK, null, vaultStates, ExpectedSoftLocks(vaultStates, QueryCriteria.SoftLockingType.LOCKED_ONLY), expectedSoftLockedStates = vaultStates)
        )
        val flowCompleted = aliceNode.services.startFlow(LockingUnlockingFlow(softLockActions)).resultFuture.getOrThrow(30.seconds)
        assertTrue(flowCompleted)
        assertEquals(vaultStates, queryCashStates(QueryCriteria.SoftLockingType.UNLOCKED_ONLY, aliceNode.services.vaultService))
    }

    @Test(timeout=300_000)
    fun `flow reserves fungible states with its own flow id and by default releases them when errors`() {
        val vaultStates = fillVault(aliceNode, 10)!!.states.map { it.ref }.toSet()
        val softLockActions = arrayOf(
            SoftLockAction(
                SoftLockingAction.LOCK,
                null,
                vaultStates,
                ExpectedSoftLocks(vaultStates, QueryCriteria.SoftLockingType.LOCKED_ONLY),
                expectedSoftLockedStates = vaultStates,
                exception = IllegalStateException("Throwing error after flow has soft locked states")
            )
        )
        assertFailsWith<IllegalStateException> {
            aliceNode.services.startFlow(LockingUnlockingFlow(softLockActions)).resultFuture.getOrThrow(30.seconds)
        }
        assertEquals(vaultStates, queryCashStates(QueryCriteria.SoftLockingType.UNLOCKED_ONLY, aliceNode.services.vaultService))
        LockingUnlockingFlow.throwOnlyOnce = true
    }

    @Test(timeout=300_000)
    fun `flow reserves fungible states with random id and then manually releases them`() {
        val randomId = UUID.randomUUID()
        val vaultStates = fillVault(aliceNode, 10)!!.states.map { it.ref }.toSet()
        val softLockActions = arrayOf(
            SoftLockAction(SoftLockingAction.LOCK, randomId, vaultStates, ExpectedSoftLocks(vaultStates, QueryCriteria.SoftLockingType.LOCKED_ONLY), expectedSoftLockedStates = EMPTY_SET),
            SoftLockAction(SoftLockingAction.UNLOCK, randomId, vaultStates, ExpectedSoftLocks(vaultStates, QueryCriteria.SoftLockingType.UNLOCKED_ONLY), expectedSoftLockedStates = EMPTY_SET)
        )
        val flowCompleted = aliceNode.services.startFlow(LockingUnlockingFlow(softLockActions)).resultFuture.getOrThrow(30.seconds)
        assertTrue(flowCompleted)
        assertEquals(vaultStates, queryCashStates(QueryCriteria.SoftLockingType.UNLOCKED_ONLY, aliceNode.services.vaultService))
    }

    @Test(timeout=300_000)
    fun `flow reserves fungible states with random id and does not release them upon completing`() {
        val randomId = UUID.randomUUID()
        val vaultStates = fillVault(aliceNode, 10)!!.states.map { it.ref }.toSet()
        val softLockActions = arrayOf(
            SoftLockAction(SoftLockingAction.LOCK, randomId, vaultStates, ExpectedSoftLocks(vaultStates, QueryCriteria.SoftLockingType.LOCKED_ONLY), expectedSoftLockedStates = EMPTY_SET)
        )
        val flowCompleted = aliceNode.services.startFlow(LockingUnlockingFlow(softLockActions)).resultFuture.getOrThrow(30.seconds)
        assertTrue(flowCompleted)
        assertEquals(vaultStates, queryCashStates(QueryCriteria.SoftLockingType.LOCKED_ONLY, aliceNode.services.vaultService))
    }

    @Test(timeout=300_000)
    fun `flow only releases by default reserved states with flow id upon completing`() {
        // lock with flow id and random id, dont manually release any. At the end, check that only flow id ones got unlocked.
        val randomId = UUID.randomUUID()
        val vaultStates = fillVault(aliceNode, 10)!!.states.map { it.ref }.toList()
        val flowIdStates = vaultStates.subList(0, vaultStates.size / 2).toSet()
        val randomIdStates = vaultStates.subList(vaultStates.size / 2, vaultStates.size).toSet()
        val softLockActions = arrayOf(
            SoftLockAction(SoftLockingAction.LOCK, null, flowIdStates, ExpectedSoftLocks(flowIdStates, QueryCriteria.SoftLockingType.LOCKED_ONLY), expectedSoftLockedStates = flowIdStates),
            SoftLockAction(SoftLockingAction.LOCK, randomId, randomIdStates, ExpectedSoftLocks(flowIdStates + randomIdStates, QueryCriteria.SoftLockingType.LOCKED_ONLY), expectedSoftLockedStates = flowIdStates)
        )
        val flowCompleted = aliceNode.services.startFlow(LockingUnlockingFlow(softLockActions)).resultFuture.getOrThrow(30.seconds)
        assertTrue(flowCompleted)
        assertEquals(flowIdStates, queryCashStates(QueryCriteria.SoftLockingType.UNLOCKED_ONLY, aliceNode.services.vaultService))
        assertEquals(randomIdStates, queryCashStates(QueryCriteria.SoftLockingType.LOCKED_ONLY, aliceNode.services.vaultService))
    }

    @Test(timeout=300_000)
    fun `flow reserves fungible states with flow id and random id, then releases the flow id ones - assert the random id ones are still locked`() {
        val randomId = UUID.randomUUID()
        val vaultStates = fillVault(aliceNode, 10)!!.states.map { it.ref }.toList()
        val flowIdStates = vaultStates.subList(0, vaultStates.size / 2).toSet()
        val randomIdStates = vaultStates.subList(vaultStates.size / 2, vaultStates.size).toSet()
        val softLockActions = arrayOf(
            SoftLockAction(SoftLockingAction.LOCK, null, flowIdStates, ExpectedSoftLocks(flowIdStates, QueryCriteria.SoftLockingType.LOCKED_ONLY), expectedSoftLockedStates = flowIdStates),
            SoftLockAction(SoftLockingAction.LOCK, randomId, randomIdStates, ExpectedSoftLocks(flowIdStates + randomIdStates, QueryCriteria.SoftLockingType.LOCKED_ONLY), expectedSoftLockedStates = flowIdStates),
            SoftLockAction(SoftLockingAction.UNLOCK, null, flowIdStates, ExpectedSoftLocks(randomIdStates, QueryCriteria.SoftLockingType.LOCKED_ONLY), expectedSoftLockedStates = EMPTY_SET)
        )
        val flowCompleted = aliceNode.services.startFlow(LockingUnlockingFlow(softLockActions)).resultFuture.getOrThrow(30.seconds)
        assertTrue(flowCompleted)
        assertEquals(flowIdStates, queryCashStates(QueryCriteria.SoftLockingType.UNLOCKED_ONLY, aliceNode.services.vaultService))
        assertEquals(randomIdStates, queryCashStates(QueryCriteria.SoftLockingType.LOCKED_ONLY, aliceNode.services.vaultService))
    }

    @Test(timeout=300_000)
    fun `flow reserves fungible states with flow id and random id, then releases the random id ones - assert the flow id ones are still locked inside the flow`() {
        val randomId = UUID.randomUUID()
        val vaultStates = fillVault(aliceNode, 10)!!.states.map { it.ref }.toList()
        val flowIdStates = vaultStates.subList(0, vaultStates.size / 2).toSet()
        val randomIdStates = vaultStates.subList(vaultStates.size / 2, vaultStates.size).toSet()
        val softLockActions = arrayOf(
            SoftLockAction(SoftLockingAction.LOCK, null, flowIdStates, ExpectedSoftLocks(flowIdStates, QueryCriteria.SoftLockingType.LOCKED_ONLY), expectedSoftLockedStates = flowIdStates),
            SoftLockAction(SoftLockingAction.LOCK, randomId, randomIdStates, ExpectedSoftLocks(flowIdStates + randomIdStates, QueryCriteria.SoftLockingType.LOCKED_ONLY), expectedSoftLockedStates = flowIdStates),
            SoftLockAction(SoftLockingAction.UNLOCK, randomId, randomIdStates, ExpectedSoftLocks(flowIdStates, QueryCriteria.SoftLockingType.LOCKED_ONLY), expectedSoftLockedStates = flowIdStates)
        )
        val flowCompleted = aliceNode.services.startFlow(LockingUnlockingFlow(softLockActions)).resultFuture.getOrThrow(30.seconds)
        assertTrue(flowCompleted)
        assertEquals(flowIdStates + randomIdStates, queryCashStates(QueryCriteria.SoftLockingType.UNLOCKED_ONLY, aliceNode.services.vaultService))
    }

    @Test(timeout=300_000)
    fun `flow soft locks fungible state upon creation`() {
        var lockedStates = 0
        CreateFungibleStateFLow.hook = { vaultService ->
            lockedStates = vaultService.queryBy<NodeVaultServiceTest.FungibleFoo>(
                QueryCriteria.VaultQueryCriteria(softLockingCondition = QueryCriteria.SoftLockingCondition(QueryCriteria.SoftLockingType.LOCKED_ONLY))
            ).states.size
        }
        aliceNode.services.startFlow(CreateFungibleStateFLow()).resultFuture.getOrThrow(30.seconds)
        assertEquals(1, lockedStates)
    }

    @Test(timeout=300_000)
    fun `when flow soft locks, then errors and retries from previous checkpoint, softLockedStates are reverted back correctly`() {
        val randomId = UUID.randomUUID()
        val vaultStates = fillVault(aliceNode, 10)!!.states.map { it.ref }.toList()
        val flowIdStates = vaultStates.subList(0, vaultStates.size / 2).toSet()
        val randomIdStates = vaultStates.subList(vaultStates.size / 2, vaultStates.size).toSet()
        val softLockActions = arrayOf(
            SoftLockAction(SoftLockingAction.LOCK, null, flowIdStates, ExpectedSoftLocks(flowIdStates, QueryCriteria.SoftLockingType.LOCKED_ONLY), expectedSoftLockedStates = flowIdStates),
            SoftLockAction(
                SoftLockingAction.LOCK,
                randomId,
                randomIdStates,
                ExpectedSoftLocks(flowIdStates + randomIdStates, QueryCriteria.SoftLockingType.LOCKED_ONLY),
                expectedSoftLockedStates = flowIdStates,
                doCheckpoint = true
            ),
            SoftLockAction(SoftLockingAction.UNLOCK, null, flowIdStates, ExpectedSoftLocks(randomIdStates, QueryCriteria.SoftLockingType.LOCKED_ONLY), expectedSoftLockedStates = EMPTY_SET),
            SoftLockAction(
                SoftLockingAction.UNLOCK,
                randomId,
                randomIdStates,
                ExpectedSoftLocks(EMPTY_SET, QueryCriteria.SoftLockingType.LOCKED_ONLY),
                expectedSoftLockedStates = EMPTY_SET,
                exception = SQLTransientConnectionException("connection is not available")
            )
        )
        val flowCompleted = aliceNode.services.startFlow(LockingUnlockingFlow(softLockActions)).resultFuture.getOrThrow(30.seconds)
        assertTrue(flowCompleted)
        assertEquals(flowIdStates + randomIdStates, queryCashStates(QueryCriteria.SoftLockingType.UNLOCKED_ONLY, aliceNode.services.vaultService))
        LockingUnlockingFlow.throwOnlyOnce = true
    }

    private fun fillVault(node: TestStartedNode, thisManyStates: Int): Vault<Cash.State>? {
        val bankNode = mockNet.createPartyNode(BOC_NAME)
        val bank = bankNode.info.singleIdentity()
        val cashIssuer = bank.ref(1)
        return node.database.transaction {
            VaultFiller(node.services, TestIdentity(notaryIdentity.name, 20), notaryIdentity).fillWithSomeTestCash(
                    100.DOLLARS,
                    bankNode.services,
                    thisManyStates,
                    thisManyStates,
                    cashIssuer
            )
        }
    }
}

enum class SoftLockingAction {
    LOCK,
    UNLOCK
}

data class ExpectedSoftLocks(val states: Set<StateRef>, val queryCriteria: QueryCriteria.SoftLockingType)

/**
 * If [lockId] is set to null, it will be populated with the flowId within the flow.
 */
data class SoftLockAction(val action: SoftLockingAction,
                          var lockId: UUID?,
                          val states: Set<StateRef>,
                          val expectedSoftLocks: ExpectedSoftLocks,
                          val expectedSoftLockedStates: Set<StateRef>,
                          val exception: Exception? = null,
                          val doCheckpoint: Boolean = false)

internal class LockingUnlockingFlow(private val softLockActions: Array<SoftLockAction>): FlowLogic<Boolean>() {

    companion object {
        var throwOnlyOnce = true
    }

    @Suspendable
    override fun call(): Boolean {
        for (softLockAction in softLockActions) {
            if (softLockAction.lockId == null) { softLockAction.lockId = stateMachine.id.uuid }

            when (softLockAction.action) {
                SoftLockingAction.LOCK -> {
                    serviceHub.vaultService.softLockReserve(softLockAction.lockId!!, NonEmptySet.copyOf(softLockAction.states))
                    // We checkpoint here so that, upon retrying to assert state after reserving
                    if (softLockAction.doCheckpoint) {
                        stateMachine.suspend(FlowIORequest.ForceCheckpoint, false)
                    }
                    assertEquals(softLockAction.expectedSoftLocks.states, queryCashStates(softLockAction.expectedSoftLocks.queryCriteria, serviceHub.vaultService))
                    assertEquals(softLockAction.expectedSoftLockedStates, (stateMachine as? FlowStateMachineImpl<*>)!!.softLockedStates)
                }
                SoftLockingAction.UNLOCK -> {
                    serviceHub.vaultService.softLockRelease(softLockAction.lockId!!, NonEmptySet.copyOf(softLockAction.states))
                    assertEquals(softLockAction.expectedSoftLocks.states, queryCashStates(softLockAction.expectedSoftLocks.queryCriteria, serviceHub.vaultService))
                    assertEquals(softLockAction.expectedSoftLockedStates, (stateMachine as? FlowStateMachineImpl<*>)!!.softLockedStates)
                }
            }

            softLockAction.exception?.let {
                if (throwOnlyOnce) {
                    throwOnlyOnce = false
                    throw it
                }
            }
        }
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