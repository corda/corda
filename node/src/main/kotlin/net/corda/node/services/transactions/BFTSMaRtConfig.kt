package net.corda.node.services.transactions

import net.corda.core.internal.div
import net.corda.core.internal.writer
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import java.io.PrintWriter
import java.net.InetAddress
import java.net.Socket
import java.net.SocketException
import java.nio.file.Files
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * BFT SMaRt can only be configured via files in a configHome directory.
 * Each instance of this class creates such a configHome, accessible via [path].
 * The files are deleted on [close] typically via [use], see [PathManager] for details.
 */
class BFTSMaRtConfig(private val replicaAddresses: List<NetworkHostAndPort>, debug: Boolean, val exposeRaces: Boolean) : PathManager<BFTSMaRtConfig>(Files.createTempDirectory("bft-smart-config")) {
    companion object {
        private val log = contextLogger()
        internal val portIsClaimedFormat = "Port %s is claimed by another replica: %s"
    }

    val clusterSize get() = replicaAddresses.size

    init {
        val claimedPorts = mutableSetOf<NetworkHostAndPort>()
        val n = clusterSize
        (0 until n).forEach { replicaId ->
            // Each replica claims the configured port and the next one:
            replicaPorts(replicaId).forEach { port ->
                claimedPorts.add(port) || throw IllegalArgumentException(portIsClaimedFormat.format(port, claimedPorts))
            }
        }
        configWriter("hosts.config") {
            replicaAddresses.forEachIndexed { index, address ->
                // The documentation strongly recommends IP addresses:
                println("$index ${InetAddress.getByName(address.host).hostAddress} ${address.port}")
            }
        }
        val systemConfig = String.format(javaClass.getResource("system.config.printf").readText(), n, maxFaultyReplicas(n), if (debug) 1 else 0, (0 until n).joinToString(","))
        configWriter("system.config") {
            print(systemConfig)
        }
    }

    private fun configWriter(name: String, block: PrintWriter.() -> Unit) {
        // Default charset, consistent with loaders:
        (path / name).writer().use {
            PrintWriter(it).use {
                it.run(block)
            }
        }
    }

    fun waitUntilReplicaWillNotPrintStackTrace(contextReplicaId: Int) {
        // A replica will printStackTrace until all lower-numbered replicas are listening.
        // But we can't probe a replica without it logging EOFException when our probe succeeds.
        // So to keep logging to a minimum we only check the previous replica:
        val peerId = contextReplicaId - 1
        if (peerId < 0) return
        // The printStackTrace we want to avoid is in replica-replica communication code:
        val address = BFTSMaRtPort.FOR_REPLICAS.ofReplica(replicaAddresses[peerId])
        log.debug { "Waiting for replica $peerId to start listening on: $address" }
        while (!address.isListening()) MILLISECONDS.sleep(200)
        log.debug { "Replica $peerId is ready for P2P." }
    }

    private fun replicaPorts(replicaId: Int): List<NetworkHostAndPort> {
        val base = replicaAddresses[replicaId]
        return BFTSMaRtPort.values().map { it.ofReplica(base) }
    }
}

private enum class BFTSMaRtPort(private val off: Int) {
    FOR_CLIENTS(0),
    FOR_REPLICAS(1);

    fun ofReplica(base: NetworkHostAndPort) = NetworkHostAndPort(base.host, base.port + off)
}

private fun NetworkHostAndPort.isListening() = try {
    Socket(host, port).use { true } // Will cause one error to be logged in the replica on success.
} catch (e: SocketException) {
    false
}

fun maxFaultyReplicas(clusterSize: Int) = (clusterSize - 1) / 3
fun minCorrectReplicas(clusterSize: Int) = (2 * clusterSize + 3) / 3
fun minClusterSize(maxFaultyReplicas: Int) = maxFaultyReplicas * 3 + 1

fun bftSMaRtSerialFilter(clazz: Class<*>): Boolean = clazz.name.let {
    it.startsWith("bftsmart.")
            || it.startsWith("java.security.")
            || it.startsWith("java.util.")
            || it.startsWith("java.lang.")
            || it.startsWith("java.net.")
}
