package net.corda.testing.node.internal

import net.corda.core.internal.readText
import net.corda.core.internal.writeText
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
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

    @Test
    fun `test dummy process can be started`() {
        val tmpFile = tempFolder.newFile("${ProcessUtilitiesTests::class.java.simpleName}.txt")
        val startedProcess = ProcessUtilities.startJavaProcess<ProcessUtilitiesTests>(listOf(tmpFile.absolutePath))
        assertTrue { startedProcess.waitFor(20, TimeUnit.SECONDS) }
        assertEquals(tmpString, tmpFile.toPath().readText())
    }
}