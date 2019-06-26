package net.corda.tools.shell

import co.paralleluniverse.fibers.Suspendable
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import com.google.common.io.Files
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.mock
import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.RPCException
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.internal.messaging.InternalCordaRPCOps
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.internal.NodeStartup
import net.corda.node.services.Permissions
import net.corda.node.services.Permissions.Companion.all
import net.corda.node.services.config.shell.toShellConfig
import net.corda.node.utilities.createKeyPairAndSelfSignedTLSCertificate
import net.corda.node.utilities.saveToKeyStore
import net.corda.node.utilities.saveToTrustStore
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.NodeHandleInternal
import net.corda.testing.internal.useSslRpcOverrides
import net.corda.testing.node.User
import net.corda.tools.shell.utlities.ANSIProgressRenderer
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.util.io.Streams
import org.crsh.text.RenderPrintWriter
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipFile
import javax.security.auth.x500.X500Principal
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InteractiveShellIntegrationTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val testName = X500Principal("CN=Test,O=R3 Ltd,L=London,C=GB")

    private lateinit var inputObjectMapper: ObjectMapper

    @Before
    fun setup() {
        inputObjectMapper = objectMapperWithClassLoader(InteractiveShell.getCordappsClassloader())
    }

    @Test
    fun `shell should not log in with invalid credentials`() {
        val user = User("u", "p", setOf())
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
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
        driver(DriverParameters(notarySpecs = emptyList())) {
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

        val (keyPair, cert) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val keyStorePath = saveToKeyStore(tempFolder.root.toPath() / "keystore.jks", keyPair, cert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")

        val trustStorePath = saveToTrustStore(tempFolder.root.toPath() / "truststore.jks", cert)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
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
        val (keyPair, cert) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val keyStorePath = saveToKeyStore(tempFolder.root.toPath() / "keystore.jks", keyPair, cert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")

        val (_, cert1) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val trustStorePath = saveToTrustStore(tempFolder.root.toPath() / "truststore.jks", cert1)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            startNode(rpcUsers = listOf(user), customOverrides = brokerSslOptions.useSslRpcOverrides()).getOrThrow().use { node ->

                val conf = ShellConfiguration(commandsDirectory = Files.createTempDir().toPath(),
                        user = user.username, password = user.password,
                        hostAndPort = node.rpcAddress,
                        ssl = clientSslOptions)

                InteractiveShell.startShell(conf)

                assertThatThrownBy { InteractiveShell.nodeInfo() }.isInstanceOf(RPCException::class.java)
            }
        }
    }

    @Test
    fun `internal shell user should not be able to connect if node started with devMode=false`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            startNode().getOrThrow().use { node ->
                val conf = (node as NodeHandleInternal).configuration.toShellConfig()
                InteractiveShell.startShell(conf)
                assertThatThrownBy { InteractiveShell.nodeInfo() }.isInstanceOf(ActiveMQSecurityException::class.java)
            }
        }
    }

    @Ignore
    @Test
    fun `ssh runs flows via standalone shell`() {
        val user = User("u", "p", setOf(Permissions.startFlow<FlowICanRun>(),
                Permissions.invokeRpc(CordaRPCOps::registeredFlows),
                Permissions.invokeRpc(CordaRPCOps::nodeInfo)))
        driver(DriverParameters(notarySpecs = emptyList())) {
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
        val user = User("mark", "dadada", setOf(Permissions.startFlow<FlowICanRun>(),
                Permissions.invokeRpc(CordaRPCOps::registeredFlows),
                Permissions.invokeRpc(CordaRPCOps::nodeInfo)/*all()*/))

        val (keyPair, cert) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val keyStorePath = saveToKeyStore(tempFolder.root.toPath() / "keystore.jks", keyPair, cert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")
        val trustStorePath = saveToTrustStore(tempFolder.root.toPath() / "truststore.jks", cert)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        var successful = false
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
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

    @Test
    fun `shell should start flow with fully qualified class name`() {
        val user = User("u", "p", setOf(all()))
        var successful = false
        driver(DriverParameters(notarySpecs = emptyList())) {
            val nodeFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val node = nodeFuture.getOrThrow()

            val conf = ShellConfiguration(commandsDirectory = Files.createTempDir().toPath(),
                    user = user.username, password = user.password,
                    hostAndPort = node.rpcAddress)
            InteractiveShell.startShell(conf)

            // setup and configure some mocks required by InteractiveShell.runFlowByNameFragment()
            val output = mock<RenderPrintWriter> {
                on { println(any<String>()) } doAnswer {
                    val line = it.arguments[0]
                    println("$line")
                    if ((line is String) && (line.startsWith("Flow completed with result:")))
                        successful = true
                }
            }
            val ansiProgressRenderer = mock<ANSIProgressRenderer> {
                on { render(any(), any()) }  doAnswer { InteractiveShell.latch.countDown() }
            }
            InteractiveShell.runFlowByNameFragment(
                    "NoOpFlow",
                    "", output, node.rpc, ansiProgressRenderer)
        }
        assertThat(successful).isTrue()
    }

    @Test
    fun `shell should start flow with unique un-qualified class name`() {
        val user = User("u", "p", setOf(all()))
        var successful = false
        driver(DriverParameters(notarySpecs = emptyList())) {
            val nodeFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val node = nodeFuture.getOrThrow()

            val conf = ShellConfiguration(commandsDirectory = Files.createTempDir().toPath(),
                    user = user.username, password = user.password,
                    hostAndPort = node.rpcAddress)
            InteractiveShell.startShell(conf)

            // setup and configure some mocks required by InteractiveShell.runFlowByNameFragment()
            val output = mock<RenderPrintWriter> {
                on { println(any<String>()) } doAnswer {
                    val line = it.arguments[0]
                    println("$line")
                    if ((line is String) && (line.startsWith("Flow completed with result:")))
                        successful = true
                }
            }
            val ansiProgressRenderer = mock<ANSIProgressRenderer> {
                on { render(any(), any()) }  doAnswer { InteractiveShell.latch.countDown() }
            }
            InteractiveShell.runFlowByNameFragment(
                    "NoOpFlowA",
                    "", output, node.rpc, ansiProgressRenderer)
        }
        assertThat(successful).isTrue()
    }

    @Test
    fun `shell should fail to start flow with ambiguous class name`() {
        val user = User("u", "p", setOf(all()))
        var successful = false
        driver(DriverParameters(notarySpecs = emptyList())) {
            val nodeFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val node = nodeFuture.getOrThrow()

            val conf = ShellConfiguration(commandsDirectory = Files.createTempDir().toPath(),
                    user = user.username, password = user.password,
                    hostAndPort = node.rpcAddress)
            InteractiveShell.startShell(conf)

            // setup and configure some mocks required by InteractiveShell.runFlowByNameFragment()
            val output = mock<RenderPrintWriter> {
                on { println(any<String>()) } doAnswer {
                    val line = it.arguments[0]
                    println("$line")
                    if ((line is String) && (line.startsWith("Ambiguous name provided, please be more specific.")))
                        successful = true
                }
            }
            val ansiProgressRenderer = mock<ANSIProgressRenderer> {
                on { render(any(), any()) }  doAnswer { InteractiveShell.latch.countDown() }
            }
            InteractiveShell.runFlowByNameFragment(
                    "NoOpFlo",
                    "", output, node.rpc, ansiProgressRenderer)
        }
        assertThat(successful).isTrue()
    }

    @Test
    fun `shell should start flow with partially matching class name`() {
        val user = User("u", "p", setOf(all()))
        var successful = false
        driver(DriverParameters(notarySpecs = emptyList())) {
            val nodeFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val node = nodeFuture.getOrThrow()

            val conf = ShellConfiguration(commandsDirectory = Files.createTempDir().toPath(),
                    user = user.username, password = user.password,
                    hostAndPort = node.rpcAddress)
            InteractiveShell.startShell(conf)

            // setup and configure some mocks required by InteractiveShell.runFlowByNameFragment()
            val output = mock<RenderPrintWriter> {
                on { println(any<String>()) } doAnswer {
                    val line = it.arguments[0]
                    println("$line")
                    if ((line is String) && (line.startsWith("Flow completed with result")))
                        successful = true
                }
            }
            val ansiProgressRenderer = mock<ANSIProgressRenderer> {
                on { render(any(), any()) }  doAnswer { InteractiveShell.latch.countDown() }
            }
            InteractiveShell.runFlowByNameFragment(
                    "Burble",
                    "", output, node.rpc, ansiProgressRenderer)
        }
        assertThat(successful).isTrue()
    }

    @Test
    fun `dumpCheckpoints creates zip with json file for suspended flow`() {
        val user = User("u", "p", setOf(all()))
        driver(DriverParameters(notarySpecs = emptyList())) {
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true).getOrThrow()
            val bobNode = startNode(providedName = BOB_NAME, rpcUsers = listOf(user), startInSameProcess = true).getOrThrow()
            bobNode.stop()

            // create logs directory since the driver is not creating it
            (aliceNode.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).toFile().mkdir()

            val conf = ShellConfiguration(commandsDirectory = Files.createTempDir().toPath(),
                    user = user.username, password = user.password,
                    hostAndPort = aliceNode.rpcAddress)
            InteractiveShell.startShell(conf)
            // setup and configure some mocks required by InteractiveShell.runFlowByNameFragment()
            val output = mock<RenderPrintWriter> {
                on { println(any<String>()) } doAnswer {
                    val line = it.arguments[0]
                    assertNotEquals("Please try 'man run' to learn what syntax is acceptable", line)
                }
            }

            aliceNode.rpc.startFlow(::SendFlow, bobNode.nodeInfo.singleIdentity())

            InteractiveShell.runRPCFromString(
                    listOf("dumpCheckpoints"), output, mock(), aliceNode.rpc as InternalCordaRPCOps, inputObjectMapper)

            // assert that the checkpoint dump zip has been created
            val zip = (aliceNode.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).list()
                    .find { it.toString().contains("checkpoints_dump-") }
            assertNotNull(zip)
            // assert that a json file has been created for the suspended flow
            val json = ZipFile((zip!!).toFile()).entries().asSequence()
                    .find { it.name.contains(SendFlow::class.simpleName!!) }
            assertNotNull(json)
        }
    }

    private fun objectMapperWithClassLoader(classLoader: ClassLoader?): ObjectMapper {
        val objectMapper = JacksonSupport.createNonRpcMapper()
        val tf = TypeFactory.defaultInstance().withClassLoader(classLoader)
        objectMapper.typeFactory = tf

        return objectMapper
    }
}

@Suppress("UNUSED")
@StartableByRPC
class NoOpFlow : FlowLogic<Unit>() {
    override val progressTracker = ProgressTracker()
    override fun call() {
        println("NO OP!")
    }
}

@Suppress("UNUSED")
@StartableByRPC
class NoOpFlowA : FlowLogic<Unit>() {
    override val progressTracker = ProgressTracker()
    override fun call() {
        println("NO OP! (A)")
    }
}

@Suppress("UNUSED")
@StartableByRPC
class BurbleFlow : FlowLogic<Unit>() {
    override val progressTracker = ProgressTracker()
    override fun call() {
        println("NO OP! (Burble)")
    }
}

@StartableByRPC
@InitiatingFlow
class SendFlow(private val party: Party) : FlowLogic<Unit>() {
    override val progressTracker = ProgressTracker()
    @Suspendable
    override fun call() {
        initiateFlow(party).sendAndReceive<String>("hi").unwrap { it }
    }
}

@InitiatedBy(SendFlow::class)
class ReceiveFlow(private val session: FlowSession) : FlowLogic<Unit>() {
    override val progressTracker = ProgressTracker()
    @Suspendable
    override fun call() {
        session.receive<String>().unwrap { it }
        session.send("hi")
    }
}

