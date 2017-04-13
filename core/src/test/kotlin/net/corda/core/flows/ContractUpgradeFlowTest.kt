package net.corda.core.flows

import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.getOrThrow
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.unconsumedStates
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.utilities.Emoji
import net.corda.flows.CashIssueFlow
import net.corda.flows.ContractUpgradeFlow
import net.corda.flows.FinalityFlow
import net.corda.node.internal.CordaRPCOpsImpl
import net.corda.node.services.startFlowPermission
import net.corda.node.utilities.transaction
import net.corda.nodeapi.CURRENT_RPC_USER
import net.corda.nodeapi.User
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.security.*

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

        val atx = a.database.transaction { a.services.storageService.validatedTransactions.getTransaction(stx.id) }
        val btx = b.database.transaction { b.services.storageService.validatedTransactions.getTransaction(stx.id) }
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

        fun check(node: MockNetwork.MockNode) {
            val nodeStx = node.database.transaction {
                node.services.storageService.validatedTransactions.getTransaction(result.ref.txhash)
            }
            requireNotNull(nodeStx)

            // Verify inputs.
            val input = node.database.transaction {
                node.services.storageService.validatedTransactions.getTransaction(nodeStx!!.tx.inputs.single().txhash)
            }
            requireNotNull(input)
            assertTrue(input!!.tx.outputs.single().data is DummyContract.State)

            // Verify outputs.
            assertTrue(nodeStx!!.tx.outputs.single().data is DummyContractV2.State)
        }
        check(a)
        check(b)
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

        val atx = a.database.transaction { a.services.storageService.validatedTransactions.getTransaction(stx.id) }
        val btx = b.database.transaction { b.services.storageService.validatedTransactions.getTransaction(stx.id) }
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
            val signedTX = a.database.transaction { a.services.storageService.validatedTransactions.getTransaction(result.ref.txhash) }
            requireNotNull(signedTX)

            // Verify inputs.
            val input = a.database.transaction { a.services.storageService.validatedTransactions.getTransaction(signedTX!!.tx.inputs.single().txhash) }
            requireNotNull(input)
            assertTrue(input!!.tx.outputs.single().data is DummyContract.State)

            // Verify outputs.
            assertTrue(signedTX!!.tx.outputs.single().data is DummyContractV2.State)
        }
    }

    @Test
    fun `upgrade Cash to v2`() {
        // Create some cash.
        val result = a.services.startFlow(CashIssueFlow(Amount(1000, USD), OpaqueBytes.of(1), a.info.legalIdentity, notary)).resultFuture
        mockNet.runNetwork()
        val baseState = a.database.transaction { a.vault.unconsumedStates<ContractState>().single() }
        assertTrue(baseState.state.data is Cash.State, "Contract state is old version.")
        val stateAndRef = result.getOrThrow().tx.outRef<Cash.State>(0)
        // Starts contract upgrade flow.
        a.services.startFlow(ContractUpgradeFlow.Instigator(stateAndRef, CashV2::class.java))
        mockNet.runNetwork()
        // Get contract state from the vault.
        val firstState = a.database.transaction { a.vault.unconsumedStates<ContractState>().single() }
        assertTrue(firstState.state.data is CashV2.State, "Contract state is upgraded to the new version.")
        assertEquals(Amount(1000000, USD).`issued by`(a.info.legalIdentity.ref(1)), (firstState.state.data as CashV2.State).amount, "Upgraded cash contain the correct amount.")
        assertEquals(listOf(a.info.legalIdentity.owningKey), (firstState.state.data as CashV2.State).owners, "Upgraded cash belongs to the right owner.")
    }

    class CashV2 : UpgradedContract<Cash.State, CashV2.State> {
        override val legacyContract = Cash::class.java

        data class State(override val amount: Amount<Issued<Currency>>, val owners: List<PublicKey>) : FungibleAsset<Currency> {
            override val owner: PublicKey = owners.first()
            override val exitKeys = (owners + amount.token.issuer.party.owningKey).toSet()
            override val contract = CashV2()
            override val participants = owners

            override fun move(newAmount: Amount<Issued<Currency>>, newOwner: PublicKey) = copy(amount = amount.copy(newAmount.quantity), owners = listOf(newOwner))
            override fun toString() = "${Emoji.bagOfCash}New Cash($amount at ${amount.token.issuer} owned by $owner)"
            override fun withNewOwner(newOwner: PublicKey) = Pair(Cash.Commands.Move(), copy(owners = listOf(newOwner)))
        }

        override fun upgrade(state: Cash.State) = CashV2.State(state.amount.times(1000), listOf(state.owner))

        override fun verify(tx: TransactionForContract) {}

        // Dummy Cash contract for testing.
        override val legalContractReference = SecureHash.sha256("")
    }
}
