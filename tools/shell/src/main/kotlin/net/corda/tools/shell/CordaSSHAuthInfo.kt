package net.corda.tools.shell

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalListener
import com.google.common.util.concurrent.MoreExecutors
import net.corda.client.rpc.RPCConnection
import net.corda.core.internal.utilities.InvocationHandlerTemplate
import net.corda.core.messaging.RPCOps
import net.corda.tools.shell.utlities.ANSIProgressRenderer
import java.lang.reflect.Proxy

internal class CordaSSHAuthInfo(private val rpcOpsProducer: RPCOpsProducer,
                                private val username: String, private val credential: String, val ansiProgressRenderer: ANSIProgressRenderer? = null,
                                val isSsh: Boolean = false) : SshAuthInfo  {
    override fun isSuccessful(): Boolean = true

    /**
     * It is necessary to have a cache to prevent creation of too many proxies for the same class. Proxy ensures that RPC connections gracefully
     * closed when cache entry is eliminated
     */
    private val proxiesCache = Caffeine.newBuilder()
            .maximumSize(10)
            .removalListener(RemovalListener<Class<out RPCOps>, Pair<RPCOps, RPCConnection<RPCOps>>> { _, value, _ -> value?.second?.close() })
            .executor(MoreExecutors.directExecutor())
            .build(CacheLoader<Class<out RPCOps>, Pair<RPCOps, RPCConnection<RPCOps>>> { key -> createRpcOps(key) })

    override fun <T : RPCOps> getOrCreateRpcOps(rpcOpsClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return proxiesCache.get(rpcOpsClass)!!.first as T
    }

    fun cleanUp() {
        proxiesCache.asMap().forEach {
            proxiesCache.invalidate(it.key)
            it.value.second.forceClose()
        }
    }

    private fun <T : RPCOps> createRpcOps(rpcOpsClass: Class<out T>): Pair<T, RPCConnection<T>> {
        val producerResult = rpcOpsProducer(username, credential, rpcOpsClass)
        val anotherProxy = proxyRPCOps(producerResult.proxy, rpcOpsClass)
        return anotherProxy to producerResult
    }

    private fun <T : RPCOps> proxyRPCOps(instance: T, rpcOpsClass: Class<out T>): T {
        require(rpcOpsClass.isInterface) { "$rpcOpsClass must be an interface" }
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(rpcOpsClass.classLoader, arrayOf(rpcOpsClass), object : InvocationHandlerTemplate {
            override val delegate = instance
        }) as T
    }
}