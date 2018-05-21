/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.tools.shell

import com.google.common.io.Files
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import net.corda.core.internal.div
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.node.services.Permissions.Companion.all
import net.corda.node.services.config.shell.toShellConfig
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.NodeHandleInternal
import net.corda.testing.driver.internal.RandomFree
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.internal.createKeyPairAndSelfSignedCertificate
import net.corda.testing.internal.saveToKeyStore
import net.corda.testing.internal.saveToTrustStore
import net.corda.testing.internal.useSslRpcOverrides
import net.corda.testing.node.User
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.util.io.Streams
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertTrue

class InteractiveShellIntegrationTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(*listOf(ALICE_NAME, BOB_NAME, DUMMY_BANK_A_NAME, DUMMY_NOTARY_NAME)
                .map { it.toDatabaseSchemaName() }.toTypedArray())
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `shell should not log in with invalid credentials`() {
        val user = User("u", "p", setOf())
        driver(DriverParameters(isDebug = true, startNodesInProcess = true, portAllocation = RandomFree)) {
            val nodeFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val node = nodeFuture.getOrThrow()

            val conf = ShellConfiguration(commandsDirectory = Files.createTempDir().toPath(),
                    user = "fake", password = "fake",
                    hostAndPort = node.rpcAddress)
            InteractiveShell.startShell(conf)

            assertThatThrownBy { InteractiveShell.nodeInfo() }.isInstanceOf(ActiveMQSecurityException::class.java)
        }
    }

    @Test
    fun `shell should log in with valid credentials`() {
        val user = User("u", "p", setOf())
        driver {
            val nodeFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val node = nodeFuture.getOrThrow()

            val conf = ShellConfiguration(commandsDirectory = Files.createTempDir().toPath(),
                    user = user.username, password = user.password,
                    hostAndPort = node.rpcAddress)

            InteractiveShell.startShell(conf)
            InteractiveShell.nodeInfo()
        }
    }

    @Test
    fun `shell should log in with ssl`() {
        val user = User("mark", "dadada", setOf(all()))
        var successful = false

        val (keyPair, cert) = createKeyPairAndSelfSignedCertificate()
        val keyStorePath = saveToKeyStore(tempFolder.root.toPath() / "keystore.jks", keyPair, cert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")

        val trustStorePath = saveToTrustStore(tempFolder.root.toPath() / "truststore.jks", cert)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        driver(DriverParameters(isDebug = true, startNodesInProcess = true, portAllocation = RandomFree)) {
            startNode(rpcUsers = listOf(user), customOverrides = brokerSslOptions.useSslRpcOverrides()).getOrThrow().use { node ->

                val conf = ShellConfiguration(commandsDirectory = Files.createTempDir().toPath(),
                        user = user.username, password = user.password,
                        hostAndPort = node.rpcAddress,
                        ssl = clientSslOptions)

                InteractiveShell.startShell(conf)

                InteractiveShell.nodeInfo()
                successful = true
            }
        }
        assertThat(successful).isTrue()
    }

    @Test
    fun `shell shoud not log in with invalid truststore`() {
        val user = User("mark", "dadada", setOf("ALL"))
        val (keyPair, cert) = createKeyPairAndSelfSignedCertificate()
        val keyStorePath = saveToKeyStore(tempFolder.root.toPath() / "keystore.jks", keyPair, cert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")

        val (_, cert1) = createKeyPairAndSelfSignedCertificate()
        val trustStorePath = saveToTrustStore(tempFolder.root.toPath() / "truststore.jks", cert1)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        driver(DriverParameters(isDebug = true, startNodesInProcess = true, portAllocation = RandomFree)) {
            startNode(rpcUsers = listOf(user), customOverrides = brokerSslOptions.useSslRpcOverrides()).getOrThrow().use { node ->

                val conf = ShellConfiguration(commandsDirectory = Files.createTempDir().toPath(),
                        user = user.username, password = user.password,
                        hostAndPort = node.rpcAddress,
                        ssl = clientSslOptions)

                InteractiveShell.startShell(conf)

                assertThatThrownBy { InteractiveShell.nodeInfo() }.isInstanceOf(ActiveMQNotConnectedException::class.java)
            }
        }
    }

    @Test
    fun `internal shell user should not be able to connect if node started with devMode=false`() {
        driver(DriverParameters(isDebug = true, startNodesInProcess = true, portAllocation = RandomFree)) {
            startNode().getOrThrow().use { node ->
                val conf = (node as NodeHandleInternal).configuration.toShellConfig()
                InteractiveShell.startShellInternal(conf)
                assertThatThrownBy { InteractiveShell.nodeInfo() }.isInstanceOf(ActiveMQSecurityException::class.java)
            }
        }
    }

    @Ignore
    @Test
    fun `ssh runs flows via standalone shell`() {
        val user = User("u", "p", setOf(Permissions.startFlow<SSHServerTest.FlowICanRun>(),
                Permissions.invokeRpc(CordaRPCOps::registeredFlows),
                Permissions.invokeRpc(CordaRPCOps::nodeInfo)))
        driver {
            val nodeFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val node = nodeFuture.getOrThrow()

            val conf = ShellConfiguration(commandsDirectory = Files.createTempDir().toPath(),
                    user = user.username, password = user.password,
                    hostAndPort = node.rpcAddress,
                    sshdPort = 2224)

            InteractiveShell.startShell(conf)
            InteractiveShell.nodeInfo()

            val session = JSch().getSession("u", "localhost", 2224)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setPassword("p")
            session.connect()

            assertTrue(session.isConnected)

            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand("start FlowICanRun")
            channel.connect(5000)

            assertTrue(channel.isConnected)

            val response = String(Streams.readAll(channel.inputStream))

            val linesWithDoneCount = response.lines().filter { line -> line.contains("Done") }

            channel.disconnect()
            session.disconnect()

            // There are ANSI control characters involved, so we want to avoid direct byte to byte matching.
            assertThat(linesWithDoneCount).hasSize(1)
        }
    }

    @Ignore
    @Test
    fun `ssh run flows via standalone shell over ssl to node`() {
        val user = User("mark", "dadada", setOf(Permissions.startFlow<SSHServerTest.FlowICanRun>(),
                Permissions.invokeRpc(CordaRPCOps::registeredFlows),
                Permissions.invokeRpc(CordaRPCOps::nodeInfo)/*all()*/))

        val (keyPair, cert) = createKeyPairAndSelfSignedCertificate()
        val keyStorePath = saveToKeyStore(tempFolder.root.toPath() / "keystore.jks", keyPair, cert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")
        val trustStorePath = saveToTrustStore(tempFolder.root.toPath() / "truststore.jks", cert)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        var successful = false
        driver(DriverParameters(isDebug = true, startNodesInProcess = true, portAllocation = RandomFree)) {
            startNode(rpcUsers = listOf(user), customOverrides = brokerSslOptions.useSslRpcOverrides()).getOrThrow().use { node ->

                val conf = ShellConfiguration(commandsDirectory = Files.createTempDir().toPath(),
                        user = user.username, password = user.password,
                        hostAndPort = node.rpcAddress,
                        ssl = clientSslOptions,
                        sshdPort = 2223)

                InteractiveShell.startShell(conf)
                InteractiveShell.nodeInfo()

                val session = JSch().getSession("mark", "localhost", 2223)
                session.setConfig("StrictHostKeyChecking", "no")
                session.setPassword("dadada")
                session.connect()

                assertTrue(session.isConnected)

                val channel = session.openChannel("exec") as ChannelExec
                channel.setCommand("start FlowICanRun")
                channel.connect(5000)

                assertTrue(channel.isConnected)

                val response = String(Streams.readAll(channel.inputStream))

                val linesWithDoneCount = response.lines().filter { line -> line.contains("Done") }

                channel.disconnect()
                session.disconnect() // TODO Simon make sure to close them

                // There are ANSI control characters involved, so we want to avoid direct byte to byte matching.
                assertThat(linesWithDoneCount).hasSize(1)

                successful = true
            }

            assertThat(successful).isTrue()

        }
    }
}