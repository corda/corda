package net.corda.client.model

import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import kotlinx.support.jdk8.collections.removeIf
import net.corda.client.fxutils.firstOrDefault
import net.corda.client.fxutils.firstOrNullObservable
import net.corda.client.fxutils.fold
import net.corda.client.fxutils.map
import net.corda.core.crypto.CompositeKey
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache.MapChange
import net.corda.node.services.network.NetworkMapService
import java.security.PublicKey

class NetworkIdentityModel {
    private val networkIdentityObservable by observable(NodeMonitorModel::networkMap)

    val networkIdentities: ObservableList<NodeInfo> =
            networkIdentityObservable.fold(FXCollections.observableArrayList()) { list, update ->
                list.removeIf {
                    when (update) {
                        is MapChange.Removed -> it == update.node
                        is MapChange.Modified -> it == update.previousNode
                        else -> false
                    }
                }
                list.addAll(update.node)
            }

    private val rpcProxy by observableValue(NodeMonitorModel::proxyObservable)

    val parties: ObservableList<NodeInfo> = networkIdentities.filtered { !it.isCordaService() }
    val notaries: ObservableList<NodeInfo> = networkIdentities.filtered { it.advertisedServices.any { it.info.type.isNotary() } }
    val myIdentity = rpcProxy.map { it?.nodeIdentity() }

    private fun NodeInfo.isCordaService(): Boolean {
        // TODO: better way to identify Corda service?
        return advertisedServices.any { it.info.type == NetworkMapService.type || it.info.type.isNotary() }
    }

    fun lookup(compositeKey: CompositeKey): ObservableValue<NodeInfo?> = parties.firstOrDefault(notaries.firstOrNullObservable { it.notaryIdentity.owningKey == compositeKey }) {
        it.legalIdentity.owningKey == compositeKey
    }

    fun lookup(publicKey: PublicKey): ObservableValue<NodeInfo?> = parties.firstOrDefault(notaries.firstOrNullObservable { it.notaryIdentity.owningKey.keys.any { it == publicKey } }) {
        it.legalIdentity.owningKey.keys.any { it == publicKey }
    }
}