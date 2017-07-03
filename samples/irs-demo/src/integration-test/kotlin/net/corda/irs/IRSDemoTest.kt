package net.corda.irs

import com.google.common.util.concurrent.Futures
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.toFuture
import net.corda.core.utilities.Authority
import net.corda.core.utilities.DUMMY_BANK_A
import net.corda.core.utilities.DUMMY_BANK_B
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.irs.api.NodeInterestRates
import net.corda.irs.contract.InterestRateSwap
import net.corda.irs.utilities.uploadFile
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.testing.IntegrationTestCategory
import net.corda.testing.driver.driver
import net.corda.testing.http.HttpApi
import org.apache.commons.io.IOUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.Observable
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class IRSDemoTest : IntegrationTestCategory {
    val rpcUser = User("user", "password", emptySet())
    val currentDate: LocalDate = LocalDate.now()
    val futureDate: LocalDate = currentDate.plusMonths(6)
    val maxWaitTime: Duration = Duration.of(60, ChronoUnit.SECONDS)

    @Test
    fun `runs IRS demo`() {
        driver(useTestClock = true, isDebug = true) {
            val (controller, nodeA, nodeB) = Futures.allAsList(
                    startNode(DUMMY_NOTARY.name, setOf(ServiceInfo(SimpleNotaryService.type), ServiceInfo(NodeInterestRates.Oracle.type))),
                    startNode(DUMMY_BANK_A.name, rpcUsers = listOf(rpcUser)),
                    startNode(DUMMY_BANK_B.name)
            ).getOrThrow()

            println("All nodes started")

            val (controllerAddr, nodeAAddr, nodeBAddr) = Futures.allAsList(
                    startWebserver(controller),
                    startWebserver(nodeA),
                    startWebserver(nodeB)
            ).getOrThrow().map { it.listenAddress }

            println("All webservers started")

            val (_, nodeAApi, nodeBApi) = listOf(controller, nodeA, nodeB).zip(listOf(controllerAddr, nodeAAddr, nodeBAddr)).map {
                val mapper = net.corda.jackson.JacksonSupport.createDefaultMapper(it.first.rpc)
                HttpApi.fromHostAndPort(it.second, "api/irs", mapper = mapper)
            }
            val nextFixingDates = getFixingDateObservable(nodeA.configuration)
            val numADeals = getTradeCount(nodeAApi)
            val numBDeals = getTradeCount(nodeBApi)

            runUploadRates(controllerAddr)
            runTrade(nodeAApi)

            assertThat(getTradeCount(nodeAApi)).isEqualTo(numADeals + 1)
            assertThat(getTradeCount(nodeBApi)).isEqualTo(numBDeals + 1)
            assertThat(getFloatingLegFixCount(nodeAApi) == 0)

            // Wait until the initial trade and all scheduled fixings up to the current date have finished
            nextFixingDates.firstWithTimeout(maxWaitTime){ it == null || it > currentDate }
            runDateChange(nodeBApi)
            nextFixingDates.firstWithTimeout(maxWaitTime) { it == null || it > futureDate }

            assertThat(getFloatingLegFixCount(nodeAApi) > 0)
        }
    }

    fun getFloatingLegFixCount(nodeApi: HttpApi) = getTrades(nodeApi)[0].calculation.floatingLegPaymentSchedule.count { it.value.rate.ratioUnit != null }

    fun getFixingDateObservable(config: FullNodeConfiguration): Observable<LocalDate?> {
        val client = CordaRPCClient(config.rpcAddress!!)
        val proxy = client.start("user", "password").proxy
        val vaultUpdates = proxy.vaultAndUpdates().second

        return vaultUpdates.map { update ->
            val irsStates = update.produced.map { it.state.data }.filterIsInstance<InterestRateSwap.State>()
            irsStates.mapNotNull { it.calculation.nextFixingDate() }.max()
        }.cache()
    }

    private fun runDateChange(nodeApi: HttpApi) {
        println("Running date change against ${nodeApi.root}")
        assertThat(nodeApi.putJson("demodate", "\"$futureDate\"")).isTrue()
    }

    private fun runTrade(nodeApi: HttpApi) {
        println("Running trade against ${nodeApi.root}")
        val fileContents = loadResourceFile("net/corda/irs/simulation/example-irs-trade.json")
        val tradeFile = fileContents.replace("tradeXXX", "trade1")
        assertThat(nodeApi.postJson("deals", tradeFile)).isTrue()
    }

    private fun runUploadRates(host: Authority) {
        println("Running upload rates against $host")
        val fileContents = loadResourceFile("net/corda/irs/simulation/example.rates.txt")
        val url = URL("http://$host/api/irs/fixes")
        assertThat(uploadFile(url, fileContents)).isTrue()
    }

    private fun loadResourceFile(filename: String): String {
        return IOUtils.toString(Thread.currentThread().contextClassLoader.getResourceAsStream(filename), Charsets.UTF_8.name())
    }

    private fun getTradeCount(nodeApi: HttpApi): Int {
        println("Getting trade count from ${nodeApi.root}")
        val deals = nodeApi.getJson<Array<*>>("deals")
        return deals.size
    }

    private fun getTrades(nodeApi: HttpApi): Array<InterestRateSwap.State> {
        println("Getting trades from ${nodeApi.root}")
        val deals = nodeApi.getJson<Array<InterestRateSwap.State>>("deals")
        return deals
    }

    fun<T> Observable<T>.firstWithTimeout(timeout: Duration, pred: (T) -> Boolean) {
        first(pred).toFuture().getOrThrow(timeout)
    }
}
