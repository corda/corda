package net.corda.nodeapi.internal.zookeeper

import net.corda.core.utilities.contextLogger
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.imps.CuratorFrameworkState
import org.apache.curator.framework.recipes.leader.LeaderLatchListener
import org.apache.curator.retry.RetryForever
import org.apache.curator.retry.RetryNTimes
import org.apache.curator.utils.CloseableUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple Zookeeper client that offers priority based leader election.
 *
 * @param connectionString the connection string(i.e. localhost:1234) used to connect to the Zookeeper server/cluster
 * @param electionPath the zNode path used for the election process. Clients that compete for leader must use the same path
 * @param nodeId unique client identifier used in the creation of child zNodes
 * @param priority indicates the priority of the client in the election process. Low value means high priority(a client
 * with [priority] set to 0 will have become leader before a client with [priority] 1
 * @param retryInterval the interval in msec between retries of the Zookeeper connection. Default value is 500 msec.
 * @param retryCount the number of retries before giving up default value is 1. Use -1 to indicate forever.
 */
class ZkClient(connectionString: String,
               electionPath: String,
               val nodeId: String,
               val priority: Int,
               retryInterval: Int = 500,
               retryCount: Int = 1) : ZkLeader {

    private companion object {
        private val log = contextLogger()
    }

    private val client: CuratorFramework
    private val leaderLatch: PrioritizedLeaderLatch
    private val listeners = ConcurrentHashMap<CordaLeaderListener, LeaderLatchListener>()

    init {
        val retryPolicy = if (retryCount == -1) {
            RetryForever(retryInterval)
        } else {
            RetryNTimes(retryCount, retryInterval)
        }
        client = CuratorFrameworkFactory.newClient(connectionString, retryPolicy)
        leaderLatch = PrioritizedLeaderLatch(client, electionPath, nodeId, priority)
    }

    override fun start() {
        if (client.state != CuratorFrameworkState.STARTED) {
            log.info("Client $nodeId is starting.")
            client.start()
        }
    }

    override fun close() {
        log.info("Client $nodeId is stopping.")
        if (leaderLatch.state.get() != PrioritizedLeaderLatch.State.CLOSED)
            CloseableUtils.closeQuietly(leaderLatch)
        CloseableUtils.closeQuietly(client)
    }

    @Throws(IllegalStateException::class)
    override fun requestLeadership() {
        if (client.state != CuratorFrameworkState.STARTED)
            throw(IllegalStateException("Client $nodeId must be started before attempting to be leader."))

        if (leaderLatch.state.get() != PrioritizedLeaderLatch.State.STARTED) {
            log.info("Client $nodeId is attempting to become leader.")
            leaderLatch.start()
        }
    }

    override fun relinquishLeadership() {
        if (leaderLatch.hasLeadership()) {
            log.info("Client $nodeId is relinquishing leadership.")
            CloseableUtils.closeQuietly(leaderLatch)
        }
    }

    override fun isLeader(): Boolean{
        return leaderLatch.hasLeadership()
    }

    override fun isStarted(): Boolean {
        return client.state == CuratorFrameworkState.STARTED
    }

    override fun addLeadershipListener(listener: CordaLeaderListener) {
        val listenerStub = object : LeaderLatchListener {
            override fun notLeader() {
                listener.notLeader()
            }

            override fun isLeader() {
                listener.isLeader()
            }

        }
        listeners[listener] = listenerStub
        leaderLatch.addListener(listenerStub)
    }


    override fun removeLeadershipListener(listener: CordaLeaderListener) {
        val listenerStub = listeners.remove(listener)
        if (listenerStub != null) {
            leaderLatch.removeListener(listenerStub)
        }
    }
}