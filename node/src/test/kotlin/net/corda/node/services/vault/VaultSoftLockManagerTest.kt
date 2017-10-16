package net.corda.node.services.vault

import co.paralleluniverse.fibers.Suspendable
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import net.corda.core.contracts.*
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.StateLoader
import net.corda.core.node.services.KeyManagementService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.SoftLockingCondition
import net.corda.core.node.services.vault.QueryCriteria.SoftLockingType.LOCKED_ONLY
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.node.services.api.VaultServiceInternal
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.util.*
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertEquals

class VaultSoftLockManagerTest {
    private val mockVault: VaultServiceInternal = mock()
    private val mockNet = MockNetwork(cordappPackages = listOf(ContractImpl::class.java.`package`.name), defaultFactory = object : MockNetwork.Factory<MockNetwork.MockNode> {
        override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?, id: Int, notaryIdentity: Pair<ServiceInfo, KeyPair>?, entropyRoot: BigInteger): MockNetwork.MockNode {
            return object : MockNetwork.MockNode(config, network, networkMapAddr, id, notaryIdentity, entropyRoot) {
                override fun makeVaultService(keyManagementService: KeyManagementService, stateLoader: StateLoader): VaultServiceInternal {
                    val realVault = super.makeVaultService(keyManagementService, stateLoader)
                    return object : VaultServiceInternal by realVault {
                        override fun softLockRelease(lockId: UUID, stateRefs: NonEmptySet<StateRef>?) {
                            mockVault.softLockRelease(lockId, stateRefs) // No need to also call the real one for these tests.
                        }
                    }
                }
            }
        }
    })
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
        val fsm = node.services.startFlow(FlowLogicImpl(state))
        mockNet.runNetwork()
        if (expectSoftLock) {
            assertEquals(listOf(state), fsm.resultFuture.getOrThrow())
            verify(mockVault).softLockRelease(fsm.id.uuid, null)
        } else {
            assertEquals(emptyList(), fsm.resultFuture.getOrThrow())
            // In this case we don't want softLockRelease called so that we avoid its expensive query.
        }
        verifyNoMoreInteractions(mockVault)
    }

    @Test
    fun `plain old state is not soft locked`() = run(false, PlainOldState(node))

    @Test
    fun `fungible asset is soft locked`() = run(true, FungibleAssetImpl(node))
}
