package com.r3.corda.networkmanage.common.persistence

import net.corda.core.crypto.SecureHash
import net.corda.nodeapi.internal.network.NetworkParameters
import net.corda.nodeapi.internal.network.SignedNetworkMap

/**
 * Data access object interface for NetworkMap persistence layer
 */
interface NetworkMapStorage {
    /**
     * Retrieves current network map. Current in this context means the one that has been most recently signed.
     * @return current network map
     */
    fun getCurrentNetworkMap(): SignedNetworkMap?

    /**
     * Retrieves node info hashes where the certificate status matches [certificateStatus].
     *
     * @param certificateStatus certificate status to be used in the node info filtering. Node info hash is returned
     * in the result collection if its certificate status matches [certificateStatus].
     * @return list of node info hashes satisfying the filtering criteria given by [certificateStatus].
     */
    fun getNodeInfoHashes(certificateStatus: CertificateStatus): List<SecureHash>

    /**
     * Persists a new instance of the signed network map.
     * @param signedNetworkMap encapsulates all the information needed for persisting current network map state.
     */
    fun saveNetworkMap(signedNetworkMap: SignedNetworkMap)

    /**
     * Retrieve network parameters by their hash.
     * @return network parameters corresponding to the given hash or null if it does not exist
     */
    fun getNetworkParameters(parameterHash: SecureHash): NetworkParameters?

    /**
     * Retrieve network map parameters.
     * @return current network map parameters or null if they don't exist
     */
    fun getCurrentNetworkParameters(): NetworkParameters?

    /**
     *  Persists given network parameters.
     *  @return hash corresponding to newly create network parameters entry
     */
    fun saveNetworkParameters(networkParameters: NetworkParameters): SecureHash

    /**
     * Retrieves the latest (i.e. most recently inserted) network parameters
     * @return latest network parameters
     */
    fun getLatestNetworkParameters(): NetworkParameters
}