package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.div
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.getOrThrow
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.ALICE
import net.corda.testing.driver.driver
import net.corda.node.internal.NodeStartup
import net.corda.node.services.startFlowPermission
import net.corda.nodeapi.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals

class BootTests {

    @Test
    fun `java deserialization is disabled`() {
        driver {
            val user = User("u", "p", setOf(startFlowPermission<ObjectInputStreamFlow>()))
            val future = startNode(rpcUsers = listOf(user)).getOrThrow().rpcClientToNode().
                start(user.username, user.password).proxy.startFlow(::ObjectInputStreamFlow).returnValue
            assertThatThrownBy { future.getOrThrow() }.isInstanceOf(InvalidClassException::class.java).hasMessage("filter status: REJECTED")
        }
    }

    @Test
    fun `double node start doesn't write into log file`() {
        val logConfigFile = Paths.get("..", "config", "dev", "log4j2.xml").toAbsolutePath()
        assertThat(logConfigFile).isRegularFile()
        driver(isDebug = true, systemProperties = mapOf("log4j.configurationFile" to logConfigFile.toString())) {
            val alice = startNode(ALICE.name).get()
            val logFolder = alice.configuration.baseDirectory / "logs"
            val logFile = logFolder.toFile().listFiles { _, name -> name.endsWith(".log") }.single()
            // Start second Alice, should fail
            assertThatThrownBy {
                startNode(ALICE.name).getOrThrow()
            }
            // We count the number of nodes that wrote into the logfile by counting "Logs can be found in"
            val numberOfNodesThatLogged = Files.lines(logFile.toPath()).filter { NodeStartup.LOGS_CAN_BE_FOUND_IN_STRING in it }.count()
            assertEquals(1, numberOfNodesThatLogged)
        }
    }
}

@StartableByRPC
class ObjectInputStreamFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val data = ByteArrayOutputStream().apply { ObjectOutputStream(this).use { it.writeObject(object : Serializable {}) } }.toByteArray()
        ObjectInputStream(data.inputStream()).use { it.readObject() }
    }
}
