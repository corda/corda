package net.corda.node.services

import com.google.common.util.concurrent.Futures
import net.corda.core.contracts.DummyContract
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.Party
import net.corda.core.getOrThrow
import net.corda.core.map
import net.corda.flows.NotaryError
import net.corda.flows.NotaryException
import net.corda.flows.NotaryFlow
import net.corda.node.internal.AbstractNode
import net.corda.node.utilities.transaction
import net.corda.testing.MEGA_CORP
import net.corda.testing.node.NodeBasedTest
import org.junit.Test
import java.security.KeyPair
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RaftNotaryServiceTests : NodeBasedTest() {
    private val notaryName = "CN=RAFT Notary Service,O=R3,OU=corda,L=London,C=UK"

    @Test
    fun `detect double spend`() {
        val (masterNode, megaCorp) = Futures.allAsList(
                startNotaryCluster(notaryName, 3).map { it.first() },
                startNode(MEGA_CORP.name)
        ).getOrThrow()

        val notaryParty = megaCorp.netMapCache.getNotary(notaryName)!!
        val notaryNodeKeyPair = with(masterNode) { database.transaction { services.notaryIdentityKey } }
        val megaCorpKey = with(megaCorp) { database.transaction { services.legalIdentityKey } }

        val inputState = issueState(megaCorp, notaryParty, notaryNodeKeyPair)

        val firstSpendTx = TransactionType.General.Builder(notaryParty).withItems(inputState).run {
            signWith(megaCorpKey)
            toSignedTransaction(false)
        }
        val firstSpend = megaCorp.services.startFlow(NotaryFlow.Client(firstSpendTx))
        firstSpend.resultFuture.getOrThrow()

        val secondSpendTx = TransactionType.General.Builder(notaryParty).withItems(inputState).run {
            val dummyState = DummyContract.SingleOwnerState(0, megaCorp.info.legalIdentity.owningKey)
            addOutputState(dummyState)
            signWith(megaCorpKey)
            toSignedTransaction(false)
        }
        val secondSpend = megaCorp.services.startFlow(NotaryFlow.Client(secondSpendTx))

        val ex = assertFailsWith(NotaryException::class) { secondSpend.resultFuture.getOrThrow() }
        val error = ex.error as NotaryError.Conflict
        assertEquals(error.txId, secondSpendTx.id)
    }

    private fun issueState(node: AbstractNode, notary: Party, notaryKey: KeyPair): StateAndRef<*> {
        return node.database.transaction {
            val tx = DummyContract.generateInitial(Random().nextInt(), notary, node.info.legalIdentity.ref(0))
            tx.signWith(node.services.legalIdentityKey)
            tx.signWith(notaryKey)
            val stx = tx.toSignedTransaction()
            node.services.recordTransactions(listOf(stx))
            StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
        }
    }
}