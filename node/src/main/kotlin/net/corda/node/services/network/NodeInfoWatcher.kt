package net.corda.node.services.network

import net.corda.core.crypto.SecureHash
import net.corda.core.internal.NODE_INFO_DIRECTORY
import net.corda.core.internal.copyTo
import net.corda.core.internal.readObject
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier
import rx.Observable
import rx.Scheduler
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.useDirectoryEntries

sealed class NodeInfoUpdate {
    data class Add(val nodeInfo: NodeInfo) : NodeInfoUpdate()
    data class Remove(val hash: SecureHash) : NodeInfoUpdate()
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
                      internal val scheduler: Scheduler,
                      private val pollInterval: Duration = 5.seconds) {
    companion object {
        private val logger = contextLogger()

        // TODO This method doesn't belong in this class
        fun saveToFile(path: Path, nodeInfoAndSigned: NodeInfoAndSigned): Path {
            // By using the hash of the node's first name we ensure:
            // 1) node info files for the same node map to the same filename and thus avoid having duplicate files for
            //    the same node
            // 2) avoid having to deal with characters in the X.500 name which are incompatible with the local filesystem
            val fileNameHash = nodeInfoAndSigned.nodeInfo.legalIdentities[0].name.serialize().hash
            val target = path / "${NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX}$fileNameHash"
            nodeInfoAndSigned
                    .signed
                    .serialize()
                    .open()
                    .copyTo(target, REPLACE_EXISTING)

            return target
        }
    }

    private data class NodeInfoFromFile(val nodeInfohash: SecureHash, val lastModified: FileTime)

    private val nodeInfosDir = nodePath / NODE_INFO_DIRECTORY
    private val nodeInfoFilesMap = HashMap<Path, NodeInfoFromFile>()
    val processedNodeInfoHashes: Set<SecureHash> get() = nodeInfoFilesMap.values.map { it.nodeInfohash }.toSet()

    init {
        require(pollInterval >= 1.seconds) { "Poll interval must be 1 second or longer." }
        nodeInfosDir.createDirectories()
    }

    /**
     * Read all the files contained in [nodePath] / [NODE_INFO_DIRECTORY] and keep watching the folder for further updates.
     *
     * @return an [Observable] that emits lists of [NodeInfoUpdate]s. Each emitted list is a poll event of the folder and
     * may be empty if no changes were detected.
     */
    fun nodeInfoUpdates(): Observable<List<NodeInfoUpdate>> {
        return Observable.interval(0, pollInterval.toMillis(), TimeUnit.MILLISECONDS, scheduler).map { pollDirectory() }
    }

    private fun pollDirectory(): List<NodeInfoUpdate> {
        logger.debug { "pollDirectory $nodeInfosDir" }
        val processedPaths = HashSet<Path>()
        val result = nodeInfosDir.useDirectoryEntries { paths ->
            paths
                    .filter {
                        logger.debug { "Examining $it" }
                        true
                    }
                    .filter { !it.toString().endsWith(".tmp") }
                    .filter { it.isRegularFile() }
                    .filter { file ->
                        val lastModifiedTime = file.getLastModifiedTime()
                        val previousLastModifiedTime = nodeInfoFilesMap[file]?.lastModified
                        val newOrChangedFile = previousLastModifiedTime == null || lastModifiedTime > previousLastModifiedTime
                        processedPaths.add(file)
                        newOrChangedFile
                    }
                    .mapNotNull { file ->
                        logger.debug { "Reading SignedNodeInfo from $file" }
                        try {
                            val nodeInfoSigned = NodeInfoAndSigned(file.readObject())
                            nodeInfoFilesMap[file] = NodeInfoFromFile(nodeInfoSigned.signed.raw.hash, file.getLastModifiedTime())
                            nodeInfoSigned
                        } catch (e: Exception) {
                            logger.warn("Unable to read SignedNodeInfo from $file", e)
                            null
                        }
                    }
                    .toList()
        }
        val removedFiles = nodeInfoFilesMap.keys - processedPaths
        val removedHashes = removedFiles.map { file ->
            NodeInfoUpdate.Remove(nodeInfoFilesMap.remove(file)!!.nodeInfohash)
        }
        logger.debug { "Read ${result.size} NodeInfo files from $nodeInfosDir" }
        logger.debug { "Number of removed NodeInfo files ${removedHashes.size}" }
        return result.map { NodeInfoUpdate.Add(it.nodeInfo) } + removedHashes
    }
}
