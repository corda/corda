package net.corda.client.model

import javafx.collections.ObservableList
import kotlinx.support.jdk8.collections.removeIf
import net.corda.client.fxutils.foldToObservableList
import net.corda.client.fxutils.map
import net.corda.core.crypto.PublicKeyTree
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.node.services.network.NetworkMapService

class NetworkIdentityModel {
    private val networkIdentityObservable by observable(NodeMonitorModel::networkMap)

    private val networkIdentities: ObservableList<NodeInfo> =
            networkIdentityObservable.foldToObservableList(Unit) { update, _accumulator, observableList ->
                observableList.removeIf {
                    when (update.type) {
                        NetworkMapCache.MapChangeType.Removed -> it == update.node
                        NetworkMapCache.MapChangeType.Modified -> it == update.prevNodeInfo
                        else -> false
                    }
                }
                observableList.addAll(update.node)
            }

    private val rpcProxy by observableValue(NodeMonitorModel::proxyObservable)

    val parties: ObservableList<NodeInfo> = networkIdentities.filtered { !it.isCordaService() }
    val notaries: ObservableList<NodeInfo> = networkIdentities.filtered { it.advertisedServices.any { it.info.type.isNotary() } }
    val myIdentity = rpcProxy.map { it?.nodeIdentity() }

    private fun NodeInfo.isCordaService(): Boolean {
        return advertisedServices.any { it.info.type == NetworkMapService.type || it.info.type.isNotary() }
    }

    fun lookup(publicKeyTree: PublicKeyTree): NodeInfo? {
        return parties.firstOrNull { it.legalIdentity.owningKey == publicKeyTree } ?: notaries.firstOrNull { it.notaryIdentity.owningKey == publicKeyTree }
    }
}
