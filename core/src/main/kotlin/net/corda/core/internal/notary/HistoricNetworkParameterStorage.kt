package net.corda.core.internal.notary

import net.corda.core.identity.Party
import net.corda.core.node.NotaryInfo

interface HistoricNetworkParameterStorage {
    /**
     * Returns the [NotaryInfo] for a notary [party] in the current or any historic network parameter whitelist, or null if not found.
     */
    fun getHistoricNotary(party: Party): NotaryInfo?
}
