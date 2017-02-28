package net.corda.node.services

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.DummyContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.Party
import net.corda.core.div
import net.corda.core.flatMap
import net.corda.core.getOrThrow
import net.corda.core.map
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.flows.NotaryError
import net.corda.flows.NotaryException
import net.corda.flows.NotaryFlow
import net.corda.node.internal.AbstractNode
import net.corda.node.internal.Node
import net.corda.node.services.transactions.BFTNonValidatingNotaryService
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.node.NodeBasedTest
import org.junit.Test
import java.security.KeyPair
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BFTNotaryServiceTests : NodeBasedTest() {
    private val notaryName = "BFT Notary Server"

    @Test
    fun `detect double spend`() {
        val (masterNode, alice) = Futures.allAsList(
                startBFTNotaryCluster(notaryName, 4, BFTNonValidatingNotaryService.type).map { it.first() },
                startNode("Alice")
        ).getOrThrow()

        val notaryParty = alice.netMapCache.getNotary(notaryName)!!
        val notaryNodeKeyPair = databaseTransaction(masterNode.database) { masterNode.services.notaryIdentityKey }
        val aliceKey = databaseTransaction(alice.database) { alice.services.legalIdentityKey }

        val inputState = issueState(alice, notaryParty, notaryNodeKeyPair)

        val firstSpendTx = TransactionType.General.Builder(notaryParty).withItems(inputState).run {
            signWith(aliceKey)
            toSignedTransaction(false)
        }

        val firstSpend = alice.services.startFlow(NotaryFlow.Client(firstSpendTx))
        firstSpend.resultFuture.getOrThrow()

        val secondSpendTx = TransactionType.General.Builder(notaryParty).withItems(inputState).run {
            val dummyState = DummyContract.SingleOwnerState(0, alice.info.legalIdentity.owningKey)
            addOutputState(dummyState)
            signWith(aliceKey)
            toSignedTransaction(false)
        }
        val secondSpend = alice.services.startFlow(NotaryFlow.Client(secondSpendTx))

        val ex = assertFailsWith(NotaryException::class) { secondSpend.resultFuture.getOrThrow() }
        val error = ex.error as NotaryError.Conflict
        assertEquals(error.txId, secondSpendTx.id)
    }

    private fun issueState(node: AbstractNode, notary: Party, notaryKey: KeyPair): StateAndRef<*> {
        return databaseTransaction(node.database) {
            val tx = DummyContract.generateInitial(Random().nextInt(), notary, node.info.legalIdentity.ref(0))
            tx.signWith(node.services.legalIdentityKey)
            tx.signWith(notaryKey)
            val stx = tx.toSignedTransaction()
            node.services.recordTransactions(listOf(stx))
            StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
        }
    }

    private fun startBFTNotaryCluster(notaryName: String,
                                      clusterSize: Int,
                                      serviceType: ServiceType): ListenableFuture<List<Node>> {
        ServiceIdentityGenerator.generateToDisk(
                (0 until clusterSize).map { tempFolder.root.toPath() / "$notaryName-$it" },
                serviceType.id,
                notaryName)

        val serviceInfo = ServiceInfo(serviceType, notaryName)

        // These ports are hardcoded in the bft smart config
        val nodePorts = listOf(11000, 11010, 11020, 11030, 11040, 11050)
        val nodeAddresses = nodePorts.take(clusterSize).map { HostAndPort.fromParts("localhost", it).toString() }

        val masterNodeFuture = startNode(
                "$notaryName-0",
                advertisedServices = setOf(serviceInfo),
                configOverrides = mapOf("notaryNodeAddress" to nodeAddresses[0],
                        "notaryClusterAddresses" to nodeAddresses))

        val remainingNodesFutures = (1 until clusterSize).map {
            Thread.sleep(1000) // BFT smart replicas have to be started in order
            startNode(
                    "$notaryName-$it",
                    advertisedServices = setOf(serviceInfo),
                    configOverrides = mapOf(
                            "notaryNodeAddress" to nodeAddresses[it],
                            "notaryClusterAddresses" to nodeAddresses))
        }

        return Futures.allAsList(remainingNodesFutures).flatMap { remainingNodes ->
            masterNodeFuture.map { masterNode -> listOf(masterNode) + remainingNodes }
        }
    }
}