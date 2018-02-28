package net.corda.shell

import com.google.common.io.Files
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import net.corda.client.rpc.internal.createCordaRPCClientWithSslAndClassLoader
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.node.services.Permissions.Companion.all
import net.corda.testing.common.internal.withCertificates
import net.corda.testing.common.internal.withKeyStores
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.internal.useSslRpcOverrides
import net.corda.testing.node.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.util.io.Streams
import org.junit.Test
import java.lang.reflect.UndeclaredThrowableException
import kotlin.test.assertTrue


class InteractiveShellIntegrationTest {

    @Test
    fun `shell should not log in with invalid credentials`() {
        val user = User("u", "p", setOf())
        driver(DriverParameters(isDebug = true, startNodesInProcess = true, portAllocation = PortAllocation.RandomFree)) {
            val nodeFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val node = nodeFuture.getOrThrow()

            val conf = ShellConfiguration(Files.createTempDir().toPath(),
                    "fake", "fake",
                    node.rpcAddress,
                    null, null, false)
            InteractiveShell.startShell(conf,
                    { username: String?, credentials: String? ->
                        val client = createCordaRPCClientWithSslAndClassLoader(conf.hostAndPort)
                        client.start(username ?: "", credentials ?: "").proxy
                    })
            assertThatThrownBy {
                    InteractiveShell.nodeInfo()
            }.isInstanceOf(UndeclaredThrowableException::class.java)
        }
    }

    @Test
    fun `shell should log in with valid crentials`() {
        val user = User("u", "p", setOf())
        driver {
            val nodeFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val node = nodeFuture.getOrThrow()

            val conf = ShellConfiguration(Files.createTempDir().toPath(),
                    user.username, user.password,
                    node.rpcAddress,
                    null, null, false)

            InteractiveShell.startShell(conf,
                    { username: String?, credentials: String? ->
                        val client = createCordaRPCClientWithSslAndClassLoader(conf.hostAndPort)
                        client.start(username ?: "", credentials ?: "").proxy
                    })
            InteractiveShell.nodeInfo()
        }
    }

    @Test
    fun `shell should log in with ssl`() {
        val user = User("mark", "dadada", setOf(all()))
        withCertificates { server, client, createSelfSigned, createSignedBy ->
            val rootCertificate = createSelfSigned(CordaX500Name("SystemUsers/Node", "IT", "R3 London", "London", "London", "GB"))
            val markCertificate = createSignedBy(CordaX500Name("shell", "IT", "R3 London", "London", "London", "GB"), rootCertificate)

            // truststore needs to contain root CA for how the driver works...
            server.keyStore["cordaclienttls"] = rootCertificate
            server.trustStore["cordaclienttls"] = rootCertificate
            server.trustStore["shell"] = markCertificate

            client.keyStore["shell"] = markCertificate
            client.trustStore["cordaclienttls"] = rootCertificate

            withKeyStores(server, client) { nodeSslOptions, clientSslOptions ->
                var successful = false
                driver(DriverParameters(isDebug = true, startNodesInProcess = true, portAllocation = PortAllocation.RandomFree)) {
                    startNode(rpcUsers = listOf(user), customOverrides = nodeSslOptions.useSslRpcOverrides()).getOrThrow().use { node ->

                        val sslConfiguration = ShellSslOptions(clientSslOptions.certificatesDirectory,
                                clientSslOptions.keyStorePassword, clientSslOptions.trustStorePassword)
                        val conf = ShellConfiguration(Files.createTempDir().toPath(),
                                user.username, user.password,
                                node.rpcAddress,
                                sslConfiguration, null, false)

                        InteractiveShell.startShell(conf,
                                { username: String?, credentials: String? ->
                                    val client = createCordaRPCClientWithSslAndClassLoader(conf.hostAndPort, sslConfiguration = sslConfiguration)
                                    client.start(username ?: "", credentials ?: "").proxy
                                })
                        InteractiveShell.nodeInfo()
                        successful = true

                    }
                }
                assertThat(successful).isTrue()
            }
        }
    }

