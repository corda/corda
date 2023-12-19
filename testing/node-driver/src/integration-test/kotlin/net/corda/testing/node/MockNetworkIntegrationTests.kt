package net.corda.testing.node

import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import net.corda.testing.node.internal.ProcessUtilities.startJavaProcess
import org.junit.Test
import kotlin.io.path.div
import kotlin.test.assertEquals

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
        val moduleOpens = listOf(
                "--add-opens", "java.base/java.time=ALL-UNNAMED", "--add-opens", "java.base/java.io=ALL-UNNAMED",
                "--add-opens", "java.base/java.util=ALL-UNNAMED", "--add-opens", "java.base/java.net=ALL-UNNAMED",
                "--add-opens", "java.base/java.nio=ALL-UNNAMED", "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
                "--add-opens", "java.base/java.security.cert=ALL-UNNAMED", "--add-opens", "java.base/javax.net.ssl=ALL-UNNAMED",
                "--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED", "--add-opens", "java.sql/java.sql=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED"
        )

        assertEquals(0, startJavaProcess<MockNetworkIntegrationTests>(emptyList(),
                extraJvmArguments = listOf("-javaagent:$quasar=$quasarOptions") + moduleOpens).waitFor())
    }
}
