package net.corda.nodeapi.internal.zookeeper

import com.google.common.base.Preconditions
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.BackgroundCallback
import org.apache.curator.framework.api.CuratorEvent
import org.apache.curator.framework.listen.StandardListenerManager
import org.apache.curator.framework.recipes.AfterConnectionEstablished
import org.apache.curator.framework.recipes.leader.LeaderLatchListener
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex
import org.apache.curator.framework.recipes.locks.LockInternals
import org.apache.curator.framework.recipes.locks.LockInternalsSorter
import org.apache.curator.framework.recipes.locks.StandardLockInternalsDriver
import org.apache.curator.framework.state.ConnectionState
import org.apache.curator.framework.state.ConnectionStateListener
import org.apache.curator.utils.ZKPaths
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.Watcher
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A modified version of the Apache Curator [org.apache.curator.framework.recipes.leader.LeaderLatch] recipe. It allows prioritized leader election.
 * Upon start, a zNode is created using the [nodeId] and [priority] arguments of the constructor.
 * If the creation is successful, a [Watcher] is created for the [path]. I will be triggered by
 * [Watcher.Event.EventType.NodeChildrenChanged] events which indicate that a candidate has left or joined the
 * election process. After receiving this event, the latch will check the candidates and their priorities to determine
 * if it is leader or not.
 *
 * Clients with the same priority are treated on a first-come first-served basis.
 *
 * Because [org.apache.zookeeper.ZooKeeper] cannot guarantee that path changes are reliably seen, a new watcher is immediately set when the
 * existing one is triggered.
 *
 * @param client the [CuratorFramework] instance used to manage the Zookeeper connection
 * @param path the path used to create zNodes for the election candidates
 * @param nodeId the unique identifier used to link a client to a zNode
 * @param priority an [Int] value that determines this client's priority in the election. The lower the value, the higher the priority
 */
