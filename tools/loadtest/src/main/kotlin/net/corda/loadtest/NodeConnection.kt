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

import com.google.common.annotations.VisibleForTesting
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.fork
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.addShutdownHook
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.util.concurrent.ForkJoinPool

/**
 * [NodeConnection] allows executing remote shell commands on the node as well as executing RPCs.
 * The RPC Client start/stop must be controlled externally with [startClient] and [doWhileClientStopped]. For example
 * if we want to do some action on the node that requires bringing down of the node we should nest it in a
 * [doWhileClientStopped], otherwise the RPC link will be broken.
 * TODO: Auto reconnect has been enable for RPC connection, investigate if we still need [doWhileClientStopped].
 */
class NodeConnection(val remoteNode: RemoteNode, private val jSchSession: Session, private val localTunnelAddress: NetworkHostAndPort) : Closeable {
    companion object {
        private val log = contextLogger()
    }

    init {
        addShutdownHook {
            close()
        }
    }

    private val client = CordaRPCClient(localTunnelAddress)
    private var rpcConnection: CordaRPCConnection? = null
    val proxy: CordaRPCOps get() = rpcConnection?.proxy ?: throw IllegalStateException("proxy requested, but the client is not running")
    val info: NodeInfo by lazy { proxy.nodeInfo() } // TODO used only when queried for advertised services
    @VisibleForTesting
    val mainIdentity: Party by lazy { info.legalIdentitiesAndCerts.first().party }

    fun <A> doWhileClientStopped(action: () -> A): A {
        val connection = rpcConnection
        require(connection != null) { "doWhileClientStopped called with no running client" }
        log.info("Stopping RPC proxy to ${remoteNode.hostname}, tunnel at $localTunnelAddress")
        connection!!.close()
        try {
            return action()
        } finally {
            log.info("Starting new RPC proxy to ${remoteNode.hostname}, tunnel at $localTunnelAddress")
            // TODO expose these somehow?
            val newConnection = client.start(remoteNode.rpcUser.username, remoteNode.rpcUser.password)
            this.rpcConnection = newConnection
        }
    }

    fun startRPCClient() {
        log.info("Creating RPC proxy to ${remoteNode.hostname}, tunnel at $localTunnelAddress")
        rpcConnection = client.start(remoteNode.rpcUser.username, remoteNode.rpcUser.password)
        log.info("Proxy created")
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

    /**
     * @return Pair of (stdout, stderr) of command
     */
    fun runShellCommandGetOutput(command: String): ShellCommandOutput {
        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()
        val exitCode = runShellCommand(command, stdoutStream, stderrStream).get()
        return ShellCommandOutput(command, exitCode, stdoutStream.toString(), stderrStream.toString())
    }

    private fun runShellCommand(command: String, stdout: OutputStream, stderr: OutputStream): CordaFuture<Int> {
        log.info("Running '$command' on ${remoteNode.hostname}")
        return ForkJoinPool.commonPool().fork {
            val (exitCode, _) = withChannelExec(command) { channel ->
                channel.outputStream = stdout
                channel.setErrStream(stderr)
                channel.connect()
                poll { channel.isEOF }
            }
            exitCode
        }
    }

    data class ShellCommandOutput(val originalShellCommand: String, val exitCode: Int, val stdout: String, val stderr: String) {
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

    fun startNode() {
        runShellCommandGetOutput("sudo systemctl start ${remoteNode.systemdServiceName}").getResultOrThrow()
    }

    fun stopNode() {
        runShellCommandGetOutput("sudo systemctl stop ${remoteNode.systemdServiceName}").getResultOrThrow()
    }

    fun restartNode() {
        runShellCommandGetOutput("sudo systemctl restart ${remoteNode.systemdServiceName}").getResultOrThrow()
    }

    fun waitUntilUp() {
        log.info("Waiting for ${remoteNode.hostname} to come online")
        runShellCommandGetOutput("until sudo netstat -tlpn | grep ${remoteNode.rpcPort} > /dev/null ; do sleep 1 ; done")
    }

    fun getNodePid(): String {
        return runShellCommandGetOutput("sudo netstat -tlpn | grep ${remoteNode.rpcPort} | awk '{print $7}' | grep -oE '[0-9]+'").getResultOrThrow().replace("\n", "")
    }

    fun <A> doWhileStopped(action: () -> A): A {
        return doWhileClientStopped {
            stopNode()
            try {
                action()
            } finally {
                startNode()
            }
        }
    }

    fun kill() {
        runShellCommandGetOutput("sudo kill ${getNodePid()}")
    }

    fun <A> doWhileSigStopped(action: () -> A): A {
        val pid = getNodePid()
        log.info("PID is $pid")
        runShellCommandGetOutput("sudo kill -SIGSTOP $pid").getResultOrThrow()
        try {
            return action()
        } finally {
            runShellCommandGetOutput("sudo kill -SIGCONT $pid").getResultOrThrow()
        }
    }

    fun clearDb() = doWhileStopped { runShellCommandGetOutput("sudo rm ${remoteNode.nodeDirectory}/persistence.mv.db").getResultOrThrow() }

    override fun close() {
        rpcConnection?.close()
        jSchSession.disconnect()
    }
}