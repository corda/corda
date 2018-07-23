/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import com.typesafe.config.ConfigFactory
import net.corda.client.mock.Generator
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.generateKeyPair
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.notary.AsyncCFTNotaryService
import net.corda.core.internal.notary.AsyncUniquenessProvider.Result
import net.corda.core.internal.notary.generateSignature
import net.corda.core.node.NotaryInfo
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.internal.StartedNode
import net.corda.node.services.config.MySQLConfiguration
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.transactions.MySQLNotaryService
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.TestClock
import net.corda.testing.node.internal.*
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.math.BigInteger
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MySQLNotaryServiceTests : IntegrationTest() {
    companion object {
        val notaryName = CordaX500Name("MySQL Notary Service", "Zurich", "CH")
        val notaryNodeName = CordaX500Name("Notary Replica 1", "Zurich", "CH")
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas("node_0", "node_1", "node_2")
    }

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var node: StartedNode<InternalMockNetwork.MockNode>
    private val nodeParty: Party get() = node.info.singleIdentity()
    private lateinit var notaryParty: Party
    private lateinit var notaryNode: StartedNode<InternalMockNetwork.MockNode>

    @Before
    fun before() {
        mockNet = InternalMockNetwork(cordappsForAllNodes = cordappsForPackages("net.corda.testing.contracts"), threadPerNode = true)
        notaryParty = DevIdentityGenerator.generateDistributedNotarySingularIdentity(listOf(mockNet.baseDirectory(mockNet.nextNodeId)), notaryName)
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
                .addCommand(dummyCommand(node.services.myInfo.singleIdentity().owningKey))
        val firstSpendTx = node.services.signInitialTransaction(firstTxBuilder)
        val secondSpendBuilder = TransactionBuilder(notaryParty).withItems(inputState).run {
            val dummyState = DummyContract.SingleOwnerState(0, node.info.singleIdentity())
            addOutputState(dummyState, DummyContract.PROGRAM_ID)
            addCommand(dummyCommand(node.services.myInfo.singleIdentity().owningKey))
            this
        }
        val secondSpendTx = node.services.signInitialTransaction(secondSpendBuilder)

        val firstSpend = node.services.startFlow(NotaryFlow.Client(firstSpendTx)).resultFuture
        val secondSpend = node.services.startFlow(NotaryFlow.Client(secondSpendTx)).resultFuture
        firstSpend.getOrThrow()

        val ex = assertFailsWith(NotaryException::class) { secondSpend.getOrThrow() }
        val error = ex.error as NotaryError.Conflict
        assertEquals(error.txId, secondSpendTx.id)
    }

    @Test
    fun `notarisations are idempotent`() {
        val inputState = issueState(node, notaryParty)

        val txBuilder = TransactionBuilder(notaryParty)
                .addInputState(inputState)
                .addCommand(dummyCommand(node.services.myInfo.singleIdentity().owningKey))
        val spendTx = node.services.signInitialTransaction(txBuilder)

        val futures = (1..10).map {
            node.services.startFlow(NotaryFlow.Client(spendTx)).resultFuture
        }
        val signatures = futures.transpose().get().flatten()

        fun checkSignature(signature: TransactionSignature) {
            signature.verify(spendTx.id)
            assertEquals(notaryParty.owningKey, signature.by)
        }
        signatures.forEach { checkSignature(it) }
    }

    @Test
    fun `notarise issue tx with time-window`() {
        val txBuilder = DummyContract.generateInitial(Random().nextInt(), notaryParty, node.info.singleIdentity().ref(0))
                .setTimeWindow(node.services.clock.instant(), 30.seconds)

        val issueTx = node.services.signInitialTransaction(txBuilder)
        val signature = node.services.startFlow(NotaryFlow.Client(issueTx)).resultFuture.getOrThrow(5.seconds)
        signature.first().verify(issueTx.id)
    }

    @Test
    fun `should re-sign a transaction with an expired time-window`() {
        val stx = run {
            val inputState = issueState(node, notaryParty)
            val tx = TransactionBuilder(notaryParty)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(nodeParty.owningKey))
                    .setTimeWindow(node.services.clock.instant(), 30.seconds)
            node.services.signInitialTransaction(tx)
        }

        val sig1 = node.services.startFlow(NotaryFlow.Client(stx)).resultFuture.get().first()
        assertEquals(sig1.by, notaryParty.owningKey)
        assertTrue(sig1.isValid(stx.id))

        mockNet.nodes.forEach {
            val nodeClock = (it.started!!.services.clock as TestClock)
            nodeClock.advanceBy(Duration.ofDays(1))
        }

        val sig2 = node.services.startFlow(NotaryFlow.Client(stx)).resultFuture.get().first()
        assertEquals(sig2.by, notaryParty.owningKey)
    }

    @Test
    fun `should report error for transaction with an invalid time-window`() {
        val stx = run {
            val inputState = issueState(node, notaryParty)
            val tx = TransactionBuilder(notaryParty)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(nodeParty.owningKey))
                    .setTimeWindow(node.services.clock.instant().plusSeconds(3600), 30.seconds)
            node.services.signInitialTransaction(tx)
        }
        val future = node.services.startFlow(NotaryFlow.Client(stx)).resultFuture

        val ex = assertFailsWith(NotaryException::class) { future.getOrThrow() }
        Assertions.assertThat(ex.error).isInstanceOf(NotaryError.TimeWindowInvalid::class.java)
    }

    @Test
    fun `requests are processed in batches`() {
        val notaryService = notaryNode.notaryService as MySQLNotaryService
        val transactionCount = 100
        val results = notaryNode.services.startFlow(RequestGenerationFlow(notaryService, transactionCount)).resultFuture.get()
        assertEquals(transactionCount, results.size)
        require(results.all { it === Result.Success })
    }

    @Test
    fun `batches with too many input states are processed in chunks`() {
        val notaryService = notaryNode.notaryService as MySQLNotaryService
        val transactionCount = 10
        val results = notaryNode.services.startFlow(RequestGenerationFlow(notaryService, transactionCount, 50)).resultFuture.get()
        assertEquals(transactionCount, results.size)
        require(results.all { it === Result.Success })
    }

    private class RequestGenerationFlow(
            private val service: MySQLNotaryService,
            private val transactionCount: Int,
            private val inputStateCount: Int? = null
    ) : FlowLogic<List<Result>>() {
        private val publicKeyGeneratorSingle = Generator.pure(generateKeyPair().public)
        private val partyGenerator: Generator<Party> = Generator.int().combine(publicKeyGeneratorSingle) { n, key ->
            Party(CordaX500Name(organisation = "Party$n", locality = "London", country = "GB"), key)
        }
        private val txIdGenerator = Generator.bytes(32).map { SecureHash.sha256(it) }
        private val stateRefGenerator = txIdGenerator.combine(Generator.intRange(0, 10)) { id, pos -> StateRef(id, pos) }
        private val random = SplittableRandom()

        @Suspendable
        override fun call(): List<Result> {
            val futures = mutableListOf<CordaFuture<Result>>()
            var requestSignature: NotarisationRequestSignature? = null
            for (i in 1..transactionCount) {
                val txId: SecureHash = txIdGenerator.generateOrFail(random)
                val callerParty = partyGenerator.generateOrFail(random)
                val inputGenerator = if (inputStateCount == null) {
                    Generator.replicatePoisson(4.0, stateRefGenerator, true)
                } else {
                    Generator.replicate(inputStateCount, stateRefGenerator)
                }
                val inputs = inputGenerator.generateOrFail(random)
                if (requestSignature == null || random.nextInt(10) < 2) {
                    requestSignature = NotarisationRequest(inputs, txId).generateSignature(serviceHub)
                }
                futures += AsyncCFTNotaryService.CommitOperation(
                        service,
                        inputs,
                        txId,
                        callerParty,
                        requestSignature,
                        null).execute()
            }
            return futures.transpose().get()
        }
    }

    private fun issueState(node: StartedNode<InternalMockNetwork.MockNode>, notary: Party): StateAndRef<*> {
        return node.database.transaction {
            val builder = DummyContract.generateInitial(Random().nextInt(), notary, node.info.singleIdentity().ref(0))
            val stx = node.services.signInitialTransaction(builder)
            node.services.recordTransactions(stx)
            StateAndRef(builder.outputStates().first(), StateRef(stx.id, 0))
        }
    }

    private fun createNotaryNode(): InternalMockNetwork.MockNode {
        val dataStoreProperties = makeInternalTestDataSourceProperties(configSupplier = { ConfigFactory.empty() }).apply {
            setProperty("autoCommit", "false")
        }
        return mockNet.createUnstartedNode(
                InternalMockNodeParameters(
                        legalName = notaryNodeName,
                        entropyRoot = BigInteger.valueOf(60L),
                        configOverrides = {
                            val notaryConfig = NotaryConfig(
                                    validating = false,
                                    mysql = MySQLConfiguration(dataStoreProperties, maxBatchSize = 10, maxBatchInputStates = 100)
                            )
                            doReturn(notaryConfig).whenever(it).notary
                        }
                )
        )
    }
}
