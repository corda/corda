package net.corda.node.services

import net.corda.core.contracts.DummyContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.appendToCommonName
import net.corda.core.crypto.commonName
import net.corda.core.div
import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.utilities.ALICE
import net.corda.flows.NotaryError
import net.corda.flows.NotaryException
import net.corda.flows.NotaryFlow
import net.corda.node.internal.AbstractNode
import net.corda.node.internal.Node
import net.corda.node.services.transactions.BFTNonValidatingNotaryService
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.node.utilities.transaction
import net.corda.testing.node.NodeBasedTest
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BFTNotaryServiceTests : NodeBasedTest() {
    private companion object {
        val notaryCommonName = X500Name("CN=BFT Notary Server,O=R3,OU=corda,L=Zurich,C=CH")

        fun buildNodeName(it: Int, notaryName: X500Name): X500Name {
            return notaryName.appendToCommonName("-$it")
        }
    }

    @Test
    fun `detect double spend`() {
        val masterNode = startBFTNotaryCluster(notaryCommonName, 4, BFTNonValidatingNotaryService.type).first()
        val alice = startNode(ALICE.name).getOrThrow()

        val notaryParty = alice.netMapCache.getNotary(notaryCommonName)!!

        val inputState = issueState(alice, notaryParty)

        val firstTxBuilder = TransactionType.General.Builder(notaryParty).withItems(inputState)
        val firstSpendTx = alice.services.signInitialTransaction(firstTxBuilder)

        val firstSpend = alice.services.startFlow(NotaryFlow.Client(firstSpendTx))
        firstSpend.resultFuture.getOrThrow()

        val secondSpendBuilder = TransactionType.General.Builder(notaryParty).withItems(inputState).run {
            val dummyState = DummyContract.SingleOwnerState(0, alice.info.legalIdentity)
            addOutputState(dummyState)
            this
        }
        val secondSpendTx = alice.services.signInitialTransaction(secondSpendBuilder)
        val secondSpend = alice.services.startFlow(NotaryFlow.Client(secondSpendTx))

        val ex = assertFailsWith(NotaryException::class) { secondSpend.resultFuture.getOrThrow() }
        val error = ex.error as NotaryError.Conflict
        assertEquals(error.txId, secondSpendTx.id)
    }

    private fun issueState(node: AbstractNode, notary: Party): StateAndRef<*> {
        return node.database.transaction {
            val builder = DummyContract.generateInitial(Random().nextInt(), notary, node.info.legalIdentity.ref(0))
            val stx = node.services.signInitialTransaction(builder)
            node.services.recordTransactions(listOf(stx))
            StateAndRef(builder.outputStates().first(), StateRef(stx.id, 0))
        }
    }

    private fun startBFTNotaryCluster(notaryName: X500Name,
                                      clusterSize: Int,
                                      serviceType: ServiceType): List<Node> {
        require(clusterSize > 0)
        val quorum = (2 * clusterSize + 1) / 3
        ServiceIdentityGenerator.generateToDisk(
                (0 until clusterSize).map { tempFolder.root.toPath() / "${notaryName.commonName}-$it" },
                serviceType.id,
                notaryName,
                quorum)

        val serviceInfo = ServiceInfo(serviceType, notaryName)
        val nodes = (0 until clusterSize).map {
            startNode(
                    buildNodeName(it, notaryName),
                    advertisedServices = setOf(serviceInfo),
                    configOverrides = mapOf("notaryNodeId" to it)
            ).getOrThrow()
        }

        return nodes
    }
}
