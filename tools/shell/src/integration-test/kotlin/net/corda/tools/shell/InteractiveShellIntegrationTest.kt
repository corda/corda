package net.corda.tools.shell

import co.paralleluniverse.fibers.Suspendable
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.mock
import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.RPCException
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.inputStream
import net.corda.core.internal.list
import net.corda.core.internal.messaging.InternalCordaRPCOps
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
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
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.NodeHandleInternal
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.useSslRpcOverrides
import net.corda.testing.node.User
import net.corda.tools.shell.utlities.ANSIProgressRenderer
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.util.io.Streams
import org.crsh.text.RenderPrintWriter
import org.junit.ClassRule
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.*
import java.util.zip.ZipInputStream
import javax.security.auth.x500.X500Principal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InteractiveShellIntegrationTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME, BOB_NAME, DUMMY_BANK_A_NAME, DUMMY_NOTARY_NAME)
    }

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
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            startShell("fake", "fake", node.rpcAddress)
            assertThatThrownBy { InteractiveShell.nodeInfo() }.isInstanceOf(ActiveMQSecurityException::class.java)
        }
    }

    @Test
    fun `shell should log in with valid credentials`() {
        val user = User("u", "p", setOf())
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            startShell(node)
            InteractiveShell.nodeInfo()
        }
    }

    @Test
    fun `shell should log in with ssl`() {
        val user = User("mark", "dadada", setOf(all()))

        val (keyPair, cert) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val keyStorePath = saveToKeyStore(tempFolder.root.toPath() / "keystore.jks", keyPair, cert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")

        val trustStorePath = saveToTrustStore(tempFolder.root.toPath() / "truststore.jks", cert)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode(rpcUsers = listOf(user), customOverrides = brokerSslOptions.useSslRpcOverrides()).getOrThrow()
            startShell(node, clientSslOptions)
            InteractiveShell.nodeInfo()
        }
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
            val node = startNode(rpcUsers = listOf(user), customOverrides = brokerSslOptions.useSslRpcOverrides()).getOrThrow()
            startShell(node, clientSslOptions)
            assertThatThrownBy { InteractiveShell.nodeInfo() }.isInstanceOf(RPCException::class.java)
        }
    }

    @Test
    fun `internal shell user should not be able to connect if node started with devMode=false`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode().getOrThrow()
            val conf = (node as NodeHandleInternal).configuration.toShellConfig()
            InteractiveShell.startShell(conf)
            assertThatThrownBy { InteractiveShell.nodeInfo() }.isInstanceOf(ActiveMQSecurityException::class.java)
        }
    }

    @Ignore
    @Test
    fun `ssh runs flows via standalone shell`() {
        val user = User("u", "p", setOf(
                Permissions.startFlow<FlowICanRun>(),
                Permissions.invokeRpc(CordaRPCOps::registeredFlows),
                Permissions.invokeRpc(CordaRPCOps::nodeInfo)
        ))
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            startShell(node, sshdPort = 2224)
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

            val linesWithDoneCount = response.lines().filter { line -> "Done" in line }

            channel.disconnect()
            session.disconnect()

            // There are ANSI control characters involved, so we want to avoid direct byte to byte matching.
            assertThat(linesWithDoneCount).hasSize(1)
        }
    }

    @Ignore
    @Test
    fun `ssh run flows via standalone shell over ssl to node`() {
        val user = User("mark", "dadada", setOf(
                Permissions.startFlow<FlowICanRun>(),
                Permissions.invokeRpc(CordaRPCOps::registeredFlows),
                Permissions.invokeRpc(CordaRPCOps::nodeInfo)/*all()*/
        ))

        val (keyPair, cert) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val keyStorePath = saveToKeyStore(tempFolder.root.toPath() / "keystore.jks", keyPair, cert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")
        val trustStorePath = saveToTrustStore(tempFolder.root.toPath() / "truststore.jks", cert)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        var successful = false
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            startNode(rpcUsers = listOf(user), customOverrides = brokerSslOptions.useSslRpcOverrides()).getOrThrow().use { node ->
                startShell(node, clientSslOptions, sshdPort = 2223)
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

                val linesWithDoneCount = response.lines().filter { line -> "Done" in line }

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
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            startShell(node)
            val (output, lines) = mockRenderPrintWriter()
            InteractiveShell.runFlowByNameFragment(NoOpFlow::class.java.name, "", output, node.rpc, mockAnsiProgressRenderer())
            assertThat(lines.last()).startsWith("Flow completed with result:")
        }
    }

    @Test
    fun `shell should start flow with unique un-qualified class name`() {
        val user = User("u", "p", setOf(all()))
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            startShell(node)
            val (output, lines) = mockRenderPrintWriter()
            InteractiveShell.runFlowByNameFragment("NoOpFlowA", "", output, node.rpc, mockAnsiProgressRenderer())
            assertThat(lines.last()).startsWith("Flow completed with result:")
        }
    }

    @Test
    fun `shell should fail to start flow with ambiguous class name`() {
        val user = User("u", "p", setOf(all()))
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            startShell(node)
            val (output, lines) = mockRenderPrintWriter()
            InteractiveShell.runFlowByNameFragment("NoOpFlo", "", output, node.rpc, mockAnsiProgressRenderer())
            assertThat(lines.any { it.startsWith("Ambiguous name provided, please be more specific.") }).isTrue()
        }
    }

    @Test
    fun `shell should start flow with partially matching class name`() {
        val user = User("u", "p", setOf(all()))
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            startShell(node)
            val (output, lines) = mockRenderPrintWriter()
            InteractiveShell.runFlowByNameFragment("Burble", "", output, node.rpc, mockAnsiProgressRenderer())
            assertThat(lines.last()).startsWith("Flow completed with result")
        }
    }

    @Test
    fun `dumpCheckpoints creates zip with json file for suspended flow`() {
        val user = User("u", "p", setOf(all()))
        driver(DriverParameters(startNodesInProcess = true)) {
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val bobNode = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()
            bobNode.stop()

            // Create logs directory since the driver is not creating it
            (aliceNode.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).createDirectories()

            startShell(aliceNode)

            val linearId = UniqueIdentifier(id = UUID.fromString("7c0719f0-e489-46e8-bf3b-ee203156fc7c"))
            aliceNode.rpc.startFlow(
                    ::FlowForCheckpointDumping,
                    MyState(
                            "some random string",
                            linearId,
                            listOf(aliceNode.nodeInfo.singleIdentity(), bobNode.nodeInfo.singleIdentity())
                    ),
                    bobNode.nodeInfo.singleIdentity()
            )

            Thread.sleep(5000)

            val (output) = mockRenderPrintWriter()
            InteractiveShell.runRPCFromString(listOf("dumpCheckpoints"), output, mock(), aliceNode.rpc as InternalCordaRPCOps, inputObjectMapper)

            val zipFile = (aliceNode.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).list().first { "checkpoints_dump-" in it.toString() }
            val json = ZipInputStream(zipFile.inputStream()).use { zip ->
                zip.nextEntry
                ObjectMapper().readTree(zip)
            }

            assertNotNull(json["flowId"].asText())
            assertEquals(FlowForCheckpointDumping::class.java.name, json["topLevelFlowClass"].asText())
            assertEquals(linearId.id.toString(), json["topLevelFlowLogic"]["myState"]["linearId"]["id"].asText())
            assertEquals(4, json["flowCallStackSummary"].size())
            assertEquals(4, json["flowCallStack"].size())
            val sendAndReceiveJson = json["suspendedOn"]["sendAndReceive"][0]
            assertEquals(bobNode.nodeInfo.singleIdentity().toString(), sendAndReceiveJson["session"]["peer"].asText())
            assertEquals(SignedTransaction::class.qualifiedName, sendAndReceiveJson["sentPayloadType"].asText())
        }
    }

    private fun startShell(node: NodeHandle, ssl: ClientRpcSslOptions? = null, sshdPort: Int? = null) {
        val user = node.rpcUsers[0]
        startShell(user.username, user.password, node.rpcAddress, ssl, sshdPort)
    }

    private fun startShell(user: String, password: String, address: NetworkHostAndPort, ssl: ClientRpcSslOptions? = null, sshdPort: Int? = null) {
        val conf = ShellConfiguration(
                commandsDirectory = tempFolder.newFolder().toPath(),
                user = user,
                password = password,
                hostAndPort = address,
                ssl = ssl,
                sshdPort = sshdPort
        )
        InteractiveShell.startShell(conf)
    }

    private fun mockRenderPrintWriter(): Pair<RenderPrintWriter, List<String>> {
        val lines = ArrayList<String>()
        val writer = mock<RenderPrintWriter> {
            on { println(any<String>()) } doAnswer {
                val line = it.getArgument(0, String::class.java)
                println(">>> $line")
                lines += line
                Unit
            }
        }
        return Pair(writer, lines)
    }

    private fun mockAnsiProgressRenderer(): ANSIProgressRenderer {
        return mock {
            on { render(any(), any()) } doAnswer { InteractiveShell.latch.countDown() }
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

@InitiatingFlow
@StartableByRPC
class FlowForCheckpointDumping(private val myState: MyState, private val party: Party): FlowLogic<Unit>() {
    // Make sure any SerializeAsToken instances are not serialised
    private var services: ServiceHub? = null

    @Suspendable
    override fun call() {
        services = serviceHub
        val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first()).apply {
            addOutputState(myState)
            addCommand(MyContract.Create(), listOf(ourIdentity, party).map(Party::owningKey))
        }
        val sessions = listOf(initiateFlow(party))
        val stx = serviceHub.signInitialTransaction(tx)
        subFlow(CollectSignaturesFlow(stx, sessions))
        throw IllegalStateException("The test should not get here")
    }
}

@InitiatedBy(FlowForCheckpointDumping::class)
class FlowForCheckpointDumpingResponder(private val session: FlowSession): FlowLogic<Unit>() {
    override fun call() {
        val signTxFlow = object : SignTransactionFlow(session) {
            override fun checkTransaction(stx: SignedTransaction) {

            }
        }
        subFlow(signTxFlow)
        throw IllegalStateException("The test should not get here")
    }
}

class MyContract : Contract {
    class Create : CommandData
    override fun verify(tx: LedgerTransaction) {}
}

@BelongsToContract(MyContract::class)
data class MyState(
        val data: String,
        override val linearId: UniqueIdentifier,
        override val participants: List<AbstractParty>
) : LinearState
