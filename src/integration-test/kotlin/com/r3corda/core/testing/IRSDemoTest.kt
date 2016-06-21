package com.r3corda.core.testing

import com.google.common.net.HostAndPort
import com.r3corda.core.testing.utilities.*
import kotlin.test.assertEquals
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class IRSDemoTest {
    @Test fun `runs IRS demo`() {
        val nodeAddrA = freeLocalHostAndPort()
        val apiAddrA = HostAndPort.fromString("${nodeAddrA.hostText}:${nodeAddrA.port + 1}")
        val dirA = Paths.get("./nodeA")
        val dirB = Paths.get("./nodeB")
        var procA: Process? = null
        var procB: Process? = null
        try {
            setupNode(dirA, "NodeA")
            setupNode(dirB, "NodeB")
            procA = startNode(dirA, "NodeA", nodeAddrA)
            procB = startNode(dirB, "NodeB", freeLocalHostAndPort())
            runTrade(apiAddrA)
            runDateChange(apiAddrA)
        } finally {
            stopNode(procA)
            stopNode(procB)
            cleanup(dirA)
            cleanup(dirB)
        }
    }
}
private fun setupNode(dir: Path, nodeType: String) {
    println("Running setup for $nodeType")
    val args = listOf("--role", "Setup" + nodeType, "--dir", dir.toString())
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args)
    assertExitOrKill(proc)
    assertEquals(proc.exitValue(), 0)
}

private fun startNode(dir: Path, nodeType: String, nodeAddr: HostAndPort): Process {
    println("Running node $nodeType")
    println("Node addr: ${nodeAddr.toString()}")
    val args = listOf("--role", nodeType, "--dir", dir.toString(), "--network-address", nodeAddr.toString())
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args)
    ensureNodeStartsOrKill(proc, nodeAddr)
    return proc
}

private fun runTrade(nodeAddr: HostAndPort) {
    println("Running trade")
    val args = listOf("--role", "Trade", "trade1", "--network-address", "http://" + nodeAddr.toString())
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args)
    assertExitOrKill(proc)
    assertEquals(proc.exitValue(), 0)
}

private fun runDateChange(nodeAddr: HostAndPort) {
    println("Running date change")
    val args = listOf("--role", "Date", "2017-01-02", "--network-address", "http://" + nodeAddr.toString())
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args)
    assertExitOrKill(proc)
    assertEquals(proc.exitValue(), 0)
}

private fun stopNode(nodeProc: Process?) {
    if(nodeProc != null) {
        println("Stopping node")
        assertAliveAndKill(nodeProc)
    }
}

private fun cleanup(dir: Path) {
    println("Erasing: " + dir.toString())
    dir.toFile().deleteRecursively()
}