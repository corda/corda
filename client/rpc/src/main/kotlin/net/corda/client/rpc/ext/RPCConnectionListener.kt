package net.corda.client.rpc.ext

import net.corda.client.rpc.RPCConnection
import net.corda.core.messaging.RPCOps

/**
 * A listener that can be attached to [MultiRPCClient] to be notified about important RPC connectivity events.
 */
interface RPCConnectionListener<I : RPCOps> {

    /**
     * Defines context information for events distributed.
     */
    interface ConnectionContext<I : RPCOps> {
        val userName: String
        val connectionOpt: RPCConnection<I>?
        val throwableOpt: Throwable?
    }

    /**
     * This method will be called to inform that RPC connection is established. [ConnectionContext.connectionOpt] will not be `null`.
     *
     * If connection is lost RPC client will attempt to re-connect and if this is successful then this method will be called
     * again with the **same**  reference of [ConnectionContext.connectionOpt] as during initial connect. I.e. it is possible to say that once
     * established [ConnectionContext.connectionOpt] stays constant during [onConnect]/[onDisconnect] cycles.
     */
    fun onConnect(context: ConnectionContext<I>)

    /**
     * This method will be called to inform about connection loss. Since given RPC client may produce multiple [RPCConnection]s,
     * [ConnectionContext.connectionOpt] will specify which connection is interrupted.
     */
    fun onDisconnect(context: ConnectionContext<I>)

    /**
     * This is a terminal notification to inform that:
     * - it has never been possible to connect due to incorrect credentials or endpoints addresses supplied. In this case
     * [ConnectionContext.connectionOpt] will be `null`;
     * or
     * - no further reconnection will be performed as maximum number of attempts has been reached. In this case
     * [ConnectionContext.connectionOpt] may not be `null`.
     */
    fun onPermanentFailure(context: ConnectionContext<I>)
}