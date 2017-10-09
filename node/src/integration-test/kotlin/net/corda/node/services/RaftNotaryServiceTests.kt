package net.corda.node.services

import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.transpose
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.testing.*
import net.corda.testing.contracts.DummyContract
import net.corda.testing.node.NodeBasedTest
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RaftNotaryServiceTests : NodeBasedTest(listOf("net.corda.testing.contracts")) {
    private val notaryName = CordaX500Name(RaftValidatingNotaryService.id, "RAFT Notary Service", "London", "GB")

    @Test
    fun `detect double spend`() {
        val (bankA) = listOf(
                startNode(DUMMY_BANK_A.name),
                startNotaryCluster(notaryName, 3).map { it.first() }
        ).transpose().getOrThrow()

        val notaryParty = bankA.services.networkMapCache.getNotary(notaryName)!!

        val inputState = issueState(bankA, notaryParty)

        val firstTxBuilder = TransactionBuilder(notaryParty)
                .addInputState(inputState)
                .addCommand(dummyCommand(bankA.services.myInfo.chooseIdentity().owningKey))
        val firstSpendTx = bankA.services.signInitialTransaction(firstTxBuilder)

        val firstSpend = bankA.services.startFlow(NotaryFlow.Client(firstSpendTx))
        firstSpend.resultFuture.getOrThrow()

        val secondSpendBuilder = TransactionBuilder(notaryParty).withItems(inputState).run {
            val dummyState = DummyContract.SingleOwnerState(0, bankA.info.chooseIdentity())
            addOutputState(dummyState, DummyContract.PROGRAM_ID)
            addCommand(dummyCommand(bankA.services.myInfo.chooseIdentity().owningKey))
            this
        }
        val secondSpendTx = bankA.services.signInitialTransaction(secondSpendBuilder)
        val secondSpend = bankA.services.startFlow(NotaryFlow.Client(secondSpendTx))

        val ex = assertFailsWith(NotaryException::class) { secondSpend.resultFuture.getOrThrow() }
        val error = ex.error as NotaryError.Conflict
        assertEquals(error.txId, secondSpendTx.id)
    }

    private fun issueState(node: StartedNode<*>, notary: Party): StateAndRef<*> {
        return node.database.transaction {
            val builder = DummyContract.generateInitial(Random().nextInt(), notary, node.info.chooseIdentity().ref(0))
            val stx = node.services.signInitialTransaction(builder)
            node.services.recordTransactions(stx)
            StateAndRef(builder.outputStates().first(), StateRef(stx.id, 0))
        }
    }
}
