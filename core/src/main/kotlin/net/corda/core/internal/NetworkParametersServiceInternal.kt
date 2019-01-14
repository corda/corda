package net.corda.core.internal

import net.corda.core.identity.Party
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.NetworkParametersService

interface NetworkParametersServiceInternal : NetworkParametersService {
    /**
     * Returns the [NotaryInfo] for a notary [party] in the current or any historic network parameter whitelist, or null if not found.
     */
    fun getHistoricNotary(party: Party): NotaryInfo?
}
