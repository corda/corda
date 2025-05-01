package net.corda.testing.node.internal

import net.corda.core.internal.deleteRecursively
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import net.corda.testing.node.internal.ProcessUtilities.startJavaProcess
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div

class InternalMockNetworkIntegrationTests {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            InternalMockNetwork().run {
                repeat(2) { createNode() }
                runNetwork()
                stopNodes()
            }
        }
    }

    @Test(timeout=300_000)
	fun `does not leak non-daemon threads`() {
        val quasar = projectRootDir / "lib" / "quasar.jar"
        val quasarOptions = "m"

        val workingDirectory = Path("build", "InternalMockNetworkIntegrationTests").apply {
            deleteRecursively()
            createDirectories()
        }
        val process = startJavaProcess<InternalMockNetworkIntegrationTests>(
                emptyList(),
                workingDirectory = workingDirectory,
                extraJvmArguments = listOf("-javaagent:$quasar=$quasarOptions") + nodeJvmArgs
        )
        assertThat(process.waitFor()).isZero()
    }
}
