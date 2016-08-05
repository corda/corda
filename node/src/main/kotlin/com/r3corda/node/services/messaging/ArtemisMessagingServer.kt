package com.r3corda.node.services.messaging

import com.google.common.net.HostAndPort
import com.r3corda.core.ThreadBox
import com.r3corda.core.crypto.newSecureRandom
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.services.config.NodeConfiguration
import org.apache.activemq.artemis.core.config.BridgeConfiguration
import org.apache.activemq.artemis.core.config.Configuration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule
import java.math.BigInteger
import java.nio.file.Path
import javax.annotation.concurrent.ThreadSafe

// TODO: Verify that nobody can connect to us and fiddle with our config over the socket due to the secman.
// TODO: Implement a discovery engine that can trigger builds of new connections when another node registers? (later)

/**
 * This class configures and manages an Apache Artemis message queue broker.
 *
 * Nodes communication is managed using an Artemis specific protocol, but it supports other protocols like AMQP/1.0
 * as well for interop.
 *
 * The current implementation is skeletal and lacks features like security or firewall tunnelling (that is, you must
 * be able to receive TCP connections in order to receive messages). It is good enough for local communication within
 * a fully connected network, trusted network or on localhost.
 */
@ThreadSafe
class ArtemisMessagingServer(directory: Path,
                             config: NodeConfiguration,
                             val myHostPort: HostAndPort) : ArtemisMessagingComponent(directory, config) {
    companion object {
        val log = loggerFor<ArtemisMessagingServer>()
    }

    private class InnerState {
        var running = false
    }

    val myAddress: SingleMessageRecipient = Address(myHostPort)
    private val mutex = ThreadBox(InnerState())
    private lateinit var activeMQServer: ActiveMQServer

    fun start() = mutex.locked {
        if (!running) {
            configureAndStartServer()
            running = true
        }
    }

    fun stop() = mutex.locked {
        activeMQServer.stop()
        running = false
    }

    private fun configureAndStartServer() {
        val config = createArtemisConfig(directory, myHostPort).apply {
            securityRoles = mapOf(
                    "#" to setOf(Role("internal", true, true, true, true, true, true, true))
            )
        }

        val securityManager = createArtemisSecurityManager()

        activeMQServer = ActiveMQServerImpl(config, securityManager).apply {
            // Throw any exceptions which are detected during startup
            registerActivationFailureListener { exception -> throw exception }
            // Deploy bridge for a newly created queue
            registerPostQueueCreationCallback { queueName ->
                log.trace("Queue created: $queueName")
                maybeDeployBridgeForAddress(queueName.toString())
            }
        }
        activeMQServer.start()
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

    private fun createArtemisSecurityManager(): ActiveMQJAASSecurityManager {
        // TODO: set up proper security configuration https://r3-cev.atlassian.net/browse/COR-307
        val securityConfig = SecurityConfiguration().apply {
            addUser("internal", BigInteger(128, newSecureRandom()).toString(16))
            addRole("internal", "internal")
            defaultUser = "internal"
        }

        return ActiveMQJAASSecurityManager(InVMLoginModule::class.java.name, securityConfig)
    }

    /**
     * For every queue created we need to have a bridge deployed in case the address of the queue
     * is that of a remote party
     */
    private fun maybeDeployBridgeForAddress(name: String) {
        val hostAndPort = HostAndPort.fromString(name)

        fun connectorExists() = name in activeMQServer.configuration.connectorConfigurations

        fun addConnector() = activeMQServer.configuration.addConnectorConfiguration(
                name,
                tcpTransport(
                        ConnectionDirection.OUTBOUND,
                        hostAndPort.hostText,
                        hostAndPort.port
                )
        )

        fun deployBridge() = activeMQServer.deployBridge(BridgeConfiguration().apply {
            setName(name)
            queueName = name
            forwardingAddress = name
            staticConnectors = listOf(name)
            confirmationWindowSize = 100000 // a guess
        })

        if (!connectorExists()) {
            addConnector()
            deployBridge()
        }
    }

    private fun setConfigDirectories(config: Configuration, dir: Path) {
        config.apply {
            bindingsDirectory = dir.resolve("bindings").toString()
            journalDirectory = dir.resolve("journal").toString()
            largeMessagesDirectory = dir.resolve("largemessages").toString()
        }
    }
}
