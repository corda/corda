package net.corda.testing.node.internal

import net.corda.core.CordaOID
import net.corda.core.internal.div
import net.corda.core.internal.location
import net.corda.core.internal.readText
import net.corda.core.internal.toPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DriverDSLImplTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `nodeClasspath does not leak classes that the out-of-process node shouldn't have`() {
        // First make sure this class actually exists to prevent a false-positive
        Class.forName("net.corda.finance.contracts.asset.Cash")
        val exitValue = ProcessUtilities.startJavaProcess<CheckClassDoesntExist>(
                arguments = listOf("net.corda.finance.contracts.asset.Cash", Test::class.java.name),
                classPath = DriverDSLImpl.nodeClasspath + CheckClassDoesntExist::class.java.location.toPath().toString()
        ).waitFor()
        assertThat(exitValue).isZero()
    }

    @Test
    fun `nodeClasspath includes classes that the out-of-process node must have`() {
        val exitValue = ProcessUtilities.startJavaProcess<CheckClassExists>(
                arguments = listOf(CordaOID::class.java.name),
                classPath = DriverDSLImpl.nodeClasspath + CheckClassExists::class.java.location.toPath().toString()
        ).waitFor()
        assertThat(exitValue).isZero()
    }

    @Test
    fun `nodeClasspath does not include calling class`() {
        val exitValue = ProcessUtilities.startJavaProcess<CheckClassDoesntExist>(
                arguments = emptyList(),
                classPath = DriverDSLImpl.nodeClasspath,
                workingDirectory = tempFolder.root.toPath()
        ).waitFor()
        assertThat(exitValue).isNotZero()
        val errLog = (tempFolder.root.toPath() / "${CheckClassDoesntExist::class.java.name}.stderr.log").readText()
        assertThat(errLog).contains("Could not find or load main ${CheckClassDoesntExist::class.java}")
    }

    object CheckClassDoesntExist {
        @JvmStatic
        fun main(classNames: Array<String>) {
            for (className in classNames) {
                try {
                    Class.forName(className)
                    throw IllegalStateException("$className exists when it shouldn't")
                } catch (e: ClassNotFoundException) {
                    // Not on the processes classpath so we're good
                }
            }
        }
    }

    object CheckClassExists {
        @JvmStatic
        fun main(classNames: Array<String>) {
            for (className in classNames) {
                Class.forName(className)
            }
        }
    }
}
