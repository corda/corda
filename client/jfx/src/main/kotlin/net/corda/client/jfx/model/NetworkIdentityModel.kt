/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.jfx.model

import com.github.benmanes.caffeine.cache.Caffeine
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.corda.client.jfx.utils.ChosenList
import net.corda.client.jfx.utils.filterNotNull
import net.corda.client.jfx.utils.fold
import net.corda.client.jfx.utils.map
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
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
                if(update is MapChange.Modified || update is MapChange.Added){
                    list.addAll(update.node)
                }
            }

    private val rpcProxy by observableValue(NodeMonitorModel::proxyObservable)

    private val identityCache = Caffeine.newBuilder()
            .build<PublicKey, ObservableValue<NodeInfo?>>({ publicKey ->
                publicKey?.let { rpcProxy.map { it?.nodeInfoFromParty(AnonymousParty(publicKey)) } }
            })
    val notaries = ChosenList(rpcProxy.map { FXCollections.observableList(it?.notaryIdentities() ?: emptyList()) })
    val notaryNodes: ObservableList<NodeInfo> = notaries.map { rpcProxy.value?.nodeInfoFromParty(it) }.filterNotNull()
    val parties: ObservableList<NodeInfo> = networkIdentities
            .filtered { it.legalIdentities.all { it !in notaries } }
    val myIdentity = rpcProxy.map { it?.nodeInfo()?.legalIdentitiesAndCerts?.first()?.party }

    fun partyFromPublicKey(publicKey: PublicKey): ObservableValue<NodeInfo?> = identityCache[publicKey]!!
}
