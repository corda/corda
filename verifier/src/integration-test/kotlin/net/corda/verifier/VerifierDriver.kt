package net.corda.verifier

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.node.services.config.configureDevKeyAndTrustStores
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.VerifierApi
import net.corda.nodeapi.internal.config.NodeSSLConfiguration
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_USER
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.internal.*
import net.corda.testing.node.NotarySpec
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.apache.activemq.artemis.core.config.Configuration
import org.apache.activemq.artemis.core.config.CoreQueueConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory
import org.apache.activemq.artemis.core.security.CheckType
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicInteger

/**
 * Behaves the same as [driver] and adds verifier-related functionality.
 */
fun <A> verifierDriver(
        isDebug: Boolean = false,
        driverDirectory: Path = Paths.get("build", getTimestampAsDirectoryName()),
        portAllocation: PortAllocation = PortAllocation.Incremental(10000),
        debugPortAllocation: PortAllocation = PortAllocation.Incremental(5005),
        systemProperties: Map<String, String> = emptyMap(),
        useTestClock: Boolean = false,
        startNodesInProcess: Boolean = false,
        waitForNodesToFinish: Boolean = false,
        extraCordappPackagesToScan: List<String> = emptyList(),
        notarySpecs: List<NotarySpec> = emptyList(),
        dsl: VerifierDriverDSL.() -> A
) = genericDriver(
        driverDsl = VerifierDriverDSL(
                DriverDSLImpl(
                        portAllocation = portAllocation,
                        debugPortAllocation = debugPortAllocation,
                        systemProperties = systemProperties,
                        driverDirectory = driverDirectory.toAbsolutePath(),
                        useTestClock = useTestClock,
                        isDebug = isDebug,
                        startNodesInProcess = startNodesInProcess,
                        waitForNodesToFinish = waitForNodesToFinish,
                        extraCordappPackagesToScan = extraCordappPackagesToScan,
                        notarySpecs = notarySpecs
                )
        ),
        coerce = { it },
        dsl = dsl,
        initialiseSerialization = false
)

/** A handle for a verifier */
data class VerifierHandle(
        val process: Process
)

/** A handle for the verification requestor */
data class VerificationRequestorHandle(
        val p2pAddress: NetworkHostAndPort,
        private val responseAddress: SimpleString,
        private val session: ClientSession,
        private val requestProducer: ClientProducer,
        private val addVerificationFuture: (Long, OpenFuture<Throwable?>) -> Unit,
        private val executorService: ScheduledExecutorService
) {
    fun verifyTransaction(transaction: LedgerTransaction): CordaFuture<Throwable?> {
        val message = session.createMessage(false)
        val verificationId = random63BitValue()
        val request = VerifierApi.VerificationRequest(verificationId, transaction, responseAddress)
        request.writeToClientMessage(message)
        val verificationFuture = openFuture<Throwable?>()
        addVerificationFuture(verificationId, verificationFuture)
        requestProducer.send(message)
        return verificationFuture
    }

    fun waitUntilNumberOfVerifiers(number: Int) {
        poll(executorService, "$number verifiers to come online") {
            if (session.queueQuery(SimpleString(VerifierApi.VERIFICATION_REQUESTS_QUEUE_NAME)).consumerCount >= number) {
                Unit
            } else {
                null
            }
        }.get()
    }
}


data class VerifierDriverDSL(private val driverDSL: DriverDSLImpl) : InternalDriverDSL by driverDSL {
    private val verifierCount = AtomicInteger(0)

    companion object {
        private val log = contextLogger()
        fun createConfiguration(baseDirectory: Path, nodeHostAndPort: NetworkHostAndPort): Config {
            return ConfigFactory.parseMap(
                    mapOf(
                            "baseDirectory" to baseDirectory.toString(),
                            "nodeHostAndPort" to nodeHostAndPort.toString()
                    )
            )
        }

        fun createVerificationRequestorArtemisConfig(baseDirectory: Path, responseAddress: String, hostAndPort: NetworkHostAndPort, sslConfiguration: SSLConfiguration): Configuration {
            val connectionDirection = ConnectionDirection.Inbound(acceptorFactoryClassName = NettyAcceptorFactory::class.java.name)
            return ConfigurationImpl().apply {
                val artemisDir = "$baseDirectory/artemis"
                bindingsDirectory = "$artemisDir/bindings"
                journalDirectory = "$artemisDir/journal"
                largeMessagesDirectory = "$artemisDir/large-messages"
                acceptorConfigurations = setOf(ArtemisTcpTransport.tcpTransport(connectionDirection, hostAndPort, sslConfiguration))
                queueConfigurations = listOf(
                        CoreQueueConfiguration().apply {
                            name = VerifierApi.VERIFICATION_REQUESTS_QUEUE_NAME
                            address = VerifierApi.VERIFICATION_REQUESTS_QUEUE_NAME
                            isDurable = false
                        },
                        CoreQueueConfiguration().apply {
                            name = responseAddress
                            address = responseAddress
                            isDurable = false
                        }
                )
            }
        }
    }

    /** Starts a lightweight verification requestor that implements the Node's Verifier API */
    fun startVerificationRequestor(name: CordaX500Name): CordaFuture<VerificationRequestorHandle> {
        val hostAndPort = driverDSL.portAllocation.nextHostAndPort()
        return driverDSL.executorService.fork {
            startVerificationRequestorInternal(name, hostAndPort)
        }
    }

