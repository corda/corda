package net.corda.bank

import net.corda.bank.api.BankOfCordaClientApi
import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.core.utilities.getOrThrow
import net.corda.testing.BOC
import net.corda.testing.IntegrationTest
import net.corda.testing.driver.driver
import org.junit.Test
import kotlin.test.assertTrue

class BankOfCordaHttpAPITest : IntegrationTest() {
    @Test
    fun `issuer flow via Http`() {
        driver(extraCordappPackagesToScan = listOf("net.corda.finance"), isDebug = true) {
            val (bocNode) = listOf(
                    startNode(providedName = BOC.name),
                    startNode(providedName = BIGCORP_LEGAL_NAME)
            ).map { it.getOrThrow() }
            val bocApiAddress = startWebserver(bocNode).getOrThrow().listenAddress
            val issueRequestParams = IssueRequestParams(1000, "USD", BIGCORP_LEGAL_NAME, "1", BOC.name, defaultNotaryIdentity.name)
            assertTrue(BankOfCordaClientApi(bocApiAddress).requestWebIssue(issueRequestParams))
        }
    }
}
