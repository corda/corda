/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.rpc.internal

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.pendingFlowsCount
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.SSLConfiguration
import rx.Observable

/** Utility which exposes the internal Corda RPC constructor to other internal Corda components */
fun createCordaRPCClientWithSsl(
        hostAndPort: NetworkHostAndPort,
        configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.default(),
        sslConfiguration: SSLConfiguration? = null
) = CordaRPCClient.createWithSsl(hostAndPort, configuration, sslConfiguration)

fun createCordaRPCClientWithSsl(
        haAddressPool: List<NetworkHostAndPort>,
        configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.default(),
        sslConfiguration: SSLConfiguration? = null
) = CordaRPCClient.createWithSsl(haAddressPool, configuration, sslConfiguration)

fun createCordaRPCClientWithSslAndClassLoader(
        hostAndPort: NetworkHostAndPort,
        configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.default(),
        sslConfiguration: SSLConfiguration? = null,
        classLoader: ClassLoader? = null
) = CordaRPCClient.createWithSslAndClassLoader(hostAndPort, configuration, sslConfiguration, classLoader)

fun createCordaRPCClientWithSslAndClassLoader(
        haAddressPool: List<NetworkHostAndPort>,
        configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.default(),
        sslConfiguration: SSLConfiguration? = null,
        classLoader: ClassLoader? = null
) = CordaRPCClient.createWithSslAndClassLoader(haAddressPool, configuration, sslConfiguration, classLoader)

fun CordaRPCOps.drainAndShutdown(): Observable<Unit> {

    setFlowsDrainingModeEnabled(true)
    return pendingFlowsCount().updates
            .doOnError { error ->
                throw error
            }
            .doOnCompleted { shutdown() }.map { }
}