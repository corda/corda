package net.corda.node.utilities

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.composite
import net.corda.core.crypto.generateKeyPair
import net.corda.core.serialization.serialize
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ServiceIdentityGenerator {
    private val log = loggerFor<ServiceIdentityGenerator>()

    /**
     * Generates signing key pairs and a common distributed service identity for a set of nodes.
     * The key pairs and the group identity get serialized to disk in the corresponding node directories.
     * This method should be called *before* any of the nodes are started.
     *
     * @param dirs List of node directories to place the generated identity and key pairs in.
     * @param serviceId The service id of the distributed service.
     * @param serviceName The legal name of the distributed service.
     * @param threshold The threshold for the generated group [CompositeKey.Node].
     */
    fun generateToDisk(dirs: List<Path>, serviceId: String, serviceName: String, threshold: Int = 1) {
        log.trace { "Generating a group identity \"serviceName\" for nodes: ${dirs.joinToString()}" }

        val keyPairs = (1..dirs.size).map { generateKeyPair() }
        val notaryKey = CompositeKey.Builder().addKeys(keyPairs.map { it.public.composite }).build(threshold)
        val notaryParty = Party(serviceName, notaryKey).serialize()

        keyPairs.zip(dirs) { keyPair, dir ->
            Files.createDirectories(dir)
            val privateKeyFile = "$serviceId-private-key"
            val publicKeyFile = "$serviceId-public"
            notaryParty.writeToFile(dir.resolve(publicKeyFile))
            keyPair.serialize().writeToFile(dir.resolve(privateKeyFile))
        }
    }
}

fun main(args: Array<String>) {
    val dirs = args[0].split(",").map { Paths.get(it) }
    val serviceId = args[1]
    val serviceName = args[2]
    val quorumSize = args.getOrNull(3)?.toInt() ?: 1

    println("Generating service identity for \"$serviceName\"")
    ServiceIdentityGenerator.generateToDisk(dirs, serviceId, serviceName, quorumSize)
}