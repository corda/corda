package net.corda.core.internal.messaging

import net.corda.core.messaging.RPCOps

/**
 * RPC operations to perform operations related to flows including management of associated persistent states like checkpoints.
 */
@Deprecated(
    "A public version of this interface has been exposed that should be interacted with using the MultiRPCClient",
    ReplaceWith("net.corda.core.messaging.flows.FlowManagerRPCOps")
)
interface FlowManagerRPCOps : RPCOps {

    /**
     * Dump all the current flow checkpoints as JSON into a zip file in the node's log directory.
     */
    fun dumpCheckpoints()

    /**
     * Dump all the current flow checkpoints, alongside with the node's main jar, all CorDapps and driver jars into a zip file in the node's
     * log directory.
     */
    fun debugCheckpoints()
}