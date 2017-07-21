package net.corda.client.rpc

import net.corda.client.rpc.internal.RPCClient
import net.corda.client.rpc.internal.RPCClientConfiguration
import net.corda.client.rpc.serialization.KryoClientSerializationScheme
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.ArtemisTcpTransport.Companion.tcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.config.SSLConfiguration
import net.corda.nodeapi.serialization.KRYO_P2P_CONTEXT
import net.corda.nodeapi.serialization.KRYO_RPC_CLIENT_CONTEXT
import net.corda.nodeapi.serialization.SerializationFactoryImpl
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
class CordaRPCClient(
        hostAndPort: NetworkHostAndPort,
        sslConfiguration: SSLConfiguration? = null,
        configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.default,
        initialiseSerialization: Boolean = true
) {
    init {
        // Init serialization.  It's plausible there are multiple clients in a single JVM, so be tolerant of
        // others having registered first.
        // TODO: allow clients to have serialization factory etc injected and align with RPC protocol version?
        if (initialiseSerialization) {
            initialiseSerialization()
        }
    }

    private val rpcClient = RPCClient<CordaRPCOps>(
            tcpTransport(ConnectionDirection.Outbound(), hostAndPort, sslConfiguration),
            configuration.toRpcClientConfiguration(),
            KRYO_RPC_CLIENT_CONTEXT
    )

    fun start(username: String, password: String): CordaRPCConnection {
        return CordaRPCConnection(rpcClient.start(CordaRPCOps::class.java, username, password))
    }

    inline fun <A> use(username: String, password: String, block: (CordaRPCConnection) -> A): A {
        return start(username, password).use(block)
    }

    companion object {
        fun initialiseSerialization() {
            try {
                SerializationDefaults.SERIALIZATION_FACTORY = SerializationFactoryImpl().apply {
                    registerScheme(KryoClientSerializationScheme())
                }
                SerializationDefaults.P2P_CONTEXT = KRYO_P2P_CONTEXT
                SerializationDefaults.RPC_CLIENT_CONTEXT = KRYO_RPC_CLIENT_CONTEXT
            } catch(e: IllegalStateException) {
                // Check that it's registered as we expect
                check(SerializationDefaults.SERIALIZATION_FACTORY is SerializationFactoryImpl) { "RPC client encountered conflicting configuration of serialization subsystem." }
                check((SerializationDefaults.SERIALIZATION_FACTORY as SerializationFactoryImpl).alreadyRegisteredSchemes.any { it is KryoClientSerializationScheme }) { "RPC client encountered conflicting configuration of serialization subsystem." }
            }
        }
    }
}