package net.corda.irs

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.messaging.vaultTrackBy
import net.corda.core.toFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.seconds
import net.corda.finance.plugin.registerFinanceJSONMappers
import net.corda.irs.contract.InterestRateSwap
import net.corda.irs.utilities.uploadFile
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.nodeapi.User
import net.corda.testing.*
import net.corda.testing.driver.driver
import net.corda.testing.http.HttpApi
import org.apache.commons.io.IOUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.Observable
import java.net.URL
import java.time.Duration
import java.time.LocalDate

class IRSDemoTest : IntegrationTestCategory {

    companion object {
        val log = loggerFor<IRSDemoTest>()
    }

    private val rpcUser = User("user", "password", setOf("ALL"))
    private val currentDate: LocalDate = LocalDate.now()
    private val futureDate: LocalDate = currentDate.plusMonths(6)
    private val maxWaitTime: Duration = 60.seconds

    @Test
    fun `runs IRS demo`() {
        driver(useTestClock = true, isDebug = true) {
            val (controller, nodeA, nodeB) = listOf(
                    startNotaryNode(DUMMY_NOTARY.name, validating = false),
                    startNode(providedName = DUMMY_BANK_A.name, rpcUsers = listOf(rpcUser)),
                    startNode(providedName = DUMMY_BANK_B.name))
                    .map { it.getOrThrow() }

            log.info("All nodes started")

            val (controllerAddr, nodeAAddr, nodeBAddr) = listOf(
                    startWebserver(controller),
                    startWebserver(nodeA),
                    startWebserver(nodeB))
                    .map { it.getOrThrow().listenAddress }

            log.info("All webservers started")

            val (_, nodeAApi, nodeBApi) = listOf(controller, nodeA, nodeB).zip(listOf(controllerAddr, nodeAAddr, nodeBAddr)).map {
                val mapper = net.corda.client.jackson.JacksonSupport.createDefaultMapper(it.first.rpc)
                registerFinanceJSONMappers(mapper)
                registerIRSModule(mapper)
                HttpApi.fromHostAndPort(it.second, "api/irs", mapper = mapper)
            }
            val nextFixingDates = getFixingDateObservable(nodeA.configuration)
            val numADeals = getTradeCount(nodeAApi)
            val numBDeals = getTradeCount(nodeBApi)

            runUploadRates(controllerAddr)
            runTrade(nodeAApi, controller.nodeInfo.chooseIdentity())

            assertThat(getTradeCount(nodeAApi)).isEqualTo(numADeals + 1)
            assertThat(getTradeCount(nodeBApi)).isEqualTo(numBDeals + 1)
            assertThat(getFloatingLegFixCount(nodeAApi) == 0)

            // Wait until the initial trade and all scheduled fixings up to the current date have finished
            nextFixingDates.firstWithTimeout(maxWaitTime) { it == null || it >= currentDate }
            runDateChange(nodeBApi)
            nextFixingDates.firstWithTimeout(maxWaitTime) { it == null || it >= futureDate }

            assertThat(getFloatingLegFixCount(nodeAApi) > 0)
        }
    }

    private fun getFloatingLegFixCount(nodeApi: HttpApi): Int {
        return getTrades(nodeApi)[0].calculation.floatingLegPaymentSchedule.count { it.value.rate.ratioUnit != null }
    }

    private fun getFixingDateObservable(config: FullNodeConfiguration): Observable<LocalDate?> {
        val client = CordaRPCClient(config.rpcAddress!!)
        val proxy = client.start("user", "password").proxy
        val vaultUpdates = proxy.vaultTrackBy<InterestRateSwap.State>().updates

        return vaultUpdates.map { update ->
            val irsStates = update.produced.map { it.state.data }
            irsStates.mapNotNull { it.calculation.nextFixingDate() }.max()
        }.cache()
    }

    private fun runDateChange(nodeApi: HttpApi) {
        log.info("Running date change against ${nodeApi.root}")
        assertThat(nodeApi.putJson("demodate", "\"$futureDate\"")).isTrue()
    }

    private fun runTrade(nodeApi: HttpApi, oracle: Party) {
        log.info("Running trade against ${nodeApi.root}")
        val fileContents = loadResourceFile("net/corda/irs/simulation/example-irs-trade.json")
        val tradeFile = fileContents.replace("tradeXXX", "trade1").replace("oracleXXX", oracle.name.toString())
        assertThat(nodeApi.postJson("deals", tradeFile)).isTrue()
    }

    private fun runUploadRates(host: NetworkHostAndPort) {
        log.info("Running upload rates against $host")
        val fileContents = loadResourceFile("net/corda/irs/simulation/example.rates.txt")
        val url = URL("http://$host/api/irs/fixes")
        assertThat(uploadFile(url, fileContents)).isTrue()
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
        return nodeApi.getJson("deals")
    }

    private fun <T> Observable<T>.firstWithTimeout(timeout: Duration, pred: (T) -> Boolean) {
        first(pred).toFuture().getOrThrow(timeout)
    }

    private fun registerIRSModule(mapper: ObjectMapper) {
        val module = SimpleModule("finance").apply {
            addDeserializer(InterestRateSwap.State::class.java, InterestRateSwapStateDeserializer(mapper))
        }
        mapper.registerModule(module)
    }

    class InterestRateSwapStateDeserializer(private val mapper: ObjectMapper) : JsonDeserializer<InterestRateSwap.State>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): InterestRateSwap.State {
            return try {
                val node = parser.readValueAsTree<TreeNode>()
                val fixedLeg: InterestRateSwap.FixedLeg = mapper.readValue(node.get("fixedLeg").toString())
                val floatingLeg: InterestRateSwap.FloatingLeg = mapper.readValue(node.get("floatingLeg").toString())
                val calculation: InterestRateSwap.Calculation = mapper.readValue(node.get("calculation").toString())
                val common: InterestRateSwap.Common = mapper.readValue(node.get("common").toString())
                val linearId: UniqueIdentifier = mapper.readValue(node.get("linearId").toString())
                val oracle: Party = mapper.readValue(node.get("oracle").toString())
                InterestRateSwap.State(fixedLeg = fixedLeg, floatingLeg = floatingLeg, calculation = calculation, common = common, linearId = linearId, oracle = oracle)
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid interest rate swap state(s) ${parser.text}: ${e.message}")
            }
        }
    }
}
