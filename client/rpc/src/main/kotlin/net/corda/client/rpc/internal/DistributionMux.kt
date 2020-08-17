package net.corda.client.rpc.internal

import net.corda.client.rpc.RPCConnection
import net.corda.client.rpc.ext.RPCConnectionListener
import net.corda.core.messaging.RPCOps
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug

/**
 * Internal helper class for distributing connectivity events to multiple [RPCConnectionListener]s.
 * It retains some state to simplify construction of [RPCConnectionListener.ConnectionContext].
 */
internal class DistributionMux<I : RPCOps>(private val listeners: Iterable<RPCConnectionListener<I>>, private val userName: String) {

    companion object {
        private val logger = contextLogger()

        private data class ConnectionContextImpl<I : RPCOps>(override val userName: String,
                                                             override val connectionOpt: RPCConnection<I>?,
                                                             override val throwableOpt: Throwable? = null) : RPCConnectionListener.ConnectionContext<I>
    }

    @Volatile
    internal var connectionOpt: RPCConnection<I>? = null

    internal fun onConnect() {
        safeForEachListener {
            onConnect(ConnectionContextImpl(userName, connectionOpt))
        }
    }

    internal fun onDisconnect(throwableOpt: Throwable?) {
        if (connectionOpt != null) {
            safeForEachListener {
                onDisconnect(ConnectionContextImpl(userName, connectionOpt, throwableOpt))
            }
        } else {
            logger.debug { "Not distributing onDisconnect as connection never been established" }
        }
    }

    internal fun onPermanentFailure(throwableOpt: Throwable?) {
        safeForEachListener {
            onPermanentFailure(ConnectionContextImpl(userName, connectionOpt, throwableOpt))
        }
    }

    private fun safeForEachListener(action: RPCConnectionListener<I>.() -> Unit) {
        listeners.forEach {
            try {
                it.action()
            } catch (ex: Exception) {
                logger.error("Exception during distribution to: $it", ex)
            }
        }
    }
}