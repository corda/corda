package net.corda.testing.node.internal

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessUtilitiesTests {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    companion object {

        private val tmpString = ProcessUtilitiesTests::class.java.name

        @JvmStatic
        fun main(args: Array<String>) {
            Paths.get(args[0]).writeText(tmpString)
        }
    }

    @Test(timeout=300_000)
	fun `test dummy process can be started`() {
        val tmpFile = tempFolder.newFile("${ProcessUtilitiesTests::class.java.simpleName}.txt")
        val startedProcess = ProcessUtilities.startJavaProcess<ProcessUtilitiesTests>(listOf(tmpFile.absolutePath))
        assertTrue { startedProcess.waitFor(20, TimeUnit.SECONDS) }
        assertEquals(tmpString, tmpFile.toPath().readText())
    }
}