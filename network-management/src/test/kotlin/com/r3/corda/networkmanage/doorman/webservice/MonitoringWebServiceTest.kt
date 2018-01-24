package com.r3.corda.networkmanage.doorman.webservice

import com.r3.corda.networkmanage.doorman.NetworkManagementServerStatus
import com.r3.corda.networkmanage.doorman.NetworkManagementWebServer
import net.corda.core.internal.openHttpConnection
import net.corda.core.utilities.NetworkHostAndPort
import org.codehaus.jackson.map.ObjectMapper
import org.junit.Test
import java.net.URL
import kotlin.test.assertEquals

class MonitoringWebServiceTest {
    @Test
    fun `get server status`() {
        val status = NetworkManagementServerStatus()
        val jsonStatus = ObjectMapper().writeValueAsString(status)
        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), MonitoringWebService(status)).use {
            it.start()
            val conn = URL("http://${it.hostAndPort}/status").openHttpConnection()
            assertEquals(200, conn.responseCode)
            assertEquals(jsonStatus, conn.inputStream.bufferedReader().readLine())
        }
    }
}