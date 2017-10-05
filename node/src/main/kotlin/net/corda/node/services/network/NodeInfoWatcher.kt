package net.corda.node.services.network

import net.corda.cordform.CordformNode
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.internal.*
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.loggerFor
import rx.Observable
import rx.Scheduler
import rx.schedulers.Schedulers
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit
import kotlin.streams.toList

/**
 * Class containing the logic to
 * - Serialize and de-serialize a [NodeInfo] to disk and reading it back.
 * - Poll a directory for new serialized [NodeInfo]
 *
 * @param path the base path of a node.
 * @param scheduler a [Scheduler] for the rx [Observable] returned by [nodeInfoUpdates], this is mainly useful for
 *        testing. It defaults to the io scheduler which is the appropriate value for production uses.
 */
class NodeInfoWatcher(private val nodePath: Path,
                      private val scheduler: Scheduler = Schedulers.io()) {

    private val nodeInfoDirectory = nodePath / CordformNode.NODE_INFO_DIRECTORY
    private val watchService : WatchService? by lazy { initWatch() }

    companion object {
        private val logger = loggerFor<NodeInfoWatcher>()

        /**
         * Saves the given [NodeInfo] to a path.
         * The node is 'encoded' as a SignedData<NodeInfo>, signed with the owning key of its first identity.
         * The name of the written file will be "nodeInfo-" followed by the hash of the content. The hash in the filename
         * is used so that one can freely copy these files without fearing to overwrite another one.
         *
         * @param path the path where to write the file, if non-existent it will be created.
         * @param nodeInfo the NodeInfo to serialize.
         * @param keyManager a KeyManagementService used to sign the NodeInfo data.
         */
        fun saveToFile(path: Path, nodeInfo: NodeInfo, keyManager: KeyManagementService) {
            try {
                path.createDirectories()
                val serializedBytes = nodeInfo.serialize()
                val regSig = keyManager.sign(serializedBytes.bytes,
                        nodeInfo.legalIdentities.first().owningKey)
                val signedData = SignedData(serializedBytes, regSig)
                val file = (path / ("nodeInfo-" + SecureHash.sha256(serializedBytes.bytes).toString())).toFile()
                file.writeBytes(signedData.serialize().bytes)
            } catch (e: Exception) {
                logger.warn("Couldn't write node info to file", e)
            }
        }
    }

    /**
     * Read all the files contained in [nodePath] / [CordformNode.NODE_INFO_DIRECTORY] and keep watching
     * the folder for further updates.
     *
     * @return an [Observable] returning [NodeInfo]s, there is no guarantee that the same value isn't returned more
     *      than once.
     */
    fun nodeInfoUpdates(): Observable<NodeInfo> {
        val pollForFiles = Observable.interval(5, TimeUnit.SECONDS, scheduler)
                .flatMapIterable { pollWatch() }
        val readCurrentFiles = Observable.from(loadFromDirectory())
        return readCurrentFiles.mergeWith(pollForFiles)
    }

    /**
     * Loads all the files contained in a given path and returns the deserialized [NodeInfo]s.
     * Signatures are checked before returning a value.
     *
     * @return a list of [NodeInfo]s
     */
    private fun loadFromDirectory(): List<NodeInfo> {
        val nodeInfoDirectory = nodePath / CordformNode.NODE_INFO_DIRECTORY
        if (!nodeInfoDirectory.isDirectory()) {
            logger.info("$nodeInfoDirectory isn't a Directory, not loading NodeInfo from files")
            return emptyList()
        }
        val result = nodeInfoDirectory.list { paths ->
            paths.filter { it.isRegularFile() }
                    .map { processFile(it) }
                    .toList()
                    .filterNotNull()
        }
        logger.info("Successfully read ${result.size} NodeInfo files.")
        return result
    }

    // Polls the watchService for changes to nodeInfoDirectory, return all the newly read NodeInfos.
    private fun pollWatch(): List<NodeInfo> {
        if (watchService == null) {
            return emptyList()
        }
        val watchKey: WatchKey = watchService?.poll() ?: return emptyList()
        val files = mutableSetOf<Path>()
        for (event in watchKey.pollEvents()) {
            val kind = event.kind()
            if (kind == StandardWatchEventKinds.OVERFLOW) continue

            val ev: WatchEvent<Path> = uncheckedCast(event)
            val filename = ev.context()
            val absolutePath = nodeInfoDirectory.resolve(filename)
            if (absolutePath.isRegularFile()) {
                files.add(absolutePath)
            }
        }
        val valid = watchKey.reset()
        if (!valid) {
            logger.warn("Can't poll $nodeInfoDirectory anymore, it was probably deleted.")
        }
        return files.mapNotNull { processFile(it) }
    }

    private fun processFile(file: Path) : NodeInfo? {
        try {
            logger.info("Reading NodeInfo from file: $file")
            val signedData = file.readAll().deserialize<SignedData<NodeInfo>>()
            return signedData.verified()
        } catch (e: Exception) {
            logger.warn("Exception parsing NodeInfo from file. $file", e)
            return null
        }
    }

    // Create a WatchService watching for changes in nodeInfoDirectory.
    private fun initWatch() : WatchService? {
        if (!nodeInfoDirectory.isDirectory()) {
            logger.warn("Not watching folder $nodeInfoDirectory it doesn't exist or it's not a directory")
            return null
        }
        val watchService = nodeInfoDirectory.fileSystem.newWatchService()
        nodeInfoDirectory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY)
        logger.info("Watching $nodeInfoDirectory for new files")
        return watchService
    }
}
