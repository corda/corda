package net.corda.irs

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.Futures
import net.corda.core.crypto.Party
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.irs.api.NodeInterestRates
import net.corda.irs.contract.InterestRateSwap
import net.corda.irs.utilities.postJson
import net.corda.irs.utilities.putJson
import net.corda.irs.utilities.uploadFile
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.messaging.CordaRPCClient
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.IntegrationTestCategory
import org.apache.commons.io.IOUtils
import org.junit.Test
import rx.observables.BlockingObservable
import java.net.URL
import java.time.LocalDate

class IRSDemoTest : IntegrationTestCategory {
    val rpcUser = User("user", "password", emptySet())
    val currentDate = LocalDate.now()
    val futureDate = currentDate.plusMonths(6)

    @Test
    fun `runs IRS demo`() {
        driver(useTestClock = true, isDebug = true) {
            val (controller, nodeA, nodeB) = Futures.allAsList(
                    startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.type), ServiceInfo(NodeInterestRates.type))),
                    startNode("Bank A", rpcUsers = listOf(rpcUser)),
                    startNode("Bank B")
            ).getOrThrow()

            val (controllerAddr, nodeAAddr, nodeBAddr) = Futures.allAsList(
                    startWebserver(controller),
                    startWebserver(nodeA),
                    startWebserver(nodeB)
            ).getOrThrow()

            val nextFixingDates = getFixingDateObservable(nodeA.configuration)

            runUploadRates(controllerAddr)
            runTrade(nodeAAddr, nodeA.nodeInfo.legalIdentity, nodeB.nodeInfo.legalIdentity)
            // Wait until the initial trade and all scheduled fixings up to the current date have finished
            nextFixingDates.first { it == null || it > currentDate }

            runDateChange(nodeBAddr)
            nextFixingDates.first { it == null || it > futureDate }
        }
    }

    fun getFixingDateObservable(config: FullNodeConfiguration): BlockingObservable<LocalDate?> {
        val client = CordaRPCClient(config.rpcAddress!!)
        client.start("user", "password")
        val proxy = client.proxy()
        val vaultUpdates = proxy.vaultAndUpdates().second

        val fixingDates = vaultUpdates.map { update ->
            val irsStates = update.produced.map { it.state.data }.filterIsInstance<InterestRateSwap.State>()
            irsStates.mapNotNull { it.calculation.nextFixingDate() }.max()
        }.cache().toBlocking()

        return fixingDates
    }

    private fun runDateChange(nodeAddr: HostAndPort) {
        val url = URL("http://$nodeAddr/api/irs/demodate")
        assert(putJson(url, "\"$futureDate\""))
    }

    private fun runTrade(nodeAddr: HostAndPort, fixedRatePayer: Party, floatingRatePayer: Party) {
        val fileContents = IOUtils.toString(Thread.currentThread().contextClassLoader.getResourceAsStream("example-irs-trade.json"))
        val tradeFile = fileContents.replace("tradeXXX", "trade1")
                .replace("fixedRatePayerKey", fixedRatePayer.owningKey.toBase58String())
                .replace("floatingRatePayerKey", floatingRatePayer.owningKey.toBase58String())
        val url = URL("http://$nodeAddr/api/irs/deals")
        assert(postJson(url, tradeFile))
    }

    private fun runUploadRates(host: HostAndPort) {
        val fileContents = IOUtils.toString(Thread.currentThread().contextClassLoader.getResourceAsStream("example.rates.txt"))
        val url = URL("http://$host/upload/interest-rates")
        assert(uploadFile(url, fileContents))
    }
}
