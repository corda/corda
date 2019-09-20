package net.corda.client.rpc

import net.corda.client.rpc.RPCMultipleInterfacesTests.LegacyIntRPCOpsImpl.testValue
import net.corda.client.rpc.RPCMultipleInterfacesTests.StringRPCOpsImpl.testPhrase
import net.corda.core.messaging.RPCOps
import net.corda.nodeapi.RPCApi
import net.corda.testing.common.internal.isInstanceOf
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.node.internal.rpcDriver
import net.corda.testing.node.internal.startRpcClient
import org.assertj.core.api.Assertions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import rx.Observable

class RPCMultipleInterfacesTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    interface IntRPCOps : RPCOps {
        fun stream(size: Int): Observable<Int>

        fun intTestMethod(): Int
    }

    interface StringRPCOps : RPCOps {
        fun stream(size: Int): Observable<String>

        fun stringTestMethod() : String
    }

    class IntRPCOpsImpl : IntRPCOps {
        override val protocolVersion = 1000

        override fun stream(size: Int): Observable<Int> {
            return Observable.range(0, size)
        }

        override fun intTestMethod(): Int = protocolVersion
    }

    object LegacyIntRPCOpsImpl : IntRPCOps {

        const val testValue = 99

        override val protocolVersion = RPCApi.METHOD_FQN_CUTOFF_VERSION

        override fun stream(size: Int): Observable<Int> {
            return Observable.range(0, size)
        }

        override fun intTestMethod(): Int = testValue
    }

    object StringRPCOpsImpl : StringRPCOps {

        const val testPhrase = "I work with Strings."

        override val protocolVersion = 1000

        override fun stream(size: Int): Observable<String> {
            return Observable.range(0, size).map { it.toString(8) }
        }

        override fun stringTestMethod(): String = testPhrase
    }

    @Test
    fun `can talk multiple interfaces`() {
        rpcDriver {
            val server = startRpcServer(listOps = listOf(IntRPCOpsImpl(), StringRPCOpsImpl)).get()

            val clientInt = startRpcClient<IntRPCOps>(server.broker.hostAndPort!!).get()
            val intList = clientInt.stream(20).toList().toBlocking().single()
            assertEquals(20, intList.size)

            assertEquals(1000, clientInt.intTestMethod())

            val clientString = startRpcClient<StringRPCOps>(server.broker.hostAndPort!!).get()
            val stringList = clientString.stream(100).toList().toBlocking().single()
            assertEquals(100, stringList.size)
            assertTrue(stringList.toString(), stringList.all { it.matches("[0-7]*".toRegex()) })

            assertEquals(testPhrase, clientString.stringTestMethod())

            server.rpcServer.close()
        }
    }

    @Test
    fun `legacy mode operation`() {
        rpcDriver {
            // This is slightly artificial, as in legacy mode it will not be possible to pass more that 1 interface
            // However, this proves the point that anything from `StringRPCOps` will not be accessible.
            val server = startRpcServer(listOps = listOf(LegacyIntRPCOpsImpl, StringRPCOpsImpl)).get()

            val clientInt = startRpcClient<IntRPCOps>(server.broker.hostAndPort!!).get()
            val intList = clientInt.stream(30).toList().toBlocking().single()
            assertEquals(30, intList.size)

            assertEquals(testValue, clientInt.intTestMethod())

            val clientString = startRpcClient<StringRPCOps>(server.broker.hostAndPort!!).get()
            Assertions.assertThatThrownBy { clientString.stringTestMethod() }
                    .isInstanceOf<RPCException>()
                    .hasMessageContaining("IntRPCOps#stringTestMethod")

            server.rpcServer.close()
        }
    }
}