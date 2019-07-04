package net.corda.flowworker

import net.corda.core.context.InvocationContext
import net.corda.core.context.Trace
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.seconds
import net.corda.finance.DOLLARS
import net.corda.finance.USD
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.workflows.getCashBalances
import net.corda.node.internal.NetworkParametersReader.NetworkParametersAndSigned
import net.corda.node.internal.artemis.ArtemisBroker
import net.corda.node.services.config.*
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.bridging.BridgeControlListener
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.MockServices
import net.corda.testing.node.internal.CustomCordapp
import net.corda.testing.node.internal.cordappWithPackages
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

class FlowWorkerStartStopTest {
    @Rule
    @JvmField
    val serializationEnvironment = SerializationEnvironmentRule(true)

    private val portAllocation = incrementalPortAllocation()

    companion object {

        private val logger = contextLogger()
    }

    private val financeCordapp = cordappWithPackages("net.corda.finance").copy(name = "finance-cordapp")
    private val financeCordappPath = CustomCordapp.getJarFile(financeCordapp)

    private val notaryKeyPair = generateKeyPair()
    private val notary = Party(DUMMY_NOTARY_NAME, notaryKeyPair.public)
    private val notaryPartyAndCertificate = getTestPartyAndCertificate(notary)

    private val networkParameters = NetworkParameters(
            minimumPlatformVersion = 1,
            notaries = listOf(NotaryInfo(notary, false)),
            modifiedTime = Instant.now(),
            maxMessageSize = MAX_MESSAGE_SIZE,
            maxTransactionSize = 4000000,
            epoch = 1,
            whitelistedContractImplementations = emptyMap()
    )

    private fun signNetworkParams(certKeyPair: CertificateAndKeyPair, trustRoot: X509Certificate): NetworkParametersAndSigned {
        val signedParams = certKeyPair.sign(networkParameters)
        return NetworkParametersAndSigned(signedParams, trustRoot)
    }

    @Test
    fun startStop() {

        val infraA = setupFlowWorkerInfra(DUMMY_BANK_A_NAME)
        val infraB = setupFlowWorkerInfra(DUMMY_BANK_B_NAME)

        val signedNetworkParameters = signNetworkParams(createDevNetworkMapCa(), infraA.trustRoot)
        try {
            logger.logMemoryStats("Very beginning")

            createFlowWorkerAndPerformTest(infraA, signedNetworkParameters, 1).first.stop()
            val (nodeBFlowWorker, nodeBFlowWorkerHub) = createFlowWorkerAndPerformTest(infraB, signedNetworkParameters, 1)

            logger.logMemoryStats("After warm-up round")

            (2..10).forEach {
                logger.info("Iteration #$it")
                createFlowWorkerAndPerformTest(infraA, signedNetworkParameters, it).first.stop()
                issueCash(infraB, nodeBFlowWorkerHub, it)
            }

            nodeBFlowWorker.stop()
            logger.logMemoryStats("Testing done")
        } finally {
            infraA.bridgeControlListener.stop()
            infraA.broker.stop()
        }
    }

    private data class FlowWorkerInfra(val trustRoot: X509Certificate, val nodeCa: X509Certificate, val bankInfo: NodeInfo, val bankKeyPair: KeyPair,
                                       val session: ClientSession, val consumer: ClientConsumer, val producer: ClientProducer,
                                       val flowWorkerRequestQueueAddress: String, val flowWorkerReplyQueueAddress: String,
                                       val broker: ArtemisBroker, val bridgeControlListener: BridgeControlListener,
                                       val config: NodeConfiguration)

    private fun setupFlowWorkerInfra(legalName: CordaX500Name): FlowWorkerInfra {

        val bankKeyPair = generateKeyPair()
        val bank = Party(legalName, bankKeyPair.public)

        val bankPartyAndCertificate = getTestPartyAndCertificate(bank)

        val bankInfo = NodeInfo(listOf(NetworkHostAndPort("localhost", 1111)), listOf(bankPartyAndCertificate), 1, 1)

        val baseDirectory = DriverParameters().driverDirectory
        val nodeDirectory = baseDirectory / legalName.organisation / "flowWorker"
        nodeDirectory.createDirectories()
        val brokerAddress = NetworkHostAndPort("localhost", portAllocation.nextPort())

        val config = genericConfig().copy(
                myLegalName = legalName,
                baseDirectory = nodeDirectory,
                messagingServerAddress = brokerAddress,
                dataSourceProperties = MockServices.makeTestDataSourceProperties(),
                database = DatabaseConfig(runMigration = true),
                cordappDirectories = listOf(financeCordappPath.parent)
        )
        // create test certificates
        config.configureWithDevSSLCertificate()

        val trustRoot = config.p2pSslOptions.trustStore.get().query { getCertificate(X509Utilities.CORDA_ROOT_CA) }
        val nodeCa = config.signingCertificateStore.get().query { getCertificate(X509Utilities.CORDA_CLIENT_CA) }

        val broker = createFlowWorkerBroker(config, networkParameters.maxMessageSize)
        val bridgeControlListener = createBridgeControlListener(config, networkParameters.maxMessageSize)

        val flowWorkerRequestQueueAddress = "${FlowWorker.FLOW_WORKER_QUEUE_ADDRESS_PREFIX}${bankKeyPair.public.toStringShort()}"
        val flowWorkerReplyQueueAddress = "${FlowWorker.FLOW_WORKER_QUEUE_ADDRESS_PREFIX}reply"

        val (session, consumer, producer) = createArtemisClient(config, flowWorkerReplyQueueAddress)
        return FlowWorkerInfra(trustRoot, nodeCa, bankInfo, bankKeyPair, session, consumer, producer, flowWorkerRequestQueueAddress, flowWorkerReplyQueueAddress,
                broker, bridgeControlListener, config)
    }

