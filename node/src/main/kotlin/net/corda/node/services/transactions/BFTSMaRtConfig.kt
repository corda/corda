package net.corda.node.services.transactions

import com.google.common.net.HostAndPort
import net.corda.core.div
import java.io.FileWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.nio.file.Files

/**
 * BFT SMaRt can only be configured via files in a configHome directory.
 * Each instance of this class creates such a configHome, accessible via [path].
 * The files are deleted on [close] typically via [use], see [PathManager] for details.
 */
class BFTSMaRtConfig(replicaAddresses: List<HostAndPort>) : PathManager(Files.createTempDirectory("bft-smart-config")) {
    companion object {
        internal val portIsClaimedFormat = "Port %s is claimed by another replica: %s"
    }

    init {
        val claimedPorts = mutableSetOf<Int>()
        replicaAddresses.map { it.port }.forEach { base ->
            // Each replica claims the configured port and the next one:
            (0..1).map { base + it }.forEach { port ->
                claimedPorts.add(port) || throw IllegalArgumentException(portIsClaimedFormat.format(port, claimedPorts))
            }
        }
        configWriter("hosts.config") {
            replicaAddresses.forEachIndexed { index, address ->
                // The documentation strongly recommends IP addresses:
                println("${index} ${InetAddress.getByName(address.host).hostAddress} ${address.port}")
            }
        }
        val n = replicaAddresses.size
        val systemConfig = String.format(javaClass.getResource("system.config.printf").readText(), n, maxFaultyReplicas(n))
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
