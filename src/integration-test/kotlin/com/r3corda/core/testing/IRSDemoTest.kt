package com.r3corda.core.testing

import com.r3corda.core.testing.utilities.spawn
import com.r3corda.core.testing.utilities.waitForNodeStartup
import kotlin.test.assertEquals
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

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
    assertEquals(proc.waitFor(2, TimeUnit.MINUTES), true);
    assertEquals(proc.exitValue(), 0)
}

private fun startNode(dir: Path, nodeType: String, nodeAddr: String): Process {
    val args = listOf("--role", nodeType, "--dir", dir.toString())
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args)
    waitForNodeStartup(nodeAddr)
    return proc
}

private fun runTrade() {
    val args = listOf("--role", "Trade", "trade1")
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args)
    assertEquals(proc.waitFor(2, TimeUnit.MINUTES), true);
    assertEquals(proc.exitValue(), 0)
}

private fun runDateChange() {
    val args = listOf("--role", "Date", "2017-01-02")
    val proc = spawn("com.r3corda.demos.IRSDemoKt", args)
    assertEquals(proc.waitFor(2, TimeUnit.MINUTES), true);
    assertEquals(proc.exitValue(), 0)
}

private fun stopNode(nodeProc: Process?) {
    assertEquals(nodeProc?.isAlive, true)
    nodeProc?.destroy()
}

private fun cleanup(dir: Path) {
    println("Erasing: " + dir.toString())
    dir.toFile().deleteRecursively()
}