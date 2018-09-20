package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaRuntimeException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.internal.readLines
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.NodeStartup
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.nodeapi.exceptions.InternalNodeException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.startNode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.*
import kotlin.test.assertEquals

class BootTests {
    @Test
    fun `java deserialization is disabled`() {
        val user = User("u", "p", setOf(startFlow<ObjectInputStreamFlow>()))
        val devParams = NodeParameters(providedName = BOB_NAME, rpcUsers = listOf(user))
        val params = NodeParameters(rpcUsers = listOf(user))

        fun NodeHandle.attemptJavaDeserialization() {
            CordaRPCClient(rpcAddress).use(user.username, user.password) { connection ->
                connection.proxy
                rpc.startFlow(::ObjectInputStreamFlow).returnValue.getOrThrow()
            }
        }
        driver {
            val devModeNode = startNode(devParams).getOrThrow()
            val node = startNode(ALICE_NAME, devMode = false, parameters = params).getOrThrow()

            assertThatThrownBy { devModeNode.attemptJavaDeserialization() }.isInstanceOf(CordaRuntimeException::class.java)
            assertThatThrownBy { node.attemptJavaDeserialization() }.isInstanceOf(InternalNodeException::class.java)
        }
    }

    @Test
    fun `double node start doesn't write into log file`() {
        driver(DriverParameters(notarySpecs = emptyList())) {
            val alice = startNode(providedName = ALICE_NAME).get()
            val logFolder = alice.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME
            val logFile = logFolder.list { it.filter { it.fileName.toString().endsWith(".log") }.findAny().get() }
            // Start second Alice, should fail
            assertThatThrownBy {
                startNode(providedName = ALICE_NAME).getOrThrow()
            }
            // We count the number of nodes that wrote into the logfile by counting "Logs can be found in"
            val numberOfNodesThatLogged = logFile.readLines { it.filter { NodeStartup.LOGS_CAN_BE_FOUND_IN_STRING in it }.count() }
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
