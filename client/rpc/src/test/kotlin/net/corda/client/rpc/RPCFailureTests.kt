package net.corda.client.rpc

import net.corda.core.CordaRuntimeException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.RPCOps
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
        override val protocolVersion = 1000
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

    @Test(timeout=300_000)
	fun `kotlin NPE`() = rpc {
        assertThatThrownBy { it.kotlinNPE() }.isInstanceOf(CordaRuntimeException::class.java)
                .hasMessageContaining("java.lang.NullPointerException")
    }

    @Test(timeout=300_000)
	fun `kotlin NPE async`() = rpc {
        val future = it.kotlinNPEAsync()
        assertThatThrownBy { future.getOrThrow() }.isInstanceOf(CordaRuntimeException::class.java)
                .hasMessageContaining("java.lang.NullPointerException")
    }

    @Test(timeout=300_000)
	fun unserializable() = rpc {
        assertThatThrownBy { it.getUnserializable() }.isInstanceOf(CordaRuntimeException::class.java)
                .hasMessageContaining("java.io.NotSerializableException:")
                .hasMessageContaining("Unserializable\" is not on the whitelist or annotated with @CordaSerializable.")
    }

    @Test(timeout=300_000)
	fun `unserializable async`() = rpc {
        val future = it.getUnserializableAsync()
        assertThatThrownBy { future.getOrThrow() }.isInstanceOf(CordaRuntimeException::class.java)
                .hasMessageContaining("java.io.NotSerializableException:")
                .hasMessageContaining("Unserializable\" is not on the whitelist or annotated with @CordaSerializable.")
    }
}
