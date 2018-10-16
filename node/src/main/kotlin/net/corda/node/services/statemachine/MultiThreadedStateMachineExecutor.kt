package net.corda.node.services.statemachine

import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.netty.util.concurrent.FastThreadLocalThread
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class MultiThreadedStateMachineExecutor(metricRegistry: MetricRegistry,
                                        poolSize: Int) : ThreadPoolExecutor(poolSize, poolSize,
        0L, TimeUnit.MILLISECONDS,
        PriorityBlockingQueue<Runnable>(poolSize * 4, FlowStateMachineComparator()),
        ThreadFactoryBuilder().setNameFormat("flow-worker").setThreadFactory(::FastThreadLocalThread).build()) {

    private val stateMachineQueueSizeOnInsert = metricRegistry.histogram("Flows.QueueSizeOnInsert")

    init {
        metricRegistry.register("Flows.QueueSize", Gauge<Int> {
            queue.size
        })
    }
    override fun execute(command: Runnable?) {
        stateMachineQueueSizeOnInsert.update(queue.size)
        super.execute(command)
    }
}