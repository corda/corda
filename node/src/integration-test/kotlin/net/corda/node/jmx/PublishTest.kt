package net.corda.node.jmx

import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.JmxPolicy
import net.corda.testing.driver.driver
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertTrue

class PublishTest {

    @Test(timeout=300_000)
    fun `node publishes node information via JMX when configured to do so`() {
        driver(DriverParameters(notarySpecs = emptyList(), jmxPolicy = JmxPolicy.defaultEnabled())) {
            val jmxAddress = startNode().get().jmxAddress.toString()
            val nodeStatusURL = URL("http://$jmxAddress/jolokia/read/net.corda:*")
            val httpResponse = with(nodeStatusURL.openConnection() as HttpURLConnection) {
                requestMethod = "GET"
                responseCode
            }

            assertTrue {
                httpResponse == HttpURLConnection.HTTP_OK
            }
        }
    }
}