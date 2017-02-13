package net.corda.core.flows

import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.getOrThrow
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.utilities.Emoji
import net.corda.flows.CashIssueFlow
import net.corda.flows.ContractUpgradeFlow
import net.corda.flows.FinalityFlow
import net.corda.node.internal.CordaRPCOpsImpl
import net.corda.node.services.User
import net.corda.node.services.messaging.CURRENT_RPC_USER
import net.corda.node.services.startFlowPermission
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContractUpgradeFlowTest {
    lateinit var mockNet: MockNetwork
    lateinit var a: MockNetwork.MockNode
    lateinit var b: MockNetwork.MockNode
    lateinit var notary: Party

    @Before
    fun setup() {
        mockNet = MockNetwork()
        val nodes = mockNet.createSomeNodes()
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]
        notary = nodes.notaryNode.info.notaryIdentity
        mockNet.runNetwork()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test
    fun `2 parties contract upgrade`() {
        // Create dummy contract.
        val twoPartyDummyContract = DummyContract.generateInitial(0, notary, a.info.legalIdentity.ref(1), b.info.legalIdentity.ref(1))
        val stx = twoPartyDummyContract.signWith(a.services.legalIdentityKey)
                .signWith(b.services.legalIdentityKey)
                .toSignedTransaction()

        a.services.startFlow(FinalityFlow(stx, setOf(a.info.legalIdentity, b.info.legalIdentity)))
        mockNet.runNetwork()

        val atx = databaseTransaction(a.database) { a.services.storageService.validatedTransactions.getTransaction(stx.id) }
        val btx = databaseTransaction(b.database) { b.services.storageService.validatedTransactions.getTransaction(stx.id) }
        requireNotNull(atx)
        requireNotNull(btx)

        // The request is expected to be rejected because party B haven't authorise the upgrade yet.
        val rejectedFuture = a.services.startFlow(ContractUpgradeFlow.Instigator(atx!!.tx.outRef(0), DummyContractV2::class.java)).resultFuture
        mockNet.runNetwork()
        assertFailsWith(ExecutionException::class) { rejectedFuture.get() }

        // Party B authorise the contract state upgrade.
        b.services.vaultService.authoriseContractUpgrade(btx!!.tx.outRef<ContractState>(0), DummyContractV2::class.java)

        // Party A initiates contract upgrade flow, expected to succeed this time.
        val resultFuture = a.services.startFlow(ContractUpgradeFlow.Instigator(atx.tx.outRef(0), DummyContractV2::class.java)).resultFuture
        mockNet.runNetwork()

        val result = resultFuture.get()

        listOf(a, b).forEach {
            val stx = databaseTransaction(a.database) { a.services.storageService.validatedTransactions.getTransaction(result.ref.txhash) }
            requireNotNull(stx)

            // Verify inputs.
            val input = databaseTransaction(a.database) { a.services.storageService.validatedTransactions.getTransaction(stx!!.tx.inputs.single().txhash) }
            requireNotNull(input)
            assertTrue(input!!.tx.outputs.single().data is DummyContract.State)

            // Verify outputs.
            assertTrue(stx!!.tx.outputs.single().data is DummyContractV2.State)
        }
    }

    @Test
    fun `2 parties contract upgrade using RPC`() {
        // Create dummy contract.
        val twoPartyDummyContract = DummyContract.generateInitial(0, notary, a.info.legalIdentity.ref(1), b.info.legalIdentity.ref(1))
        val stx = twoPartyDummyContract.signWith(a.services.legalIdentityKey)
                .signWith(b.services.legalIdentityKey)
                .toSignedTransaction()

        a.services.startFlow(FinalityFlow(stx, setOf(a.info.legalIdentity, b.info.legalIdentity)))
        mockNet.runNetwork()

        val atx = databaseTransaction(a.database) { a.services.storageService.validatedTransactions.getTransaction(stx.id) }
        val btx = databaseTransaction(b.database) { b.services.storageService.validatedTransactions.getTransaction(stx.id) }
        requireNotNull(atx)
        requireNotNull(btx)

        // The request is expected to be rejected because party B haven't authorise the upgrade yet.

        val rpcA = CordaRPCOpsImpl(a.services, a.smm, a.database)
        val rpcB = CordaRPCOpsImpl(b.services, b.smm, b.database)

        CURRENT_RPC_USER.set(User("user", "pwd", permissions = setOf(
                startFlowPermission<ContractUpgradeFlow.Instigator<*, *>>()
        )))

        val rejectedFuture = rpcA.startFlow({ stateAndRef, upgrade -> ContractUpgradeFlow.Instigator(stateAndRef, upgrade) },
                atx!!.tx.outRef<DummyContract.State>(0),
                DummyContractV2::class.java).returnValue

        mockNet.runNetwork()
        assertFailsWith(ExecutionException::class) { rejectedFuture.get() }

        // Party B authorise the contract state upgrade.
        rpcB.authoriseContractUpgrade(btx!!.tx.outRef<ContractState>(0), DummyContractV2::class.java)

        // Party A initiates contract upgrade flow, expected to succeed this time.
        val resultFuture = rpcA.startFlow({ stateAndRef, upgrade -> ContractUpgradeFlow.Instigator(stateAndRef, upgrade) },
                atx.tx.outRef<DummyContract.State>(0),
                DummyContractV2::class.java).returnValue

        mockNet.runNetwork()
        val result = resultFuture.get()
        // Check results.
        listOf(a, b).forEach {
            val stx = databaseTransaction(a.database) { a.services.storageService.validatedTransactions.getTransaction(result.ref.txhash) }
            requireNotNull(stx)

            // Verify inputs.
            val input = databaseTransaction(a.database) { a.services.storageService.validatedTransactions.getTransaction(stx!!.tx.inputs.single().txhash) }
            requireNotNull(input)
            assertTrue(input!!.tx.outputs.single().data is DummyContract.State)

            // Verify outputs.
            assertTrue(stx!!.tx.outputs.single().data is DummyContractV2.State)
        }
    }

    @Test
    fun `upgrade Cash to v2`() {
        // Create some cash.
        val result = a.services.startFlow(CashIssueFlow(Amount(1000, USD), OpaqueBytes.of(1), a.info.legalIdentity, notary)).resultFuture
        mockNet.runNetwork()
        val stateAndRef = result.getOrThrow().tx.outRef<Cash.State>(0)
        // Starts contract upgrade flow.
        a.services.startFlow(ContractUpgradeFlow.Instigator(stateAndRef, CashV2::class.java))
        mockNet.runNetwork()
        // Get contract state form the vault.
        val state = databaseTransaction(a.database) { a.vault.currentVault.states }
        assertTrue(state.single().state.data is CashV2.State, "Contract state is upgraded to the new version.")
        assertEquals(Amount(1000000, USD).`issued by`(a.info.legalIdentity.ref(1)), (state.first().state.data as CashV2.State).amount, "Upgraded cash contain the correct amount.")
        assertEquals(listOf(a.info.legalIdentity.owningKey), (state.first().state.data as CashV2.State).owners, "Upgraded cash belongs to the right owner.")
    }

    class CashV2 : UpgradedContract<Cash.State, CashV2.State> {
        override val legacyContract = Cash::class.java

        data class State(override val amount: Amount<Issued<Currency>>, val owners: List<CompositeKey>) : FungibleAsset<Currency> {
            override val owner: CompositeKey = owners.first()
            override val exitKeys = (owners + amount.token.issuer.party.owningKey).toSet()
            override val contract = CashV2()
            override val participants = owners

            override fun move(newAmount: Amount<Issued<Currency>>, newOwner: CompositeKey) = copy(amount = amount.copy(newAmount.quantity, amount.token), owners = listOf(newOwner))
            override fun toString() = "${Emoji.bagOfCash}New Cash($amount at ${amount.token.issuer} owned by $owner)"
            override fun withNewOwner(newOwner: CompositeKey) = Pair(Cash.Commands.Move(), copy(owners = listOf(newOwner)))
        }

        override fun upgrade(state: Cash.State) = CashV2.State(state.amount.times(1000), listOf(state.owner))

        override fun verify(tx: TransactionForContract) {}

        // Dummy Cash contract for testing.
        override val legalContractReference = SecureHash.sha256("")
    }
}
