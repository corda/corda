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

import com.esotericsoftware.kryo.KryoException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.*
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.node.internal.rpcDriver
import net.corda.testing.node.internal.startRpcClient
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test

class RPCFailureTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    class Unserializable
    interface Ops : RPCOps {
        fun getUnserializable(): Unserializable
        fun getUnserializableAsync(): CordaFuture<Unserializable>
        fun kotlinNPE()
        fun kotlinNPEAsync(): CordaFuture<Unit>
    }

    class OpsImpl : Ops {
        override val protocolVersion = 1
        override fun getUnserializable() = Unserializable()
        override fun getUnserializableAsync(): CordaFuture<Unserializable> {
            return openFuture<Unserializable>().apply { capture { getUnserializable() } }
        }

        override fun kotlinNPE() {
            (null as Any?)!!.hashCode()
        }

        override fun kotlinNPEAsync(): CordaFuture<Unit> {
            return openFuture<Unit>().apply { capture { kotlinNPE() } }
        }
    }

    private fun rpc(proc: (Ops) -> Any?): Unit = rpcDriver {
        val server = startRpcServer(ops = OpsImpl()).getOrThrow()
        proc(startRpcClient<Ops>(server.broker.hostAndPort!!).getOrThrow())
    }

    @Test
    fun `kotlin NPE`() = rpc {
        assertThatThrownBy { it.kotlinNPE() }.isInstanceOf(KotlinNullPointerException::class.java)
    }

    @Test
    fun `kotlin NPE async`() = rpc {
        val future = it.kotlinNPEAsync()
        assertThatThrownBy { future.getOrThrow() }.isInstanceOf(KotlinNullPointerException::class.java)
    }

    @Test
    fun unserializable() = rpc {
        assertThatThrownBy { it.getUnserializable() }.isInstanceOf(KryoException::class.java)
    }

    @Test
    fun `unserializable async`() = rpc {
        val future = it.getUnserializableAsync()
        assertThatThrownBy { future.getOrThrow() }.isInstanceOf(KryoException::class.java)
    }
}
