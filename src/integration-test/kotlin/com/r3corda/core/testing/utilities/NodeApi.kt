package com.r3corda.core.testing.utilities

import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertEquals

class NodeDidNotStartException: Throwable {
    constructor(message: String): super(message) {}
}

fun ensureNodeStartsOrKill(proc: Process, nodeAddr: String) {
    try {
        assertEquals(proc.isAlive, true)
        waitForNodeStartup(nodeAddr)
    } catch (e: Exception) {
        proc.destroy()
        throw e
    }
}

private fun waitForNodeStartup(nodeAddr: String) {
    var retries = 0
    var respCode: Int
    do {
        retries++
        val url = URL(nodeAddr + "/api/status")
        val err = try {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            respCode = conn.responseCode
            InputStreamReader(conn.inputStream).readLines().joinToString { it }
        } catch(e: ConnectException) {
            // This is to be expected while it loads up
            respCode = 404
            "Node hasn't started"
        } catch(e: SocketException) {
            respCode = -1
            "Could not connect: ${e.toString()}"
        }

        if(retries > 200) {
            throw NodeDidNotStartException("The node did not start: " + err)
        }
    } while (respCode != 200)
}