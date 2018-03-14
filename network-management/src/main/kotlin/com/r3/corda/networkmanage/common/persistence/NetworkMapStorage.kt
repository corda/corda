/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.utils.SignedNetworkMap
import com.r3.corda.networkmanage.common.utils.SignedNetworkParameters
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.node.NetworkParameters

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
     * Retrieves node info hashes where [isCurrent] is true and the certificate status is [CertificateStatus.VALID]
     *
     * @return list of current and valid node info hashes.
     */
    fun getActiveNodeInfoHashes(): List<SecureHash>

    /**
     * Persists a new instance of the signed network map.
     * @param signedNetworkMap encapsulates all the information needed for persisting current network map state.
     */
    fun saveNetworkMap(signedNetworkMap: SignedNetworkMap)

    /**
     * Retrieve the signed with certificate network parameters by their hash. The hash is that of the underlying
     * [NetworkParameters] object and not the `SignedWithCert<NetworkParameters>` object that's returned.
     * @return signed network parameters corresponding to the given hash or null if it does not exist (parameters don't exist or they haven't been signed yet)
     */
    fun getSignedNetworkParameters(hash: SecureHash): SignedNetworkParameters?

    /**
     * Retrieve the network parameters of the current network map, or null if there's no network map.
     */
    // TODO: Remove this method. We should get the "current" network parameter by using the the hash in the network map and use the [getSignedNetworkParameters] method.
    fun getNetworkParametersOfNetworkMap(): SignedNetworkParameters?

    /**
     *  Persists given network parameters with signature if provided.
     *  @return hash corresponding to newly created network parameters entry
     */
    fun saveNetworkParameters(networkParameters: NetworkParameters, sig: DigitalSignatureWithCert?): SecureHash

    /**
     * Retrieves the latest (i.e. most recently inserted) network parameters
     * Note that they may not have been signed up yet.
     * @return latest network parameters
     */
    fun getLatestNetworkParameters(): NetworkParameters?
}
