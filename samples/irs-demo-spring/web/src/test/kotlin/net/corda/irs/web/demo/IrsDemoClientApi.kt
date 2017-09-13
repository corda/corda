package net.corda.irs.web.demo

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.testing.http.HttpApi
import org.apache.commons.io.IOUtils

/**
 * Interface for communicating with nodes running the IRS demo.
 */
class IRSDemoClientApi(private val hostAndPort: NetworkHostAndPort) {
    private val api = HttpApi.fromHostAndPort(hostAndPort, apiRoot)

    fun runTrade(tradeId: String): Boolean {
        val fileContents = IOUtils.toString(javaClass.classLoader.getResourceAsStream("net/corda/irs/web/simulation/example-irs-trade.json"), Charsets.UTF_8.name())
        val tradeFile = fileContents.replace("tradeXXX", tradeId)
        return api.postJson("deals", tradeFile)
    }

    fun runDateChange(newDate: String): Boolean {
        return api.putJson("demodate", "\"$newDate\"")
    }

    // TODO: Add uploading of files to the HTTP API
    fun runUploadRates() {
        // refers to example.rates.txt from cordapp
        val fileContents = IOUtils.toString(Thread.currentThread().contextClassLoader.getResourceAsStream("net/corda/irs/simulation/example.rates.txt"), Charsets.UTF_8.name())
        check(api.postPlain("fixes", fileContents))
        println("Rates successfully uploaded!")
    }

    private companion object {
        private val apiRoot = "api/irs"
    }
}
