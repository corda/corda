package net.corda.client.rpc

import net.corda.core.DoNotImplement
import net.corda.core.messaging.RPCOps
import java.io.Closeable

/**
 * Holds a [proxy] object implementing [I] that forwards requests to the RPC server. The server version can be queried
 * via this interface.
 *
 * [Closeable.close] may be used to shut down the connection and release associated resources. It is an
 * alias for [notifyServerAndClose].
 */
@DoNotImplement
interface RPCConnection<out I : RPCOps> : Closeable {
    /**
     * Holds a synthetic class that automatically forwards method calls to the server, and returns the response.
     */
    val proxy: I

    /** The RPC protocol version reported by the server. */
    val serverProtocolVersion: Int

    /**
     * Closes this client gracefully by sending a notification to the server, so it can immediately clean up resources.
     * If the server is not available this method may block for a short period until it's clear the server is not
     * coming back.
     */
    fun notifyServerAndClose()

    /**
     * Closes this client without notifying the server.
     *
     * The server will eventually clear out the RPC message queue and disconnect subscribed observers,
     * but this may take longer than desired, so to conserve resources you should normally use [notifyServerAndClose].
     * This method is helpful when the node may be shutting down or have already shut down and you don't want to
     * block waiting for it to come back, which typically happens in integration tests and demos rather than production.
     */
    fun forceClose()
}