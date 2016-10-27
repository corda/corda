package com.r3corda.client.model

import com.r3corda.client.fxutils.foldToObservableList
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.NetworkMapCache
import javafx.collections.ObservableList
import kotlinx.support.jdk8.collections.removeIf
import rx.Observable

class NetworkIdentityModel {
    private val networkIdentityObservable: Observable<NetworkMapCache.MapChange> by observable(NodeMonitorModel::networkMap)

    val networkIdentities: ObservableList<NodeInfo> =
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
}