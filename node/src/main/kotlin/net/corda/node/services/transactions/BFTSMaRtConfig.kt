package net.corda.node.services.transactions

import com.google.common.net.HostAndPort
import net.corda.core.div
import net.corda.core.utilities.loggerFor
import java.io.FileWriter
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
class BFTSMaRtConfig(private val replicaAddresses: List<HostAndPort>, debug: Boolean = false) : PathManager<BFTSMaRtConfig>(Files.createTempDirectory("bft-smart-config")) {
    companion object {
        private val log = loggerFor<BFTSMaRtConfig>()
        internal val portIsClaimedFormat = "Port %s is claimed by another replica: %s"
    }

    init {
        val claimedPorts = mutableSetOf<HostAndPort>()
        val n = replicaAddresses.size
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
        FileWriter((path / name).toFile()).use {
            PrintWriter(it).use {
                it.run(block)
            }
        }
    }

    fun waitUntilReplicaWillNotPrintStackTrace(contextReplicaId: Int) {
        replicaAddresses.subList(0, contextReplicaId).forEachIndexed { otherId, baseAddress ->
            // The printStackTrace we want to avoid is in replica-replica communication code:
            val address = BFTSMaRtPort.forReplicas.ofReplica(baseAddress)
            log.debug("Waiting for replica $otherId to start listening on: $address")
            while (!isOpen(address)) MILLISECONDS.sleep(200)
            log.debug("Replica $otherId is ready for P2P.")
        }
    }

    private fun replicaPorts(replicaId: Int): List<HostAndPort> {
        val base = replicaAddresses[replicaId]
        return BFTSMaRtPort.values().map { it.ofReplica(base) }
    }
}

private enum class BFTSMaRtPort(private val off: Int) {
    forClients(0), forReplicas(1);

    fun ofReplica(base: HostAndPort) = HostAndPort.fromParts(base.host, base.port + off)
}

private fun isOpen(address: HostAndPort) = try {
    Socket(address.host, address.port).use { true } // Will cause one error to be logged in the replica on success.
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
