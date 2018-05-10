package net.corda.node.services.network

import net.corda.cordform.CordformNode
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.*
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import rx.Observable
import rx.Scheduler
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.streams.toList

sealed class NodeInfoUpdate{
    class Add(val nodeInfo: NodeInfo): NodeInfoUpdate()
    class Remove(val hash: SecureHash): NodeInfoUpdate()
}

/**
 * Class containing the logic to
 * - Serialize and de-serialize a [NodeInfo] to disk and reading it back.
 * - Poll a directory for new serialized [NodeInfo]
 *
 * @param nodePath the base path of a node.
 * @param pollInterval how often to poll the filesystem in milliseconds. Must be longer than 5 seconds.
 * @param scheduler a [Scheduler] for the rx [Observable] returned by [nodeInfoUpdates], this is mainly useful for
 *        testing. It defaults to the io scheduler which is the appropriate value for production uses.
 */
// TODO: Use NIO watch service instead?
class NodeInfoWatcher(private val nodePath: Path,
                      private val scheduler: Scheduler,
                      private val pollInterval: Duration = 5.seconds) {
    companion object {
        private val logger = contextLogger()

        // TODO This method doesn't belong in this class
        fun saveToFile(path: Path, nodeInfoAndSigned: NodeInfoAndSigned) {
            // By using the hash of the node's first name we ensure:
            // 1) node info files for the same node map to the same filename and thus avoid having duplicate files for
            //    the same node
            // 2) avoid having to deal with characters in the X.500 name which are incompatible with the local filesystem
            val fileNameHash = nodeInfoAndSigned.nodeInfo.legalIdentities[0].name.serialize().hash
            nodeInfoAndSigned
                    .signed
                    .serialize()
                    .open()
                    .copyTo(path / "${NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX}$fileNameHash", REPLACE_EXISTING)
        }
    }

    private val nodeInfosDir = nodePath / CordformNode.NODE_INFO_DIRECTORY
    private val nodeInfoFiles = HashMap<Path, FileTime>()
    private val processedPathHashMap = HashMap<Path, SecureHash>()
    val processedNodeInfoHashes: Set<SecureHash> get() = processedPathHashMap.values.toSet()

    init {
        require(pollInterval >= 5.seconds) { "Poll interval must be 5 seconds or longer." }
        nodeInfosDir.createDirectories()
    }

    /**
     * Read all the files contained in [nodePath] / [CordformNode.NODE_INFO_DIRECTORY] and keep watching
     * the folder for further updates.
     *
     * We simply list the directory content every 5 seconds, the Java implementation of WatchService has been proven to
     * be unreliable on MacOs and given the fairly simple use case we have, this simple implementation should do.
     *
     * @return an [Observable] returning [NodeInfoUpdate]s, at most one [NodeInfo] is returned for each processed file.
     */
    fun nodeInfoUpdates(): Observable<NodeInfoUpdate> {
        return Observable.interval(pollInterval.toMillis(), TimeUnit.MILLISECONDS, scheduler)
                .flatMapIterable { loadFromDirectory() }
    }

    // TODO This method doesn't belong in this class
    fun saveToFile(nodeInfoAndSigned: NodeInfoAndSigned) {
        return Companion.saveToFile(nodePath, nodeInfoAndSigned)
    }

    private fun loadFromDirectory(): List<NodeInfoUpdate> {
        val processedPaths = HashSet<Path>()
        val result = nodeInfosDir.list { paths ->
            paths
                    .filter { it.isRegularFile() }
                    .filter { file ->
                        val lastModifiedTime = file.lastModifiedTime()
                        val previousLastModifiedTime = nodeInfoFiles[file]
                        val newOrChangedFile = previousLastModifiedTime == null || lastModifiedTime > previousLastModifiedTime
                        nodeInfoFiles[file] = lastModifiedTime
                        processedPaths.add(file)
                        newOrChangedFile
                    }
                    .mapNotNull { file ->
                        logger.debug { "Reading SignedNodeInfo from $file" }
                        try {
                            val nodeInfoSigned = NodeInfoAndSigned(file.readObject())
                            processedPathHashMap[file] = nodeInfoSigned.signed.raw.hash
                            nodeInfoSigned
                        } catch (e: Exception) {
                            logger.warn("Unable to read SignedNodeInfo from $file", e)
                            null
                        }
                    }
                    .toList()
        }
        val removedFiles = processedPathHashMap.keys - processedPaths
        val removedHashes = removedFiles.mapNotNull { file ->
            processedPathHashMap[file]?.let {
                processedPathHashMap.remove(file)
                NodeInfoUpdate.Remove(it)
            }
        }
        logger.debug { "Read ${result.size} NodeInfo files from $nodeInfosDir" }
        logger.debug { "Number of removed files: ${removedHashes.size}" }
        return result.map { NodeInfoUpdate.Add(it.nodeInfo) } + removedHashes
    }
}

// TODO Remove this once we have a tool that can read AMQP serialised files
fun main(args: Array<String>) {
    _contextSerializationEnv.set(SerializationEnvironmentImpl(
            SerializationFactoryImpl().apply {
                registerScheme(AMQPServerSerializationScheme())
            },
            AMQP_P2P_CONTEXT)
    )
    println(Paths.get(args[0]).readObject<SignedNodeInfo>().verified())
}
