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

import com.r3.corda.networkmanage.common.persistence.entity.NetworkMapEntity
import com.r3.corda.networkmanage.common.persistence.entity.NetworkParametersEntity
import com.r3.corda.networkmanage.common.persistence.entity.ParametersUpdateEntity
import com.r3.corda.networkmanage.common.persistence.entity.NodeInfoEntity
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.node.NetworkParameters
import net.corda.nodeapi.internal.network.NetworkMapAndSigned
import net.corda.nodeapi.internal.network.ParametersUpdate
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import java.time.Instant

/**
 * Data access object interface for NetworkMap persistence layer
 */
// TODO This storage abstraction should be removed. It results in less readable code when constructing network map in NetworkMapSigner
interface NetworkMapStorage {
    /**
     * Returns the active network map, or null
     */
    fun getActiveNetworkMap(): NetworkMapEntity?

    /**
     * Persist the new active network map, replacing any existing network map.
     */
    fun saveNewActiveNetworkMap(networkMapAndSigned: NetworkMapAndSigned)

    /**
     * Retrieves node info hashes where [NodeInfoEntity.isCurrent] is true and the certificate status is [CertificateStatus.VALID]
     */
    // TODO "Active" is the wrong word here
    fun getActiveNodeInfoHashes(): List<SecureHash>

    /**
     * Retrieve the signed with certificate network parameters by their hash. The hash is that of the underlying
     * [NetworkParameters] object and not the [SignedNetworkParameters] object that's returned.
     * @return signed network parameters corresponding to the given hash or null if it does not exist (parameters don't exist or they haven't been signed yet)
     */
    fun getSignedNetworkParameters(hash: SecureHash): SignedNetworkParameters?

    /**
     *  Persists given network parameters with signature if provided.
     *  @return hash corresponding to newly created network parameters entry
     */
    fun saveNetworkParameters(networkParameters: NetworkParameters, signature: DigitalSignatureWithCert?): SecureHash

    /**
     * Save new parameters update information with corresponding network parameters. Only one parameters update entity can be present at any time. Any existing
     * parameters update is cleared and overwritten by this one.
     */
    fun saveNewParametersUpdate(networkParameters: NetworkParameters, description: String, updateDeadline: Instant)

    /**
     * Indicate that it is time to switch network parameters in network map from active ones to the ones from update.
     * @param parametersHash hash of the parameters from update
     */
    fun setFlagDay(parametersHash: SecureHash)

    /**
     * Retrieves the latest (i.e. most recently inserted) network parameters entity
     * Note that they may not have been signed up yet.
     * @return latest network parameters entity
     */
    fun getLatestNetworkParameters(): NetworkParametersEntity?

    /**
     * Retrieve any parameters update that may be active, null if none are present.
     */
    fun getParametersUpdate(): ParametersUpdateEntity?

    /**
     * Removes any scheduled parameters updates.
     */
    fun clearParametersUpdates()
}
