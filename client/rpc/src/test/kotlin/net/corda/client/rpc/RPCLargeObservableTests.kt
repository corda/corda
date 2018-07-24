package net.corda.client.rpc

import net.corda.core.messaging.RPCOps
import net.corda.testing.node.internal.RPCDriverDSL
import net.corda.testing.node.internal.rpcDriver
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import rx.Observable
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class RPCLargeObservableTests : AbstractRPCTest() {

    private fun RPCDriverDSL.testProxy(): TestOps {
        return testProxy<TestOps>(TestOpsImpl()).ops
    }

    internal interface TestOps : RPCOps {

        fun makeObservable(): Observable<Int>
    }

    internal class TestOpsImpl : TestOps {
        override val protocolVersion = 1

        override fun makeObservable(): Observable<Int> = Observable.range(1, Int.MAX_VALUE)
    }

    @Test
    fun `simple observable`() {
        rpcDriver {
            val proxy = testProxy()
            // This tests that the observations are transmitted correctly, also check that server side doesn't try to serialize the whole lot
            // till client consumed some of the output produced.
            val observations = proxy.makeObservable().take(4).toBlocking().toIterable().toList()
            assertEquals(listOf(1, 2, 3, 4), observations)
        }
    }
}