    @Test
    fun `shell shoud not log in without ssl keystore`() {
        val user = User("mark", "dadada", setOf("ALL"))
        withCertificates { server, client, createSelfSigned, createSignedBy ->
            val rootCertificate = createSelfSigned(CordaX500Name("SystemUsers/Node", "IT", "R3 London", "London", "London", "GB"))
            val markCertificate = createSignedBy(CordaX500Name("shell", "IT", "R3 London", "London", "London", "GB"), rootCertificate)

            // truststore needs to contain root CA for how the driver works...
            server.keyStore["cordaclienttls"] = rootCertificate
            server.trustStore["cordaclienttls"] = rootCertificate
            server.trustStore["shell"] = markCertificate

            //client key store doesn't have "mark" certificate
            client.trustStore["cordaclienttls"] = rootCertificate

            withKeyStores(server, client) { nodeSslOptions, clientSslOptions ->
                var successful = false
                driver(DriverParameters(isDebug = true, startNodesInProcess = true, portAllocation = PortAllocation.RandomFree)) {
                    startNode(rpcUsers = listOf(user), customOverrides = nodeSslOptions.useSslRpcOverrides()).getOrThrow().use { node ->

                        val sslConfiguration = ShellSslOptions(clientSslOptions.certificatesDirectory,
                                clientSslOptions.keyStorePassword, clientSslOptions.trustStorePassword)

                        val conf = net.corda.shell.ShellConfiguration(Files.createTempDir().toPath(),
                                user.username, user.password,
                                node.rpcAddress,
                                sslConfiguration, null, false)

                        InteractiveShell.startShell(conf,
                                { username: String?, credentials: String? ->
                                    val client = createCordaRPCClientWithSslAndClassLoader(conf.hostAndPort, sslConfiguration = sslConfiguration)
                                    client.start(username ?: "", credentials ?: "").proxy
                                })

                        try {
                            InteractiveShell.nodeInfo()
                            successful = true

                        } catch (e: Exception) {
                        }
                    }
                }
                assertThat(successful).isFalse()
            }
        }
    }

    @Test
    fun `ssh runs flows via standalone shell`() {
        val user = User("u", "p", setOf(Permissions.startFlow<SSHServerTest.FlowICanRun>(),
                Permissions.invokeRpc(CordaRPCOps::registeredFlows),
                Permissions.invokeRpc(CordaRPCOps::nodeInfo)))
        driver {
            val nodeFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val node = nodeFuture.getOrThrow()

            val conf = net.corda.shell.ShellConfiguration(Files.createTempDir().toPath(),
                    user.username, user.password,
                    node.rpcAddress,
                    null, 2224, false)

            InteractiveShell.startShell(conf,
                    { username: String?, credentials: String? ->
                        val client = createCordaRPCClientWithSslAndClassLoader(conf.hostAndPort)
                        client.start(username ?: "", credentials ?: "").proxy
                    })
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

    @Test
    fun `ssh run flows via standalone shell over ssl to node`() {
        val user = User("mark", "dadada", setOf(Permissions.startFlow<SSHServerTest.FlowICanRun>(),
                Permissions.invokeRpc(CordaRPCOps::registeredFlows),
                Permissions.invokeRpc(CordaRPCOps::nodeInfo)/*all()*/))
        withCertificates { server, client, createSelfSigned, createSignedBy ->
            val rootCertificate = createSelfSigned(CordaX500Name("SystemUsers/Node", "IT", "R3 London", "London", "London", "GB"))
            val markCertificate = createSignedBy(CordaX500Name("shell", "IT", "R3 London", "London", "London", "GB"), rootCertificate)

            // truststore needs to contain root CA for how the driver works...
            server.keyStore["cordaclienttls"] = rootCertificate
            server.trustStore["cordaclienttls"] = rootCertificate
            server.trustStore["shell"] = markCertificate

            client.keyStore["shell"] = markCertificate
            client.trustStore["cordaclienttls"] = rootCertificate

            withKeyStores(server, client) { nodeSslOptions, clientSslOptions ->
                var successful = false
                driver(DriverParameters(isDebug = true, startNodesInProcess = true, portAllocation = PortAllocation.RandomFree)) {
                    startNode(rpcUsers = listOf(user), customOverrides = nodeSslOptions.useSslRpcOverrides()).getOrThrow().use { node ->

                        val sslConfiguration = ShellSslOptions(clientSslOptions.certificatesDirectory,
                                clientSslOptions.keyStorePassword, clientSslOptions.trustStorePassword)
                        val conf = ShellConfiguration(Files.createTempDir().toPath(),
                                user.username, user.password,
                                node.rpcAddress,
                                sslConfiguration, 2223, false)

                        InteractiveShell.startShell(conf,
                                { username: String?, credentials: String? ->
                                    val client = createCordaRPCClientWithSslAndClassLoader(conf.hostAndPort, sslConfiguration = sslConfiguration)
                                    client.start(username ?: "", credentials ?: "").proxy
                                })
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
                }
                assertThat(successful).isTrue()
            }
        }
    }
}