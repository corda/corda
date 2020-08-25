package net.corda.node.internal.checkpoints

import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.messaging.FlowManagerRPCOps
import net.corda.node.services.rpc.CheckpointDumperImpl

/**
 * Implementation of [FlowManagerRPCOps]
 */
internal class FlowManagerRPCOpsImpl(private val checkpointDumper: CheckpointDumperImpl) : FlowManagerRPCOps {

    override val protocolVersion: Int = PLATFORM_VERSION

    override fun dumpCheckpoints() = checkpointDumper.dumpCheckpoints()

    override fun debugCheckpoints() = checkpointDumper.debugCheckpoints()
}