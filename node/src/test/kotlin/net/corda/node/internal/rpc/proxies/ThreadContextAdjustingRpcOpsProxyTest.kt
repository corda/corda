package net.corda.node.internal.rpc.proxies

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.messaging.InternalCordaRPCOps
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.`when`

class ThreadContextAdjustingRpcOpsProxyTest {

    private val coreOps = mock<InstrumentedCordaRPCOps>()
    private val mockClassloader = mock<ClassLoader>()
    private val proxy = ThreadContextAdjustingRpcOpsProxy(coreOps, mockClassloader)


    private interface InstrumentedCordaRPCOps: InternalCordaRPCOps {
        fun getThreadContextClassLoader(): ClassLoader = Thread.currentThread().contextClassLoader
    }

    @Test
    fun verifyThreadContextIsAdjustedTemporarily() {
        `when`(coreOps.killFlow(any())).thenAnswer {
            assertThat(Thread.currentThread().contextClassLoader).isEqualTo(mockClassloader)
            true
        }

        val result = proxy.killFlow(StateMachineRunId.createRandom())
        assertThat(Thread.currentThread().contextClassLoader).isNotEqualTo(mockClassloader)
    }
}