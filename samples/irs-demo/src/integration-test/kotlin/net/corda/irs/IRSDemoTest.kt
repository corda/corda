package net.corda.irs

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.vaultTrackBy
import net.corda.core.toFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.irs.flows.plugin.registerFinanceJSONMappers
import net.corda.irs.contract.InterestRateSwap
import net.corda.irs.web.IrsDemoWebApplication
import net.corda.test.spring.springDriver
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.http.HttpApi
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.cordappWithPackages
import org.apache.commons.io.IOUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.Observable
import java.time.Duration
import java.time.LocalDate

class IRSDemoTest {
    companion object {
        private val log = contextLogger()
    }

    private val rpcUsers = listOf(User("user", "password", setOf("ALL")))
    private val currentDate: LocalDate = LocalDate.now()
    private val futureDate: LocalDate = currentDate.plusMonths(6)
    private val maxWaitTime: Duration = 150.seconds

    private class Timer(val timerName: String){

        private var timerInMilliSeconds: Long = 0

        fun reset(checkpointName: String = String()) {
            timerInMilliSeconds = System.currentTimeMillis()
            log.info("$timerName - $checkpointName - Timer reset")
        }

        fun printElapsedTime(checkpointName: String) {
            val elapsedTimeInMilliSec = calculatedElapsedTime(timerInMilliSeconds)
            val elapsedTimeStr = getElapsedTimeAsStandardisedString(elapsedTimeInMilliSec)

            log.info("$timerName - $checkpointName - Elapsed time: $elapsedTimeStr ($elapsedTimeInMilliSec milliseconds)")
        }

        private fun calculatedElapsedTime(startTimeInMillis: Long) = System.currentTimeMillis() - startTimeInMillis
        private fun getElapsedTimeAsStandardisedString(timeInMilliseconds: Long) = Duration.ofMillis(timeInMilliseconds).toString()
    }

    private val globalTimer = Timer("Global timer")
    private val localTimer = Timer("Local timer")

