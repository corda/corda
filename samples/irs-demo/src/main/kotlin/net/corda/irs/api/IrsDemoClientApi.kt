package net.corda.irs.api

import com.google.common.net.HostAndPort
import net.corda.irs.utilities.uploadFile
import net.corda.testing.http.HttpApi
import org.apache.commons.io.IOUtils
import java.net.URL

/**
 * Interface for communicating with nodes running the IRS demo.
 */
class IRSDemoClientApi(private val hostAndPort: HostAndPort) {
    private val api = HttpApi.fromHostAndPort(hostAndPort, apiRoot)

    fun runTrade(tradeId: String): Boolean {
        val fileContents = IOUtils.toString(javaClass.classLoader.getResourceAsStream("net/corda/irs/simulation/example-irs-trade.json"), Charsets.UTF_8.name())
        val tradeFile = fileContents.replace("tradeXXX", tradeId)
        return api.postJson("deals", tradeFile)
    }

    fun runDateChange(newDate: String): Boolean {
        return api.putJson("demodate", "\"$newDate\"")
    }

    // TODO: Add uploading of files to the HTTP API
    fun runUploadRates() {
        val fileContents = IOUtils.toString(Thread.currentThread().contextClassLoader.getResourceAsStream("net/corda/irs/simulation/example.rates.txt"), Charsets.UTF_8.name())
        val url = URL("http://$hostAndPort/upload/interest-rates")
        check(uploadFile(url, fileContents))
    }

    private companion object {
        private val apiRoot = "api/irs"
    }
}
