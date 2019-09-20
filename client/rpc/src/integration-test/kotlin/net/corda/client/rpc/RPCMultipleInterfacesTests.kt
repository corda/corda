package net.corda.client.rpc

import net.corda.client.rpc.RPCMultipleInterfacesTests.StringRPCOpsImpl.testPhrase
import net.corda.client.rpc.internal.RPCClient
import net.corda.core.context.Trace
import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.RPCOps
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.serialize
import net.corda.core.utilities.*
import net.corda.node.services.rpc.RPCServerConfiguration
import net.corda.nodeapi.RPCApi
import net.corda.testing.common.internal.eventually
import net.corda.testing.common.internal.succeeds
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.testThreadFactory
import net.corda.testing.node.internal.*
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration
import org.apache.activemq.artemis.api.core.SimpleString
import org.junit.After
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import rx.Observable
import rx.subjects.PublishSubject
import rx.subjects.UnicastSubject
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class RPCMultipleInterfacesTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    interface IntRPCOps : RPCOps {
        fun stream(size: Int): Observable<Int>
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

            val clientString = startRpcClient<StringRPCOps>(server.broker.hostAndPort!!).get()
            val stringList = clientString.stream(100).toList().toBlocking().single()
            assertEquals(100, stringList.size)
            assertTrue(stringList.toString(), stringList.all { it.matches("[0-7]*".toRegex()) })

            assertEquals(testPhrase, clientString.stringTestMethod())

            server.rpcServer.close()
        }
    }
}