package net.corda.finance.flows

import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.withoutIssuer
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.cash.selection.AbstractCashSelection
import net.corda.finance.contracts.getCashBalance
import net.corda.finance.issuedBy
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test

class CashSelectionTest {
    private val mockNet = InternalMockNetwork(cordappPackages = listOf("net.corda.finance"), threadPerNode = true)

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `unconsumed cash states`() {
        val issuerRef = OpaqueBytes.of(0)
        val issuedAmount = 1000.DOLLARS

        val node = mockNet.createNode()
        node.services.startFlow(CashIssueFlow(issuedAmount, issuerRef, mockNet.defaultNotaryIdentity)).resultFuture.getOrThrow()

        val availableBalance = node.services.getCashBalance(issuedAmount.token)

        assertThat(availableBalance).isEqualTo(issuedAmount)

        val exitedAmount = 300.DOLLARS
        node.services.startFlow(CashExitFlow(exitedAmount, issuerRef)).resultFuture.getOrThrow()

        val availableBalanceAfterExit = node.services.getCashBalance(issuedAmount.token)

        assertThat(availableBalanceAfterExit).isEqualTo(issuedAmount - exitedAmount)
    }

    @Test
    fun `cash selection sees states added in the same transaction`() {
        val node = mockNet.createNode()
        val nodeIdentity = node.services.myInfo.singleIdentity()
        val issuer = nodeIdentity.ref(1)
        val coin = 1.DOLLARS.issuedBy(issuer)
        val exitedAmount = 1.DOLLARS
        val issuance = TransactionBuilder(null as Party?)
        issuance.addOutputState(TransactionState(Cash.State(coin, nodeIdentity), Cash.PROGRAM_ID, mockNet.defaultNotaryIdentity))
        issuance.addCommand(Cash.Commands.Issue(), nodeIdentity.owningKey)

        // Insert and select in the same transaction
        val exitStates = node.database.transaction {
            val transaction = node.services.signInitialTransaction(issuance, nodeIdentity.owningKey)
            node.services.recordTransactions(transaction)

            val builder = TransactionBuilder(notary = null)
            AbstractCashSelection
                    .getInstance { node.services.jdbcSession().metaData }
                    .unconsumedCashStatesForSpending(node.services, exitedAmount, setOf(issuer.party), builder.notary, builder.lockId, setOf(issuer.reference))
        }
        val returnedCoinsNumber = 1
        assertThat(exitStates.size).isEqualTo(returnedCoinsNumber)
    }

    @Test
    fun `don't return extra coins if the selected amount has been reached`() {
        val node = mockNet.createNode()
        val nodeIdentity = node.services.myInfo.singleIdentity()

        val issuer = nodeIdentity.ref(1)

        val exitStates = node.database.transaction {
            //issue $1 coin twice
            repeat(2) {
                val coin = 1.DOLLARS.issuedBy(issuer)
                val issuance = TransactionBuilder(null as Party?)
                issuance.addOutputState(TransactionState(Cash.State(coin, nodeIdentity), Cash.PROGRAM_ID, mockNet.defaultNotaryIdentity))
                issuance.addCommand(Cash.Commands.Issue(), nodeIdentity.owningKey)

                val transaction = node.services.signInitialTransaction(issuance, nodeIdentity.owningKey)

                node.services.recordTransactions(transaction)
            }

            val exitedAmount = 1.DOLLARS
            val builder = TransactionBuilder(notary = null)
            AbstractCashSelection
                    .getInstance { node.services.jdbcSession().metaData }
                    .unconsumedCashStatesForSpending(node.services, exitedAmount, setOf(issuer.party), builder.notary, builder.lockId, setOf(issuer.reference))
        }
        val returnedCoinsNumber = 1
        assertThat(exitStates.size).isEqualTo(returnedCoinsNumber)
    }

    @Test
    fun `select cash states issued by single transaction and give change`() {
        val node = mockNet.createNode()
        val nodeIdentity = node.services.myInfo.singleIdentity()

        val coins = listOf(3.DOLLARS, 2.DOLLARS, 1.DOLLARS).map { it.issuedBy(nodeIdentity.ref(1)) }

        //create single transaction with 3 cash outputs
        val issuance = TransactionBuilder(null as Party?)
        coins.forEach {
            issuance.addOutputState(TransactionState(Cash.State(it, nodeIdentity), "net.corda.finance.contracts.asset.Cash", mockNet.defaultNotaryIdentity))
        }
        issuance.addCommand(Cash.Commands.Issue(), nodeIdentity.owningKey)

        val transaction = node.services.signInitialTransaction(issuance, nodeIdentity.owningKey)
        node.database.transaction {
            node.services.recordTransactions(transaction)
        }

        val issuedAmount = coins.reduce { sum, element -> sum + element }.withoutIssuer()

        val availableBalance = node.services.getCashBalance(issuedAmount.token)

        assertThat(availableBalance).isEqualTo(issuedAmount)

        val exitedAmount = 3.01.DOLLARS
        node.services.startFlow(CashExitFlow(exitedAmount, OpaqueBytes.of(1))).resultFuture.getOrThrow()

        val availableBalanceAfterExit = node.services.getCashBalance(issuedAmount.token)

        assertThat(availableBalanceAfterExit).isEqualTo(issuedAmount - exitedAmount)
    }
}
