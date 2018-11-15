package net.corda.core.node.services

import net.corda.core.DoNotImplement
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo

/**
 * Interface for handling network parameters storage used for resolving transactions according to parameters that were
 * historically in force in the network.
 */
@DoNotImplement
interface NetworkParametersStorage {
    /**
     * Hash of the current parameters for the network.
     */
    val currentHash: SecureHash

    /**
     * For backwards compatibility, this parameters hash will be used for resolving historical transactions in the chain.
     */
    val defaultHash: SecureHash

    /**
     * Return network parameters for the given hash. Null if there are no parameters for this hash in the storage and we are unable to
     * get them from network map.
     */
    fun lookup(hash: SecureHash): NetworkParameters?

    /**
     * Returns the [NotaryInfo] for a notary [party] in the current or any historic network parameter whitelist, or null if not found.
     */
    fun getHistoricNotary(party: Party): NotaryInfo?
}
