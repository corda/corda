/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.flowworker

import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.context.InvocationContext
import net.corda.core.context.Trace
import net.corda.core.crypto.Crypto.generateKeyPair
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.Party
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.seconds
import net.corda.finance.DOLLARS
import net.corda.finance.USD
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.internal.artemis.ArtemisBroker
import net.corda.node.services.config.*
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.bridging.BridgeControlListener
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.PortAllocation
import net.corda.testing.node.MockServices
import net.corda.testing.node.internal.TestCordappDirectories
import net.corda.testing.node.internal.cordappsForPackages
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

class FlowWorkerTest {

    @Rule
    @JvmField
    val serializationEnvironment = SerializationEnvironmentRule(true)

    private val portAllocation = PortAllocation.Incremental(10000)

    private val networkParameters = NetworkParameters(
            minimumPlatformVersion = 1,
            notaries = listOf(),
            modifiedTime = Instant.now(),
            maxMessageSize = MAX_MESSAGE_SIZE,
            maxTransactionSize = 4000000,
            epoch = 1,
            whitelistedContractImplementations = emptyMap()
    )

    private val bankAKeyPair = generateKeyPair()
    private val bankBKeyPair = generateKeyPair()
    private val notaryKeyPair = generateKeyPair()
    private val bankA = Party(DUMMY_BANK_A_NAME, bankAKeyPair.public)
    private val bankB = Party(DUMMY_BANK_B_NAME, bankBKeyPair.public)
    private val notary = Party(DUMMY_NOTARY_NAME, notaryKeyPair.public)
    private val bankAPartyAndCertificate = getTestPartyAndCertificate(bankA)
    private val bankBPartyAndCertificate = getTestPartyAndCertificate(bankB)
    private val notaryPartyAndCertificate = getTestPartyAndCertificate(notary)

    private val bankAInfo = NodeInfo(listOf(NetworkHostAndPort("localhost", 1111)), listOf(bankAPartyAndCertificate), 1, 1)
    private val bankBInfo = NodeInfo(listOf(NetworkHostAndPort("localhost", 1112)), listOf(bankBPartyAndCertificate), 1, 1)

    private val cordappDirectories = TestCordappDirectories.cached(cordappsForPackages(listOf("net.corda.finance"))).toList()

    @Test
    fun `cash issue`() {
        val baseDirectory = DriverParameters().driverDirectory
        val nodeDirectory = baseDirectory / DUMMY_BANK_A_NAME.organisation / "flowWorker"
        nodeDirectory.createDirectories()
        val brokerAddress = NetworkHostAndPort("localhost", portAllocation.nextPort())

        val config = genericConfig().copy(myLegalName = DUMMY_BANK_A_NAME, baseDirectory = nodeDirectory,
                messagingServerAddress = brokerAddress, dataSourceProperties = MockServices.makeTestDataSourceProperties(),
                cordappDirectories = cordappDirectories)
        // create test certificates
        config.configureWithDevSSLCertificate()

        val trustRoot = config.loadTrustStore().getCertificate(X509Utilities.CORDA_ROOT_CA)
        val nodeCa = config.loadNodeKeyStore().getCertificate(X509Utilities.CORDA_CLIENT_CA)

        val broker = createFlowWorkerBroker(config, networkParameters.maxMessageSize)
        val bridgeControlListener = createBridgeControlListener(config, networkParameters.maxMessageSize)

        val flowWorkerRequestQueueAddress = "${FlowWorker.FLOW_WORKER_QUEUE_ADDRESS_PREFIX}${bankAKeyPair.public.toStringShort()}"
        val flowWorkerReplyQueueAddress = "${FlowWorker.FLOW_WORKER_QUEUE_ADDRESS_PREFIX}reply"

        val (session, consumer, producer) = createArtemisClient(config, flowWorkerReplyQueueAddress)

        val (flowWorker, flowWorkerServiceHub) = createFlowWorker(config, bankAInfo, networkParameters, bankAKeyPair, trustRoot, nodeCa)
        try {
            flowWorkerServiceHub.database.transaction {
                flowWorkerServiceHub.identityService.registerIdentity(notaryPartyAndCertificate)
            }

            val traceId = Trace.InvocationId.newInstance()
            val startFlowMessage = StartFlow(DUMMY_BANK_A_NAME, CashIssueFlow::class.java, arrayOf(10.DOLLARS, OpaqueBytes.of(0x01), notary),
                    InvocationContext.service("bla", DUMMY_BANK_A_NAME), flowWorkerReplyQueueAddress, traceId)
            val message = session.createMessage(true)
            message.writeBodyBufferBytes(startFlowMessage.serialize(context = SerializationDefaults.RPC_CLIENT_CONTEXT).bytes)

            producer.send(flowWorkerRequestQueueAddress, message)

            val flowReplyStateMachineRunId = receiveFlowWorkerMessage<FlowReplyStateMachineRunId>(consumer)
            println(flowReplyStateMachineRunId)

            val flowReplyResult = receiveFlowWorkerMessage<FlowReplyResult>(consumer)
            assertEquals(traceId, flowReplyResult.replyId)
            println(flowReplyResult)

            val cashBalance = flowWorkerServiceHub.getCashBalances()
            assertEquals(10.DOLLARS, cashBalance[USD])
            println("Cash: $cashBalance")
        } finally {
            flowWorker.stop()
            bridgeControlListener.stop()
            broker.stop()
        }
    }