internal class PrioritizedLeaderLatch(private val client: CuratorFramework,
                                      private val path: String,
                                      private val nodeId: String,
                                      private val priority: Int) : Closeable {

    val state = AtomicReference<State>(State.CLOSED)

    private val watchedClient = client.newWatcherRemoveCuratorFramework()
    private val leaderLock = LeaderLock(client, "$path$LOCK_PATH_NAME", nodeId)
    private val hasLeadership = AtomicBoolean(false)
    private val ourPath = AtomicReference<String>()
    private val startTask = AtomicReference<Future<*>>()

    private val listeners =  StandardListenerManager.standard<LeaderLatchListener>()
    private val connectionStateListener = ConnectionStateListener { _, newState -> handleStateChange(newState) }

    private companion object {
        private val log = contextLogger()
        /** Used to split the zNode path and extract the priority value for sorting and comparison */
        private const val LOCK_NAME = "-latch-"
        private const val LOCK_PATH_NAME = "-lock"
        private val sorter = LockInternalsSorter { str, lockName -> StandardLockInternalsDriver.standardFixForSorting(str, lockName) }
    }

    /**
     * Joins the election process
     */
    @Throws(Exception::class)
    fun start() {
        Preconditions.checkState(state.compareAndSet(State.CLOSED, State.STARTED),
                "Cannot be started more than once.")
        startTask.set(AfterConnectionEstablished.execute(watchedClient) {
            try {
                internalStart()
            } finally {
                startTask.set(null)
            }
        })
    }

    /**
     * Leaves the election process, relinquishing leadership if acquired.
     * Cleans up all watchers and connection listener
     */
    @Synchronized
    @Throws(IOException::class, IllegalStateException::class)
    override fun close() {
        Preconditions.checkState(state.compareAndSet(State.STARTED, State.CLOSED),
                "Already closed or has not been started.")
        cancelStartTask()
        try {
            watchedClient.removeWatchers()
            setLeadership(false)
            setNode(null)
        } catch (e: Exception) {
            throw IOException(e)
        } finally {
            watchedClient.connectionStateListenable.removeListener(connectionStateListener)
        }
    }

    /**
     * Adds an election listener that will remain until explicitly removed
     * @param listener a [LeaderLatchListener] instance
     */
    fun addListener(listener: LeaderLatchListener) {
        listeners.addListener(listener)
    }

    /**
     * Removes the listener passed as argument
     * @param listener a [LeaderLatchListener] instance
     */
    fun removeListener(listener: LeaderLatchListener) {
        listeners.removeListener(listener)
    }

    /**
     * @return `true` if leader, `false` otherwise
     */
    fun hasLeadership(): Boolean {
        return State.STARTED == state.get() && hasLeadership.get()
    }

    @Synchronized
    private fun internalStart() {
        if (State.STARTED == state.get()) {
            log.info("$nodeId latch started for path $path.")
            watchedClient.connectionStateListenable.addListener(connectionStateListener)
            try {
                reset()
            } catch (e: Exception) {
                log.error("An error occurred while resetting leadership for client $nodeId.", e)
            }
        }
    }

    private fun cancelStartTask(): Boolean {
        val localStartTask = startTask.getAndSet(null)

        if (localStartTask != null) {
            localStartTask.cancel(true)
            return true
        }

        return false
    }

    @Synchronized
    private fun handleStateChange(newState: ConnectionState?) {
        log.info("State change. New state: $newState")
        when (newState) {
            ConnectionState.RECONNECTED -> {
                try {
                    if (watchedClient.connectionStateErrorPolicy.isErrorState(ConnectionState.SUSPENDED) ||
                            !hasLeadership.get()) {
                        log.info("Client $nodeId reconnected. Resetting latch.")
                        reset()
                    }
                } catch (e: Exception) {
                    log.error("Could not reset leader latch for client $nodeId.", e)
                    setLeadership(false)
                }
            }

            ConnectionState.SUSPENDED -> {
                watchedClient.removeWatchers()
                if (watchedClient.connectionStateErrorPolicy.isErrorState(ConnectionState.SUSPENDED))
                    setLeadership(false)
            }

            ConnectionState.LOST ->  { setLeadership(false); watchedClient.removeWatchers() }

            else -> log.debug { "Ignoring state change $newState" }
        }
    }

    @Throws(Exception::class)
    private fun reset() {
        setLeadership(false)
        setNode(null)

        val joinElectionCallback = BackgroundCallback { _, event ->
            if (event.resultCode == KeeperException.Code.OK.intValue()) {
                setNode(event.name)
                if (State.CLOSED == state.get())
                    setNode(null)
                else {
                    log.info("$nodeId is joining election with node ${ourPath.get()}")
                    watchedClient.children.usingWatcher(ElectionWatcher(this)).inBackground(NoNodeCallback(this)).forPath(path)
                    processCandidates()
                }
            } else {
                log.error("processCandidates() failed: " + event.resultCode)
            }
        }

        val latchName = "$nodeId$LOCK_NAME${"%05d".format(priority)}" // Fixed width priority to ensure numeric sorting
        try {
            watchedClient.create()
                    .creatingParentContainersIfNeeded()
                    .withProtection().withMode(CreateMode.EPHEMERAL)
                    .inBackground(joinElectionCallback).forPath(ZKPaths.makePath(path, latchName), nodeId.toByteArray(Charsets.UTF_8))
        } catch (e: IllegalStateException) {
            log.warn("Trying to join election while client is being closed.")
        }
    }

    @Synchronized
    private fun setLeadership(newValue: Boolean) {
        val oldValue = hasLeadership.getAndSet(newValue)
        log.info("Client $nodeId: setting leadership to $newValue; old value was $oldValue.")
        if (oldValue && !newValue) {
            listeners.forEach { listener -> listener?.notLeader() }
            // Release lock after listeners have been notified
            try {
                if (leaderLock.isAcquiredInThisProcess) {
                    leaderLock.release()
                } else {
                    log.info("Lock is not held by this process. Cannot release.")
                }
            } catch (e: IllegalStateException) {
                log.warn("Client $nodeId: tried to release leader lock without owning it.")
            }
        } else if (!oldValue && newValue) {
            // Make sure we're the only leader before invoking listeners.
            leaderLock.asyncAcquire {
                listeners.forEach { listener ->
                    listener?.isLeader()
                }
            }
        }
    }

    @Throws(Exception::class)
    private fun processCandidates() {
        val callback = BackgroundCallback { _, event ->
            if (event.resultCode == KeeperException.Code.OK.intValue())
                checkLeadership(event.children)
        }

        watchedClient.children.inBackground(callback).forPath(ZKPaths.makePath(path, null))
    }

    @Throws(Exception::class)
    private fun checkLeadership(children: List<String>) {
        val localOurPath = ourPath.get()
        val sortedChildren = LockInternals.getSortedChildren(LOCK_NAME, sorter, children)
        val ownIndex = if (localOurPath != null) sortedChildren.indexOf(ZKPaths.getNodeFromPath(localOurPath)) else -1
        log.debug("Election candidates are: $sortedChildren")
        when {
            ownIndex < 0 -> {
                log.error("Can't find our zNode[$nodeId]. Resetting. Index: $ownIndex. My path is ${ourPath.get()}")
                reset()
            }

            ownIndex == 0 -> {
                if (!hasLeadership.get())
                    setLeadership(true)
            }

            else -> {
                if (hasLeadership.get()) {
                    setLeadership(false)
                }
            }
        }
    }

    @Throws(Exception::class)
    private fun setNode(newValue: String?) {
        val oldPath: String? = ourPath.getAndSet(newValue)
        if (oldPath != null) {
            log.info("Deleting node $oldPath.")
            watchedClient.delete().guaranteed().inBackground().forPath(oldPath)
        }
    }

    enum class State {
        STARTED,
        CLOSED
    }

    private class NoNodeCallback(private val latch: PrioritizedLeaderLatch) : BackgroundCallback {
        override fun processResult(client: CuratorFramework, event: CuratorEvent) {
            if (event.resultCode == KeeperException.Code.NONODE.intValue())
                if (event.resultCode == KeeperException.Code.NONODE.intValue())
                    latch.reset()
        }
    }

    private class ElectionWatcher(private val latch: PrioritizedLeaderLatch) : Watcher {
        override fun process(event: WatchedEvent) {
            log.info("Client ${latch.nodeId} detected event ${event.type}.")
            if (State.STARTED == latch.state.get()) {
                latch.watchedClient.children.usingWatcher(ElectionWatcher(latch)).inBackground(NoNodeCallback(latch)).forPath(latch.path)
                if (Watcher.Event.EventType.NodeChildrenChanged == event.type && latch.ourPath.get() != null) {
                    try {
                        log.info("Client ${latch.nodeId}: change detected in children nodes of path ${latch.path}; checking candidates.")
                        latch.processCandidates()
                    } catch (e: Exception) {
                        log.error("Client ${latch.nodeId}: an error occurred checking the leadership.", e)
                    }
                }
            }
        }
    }

    /**
     * A wrapper class to make sure we handle the leader lock with the same thread
     */
    private class LeaderLock(client: CuratorFramework, path: String, private val nodeId: String) {
        private val leaderLock = InterProcessSemaphoreMutex(client, path)
        private val lockHandlingExecutor = Executors.newSingleThreadExecutor()
        val isAcquiredInThisProcess get() = leaderLock.isAcquiredInThisProcess

        fun release() {
            lockHandlingExecutor.submit {
                log.info("Client $nodeId: Releasing leader lock...")
                leaderLock.release()
                log.info("Client $nodeId: Leader lock released.")
            }.get()
        }

        fun asyncAcquire(onComplete: () -> Unit) {
            lockHandlingExecutor.submit {
                try {
                    log.info("Client $nodeId: Acquiring leader lock.")
                    if (!isAcquiredInThisProcess) {
                       leaderLock.acquire()
                    } else {
                        log.info("Client $nodeId: Lock already acquired.")
                    }
                    log.info("Client $nodeId: Leader lock acquired.")
                } catch (e: IOException) {
                    log.warn("Client $nodeId: Client closed while trying to acquire leader lock.")
                } catch (e: IllegalStateException) {
                    log.warn("Client $nodeId: Client tried to acquire leader lock while closing.")
                }
                onComplete()
            }
        }
    }
}