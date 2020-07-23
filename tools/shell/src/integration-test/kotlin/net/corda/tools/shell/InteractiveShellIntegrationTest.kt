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
import net.corda.client.jackson.internal.valueAs
import net.corda.client.rpc.RPCException
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
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
import net.corda.core.utilities.seconds
import net.corda.node.internal.NodeStartup
import net.corda.node.services.Permissions
import net.corda.node.services.Permissions.Companion.all
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.node.services.config.shell.toShellConfig
import net.corda.node.utilities.createKeyPairAndSelfSignedTLSCertificate
import net.corda.node.utilities.saveToKeyStore
import net.corda.node.utilities.saveToTrustStore
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.NodeHandleInternal
import net.corda.testing.internal.useSslRpcOverrides
import net.corda.testing.node.User
import net.corda.testing.node.internal.enclosedCordapp
import net.corda.tools.shell.SSHServerTest.FlowICanRun
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
import org.junit.jupiter.api.assertThrows
import org.junit.rules.TemporaryFolder
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeoutException
import java.util.zip.ZipInputStream
import javax.security.auth.x500.X500Principal
import kotlin.test.assertEquals
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

    @Test(timeout=300_000)
	fun `shell should not log in with invalid credentials`() {
        val user = User("u", "p", setOf())
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = emptyList())) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            startShell("fake", "fake", node.rpcAddress)
            assertThatThrownBy { InteractiveShell.nodeInfo() }.isInstanceOf(ActiveMQSecurityException::class.java)
        }
    }

    @Test(timeout=300_000)
	fun `shell should log in with valid credentials`() {
        val user = User("u", "p", setOf())
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = emptyList())) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            startShell(node)
            InteractiveShell.nodeInfo()
        }
    }

    @Test(timeout=300_000)
	fun `shell should log in with ssl`() {
        val user = User("mark", "dadada", setOf(all()))

        val (keyPair, cert) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val keyStorePath = saveToKeyStore(tempFolder.root.toPath() / "keystore.jks", keyPair, cert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")

        val trustStorePath = saveToTrustStore(tempFolder.root.toPath() / "truststore.jks", cert)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = emptyList())) {
            val node = startNode(rpcUsers = listOf(user), customOverrides = brokerSslOptions.useSslRpcOverrides()).getOrThrow()
            startShell(node, clientSslOptions)
            InteractiveShell.nodeInfo()
        }
    }

    @Test(timeout=300_000)
	fun `shell shoud not log in with invalid truststore`() {
        val user = User("mark", "dadada", setOf("ALL"))
        val (keyPair, cert) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val keyStorePath = saveToKeyStore(tempFolder.root.toPath() / "keystore.jks", keyPair, cert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")

        val (_, cert1) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val trustStorePath = saveToTrustStore(tempFolder.root.toPath() / "truststore.jks", cert1)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = emptyList())) {
            val node = startNode(rpcUsers = listOf(user), customOverrides = brokerSslOptions.useSslRpcOverrides()).getOrThrow()
            startShell(node, clientSslOptions)
            assertThatThrownBy { InteractiveShell.nodeInfo() }.isInstanceOf(RPCException::class.java)
        }
    }

    @Test(timeout=300_000)
	fun `internal shell user should not be able to connect if node started with devMode=false`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = emptyList())) {
            val node = startNode().getOrThrow()
            val conf = (node as NodeHandleInternal).configuration.toShellConfig()
            InteractiveShell.startShell(conf)
            assertThatThrownBy { InteractiveShell.nodeInfo() }.isInstanceOf(ActiveMQSecurityException::class.java)
        }
    }

    @Ignore
    @Test(timeout=300_000)
	fun `ssh runs flows via standalone shell`() {
        val user = User("u", "p", setOf(
                startFlow<FlowICanRun>(),
                Permissions.invokeRpc(CordaRPCOps::registeredFlows),
                Permissions.invokeRpc(CordaRPCOps::nodeInfo)
        ))
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = listOf(enclosedCordapp()))) {
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
    @Test(timeout=300_000)
	fun `ssh run flows via standalone shell over ssl to node`() {
        val user = User("mark", "dadada", setOf(
                startFlow<FlowICanRun>(),
                Permissions.invokeRpc(CordaRPCOps::registeredFlows),
                Permissions.invokeRpc(CordaRPCOps::nodeInfo)/*all()*/
        ))

        val (keyPair, cert) = createKeyPairAndSelfSignedTLSCertificate(testName)
        val keyStorePath = saveToKeyStore(tempFolder.root.toPath() / "keystore.jks", keyPair, cert)
        val brokerSslOptions = BrokerRpcSslOptions(keyStorePath, "password")
        val trustStorePath = saveToTrustStore(tempFolder.root.toPath() / "truststore.jks", cert)
        val clientSslOptions = ClientRpcSslOptions(trustStorePath, "password")

        var successful = false
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = listOf(enclosedCordapp()))) {
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

    @Test(timeout=300_000)
	fun `shell should start flow with fully qualified class name`() {
        val user = User("u", "p", setOf(all()))
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            startShell(node)
            val (output, lines) = mockRenderPrintWriter()
            InteractiveShell.runFlowByNameFragment(NoOpFlow::class.java.name, "", output, node.rpc, mockAnsiProgressRenderer())
            assertThat(lines.last()).startsWith("Flow completed with result:")
        }
    }

    @Test(timeout=300_000)
	fun `shell should start flow with unique un-qualified class name`() {
        val user = User("u", "p", setOf(all()))
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            startShell(node)
            val (output, lines) = mockRenderPrintWriter()
            InteractiveShell.runFlowByNameFragment("NoOpFlowA", "", output, node.rpc, mockAnsiProgressRenderer())
            assertThat(lines.last()).startsWith("Flow completed with result:")
        }
    }

    @Test(timeout=300_000)
	fun `shell should fail to start flow with ambiguous class name`() {
        val user = User("u", "p", setOf(all()))
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            startShell(node)
            val (output, lines) = mockRenderPrintWriter()
            InteractiveShell.runFlowByNameFragment("NoOpFlo", "", output, node.rpc, mockAnsiProgressRenderer())
            assertThat(lines.any { it.startsWith("Ambiguous name provided, please be more specific.") }).isTrue()
        }
    }

    @Test(timeout=300_000)
	fun `shell should start flow with partially matching class name`() {
        val user = User("u", "p", setOf(all()))
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val node = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            startShell(node)
            val (output, lines) = mockRenderPrintWriter()
            InteractiveShell.runFlowByNameFragment("Burble", "", output, node.rpc, mockAnsiProgressRenderer())
            assertThat(lines.last()).startsWith("Flow completed with result")
        }
    }

    @Test(timeout=300_000)
	fun `dumpCheckpoints correctly serializes FlowExternalOperations`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            (alice.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).createDirectories()
            alice.rpc.startFlow(::ExternalOperationFlow)
            ExternalOperation.lock.acquire()
            InteractiveShell.runDumpCheckpoints(alice.rpc as InternalCordaRPCOps)
            ExternalOperation.lock2.release()

            val zipFile = (alice.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).list().first { "checkpoints_dump-" in it.toString() }
            val json = ZipInputStream(zipFile.inputStream()).use { zip ->
                zip.nextEntry
                ObjectMapper().readTree(zip)
            }

            assertEquals("hello there", json["suspendedOn"]["customOperation"]["operation"]["a"].asText())
            assertEquals(123, json["suspendedOn"]["customOperation"]["operation"]["b"].asInt())
            assertEquals("please work", json["suspendedOn"]["customOperation"]["operation"]["c"]["d"].asText())
            assertEquals("I beg you", json["suspendedOn"]["customOperation"]["operation"]["c"]["e"].asText())
        }
    }

    @Test(timeout=300_000)
	fun `dumpCheckpoints correctly serializes FlowExternalAsyncOperations`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            (alice.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).createDirectories()
            alice.rpc.startFlow(::ExternalAsyncOperationFlow)
            ExternalAsyncOperation.lock.acquire()
            InteractiveShell.runDumpCheckpoints(alice.rpc as InternalCordaRPCOps)
            ExternalAsyncOperation.future.complete(null)
            val zipFile = (alice.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).list().first { "checkpoints_dump-" in it.toString() }
            val json = ZipInputStream(zipFile.inputStream()).use { zip ->
                zip.nextEntry
                ObjectMapper().readTree(zip)
            }

            assertEquals("hello there", json["suspendedOn"]["customOperation"]["operation"]["a"].asText())
            assertEquals(123, json["suspendedOn"]["customOperation"]["operation"]["b"].asInt())
            assertEquals("please work", json["suspendedOn"]["customOperation"]["operation"]["c"]["d"].asText())
            assertEquals("I beg you", json["suspendedOn"]["customOperation"]["operation"]["c"]["e"].asText())
        }
    }

    @Test(timeout=300_000)
	fun `dumpCheckpoints correctly serializes WaitForStateConsumption`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            (alice.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).createDirectories()
            val stateRefs = setOf(
                StateRef(SecureHash.randomSHA256(), 0),
                StateRef(SecureHash.randomSHA256(), 1),
                StateRef(SecureHash.randomSHA256(), 2)
            )
            assertThrows<TimeoutException> {
                alice.rpc.startFlow(::WaitForStateConsumptionFlow, stateRefs).returnValue.getOrThrow(10.seconds)
            }
            InteractiveShell.runDumpCheckpoints(alice.rpc as InternalCordaRPCOps)
            val zipFile = (alice.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).list().first { "checkpoints_dump-" in it.toString() }
            val json = ZipInputStream(zipFile.inputStream()).use { zip ->
                zip.nextEntry
                ObjectMapper().readTree(zip)
            }

            assertEquals(stateRefs, json["suspendedOn"]["waitForStateConsumption"].valueAs<List<StateRef>>(inputObjectMapper).toSet())
        }
    }

    @Test(timeout=300_000)
	fun `dumpCheckpoints creates zip with json file for suspended flow`() {
        val user = User("u", "p", setOf(all()))
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val (aliceNode, bobNode) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it, rpcUsers = listOf(user)) }
                    .transpose()
                    .getOrThrow()
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

            mockRenderPrintWriter()
            InteractiveShell.runDumpCheckpoints(aliceNode.rpc as InternalCordaRPCOps)

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

    @StartableByRPC
    class ExternalAsyncOperationFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            await(ExternalAsyncOperation("hello there", 123, Data("please work", "I beg you")))
        }
    }

    class ExternalAsyncOperation(val a: String, val b: Int, val c: Data): FlowExternalAsyncOperation<Unit> {

        companion object {
            val future = CompletableFuture<Unit>()
            val lock = Semaphore(0)
        }

        override fun execute(deduplicationId: String): CompletableFuture<Unit> {
            return future.also { lock.release() }
        }
    }

    class Data(val d: String, val e: String)

    @StartableByRPC
    class ExternalOperationFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            await(ExternalOperation("hello there", 123, Data("please work", "I beg you")))
        }
    }

    class ExternalOperation(val a: String, val b: Int, val c: Data): FlowExternalOperation<Unit> {

        companion object {
            val lock = Semaphore(0)
            val lock2 = Semaphore(0)
        }

        override fun execute(deduplicationId: String) {
            lock.release()
            lock2.acquire()
        }
    }

    @StartableByRPC
    class WaitForStateConsumptionFlow(private val stateRefs: Set<StateRef>) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            waitForStateConsumption(stateRefs)
        }
    }
}