    @Test
    fun `swap identities`() {
        val baseDirectory = DriverParameters().driverDirectory

        val bankANodeDirectory = baseDirectory / DUMMY_BANK_A_NAME.organisation / "flowWorker"
        bankANodeDirectory.createDirectories()
        val bankAbrokerAddress = NetworkHostAndPort("localhost", portAllocation.nextPort())
        val bankAConfig = genericConfig().copy(myLegalName = DUMMY_BANK_A_NAME, baseDirectory = bankANodeDirectory,
                messagingServerAddress = bankAbrokerAddress, dataSourceProperties = MockServices.makeTestDataSourceProperties(),
                cordappDirectories = cordappDirectories)
        // create test certificates
        bankAConfig.configureWithDevSSLCertificate()

        val bankATrustRoot = bankAConfig.loadTrustStore().getCertificate(X509Utilities.CORDA_ROOT_CA)
        val bankANodeCa = bankAConfig.loadNodeKeyStore().getCertificate(X509Utilities.CORDA_CLIENT_CA)

        val bankABroker = createFlowWorkerBroker(bankAConfig, networkParameters.maxMessageSize)
        val bankABridgeControlListener = createBridgeControlListener(bankAConfig, networkParameters.maxMessageSize)
        val (bankAFlowWorker, bankAFlowWorkerServiceHub) = createFlowWorker(bankAConfig, bankAInfo, networkParameters, bankAKeyPair, bankATrustRoot, bankANodeCa)

        val bankARequestQueueAddress = "${FlowWorker.FLOW_WORKER_QUEUE_ADDRESS_PREFIX}${bankAKeyPair.public.toStringShort()}"
        val bankAReplyQueueAddress = "${FlowWorker.FLOW_WORKER_QUEUE_ADDRESS_PREFIX}reply"
        val (bankASession, bankAConsumer, bankAProducer) = createArtemisClient(bankAConfig, bankAReplyQueueAddress)

        val bankBNodeDirectory = baseDirectory / DUMMY_BANK_B_NAME.organisation / "flowWorker"
        bankBNodeDirectory.createDirectories()
        val bankBbrokerAddress = NetworkHostAndPort("localhost", portAllocation.nextPort())
        val bankBConfig = genericConfig().copy(myLegalName = DUMMY_BANK_B_NAME, baseDirectory = bankBNodeDirectory,
                messagingServerAddress = bankBbrokerAddress, dataSourceProperties = MockServices.makeTestDataSourceProperties(),
                cordappDirectories = cordappDirectories)
        // create test certificates
        bankBConfig.configureWithDevSSLCertificate()

        val bankBTrustRoot = bankBConfig.loadTrustStore().getCertificate(X509Utilities.CORDA_ROOT_CA)
        val bankBNodeCa = bankBConfig.loadNodeKeyStore().getCertificate(X509Utilities.CORDA_CLIENT_CA)
        // NetworkParametersCopier(networkParameters).install(bankBConfig.baseDirectory)

        val bankBBroker = createFlowWorkerBroker(bankBConfig, networkParameters.maxMessageSize)
        val bankBBridgeControlListener = createBridgeControlListener(bankBConfig, networkParameters.maxMessageSize)
        val (bankBFlowWorker, bankBFlowWorkerServiceHub) = createFlowWorker(bankBConfig, bankBInfo, networkParameters, bankBKeyPair, bankBTrustRoot, bankBNodeCa)

        try {
            bankAFlowWorkerServiceHub.database.transaction {
                bankAFlowWorkerServiceHub.identityService.registerIdentity(notaryPartyAndCertificate)

                bankAFlowWorkerServiceHub.networkMapCache.addNode(NodeInfo(listOf(NetworkHostAndPort("localhost", bankBConfig.messagingServerAddress!!.port)), listOf(bankBPartyAndCertificate), 1, 1))
            }

            bankBFlowWorkerServiceHub.database.transaction {
                bankBFlowWorkerServiceHub.identityService.registerIdentity(notaryPartyAndCertificate)

                bankBFlowWorkerServiceHub.networkMapCache.addNode(NodeInfo(listOf(NetworkHostAndPort("localhost", bankAConfig.messagingServerAddress!!.port)), listOf(bankAPartyAndCertificate), 1, 1))
            }

            val swapIdentitiesTraceId = Trace.InvocationId.newInstance()
            val swapIdentitiesStartFlowMessage = StartFlow(DUMMY_BANK_A_NAME, SwapIdentitiesFlow::class.java, arrayOf(bankB),
                    InvocationContext.service("bla", DUMMY_BANK_A_NAME), bankAReplyQueueAddress, swapIdentitiesTraceId)
            val swapIdentitiesMessage = bankASession.createMessage(true)
            swapIdentitiesMessage.writeBodyBufferBytes(swapIdentitiesStartFlowMessage.serialize(context = SerializationDefaults.RPC_CLIENT_CONTEXT).bytes)

            bankAProducer.send(bankARequestQueueAddress, swapIdentitiesMessage)

            val swapIdentitiesStateMachineRunId = receiveFlowWorkerMessage<FlowReplyStateMachineRunId>(bankAConsumer)
            println(swapIdentitiesStateMachineRunId)

            val swapIdentitiesResult = receiveFlowWorkerMessage<FlowReplyResult>(bankAConsumer)
            assertEquals(swapIdentitiesTraceId, swapIdentitiesResult.replyId)
            println(swapIdentitiesResult)
        } finally {
            bankAFlowWorker.stop()
            bankBFlowWorker.stop()
            bankABridgeControlListener.stop()
            bankBBridgeControlListener.stop()
            bankABroker.stop()
            bankBBroker.stop()
        }
    }

