package net.corda.node.internal.rpc.proxies

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.CordaRPCOps
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.`when`

class ThreadContextAdjustingRpcOpsProxyTest {

    private val coreOps = mock<InstrumentedCordaRPCOps>()
    private val mockClassloader = mock<ClassLoader>()
    private val proxy = ThreadContextAdjustingRpcOpsProxy.proxy(coreOps, CordaRPCOps::class.java, mockClassloader)

    private interface InstrumentedCordaRPCOps : CordaRPCOps {
        fun getThreadContextClassLoader(): ClassLoader = Thread.currentThread().contextClassLoader
    }

    @Test(timeout=300_000)
	fun verifyThreadContextIsAdjustedTemporarily() {
        `when`(coreOps.killFlow(any())).thenAnswer {
            assertThat(Thread.currentThread().contextClassLoader).isEqualTo(mockClassloader)
            true
        }

        proxy.killFlow(StateMachineRunId.createRandom())
        assertThat(Thread.currentThread().contextClassLoader).isNotEqualTo(mockClassloader)
    }
}