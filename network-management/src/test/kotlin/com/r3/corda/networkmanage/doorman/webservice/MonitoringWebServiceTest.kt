/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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