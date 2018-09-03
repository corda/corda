package net.corda.demobench.model

import net.corda.nodeapi.internal.network.NodeInfoFilesCopier
import rx.Scheduler
import rx.schedulers.Schedulers
import tornadofx.*

/**
 * Utility class which copies nodeInfo files across a set of running nodes.
 *
 * This class will create paths that it needs to poll and to where it needs to copy files in case those
 * don't exist yet.
 */
class DemoBenchNodeInfoFilesCopier(scheduler: Scheduler = Schedulers.io()) : Controller() {

    private val nodeInfoFilesCopier = NodeInfoFilesCopier(scheduler)

    /**
     * @param nodeConfig the configuration to be added.
     * Add a [NodeConfig] for a node which is about to be started.
     * Its nodeInfo file will be copied to other nodes' additional-node-infos directory, and conversely,
     * other nodes' nodeInfo files will be copied to this node additional-node-infos directory.
     */
    fun addConfig(nodeConfig: NodeConfigWrapper): Unit = nodeInfoFilesCopier.addConfig(nodeConfig.nodeDir)

    /**
     * @param nodeConfig the configuration to be removed.
     * Remove the configuration of a node which is about to be stopped or already stopped.
     * No files written by that node will be copied to other nodes, nor files from other nodes will be copied to this
     * one.
     */
    fun removeConfig(nodeConfig: NodeConfigWrapper): Unit = nodeInfoFilesCopier.removeConfig(nodeConfig.nodeDir)

    fun reset(): Unit = nodeInfoFilesCopier.reset()
}