/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.rpc

import net.corda.client.rpc.internal.CordaRPCClientConfigurationImpl
import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.concurrent.map
import net.corda.core.messaging.RPCOps
import net.corda.node.services.messaging.RPCServerConfiguration
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.node.User
import net.corda.testing.node.internal.RPCDriverDSL
import net.corda.testing.node.internal.rpcTestUser
import net.corda.testing.node.internal.startInVmRpcClient
import net.corda.testing.node.internal.startRpcClient
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.junit.Rule
import org.junit.runners.Parameterized

open class AbstractRPCTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    enum class RPCTestMode {
        InVm,
        Netty
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "Mode = {0}")
        fun defaultModes() = modes(RPCTestMode.InVm, RPCTestMode.Netty)

        fun modes(vararg modes: RPCTestMode) = listOf(*modes).map { arrayOf(it) }
    }

    @Parameterized.Parameter
    lateinit var mode: RPCTestMode

    data class TestProxy<out I : RPCOps>(
            val ops: I,
            val createSession: () -> ClientSession
    )

    inline fun <reified I : RPCOps> RPCDriverDSL.testProxy(
            ops: I,
            rpcUser: User = rpcTestUser,
            clientConfiguration: CordaRPCClientConfigurationImpl = CordaRPCClientConfigurationImpl.default,
            serverConfiguration: RPCServerConfiguration = RPCServerConfiguration.default
    ): TestProxy<I> {
        return when (mode) {
            RPCTestMode.InVm ->
                startInVmRpcServer(ops = ops, rpcUser = rpcUser, configuration = serverConfiguration).flatMap {
                    startInVmRpcClient<I>(rpcUser.username, rpcUser.password, clientConfiguration).map {
                        TestProxy(it, { startInVmArtemisSession(rpcUser.username, rpcUser.password) })
                    }
                }
            RPCTestMode.Netty ->
                startRpcServer(ops = ops, rpcUser = rpcUser, configuration = serverConfiguration).flatMap { (broker) ->
                    startRpcClient<I>(broker.hostAndPort!!, rpcUser.username, rpcUser.password, clientConfiguration).map {
                        TestProxy(it, { startArtemisSession(broker.hostAndPort!!, rpcUser.username, rpcUser.password) })
                    }
                }
        }.get()
    }
}
