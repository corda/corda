package net.corda.loadtest

import com.google.common.net.HostAndPort
import com.jcraft.jsch.*
import com.jcraft.jsch.agentproxy.AgentProxy
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector
import com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.messaging.CordaRPCOps
import net.corda.node.driver.PortAllocation
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.util.*
import kotlin.streams.toList

private val log = LoggerFactory.getLogger(ConnectionManager::class.java)

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
                if (connector.isAvailable) {
                    return IdentityRepository.RUNNING
                } else {
                    return IdentityRepository.UNAVAILABLE
                }
            }

            override fun getName() = connector.name
            override fun getIdentities(): Vector<Identity> = Vector(listOf(
                    object : Identity {
                        override fun clear() {
                        }

                        override fun getAlgName() = String(Buffer(identity.blob).string)
                        override fun getName() = String(identity.comment)
                        override fun isEncrypted() = false
                        override fun getSignature(data: ByteArray?) = agentProxy.sign(identity.blob, data)
                        override fun decrypt() = true
                        override fun getPublicKeyBlob() = identity.blob
                        override fun setPassphrase(passphrase: ByteArray?) = true
                    }
            ))

            override fun remove(blob: ByteArray?) = throw UnsupportedOperationException()
            override fun removeAll() = throw UnsupportedOperationException()
            override fun add(identity: ByteArray?) = throw UnsupportedOperationException()
        }
    }
}

class ConnectionManager(private val username: String, private val jSch: JSch) {
    fun connectToNode(
            nodeHost: String,
            remoteMessagingPort: Int,
            localTunnelAddress: HostAndPort,
            rpcUsername: String,
            rpcPassword: String
    ): NodeConnection {
        val session = jSch.getSession(username, nodeHost, 22)
        // We don't check the host fingerprints because they may change often
        session.setConfig("StrictHostKeyChecking", "no")
        log.info("Connecting to $nodeHost...")
        session.connect()
        log.info("Connected to $nodeHost!")

        log.info("Creating tunnel from $nodeHost:$remoteMessagingPort to $localTunnelAddress...")
        session.setPortForwardingL(localTunnelAddress.port, localTunnelAddress.host, remoteMessagingPort)
        log.info("Tunnel created!")

        val connection = NodeConnection(nodeHost, session, localTunnelAddress, rpcUsername, rpcPassword)
        connection.startClient()
        return connection
    }
}

/**
 * Connects to a list of nodes and executes the passed in action with the connections as parameter. The connections are
 * safely cleaned up if an exception is thrown.
 *
 * @param username The UNIX username to use for SSH authentication.
 * @param nodeHosts The list of hosts.
 * @param remoteMessagingPort The Artemis messaging port nodes are listening on.
 * @param tunnelPortAllocation A local port allocation strategy for creating SSH tunnels.
 * @param withConnections An action to run once we're connected to the nodes.
 * @return The return value of [withConnections]
 */
fun <A> connectToNodes(
        username: String,
        nodeHosts: List<String>,
        remoteMessagingPort: Int,
        tunnelPortAllocation: PortAllocation,
        rpcUsername: String,
        rpcPassword: String,
        withConnections: (List<NodeConnection>) -> A
): A {
    val manager = ConnectionManager(username, setupJSchWithSshAgent())
    val connections = nodeHosts.parallelStream().map { nodeHost ->
        manager.connectToNode(
                nodeHost = nodeHost,
                remoteMessagingPort = remoteMessagingPort,
                localTunnelAddress = tunnelPortAllocation.nextHostAndPort(),
                rpcUsername = rpcUsername,
                rpcPassword = rpcPassword
        )
    }.toList()

    return try {
        withConnections(connections)
    } finally {
        connections.forEach(NodeConnection::close)
    }
}

/**
 * [NodeConnection] allows executing remote shell commands on the node as well as executing RPCs.
 * The RPC Client start/stop must be controlled externally with [startClient] and [doWhileClientStopped]. For example
 * if we want to do some action on the node that requires bringing down of the node we should nest it in a
 * [doWhileClientStopped], otherwise the RPC link will be broken.
 */
class NodeConnection(
        val hostName: String,
        private val jSchSession: Session,
        private val localTunnelAddress: HostAndPort,
        private val rpcUsername: String,
        private val rpcPassword: String
) : Closeable {
    private val client = CordaRPCClient(localTunnelAddress)
    private var connection: CordaRPCConnection? = null
    val proxy: CordaRPCOps get() = connection?.proxy ?: throw IllegalStateException("proxy requested, but the client is not running")

    data class ShellCommandOutput(
            val originalShellCommand: String,
            val exitCode: Int,
            val stdout: String,
            val stderr: String
    ) {
        fun getResultOrThrow(): String {
            if (exitCode != 0) {
                val diagnostic =
                        "There was a problem running \"$originalShellCommand\":\n" +
                                "    stdout:\n$stdout" +
                                "    stderr:\n$stderr"
                log.error(diagnostic)
                throw Exception(diagnostic)
            } else {
                return stdout
            }
        }
    }

    fun <A> doWhileClientStopped(action: () -> A): A {
        val connection = connection
        require(connection != null) { "doWhileClientStopped called with no running client" }
        log.info("Stopping RPC proxy to $hostName, tunnel at $localTunnelAddress")
        connection!!.close()
        try {
            return action()
        } finally {
            log.info("Starting new RPC proxy to $hostName, tunnel at $localTunnelAddress")
            // TODO expose these somehow?
            val newConnection = client.start(rpcUsername, rpcPassword)
            this.connection = newConnection
        }
    }

    fun startClient() {
        log.info("Creating RPC proxy to $hostName, tunnel at $localTunnelAddress")
        connection = client.start(rpcUsername, rpcPassword)
        log.info("Proxy created")
    }

    /**
     * @return Pair of (stdout, stderr) of command
     */
    fun runShellCommandGetOutput(command: String): ShellCommandOutput {
        log.info("Running '$command' on $hostName")
        val (exitCode, pair) = withChannelExec(command) { channel ->
            val stdoutStream = ByteArrayOutputStream()
            val stderrStream = ByteArrayOutputStream()
            channel.outputStream = stdoutStream
            channel.setErrStream(stderrStream)
            channel.connect()
            poll { channel.isEOF }
            Pair(stdoutStream.toString(), stderrStream.toString())
        }
        return ShellCommandOutput(
                originalShellCommand = command,
                exitCode = exitCode,
                stdout = pair.first,
                stderr = pair.second
        )
    }

    /**
     * @param function should call [ChannelExec.connect]
     * @return A pair of (exit code, [function] return value)
     */
    private fun <A> withChannelExec(command: String, function: (ChannelExec) -> A): Pair<Int, A> {
        val channel = jSchSession.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        try {
            val result = function(channel)
            poll { channel.isEOF }
            return Pair(channel.exitStatus, result)
        } finally {
            channel.disconnect()
        }
    }

    override fun close() {
        connection?.close()
        jSchSession.disconnect()
    }
}

fun poll(intervalMilliseconds: Long = 500, function: () -> Boolean) {
    while (!function()) {
        Thread.sleep(intervalMilliseconds)
    }
}
