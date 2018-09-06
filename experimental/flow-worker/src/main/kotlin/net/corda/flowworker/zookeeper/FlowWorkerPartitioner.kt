package net.corda.flowworker.zookeeper

import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.utils.ZKPaths
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.Watcher
import org.apache.zookeeper.Watcher.Event.EventType.NodeChildrenChanged
import org.apache.zookeeper.Watcher.Event.EventType.NodeDeleted
import rx.subjects.BehaviorSubject

class FlowWorkerPartitioner(client: CuratorFramework,
                            private val partitionPath: String,
                            private val leaderLatch: FlowWorkerRegister) : AbstractZkLatch(client) {


    companion object {
        private val logger = contextLogger()
    }

    val partition: BehaviorSubject<Pair<Long, Long>?> = BehaviorSubject.create()

    override fun initiateLatch(startedClient: CuratorFramework) {
        // Make sure the partition path exists before watching it.
        startedClient.createContainers(ZKPaths.makePath(partitionPath, null))
        // Listen to partition definition from leader
        watchFlowPartition(startedClient)
        // Leader will create the partitions after elected.
        leaderLatch.registration.subscribe { registration ->
            if (registration.isLeader && started) {
                val bucketSize = Long.MAX_VALUE / registration.workers.size
                val upperBound = registration.workers.mapIndexed { index, _ ->
                    bucketSize * (index + 1)
                }.dropLast(1) + Long.MAX_VALUE // Make sure upper bound end with the MAX value.
                val lowerBound = listOf(0L) + upperBound.dropLast(1).map { it + 1 } // Make sure lower bound start from 0
                val partitions = registration.workers.zip(lowerBound.zip(upperBound)).toMap()
                startedClient.create()
                        .creatingParentContainersIfNeeded()
                        .withProtection()
                        .withMode(CreateMode.EPHEMERAL)
                        .inBackground { _, e ->
                            logger.info("Partition info created in path ${e.path}")
                        }.forPath(ZKPaths.makePath(partitionPath, "partition"), partitions.serialize().bytes)
            }
        }
    }

    override fun reset(startedClient: CuratorFramework) {
        partition.onNext(null)
    }

    private fun watchFlowPartition(watchedClient: CuratorFramework) {
        watchedClient.children.usingWatcher(Watcher {
            if (started) {
                watchFlowPartition(watchedClient)
                when (it.type) {
                    NodeChildrenChanged -> {
                        watchedClient.children.inBackground { _, event ->
                            // TODO: Determine which is the latest, could happen if partition from last leader didn't get deleted.
                            if (event.children.isNotEmpty()) {
                                watchedClient.data.inBackground { _, e ->
                                    val partitions = e.data.deserialize<Map<String, Pair<Long, Long>>>()
                                    partition.onNext(partitions[leaderLatch.registration.value.myPath?.split("/")?.last()])
                                    logger.info("My partition is ${partition.value}")
                                }.forPath(ZKPaths.makePath(partitionPath, event.children.last()))
                            } else {
                                // No partition data i.e leader is down, set partition to null
                                reset(watchedClient)
                            }
                        }.forPath(it.path)
                    }
                    NodeDeleted -> reset(watchedClient)
                    else -> throw IllegalArgumentException("Unsupported event type ${it.type}.")
                }
            }
        }).forPath(ZKPaths.makePath(partitionPath, null))
    }

    override fun close() {
        super.close()
        partition.onCompleted()
    }
}