package net.corda.notary.experimental.bftsmart

import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NotaryInfo
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.Try
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.config.NotaryConfig
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.TestClock
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutionException
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// This test is excluded from CI due to the experimental nature of the BFT notary
@Ignore
class BFTNotaryServiceTests {
    companion object {
        private lateinit var mockNet: InternalMockNetwork
        private lateinit var notary: Party
        private lateinit var node: TestStartedNode

        @BeforeClass
        @JvmStatic
        fun before() {
            mockNet = InternalMockNetwork(cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP))
            val clusterSize = minClusterSize(1)
            val started = startBftClusterAndNode(clusterSize, mockNet)
            notary = started.first
            node = started.second
        }

        @AfterClass
        @JvmStatic
        fun stopNodes() {
            mockNet.stopNodes()
        }

        private fun startBftClusterAndNode(clusterSize: Int, mockNet: InternalMockNetwork, exposeRaces: Boolean = false): Pair<Party, TestStartedNode> {
            (Paths.get("config") / "currentView").deleteIfExists() // XXX: Make config object warn if this exists?
            val replicaIds = (0 until clusterSize)
            val serviceLegalName = CordaX500Name("BFT", "Zurich", "CH")
            val notaryIdentity = DevIdentityGenerator.generateDistributedNotaryCompositeIdentity(
                    replicaIds.map { mockNet.baseDirectory(mockNet.nextNodeId + it) },
                    serviceLegalName)

            val networkParameters = NetworkParametersCopier(testNetworkParameters(listOf(NotaryInfo(notaryIdentity, false))))

            val clusterAddresses = replicaIds.map { NetworkHostAndPort("localhost", 11000 + it * 10) }

            val nodes = replicaIds.map { replicaId ->
                mockNet.createUnstartedNode(InternalMockNodeParameters(configOverrides = {
                    val notary = NotaryConfig(
                            validating = false,
                            bftSMaRt = BFTSmartConfig(replicaId, clusterAddresses, exposeRaces = exposeRaces),
                            serviceLegalName = serviceLegalName
                    )
                    doReturn(notary).whenever(it).notary
                }))

            } + mockNet.createUnstartedNode()

            // MockNetwork doesn't support BFT clusters, so we create all the nodes we need unstarted, and then install the
            // network-parameters in their directories before they're started.
            val node = nodes.map { node ->
                networkParameters.install(mockNet.baseDirectory(node.id))
                node.start()
            }.last()

            return Pair(notaryIdentity, node)
        }
    }

    @Test(timeout=300_000)
	fun `detect double spend`() {
        node.run {
            val issueTx = signInitialTransaction(notary) {
                addOutputState(DummyContract.SingleOwnerState(owner = info.singleIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
            }
            services.recordTransactions(issueTx)
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
                    assertEquals(minCorrectReplicas(3), signers.size)
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
                    val exception = result.exception as NotaryException
                    val error = exception.error as NotaryError.Conflict
                    assertEquals(tx.id, error.txId)
                    val (stateRef, cause) = error.consumedStates.entries.single()
                    assertEquals(StateRef(issueTx.id, 0), stateRef)
                    assertEquals(spendTxs[successfulIndex].id.reHash(), cause.hashOfTransactionId)
                }
            }
        }
    }

    @Test(timeout=300_000)
	fun `transactions outside their time window are rejected`() {
        node.run {
            val issueTx = signInitialTransaction(notary) {
                addOutputState(DummyContract.SingleOwnerState(owner = info.singleIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
            }
            services.recordTransactions(issueTx)
            val spendTx = signInitialTransaction(notary) {
                addInputState(issueTx.tx.outRef<ContractState>(0))
                setTimeWindow(TimeWindow.fromOnly(Instant.MAX))
            }
            val flow = NotaryFlow.Client(spendTx)
            val resultFuture = services.startFlow(flow).resultFuture
            mockNet.runNetwork()
            val exception = assertFailsWith<ExecutionException> { resultFuture.get() }
            assertThat(exception.cause).isInstanceOf(NotaryException::class.java)
            val error = (exception.cause as NotaryException).error
            assertThat(error).isInstanceOf(NotaryError.TimeWindowInvalid::class.java)
        }
    }

    @Test(timeout=300_000)
	fun `notarise issue tx with time-window`() {
        node.run {
            val issueTx = signInitialTransaction(notary) {
                setTimeWindow(services.clock.instant(), 30.seconds)
                addOutputState(DummyContract.SingleOwnerState(owner = info.singleIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
            }
            val resultFuture = services.startFlow(NotaryFlow.Client(issueTx)).resultFuture

            mockNet.runNetwork()
            val signatures = resultFuture.get()
            verifySignatures(signatures, issueTx.id)
        }
    }

    @Test(timeout=300_000)
	fun `transactions can be re-notarised outside their time window`() {
        node.run {
            val issueTx = signInitialTransaction(notary) {
                addOutputState(DummyContract.SingleOwnerState(owner = info.singleIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
            }
            services.recordTransactions(issueTx)
            val spendTx = signInitialTransaction(notary) {
                addInputState(issueTx.tx.outRef<ContractState>(0))
                setTimeWindow(TimeWindow.untilOnly(Instant.now() + Duration.ofHours(1)))
            }
            val resultFuture = services.startFlow(NotaryFlow.Client(spendTx)).resultFuture
            mockNet.runNetwork()
            val signatures = resultFuture.get()
            verifySignatures(signatures, spendTx.id)

            for (node in mockNet.nodes) {
                (node.started!!.services.clock as TestClock).advanceBy(Duration.ofDays(1))
            }

            val resultFuture2 = services.startFlow(NotaryFlow.Client(spendTx)).resultFuture
            mockNet.runNetwork()
            val signatures2 = resultFuture2.get()
            verifySignatures(signatures2, spendTx.id)
        }
    }

    private fun verifySignatures(signatures: List<TransactionSignature>, txId: SecureHash) {
        notary.owningKey.isFulfilledBy(signatures.map { it.by })
        signatures.forEach { it.verify(txId) }
    }

    private fun TestStartedNode.signInitialTransaction(notary: Party, block: TransactionBuilder.() -> Any?): SignedTransaction {
        return services.signInitialTransaction(
                TransactionBuilder(notary).apply {
                    addCommand(dummyCommand(services.myInfo.singleIdentity().owningKey))
                    block()
                }
        )
    }
}
