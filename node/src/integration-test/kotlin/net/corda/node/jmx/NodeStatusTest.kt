package net.corda.node.jmx

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.nodeapi.internal.NodeStatus
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.JmxPolicy
import net.corda.testing.driver.driver
import org.junit.Test
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_OK
import java.net.URL
import java.util.stream.Collectors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeStatusTest {

    @Test(timeout=300_000)
    fun `node status is published via JMX`() {
        driver(DriverParameters(notarySpecs = emptyList(), jmxPolicy = JmxPolicy.defaultEnabled())) {
            val jmxAddress = startNode().get().jmxAddress.toString()
            val nodeStatusURL = URL("http://$jmxAddress/jolokia/read/net.corda:name=Status,type=Node")
            val jmxInfo = with(nodeStatusURL.openConnection() as HttpURLConnection) {
                requestMethod = "GET"
                inputStream.bufferedReader().use {
                    it.lines().collect(Collectors.toList()).joinToString()
                }
            }

            assertTrue {
                jmxInfo.isNotEmpty()
            }

            val jsonTree = ObjectMapper().readTree(jmxInfo)
            val httpStatus = jsonTree.get("status").asInt()
            val nodeStatus = jsonTree.get("value").get("Value").asText()

            assertEquals(httpStatus, HTTP_OK)
            assertEquals(nodeStatus, NodeStatus.STARTED.toString())
        }
    }
}