package net.corda.irs

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.Futures
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.irs.api.NodeInterestRates
import net.corda.irs.utilities.postJson
import net.corda.irs.utilities.putJson
import net.corda.irs.utilities.uploadFile
import net.corda.node.driver.driver
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.IntegrationTestCategory
import org.apache.commons.io.IOUtils
import org.junit.Test
import java.net.URL

class IRSDemoTest : IntegrationTestCategory {
    @Test
    fun `runs IRS demo`() {
        driver(dsl = {
            val (controller, nodeA, nodeB) = Futures.allAsList(
                    startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.type), ServiceInfo(NodeInterestRates.type))),
                    startNode("Bank A"),
                    startNode("Bank B")
            ).getOrThrow()
            runUploadRates(controller.configuration.webAddress)
            runTrade(nodeA.configuration.webAddress)
            runDateChange(nodeB.configuration.webAddress)
        }, useTestClock = true, isDebug = true)
    }
}

private fun runDateChange(nodeAddr: HostAndPort) {
    val url = URL("http://$nodeAddr/api/irs/demodate")
    assert(putJson(url, "\"2017-06-05\""))
}

private fun runTrade(nodeAddr: HostAndPort) {
    val fileContents = IOUtils.toString(Thread.currentThread().contextClassLoader.getResourceAsStream("example-irs-trade.json"))
    val tradeFile = fileContents.replace("tradeXXX", "trade1")
    val url = URL("http://$nodeAddr/api/irs/deals")
    assert(postJson(url, tradeFile))
}

private fun runUploadRates(host: HostAndPort) {
    val fileContents = IOUtils.toString(Thread.currentThread().contextClassLoader.getResourceAsStream("example.rates.txt"))
    val url = URL("http://$host/upload/interest-rates")
    assert(uploadFile(url, fileContents))
}
