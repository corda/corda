package net.corda.bank

import net.corda.bank.api.BankOfCordaClientApi
import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.driver
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.BOC
import org.junit.Test
import java.util.concurrent.CompletableFuture.allOf
import kotlin.test.assertTrue

class BankOfCordaHttpAPITest {
    @Test
    fun `issuer flow via Http`() {
        driver(dsl = {
            val nodeBOCFuture = startNode(providedName = BOC.name, advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type))).toCompletableFuture()
            val nodeBigCorpFuture = startNode(providedName = BIGCORP_LEGAL_NAME).toCompletableFuture()
            allOf(nodeBOCFuture, nodeBigCorpFuture).getOrThrow()
            val (nodeBankOfCorda) = listOf(nodeBOCFuture, nodeBigCorpFuture).map { it.getOrThrow() }
            val anonymous = false
            val nodeBankOfCordaApiAddr = startWebserver(nodeBankOfCorda).getOrThrow().listenAddress
            assertTrue(BankOfCordaClientApi(nodeBankOfCordaApiAddr).requestWebIssue(IssueRequestParams(1000, "USD", BIGCORP_LEGAL_NAME, "1", BOC.name, BOC.name, anonymous)))
        }, isDebug = true)
    }
}