    @Test(timeout=300_000)
    fun `runs IRS demo`() {

        globalTimer.reset("At the top of the test")

        localTimer.reset("DriverParameters")
        val driver = DriverParameters(
                useTestClock = true,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, rpcUsers = rpcUsers)),
                cordappsForAllNodes = FINANCE_CORDAPPS + cordappWithPackages("net.corda.irs")
        )
        localTimer.printElapsedTime("DriverParameters")
        globalTimer.printElapsedTime("DriverParameters")

        localTimer.reset("springDriver1")
        springDriver(driver) {
            localTimer.printElapsedTime("springDriver1")

            globalTimer.printElapsedTime("Entered lambda spring driver")

            localTimer.reset("starting nodes")
            val (nodeA, nodeB, controller) = listOf(
                    startNode(providedName = DUMMY_BANK_A_NAME, rpcUsers = rpcUsers),
                    startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = rpcUsers),
                    startNode(providedName = CordaX500Name("Regulator", "Moscow", "RU")),
                    defaultNotaryNode
            ).map { it.getOrThrow() }
            log.info("All nodes started")
            localTimer.printElapsedTime("starting nodes")

            globalTimer.printElapsedTime("all nodes started")

            localTimer.reset("startSpringBootWebapp")
            val (controllerAddr, nodeAAddr, nodeBAddr) = listOf(controller, nodeA, nodeB).map {
                startSpringBootWebapp(IrsDemoWebApplication::class.java, it, "/api/irs/demodate")
            }.map { it.getOrThrow().listenAddress }
            log.info("All webservers started")
            localTimer.printElapsedTime("startSpringBootWebapp")

            globalTimer.printElapsedTime("startSpringBootWebapp")

            localTimer.reset("JacksonSupport.createDefaultMapper")
            val (controllerApi, nodeAApi, nodeBApi) = listOf(controller, nodeA, nodeB).zip(listOf(controllerAddr, nodeAAddr, nodeBAddr)).map {
                val mapper = JacksonSupport.createDefaultMapper(it.first.rpc)
                registerFinanceJSONMappers(mapper)
                registerIRSModule(mapper)
                HttpApi.fromHostAndPort(it.second, "api/irs", mapper = mapper)
            }
            localTimer.printElapsedTime("JacksonSupport.createDefaultMapper")
            globalTimer.printElapsedTime("JacksonSupport.createDefaultMapper")

            val nextFixingDates = getFixingDateObservable(nodeA.rpcAddress)
            val numADeals = getTradeCount(nodeAApi)
            val numBDeals = getTradeCount(nodeBApi)
            globalTimer.printElapsedTime("Get fixing date observable")

            localTimer.reset("runUploadRates")
            runUploadRates(controllerApi)
            localTimer.printElapsedTime("runUploadRates")
            globalTimer.printElapsedTime("runUploadRates")

            localTimer.reset("runTrade")
            runTrade(nodeAApi, controller.nodeInfo.singleIdentity())
            localTimer.printElapsedTime("runTrade")
            globalTimer.printElapsedTime("runTrade")

            assertThat(getTradeCount(nodeAApi)).isEqualTo(numADeals + 1)
            assertThat(getTradeCount(nodeBApi)).isEqualTo(numBDeals + 1)
            assertThat(getFloatingLegFixCount(nodeAApi) == 0)

            // Wait until the initial trade and all scheduled fixings up to the current date have finished
            localTimer.reset("nextFixingDates 1")
            nextFixingDates.firstWithTimeout(maxWaitTime) { it == null || it >= currentDate }
            localTimer.printElapsedTime("nextFixingDates 1")
            globalTimer.printElapsedTime("nextFixingDates 1")

            localTimer.reset("runDateChange")
            runDateChange(nodeBApi)
            localTimer.printElapsedTime("runDateChange")
            globalTimer.printElapsedTime("runDateChange")

            localTimer.reset("nextFixingDates 2")
            nextFixingDates.firstWithTimeout(maxWaitTime) { it == null || it >= futureDate }
            localTimer.printElapsedTime("nextFixingDates 2")
            globalTimer.printElapsedTime("nextFixingDates 2")

            localTimer.reset("getFloatingLegFixCount")
            assertThat(getFloatingLegFixCount(nodeAApi) > 0)
            localTimer.printElapsedTime("getFloatingLegFixCount")
            globalTimer.printElapsedTime("getFloatingLegFixCount")
        }
        globalTimer.printElapsedTime("Exited springDriver")
    }

    private fun getFloatingLegFixCount(nodeApi: HttpApi): Int {
        return getTrades(nodeApi)[0].calculation.floatingLegPaymentSchedule.count { it.value.rate.ratioUnit != null }
    }

    private fun getFixingDateObservable(address: NetworkHostAndPort): Observable<LocalDate?> {
        val client = CordaRPCClient(address)
        val proxy = client.start("user", "password").proxy
        val vaultUpdates = proxy.vaultTrackBy<InterestRateSwap.State>().updates

        return vaultUpdates.map { update ->
            val irsStates = update.produced.map { it.state.data }
            irsStates.mapNotNull { it.calculation.nextFixingDate() }.max()
        }.cache()
    }

    private fun runDateChange(nodeApi: HttpApi) {
        log.info("Running date change against ${nodeApi.root}")
        nodeApi.putJson("demodate", "\"$futureDate\"")
    }

    private fun runTrade(nodeApi: HttpApi, oracle: Party) {
        log.info("Running trade against ${nodeApi.root}")
        val fileContents = loadResourceFile("net/corda/irs/web/simulation/example-irs-trade.json")
        val tradeFile = fileContents.replace("tradeXXX", "trade1").replace("oracleXXX", oracle.name.toString())
        nodeApi.postJson("deals", tradeFile)
    }

    private fun runUploadRates(nodeApi: HttpApi) {
        log.info("Running upload rates against ${nodeApi.root}")
        val fileContents = loadResourceFile("net/corda/irs/simulation/example.rates.txt")
        nodeApi.postPlain("fixes", fileContents)
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
