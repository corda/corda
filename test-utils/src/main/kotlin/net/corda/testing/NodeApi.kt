package net.corda.testing

import com.google.common.net.HostAndPort
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertEquals

class NodeApi {
    class NodeDidNotStartException(message: String): Exception(message)

    companion object {
        // Increased timeout to two minutes.
        val NODE_WAIT_RETRY_COUNT: Int = 600
        val NODE_WAIT_RETRY_DELAY_MS: Long = 200

        fun ensureNodeStartsOrKill(proc: Process, nodeWebserverAddr: HostAndPort) {
            try {
                assertEquals(proc.isAlive, true)
                waitForNodeStartup(nodeWebserverAddr)
            } catch (e: Throwable) {
                println("Forcibly killing node process")
                proc.destroyForcibly()
                throw e
            }
        }

        private fun waitForNodeStartup(nodeWebserverAddr: HostAndPort) {
            val url = URL("http://${nodeWebserverAddr.toString()}/api/status")
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

                if (retries > NODE_WAIT_RETRY_COUNT) {
                    throw NodeDidNotStartException("The node did not start: " + err)
                }

                Thread.sleep(NODE_WAIT_RETRY_DELAY_MS)
            } while (respCode != 200)
        }
    }
}
