package net.corda.bank

import com.google.common.util.concurrent.Futures
import net.corda.bank.api.BankOfCordaClientApi
import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.node.driver.driver
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.BOC
import org.junit.Test

class BankOfCordaHttpAPITest {
    @Test
    fun `issuer flow via Http`() {
        driver(dsl = {
            val (nodeBankOfCorda) = Futures.allAsList(
                    startNode(BOC.name, setOf(ServiceInfo(SimpleNotaryService.type))),
                    startNode(BIGCORP_LEGAL_NAME)
            ).getOrThrow()
            val nodeBankOfCordaApiAddr = startWebserver(nodeBankOfCorda).getOrThrow()
            assert(BankOfCordaClientApi(nodeBankOfCordaApiAddr).requestWebIssue(IssueRequestParams(1000, "USD", BIGCORP_LEGAL_NAME.toString(), "1", BOC.name.toString())))
        }, isDebug = true)
    }
}
