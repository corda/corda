package com.r3corda.node.services.messaging

import com.google.common.net.HostAndPort
import com.r3corda.core.RunOnCallerThread
import com.r3corda.core.ThreadBox
import com.r3corda.core.crypto.WhitelistTrustManagerProvider
import com.r3corda.core.crypto.X509Utilities
import com.r3corda.core.crypto.newSecureRandom
import com.r3corda.core.crypto.registerWhitelistTrustManager
import com.r3corda.core.messaging.*
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.internal.Node
import com.r3corda.node.services.api.MessagingServiceInternal
import com.r3corda.node.services.config.NodeConfiguration
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.api.core.client.*
import org.apache.activemq.artemis.core.config.BridgeConfiguration
import org.apache.activemq.artemis.core.config.Configuration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.*
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule
import java.math.BigInteger
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import javax.annotation.concurrent.ThreadSafe

// TODO: Verify that nobody can connect to us and fiddle with our config over the socket due to the secman.
// TODO: Implement a discovery engine that can trigger builds of new connections when another node registers? (later)

/**
 * This class implements the [MessagingService] API using Apache Artemis, the successor to their ActiveMQ product.
 * Artemis is a message queue broker and here, we embed the entire server inside our own process. Nodes communicate
 * with each other using an Artemis specific protocol, but it supports other protocols like AMQP/1.0
 * as well for interop.
 *
 * The current implementation is skeletal and lacks features like security or firewall tunnelling (that is, you must
 * be able to receive TCP connections in order to receive messages). It is good enough for local communication within
 * a fully connected network, trusted network or on localhost.
 *
 * @param directory A place where Artemis can stash its message journal and other files.
 * @param myHostPort What host and port to bind to for receiving inbound connections.
 * @param config The config object is used to pass in the passwords for the certificate KeyStore and TrustStore
 * @param defaultExecutor This will be used as the default executor to run message handlers on, if no other is specified.
 */
