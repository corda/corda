package net.corda.node.services

import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.map
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.chooseIdentity
import net.corda.testing.contracts.DummyContract
import net.corda.testing.driver.driver
import net.corda.testing.core.dummyCommand
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.InProcess
import net.corda.testing.node.ClusterSpec
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.startFlow
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RaftNotaryServiceTests {
    private val notaryName = CordaX500Name("RAFT Notary Service", "London", "GB")

    @Test
    fun `detect double spend`() {
        driver(DriverParameters(
                startNodesInProcess = true,
                extraCordappPackagesToScan = listOf("net.corda.testing.contracts"),
                notarySpecs = listOf(NotarySpec(notaryName, cluster = ClusterSpec.Raft(clusterSize = 3)))
        )) {
            val bankA = startNode(providedName = DUMMY_BANK_A_NAME).map { (it as InProcess) }.getOrThrow()
            val inputState = issueState(bankA, defaultNotaryIdentity)

            val firstTxBuilder = TransactionBuilder(defaultNotaryIdentity)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(bankA.services.myInfo.chooseIdentity().owningKey))
            val firstSpendTx = bankA.services.signInitialTransaction(firstTxBuilder)

            val firstSpend = bankA.services.startFlow(NotaryFlow.Client(firstSpendTx))
            firstSpend.getOrThrow()

            val secondSpendBuilder = TransactionBuilder(defaultNotaryIdentity).withItems(inputState).run {
                val dummyState = DummyContract.SingleOwnerState(0, bankA.services.myInfo.chooseIdentity())
                addOutputState(dummyState, DummyContract.PROGRAM_ID)
                addCommand(dummyCommand(bankA.services.myInfo.chooseIdentity().owningKey))
                this
            }
            val secondSpendTx = bankA.services.signInitialTransaction(secondSpendBuilder)
            val secondSpend = bankA.services.startFlow(NotaryFlow.Client(secondSpendTx))

            val ex = assertFailsWith(NotaryException::class) { secondSpend.getOrThrow() }
            val error = ex.error as NotaryError.Conflict
            assertEquals(error.txId, secondSpendTx.id)
        }
    }

    private fun issueState(nodeHandle: InProcess, notary: Party): StateAndRef<*> {
        return nodeHandle.database.transaction {

            val builder = DummyContract.generateInitial(Random().nextInt(), notary, nodeHandle.services.myInfo.chooseIdentity().ref(0))
            val stx = nodeHandle.services.signInitialTransaction(builder)
            nodeHandle.services.recordTransactions(stx)
            StateAndRef(builder.outputStates().first(), StateRef(stx.id, 0))
        }
    }
}
