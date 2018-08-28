package net.corda.client.jfx.model

import com.github.benmanes.caffeine.cache.Caffeine
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.corda.client.jfx.utils.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.internal.buildNamed
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache.MapChange
import java.security.PublicKey

class NetworkIdentityModel {
    private val networkIdentityObservable by observable(NodeMonitorModel::networkMap)

    private val networkIdentities: ObservableList<NodeInfo> =
            networkIdentityObservable.fold(FXCollections.observableArrayList()) { list, update ->
                list.removeIf {
                    when (update) {
                        is MapChange.Removed -> it == update.node
                        is MapChange.Modified -> it == update.previousNode
                        else -> false
                    }
                }
                if (update is MapChange.Modified || update is MapChange.Added) {
                    list.addAll(update.node)
                }
            }

    private val rpcProxy by observableValue(NodeMonitorModel::proxyObservable)

    private val identityCache = Caffeine.newBuilder()
            .buildNamed<PublicKey, ObservableValue<NodeInfo?>>("NetworkIdentityModel_identity", { publicKey ->
                publicKey.let { rpcProxy.map { it?.cordaRPCOps?.nodeInfoFromParty(AnonymousParty(publicKey)) } }
            })
    val notaries = ChosenList(rpcProxy.map { FXCollections.observableList(it?.cordaRPCOps?.notaryIdentities() ?: emptyList()) }, "notaries")
    val notaryNodes: ObservableList<NodeInfo> = notaries.map { rpcProxy.value?.cordaRPCOps?.nodeInfoFromParty(it) }.filterNotNull()
    val parties: ObservableList<NodeInfo> = networkIdentities
            .filtered { it.legalIdentities.all { it !in notaries } }.unique()
    val myIdentity = rpcProxy.map { it?.cordaRPCOps?.nodeInfo()?.legalIdentitiesAndCerts?.first()?.party }

    fun partyFromPublicKey(publicKey: PublicKey): ObservableValue<NodeInfo?> = identityCache[publicKey]!!
}
