package net.corda.node.customcheckpointserializer

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.CheckpointCustomSerializer
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.logFile
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions
import org.junit.Test
import java.time.Duration

class DuplicateSerializerLogWithSameSerializerTest {
    @Test(timeout=300_000)
    fun `check duplicate serialisers are logged not logged for the same class`() {

        // Duplicate the cordapp in this node
        driver(DriverParameters(cordappsForAllNodes = listOf(this.enclosedCordapp(), this.enclosedCordapp()))) {
            val node = startNode(startInSameProcess = false).getOrThrow()
            node.rpc.startFlow(::TestFlow).returnValue.get()

            // Log4j 2.17.2+ disables scripting by default, so ScriptPatternSelector may
            // fall back to outputting just the message without any log level prefix.
            // Search for relevant message content directly instead of filtering by log level.
            val text = node.logFile().readLines()

            // Initial message is not logged - no line should contain this
            Assertions.assertThat(text)
                    .noneMatch { it.contains("Duplicate custom checkpoint serializer for type ") }
            // Log does not mention DuplicateSerializerThatShouldNotBeLogged
            Assertions.assertThat(text)
                    .noneMatch { it.contains("DuplicateSerializerThatShouldNotBeLogged") }
        }
    }

    @CordaSerializable
    class UnusedClass

    @Suppress("unused")
    class DuplicateSerializerThatShouldNotBeLogged : CheckpointCustomSerializer<UnusedClass, String> {
        override fun toProxy(obj: UnusedClass): String = ""
        override fun fromProxy(proxy: String): UnusedClass = UnusedClass()
    }

    @StartableByRPC
    @InitiatingFlow
    class TestFlow : FlowLogic<UnusedClass>() {
        override fun call(): UnusedClass {
            val unusedClass = UnusedClass()

            sleep(Duration.ofSeconds(0))

            return unusedClass
        }
    }
}
