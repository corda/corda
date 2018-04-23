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

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.deleteIfExists
import net.corda.core.internal.div
import net.corda.core.node.NotaryInfo
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
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNetwork.MockNode
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BFTNotaryServiceTests : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas("node_0", "node_1", "node_2", "node_3", "node_4", "node_5",
                "node_6", "node_7", "node_8", "node_9")
    }

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var notary: Party
    private lateinit var node: StartedNode<MockNode>

    @Before
    fun before() {
        mockNet = InternalMockNetwork(listOf("net.corda.testing.contracts"))
    }

    @After
    fun stopNodes() {
        mockNet.stopNodes()
    }

    private fun startBftClusterAndNode(clusterSize: Int, exposeRaces: Boolean = false) {
        (Paths.get("config") / "currentView").deleteIfExists() // XXX: Make config object warn if this exists?
        val replicaIds = (0 until clusterSize)

        notary = DevIdentityGenerator.generateDistributedNotaryCompositeIdentity(
                replicaIds.map { mockNet.baseDirectory(mockNet.nextNodeId + it) },
                CordaX500Name("BFT", "Zurich", "CH"))

        val networkParameters = NetworkParametersCopier(testNetworkParameters(listOf(NotaryInfo(notary, false))))

        val clusterAddresses = replicaIds.map { NetworkHostAndPort("localhost", 11000 + it * 10) }

        val nodes = replicaIds.map { replicaId ->
            mockNet.createUnstartedNode(InternalMockNodeParameters(configOverrides = {
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
                addOutputState(DummyContract.SingleOwnerState(owner = info.singleIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
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
                addOutputState(DummyContract.SingleOwnerState(owner = info.singleIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
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
                    val exception = result.exception as NotaryException
                    val error = exception.error as NotaryError.Conflict
                    assertEquals(tx.id, error.txId)
                    val (stateRef, cause) = error.consumedStates.entries.single()
                    assertEquals(StateRef(issueTx.id, 0), stateRef)
                    assertEquals(spendTxs[successfulIndex].id.sha256(), cause.hashOfTransactionId)
                }
            }
        }
    }

    private fun StartedNode<MockNode>.signInitialTransaction(notary: Party, block: TransactionBuilder.() -> Any?): SignedTransaction {
        return services.signInitialTransaction(
                TransactionBuilder(notary).apply {
                    addCommand(dummyCommand(services.myInfo.singleIdentity().owningKey))
                    block()
                }
        )
    }
}
