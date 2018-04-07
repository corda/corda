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

import com.r3.corda.networkmanage.common.persistence.entity.*
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.node.NetworkParameters
import net.corda.nodeapi.internal.network.NetworkMapAndSigned
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import java.time.Instant

/**
 * Data access object interface for NetworkMap persistence layer
 */
// TODO This storage abstraction needs some thought. Some of the methods clearly don't make sense e.g. setParametersUpdateStatus.
// The NetworkMapSignerTest uses a mock of this which means we need to provide methods for every trivial db operation.
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
     * Nodes should have declared that they are using correct set of parameters.
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
     *  @return The newly inserted [NetworkParametersEntity]
     */
    fun saveNetworkParameters(networkParameters: NetworkParameters, signature: DigitalSignatureWithCert?): NetworkParametersEntity

    /**
     * Save new parameters update information with corresponding network parameters. Only one parameters update entity
     * can be NEW or FLAG_DAY at any time - if one exists it will be cancelled.
     */
    fun saveNewParametersUpdate(networkParameters: NetworkParameters, description: String, updateDeadline: Instant)

    /**
     * Retrieves the latest (i.e. most recently inserted) network parameters entity
     * Note that they may not have been signed up yet.
     * @return latest network parameters entity
     */
    fun getLatestNetworkParameters(): NetworkParametersEntity?

    /** Returns the single new or flag day parameters update, or null if there isn't one. */
    fun getCurrentParametersUpdate(): ParametersUpdateEntity?

    fun setParametersUpdateStatus(update: ParametersUpdateEntity, newStatus: UpdateStatus)

    /**
     * Perform the switch of parameters on the flagDay.
     * 1. Change status of ParametersUpdateEntity to [UpdateStatus.APPLIED]
     * 2. Mark all the node infos that didn't accept the update as not current (so they won't be advertised in the network map)
     */
    fun switchFlagDay(update: ParametersUpdateEntity)
}