@ThreadSafe
class ArtemisMessagingService(val directory: Path,
                              val myHostPort: HostAndPort,
                              val config: NodeConfiguration,
                              val defaultExecutor: Executor = RunOnCallerThread) : SingletonSerializeAsToken(), MessagingServiceInternal {

    // In future: can contain onion routing info, etc.
    private data class Address(val hostAndPort: HostAndPort) : SingleMessageRecipient

    companion object {
        init {
            // Until  https://issues.apache.org/jira/browse/ARTEMIS-656 is resolved gate acceptable
            // certificate hosts manually.
            registerWhitelistTrustManager()
        }


        val log = loggerFor<ArtemisMessagingService>()

        // This is a "property" attached to an Artemis MQ message object, which contains our own notion of "topic".
        // We should probably try to unify our notion of "topic" (really, just a string that identifies an endpoint
        // that will handle messages, like a URL) with the terminology used by underlying MQ libraries, to avoid
        // confusion.
        val TOPIC_PROPERTY = "platform-topic"

        val SESSION_ID_PROPERTY = "session-id"

        /** Temp helper until network map is established. */
        fun makeRecipient(hostAndPort: HostAndPort): SingleMessageRecipient = Address(hostAndPort)
        fun makeRecipient(hostname: String) = makeRecipient(toHostAndPort(hostname))
        fun toHostAndPort(hostname: String) = HostAndPort.fromString(hostname).withDefaultPort(Node.DEFAULT_PORT)
    }

    private lateinit var activeMQServer: ActiveMQServer
    private lateinit var clientFactory: ClientSessionFactory
    private var session: ClientSession? = null
    private var inboundConsumer: ClientConsumer? = null

    private class InnerState {
        var running = false
        val sendClients = HashMap<Address, ClientProducer>()
    }

    private val mutex = ThreadBox(InnerState())

    /** A registration to handle messages of different types */
    inner class Handler(val executor: Executor?,
                        val topicSession: TopicSession,
                        val callback: (Message, MessageHandlerRegistration) -> Unit) : MessageHandlerRegistration

    private val handlers = CopyOnWriteArrayList<Handler>()

    // TODO: This is not robust and needs to be replaced by more intelligently using the message queue server.
    private val undeliveredMessages = CopyOnWriteArrayList<Message>()

    private val keyStorePath = directory.resolve("certificates").resolve("sslkeystore.jks")
    private val trustStorePath = directory.resolve("certificates").resolve("truststore.jks")

    // Restrict enabled Cipher Suites to AES and GCM as minimum for the bulk cipher.
    // Our self-generated certificates all use ECDSA for handshakes, but we allow classical RSA certificates to work
    // in case we need to use keytool certificates in some demos
    private val CIPHER_SUITES = listOf(
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256")

    init {
        require(directory.fileSystem == FileSystems.getDefault()) { "Artemis only uses the default file system" }
    }

    private fun getSendClient(address: Address): ClientProducer {
        return mutex.locked {
            sendClients.getOrPut(address) {
                if (address != myAddress) {
                    maybeSetupConnection(address.hostAndPort)
                }
                session!!.createProducer(address.hostAndPort.toString())
            }
        }
    }

    fun start() {
        // Wire up various bits of configuration. This is so complicated because Artemis is an embedded message queue
        // server. Thus we're running both a "server" and a "client" in the same JVM process. A future node might be
        // able to use an external MQ server instead, for instance, if a bank already has an MQ setup and wishes to
        // reuse it, or if it makes sense for scaling to split the functionality out, or if it makes sense for security.
        //
        // But for now, we bundle it all up into one thing.
        val config = createArtemisConfig(directory, myHostPort).apply {
            securityRoles = mapOf(
                    "#" to setOf(Role("internal", true, true, true, true, true, true, true))
            )
        }

        val securityConfig = SecurityConfiguration().apply {
            addUser("internal", BigInteger(128, newSecureRandom()).toString(16))
            addRole("internal", "internal")
            defaultUser = "internal"
        }
        val securityManager = ActiveMQJAASSecurityManager(InVMLoginModule::class.java.name, securityConfig)

        activeMQServer = ActiveMQServerImpl(config, securityManager)
        // Throw any exceptions which are detected during startup
        activeMQServer.registerActivationFailureListener { exception -> throw exception }
        activeMQServer.start()

        // Connect to our server.
        clientFactory = ActiveMQClient.createServerLocatorWithoutHA(
                tcpTransport(ConnectionDirection.OUTBOUND, myHostPort.hostText, myHostPort.port)).createSessionFactory()

        // Create a queue on which to receive messages and set up the handler.
        val session = clientFactory.createSession()
        this.session = session

        session.createQueue(myHostPort.toString(), "inbound", false)
        inboundConsumer = session.createConsumer("inbound").setMessageHandler { message: ClientMessage ->
            // This code runs for every inbound message.
            try {
                if (!message.containsProperty(TOPIC_PROPERTY)) {
                    log.warn("Received message without a $TOPIC_PROPERTY property, ignoring")
                    return@setMessageHandler
                }
                if (!message.containsProperty(SESSION_ID_PROPERTY)) {
                    log.warn("Received message without a $SESSION_ID_PROPERTY property, ignoring")
                    return@setMessageHandler
                }
                val topic = message.getStringProperty(TOPIC_PROPERTY)
                val sessionID = message.getLongProperty(SESSION_ID_PROPERTY)

                val body = ByteArray(message.bodySize).apply { message.bodyBuffer.readBytes(this) }

                val msg = object : Message {
                    override val topicSession = TopicSession(topic, sessionID)
                    override val data: ByteArray = body
                    override val debugTimestamp: Instant = Instant.ofEpochMilli(message.timestamp)
                    override val debugMessageID: String = message.messageID.toString()
                    override fun serialise(): ByteArray = body
                    override fun toString() = topic + "#" + String(data)
                }

                deliverMessage(msg)
            } finally {
                // TODO the message is delivered onto an executor and so we may be acking the message before we've
                // finished processing it
                message.acknowledge()
            }
        }
        session.start()

        mutex.locked { running = true }
    }

    private fun deliverMessage(msg: Message): Boolean {
        // Because handlers is a COW list, the loop inside filter will operate on a snapshot. Handlers being added
        // or removed whilst the filter is executing will not affect anything.
        val deliverTo = handlers.filter { it.topicSession.isBlank() || it.topicSession == msg.topicSession }

        if (deliverTo.isEmpty()) {
            // This should probably be downgraded to a trace in future, so the protocol can evolve with new topics
            // without causing log spam.
            log.warn("Received message for ${msg.topicSession} that doesn't have any registered handlers yet")

            // This is a hack; transient messages held in memory isn't crash resistant.
            // TODO: Use Artemis API more effectively so we don't pop messages off a queue that we aren't ready to use.
            undeliveredMessages += msg

            return false
        }

        for (handler in deliverTo) {
            (handler.executor ?: defaultExecutor).execute {
                try {
                    handler.callback(msg, handler)
                } catch(e: Exception) {
                    log.error("Caught exception whilst executing message handler for ${msg.topicSession}", e)
                }
            }
        }

        return true
    }

    override fun stop() {
        mutex.locked {
            for (producer in sendClients.values)
                producer.close()
            sendClients.clear()
            inboundConsumer?.close()
            session?.close()
            activeMQServer.stop()

            // We expect to be garbage collected shortly after being stopped, so we don't null anything explicitly here.

            running = false
        }
    }

    override fun registerTrustedAddress(address: SingleMessageRecipient) {
        require(address is Address) { "Address is not an Artemis Message Address" }
        val hostName = (address as Address).hostAndPort.hostText
        WhitelistTrustManagerProvider.addWhitelistEntry(hostName)
    }

    override fun send(message: Message, target: MessageRecipients) {
        if (target !is Address)
            TODO("Only simple sends to single recipients are currently implemented")
        val artemisMessage = session!!.createMessage(true).apply {
            val sessionID = message.topicSession.sessionID
            putStringProperty(TOPIC_PROPERTY, message.topicSession.topic)
            putLongProperty(SESSION_ID_PROPERTY, sessionID)
            writeBodyBufferBytes(message.data)
        }
        getSendClient(target).send(artemisMessage)
    }

    override fun addMessageHandler(topic: String, sessionID: Long, executor: Executor?,
                                   callback: (Message, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration
        = addMessageHandler(TopicSession(topic, sessionID), executor, callback)

    override fun addMessageHandler(topicSession: TopicSession,
                                   executor: Executor?,
                                   callback: (Message, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration {
        require(!topicSession.isBlank()) { "Topic must not be blank, as the empty topic is a special case." }
        val handler = Handler(executor, topicSession, callback)
        handlers.add(handler)
        undeliveredMessages.removeIf { deliverMessage(it) }
        return handler
    }

    override fun removeMessageHandler(registration: MessageHandlerRegistration) {
        handlers.remove(registration)
    }

    override fun createMessage(topicSession: TopicSession, data: ByteArray): Message {
        // TODO: We could write an object that proxies directly to an underlying MQ message here and avoid copying.
        return object : Message {
            override val topicSession: TopicSession get() = topicSession
            override val data: ByteArray get() = data
            override val debugTimestamp: Instant = Instant.now()
            override fun serialise(): ByteArray = this.serialise()
            override val debugMessageID: String get() = Instant.now().toEpochMilli().toString()
            override fun toString() = topicSession.toString() + "#" + String(data)
        }
    }

    override fun createMessage(topic: String, sessionID: Long, data: ByteArray): Message
            = createMessage(TopicSession(topic, sessionID), data)

    override val myAddress: SingleMessageRecipient = Address(myHostPort)

    private enum class ConnectionDirection { INBOUND, OUTBOUND }

    private fun maybeSetupConnection(hostAndPort: HostAndPort) {
        val name = hostAndPort.toString()

        // To make ourselves talk to a remote server, we need a "bridge". Bridges are things inside Artemis that know how
        // to handle remote machines going away temporarily, retry connections, etc. They're the bit that handles
        // unreliable peers. Thus, we need one bridge per node we are talking to.
        //
        // Each bridge consumes from a queue on our end and forwards messages to a queue on their end. So for each node
        // we must create a queue, then create and configure a bridge.
        //
        // Note that bridges are not two way. A having a bridge to B does not imply that B can connect back to A. This
        // becomes important for cases like firewall tunnelling and connection proxying where connectivity is not
        // entirely duplex. The Artemis team may add this functionality in future:
        //
        // https://issues.apache.org/jira/browse/ARTEMIS-355
        if (!session!!.queueQuery(SimpleString(name)).isExists) {
            session!!.createQueue(name, name, true /* durable */)
        }
        if (!activeMQServer.configuration.connectorConfigurations.containsKey(name)) {
            activeMQServer.configuration.addConnectorConfiguration(name, tcpTransport(ConnectionDirection.OUTBOUND,
                    hostAndPort.hostText, hostAndPort.port))
            activeMQServer.deployBridge(BridgeConfiguration().apply {
                setName(name)
                queueName = name
                forwardingAddress = name
                staticConnectors = listOf(name)
                confirmationWindowSize = 100000   // a guess
            })
        }
    }

    private fun setConfigDirectories(config: Configuration, dir: Path) {
        config.apply {
            bindingsDirectory = dir.resolve("bindings").toString()
            journalDirectory = dir.resolve("journal").toString()
            largeMessagesDirectory = dir.resolve("largemessages").toString()
        }
    }

    private fun createArtemisConfig(directory: Path, hp: HostAndPort): Configuration {
        val config = ConfigurationImpl()
        setConfigDirectories(config, directory)
        // We will be talking to our server purely in memory.
        config.acceptorConfigurations = setOf(
                tcpTransport(ConnectionDirection.INBOUND, "0.0.0.0", hp.port)
        )
        return config
    }

    private fun tcpTransport(direction: ConnectionDirection, host: String, port: Int) =
            TransportConfiguration(
                    when (direction) {
                        ConnectionDirection.INBOUND -> NettyAcceptorFactory::class.java.name
                        ConnectionDirection.OUTBOUND -> NettyConnectorFactory::class.java.name
                    },
                    mapOf(
                            // Basic TCP target details
                            HOST_PROP_NAME to host,
                            PORT_PROP_NAME to port.toInt(),

                            // Turn on AMQP support, which needs the protocol jar on the classpath.
                            // Unfortunately we cannot disable core protocol as artemis only uses AMQP for interop
                            // It does not use AMQP messages for its own messages e.g. topology and heartbeats
                            // TODO further investigate how to ensure we use a well defined wire level protocol for Node to Node communications
                            PROTOCOLS_PROP_NAME to "CORE,AMQP",

                            // Enable TLS transport layer with client certs and restrict to at least SHA256 in handshake
                            // and AES encryption
                            SSL_ENABLED_PROP_NAME to true,
                            KEYSTORE_PROVIDER_PROP_NAME to "JKS",
                            KEYSTORE_PATH_PROP_NAME to keyStorePath,
                            KEYSTORE_PASSWORD_PROP_NAME to config.keyStorePassword, // TODO proper management of keystores and password
                            TRUSTSTORE_PROVIDER_PROP_NAME to "JKS",
                            TRUSTSTORE_PATH_PROP_NAME to trustStorePath,
                            TRUSTSTORE_PASSWORD_PROP_NAME to config.trustStorePassword,
                            ENABLED_CIPHER_SUITES_PROP_NAME to CIPHER_SUITES.joinToString(","),
                            ENABLED_PROTOCOLS_PROP_NAME to "TLSv1.2",
                            NEED_CLIENT_AUTH_PROP_NAME to true
                    )
            )

    /**
     * Strictly for dev only automatically construct a server certificate/private key signed from
     * the CA certs in Node resources. Then provision KeyStores into certificates folder under node path.
     */
    fun configureWithDevSSLCertificate() {
        Files.createDirectories(directory.resolve("certificates"))
        if (!Files.exists(trustStorePath)) {
            Files.copy(javaClass.classLoader.getResourceAsStream("com/r3corda/node/internal/certificates/cordatruststore.jks"),
                    trustStorePath)
        }
        if (!Files.exists(keyStorePath)) {
            val caKeyStore = X509Utilities.loadKeyStore(
                    javaClass.classLoader.getResourceAsStream("com/r3corda/node/internal/certificates/cordadevcakeys.jks"),
                    "cordacadevpass")
            X509Utilities.createKeystoreForSSL(keyStorePath, config.keyStorePassword, config.keyStorePassword, caKeyStore, "cordacadevkeypass")
        }
    }

}
