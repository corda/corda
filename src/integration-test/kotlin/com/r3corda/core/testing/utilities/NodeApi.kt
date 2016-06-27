package com.r3corda.core.testing.utilities

import com.google.common.net.HostAndPort
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertEquals

class NodeDidNotStartException: Exception {
    constructor(message: String): super(message) {}
}

fun ensureNodeStartsOrKill(proc: Process, nodeAddr: HostAndPort) {
    try {
        assertEquals(proc.isAlive, true)
        waitForNodeStartup(nodeAddr)
    } catch (e: Throwable) {
        println("Forcibly killing node process")
        proc.destroyForcibly()
        throw e
    }
}

private fun waitForNodeStartup(nodeAddr: HostAndPort) {
    val url = URL("http://${nodeAddr.toString()}/api/status")
    var retries = 0
    var respCode: Int
    do {
        retries++
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
        } catch (e: IOException) {
            respCode = -1
            "IOException: ${e.toString()}"
        }

        if (retries > 25) {
            throw NodeDidNotStartException("The node did not start: " + err)
        }
    } while (respCode != 200)
}
