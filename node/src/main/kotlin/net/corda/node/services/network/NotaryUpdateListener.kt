package net.corda.node.services.network

import net.corda.core.node.NotaryInfo

/**
 * When notaries inside network parameters change on a flag day, onNewNotaryList will be invoked with the new notary list.
 * Used inside {@link net.corda.node.services.network.NetworkParametersUpdater}
 */
interface NotaryUpdateListener {
    fun onNewNotaryList(notaries: List<NotaryInfo>)
}