package net.corda.bank

import net.corda.bank.api.BankOfCordaClientApi
import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.core.internal.concurrent.transpose
import net.corda.core.utilities.getOrThrow
import net.corda.testing.BOC
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.driver.driver
import org.junit.Test
import kotlin.test.assertTrue

class BankOfCordaHttpAPITest {
    @Test
    fun `issuer flow via Http`() {
        driver(isDebug = true, extraCordappPackagesToScan = listOf("net.corda.finance")) {
            val (_, bocNode) = listOf(
                    startNotaryNode(providedName = DUMMY_NOTARY.name),
                    startNode(providedName = BOC.name),
                    startNode(providedName = BIGCORP_LEGAL_NAME)
            ).transpose().getOrThrow()
            val bocApiAddress = startWebserver(bocNode).getOrThrow().listenAddress
            val issueRequestParams = IssueRequestParams(1000, "USD", BIGCORP_LEGAL_NAME, "1", BOC.name, DUMMY_NOTARY.name)
            assertTrue(BankOfCordaClientApi(bocApiAddress).requestWebIssue(issueRequestParams))
        }
    }
}
