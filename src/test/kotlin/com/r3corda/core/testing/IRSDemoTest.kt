package com.r3corda.core.testing

import com.r3corda.demos.runIRSDemo
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class IRSDemoTest {
    @Test fun `runs IRS demo`() {
        val dirA = Paths.get("./nodeA")
        val dirB = Paths.get("./nodeB")
        try {
            setupNodeA(dirA)
            setupNodeB(dirB)
            startNodeA(dirA)
            startNodeB(dirB)
            runTrade()
            runDateChange()
            stopNodeA()
            stopNodeB()
        } finally {
            cleanup(dirA)
            cleanup(dirB)
        }
    }
}

private fun setupNodeA(dir: Path) {
    runIRSDemo(arrayOf("--role", "SetupNodeA", "--dir", dir.toString()))
}

private fun setupNodeB(dir: Path) {
    runIRSDemo(arrayOf("--role", "SetupNodeB", "--dir", dir.toString()))
}

private fun startNodeA(dir: Path) {
    thread(true, false, null, "NodeA", -1, { runIRSDemo(arrayOf("--role", "NodeA", "--dir", dir.toString()), true) })
    Thread.sleep(15000)
}

private fun startNodeB(dir: Path) {
    thread(true, false, null, "NodeB", -1, { runIRSDemo(arrayOf("--role", "NodeB", "--dir", dir.toString()), true) })
    Thread.sleep(15000)
}

private fun stopNodeA() {

}

private fun stopNodeB() {

}

private fun runTrade() {
    assertEquals(runIRSDemo(arrayOf("--role", "Trade", "trade1")), 0)
}

private fun runDateChange() {
    assertEquals(runIRSDemo(arrayOf("--role", "Date", "2017-01-02")), 0)
}

private fun cleanup(dir: Path) {
    println("Erasing: " + dir.toString())
    dir.toFile().deleteRecursively()
}