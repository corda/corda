package net.corda.notary.experimental.raft

import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.map
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyContract.SingleOwnerState
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.InProcess
import net.corda.testing.driver.driver
import net.corda.testing.node.ClusterSpec
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import org.junit.Test
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RaftNotaryServiceTests {
    private val notaryName = CordaX500Name("RAFT Notary Service", "London", "GB")

    @Test(timeout=300_000)
	fun `detect double spend`() {
        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP),
                notarySpecs = listOf(NotarySpec(notaryName, cluster = ClusterSpec.Raft(clusterSize = 3)))
        )) {
            val bankA = startNode(providedName = DUMMY_BANK_A_NAME).map { (it as InProcess) }.getOrThrow()
            val inputState = issueState(bankA, defaultNotaryIdentity)

            val firstTxBuilder = DummyContract.move(inputState, bankA.services.myInfo.singleIdentity())
            val firstSpendTx = bankA.services.signInitialTransaction(firstTxBuilder)

            val firstSpend = bankA.startFlow(NotaryFlow.Client(firstSpendTx))
            firstSpend.getOrThrow()

            val secondSpendBuilder = DummyContract.move(inputState, bankA.services.myInfo.singleIdentity())
            val secondSpendTx = bankA.services.signInitialTransaction(secondSpendBuilder)
            val secondSpend = bankA.startFlow(NotaryFlow.Client(secondSpendTx))

            val ex = assertFailsWith(NotaryException::class) { secondSpend.getOrThrow() }
            val error = ex.error as NotaryError.Conflict
            assertEquals(error.txId, secondSpendTx.id)
        }
    }

    @Test(timeout=300_000)
	fun `notarise issue tx with time-window`() {
        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP),
                notarySpecs = listOf(NotarySpec(notaryName, cluster = ClusterSpec.Raft(clusterSize = 3)))
        )) {
            val bankA = startNode(providedName = DUMMY_BANK_A_NAME).map { (it as InProcess) }.getOrThrow()
            val builder = DummyContract.generateInitial(Random().nextInt(), defaultNotaryIdentity, bankA.services.myInfo.singleIdentity().ref(0))
                    .setTimeWindow(bankA.services.clock.instant(), 30.seconds)
            val issueTx = bankA.services.signInitialTransaction(builder)

            bankA.startFlow(NotaryFlow.Client(issueTx)).getOrThrow()
        }
    }

    private fun issueState(nodeHandle: InProcess, notary: Party): StateAndRef<SingleOwnerState> {
        val builder = DummyContract.generateInitial(Random().nextInt(), notary, nodeHandle.services.myInfo.singleIdentity().ref(0))
        val stx = nodeHandle.services.signInitialTransaction(builder)
        nodeHandle.services.recordTransactions(stx)
        return stx.coreTransaction.outRef(0)
    }
}
