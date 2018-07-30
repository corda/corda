package net.corda.flowworker

import co.paralleluniverse.fibers.Suspendable
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.NodeConfigurationImpl
import net.corda.node.services.config.parseAsNodeConfiguration
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.messaging.P2PMessagingClient
import net.corda.node.services.statemachine.ExternalEvent
import net.corda.node.services.statemachine.InitialSessionMessage
import net.corda.node.services.statemachine.SessionId
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.internal.NodeBasedTest
import net.corda.testing.node.internal.TestCordappDirectories
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.getCallerPackage
import org.apache.activemq.artemis.api.core.Message
import org.apache.activemq.artemis.api.core.SimpleString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class FlowWorkerTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val bankAKeyPair = generateKeyPair()
    private val bankBKeyPair = generateKeyPair()
    private val notaryKeyPair = generateKeyPair()
    private val bankA = Party(DUMMY_BANK_A_NAME, bankAKeyPair.public)
    private val bankB = Party(DUMMY_BANK_B_NAME, bankBKeyPair.public)
    private val notary = Party(DUMMY_NOTARY_NAME, notaryKeyPair.public)
    private val bankAPartyAndCertificate = getTestPartyAndCertificate(bankA)
    private val bankBPartyAndCertificate = getTestPartyAndCertificate(bankB)
    private val notaryPartyAndCertificate = getTestPartyAndCertificate(notary)

    private val cordappPackages = listOf("net.corda.finance")
    private val cordapps = cordappsForPackages(getCallerPackage(NodeBasedTest::class)?.let { cordappPackages + it }
            ?: cordappPackages)

    private lateinit var configuration: NodeConfiguration

    @Before
    fun setup() {
        val testConfig = ConfigFactory.parseResources("test-config.conf", ConfigParseOptions.defaults().setAllowMissing(false)).parseAsNodeConfiguration() as NodeConfigurationImpl
        configuration = testConfig.copy(baseDirectory = temporaryFolder.root.toPath(), dataSourceProperties = makeTestDataSourceProperties(), cordappDirectories = TestCordappDirectories.cached(cordapps).toList())
    }

    private val myInfo = NodeInfo(listOf(NetworkHostAndPort("localhost", 3334)), listOf(bankAPartyAndCertificate), 1, 1)
    private val networkParameters = NetworkParameters(
            minimumPlatformVersion = 1,
            notaries = listOf(),
            modifiedTime = Instant.now(),
            maxMessageSize = 10485760,
            maxTransactionSize = 4000000,
            epoch = 1,
            whitelistedContractImplementations = emptyMap()
    )

    @Test
    fun `send message`() {
        val flowWorkerServiceHub = FlowWorkerServiceHub(configuration, myInfo, networkParameters, bankAKeyPair)
        val flowWorker = FlowWorker(flowWorkerServiceHub)
        flowWorker.start()

        flowWorkerServiceHub.networkMapCache.addNode(NodeInfo(listOf(NetworkHostAndPort("localhost", 3333)), listOf(bankBPartyAndCertificate), 1, 1))
        flowWorkerServiceHub.flowFactories[SomeFlowLogic::class.java] = InitiatedFlowFactory.Core { flowSession -> SomeFlowLogic(flowSession) }

        val cordaMessage = flowWorkerServiceHub.networkService.createMessage("platform.session", data = ByteSequence.of(InitialSessionMessage(SessionId(1), 1, SomeFlowLogic::class.java.name, 1, "", "test".serialize()).serialize().bytes).bytes)
        val artemisMessage = (flowWorkerServiceHub.networkService as P2PMessagingClient).messagingExecutor!!.cordaToArtemisMessage(cordaMessage)
        artemisMessage!!.putStringProperty(Message.HDR_VALIDATED_USER, SimpleString(DUMMY_BANK_B_NAME.toString()))
        (flowWorkerServiceHub.networkService as P2PMessagingClient).deliver(artemisMessage)

        flowWorker.stop()
    }

    @Test
    fun `cash issue`() {
        val flowWorkerServiceHub = FlowWorkerServiceHub(configuration, myInfo, networkParameters, bankAKeyPair)
        val flowWorker = FlowWorker(flowWorkerServiceHub)
        flowWorker.start()

        flowWorkerServiceHub.database.transaction {
            flowWorkerServiceHub.identityService.registerIdentity(notaryPartyAndCertificate)
        }

        val startFlowEventCashIssue = object : ExternalEvent.ExternalStartFlowEvent<AbstractCashFlow.Result>, DeduplicationHandler {
            override val deduplicationHandler = this
            override fun insideDatabaseTransaction() {}
            override fun afterDatabaseTransaction() {}
            override val externalCause = this
            override val flowLogic = CashIssueFlow(10.DOLLARS, OpaqueBytes.of(0x01), notary)
            override val context = InvocationContext.service("bla", DUMMY_BANK_A_NAME)
            private val _future = openFuture<FlowStateMachine<AbstractCashFlow.Result>>()
            override fun wireUpFuture(flowFuture: CordaFuture<FlowStateMachine<AbstractCashFlow.Result>>) {
                _future.captureLater(flowFuture)
            }

            override val future: CordaFuture<FlowStateMachine<AbstractCashFlow.Result>>
                get() = _future
        }
        val result = flowWorker.startFlow(startFlowEventCashIssue)
        println(result.getOrThrow().resultFuture.getOrThrow())
        println("Cash " + flowWorkerServiceHub.getCashBalances())

        flowWorker.stop()
    }

}

private class SomeFlowLogic(private val session: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        println("FLOW START")
        session.send("FLOW SEND A MESSAGE")
        println("FLOW END")
    }
}