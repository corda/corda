package net.corda.irs.web.demo

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.testing.http.HttpApi
import org.apache.commons.io.IOUtils

/**
 * Interface for communicating with nodes running the IRS demo.
 */
class IRSDemoClientApi(hostAndPort: NetworkHostAndPort) {
    private val api = HttpApi.fromHostAndPort(hostAndPort, apiRoot)

    fun runTrade(tradeId: String, oracleName: CordaX500Name) {
        val fileContents = IOUtils.toString(javaClass.classLoader.getResourceAsStream("net/corda/irs/web/simulation/example-irs-trade.json"), Charsets.UTF_8.name())
        val tradeFile = fileContents.replace("tradeXXX", tradeId).replace("oracleXXX", oracleName.toString())
        api.postJson("deals", tradeFile)
    }

    fun runDateChange(newDate: String) {
        api.putJson("demodate", "\"$newDate\"")
    }

    // TODO: Add uploading of files to the HTTP API
    fun runUploadRates() {
        val fileContents = IOUtils.toString(Thread.currentThread().contextClassLoader.getResourceAsStream("net/corda/irs/simulation/example.rates.txt"), Charsets.UTF_8.name())
        api.postPlain("fixes", fileContents)
        println("Rates successfully uploaded!")
    }

    private companion object {
        private const val apiRoot = "api/irs"
    }
}
