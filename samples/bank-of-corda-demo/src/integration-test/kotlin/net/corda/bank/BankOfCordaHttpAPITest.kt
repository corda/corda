package net.corda.bank

import net.corda.bank.api.BankOfCordaClientApi
import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.BOC
import net.corda.testing.driver.driver
import org.junit.Test
import kotlin.test.assertTrue

class BankOfCordaHttpAPITest {
    @Test
    fun `issuer flow via Http`() {
        driver(dsl = {
            val bigCorpNodeFuture = startNode(providedName = BIGCORP_LEGAL_NAME)
            val nodeBankOfCordaFuture = startNode(providedName = BOC.name, advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)))
            val (nodeBankOfCorda) = listOf(nodeBankOfCordaFuture, bigCorpNodeFuture).map { it.getOrThrow() }
            val nodeBankOfCordaApiAddr = startWebserver(nodeBankOfCorda).getOrThrow().listenAddress
            assertTrue(BankOfCordaClientApi(nodeBankOfCordaApiAddr).requestWebIssue(IssueRequestParams(1000, "USD", BIGCORP_LEGAL_NAME, "1", BOC.name, BOC.name)))
        }, isDebug = true)
    }
}
