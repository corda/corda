package net.corda.client.rpc

import net.corda.client.rpc.internal.KryoClientSerializationScheme
import net.corda.client.rpc.internal.RPCClient
import net.corda.client.rpc.internal.RPCClientConfiguration
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.ArtemisTcpTransport.Companion.tcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.internal.serialization.KRYO_RPC_CLIENT_CONTEXT
import java.time.Duration

/** @see RPCClient.RPCConnection */
class CordaRPCConnection internal constructor(
        connection: RPCClient.RPCConnection<CordaRPCOps>
) : RPCClient.RPCConnection<CordaRPCOps> by connection

/** @see RPCClientConfiguration */
data class CordaRPCClientConfiguration(
        val connectionMaxRetryInterval: Duration
) {
    internal fun toRpcClientConfiguration(): RPCClientConfiguration {
        return RPCClientConfiguration.default.copy(
                connectionMaxRetryInterval = connectionMaxRetryInterval
        )
    }
    companion object {
        @JvmStatic
        val default = CordaRPCClientConfiguration(
                connectionMaxRetryInterval = RPCClientConfiguration.default.connectionMaxRetryInterval
        )
    }
}

/** @see RPCClient */
//TODO Add SSL support
class CordaRPCClient(
        hostAndPort: NetworkHostAndPort,
        configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.default,
        initialiseSerialization: Boolean = true
) {
    init {
        // Init serialization.  It's plausible there are multiple clients in a single JVM, so be tolerant of
        // others having registered first.
        // TODO: allow clients to have serialization factory etc injected and align with RPC protocol version?
        if (initialiseSerialization) {
            KryoClientSerializationScheme.initialiseSerialization()
        }
    }

    private val rpcClient = RPCClient<CordaRPCOps>(
            tcpTransport(ConnectionDirection.Outbound(), hostAndPort, config = null),
            configuration.toRpcClientConfiguration(),
            KRYO_RPC_CLIENT_CONTEXT
    )

    fun start(username: String, password: String): CordaRPCConnection {
        return CordaRPCConnection(rpcClient.start(CordaRPCOps::class.java, username, password))
    }

    inline fun <A> use(username: String, password: String, block: (CordaRPCConnection) -> A): A {
        return start(username, password).use(block)
    }
}