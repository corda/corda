package net.corda.nodeapi.internal.zookeeper

import net.corda.core.utilities.contextLogger
import org.apache.curator.RetryPolicy
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.imps.CuratorFrameworkState
import org.apache.curator.framework.recipes.leader.LeaderLatchListener
import org.apache.curator.retry.RetryOneTime
import org.apache.curator.utils.CloseableUtils

/**
 * Simple Zookeeper client that offers priority based leader election.
 *
 * @param connectionString the connection string(i.e. localhost:1234) used to connect to the Zookeeper server/cluster
 * @param electionPath the zNode path used for the election process. Clients that compete for leader must use the same path
 * @param nodeId unique client identifier used in the creation of child zNodes
 * @param priority indicates the priority of the client in the election process. Low value means high priority(a client
 * with [priority] set to 0 will have become leader before a client with [priority] 1
 * @param retryPolicy is an instance of [RetryPolicy] and indicates the process in case connection to Zookeeper server/cluster
 * is lost. If no policy is supplied, [RetryOneTime] will be used with 500ms before attempting to reconnect
 */
class ZkClient(connectionString: String,
               electionPath: String,
               val nodeId: String,
               val priority: Int,
               retryPolicy: RetryPolicy = RetryOneTime(500)) : ZkLeader {

    private companion object {
        private val log = contextLogger()
    }

    private val client = CuratorFrameworkFactory.newClient(connectionString, retryPolicy)
    private val leaderLatch = PrioritizedLeaderLatch(client, electionPath, nodeId, priority)

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

    override fun addLeadershipListener(listener: LeaderLatchListener) {
        leaderLatch.addListener(listener)
    }


    override fun removeLeaderShipListener(listener: LeaderLatchListener) {
        leaderLatch.removeListener(listener)
    }
}