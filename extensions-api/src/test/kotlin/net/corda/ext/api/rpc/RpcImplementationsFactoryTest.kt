package net.corda.ext.api.rpc

import com.nhaarman.mockito_kotlin.mock
import net.corda.core.messaging.RPCOps
import net.corda.ext.api.NodeInitialContext
import net.corda.ext.api.NodeServicesContext
import net.corda.ext.api.admin.NodeAdmin
import net.corda.ext.api.lifecycle.NodeRpcOps
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.UnsupportedOperationException

class RpcImplementationsFactoryTest {

    private val mockContext = object : NodeServicesContext by mock() {
        override val nodeAdmin = object : NodeAdmin by mock() {
            override val corDappClassLoader = this::class.java.classLoader
        }
    }

    @Test
    fun testDiscoverAndCreateOverrides() {
        val discoveryResult = RpcImplementationsFactory(::customLoadingMethod).discoverAndCreate(mockContext)
        assertEquals(listOf(AlphaImpl::class.java, BetaImpl3::class.java, GammaImpl2::class.java),
                discoveryResult.map { it.lifecycleInstance::class.java })
    }

    private interface Alpha : RPCOps

    private class AlphaImpl : Alpha, NodeRpcOps<Alpha> by mock() {
        override val targetInterface: Class<Alpha> = Alpha::class.java
        override fun getVersion(nodeServicesContext: NodeInitialContext): Int = 1
    }

    private interface Beta : RPCOps

    private class BetaImpl1 : Beta, NodeRpcOps<Beta> by mock() {
        override val targetInterface: Class<Beta> = Beta::class.java
        override fun getVersion(nodeServicesContext: NodeInitialContext): Int = 1
    }

    private class BetaImpl2 : Beta, NodeRpcOps<Beta> by mock() {
        override val targetInterface: Class<Beta> = Beta::class.java
        override fun getVersion(nodeServicesContext: NodeInitialContext): Int = 2
    }

    private class BetaImpl3 : Beta, NodeRpcOps<Beta> by mock() {
        override val targetInterface: Class<Beta> = Beta::class.java
        override fun getVersion(nodeServicesContext: NodeInitialContext): Int = 3
    }

    private interface Gamma : RPCOps

    private open class GammaImpl1 : Gamma, NodeRpcOps<Gamma> by mock() {
        override val targetInterface: Class<Gamma> = Gamma::class.java
        override fun getVersion(nodeServicesContext: NodeInitialContext): Int = 1
    }

    private open class GammaImpl2 : GammaImpl1() {
        override fun getVersion(nodeServicesContext: NodeInitialContext): Int = 2
    }

    private class GammaImpl3 : GammaImpl2() {
        override fun getVersion(nodeServicesContext: NodeInitialContext): Int {
            throw UnsupportedOperationException()
        }
    }

    private fun customLoadingMethod(): List<NodeRpcOps<*>> = listOf(AlphaImpl(),
            BetaImpl1(), BetaImpl2(), BetaImpl3(),
            GammaImpl1(), GammaImpl2(), GammaImpl3())
}