/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.loadtest

import com.jcraft.jsch.Buffer
import com.jcraft.jsch.Identity
import com.jcraft.jsch.IdentityRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.agentproxy.AgentProxy
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector
import com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.testing.driver.PortAllocation
import java.util.*
import kotlin.streams.toList

private val log = loggerFor<ConnectionManager>()

/**
 * Creates a new [JSch] instance with identities loaded from the running SSH agent.
 */
fun setupJSchWithSshAgent(): JSch {
    val connector = SSHAgentConnector(JNAUSocketFactory())
    val agentProxy = AgentProxy(connector)
    val identities = agentProxy.identities
    require(identities.isNotEmpty()) { "No SSH identities found, please add one to the agent" }
    require(identities.size == 1) { "Multiple SSH identities found, don't know which one to pick" }
    val identity = identities[0]
    log.info("Using SSH identity ${String(identity.comment)}")

    return JSch().apply {
        identityRepository = object : IdentityRepository {
            override fun getStatus(): Int {
                return if (connector.isAvailable) {
                    IdentityRepository.RUNNING
                } else {
                    IdentityRepository.UNAVAILABLE
                }
            }

            override fun getName() = connector.name
            override fun getIdentities(): Vector<Identity> = Vector(listOf(
                    object : Identity {
                        override fun clear() {}
                        override fun getAlgName() = String(Buffer(identity.blob).string)
                        override fun getName() = String(identity.comment)
                        override fun isEncrypted() = false
                        override fun getSignature(data: ByteArray?) = agentProxy.sign(identity.blob, data)
                        @Suppress("OverridingDeprecatedMember")
                        override fun decrypt() = true

                        override fun getPublicKeyBlob() = identity.blob
                        override fun setPassphrase(passphrase: ByteArray?) = true
                    }
            ))

            override fun remove(blob: ByteArray?) = throw UnsupportedOperationException()
            override fun removeAll() = throw UnsupportedOperationException()
            override fun add(bytes: ByteArray?) = throw UnsupportedOperationException()
        }
    }
}

class ConnectionManager(private val jSch: JSch) {
    fun connectToNode(remoteNode: RemoteNode, localTunnelAddress: NetworkHostAndPort): NodeConnection {
        val session = jSch.getSession(remoteNode.sshUserName, remoteNode.hostname, 22)
        // We don't check the host fingerprints because they may change often
        session.setConfig("StrictHostKeyChecking", "no")
        log.info("Connecting to ${remoteNode.hostname}...")
        session.connect()
        log.info("Connected to ${remoteNode.hostname}!")

        log.info("Creating tunnel from ${remoteNode.hostname} to $localTunnelAddress...")
        session.setPortForwardingL(localTunnelAddress.port, localTunnelAddress.host, remoteNode.rpcPort)
        log.info("Tunnel created!")

        val connection = NodeConnection(remoteNode, session, localTunnelAddress)
        connection.startNode()
        connection.waitUntilUp()
        connection.startRPCClient()
        return connection
    }
}

/**
 * Connects to a list of nodes and executes the passed in action with the connections as parameter. The connections are
 * safely cleaned up if an exception is thrown.
 *
 * @param tunnelPortAllocation A local port allocation strategy for creating SSH tunnels.
 * @param withConnections An action to run once we're connected to the nodes.
 * @return The return value of [withConnections]
 */
fun <A> connectToNodes(remoteNodes: List<RemoteNode>, tunnelPortAllocation: PortAllocation, withConnections: (List<NodeConnection>) -> A): A {
    val manager = ConnectionManager(setupJSchWithSshAgent())
    val connections = remoteNodes.parallelStream().map { remoteNode ->
        manager.connectToNode(remoteNode, tunnelPortAllocation.nextHostAndPort())
    }.toList()
    return try {
        withConnections(connections)
    } finally {
        connections.forEach(NodeConnection::close)
    }
}

fun poll(intervalMilliseconds: Long = 500, function: () -> Boolean) {
    while (!function()) {
        Thread.sleep(intervalMilliseconds)
    }
}
