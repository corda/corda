package net.corda.node.services

import net.corda.core.contracts.DummyContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.appendToCommonName
import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.DUMMY_CA
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.flows.NotaryError
import net.corda.flows.NotaryException
import net.corda.flows.NotaryFlow
import net.corda.node.internal.AbstractNode
import net.corda.node.internal.Node
import net.corda.node.services.transactions.BFTNonValidatingNotaryService
import net.corda.node.services.transactions.minCorrectReplicas
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.node.utilities.transaction
import net.corda.testing.node.NodeBasedTest
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Old tests for the BFT notary, to be removed once [BFTNotaryServiceTests] functions again
class BFTNotaryServiceLegacyTests : NodeBasedTest() {
    @Test
    fun `detect double spend`() {
        val clusterName = X500Name("CN=BFT,O=R3,OU=corda,L=Zurich,C=CH")
        startBFTNotaryCluster(clusterName, 4, BFTNonValidatingNotaryService.type)
        val alice = startNode(ALICE.name).getOrThrow()
        val notaryParty = alice.netMapCache.getNotary(clusterName)!!
        val inputState = issueState(alice, notaryParty)
        val firstTxBuilder = TransactionType.General.Builder(notaryParty).withItems(inputState)
        val firstSpendTx = alice.services.signInitialTransaction(firstTxBuilder)
        alice.services.startFlow(NotaryFlow.Client(firstSpendTx)).resultFuture.getOrThrow()
        val secondSpendBuilder = TransactionType.General.Builder(notaryParty).withItems(inputState).also {
            it.addOutputState(DummyContract.SingleOwnerState(0, alice.info.legalIdentity))
        }
        val secondSpendTx = alice.services.signInitialTransaction(secondSpendBuilder)
        val secondSpend = alice.services.startFlow(NotaryFlow.Client(secondSpendTx))
        val ex = assertFailsWith(NotaryException::class) {
            secondSpend.resultFuture.getOrThrow()
        }
        val error = ex.error as NotaryError.Conflict
        assertEquals(error.txId, secondSpendTx.id)
    }

    private fun issueState(node: AbstractNode, notary: Party) = node.run {
        database.transaction {
            val builder = DummyContract.generateInitial(Random().nextInt(), notary, info.legalIdentity.ref(0))
            val stx = services.signInitialTransaction(builder)
            services.recordTransactions(listOf(stx))
            StateAndRef(builder.outputStates().first(), StateRef(stx.id, 0))
        }
    }

    private fun startBFTNotaryCluster(clusterName: X500Name,
                                      clusterSize: Int,
                                      serviceType: ServiceType) {
        require(clusterSize > 0)
        val replicaNames = (0 until clusterSize).map { DUMMY_NOTARY.name.appendToCommonName(" $it") }
        ServiceIdentityGenerator.generateToDisk(
                replicaNames.map { baseDirectory(it) },
                serviceType.id,
                clusterName,
                minCorrectReplicas(clusterSize))
        val serviceInfo = ServiceInfo(serviceType, clusterName)
        val notaryClusterAddresses = (0 until clusterSize).map { "localhost:${11000 + it * 10}" }
        (0 until clusterSize).forEach {
            startNode(
                    replicaNames[it],
                    advertisedServices = setOf(serviceInfo),
                    configOverrides = mapOf("bftReplicaId" to it, "notaryClusterAddresses" to notaryClusterAddresses)
            ).getOrThrow()
        }
    }
}
