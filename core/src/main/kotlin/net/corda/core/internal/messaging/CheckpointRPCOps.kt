package net.corda.core.internal.messaging

import net.corda.core.messaging.RPCOps

/**
 * RPC operations to obtain information about checkpoints of flows execution.
 */
interface CheckpointRPCOps : RPCOps {
    /**
     * Dump all the current flow checkpoints as JSON into a zip file in the node's log directory.
     */
    fun dumpCheckpoints()
}