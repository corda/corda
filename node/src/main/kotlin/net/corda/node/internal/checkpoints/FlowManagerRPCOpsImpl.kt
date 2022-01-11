@file:Suppress("DEPRECATION")
package net.corda.node.internal.checkpoints

import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.messaging.flows.FlowManagerRPCOps
import net.corda.node.services.rpc.CheckpointDumperImpl
import net.corda.core.internal.messaging.FlowManagerRPCOps as InternalFlowManagerRPCOps

/**
 * Implementation of [FlowManagerRPCOps]
 */
internal class FlowManagerRPCOpsImpl(private val checkpointDumper: CheckpointDumperImpl) : FlowManagerRPCOps, InternalFlowManagerRPCOps {

    override val protocolVersion: Int = PLATFORM_VERSION

    override fun dumpCheckpoints() = checkpointDumper.dumpCheckpoints()

    override fun debugCheckpoints() = checkpointDumper.debugCheckpoints()
}