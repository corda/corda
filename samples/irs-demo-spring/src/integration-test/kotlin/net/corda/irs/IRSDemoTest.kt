package net.corda.irs

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.vaultTrackBy
import net.corda.core.node.services.ServiceInfo
import net.corda.core.toFuture
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.seconds
import net.corda.finance.plugin.registerFinanceJSONMappers
import net.corda.irs.api.NodeInterestRates
import net.corda.irs.contract.InterestRateSwap
import net.corda.irs.web.IrsDemoWebApplication
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.test.spring.springDriver
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.DUMMY_BANK_B
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.IntegrationTestCategory
import net.corda.testing.http.HttpApi
import org.apache.commons.io.IOUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.Observable
import java.time.Duration
import java.time.LocalDate

class IRSDemoTest : IntegrationTestCategory {

    companion object {
        val log = loggerFor<IRSDemoTest>()
    }

    val rpcUser = User("user", "password",
            setOf("StartFlow.net.corda.irs.flows.AutoOfferFlow\$Requester",
                    "StartFlow.net.corda.irs.flows.UpdateBusinessDayFlow\$Broadcast",
                    "StartFlow.net.corda.irs.api.NodeInterestRates\$UploadFixesFlow"))

    val currentDate: LocalDate = LocalDate.now()
    val futureDate: LocalDate = currentDate.plusMonths(6)
    val maxWaitTime: Duration = 60.seconds

    @Test
    fun `runs IRS demo`() {

        springDriver(
                isDebug = true,
                useTestClock = true,
                scanPackage = "net.corda.irs"
        ) {
            val controllerFuture = startNode(
                    providedName = DUMMY_NOTARY.name,
                    advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type), ServiceInfo(NodeInterestRates.Oracle.type)),
                    rpcUsers = listOf(rpcUser))
            val nodeAFuture = startNode(providedName = DUMMY_BANK_A.name, rpcUsers = listOf(rpcUser))
            val nodeBFuture = startNode(providedName = DUMMY_BANK_B.name, rpcUsers = listOf(rpcUser))
            val (controller, nodeA, nodeB) = listOf(controllerFuture, nodeAFuture, nodeBFuture).map { it.getOrThrow() }

            log.info("All nodes started")

            val controllerAddrFuture = startSpringBootWebapp(IrsDemoWebApplication::class.java, controller, "/api/irs/demodate")
            val nodeAAddrFuture = startSpringBootWebapp(IrsDemoWebApplication::class.java, nodeA, "/api/irs/demodate")
            val nodeBAddrFuture = startSpringBootWebapp(IrsDemoWebApplication::class.java, nodeB, "/api/irs/demodate")

            val (controllerAddr, nodeAAddr, nodeBAddr) =
                    listOf(controllerAddrFuture, nodeAAddrFuture, nodeBAddrFuture).map { it.getOrThrow().listenAddress }

            log.info("All webservers started")

            val (controllerApi, nodeAApi, nodeBApi) = listOf(controller, nodeA, nodeB).zip(listOf(controllerAddr, nodeAAddr, nodeBAddr)).map {
                val mapper = net.corda.client.jackson.JacksonSupport.createDefaultMapper(it.first.rpc)
                registerFinanceJSONMappers(mapper)
                HttpApi.fromHostAndPort(it.second, "api/irs", mapper = mapper)
            }
            val nextFixingDates = getFixingDateObservable(nodeA.configuration)
            val numADeals = getTradeCount(nodeAApi)
            val numBDeals = getTradeCount(nodeBApi)

            runUploadRates(controllerApi)
            runTrade(nodeAApi)

            assertThat(getTradeCount(nodeAApi)).isEqualTo(numADeals + 1)
            assertThat(getTradeCount(nodeBApi)).isEqualTo(numBDeals + 1)
            assertThat(getFloatingLegFixCount(nodeAApi) == 0)

            // Wait until the initial trade and all scheduled fixings up to the current date have finished
            nextFixingDates.firstWithTimeout(maxWaitTime){ println("Comparing  current $it and $currentDate"); it == null || it >= currentDate }
            runDateChange(nodeBApi)
            nextFixingDates.firstWithTimeout(maxWaitTime) { println("Comparing future $it and $futureDate"); it == null || it >= futureDate }

            assertThat(getFloatingLegFixCount(nodeAApi) > 0)
        }
    }

    fun getFloatingLegFixCount(nodeApi: HttpApi) = getTrades(nodeApi)[0].calculation.floatingLegPaymentSchedule.count { it.value.rate.ratioUnit != null }

    fun getFixingDateObservable(config: FullNodeConfiguration): Observable<LocalDate?> {
        val client = CordaRPCClient(config.rpcAddress!!, initialiseSerialization = false)
        val proxy = client.start("user", "password").proxy
        val vaultUpdates = proxy.vaultTrackBy<InterestRateSwap.State>().updates

        return vaultUpdates.map { update ->
            val irsStates = update.produced.map { it.state.data }
            val localDate = irsStates.mapNotNull { it.calculation.nextFixingDate() }.max()
            println(localDate)
            localDate
        }.cache()
    }

    private fun runDateChange(nodeApi: HttpApi) {
        log.info("Running date change against ${nodeApi.root}")
        assertThat(nodeApi.putJson("demodate", "\"$futureDate\"")).isTrue()
    }

    private fun runTrade(nodeApi: HttpApi) {
        log.info("Running trade against ${nodeApi.root}")
        val fileContents = loadResourceFile("net/corda/irs/web/simulation/example-irs-trade.json")
        val tradeFile = fileContents.replace("tradeXXX", "trade1")
        assertThat(nodeApi.postJson("deals", tradeFile)).isTrue()
    }

    private fun runUploadRates(nodeApi: HttpApi) {
        log.info("Running upload rates against ${nodeApi.root}")
        val fileContents = loadResourceFile("net/corda/irs/simulation/example.rates.txt")
        assertThat(nodeApi.postPlain("fixes", fileContents)).isTrue()
    }

    private fun loadResourceFile(filename: String): String {
        return IOUtils.toString(Thread.currentThread().contextClassLoader.getResourceAsStream(filename), Charsets.UTF_8.name())
    }

    private fun getTradeCount(nodeApi: HttpApi): Int {
        log.info("Getting trade count from ${nodeApi.root}")
        val deals = nodeApi.getJson<Array<*>>("deals")
        return deals.size
    }

    private fun getTrades(nodeApi: HttpApi): Array<InterestRateSwap.State> {
        log.info("Getting trades from ${nodeApi.root}")
        val deals = nodeApi.getJson<Array<InterestRateSwap.State>>("deals")
        return deals
    }

    fun<T> Observable<T>.firstWithTimeout(timeout: Duration, pred: (T) -> Boolean) {
        first(pred).toFuture().getOrThrow(timeout)
    }
}
