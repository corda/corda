package com.r3corda.core.testing

import kotlin.test.assertEquals
import org.junit.Test
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

private class NodeDidNotStartException: Throwable {
    constructor(message: String): super(message) {}
}

class IRSDemoTest {
    @Test fun `runs IRS demo`() {
        val dirA = Paths.get("./nodeA")
        val dirB = Paths.get("./nodeB")
        var procA: Process? = null
        var procB: Process? = null
        try {
            setupNode(dirA, "NodeA")
            setupNode(dirB, "NodeB")
            procA = startNode(dirA, "NodeA", "http://localhost:31338")
            procB = startNode(dirB, "NodeB", "http://localhost:31341")
            runTrade()
            runDateChange()
        } finally {
            stopNode(procA)
            stopNode(procB)
            cleanup(dirA)
            cleanup(dirB)
        }
    }
}
private fun setupNode(dir: Path, nodeType: String) {
    val args = listOf("--role", "Setup" + nodeType, "--dir", dir.toString())
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args)
    proc.waitFor();
    assertEquals(proc.exitValue(), 0)
}

private fun startNode(dir: Path, nodeType: String, nodeAddr: String): Process {
    val args = listOf("--role", nodeType, "--dir", dir.toString())
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args)
    waitForNode(nodeAddr)
    return proc
}

// Todo: Move this to a library and use it in the trade demo
private fun waitForNode(nodeAddr: String) {
    var retries = 0
    var respCode: Int = 404
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

private fun runTrade() {
    val args = listOf("--role", "Trade", "trade1")
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args)
    proc.waitFor();
    assertEquals(proc.exitValue(), 0)
}

private fun runDateChange() {
    val args = listOf("--role", "Date", "2017-01-02")
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args)
    proc.waitFor();
    assertEquals(proc.exitValue(), 0)
}

private fun stopNode(nodeProc: Process?) {
    nodeProc?.destroy()
}

private fun cleanup(dir: Path) {
    println("Erasing: " + dir.toString())
    dir.toFile().deleteRecursively()
}