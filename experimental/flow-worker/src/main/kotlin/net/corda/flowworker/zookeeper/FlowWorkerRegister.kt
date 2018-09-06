package net.corda.flowworker.zookeeper

import net.corda.core.utilities.contextLogger
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.locks.LockInternals
import org.apache.curator.framework.recipes.locks.LockInternalsSorter
import org.apache.curator.framework.recipes.locks.StandardLockInternalsDriver
import org.apache.curator.utils.ZKPaths
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.Code.NONODE
import org.apache.zookeeper.KeeperException.Code.OK
import org.apache.zookeeper.Watcher
import org.apache.zookeeper.Watcher.Event.EventType.NodeChildrenChanged
import rx.subjects.BehaviorSubject

/**
 *  Use for registration with the zookeeper worker group and worker leader election.
 */
class FlowWorkerRegister(client: CuratorFramework,
                         private val path: String,
                         private val nodeId: String,
                         private val priority: Int) : AbstractZkLatch(client) {
    private companion object {
        private val logger = contextLogger()
        /** Used to split the zNode path and extract the priority value for sorting and comparison */
        private const val LOCK_NAME = "-latch-"
        private val workerSorter = LockInternalsSorter { str, lockName -> StandardLockInternalsDriver.standardFixForSorting(str, lockName) }
    }

    val registration: BehaviorSubject<FlowWorkerRegistration> = BehaviorSubject.create(FlowWorkerRegistration())

    /**
     * Leaves the election process, relinquishing leadership if acquired.
     * Cleans up all watchers and connection listener
     */
    @Throws(Exception::class)
    override fun initiateLatch(startedClient: CuratorFramework) {
        logger.info("$nodeId latch started for path $path.")
        reset(startedClient)
        val latchName = "$nodeId$LOCK_NAME${"%05d".format(priority)}" // Fixed width priority to ensure numeric sorting
        startedClient.create()
                .creatingParentContainersIfNeeded()
                .withProtection().withMode(CreateMode.EPHEMERAL)
                .inBackground { _, event ->
                    if (event.resultCode == OK.intValue()) {
                        startedClient.setNode(event.name)
                        if (!started) {
                            startedClient.setNode(null)
                        } else {
                            logger.info("$nodeId is joining election with node ${registration.value.myPath}")
                            startedClient.watchLeader()
                            startedClient.processCandidates()
                        }
                    } else {
                        logger.error("processCandidates() failed: " + event.resultCode)
                    }
                }.forPath(ZKPaths.makePath(path, latchName), nodeId.toByteArray(Charsets.UTF_8))
    }

    private fun CuratorFramework.watchLeader() {
        children.usingWatcher(Watcher {
            logger.info("Client $nodeId detected event ${it.type}.")
            if (started) {
                watchLeader()
                if (NodeChildrenChanged == it.type && registration.value.myPath != null) {
                    try {
                        logger.info("Change detected in children nodes of path $path. Checking candidates.")
                        processCandidates()
                    } catch (e: Exception) {
                        logger.error("An error occurred checking the leadership.", e)
                    }
                }
            }
        }).inBackground { _, event ->
            if (event.resultCode == NONODE.intValue()) {
                reset(this)
            }
        }.forPath(path)
    }

    private fun setLeadership(newValue: Boolean, workers: List<String> = emptyList()) {
        logger.info("Setting leadership to $newValue. Old value was ${registration.value.isLeader}.")
        registration.onNext(registration.value.copy(isLeader = newValue, workers = workers))
    }

    @Throws(Exception::class)
    private fun CuratorFramework.processCandidates() {
        children.inBackground { _, event ->
            if (event.resultCode == OK.intValue())
                checkLeadership(event.children)
        }.forPath(ZKPaths.makePath(path, null))
    }

    @Throws(Exception::class)
    private fun CuratorFramework.checkLeadership(children: List<String>) {
        val localOurPath = registration.value.myPath
        val sortedChildren = LockInternals.getSortedChildren(LOCK_NAME, workerSorter, children)
        val ownIndex = if (localOurPath != null) sortedChildren.indexOf(ZKPaths.getNodeFromPath(localOurPath)) else -1
        logger.debug("Election candidates are: $sortedChildren")
        when {
            ownIndex < 0 -> {
                logger.error("Can't find our zNode[$nodeId]. Resetting. Index: $ownIndex. My path is ${registration.value.myPath}")
                reset(this)
            }
            ownIndex == 0 -> setLeadership(true, sortedChildren)
            else -> setLeadership(false)
        }
    }

    @Throws(Exception::class)
    private fun CuratorFramework.setNode(newValue: String?) {
        val oldPath: String? = registration.value.myPath
        registration.onNext(registration.value.copy(myPath = newValue))
        if (oldPath != null) {
            logger.info("Deleting node $oldPath.")
            delete().guaranteed().inBackground().forPath(oldPath)
        }
    }

    override fun reset(startedClient: CuratorFramework) {
        setLeadership(false)
        startedClient.setNode(null)
    }

    override fun close() {
        super.close()
        registration.onCompleted()
    }
}

data class FlowWorkerRegistration(val myPath: String? = null, val isLeader: Boolean = false, val workers: List<String> = emptyList())
