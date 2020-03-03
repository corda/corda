package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaRuntimeException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.*
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.NodeStartup
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.nodeapi.internal.crypto.X509Utilities.NODE_IDENTITY_KEY_ALIAS
import net.corda.nodeapi.internal.installDevNodeCaCertPath
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.testing.node.User
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.testing.node.internal.startNode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BootTests {
    @Test(timeout=300_000)
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
        driver(DriverParameters(cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val devModeNode = startNode(devParams).getOrThrow()
            val node = startNode(ALICE_NAME, devMode = false, parameters = params).getOrThrow()

            assertThatThrownBy { devModeNode.attemptJavaDeserialization() }.isInstanceOf(CordaRuntimeException::class.java)
            assertThatThrownBy { node.attemptJavaDeserialization() }.isInstanceOf(CordaRuntimeException::class.java)
        }
    }

    @Test(timeout=300_000)
	fun `double node start doesn't write into log file`() {
        driver(DriverParameters(notarySpecs = emptyList(), cordappsForAllNodes = emptyList())) {
            val alice = startNode(providedName = ALICE_NAME).get()
            val logFolder = alice.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME
            val logFile = logFolder.list { it.filter { a -> a.isRegularFile() && a.fileName.toString().startsWith("node") }.findFirst().get() }
            // Start second Alice, should fail
            assertThatThrownBy {
                startNode(providedName = ALICE_NAME).getOrThrow()
            }
            // We count the number of nodes that wrote into the logfile by counting "Logs can be found in"
            val numberOfNodesThatLogged = logFile.readLines { it.filter { NodeStartup.LOGS_CAN_BE_FOUND_IN_STRING in it }.count() }
            assertEquals(1, numberOfNodesThatLogged)
        }
    }

    @Test(timeout=300_000)
	fun `node fails to start if legal identity is lost`() {
        driver(DriverParameters(
                notarySpecs = emptyList(),
                inMemoryDB = false,
                startNodesInProcess = false,
                cordappsForAllNodes = emptyList()
        )) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val aliceCertDir = alice.baseDirectory / "certificates"
            (aliceCertDir / "nodekeystore.jks").delete()
            val cert = CertificateStoreStubs.Signing.withCertificatesDirectory(aliceCertDir).get(true)
            // Creating a new certificate store does not populate that store with the node certificate path. If the node certificate path is
            // missing, the node will fail to start but not because the legal identity is missing. To test that a missing legal identity
            // prevents the node from starting, the node certificate path must be installed.
            cert.installDevNodeCaCertPath(ALICE_NAME)
            alice.stop()
            // The node shouldn't start, and the logs should indicate that the failure is due to a missing identity key
            assertThatThrownBy {
                startNode(providedName = ALICE_NAME).getOrThrow()
            }
            val logFolder = alice.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME
            val logFile = logFolder.list { it.filter { a -> a.isRegularFile() && a.fileName.toString().startsWith("node") }.findFirst().get() }
            val lines = logFile.readLines { lines -> lines.filter { NODE_IDENTITY_KEY_ALIAS in it }.toArray() }
            assertTrue(lines.count() > 0)
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


}