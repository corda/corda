/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.network

import net.corda.core.internal.*
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.NODE_INFO_DIRECTORY
import rx.Observable
import rx.Scheduler
import rx.Subscription
import rx.schedulers.Schedulers
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

/**
 * Utility class which copies nodeInfo files across a set of running nodes.
 *
 * This class will create paths that it needs to poll and to where it needs to copy files in case those
 * don't exist yet.
 */
class NodeInfoFilesCopier(scheduler: Scheduler = Schedulers.io()) : AutoCloseable {

    companion object {
        private val log = contextLogger()
        const val NODE_INFO_FILE_NAME_PREFIX = "nodeInfo-"
    }

    private val nodeDataMapBox = ThreadBox(mutableMapOf<Path, NodeData>())
    /**
     * Whether the NodeInfoFilesCopier is closed. When the NodeInfoFilesCopier is closed it will stop polling the
     * filesystem and all the public methods except [#close] will throw.
     */
    private var closed = false
    private val subscription: Subscription

    init {
        this.subscription = Observable.interval(5, TimeUnit.SECONDS, scheduler)
                .subscribe { poll() }
    }

    /**
     * @param nodeDir a path to be watched for NodeInfos
     * Add a path of a node which is about to be started.
     * Its nodeInfo file will be copied to other nodes' additional-node-infos directory, and conversely,
     * other nodes' nodeInfo files will be copied to this node additional-node-infos directory.
     */
    fun addConfig(nodeDir: Path) {
        require(!closed) { "NodeInfoFilesCopier is already closed" }
        nodeDataMapBox.locked {
            val newNodeFile = NodeData(nodeDir)
            put(nodeDir, newNodeFile)

            for (previouslySeenFile in allPreviouslySeenFiles()) {
                atomicCopy(previouslySeenFile, newNodeFile.additionalNodeInfoDirectory.resolve(previouslySeenFile.fileName))
            }
            log.info("Now watching: $nodeDir")
        }
    }

    /**
     * Remove the configuration of a node which is about to be stopped or already stopped.
     * No files written by that node will be copied to other nodes, nor files from other nodes will be copied to this
     * one.
     */
    fun removeConfig(nodeDir: Path) {
        require(!closed) { "NodeInfoFilesCopier is already closed" }
        nodeDataMapBox.locked {
            remove(nodeDir) ?: return
            log.info("Stopped watching: $nodeDir")
        }
    }

    fun reset() {
        require(!closed) { "NodeInfoFilesCopier is already closed" }
        nodeDataMapBox.locked {
            clear()
        }
    }

    /**
     * Stops polling the filesystem.
     * This function can be called as many times as one wants.
     */
    override fun close() {
        if (!closed) {
            closed = true
            subscription.unsubscribe()
        }
    }

    private fun allPreviouslySeenFiles() = nodeDataMapBox.alreadyLocked { values.flatMap { it.previouslySeenFiles.keys } }

    private fun poll() {
        nodeDataMapBox.locked {
            for (nodeData in values) {
                nodeData.nodeDir.list { paths ->
                    paths
                            .filter { it.isRegularFile() }
                            .filter { it.fileName.toString().startsWith(NODE_INFO_FILE_NAME_PREFIX) }
                            .forEach { processPath(nodeData, it) }
                }
            }
        }
    }

    // Takes a path under nodeData config dir and decides whether the file represented by that path needs to
    // be copied.
    private fun processPath(nodeData: NodeData, path: Path) {
        nodeDataMapBox.alreadyLocked {
            val newTimestamp = path.lastModifiedTime()
            val previousTimestamp = nodeData.previouslySeenFiles.put(path, newTimestamp) ?: FileTime.fromMillis(-1)
            if (newTimestamp > previousTimestamp) {
                for (destination in this.values.filter { it.nodeDir != nodeData.nodeDir }.map { it.additionalNodeInfoDirectory }) {
                    val fullDestinationPath = destination.resolve(path.fileName)
                    atomicCopy(path, fullDestinationPath)
                }
            }
        }
    }

    private fun atomicCopy(source: Path, destination: Path) {
        val tempDestination = try {
            Files.createTempFile(destination.parent, "", null)
        } catch (exception: IOException) {
            log.warn("Couldn't create a temporary file to copy $source", exception)
            throw exception
        }
        try {
            // First copy the file to a temporary file within the appropriate directory.
            source.copyTo(tempDestination, COPY_ATTRIBUTES, REPLACE_EXISTING)
        } catch (exception: IOException) {
            log.warn("Couldn't copy $source to $tempDestination.", exception)
            tempDestination.delete()
            throw exception
        }
        try {
            // Then rename it to the desired name. This way the file 'appears' on the filesystem as an atomic operation.
            tempDestination.moveTo(destination, REPLACE_EXISTING)
        } catch (exception: IOException) {
            log.warn("Couldn't move $tempDestination to $destination.", exception)
            tempDestination.delete()
            throw exception
        }
    }

    /**
     * Convenience holder for all the paths and files relative to a single node.
     */
    private class NodeData(val nodeDir: Path) {
        val additionalNodeInfoDirectory: Path = nodeDir.resolve(NODE_INFO_DIRECTORY)
        // Map from Path to its lastModifiedTime.
        val previouslySeenFiles = mutableMapOf<Path, FileTime>()

        init {
            additionalNodeInfoDirectory.createDirectories()
        }
    }
}
