package com.r3corda.core.testing

import com.google.common.net.HostAndPort
import com.r3corda.testing.*
import kotlin.test.assertEquals
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class IRSDemoTest: IntegrationTestCategory {
    @Test fun `runs IRS demo`() {
        val hostAndPorts = getFreeLocalPorts("localhost", 4)

        val nodeAddrA = hostAndPorts[0]
        val apiAddrA = hostAndPorts[1]
        val apiAddrB = hostAndPorts[2]

        val baseDirectory = Paths.get("./build/integration-test/${TestTimestamp.timestamp}/irs-demo")
        var procA: Process? = null
        var procB: Process? = null
        try {
            setupNode(baseDirectory, "NodeA")
            setupNode(baseDirectory, "NodeB")
            procA = startNode(
                    baseDirectory = baseDirectory,
                    nodeType = "NodeA",
                    nodeAddr = nodeAddrA,
                    networkMapAddr = apiAddrA,
                    apiAddr = apiAddrA
            )
            procB = startNode(
                    baseDirectory = baseDirectory,
                    nodeType = "NodeB",
                    nodeAddr = hostAndPorts[3],
                    networkMapAddr = nodeAddrA,
                    apiAddr = apiAddrB
            )
            runTrade(apiAddrA)
            runDateChange(apiAddrA)
        } finally {
            stopNode(procA)
            stopNode(procB)
        }
    }
}

private fun setupNode(baseDirectory: Path, nodeType: String) {
    println("Running setup for $nodeType")
    val args = listOf("--role", "Setup" + nodeType, "--base-directory", baseDirectory.toString())
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args, "IRSDemoSetup$nodeType")
    assertExitOrKill(proc)
    assertEquals(proc.exitValue(), 0)
}

private fun startNode(baseDirectory: Path,
                      nodeType: String,
                      nodeAddr: HostAndPort,
                      networkMapAddr: HostAndPort,
                      apiAddr: HostAndPort): Process {
    println("Running node $nodeType")
    println("Node addr: $nodeAddr")
    println("Network map addr: $networkMapAddr")
    println("API addr: $apiAddr")
    val args = listOf(
            "--role", nodeType,
            "--base-directory", baseDirectory.toString(),
            "--network-address", nodeAddr.toString(),
            "--network-map-address", networkMapAddr.toString(),
            "--api-address", apiAddr.toString())
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args, "IRSDemo$nodeType")
    NodeApi.ensureNodeStartsOrKill(proc, apiAddr)
    return proc
}

private fun runTrade(nodeAddr: HostAndPort) {
    println("Running trade")
    val args = listOf("--role", "Trade", "trade1", "--api-address", nodeAddr.toString())
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args, "IRSDemoTrade")
    assertExitOrKill(proc)
    assertEquals(proc.exitValue(), 0)
}

private fun runDateChange(nodeAddr: HostAndPort) {
    println("Running date change")
    val args = listOf("--role", "Date", "2017-01-02", "--api-address", nodeAddr.toString())
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args, "IRSDemoDate")
    assertExitOrKill(proc)
    assertEquals(proc.exitValue(), 0)
}

private fun stopNode(nodeProc: Process?) {
    if (nodeProc != null) {
        println("Stopping node")
        assertAliveAndKill(nodeProc)
    }
}
