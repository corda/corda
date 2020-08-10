package net.corda.node.internal.checkpoints

import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.messaging.CheckpointRPCOps
import net.corda.node.services.rpc.CheckpointDumperImpl

/**
 * Implementation of [CheckpointRPCOps]
 */
internal class CheckpointRPCOpsImpl(private val checkpointDumper: CheckpointDumperImpl) : CheckpointRPCOps {

    override val protocolVersion: Int = PLATFORM_VERSION

    override fun dumpCheckpoints() = checkpointDumper.dumpCheckpoints()
}