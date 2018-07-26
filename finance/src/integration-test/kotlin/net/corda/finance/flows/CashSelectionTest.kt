package net.corda.finance.flows

import net.corda.core.contracts.TransactionState
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.cash.selection.AbstractCashSelection
import net.corda.finance.issuedBy
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.InProcessImpl
import net.corda.testing.driver.internal.OutOfProcessImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CashSelectionTest {

    @Test
    fun `cash selection sees states added in the same transaction`() {
        driver(DriverParameters(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.finance"))) {
            val node = startNode().getOrThrow() as InProcessImpl
            val nodeIdentity = node.services.myInfo.singleIdentity()
            val issuer = nodeIdentity.ref(1)
            val coin = 1.DOLLARS.issuedBy(issuer)
            val exitedAmount = 1.DOLLARS
            val issuance = TransactionBuilder(null as Party?)
            issuance.addOutputState(TransactionState(Cash.State(coin, nodeIdentity), Cash.PROGRAM_ID, defaultNotaryIdentity))
            issuance.addCommand(Cash.Commands.Issue(), nodeIdentity.owningKey)

            //insert ans select in the same transaction
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
    }

    @Test
    fun `dont return extra coins if the selected amount has been reached`() {
        driver(DriverParameters(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.finance"))) {
            val node = startNode().getOrThrow() as InProcessImpl
            val nodeIdentity = node.services.myInfo.singleIdentity()

            val issuer = nodeIdentity.ref(1)

            val exitStates = node.database.transaction {
                //issue $1 coin twice
                repeat(2, {
                    val coin = 1.DOLLARS.issuedBy(issuer)
                    val issuance = TransactionBuilder(null as Party?)
                    issuance.addOutputState(TransactionState(Cash.State(coin, nodeIdentity), Cash.PROGRAM_ID, defaultNotaryIdentity))
                    issuance.addCommand(Cash.Commands.Issue(), nodeIdentity.owningKey)

                    val transaction = node.services.signInitialTransaction(issuance, nodeIdentity.owningKey)

                    node.services.recordTransactions(transaction)
                })

                val exitedAmount = 1.DOLLARS
                val builder = TransactionBuilder(notary = null)
                AbstractCashSelection
                        .getInstance { node.services.jdbcSession().metaData }
                        .unconsumedCashStatesForSpending(node.services, exitedAmount, setOf(issuer.party), builder.notary, builder.lockId, setOf(issuer.reference))
            }
            val returnedCoinsNumber = 1
            assertThat(exitStates.size).isEqualTo(returnedCoinsNumber)
        }
    }
}