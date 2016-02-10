/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import com.google.common.net.HostAndPort
import core.RunOnCallerThread
import core.ThreadBox
import core.messaging.*
import core.utilities.loggerFor
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.api.core.client.*
import org.apache.activemq.artemis.core.config.BridgeConfiguration
import org.apache.activemq.artemis.core.config.Configuration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule
import java.math.BigInteger
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import javax.annotation.concurrent.ThreadSafe

// TODO: Verify that nobody can connect to us and fiddle with our config over the socket due to the secman.
// TODO: Implement a discovery engine that can trigger builds of new connections when another node registers? (later)
// TODO: SSL

/**
 * This class implements the [MessagingService] API using Apache Artemis, the successor to their ActiveMQ product.
 * Artemis is a message queue broker and here, we embed the entire server inside our own process. Nodes communicate
 * with each other using (by default) an Artemis specific protocol, but it supports other protocols like AQMP/1.0
 * as well.
 *
 * The current implementation is skeletal and lacks features like security or firewall tunnelling (that is, you must
 * be able to receive TCP connections in order to receive messages). It is good enough for local communication within
 * a fully connected network, trusted network or on localhost.
 */
@ThreadSafe
class ArtemisMessagingService(val directory: Path, val myHostPort: HostAndPort) : MessagingService {
    // In future: can contain onion routing info, etc.
    private data class Address(val hostAndPort: HostAndPort) : SingleMessageRecipient

    companion object {
        val log = loggerFor<ArtemisMessagingService>()

        // This is a "property" attached to an Artemis MQ message object, which contains our own notion of "topic".
        // We should probably try to unify our notion of "topic" (really, just a string that identifies an endpoint
        // that will handle messages, like a URL) with the terminology used by underlying MQ libraries, to avoid
        // confusion.
        val TOPIC_PROPERTY = "platform-topic"

        /** Temp helper until network map is established. */
        fun makeRecipient(hostAndPort: HostAndPort): SingleMessageRecipient = Address(hostAndPort)
    }

    private lateinit var mq: EmbeddedActiveMQ
    private lateinit var clientFactory: ClientSessionFactory
    private lateinit var session: ClientSession
    private lateinit var inboundConsumer: ClientConsumer

    private class InnerState {
        var running = false
        val sendClients = HashMap<Address, ClientProducer>()
    }
    private val mutex = ThreadBox(InnerState())

    /** A registration to handle messages of different types */
    inner class Handler(val executor: Executor?, val topic: String,
                        val callback: (Message, MessageHandlerRegistration) -> Unit) : MessageHandlerRegistration
    private val handlers = CopyOnWriteArrayList<Handler>()

