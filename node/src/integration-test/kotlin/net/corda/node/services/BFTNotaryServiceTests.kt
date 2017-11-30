package net.corda.node.services

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.CompositeKey
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.deleteIfExists
import net.corda.core.internal.div
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.Try
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.node.services.config.BFTSMaRtConfiguration
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.transactions.minClusterSize
import net.corda.node.services.transactions.minCorrectReplicas
import net.corda.nodeapi.internal.IdentityGenerator
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.nodeapi.internal.network.NotaryInfo
import net.corda.testing.chooseIdentity
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.dummyCommand
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.startFlow
import org.junit.After
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BFTNotaryServiceTests {
    private val mockNet = MockNetwork(emptyList())
    private lateinit var notary: Party
    private lateinit var node: StartedNode<MockNode>

    @After
    fun stopNodes() {
        mockNet.stopNodes()
    }

    private fun startBftClusterAndNode(clusterSize: Int, exposeRaces: Boolean = false) {
        (Paths.get("config") / "currentView").deleteIfExists() // XXX: Make config object warn if this exists?
        val replicaIds = (0 until clusterSize)

        notary = IdentityGenerator.generateDistributedNotaryIdentity(
                replicaIds.map { mockNet.baseDirectory(mockNet.nextNodeId + it) },
                CordaX500Name("BFT", "Zurich", "CH"))

        val networkParameters = NetworkParametersCopier(testNetworkParameters(listOf(NotaryInfo(notary, false))))

        val clusterAddresses = replicaIds.map { NetworkHostAndPort("localhost", 11000 + it * 10) }

        val nodes = replicaIds.map { replicaId ->
            mockNet.createUnstartedNode(MockNodeParameters(configOverrides = {
                val notary = NotaryConfig(validating = false, bftSMaRt = BFTSMaRtConfiguration(replicaId, clusterAddresses, exposeRaces = exposeRaces))
                doReturn(notary).whenever(it).notary
            }))
        } + mockNet.createUnstartedNode()

        // MockNetwork doesn't support BFT clusters, so we create all the nodes we need unstarted, and then install the
        // network-parameters in their directories before they're started.
        node = nodes.map { node ->
            networkParameters.install(mockNet.baseDirectory(node.id))
            node.start()
        }.last()
    }

    /** Failure mode is the redundant replica gets stuck in startup, so we can't dispose it cleanly at the end. */
    @Test
    fun `all replicas start even if there is a new consensus during startup`() {
        startBftClusterAndNode(minClusterSize(1), exposeRaces = true) // This true adds a sleep to expose the race.
        val f = node.run {
            val trivialTx = signInitialTransaction(notary) {
                addOutputState(DummyContract.SingleOwnerState(owner = info.chooseIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
            }
            // Create a new consensus while the redundant replica is sleeping:
            services.startFlow(NotaryFlow.Client(trivialTx)).resultFuture
        }
        mockNet.runNetwork()
        f.getOrThrow()
    }

    @Test
    fun `detect double spend 1 faulty`() {
        detectDoubleSpend(1)
    }

    @Test
    fun `detect double spend 2 faulty`() {
        detectDoubleSpend(2)
    }

    private fun detectDoubleSpend(faultyReplicas: Int) {
        val clusterSize = minClusterSize(faultyReplicas)
        startBftClusterAndNode(clusterSize)
        node.run {
            val issueTx = signInitialTransaction(notary) {
                addOutputState(DummyContract.SingleOwnerState(owner = info.chooseIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
            }
            database.transaction {
                services.recordTransactions(issueTx)
            }
            val spendTxs = (1..10).map {
                signInitialTransaction(notary) {
                    addInputState(issueTx.tx.outRef<ContractState>(0))
                }
            }
            assertEquals(spendTxs.size, spendTxs.map { it.id }.distinct().size)
            val flows = spendTxs.map { NotaryFlow.Client(it) }
            val stateMachines = flows.map { services.startFlow(it) }
            mockNet.runNetwork()
            val results = stateMachines.map { Try.on { it.resultFuture.getOrThrow() } }
            val successfulIndex = results.mapIndexedNotNull { index, result ->
                if (result is Try.Success) {
                    val signers = result.value.map { it.by }
                    assertEquals(minCorrectReplicas(clusterSize), signers.size)
                    signers.forEach {
                        assertTrue(it in (notary.owningKey as CompositeKey).leafKeys)
                    }
                    index
                } else {
                    null
                }
            }.single()
            spendTxs.zip(results).forEach { (tx, result) ->
                if (result is Try.Failure) {
                    val error = (result.exception as NotaryException).error as NotaryError.Conflict
                    assertEquals(tx.id, error.txId)
                    val (stateRef, consumingTx) = error.conflict.verified().stateHistory.entries.single()
                    assertEquals(StateRef(issueTx.id, 0), stateRef)
                    assertEquals(spendTxs[successfulIndex].id, consumingTx.id)
                    assertEquals(0, consumingTx.inputIndex)
                    assertEquals(info.chooseIdentity(), consumingTx.requestingParty)
                }
            }
        }
    }

    private fun StartedNode<*>.signInitialTransaction(notary: Party, block: TransactionBuilder.() -> Any?): SignedTransaction {
        return services.signInitialTransaction(
                TransactionBuilder(notary).apply {
                    addCommand(dummyCommand(services.myInfo.chooseIdentity().owningKey))
                    block()
                }
        )
    }
}
