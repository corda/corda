package com.r3corda.core.testing.utilities

import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL

class NodeDidNotStartException: Throwable {
    constructor(message: String): super(message) {}
}

fun waitForNodeStartup(nodeAddr: String) {
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
        }

        if(retries > 200) {
            throw NodeDidNotStartException("The node did not start: " + err)
        }
    } while (respCode != 200)
}