package net.corda.ext.api.rpc

import net.corda.core.messaging.RPCOps
import net.corda.core.utilities.contextLogger
import net.corda.ext.api.lifecycle.NodeLifecycleObserver
import net.corda.ext.api.NodeServicesContext
import net.corda.ext.api.lifecycle.NodeRpcOps
import net.corda.ext.api.rpc.proxies.AuthenticatedRpcOpsProxy
import net.corda.ext.api.rpc.proxies.ThreadContextAdjustingRpcOpsProxy
import java.util.*

private typealias RpcOpWithVersion = Pair<NodeRpcOps<out RPCOps>, Int?>

/**
 * Responsible for discovery of `RPCOps` server-side implementations, filtering them out as necessary and
 * creating proxies which will enforce permissions and other aspects.
 */
class RpcImplementationsFactory(private val loadingMethod: () -> List<NodeRpcOps<*>> = Companion::defaultLoadingMethod) {

    companion object {
        private val logger = contextLogger()

        private fun defaultLoadingMethod() : List<NodeRpcOps<*>> {
            val serviceLoader = ServiceLoader.load(NodeRpcOps::class.java)
            return try {
                serviceLoader.toList()
            } catch (ex: Throwable) {
                logger.error("Unexpected exception", ex)
                throw IllegalStateException(ex)
            }
        }
    }

    /**
     * Represents container which holds a real implementation for node lifecycle notification purposes along with
     * proxied version of the interface which knows how to perform entitlements check as well as perform some of the actions.
     */
    data class ImplWithProxy(val lifecycleInstance: NodeLifecycleObserver, val proxy : RPCOps)

    fun discoverAndCreate(nodeServicesContext: NodeServicesContext) : List<ImplWithProxy> {
        val implementationsLoaded: List<NodeRpcOps<*>> = loadingMethod()
        logger.info("Discovered NodeRpcOps count: ${implementationsLoaded.size}")

        val implementationsWithVersions: List<RpcOpWithVersion> = implementationsLoaded.map {
            RpcOpWithVersion(it, try {
                it.getVersion(nodeServicesContext)
            } catch (ex: Exception) {
                logger.warn("Failed to provide version: $it", ex)
                null
            })
        }

        val versionSuccess: List<RpcOpWithVersion> = implementationsWithVersions.filter { it.second != null }

        val groupedByTargetInterface: Map<Class<out RPCOps>, List<RpcOpWithVersion>> = versionSuccess.groupBy { it.first.targetInterface }

        val maxVersionList: List<NodeRpcOps<out RPCOps>> = groupedByTargetInterface.map { intGroup ->
            val listOfSameInterfaceImplementations = intGroup.value
            val maxVersionElement = listOfSameInterfaceImplementations.maxBy { it.second!! }!!
            logMaxVersionImpls(listOfSameInterfaceImplementations, maxVersionElement)
            maxVersionElement.first
        }

        return maxVersionList.map { rpcOpsImpl ->
            // Mind that order of proxies is important
            val targetInterface = rpcOpsImpl.targetInterface
            val stage1Proxy = AuthenticatedRpcOpsProxy.proxy(rpcOpsImpl, targetInterface)
            val stage2Proxy = ThreadContextAdjustingRpcOpsProxy.proxy(stage1Proxy, targetInterface,
                    nodeServicesContext.nodeAdmin.corDappClassLoader)

            ImplWithProxy(rpcOpsImpl, stage2Proxy)
        }
    }

    private fun logMaxVersionImpls(listOfSameInterfaceImplementations: List<RpcOpWithVersion>,
                                   maxVersionElement: RpcOpWithVersion) {
        val targetInterface = maxVersionElement.first.targetInterface
        if(listOfSameInterfaceImplementations.size == 1) {
            logger.info("For $targetInterface there is a single implementation: ${maxVersionElement.first}")
            return
        }

        val eliminatedWithVersions = listOfSameInterfaceImplementations.filterNot { it === maxVersionElement }
                .joinToString { "${it.first} -> ${it.second}" }
        val maxVersion = maxVersionElement.second!!

        logger.info("For $targetInterface maxVersion is: $maxVersion. The winner is: ${maxVersionElement.first}." +
                " Runners-up: $eliminatedWithVersions.")
    }
}