package net.corda.flowworker.zookeeper

import net.corda.core.utilities.contextLogger
import org.apache.curator.utils.CloseableUtils

class FlowWorkerZkClient(connectionString: String,
                         electionPath: String,
                         partitionPath: String,
                         private val nodeId: String,
                         priority: Int,
                         retryInterval: Int = 500,
                         retryCount: Int = 1) : AbstractZkClient(connectionString, retryInterval, retryCount) {

    private companion object {
        private val logger = contextLogger()
    }

    private val leaderLatch = FlowWorkerRegister(client, electionPath, nodeId, priority)
    private val partitioner = FlowWorkerPartitioner(client, partitionPath, leaderLatch)

    val registration = leaderLatch.registration
    val partition = partitioner.partition

    override fun startInternal() {
        logger.info("Client $nodeId is starting.")
        partitioner.start()
        logger.info("Client $nodeId is attempting to become leader.")
        leaderLatch.start()
    }

    override fun close() {
        logger.info("Client $nodeId is stopping.")
        CloseableUtils.closeQuietly(leaderLatch)
        CloseableUtils.closeQuietly(partitioner)
        super.close()
    }

    fun isLeader(): Boolean {
        return registration.value.isLeader
    }
}