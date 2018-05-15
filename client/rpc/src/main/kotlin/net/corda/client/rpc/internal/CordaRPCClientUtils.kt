package net.corda.client.rpc.internal

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.pendingFlowsCount
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.ClientRpcSslOptions
import net.corda.nodeapi.internal.config.SSLConfiguration
import rx.Observable

/** Utility which exposes the internal Corda RPC constructor to other internal Corda components */
fun createCordaRPCClientWithSslAndClassLoader(
        hostAndPort: NetworkHostAndPort,
        configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.default(),
        sslConfiguration: ClientRpcSslOptions? = null,
        classLoader: ClassLoader? = null
) = CordaRPCClient.createWithSslAndClassLoader(hostAndPort, configuration, sslConfiguration, classLoader)

fun createCordaRPCClientWithInternalSslAndClassLoader(
        hostAndPort: NetworkHostAndPort,
        configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.default(),
        sslConfiguration: SSLConfiguration? = null,
        classLoader: ClassLoader? = null
) = CordaRPCClient.createWithInternalSslAndClassLoader(hostAndPort, configuration, sslConfiguration, classLoader)

fun CordaRPCOps.drainAndShutdown(): Observable<Unit> {

    setFlowsDrainingModeEnabled(true)
    return pendingFlowsCount().updates
            .doOnError { error ->
                throw error
            }
            .doOnCompleted { shutdown() }.map { }
}