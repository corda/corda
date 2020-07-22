package net.corda.node.customcheckpointserializer

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.CheckpointCustomSerializer
import net.corda.core.utilities.getOrThrow
import net.corda.node.logging.logFile
import net.corda.testing.driver.driver
import org.assertj.core.api.Assertions
import org.junit.Test
import java.time.Duration

class DuplicateSerializerLogTest{
    @Test(timeout=300_000)
    fun `check duplicate serialisers are logged`() {
        driver {
            val node = startNode(startInSameProcess = false).getOrThrow()
            node.rpc.startFlow(::TestFlow).returnValue.get()

            val text = node.logFile().readLines().filter { it.startsWith("[WARN") }

            // Initial message is correct
            Assertions.assertThat(text).anyMatch {it.contains("Duplicate custom checkpoint serializer for type net.corda.node.customcheckpointserializer.DifficultToSerialize\$BrokenMapInterface<java.lang.Object, java.lang.Object>. Serializers: ")}
            // Message mentions TestInterfaceSerializer
            Assertions.assertThat(text).anyMatch {it.contains("net.corda.node.customcheckpointserializer.TestCorDapp\$TestInterfaceSerializer")}
            // Message mentions DuplicateSerializer
            Assertions.assertThat(text).anyMatch {it.contains("net.corda.node.customcheckpointserializer.DuplicateSerializerLogTest\$DuplicateSerializer")}
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class TestFlow : FlowLogic<DifficultToSerialize.BrokenMapInterface<String, String>>() {
        override fun call(): DifficultToSerialize.BrokenMapInterface<String, String> {
            val brokenMap: DifficultToSerialize.BrokenMapInterface<String, String> = DifficultToSerialize.BrokenMapInterfaceImpl()
            brokenMap.putAll(mapOf("test" to "input"))

            sleep(Duration.ofSeconds(0))

            return brokenMap
        }
    }

    @Suppress("unused")
    class DuplicateSerializer :
            CheckpointCustomSerializer<DifficultToSerialize.BrokenMapInterface<Any, Any>, HashMap<Any, Any>> {

        override fun toProxy(obj: DifficultToSerialize.BrokenMapInterface<Any, Any>): HashMap<Any, Any> {
            val proxy = HashMap<Any, Any>()
            return obj.toMap(proxy)
        }
        override fun fromProxy(proxy: HashMap<Any, Any>): DifficultToSerialize.BrokenMapInterface<Any, Any> {
            return DifficultToSerialize.BrokenMapInterfaceImpl<Any, Any>()
                    .also { it.putAll(proxy) }
        }
    }
}
