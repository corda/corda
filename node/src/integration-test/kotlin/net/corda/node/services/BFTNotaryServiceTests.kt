package net.corda.node.services

import com.google.common.net.HostAndPort
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.*
import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.node.services.ServiceInfo
import net.corda.flows.NotaryError
import net.corda.flows.NotaryException
import net.corda.flows.NotaryFlow
import net.corda.node.internal.AbstractNode
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.BFTNonValidatingNotaryService
import net.corda.node.services.transactions.minClusterSize
import net.corda.node.services.transactions.minCorrectReplicas
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.node.utilities.transaction
import net.corda.testing.node.MockNetwork
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Ignore
import org.junit.After
import org.junit.Test
import java.nio.file.Files
import kotlin.test.*

class BFTNotaryServiceTests {
    companion object {
        private val clusterName = X500Name("CN=BFT,O=R3,OU=corda,L=Zurich,C=CH")
        private val serviceType = BFTNonValidatingNotaryService.type
    }

    private val mockNet = MockNetwork()
    private val node = mockNet.createNode(advertisedServices = ServiceInfo(NetworkMapService.type))
    @After
    fun stopNodes() {
        mockNet.stopNodes()
    }

    private fun bftNotaryCluster(clusterSize: Int): Party {
        Files.deleteIfExists("config" / "currentView") // XXX: Make config object warn if this exists?
        val replicaIds = (0 until clusterSize)
        val party = ServiceIdentityGenerator.generateToDisk(
                replicaIds.map { mockNet.baseDirectory(mockNet.nextNodeId + it) },
                serviceType.id,
                clusterName)
        val bftNotaryService = ServiceInfo(serviceType, clusterName)
        val notaryClusterAddresses = replicaIds.map { HostAndPort.fromParts("localhost", 11000 + it * 10) }
        replicaIds.forEach { replicaId ->
            mockNet.createNode(
                    node.network.myAddress,
                    advertisedServices = bftNotaryService,
                    configOverrides = {
                        whenever(it.bftReplicaId).thenReturn(replicaId)
                        whenever(it.notaryClusterAddresses).thenReturn(notaryClusterAddresses)
                    })
        }
        return party
    }

    @Test
    @Ignore("Under investigation due to failure on TC build server")
    // TODO: Fix this then remove [BFTNotaryServiceLegacyTests]
    fun `detect double spend 1 faulty`() {
        detectDoubleSpend(1)
    }

    @Test
    @Ignore("Under investigation due to failure on TC build server")
    // TODO: Fix this then remove [BFTNotaryServiceLegacyTests]
    fun `detect double spend 2 faulty`() {
        detectDoubleSpend(2)
    }

    private fun detectDoubleSpend(faultyReplicas: Int) {
        val clusterSize = minClusterSize(faultyReplicas)
        val notary = bftNotaryCluster(clusterSize)
        node.run {
            val issueTx = signInitialTransaction(notary) {
                addOutputState(DummyContract.SingleOwnerState(owner = info.legalIdentity))
            }
            database.transaction {
                services.recordTransactions(issueTx)
            }
            val spendTxs = (1..10).map {
                signInitialTransaction(notary, true) {
                    addInputState(issueTx.tx.outRef<ContractState>(0))
                }
            }
            assertEquals(spendTxs.size, spendTxs.map { it.id }.distinct().size)
            val flows = spendTxs.map { NotaryFlow.Client(it) }
            val stateMachines = flows.map { services.startFlow(it) }
            mockNet.runNetwork()
            val results = stateMachines.map { ErrorOr.catch { it.resultFuture.getOrThrow() } }
            val successfulIndex = results.mapIndexedNotNull { index, result ->
                if (result.error == null) {
                    val signers = result.getOrThrow().map { it.by }
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
                if (result.error != null) {
                    val error = (result.error as NotaryException).error as NotaryError.Conflict
                    assertEquals(tx.id, error.txId)
                    val (stateRef, consumingTx) = error.conflict.verified().stateHistory.entries.single()
                    assertEquals(StateRef(issueTx.id, 0), stateRef)
                    assertEquals(spendTxs[successfulIndex].id, consumingTx.id)
                    assertEquals(0, consumingTx.inputIndex)
                    assertEquals(info.legalIdentity, consumingTx.requestingParty)
                }
            }
        }
    }
}

private fun AbstractNode.signInitialTransaction(
        notary: Party,
        makeUnique: Boolean = false,
        block: TransactionType.General.Builder.() -> Any?
) = services.signInitialTransaction(TransactionType.General.Builder(notary).apply {
    block()
    if (makeUnique) {
        addAttachment(SecureHash.randomSHA256())
    }
})
