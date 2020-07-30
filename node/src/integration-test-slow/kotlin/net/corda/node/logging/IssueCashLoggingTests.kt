package net.corda.node.logging

import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.div
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.node.services.Permissions.Companion.all
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File

class IssueCashLoggingTests {

    @Test(timeout=300_000)
	fun `issuing and sending cash as payment do not result in duplicate insertion warnings`() {
        val user = User("mark", "dadada", setOf(all()))
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS)) {
            val (nodeA, nodeB) = listOf(startNode(rpcUsers = listOf(user)),
                    startNode())
                    .transpose()
                    .getOrThrow()

            val amount = 1.DOLLARS
            val ref = OpaqueBytes.of(0)
            val recipient = nodeB.nodeInfo.legalIdentities[0]

            nodeA.rpc.startFlow(::CashIssueAndPaymentFlow, amount, ref, recipient, false, defaultNotaryIdentity).returnValue.getOrThrow()

            val linesWithDuplicateInsertionWarningsInA = nodeA.logFile().useLines { lines -> lines.filter(String::containsDuplicateInsertWarning).toList() }
            val linesWithDuplicateInsertionWarningsInB = nodeB.logFile().useLines { lines -> lines.filter(String::containsDuplicateInsertWarning).toList() }

            assertThat(linesWithDuplicateInsertionWarningsInA).isEmpty()
            assertThat(linesWithDuplicateInsertionWarningsInB).isEmpty()
        }
    }
}

private fun String.containsDuplicateInsertWarning(): Boolean = contains("Double insert") && contains("not inserting the second time")

fun NodeHandle.logFile(): File = (baseDirectory / "logs").toFile().walk().filter { it.name.startsWith("node-") && it.extension == "log" }.single()