    private fun startVerificationRequestorInternal(name: CordaX500Name, hostAndPort: NetworkHostAndPort): VerificationRequestorHandle {
        val baseDir = driverDSL.driverDirectory / name.organisation
        val sslConfig = object : NodeSSLConfiguration {
            override val baseDirectory = baseDir
            override val keyStorePassword: String get() = "cordacadevpass"
            override val trustStorePassword: String get() = "trustpass"
        }
        sslConfig.configureDevKeyAndTrustStores(name)

        val responseQueueNonce = random63BitValue()
        val responseAddress = "${VerifierApi.VERIFICATION_RESPONSES_QUEUE_NAME_PREFIX}.$responseQueueNonce"

        val artemisConfig = createVerificationRequestorArtemisConfig(baseDir, responseAddress, hostAndPort, sslConfig)

        val securityManager = object : ActiveMQSecurityManager {
            // We don't need auth, SSL is good enough
            override fun validateUser(user: String?, password: String?) = true

            override fun validateUserAndRole(user: String?, password: String?, roles: MutableSet<Role>?, checkType: CheckType?) = true
        }

        val server = ActiveMQServerImpl(artemisConfig, securityManager)
        log.info("Starting verification requestor Artemis server with base dir $baseDir")
        server.start()
        driverDSL.shutdownManager.registerShutdown(doneFuture {
            server.stop()
        })
        val locator = ActiveMQClient.createServerLocatorWithoutHA().apply {
            isUseGlobalPools = nodeSerializationEnv != null
        }
        val transport = ArtemisTcpTransport.tcpTransport(ConnectionDirection.Outbound(), hostAndPort, sslConfig)
        val sessionFactory = locator.createSessionFactory(transport)
        val session = sessionFactory.createSession()
        driverDSL.shutdownManager.registerShutdown(doneFuture {
            session.stop()
            sessionFactory.close()
        })
        val producer = session.createProducer(VerifierApi.VERIFICATION_REQUESTS_QUEUE_NAME)

        val consumer = session.createConsumer(responseAddress)
        // We demux the individual txs ourselves to avoid race when a new verifier is added
        val verificationResponseFutures = ConcurrentHashMap<Long, OpenFuture<Throwable?>>()
        consumer.setMessageHandler {
            val result = VerifierApi.VerificationResponse.fromClientMessage(it)
            val resultFuture = verificationResponseFutures.remove(result.verificationId)
            log.info("${verificationResponseFutures.size} verifications left")
            if (resultFuture != null) {
                resultFuture.set(result.exception)
            } else {
                log.warn("Verification requestor $name can't find tx result future with id ${result.verificationId}, possible dupe")
            }
        }
        session.start()
        return VerificationRequestorHandle(
                p2pAddress = hostAndPort,
                responseAddress = SimpleString(responseAddress),
                session = session,
                requestProducer = producer,
                addVerificationFuture = { verificationNonce, future ->
                    verificationResponseFutures.put(verificationNonce, future)
                },
                executorService = driverDSL.executorService
        )
    }

    /** Starts an out of process verifier connected to [address] */
    fun startVerifier(address: NetworkHostAndPort): CordaFuture<VerifierHandle> {
        log.info("Starting verifier connecting to address $address")
        val id = verifierCount.andIncrement
        val jdwpPort = if (driverDSL.isDebug) driverDSL.debugPortAllocation.nextPort() else null
        val verifierName = CordaX500Name(organisation = "Verifier$id", locality = "London", country = "GB")
        val baseDirectory = (driverDSL.driverDirectory / verifierName.organisation).createDirectories()
        val config = createConfiguration(baseDirectory, address)
        val configFilename = "verifier.conf"
        writeConfig(baseDirectory, configFilename, config)
        Verifier.loadConfiguration(baseDirectory, baseDirectory / configFilename).configureDevKeyAndTrustStores(verifierName)
        val process = ProcessUtilities.startJavaProcess<Verifier>(listOf(baseDirectory.toString()), jdwpPort = jdwpPort)
        driverDSL.shutdownManager.registerProcessShutdown(process)
        return doneFuture(VerifierHandle(process))
    }

    /** Starts a verifier connecting to the specified node */
    fun startVerifier(nodeHandle: NodeHandle): CordaFuture<VerifierHandle> {
        return startVerifier(nodeHandle.configuration.p2pAddress)
    }

    /** Starts a verifier connecting to the specified requestor */
    fun startVerifier(verificationRequestorHandle: VerificationRequestorHandle): CordaFuture<VerifierHandle> {
        return startVerifier(verificationRequestorHandle.p2pAddress)
    }

    private fun <A> NodeHandle.connectToNode(closure: (ClientSession) -> A): A {
        val transport = ArtemisTcpTransport.tcpTransport(ConnectionDirection.Outbound(), configuration.p2pAddress, configuration)
        val locator = ActiveMQClient.createServerLocatorWithoutHA(transport)
        val sessionFactory = locator.createSessionFactory()
        val session = sessionFactory.createSession(NODE_USER, NODE_USER, false, true, true, locator.isPreAcknowledge, locator.ackBatchSize)
        return session.use {
            closure(it)
        }
    }

    /**
     * Waits until [number] verifiers are listening for verification requests coming from the Node. Check
     * [VerificationRequestorHandle.waitUntilNumberOfVerifiers] for an equivalent for requestors.
     */
    fun NodeHandle.waitUntilNumberOfVerifiers(number: Int) {
        connectToNode { session ->
            poll(driverDSL.executorService, "$number verifiers to come online") {
                if (session.queueQuery(SimpleString(VerifierApi.VERIFICATION_REQUESTS_QUEUE_NAME)).consumerCount >= number) {
                    Unit
                } else {
                    null
                }
            }.get()
        }
    }
}
