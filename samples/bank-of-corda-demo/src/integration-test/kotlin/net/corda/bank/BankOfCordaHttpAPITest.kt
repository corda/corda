package net.corda.bank

import net.corda.bank.api.BankOfCordaClientApi
import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.core.utilities.getOrThrow
import net.corda.testing.BOC
import net.corda.testing.driver.driver
import net.corda.testing.notary
import org.junit.Test
import kotlin.test.assertTrue

class BankOfCordaHttpAPITest {
    @Test
    fun `issuer flow via Http`() {
        driver(extraCordappPackagesToScan = listOf("net.corda.finance"), dsl = {
            val bigCorpNodeFuture = startNode(providedName = BIGCORP_LEGAL_NAME)
            val nodeBankOfCordaFuture = startNotaryNode(BOC.name, validating = false)
            val (nodeBankOfCorda) = listOf(nodeBankOfCordaFuture, bigCorpNodeFuture).map { it.getOrThrow() }
            val nodeBankOfCordaApiAddr = startWebserver(nodeBankOfCorda).getOrThrow().listenAddress
            val notaryName = notary().node.nodeInfo.legalIdentities[1].name
            assertTrue(BankOfCordaClientApi(nodeBankOfCordaApiAddr).requestWebIssue(IssueRequestParams(1000, "USD", BIGCORP_LEGAL_NAME, "1", BOC.name, notaryName)))
        }, isDebug = true)
    }
}
