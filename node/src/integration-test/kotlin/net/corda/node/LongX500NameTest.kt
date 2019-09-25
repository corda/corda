package net.corda.node

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.CordaX500Name.Companion.MAX_LENGTH_COMMON_NAME
import net.corda.core.identity.CordaX500Name.Companion.MAX_LENGTH_LOCALITY
import net.corda.core.identity.CordaX500Name.Companion.MAX_LENGTH_ORGANISATION_UNIT
import net.corda.core.identity.CordaX500Name.Companion.MAX_LENGTH_STATE
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.junit.Test

class LongX500NameTest {

    companion object {
        val LONG_X500_NAME = CordaX500Name(
                commonName = "CommonName".padEnd(MAX_LENGTH_COMMON_NAME - 1, 'X'),
                organisationUnit = "OrganisationUnit".padEnd(MAX_LENGTH_ORGANISATION_UNIT - 1, 'X'),
                organisation = "Bob Plc",
                locality = "Locality".padEnd(MAX_LENGTH_LOCALITY - 1, 'X'),
                state = "State".padEnd(MAX_LENGTH_STATE - 1, 'X'),
                country = "IT")
    }

    @Test
    fun `corda supports nodes with a long x500 name`() {
        val user = User("u", "p", setOf(Permissions.all()))
        driver(DriverParameters(cordappsForAllNodes = FINANCE_CORDAPPS)) {
            val (alice, bob) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)),
                    startNode(providedName = LONG_X500_NAME, rpcUsers = listOf(user))).transpose().getOrThrow()

            // Move some cash around to exercise multiple codepaths.
            val issueRef = OpaqueBytes.of(0)
            alice.rpc.startFlow(::CashIssueAndPaymentFlow,
                    1000.DOLLARS,
                    issueRef,
                    bob.nodeInfo.singleIdentity(),
                    true,
                    defaultNotaryIdentity
            ).returnValue.getOrThrow()
            bob.rpc.startFlow(::CashIssueAndPaymentFlow,
                    1000.DOLLARS,
                    issueRef,
                    alice.nodeInfo.singleIdentity(),
                    true,
                    defaultNotaryIdentity
            ).returnValue.getOrThrow()
        }
    }
}
