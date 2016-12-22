package net.corda.node.services

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
import net.corda.core.node.services.ServiceInfo
import net.corda.flows.NotaryError
import net.corda.flows.NotaryException
import net.corda.flows.NotaryFlow
import net.corda.node.internal.AbstractNode
import net.corda.node.internal.Node
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.getFreeLocalPorts
import net.corda.testing.node.NodeBasedTest
import org.junit.Test
import java.security.KeyPair
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RaftNotaryServiceTests : NodeBasedTest() {
    private val notaryName = "RAFT Notary Service"
    private val clusterSize = 3

    @Test
    fun `detect double spend`() {
        val (masterNode, alice) = Futures.allAsList(createNotaryCluster(), startNode("Alice")).getOrThrow()

        val notaryParty = alice.netMapCache.getNotary(notaryName)!!

        val stx = run {
            val notaryNodeKeyPair = databaseTransaction(masterNode.database) {
                masterNode.services.notaryIdentityKey
            }
            val inputState = issueState(alice, notaryParty, notaryNodeKeyPair)
            val tx = TransactionType.General.Builder(notaryParty).withItems(inputState)
            val aliceKey = databaseTransaction(alice.database) {
                alice.services.legalIdentityKey
            }
            tx.signWith(aliceKey)
            tx.toSignedTransaction(false)
        }

        val buildFlow = { NotaryFlow.Client(stx) }

        val firstSpend = alice.services.startFlow(buildFlow())
        firstSpend.resultFuture.getOrThrow()

        val secondSpend = alice.services.startFlow(buildFlow())

        val ex = assertFailsWith(NotaryException::class) { secondSpend.resultFuture.getOrThrow() }
        val error = ex.error as NotaryError.Conflict
        assertEquals(error.tx, stx.tx)
    }

    private fun createNotaryCluster(): ListenableFuture<Node> {
        val notaryService = ServiceInfo(RaftValidatingNotaryService.type, notaryName)
        val notaryAddresses = getFreeLocalPorts("localhost", clusterSize).map { it.toString() }
        ServiceIdentityGenerator.generateToDisk(
            (0 until clusterSize).map { tempFolder.root.toPath() / "Notary$it" },
            notaryService.type.id,
            notaryName)

        val masterNode = startNode(
            "Notary0",
            advertisedServices = setOf(notaryService),
            configOverrides = mapOf("notaryNodeAddress" to notaryAddresses[0]))

        val remainingNodes = (1 until clusterSize).map {
            startNode(
                "Notary$it",
                advertisedServices = setOf(notaryService),
                configOverrides = mapOf(
                    "notaryNodeAddress" to notaryAddresses[it],
                    "notaryClusterAddresses" to listOf(notaryAddresses[0])))
        }

        return Futures.allAsList(remainingNodes).flatMap { masterNode }
    }

    private fun issueState(node: AbstractNode, notary: Party, notaryKey: KeyPair): StateAndRef<*> {
        return databaseTransaction(node.database) {
            val tx = DummyContract.generateInitial(node.info.legalIdentity.ref(0), Random().nextInt(), notary)
            tx.signWith(node.services.legalIdentityKey)
            tx.signWith(notaryKey)
            val stx = tx.toSignedTransaction()
            node.services.recordTransactions(listOf(stx))
            StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
        }
    }
}