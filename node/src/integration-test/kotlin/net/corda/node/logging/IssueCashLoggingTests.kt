package net.corda.node.logging

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.Permissions.Companion.all
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.*
import java.util.concurrent.CompletableFuture.allOf

class IssueCashLoggingTests {

    private val UNSIGNED_FINANCE_CORDAPP = cordappWithPackages("net.corda.finance")

    @Test
    fun `issuing and sending cash as payment do not result in duplicate insertion warnings`() {
        val user = User("mark", "dadada", setOf(all()))
        driver(DriverParameters(cordappsForAllNodes = setOf(UNSIGNED_FINANCE_CORDAPP))) {
            val nodeA = startNode(rpcUsers = listOf(user)).getOrThrow()
            val nodeB = startNode().getOrThrow()

            val amount = 1.DOLLARS
            val ref = OpaqueBytes.of(0)
            val recipient = nodeB.nodeInfo.legalIdentities[0]

            val futures = mutableListOf<CordaFuture<*>>()

            futures += nodeA.rpc.startFlow(::CashIssueAndPaymentFlow, amount, ref, recipient, false, defaultNotaryIdentity).returnValue

            allOf(*futures.map(CordaFuture<*>::toCompletableFuture).toTypedArray()).getOrThrow()

            val linesWithDuplicateInsertionWarningsInA = nodeA.logFile().useLines { lines -> lines.filter { line -> line.contains("Double insert") }.filter { line -> line.contains("not inserting the second time") }.toList() }
            val linesWithDuplicateInsertionWarningsInB = nodeB.logFile().useLines { lines -> lines.filter { line -> line.contains("Double insert") }.filter { line -> line.contains("not inserting the second time") }.toList() }

            assertThat(linesWithDuplicateInsertionWarningsInA).isEmpty()
            assertThat(linesWithDuplicateInsertionWarningsInB).isEmpty()
        }
    }

    @StartableByRPC
    class CashIssueAndPaymentFlow(val amount: Amount<Currency>,
                                  val issueRef: OpaqueBytes,
                                  val recipient: Party,
                                  val anonymous: Boolean,
                                  val notary: Party,
                                  progressTracker: ProgressTracker) : AbstractCashFlow<AbstractCashFlow.Result>(progressTracker) {

        constructor(amount: Amount<Currency>,
                    issueRef: OpaqueBytes,
                    recipient: Party,
                    anonymous: Boolean,
                    notary: Party) : this(amount, issueRef, recipient, anonymous, notary, tracker())

        constructor(request: IssueAndPaymentRequest) : this(request.amount, request.issueRef, request.recipient, request.anonymous, request.notary, tracker())

        @Suspendable
        override fun call(): Result {
            subFlow(CashIssueFlow(amount, issueRef, notary))
            return subFlow(CashPaymentFlow(amount, recipient, anonymous))
        }

        @CordaSerializable
        class IssueAndPaymentRequest(amount: Amount<Currency>, val issueRef: OpaqueBytes, val recipient: Party, val notary: Party, val anonymous: Boolean) : AbstractRequest(amount)
    }
}