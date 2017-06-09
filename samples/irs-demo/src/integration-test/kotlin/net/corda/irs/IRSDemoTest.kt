package net.corda.irs

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.Futures
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.DUMMY_BANK_A
import net.corda.core.utilities.DUMMY_BANK_B
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.irs.api.NodeInterestRates
import net.corda.irs.contract.InterestRateSwap
import net.corda.irs.utilities.postJson
import net.corda.irs.utilities.putJson
import net.corda.irs.utilities.uploadFile
import net.corda.node.driver.driver
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.testing.IntegrationTestCategory
import net.corda.testing.http.HttpApi
import org.apache.commons.io.IOUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.observables.BlockingObservable
import java.net.URL
import java.time.LocalDate

class IRSDemoTest : IntegrationTestCategory {
    val rpcUser = User("user", "password", emptySet())
    val currentDate: LocalDate = LocalDate.now()
    val futureDate: LocalDate = currentDate.plusMonths(6)

    @Test
    fun `runs IRS demo`() {
        driver(useTestClock = true, isDebug = true) {
            val (controller, nodeA, nodeB) = Futures.allAsList(
                    startNode(DUMMY_NOTARY.name, setOf(ServiceInfo(SimpleNotaryService.type), ServiceInfo(NodeInterestRates.Oracle.type))),
                    startNode(DUMMY_BANK_A.name, rpcUsers = listOf(rpcUser)),
                    startNode(DUMMY_BANK_B.name)
            ).getOrThrow()

            val (controllerAddr, nodeAAddr, nodeBAddr) = Futures.allAsList(
                    startWebserver(controller),
                    startWebserver(nodeA),
                    startWebserver(nodeB)
            ).getOrThrow().map { it.listenAddress }

            val nextFixingDates = getFixingDateObservable(nodeA.configuration)
            val numADeals = getTradeCount(nodeAAddr)
            val numBDeals = getTradeCount(nodeBAddr)

            runUploadRates(controllerAddr)
            runTrade(nodeAAddr)

            assertThat(getTradeCount(nodeAAddr)).isEqualTo(numADeals + 1)
            assertThat(getTradeCount(nodeBAddr)).isEqualTo(numBDeals + 1)

            // Wait until the initial trade and all scheduled fixings up to the current date have finished
            nextFixingDates.first { it == null || it > currentDate }

            runDateChange(nodeBAddr)
            nextFixingDates.first { it == null || it > futureDate }
        }
    }

    fun getFixingDateObservable(config: FullNodeConfiguration): BlockingObservable<LocalDate?> {
        val client = CordaRPCClient(config.rpcAddress!!)
        val proxy = client.start("user", "password").proxy
        val vaultUpdates = proxy.vaultAndUpdates().second

        val fixingDates = vaultUpdates.map { update ->
            val irsStates = update.produced.map { it.state.data }.filterIsInstance<InterestRateSwap.State>()
            irsStates.mapNotNull { it.calculation.nextFixingDate() }.max()
        }.cache().toBlocking()

        return fixingDates
    }

    private fun runDateChange(nodeAddr: HostAndPort) {
        val url = URL("http://$nodeAddr/api/irs/demodate")
        assertThat(putJson(url, "\"$futureDate\"")).isTrue()
    }

    private fun runTrade(nodeAddr: HostAndPort) {
        val fileContents = loadResourceFile("net/corda/irs/simulation/example-irs-trade.json")
        val tradeFile = fileContents.replace("tradeXXX", "trade1")
        val url = URL("http://$nodeAddr/api/irs/deals")
        assertThat(postJson(url, tradeFile)).isTrue()
    }

    private fun runUploadRates(host: HostAndPort) {
        val fileContents = loadResourceFile("net/corda/irs/simulation/example.rates.txt")
        val url = URL("http://$host/upload/interest-rates")
        assertThat(uploadFile(url, fileContents)).isTrue()
    }

    private fun loadResourceFile(filename: String): String {
        return IOUtils.toString(Thread.currentThread().contextClassLoader.getResourceAsStream(filename), Charsets.UTF_8.name())
    }

    private fun getTradeCount(nodeAddr: HostAndPort): Int {
        val api = HttpApi.fromHostAndPort(nodeAddr, "api/irs")
        val deals = api.getJson<Array<*>>("deals")
        return deals.size
    }
}