    private fun genericConfig(): NodeConfigurationImpl {
        return NodeConfigurationImpl(baseDirectory = Paths.get("."), myLegalName = DUMMY_BANK_A_NAME, emailAddress = "",
                keyStorePassword = "pass", trustStorePassword = "pass", crlCheckSoftFail = true, dataSourceProperties = Properties(),
                rpcUsers = listOf(), verifierType = VerifierType.InMemory, flowTimeout = FlowTimeoutConfiguration(5.seconds, 3, 1.0),
                p2pAddress = NetworkHostAndPort("localhost", 1), rpcSettings = NodeRpcSettings(NetworkHostAndPort("localhost", 1), null, ssl = null),
                relay = null, messagingServerAddress = null, enterpriseConfiguration = EnterpriseConfiguration(mutualExclusionConfiguration = MutualExclusionConfiguration(updateInterval = 0, waitInterval = 0)),
                notary = null)
    }

    private fun createFlowWorkerBroker(config: NodeConfiguration, maxMessageSize: Int): ArtemisBroker {
        val broker = ArtemisMessagingServer(config, config.messagingServerAddress!!, maxMessageSize)
        broker.start()
        return broker
    }

    private fun createFlowWorker(config: NodeConfiguration, myInfo: NodeInfo, networkParameters: NetworkParameters, ourKeyPair: KeyPair, trustRoot: X509Certificate, nodeCa: X509Certificate): Pair<FlowWorker, FlowWorkerServiceHub> {
        val flowWorkerServiceHub = FlowWorkerServiceHub(config, myInfo, networkParameters, ourKeyPair, trustRoot, nodeCa)
        val flowWorker = FlowWorker(UUID.randomUUID().toString(), flowWorkerServiceHub)
        flowWorker.start()
        return Pair(flowWorker, flowWorkerServiceHub)
    }

    private fun createBridgeControlListener(config: NodeConfiguration, maxMessageSize: Int): BridgeControlListener {
        val bridgeControlListener = BridgeControlListener(config, config.messagingServerAddress!!, maxMessageSize)
        bridgeControlListener.start()
        return bridgeControlListener
    }

    private fun createArtemisClient(config: NodeConfiguration, queueAddress: String): Triple<ClientSession, ClientConsumer, ClientProducer> {
        val artemisClient = ArtemisMessagingClient(config, config.messagingServerAddress!!, MAX_MESSAGE_SIZE)
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