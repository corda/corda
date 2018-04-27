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
import com.typesafe.config.ConfigFactory
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NotaryInfo
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.node.services.config.MySQLConfiguration
import net.corda.node.services.config.NotaryConfig
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.internal.*
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.math.BigInteger
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    private lateinit var notaryParty: Party
    private lateinit var notaryNode: StartedNode<InternalMockNetwork.MockNode>

    @Before
    fun before() {
        mockNet = InternalMockNetwork(cordappPackages = listOf("net.corda.testing.contracts"))
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

        val firstSpend = node.services.startFlow(NotaryFlow.Client(firstSpendTx)).resultFuture
        mockNet.runNetwork()

        firstSpend.getOrThrow()

        val secondSpendBuilder = TransactionBuilder(notaryParty).withItems(inputState).run {
            val dummyState = DummyContract.SingleOwnerState(0, node.info.singleIdentity())
            addOutputState(dummyState, DummyContract.PROGRAM_ID)
            addCommand(dummyCommand(node.services.myInfo.singleIdentity().owningKey))
            this
        }
        val secondSpendTx = node.services.signInitialTransaction(secondSpendBuilder)
        val secondSpend = node.services.startFlow(NotaryFlow.Client(secondSpendTx)).resultFuture

        mockNet.runNetwork()

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

        val notarise = node.services.startFlow(NotaryFlow.Client(spendTx)).resultFuture
        mockNet.runNetwork()
        val signature = notarise.get().single()

        val notariseRetry = node.services.startFlow(NotaryFlow.Client(spendTx)).resultFuture
        mockNet.runNetwork()
        val signatureRetry = notariseRetry.get().single()

        fun checkSignature(signature: TransactionSignature) {
            signature.verify(spendTx.id)
            assertEquals(notaryParty.owningKey, signature.by)
        }

        checkSignature(signature)
        checkSignature(signatureRetry)
    }

    private fun createNotaryNode(): InternalMockNetwork.MockNode {
        val dataStoreProperties = makeTestDataSourceProperties(configSupplier = { _, _ ->ConfigFactory.empty() }, fallBackConfigSupplier = ::inMemoryH2DataSourceConfig).apply {
            setProperty("autoCommit", "false")
        }
        return mockNet.createUnstartedNode(
                InternalMockNodeParameters(
                        legalName = notaryNodeName,
                        entropyRoot = BigInteger.valueOf(60L),
                        configOverrides = {
                            val notaryConfig = NotaryConfig(validating = false, mysql = MySQLConfiguration(dataStoreProperties))
                            doReturn(notaryConfig).whenever(it).notary
                        }
                )
        )
    }

    private fun issueState(node: StartedNode<InternalMockNetwork.MockNode>, notary: Party): StateAndRef<*> {
        return node.database.transaction {
            val builder = DummyContract.generateInitial(Random().nextInt(), notary, node.info.singleIdentity().ref(0))
            val stx = node.services.signInitialTransaction(builder)
            node.services.recordTransactions(stx)
            StateAndRef(builder.outputStates().first(), StateRef(stx.id, 0))
        }
    }
}
