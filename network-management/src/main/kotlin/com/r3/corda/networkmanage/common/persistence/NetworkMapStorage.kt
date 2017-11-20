package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.signer.SignedNetworkMap
import net.corda.core.crypto.SecureHash
import net.corda.core.node.NetworkParameters

/**
 * Data access object interface for NetworkMap persistence layer
 */
interface NetworkMapStorage {
    /**
     * Retrieves current network map. Current in this context means the one that has been most recently signed.
     * @return current network map
     */
    fun getCurrentNetworkMap(): SignedNetworkMap

    /**
     * Retrieves current map node info hashes only. Hashes are further filtered by the [certificateStatuses] parameter
     * that restricts considered node info to only those which [CertificateStatus] value corresponds to one in the passed
     * collection. If null or empty list is passed then filtering has no effect and all node info hashes from the current
     * network map are returned.
     * @param certificateStatuses certificate statuses to be used in the node info filtering. Node info hash is returned
     * in the result collection only if it is in the current network map and its certificate status belongs to the
     * [certificateStatuses] collection or if [certificateStatuses] collection is null or empty.
     * @return list of current network map node info hashes satisfying the filtering criteria given by [certificateStatuses].
     */
    fun getCurrentNetworkMapNodeInfoHashes(certificateStatuses: List<CertificateStatus>): List<SecureHash>

    /**
     * Persists a new instance of the signed network map.
     * @param signedNetworkMap encapsulates all the information needed for persisting current network map state.
     */
    fun saveNetworkMap(signedNetworkMap: SignedNetworkMap)

    /**
     * Retrieve all node info hashes for all node info with valid certificates,
     * that are not associated with any network map yet.
     */
    fun getDetachedAndValidNodeInfoHashes(): List<SecureHash>

    /**
     * Retrieve network parameters by their hash.
     * @return network parameters corresponding to the given hash or null if it does not exist
     */
    fun getNetworkParameters(parameterHash: SecureHash): NetworkParameters

    /**
     * Retrieve network map parameters that are used in the current network map.
     * @return current network map parameters
     */
    fun getCurrentNetworkParameters(): NetworkParameters

    /**
     *  Persists given network parameters.
     *  @return hash corresponding to newly create network parameters entry
     */
    fun putNetworkParameters(networkParameters: NetworkParameters): SecureHash

    /**
     * Retrieves the latest (i.e. most recently inserted) network parameters
     * @return latest network parameters
     */
    fun getLatestNetworkParameters(): NetworkParameters
}