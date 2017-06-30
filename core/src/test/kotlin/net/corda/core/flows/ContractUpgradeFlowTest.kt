package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.getOrThrow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.unconsumedStates
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.Emoji
import net.corda.flows.CashIssueFlow
import net.corda.flows.ContractUpgradeFlow
import net.corda.flows.FinalityFlow
import net.corda.node.internal.CordaRPCOpsImpl
import net.corda.node.services.startFlowPermission
import net.corda.node.utilities.transaction
import net.corda.nodeapi.User
import net.corda.testing.RPCDriverExposedDSLInterface
import net.corda.testing.node.MockNetwork
import net.corda.testing.rpcDriver
import net.corda.testing.rpcTestUser
import net.corda.testing.startRpcClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
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
        val signedByA = a.services.signInitialTransaction(twoPartyDummyContract)
        val stx = b.services.addSignature(signedByA)

        a.services.startFlow(FinalityFlow(stx, setOf(a.info.legalIdentity, b.info.legalIdentity)))
        mockNet.runNetwork()

        val atx = a.database.transaction { a.services.validatedTransactions.getTransaction(stx.id) }
        val btx = b.database.transaction { b.services.validatedTransactions.getTransaction(stx.id) }
        requireNotNull(atx)
        requireNotNull(btx)

        // The request is expected to be rejected because party B hasn't authorised the upgrade yet.
        val rejectedFuture = a.services.startFlow(ContractUpgradeFlow(atx!!.tx.outRef(0), DummyContractV2::class.java)).resultFuture
        mockNet.runNetwork()
        assertFailsWith(FlowSessionException::class) { rejectedFuture.getOrThrow() }

        // Party B authorise the contract state upgrade.
        b.services.vaultService.authoriseContractUpgrade(btx!!.tx.outRef<ContractState>(0), DummyContractV2::class.java)

        // Party A initiates contract upgrade flow, expected to succeed this time.
        val resultFuture = a.services.startFlow(ContractUpgradeFlow(atx.tx.outRef(0), DummyContractV2::class.java)).resultFuture
        mockNet.runNetwork()

        val result = resultFuture.getOrThrow()

        fun check(node: MockNetwork.MockNode) {
            val nodeStx = node.database.transaction {
                node.services.validatedTransactions.getTransaction(result.ref.txhash)
            }
            requireNotNull(nodeStx)

            // Verify inputs.
            val input = node.database.transaction {
                node.services.validatedTransactions.getTransaction(nodeStx!!.tx.inputs.single().txhash)
            }
            requireNotNull(input)
            assertTrue(input!!.tx.outputs.single().data is DummyContract.State)

            // Verify outputs.
            assertTrue(nodeStx!!.tx.outputs.single().data is DummyContractV2.State)
        }
        check(a)
        check(b)
    }

    private fun RPCDriverExposedDSLInterface.startProxy(node: MockNetwork.MockNode, user: User): CordaRPCOps {
        return startRpcClient<CordaRPCOps>(
                rpcAddress = startRpcServer(
                        rpcUser = user,
                        ops = CordaRPCOpsImpl(node.services, node.smm, node.database)
                ).get().broker.hostAndPort!!,
                username = user.username,
                password = user.password
        ).get()
    }

    @Test
    fun `2 parties contract upgrade using RPC`() {
        rpcDriver {
            // Create dummy contract.
            val twoPartyDummyContract = DummyContract.generateInitial(0, notary, a.info.legalIdentity.ref(1), b.info.legalIdentity.ref(1))
            val signedByA = a.services.signInitialTransaction(twoPartyDummyContract)
            val stx = b.services.addSignature(signedByA)

            val user = rpcTestUser.copy(permissions = setOf(
                    startFlowPermission<FinalityInvoker>(),
                    startFlowPermission<ContractUpgradeFlow<*, *>>()
            ))
            val rpcA = startProxy(a, user)
            val rpcB = startProxy(b, user)
            val handle = rpcA.startFlow(::FinalityInvoker, stx, setOf(a.info.legalIdentity, b.info.legalIdentity))
            mockNet.runNetwork()
            handle.returnValue.getOrThrow()

            val atx = a.database.transaction { a.services.validatedTransactions.getTransaction(stx.id) }
            val btx = b.database.transaction { b.services.validatedTransactions.getTransaction(stx.id) }
            requireNotNull(atx)
            requireNotNull(btx)

            val rejectedFuture = rpcA.startFlow({ stateAndRef, upgrade -> ContractUpgradeFlow(stateAndRef, upgrade) },
                    atx!!.tx.outRef<DummyContract.State>(0),
                    DummyContractV2::class.java).returnValue

            mockNet.runNetwork()
            assertFailsWith(FlowSessionException::class) { rejectedFuture.getOrThrow() }

            // Party B authorise the contract state upgrade.
            rpcB.authoriseContractUpgrade(btx!!.tx.outRef<ContractState>(0), DummyContractV2::class.java)

            // Party A initiates contract upgrade flow, expected to succeed this time.
            val resultFuture = rpcA.startFlow({ stateAndRef, upgrade -> ContractUpgradeFlow(stateAndRef, upgrade) },
                    atx.tx.outRef<DummyContract.State>(0),
                    DummyContractV2::class.java).returnValue

            mockNet.runNetwork()
            val result = resultFuture.getOrThrow()
            // Check results.
            listOf(a, b).forEach {
                val signedTX = a.database.transaction { a.services.validatedTransactions.getTransaction(result.ref.txhash) }
                requireNotNull(signedTX)

                // Verify inputs.
                val input = a.database.transaction { a.services.validatedTransactions.getTransaction(signedTX!!.tx.inputs.single().txhash) }
                requireNotNull(input)
                assertTrue(input!!.tx.outputs.single().data is DummyContract.State)

                // Verify outputs.
                assertTrue(signedTX!!.tx.outputs.single().data is DummyContractV2.State)
            }
        }
    }

    @Test
    fun `upgrade Cash to v2`() {
        // Create some cash.
        val anonymous = false
        val result = a.services.startFlow(CashIssueFlow(Amount(1000, USD), OpaqueBytes.of(1), a.info.legalIdentity, notary, anonymous)).resultFuture
        mockNet.runNetwork()
        val stx = result.getOrThrow().stx
        val stateAndRef = stx.tx.outRef<Cash.State>(0)
        val baseState = a.database.transaction { a.vault.unconsumedStates<ContractState>().single() }
        assertTrue(baseState.state.data is Cash.State, "Contract state is old version.")
        // Starts contract upgrade flow.
        val upgradeResult = a.services.startFlow(ContractUpgradeFlow(stateAndRef, CashV2::class.java)).resultFuture
        mockNet.runNetwork()
        upgradeResult.getOrThrow()
        // Get contract state from the vault.
        val firstState = a.database.transaction { a.vault.unconsumedStates<ContractState>().single() }
        assertTrue(firstState.state.data is CashV2.State, "Contract state is upgraded to the new version.")
        assertEquals(Amount(1000000, USD).`issued by`(a.info.legalIdentity.ref(1)), (firstState.state.data as CashV2.State).amount, "Upgraded cash contain the correct amount.")
        assertEquals<Collection<AbstractParty>>(listOf(a.info.legalIdentity), (firstState.state.data as CashV2.State).owners, "Upgraded cash belongs to the right owner.")
    }

    class CashV2 : UpgradedContract<Cash.State, CashV2.State> {
        override val legacyContract = Cash::class.java

        data class State(override val amount: Amount<Issued<Currency>>, val owners: List<AbstractParty>) : FungibleAsset<Currency> {
            override val owner: AbstractParty = owners.first()
            override val exitKeys = (owners + amount.token.issuer.party).map { it.owningKey }.toSet()
            override val contract = CashV2()
            override val participants = owners

            override fun move(newAmount: Amount<Issued<Currency>>, newOwner: AbstractParty) = copy(amount = amount.copy(newAmount.quantity), owners = listOf(newOwner))
            override fun toString() = "${Emoji.bagOfCash}New Cash($amount at ${amount.token.issuer} owned by $owner)"
            override fun withNewOwner(newOwner: AbstractParty) = Pair(Cash.Commands.Move(), copy(owners = listOf(newOwner)))
        }

        override fun upgrade(state: Cash.State) = CashV2.State(state.amount.times(1000), listOf(state.owner))

        override fun verify(tx: TransactionForContract) {}

        // Dummy Cash contract for testing.
        override val legalContractReference = SecureHash.sha256("")
    }

    @StartableByRPC
    class FinalityInvoker(val transaction: SignedTransaction,
                          val extraRecipients: Set<Party>) : FlowLogic<List<SignedTransaction>>() {
        @Suspendable
        override fun call(): List<SignedTransaction> = subFlow(FinalityFlow(transaction, extraRecipients))
    }
}