    private fun getSendClient(addr: Address): ClientProducer {
        return mutex.locked {
            sendClients.getOrPut(addr) {
                maybeSetupConnection(addr.hostAndPort)
                val qName = addr.hostAndPort.toString()
                session.createProducer(qName)
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
        mq = EmbeddedActiveMQ()
        val config = createArtemisConfig(directory, myHostPort)
        mq.setConfiguration(config)
        val secConfig = SecurityConfiguration()
        val password = BigInteger(128, SecureRandom.getInstanceStrong()).toString(16)
        secConfig.addUser("internal", password)
        secConfig.addRole("internal", "internal")
        secConfig.defaultUser = "internal"
        config.securityRoles = mapOf(
                "#" to setOf(Role("internal", true, true, true, true, true, true, true))
        )
        val secManager = ActiveMQJAASSecurityManager(InVMLoginModule::class.java.name, secConfig)
        mq.setSecurityManager(secManager)

        // Currently we cannot find out if something goes wrong during startup :( This is bug ARTEMIS-388 filed by me.
        // The fix should be in the 1.3.0 release:
        //
        // https://issues.apache.org/jira/browse/ARTEMIS-388
        mq.start()

        // Connect to our in-memory server.
        clientFactory = ActiveMQClient.createServerLocatorWithoutHA(
                TransportConfiguration(InVMConnectorFactory::class.java.name)).createSessionFactory()

        // Create a queue on which to receive messages and set up the handler.
        session = clientFactory.createSession()
        session.createQueue(myHostPort.toString(), "inbound", false)
        inboundConsumer = session.createConsumer("inbound").setMessageHandler { message: ClientMessage ->
            // This code runs for every inbound message.
            if (!message.containsProperty(TOPIC_PROPERTY)) {
                log.warn("Received message without a $TOPIC_PROPERTY property, ignoring")
                // TODO: Figure out whether we always need to acknowledge messages, even when invalid.
                return@setMessageHandler
            }
            val topic = message.getStringProperty(TOPIC_PROPERTY)
            // Because handlers is a COW list, the loop inside filter will operate on a snapshot. Handlers being added
            // or removed whilst the filter is executing will not affect anything.
            val deliverTo = handlers.filter { if (it.topic.isBlank()) true else it.topic == topic }

            if (deliverTo.isEmpty()) {
                // This should probably be downgraded to a trace in future, so the protocol can evolve with new topics
                // without causing log spam.
                log.warn("Received message for $topic that doesn't have any registered handlers.")
                return@setMessageHandler
            }

            val bits = ByteArray(message.bodySize)
            message.bodyBuffer.readBytes(bits)

            val msg = object : Message {
                override val topic = topic
                override val data: ByteArray = bits
                override val debugTimestamp: Instant = Instant.ofEpochMilli(message.timestamp)
                override val debugMessageID: String = message.messageID.toString()
                override fun serialise(): ByteArray = bits
            }
            for (handler in deliverTo) {
                (handler.executor ?: RunOnCallerThread).execute {
                    try {
                        handler.callback(msg, handler)
                    } catch(e: Exception) {
                        log.error("Caught exception whilst executing message handler for $topic", e)
                    }
                }
            }
            message.acknowledge()
        }
        session.start()

        mutex.locked { running = true }
    }

    override fun stop() {
        mutex.locked {
            for (producer in sendClients.values)
                producer.close()
            sendClients.clear()
            inboundConsumer.close()
            session.close()
            mq.stop()

            // We expect to be garbage collected shortly after being stopped, so we don't null anything explicitly here.

            running = false
        }
    }

    override fun send(message: Message, target: MessageRecipients) {
        if (target !is Address)
            TODO("Only simple sends to single recipients are currently implemented")
        val artemisMessage = session.createMessage(true).putStringProperty("platform-topic", message.topic).writeBodyBufferBytes(message.data)
        getSendClient(target).send(artemisMessage)
    }

    override fun addMessageHandler(topic: String, executor: Executor?,
                                   callback: (Message, MessageHandlerRegistration) -> Unit): MessageHandlerRegistration {
        val handler = Handler(executor, topic, callback)
        handlers.add(handler)
        return handler
    }

    override fun removeMessageHandler(registration: MessageHandlerRegistration) {
        handlers.remove(registration)
    }

    override fun createMessage(topic: String, data: ByteArray): Message {
        // TODO: We could write an object that proxies directly to an underlying MQ message here and avoid copying.
        return object : Message {
            override val topic: String get() = topic
            override val data: ByteArray get() = data
            override val debugTimestamp: Instant = Instant.now()
            override fun serialise(): ByteArray = this.serialise()
            override val debugMessageID: String get() = Instant.now().toEpochMilli().toString()
            override fun toString() = topic + "#" + String(data)
        }
    }

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
        if (!session.queueQuery(SimpleString(name)).isExists) {
            session.createQueue(name, name, true /* durable */)
        }
        if (!mq.activeMQServer.configuration.connectorConfigurations.containsKey(name)) {
            mq.activeMQServer.configuration.addConnectorConfiguration(name, tcpTransport(ConnectionDirection.OUTBOUND,
                    hostAndPort.hostText, hostAndPort.port))
            mq.activeMQServer.deployBridge(BridgeConfiguration().apply {
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
                tcpTransport(ConnectionDirection.INBOUND, "0.0.0.0", hp.port),
                TransportConfiguration(InVMAcceptorFactory::class.java.name)
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
                            TransportConstants.HOST_PROP_NAME to host,
                            TransportConstants.PORT_PROP_NAME to port.toInt()
                    )
            )

}
