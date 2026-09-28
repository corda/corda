package net.corda.testing.node

import net.corda.core.internal.deleteRecursively
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import net.corda.testing.node.internal.ProcessUtilities.startJavaProcess
import net.corda.testing.node.internal.nodeJvmArgs
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div

class MockNetworkIntegrationTests {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MockNetwork(MockNetworkParameters()).run {
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

        val workingDirectory = Path("build", "MockNetworkIntegrationTests").apply {
            deleteRecursively()
            createDirectories()
        }
        val process = startJavaProcess<MockNetworkIntegrationTests>(
                emptyList(),
                workingDirectory = workingDirectory,
                extraJvmArguments = listOf("-javaagent:$quasar=$quasarOptions") + nodeJvmArgs
        )
        assertThat(process.waitFor()).isZero()
    }
}
