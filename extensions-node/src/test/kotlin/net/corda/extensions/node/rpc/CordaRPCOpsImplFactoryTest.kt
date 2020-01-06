package net.corda.extensions.node.rpc

import com.nhaarman.mockito_kotlin.mock
import net.corda.ext.api.NodeServicesContext
import net.corda.ext.api.admin.NodeAdmin
import net.corda.ext.api.rpc.RpcImplementationsFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CordaRPCOpsImplFactoryTest {

    private val mockContext = object : NodeServicesContext by mock() {
        override val nodeAdmin = object : NodeAdmin by mock() {
            override val corDappClassLoader = this::class.java.classLoader
        }
    }

    @Test
    fun testDiscoverAndCreate() {
        val discoveryResult = RpcImplementationsFactory().discoverAndCreate(mockContext).map { it.lifecycleInstance::class.java }.toSet()
        assertEquals(2, discoveryResult.size)
        assertTrue(CordaRPCOpsImpl::class.java in discoveryResult)
        assertTrue(NodeHealthCheckRpcOpsImpl::class.java in discoveryResult)
    }
}