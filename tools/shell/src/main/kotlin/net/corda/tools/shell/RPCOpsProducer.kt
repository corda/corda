package net.corda.tools.shell

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.GracefulReconnect
import net.corda.client.rpc.internal.RPCClient
import net.corda.core.internal.messaging.InternalCordaRPCOps
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.RPCOps
import java.io.Closeable

internal interface RPCOpsProducer {
    /**
     * Returns a pair of underlying proxy along with the way to gracefully terminate remote communication.
     */
     operator fun <T : RPCOps> invoke(username: String?, credential: String?, rpcOpsClass: Class<T>) : Pair<T, Closeable>
}

internal class DefaultRPCOpsProducer(private val configuration: ShellConfiguration, private val classLoader: ClassLoader? = null, private val standalone: Boolean) : RPCOpsProducer {

    override fun <T : RPCOps> invoke(username: String?, credential: String?, rpcOpsClass: Class<T>): Pair<T, Closeable> {

        return if (rpcOpsClass == InternalCordaRPCOps::class.java || rpcOpsClass == CordaRPCOps::class.java) {
            // For CordaRPCOps we are using CordaRPCClient
            val connection = if (standalone) {
                CordaRPCClient(
                        configuration.hostAndPort,
                        configuration.ssl,
                        classLoader
                ).start(username!!, credential!!, gracefulReconnect = GracefulReconnect())
            } else {
                CordaRPCClient(
                        hostAndPort = configuration.hostAndPort,
                        configuration = CordaRPCClientConfiguration.DEFAULT.copy(
                                maxReconnectAttempts = 1
                        ),
                        sslConfiguration = configuration.ssl,
                        classLoader = classLoader
                ).start(username!!, credential!!)
            }
            @Suppress("UNCHECKED_CAST")
            (connection.proxy as T) to connection
        } else {
            // For other types "plain" RPCClient is used
            val rpcClient = RPCClient<T>(configuration.hostAndPort, configuration.ssl)
            val connection = rpcClient.start(rpcOpsClass, username!!, credential!!)
            connection.proxy to connection
        }
    }
}