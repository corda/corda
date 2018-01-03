package net.corda.node.services

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.node.services.config.NotaryConfig
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.nodeapi.internal.network.NotaryInfo
import net.corda.testing.*
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.inMemoryH2DataSourceConfig
import net.corda.testing.node.startFlow
import org.junit.*
import java.math.BigInteger
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MySQLNotaryServiceTests : IntegrationTest() {
    companion object {
        val notaryName = CordaX500Name("MySQL Notary Service", "Zurich", "CH")
        @ClassRule @JvmField
        val databaseSchemas = IntegrationTestSchemas("node_0", "node_1", "node_2")
    }

    private lateinit var mockNet: MockNetwork
    private lateinit var node: StartedNode<MockNetwork.MockNode>
    private lateinit var notaryParty: Party
    private lateinit var notaryNode: StartedNode<MockNetwork.MockNode>

    @Before
    fun before() {
        mockNet = MockNetwork(cordappPackages = listOf("net.corda.testing.contracts"))
        notaryParty = DevIdentityGenerator.installKeyStoreWithNodeIdentity(mockNet.baseDirectory(mockNet.nextNodeId), notaryName)
        val networkParameters = NetworkParametersCopier(testNetworkParameters(listOf(NotaryInfo(notaryParty, false))))
        val notaryNodeUnstarted = createNotaryNode()
        val nodeUnstarted = mockNet.createUnstartedNode()

        val startedNodes = listOf(notaryNodeUnstarted, nodeUnstarted).map { n ->
            networkParameters.install(mockNet.baseDirectory(n.id))
            n.start()
        }
        notaryNode = startedNodes.first()
        node = startedNodes.last()
    }

    @After
    fun stopNodes() {
        mockNet.stopNodes()
    }

    @Test
    fun `detect double spend`() {
        val inputState = issueState(node, notaryParty)

        val firstTxBuilder = TransactionBuilder(notaryParty)
                .addInputState(inputState)
                .addCommand(dummyCommand(node.services.myInfo.chooseIdentity().owningKey))
        val firstSpendTx = node.services.signInitialTransaction(firstTxBuilder)

        val firstSpend = node.services.startFlow(NotaryFlow.Client(firstSpendTx))
        mockNet.runNetwork()

        firstSpend.resultFuture.getOrThrow()

        val secondSpendBuilder = TransactionBuilder(notaryParty).withItems(inputState).run {
            val dummyState = DummyContract.SingleOwnerState(0, node.info.chooseIdentity())
            addOutputState(dummyState, DummyContract.PROGRAM_ID)
            addCommand(dummyCommand(node.services.myInfo.chooseIdentity().owningKey))
            this
        }
        val secondSpendTx = node.services.signInitialTransaction(secondSpendBuilder)
        val secondSpend = node.services.startFlow(NotaryFlow.Client(secondSpendTx))

        mockNet.runNetwork()

        val ex = assertFailsWith(NotaryException::class) { secondSpend.resultFuture.getOrThrow() }
        val error = ex.error as NotaryError.Conflict
        assertEquals(error.txId, secondSpendTx.id)
    }

    @Test
    fun `notarisations are idempotent`() {
        val inputState = issueState(node, notaryParty)

        val txBuilder = TransactionBuilder(notaryParty)
                .addInputState(inputState)
                .addCommand(dummyCommand(node.services.myInfo.chooseIdentity().owningKey))
        val spendTx = node.services.signInitialTransaction(txBuilder)

        val notarise = node.services.startFlow(NotaryFlow.Client(spendTx))
        mockNet.runNetwork()
        val signature = notarise.resultFuture.get().single()

        val notariseRetry = node.services.startFlow(NotaryFlow.Client(spendTx))
        mockNet.runNetwork()
        val signatureRetry = notariseRetry.resultFuture.get().single()

        fun checkSignature(signature: TransactionSignature) {
            signature.verify(spendTx.id)
            assertEquals(notaryParty.owningKey, signature.by)
        }

        checkSignature(signature)
        checkSignature(signatureRetry)
    }

    private fun createNotaryNode(): MockNetwork.MockNode {
        val dataStoreProperties = makeTestDataSourceProperties(configSupplier = ::inMemoryH2DataSourceConfig).apply {
            setProperty("autoCommit", "false")
        }
        return mockNet.createUnstartedNode(
                MockNodeParameters(
                        legalName = notaryName,
                        entropyRoot = BigInteger.valueOf(60L),
                        configOverrides = {
                            val notaryConfig = NotaryConfig(validating = false, mysql = dataStoreProperties)
                            doReturn(notaryConfig).whenever(it).notary
                        }
                )
        )
    }

    private fun issueState(node: StartedNode<*>, notary: Party): StateAndRef<*> {
        return node.database.transaction {
            val builder = DummyContract.generateInitial(Random().nextInt(), notary, node.info.chooseIdentity().ref(0))
            val stx = node.services.signInitialTransaction(builder)
            node.services.recordTransactions(stx)
            StateAndRef(builder.outputStates().first(), StateRef(stx.id, 0))
        }
    }
}
