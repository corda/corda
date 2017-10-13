package net.corda.demobench.model

import net.corda.cordform.CordformNode
import net.corda.core.internal.createDirectories
import net.corda.core.internal.isRegularFile
import net.corda.core.internal.list
import rx.Observable
import rx.Scheduler
import rx.schedulers.Schedulers
import tornadofx.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * Utility class which copies nodeInfo files across a set of running nodes.
 *
 * This class will create paths that it needs to poll and to where it needs to copy files in case those
 * don't exist yet.
 */
class NodeInfoFilesCopier(scheduler: Scheduler = Schedulers.io()): Controller() {

    private val nodeDataMap = mutableMapOf<Path, NodeData>()

    init {
        Observable.interval(5, TimeUnit.SECONDS, scheduler)
                .subscribe { poll() }
    }

    /**
     * @param nodeConfig the configuration to be added.
     * Add a [NodeConfig] for a node which is about to be started.
     * Its nodeInfo file will be copied to other nodes' additional-node-infos directory, and conversely,
     * other nodes' nodeInfo files will be copied to this node additional-node-infos directory.
     */
    @Synchronized
    fun addConfig(nodeConfig: NodeConfigWrapper) {
        val newNodeFile = NodeData(nodeConfig.nodeDir)
        nodeDataMap[nodeConfig.nodeDir] = newNodeFile

        for (previouslySeenFile in allPreviouslySeenFiles()) {
            copy(previouslySeenFile, newNodeFile.destination.resolve(previouslySeenFile.fileName))
        }
        log.info("Now watching: ${nodeConfig.nodeDir}")
    }

    /**
     * @param nodeConfig the configuration to be removed.
     * Remove the configuration of a node which is about to be stopped or already stopped.
     * No files written by that node will be copied to other nodes, nor files from other nodes will be copied to this
     * one.
     */
    @Synchronized
    fun removeConfig(nodeConfig: NodeConfigWrapper) {
        nodeDataMap.remove(nodeConfig.nodeDir) ?: return
        log.info("Stopped watching: ${nodeConfig.nodeDir}")
    }

    @Synchronized
    fun reset() {
        nodeDataMap.clear()
    }

    private fun allPreviouslySeenFiles() = nodeDataMap.values.map { it.previouslySeenFiles.keys }.flatten()

    @Synchronized
    private fun poll() {
        for (nodeData in nodeDataMap.values) {
            nodeData.nodeDir.list { paths ->
                paths.filter { it.isRegularFile() }
                        .filter { it.fileName.toString().startsWith("nodeInfo-") }
                        .forEach { path -> processPath(nodeData, path) }
            }
        }
    }

    // Takes a path under nodeData config dir and decides whether the file represented by that path needs to
    // be copied.
    private fun processPath(nodeData: NodeData, path: Path) {
        val newTimestamp = Files.readAttributes(path, BasicFileAttributes::class.java).lastModifiedTime()
        val previousTimestamp = nodeData.previouslySeenFiles.put(path, newTimestamp) ?: FileTime.fromMillis(-1)
        if (newTimestamp > previousTimestamp) {
            for (destination in nodeDataMap.values.filter { it.nodeDir != nodeData.nodeDir }.map { it.destination }) {
                val fullDestinationPath = destination.resolve(path.fileName)
                copy(path, fullDestinationPath)
            }
        }
    }

    private fun copy(source: Path, destination: Path) {
        val tempDestination = try {
            Files.createTempFile(destination.parent, ".", null)
        } catch (exception: IOException) {
            log.log(Level.WARNING, "Couldn't create a temporary file to copy $source", exception)
            throw exception
        }
        try {
            // First copy the file to a temporary file within the appropriate directory.
            Files.copy(source, tempDestination, COPY_ATTRIBUTES, REPLACE_EXISTING)
        } catch (exception: IOException) {
            log.log(Level.WARNING, "Couldn't copy $source to $tempDestination.", exception)
            Files.delete(tempDestination)
            throw exception
        }
        try {
            // Then rename it to the desired name. This way the file 'appears' on the filesystem as an atomic operation.
            Files.move(tempDestination, destination, REPLACE_EXISTING)
        } catch (exception: IOException) {
            log.log(Level.WARNING, "Couldn't move $tempDestination to $destination.", exception)
            Files.delete(tempDestination)
            throw exception
        }
    }

    /**
     * Convenience holder for all the paths and files relative to a single node.
     */
    private class NodeData(val nodeDir: Path) {
        val destination: Path = nodeDir.resolve(CordformNode.NODE_INFO_DIRECTORY)
        // Map from Path to its lastModifiedTime.
        val previouslySeenFiles = mutableMapOf<Path, FileTime>()

        init {
            destination.createDirectories()
        }
    }
}
