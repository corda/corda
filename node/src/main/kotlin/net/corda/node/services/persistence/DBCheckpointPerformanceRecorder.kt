package net.corda.node.services.persistence

import com.codahale.metrics.Gauge
import com.codahale.metrics.Histogram
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Reservoir
import com.codahale.metrics.SlidingTimeWindowArrayReservoir
import com.codahale.metrics.SlidingTimeWindowReservoir
import net.corda.core.serialization.SerializedBytes
import net.corda.node.services.statemachine.CheckpointState
import net.corda.node.services.statemachine.FlowState
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

interface CheckpointPerformanceRecorder {

    /**
     * Record performance metrics regarding the serialized size of [CheckpointState] and [FlowState]
     */
    fun record(serializedCheckpointState: SerializedBytes<CheckpointState>, serializedFlowState: SerializedBytes<FlowState>?)
}

class DBCheckpointPerformanceRecorder(metrics: MetricRegistry) : CheckpointPerformanceRecorder {

    private val checkpointingMeter = metrics.meter("Flows.Checkpointing Rate")
    private val checkpointSizesThisSecond = SlidingTimeWindowReservoir(1, TimeUnit.SECONDS)
    private val lastBandwidthUpdate = AtomicLong(0)
    private val checkpointBandwidthHist = metrics.register(
        "Flows.CheckpointVolumeBytesPerSecondHist", Histogram(
            SlidingTimeWindowArrayReservoir(1, TimeUnit.DAYS)
        )
    )
    private val checkpointBandwidth = metrics.register(
        "Flows.CheckpointVolumeBytesPerSecondCurrent",
        LatchedGauge(checkpointSizesThisSecond)
    )

    /**
     * This [Gauge] just reports the sum of the bytes checkpointed during the last second.
     */
    private class LatchedGauge(private val reservoir: Reservoir) : Gauge<Long> {
        override fun getValue(): Long {
            return reservoir.snapshot.values.sum()
        }
    }

    override fun record(serializedCheckpointState: SerializedBytes<CheckpointState>, serializedFlowState: SerializedBytes<FlowState>?) {
        /* For now we don't record states where the serializedFlowState is null and thus the checkpoint is in a completed state.
           As this will skew the mean with lots of small checkpoints. For the moment we only measure runnable checkpoints. */
        serializedFlowState?.let {
            updateData(serializedCheckpointState.size.toLong() + it.size.toLong())
        }
    }

    private fun updateData(totalSize: Long) {
        checkpointingMeter.mark()
        checkpointSizesThisSecond.update(totalSize)
        var lastUpdateTime = lastBandwidthUpdate.get()
        while (System.nanoTime() - lastUpdateTime > TimeUnit.SECONDS.toNanos(1)) {
            if (lastBandwidthUpdate.compareAndSet(lastUpdateTime, System.nanoTime())) {
                val checkpointVolume = checkpointSizesThisSecond.snapshot.values.sum()
                checkpointBandwidthHist.update(checkpointVolume)
            }
            lastUpdateTime = lastBandwidthUpdate.get()
        }
    }
}
