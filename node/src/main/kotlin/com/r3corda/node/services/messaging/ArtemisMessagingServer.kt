package com.r3corda.node.services.messaging

import com.google.common.net.HostAndPort
import com.r3corda.core.ThreadBox
import com.r3corda.core.crypto.AddressFormatException
import com.r3corda.core.crypto.newSecureRandom
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.services.NetworkMapCache
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.services.config.NodeConfiguration
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.core.config.BridgeConfiguration
import org.apache.activemq.artemis.core.config.Configuration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule
import rx.Subscription
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
class ArtemisMessagingServer(config: NodeConfiguration,
                             val myHostPort: HostAndPort,
                             val networkMapCache: NetworkMapCache) : ArtemisMessagingComponent(config) {
    companion object {
        val log = loggerFor<ArtemisMessagingServer>()
    }

    private class InnerState {
        var running = false
    }

    private val mutex = ThreadBox(InnerState())
    private lateinit var activeMQServer: ActiveMQServer
    private var networkChangeHandle: Subscription? = null

    fun start() = mutex.locked {
        if (!running) {
            configureAndStartServer()
            networkChangeHandle = networkMapCache.changed.subscribe { onNetworkChange(it) }
            running = true
        }
    }

    fun stop() = mutex.locked {
        networkChangeHandle?.unsubscribe()
        networkChangeHandle = null
        activeMQServer.stop()
        running = false
    }

    fun bridgeToNetworkMapService(networkMapService: SingleMessageRecipient?) {
        if ((networkMapService != null) && (networkMapService is NetworkMapAddress)) {
            val query = activeMQServer.queueQuery(NETWORK_MAP_ADDRESS)
            if (!query.isExists) {
                activeMQServer.createQueue(NETWORK_MAP_ADDRESS, NETWORK_MAP_ADDRESS, null, true, false)
            }

            maybeDeployBridgeForAddress(NETWORK_MAP_ADDRESS, networkMapService)
        }
    }

    private fun onNetworkChange(change: NetworkMapCache.MapChange) {
        val address = change.node.address
        if (address is ArtemisMessagingComponent.ArtemisAddress) {
            val queueName = address.queueName
            when (change.type) {
                NetworkMapCache.MapChangeType.Added -> {
                    val query = activeMQServer.queueQuery(queueName)
                    if (query.isExists) {
                        // Queue exists so now wire up bridge
                        maybeDeployBridgeForAddress(queueName, change.node.address)
                    }
                }

                NetworkMapCache.MapChangeType.Modified -> {
                    (change.prevNodeInfo?.address as? ArtemisMessagingComponent.ArtemisAddress)?.let {
                        // remove any previous possibly different bridge
                        maybeDestroyBridge(it.queueName)
                    }
                    val query = activeMQServer.queueQuery(queueName)
                    if (query.isExists) {
                        // Deploy new bridge
                        maybeDeployBridgeForAddress(queueName, change.node.address)
                    }
                }

                NetworkMapCache.MapChangeType.Removed -> {
                    (change.prevNodeInfo?.address as? ArtemisMessagingComponent.ArtemisAddress)?.let {
                        // Remove old bridge
                        maybeDestroyBridge(it.queueName)
                    }
                    // just in case of NetworkMapCache version issues
                    maybeDestroyBridge(queueName)
                }
            }
        }
    }

    private fun configureAndStartServer() {
        val config = createArtemisConfig(config.certificatesPath, myHostPort).apply {
            securityRoles = mapOf(
                    "#" to setOf(Role("internal", true, true, true, true, true, true, true))
            )
        }

        val securityManager = createArtemisSecurityManager()

        activeMQServer = ActiveMQServerImpl(config, securityManager).apply {
            // Throw any exceptions which are detected during startup
            registerActivationFailureListener { exception -> throw exception }

            // Some types of queue might need special preparation on our side, like dialling back or preparing
            // a lazily initialised subsystem.
            registerPostQueueCreationCallback { queueName ->
                log.debug("Queue created: $queueName")
                if (queueName.startsWith(PEERS_PREFIX) && queueName != NETWORK_MAP_ADDRESS) {
                    try {
                        val identity = parseKeyFromQueueName(queueName.toString())
                        val nodeInfo = networkMapCache.getNodeByPublicKey(identity)
                        if (nodeInfo != null) {
                            maybeDeployBridgeForAddress(queueName, nodeInfo.address)
                        } else {
                            log.error("Queue created for a peer that we don't know from the network map: $queueName")
                        }
                    } catch (e: AddressFormatException) {
                        log.error("Protocol violation: Could not parse queue name as Base 58: $queueName")
                    }
                }
            }

            registerPostQueueDeletionCallback { address, qName ->
                if (qName == address)
                    log.debug("Queue deleted: $qName")
                else
                    log.debug("Queue deleted: $qName for $address")
            }
        }
        activeMQServer.start()
    }

    private fun createArtemisConfig(directory: Path, hp: HostAndPort): Configuration {
        val config = ConfigurationImpl()
        setConfigDirectories(config, directory)
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

    private fun connectorExists(hostAndPort: HostAndPort) = hostAndPort.toString() in activeMQServer.configuration.connectorConfigurations

    private fun addConnector(hostAndPort: HostAndPort) = activeMQServer.configuration.addConnectorConfiguration(
            hostAndPort.toString(),
            tcpTransport(
                    ConnectionDirection.OUTBOUND,
                    hostAndPort.hostText,
                    hostAndPort.port
            )
    )

    private fun bridgeExists(name: SimpleString) = activeMQServer.clusterManager.bridges.containsKey(name.toString())

    private fun deployBridge(hostAndPort: HostAndPort, name: SimpleString) {
        activeMQServer.deployBridge(BridgeConfiguration().apply {
            val nameStr = name.toString()
            setName(nameStr)
            queueName = nameStr
            forwardingAddress = nameStr
            staticConnectors = listOf(hostAndPort.toString())
            confirmationWindowSize = 100000 // a guess
        })
    }

    /**
     * For every queue created we need to have a bridge deployed in case the address of the queue
     * is that of a remote party.
     */
    private fun maybeDeployBridgeForAddress(name: SimpleString, nodeInfo: SingleMessageRecipient) {
        require(name.startsWith(PEERS_PREFIX))
        val hostAndPort = toHostAndPort(nodeInfo)
        if (hostAndPort == myHostPort)
            return
        if (!connectorExists(hostAndPort))
            addConnector(hostAndPort)
        if (!bridgeExists(name))
            deployBridge(hostAndPort, name)
    }

    private fun maybeDestroyBridge(name: SimpleString) {
        if (bridgeExists(name)) {
            activeMQServer.destroyBridge(name.toString())
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
