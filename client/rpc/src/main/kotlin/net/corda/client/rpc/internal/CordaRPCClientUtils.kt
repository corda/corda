package net.corda.client.rpc.internal

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.messaging.ClientRpcSslOptions

/** Utility which exposes the internal Corda RPC constructor to other internal Corda components */
fun createCordaRPCClientWithSslAndClassLoader(
        hostAndPort: NetworkHostAndPort,
        configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
        sslConfiguration: ClientRpcSslOptions? = null,
        classLoader: ClassLoader? = null
) = CordaRPCClient.createWithSslAndClassLoader(hostAndPort, configuration, sslConfiguration, classLoader)