package net.corda.core.flows

import net.corda.core.internal.WriteOnceProperty
import java.nio.file.Path

object FlowStackSnapshotDefaults {
    var FLOW_STACK_SNAPSHOT_FACTORY: FlowStackSnapshotFactory by WriteOnceProperty(FlowStackSnapshotDefaultFactory())
}

interface FlowStackSnapshotFactory {
    fun getFlowStackSnapshot(flowClass: Class<*>): FlowStackSnapshot
    fun persistAsJsonFile(flowClass: Class<*>, baseDir: Path, flowId: String): Unit
}

private class FlowStackSnapshotDefaultFactory : FlowStackSnapshotFactory {

    override fun getFlowStackSnapshot(flowClass: Class<*>): FlowStackSnapshot {
        throw UnsupportedOperationException("Flow stack snapshot are not supposed to be used in a production deployment")
    }

    override fun persistAsJsonFile(flowClass: Class<*>, baseDir: Path, flowId: String) {
        throw UnsupportedOperationException("Flow stack snapshot are not supposed to be used in a production deployment")
    }
}

data class FlowStackSnapshot constructor(
        val timestamp: Long = System.currentTimeMillis(),
        val flowClass: Class<*>? = null,
        val stackFrames: List<Frame> = listOf()
) {
    data class Frame(
            val stackTraceElement: StackTraceElement? = null, // This should be the call that *pushed* the frame of [objects]
            val stackObjects: List<Any?> = listOf()
    )
}

data class StackFrameDataToken(val className: String)
