package net.corda.irs.web.demo

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.testing.http.HttpApi
import org.apache.commons.io.IOUtils

/**
 * Interface for communicating with nodes running the IRS demo.
 */
class IRSDemoClientApi(private val hostAndPort: NetworkHostAndPort) {
    private val api = HttpApi.fromHostAndPort(hostAndPort, apiRoot)

    fun runTrade(tradeId: String, oracleName: CordaX500Name): Boolean {
        val fileContents = IOUtils.toString(javaClass.classLoader.getResourceAsStream("net/corda/irs/simulation/example-irs-trade.json"), Charsets.UTF_8.name())
        val tradeFile = fileContents.replace("tradeXXX", tradeId).replace("oracleXXX", oracleName.toString())
        return api.postJson("deals", tradeFile)
    }

    fun runDateChange(newDate: String): Boolean {
        return api.putJson("demodate", "\"$newDate\"")
    }

    // TODO: Add uploading of files to the HTTP API
    fun runUploadRates() {
        val fileContents = IOUtils.toString(Thread.currentThread().contextClassLoader.getResourceAsStream("net/corda/irs/simulation/example.rates.txt"), Charsets.UTF_8.name())
        check(api.postPlain("fixes", fileContents))
        println("Rates successfully uploaded!")
    }

    private companion object {
        private val apiRoot = "api/irs"
    }
}