    private fun createFlowWorkerAndPerformTest(infra: FlowWorkerInfra, signedNetworkParameters: NetworkParametersAndSigned, iterNumber: Int): Pair<FlowWorker, FlowWorkerServiceHub> {

        val (flowWorker, flowWorkerServiceHub) = createFlowWorker(infra.config, infra.bankInfo, signedNetworkParameters,
                infra.bankKeyPair, infra.trustRoot, infra.nodeCa)

        issueCash(infra, flowWorkerServiceHub, iterNumber)

        return Pair(flowWorker, flowWorkerServiceHub)
    }

    private fun issueCash(infra: FlowWorkerInfra, flowWorkerServiceHub: FlowWorkerServiceHub, iterNumber: Int) {
        val traceId = Trace.InvocationId.newInstance()
        val legalName = infra.bankInfo.legalIdentities.single().name
        val startFlowMessage = StartFlow(legalName, CashIssueFlow::class.java, arrayOf(10.DOLLARS, OpaqueBytes.of(0x01), notary),
                InvocationContext.service("bla", legalName), infra.flowWorkerReplyQueueAddress, traceId)
        val message = infra.session.createMessage(true)
        message.writeBodyBufferBytes(startFlowMessage.serialize(context = SerializationDefaults.RPC_CLIENT_CONTEXT).bytes)

        infra.producer.send(infra.flowWorkerRequestQueueAddress, message)

        val flowReplyStateMachineRunId = receiveFlowWorkerMessage<FlowReplyStateMachineRunId>(infra.consumer)
        println(flowReplyStateMachineRunId)

        val flowReplyResult = receiveFlowWorkerMessage<FlowReplyResult>(infra.consumer)
        assertEquals(traceId, flowReplyResult.replyId)
        println(flowReplyResult)

        val cashBalance = flowWorkerServiceHub.getCashBalances()
        assertEquals((10 * iterNumber).DOLLARS, cashBalance[USD])
        println("Cash: $cashBalance")
    }

    private fun genericConfig(): NodeConfigurationImpl {
        return NodeConfigurationImpl(baseDirectory = Paths.get("."), myLegalName = CHARLIE_NAME, emailAddress = "",
                keyStorePassword = "pass", trustStorePassword = "pass", crlCheckSoftFail = true, dataSourceProperties = Properties(),
                rpcUsers = listOf(), verifierType = VerifierType.InMemory, flowTimeout = FlowTimeoutConfiguration(5.seconds, 3, 1.0),
                p2pAddress = NetworkHostAndPort("localhost", 1), rpcSettings = NodeRpcSettings(NetworkHostAndPort("localhost", 1), null, ssl = null),
                relay = null, messagingServerAddress = null, enterpriseConfiguration = EnterpriseConfiguration(mutualExclusionConfiguration = MutualExclusionConfiguration(updateInterval = 0, waitInterval = 0)),
                notary = null, flowOverrides = FlowOverrideConfig(listOf()))
    }

    private fun createFlowWorkerBroker(config: NodeConfiguration, maxMessageSize: Int): ArtemisBroker {
        val broker = ArtemisMessagingServer(config, config.messagingServerAddress!!, maxMessageSize)
        broker.start()
        return broker
    }

    private fun createFlowWorker(config: NodeConfiguration, myInfo: NodeInfo, signedNetworkParameters: NetworkParametersAndSigned, ourKeyPair: KeyPair, trustRoot: X509Certificate, nodeCa: X509Certificate): Pair<FlowWorker, FlowWorkerServiceHub> {
        val flowWorkerServiceHub = FlowWorkerServiceHub(config, myInfo, ourKeyPair, trustRoot, nodeCa, signedNetworkParameters)
        val flowWorker = FlowWorker(UUID.randomUUID().toString(), flowWorkerServiceHub)
        flowWorker.start()
        flowWorkerServiceHub.database.transaction {
            flowWorkerServiceHub.identityService.registerIdentity(notaryPartyAndCertificate)
        }
        return Pair(flowWorker, flowWorkerServiceHub)
    }

    private fun createBridgeControlListener(config: NodeConfiguration, maxMessageSize: Int): BridgeControlListener {
        val bridgeControlListener = BridgeControlListener(config.p2pSslOptions, config.messagingServerAddress!!, maxMessageSize, config.crlCheckSoftFail, enableSNI = true)
        bridgeControlListener.start()
        return bridgeControlListener
    }

    private fun createArtemisClient(config: NodeConfiguration, queueAddress: String): Triple<ClientSession, ClientConsumer, ClientProducer> {
        val artemisClient = ArtemisMessagingClient(config.p2pSslOptions, config.messagingServerAddress!!, MAX_MESSAGE_SIZE)
        val started = artemisClient.start()
        started.session.createQueue(queueAddress, RoutingType.ANYCAST, queueAddress, true)
        return Triple(started.session, started.session.createConsumer(queueAddress), started.session.createProducer())
    }

    private inline fun <reified T : FlowWorkerMessage> receiveFlowWorkerMessage(consumer: ClientConsumer): T {
        val message = consumer.receive()
        val data = ByteArray(message.bodySize).apply { message.bodyBuffer.readBytes(this) }
        return data.deserialize(context = SerializationDefaults.RPC_CLIENT_CONTEXT)
    }
}
