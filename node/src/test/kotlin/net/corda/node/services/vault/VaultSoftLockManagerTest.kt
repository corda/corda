package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.SoftLockingCondition
import net.corda.core.node.services.vault.QueryCriteria.SoftLockingType.LOCKED_ONLY
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Test
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertEquals

class VaultSoftLockManagerTest {
    private val mockNet = MockNetwork(cordappPackages = listOf(ContractImpl::class.java.`package`.name))
    private val node = mockNet.createNotaryNode()
    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    private object CommandDataImpl : CommandData
    private class FlowLogicImpl(private val state: ContractState) : FlowLogic<List<ContractState>>() {
        @Suspendable
        override fun call() = run {
            subFlow(FinalityFlow(serviceHub.signInitialTransaction(TransactionBuilder(notary = ourIdentity).apply {
                addOutputState(state, ContractImpl::class.jvmName)
                addCommand(CommandDataImpl, ourIdentity.owningKey)
            })))
            serviceHub.vaultService.queryBy<ContractState>(VaultQueryCriteria(softLockingCondition = SoftLockingCondition(LOCKED_ONLY))).states.map {
                it.state.data
            }
        }
    }

    private abstract class SingleParticipantState(participant: StartedNode<*>) : ContractState {
        override val participants = listOf(participant.info.chooseIdentity())
    }

    private class PlainOldState(participant: StartedNode<*>) : SingleParticipantState(participant)
    private class FungibleAssetImpl(participant: StartedNode<*>) : SingleParticipantState(participant), FungibleAsset<Unit> {
        override val owner get() = participants[0]
        override fun withNewOwner(newOwner: AbstractParty) = throw UnsupportedOperationException()
        override val amount get() = Amount(1, Issued(PartyAndReference(owner, OpaqueBytes.of(1)), Unit))
        override val exitKeys get() = throw UnsupportedOperationException()
        override fun withNewOwnerAndAmount(newAmount: Amount<Issued<Unit>>, newOwner: AbstractParty) = throw UnsupportedOperationException()
        override fun equals(other: Any?) = other is FungibleAssetImpl && participants == other.participants
        override fun hashCode() = participants.hashCode()
    }

    class ContractImpl : Contract {
        override fun verify(tx: LedgerTransaction) {}
    }

    private fun run(expectSoftLock: Boolean, state: ContractState) {
        val f = node.services.startFlow(FlowLogicImpl(state)).resultFuture
        mockNet.runNetwork()
        assertEquals(if (expectSoftLock) listOf(state) else emptyList(), f.getOrThrow())
    }

    @Test
    fun `plain old state is not soft locked`() = run(false, PlainOldState(node))

    @Test
    fun `fungible asset is soft locked`() = run(true, FungibleAssetImpl(node))
}
