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
import com.r3.corda.networkmanage.common.utils.logger
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.network.NetworkMapAndSigned
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import java.time.Instant

/**
 * Database implementation of the [NetworkMapStorage] interface
 */
class PersistentNetworkMapStorage(private val database: CordaPersistence) : NetworkMapStorage {
    companion object {
        // Used internally to identify global network map in database table.
        private const val PUBLIC_NETWORK_ID = "PUBLIC_NETWORK"
    }

    override fun getNetworkMaps(): NetworkMaps {
        return database.transaction {
            val networkMapEntities = session.createQuery("from ${NetworkMapEntity::class.java.name}", NetworkMapEntity::class.java)
                    .resultList
                    .associateBy { it.id }
            NetworkMaps(networkMapEntities[PUBLIC_NETWORK_ID], networkMapEntities.filterKeys { it != PUBLIC_NETWORK_ID })
        }
    }

    override fun saveNewNetworkMap(networkId: String?, networkMapAndSigned: NetworkMapAndSigned) {
        val (networkMap, signedNetworkMap) = networkMapAndSigned
        database.transaction {
            val networkParametersEntity = checkNotNull(getNetworkParametersEntity(networkMap.networkParameterHash)) {
                "Network parameters ${networkMap.networkParameterHash} must be first persisted"
            }
            check(networkParametersEntity.isSigned) {
                "Network parameters ${networkMap.networkParameterHash} are not signed"
            }
            session.merge(NetworkMapEntity(
                    id = networkId ?: PUBLIC_NETWORK_ID,
                    networkMap = networkMap,
                    signature = signedNetworkMap.sig.bytes,
                    certificate = signedNetworkMap.sig.by,
                    networkParameters = networkParametersEntity
            ))
        }
    }

    override fun getSignedNetworkParameters(hash: SecureHash): SignedNetworkParameters? {
        return database.transaction {
            getNetworkParametersEntity(hash)?.let {
                if (it.isSigned) it.toSignedNetworkParameters() else null
            }
        }
    }

    override fun getNodeInfoHashes(): NodeInfoHashes {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createTupleQuery().run {
                from(NodeInfoEntity::class.java).run {
                    val certStatusExpression = get<CertificateSigningRequestEntity>(NodeInfoEntity::certificateSigningRequest.name)
                            .get<CertificateDataEntity>(CertificateSigningRequestEntity::certificateData.name)
                            .get<CertificateStatus>(CertificateDataEntity::certificateStatus.name)
                    // TODO When revoking certs, all node-infos that point to it must be made non-current. Then this check
                    // isn't needed.
                    val certStatusEq = builder.equal(certStatusExpression, CertificateStatus.VALID)
                    val isCurrentNodeInfo = builder.isTrue(get<Boolean>(NodeInfoEntity::isCurrent.name))

                    val networkIdSelector = get<CertificateSigningRequestEntity>(NodeInfoEntity::certificateSigningRequest.name)
                            .get<PrivateNetworkEntity>(CertificateSigningRequestEntity::privateNetwork.name)
                            .get<String>(PrivateNetworkEntity::networkId.name)

                    multiselect(networkIdSelector, get<String>(NodeInfoEntity::nodeInfoHash.name)).where(builder.and(certStatusEq, isCurrentNodeInfo))
                }
            }
            val allNodeInfos = session.createQuery(query).resultList.groupBy { it[0]?.toString() ?: PUBLIC_NETWORK_ID }.mapValues { it.value.map { SecureHash.parse(it.get(1, String::class.java)) } }
            NodeInfoHashes(allNodeInfos[PUBLIC_NETWORK_ID] ?: emptyList(), allNodeInfos.filterKeys { it != PUBLIC_NETWORK_ID })
        }
    }

    override fun saveNetworkParameters(networkParameters: NetworkParameters, signature: DigitalSignatureWithCert?): NetworkParametersEntity {
        val serialized = networkParameters.serialize()
        signature?.verify(serialized)
        val hash = serialized.hash
        return database.transaction {
            val entity = getNetworkParametersEntity(hash)
            val newNetworkParamsEntity = entity?.copy(
                    signature = signature?.bytes,
                    certificate = signature?.by
            ) ?: NetworkParametersEntity(
                    networkParameters = networkParameters,
                    hash = hash.toString(),
                    signature = signature?.bytes,
                    certificate = signature?.by
            )
            session.merge(newNetworkParamsEntity) as NetworkParametersEntity
        }
    }

    override fun getLatestNetworkParameters(): NetworkParametersEntity? {
        return database.transaction {
            val query = session.criteriaBuilder.run {
                createQuery(NetworkParametersEntity::class.java).run {
                    from(NetworkParametersEntity::class.java).run {
                        orderBy(desc(get<String>(NetworkParametersEntity::created.name)))
                    }
                }
            }
            // We just want the last entry
            session.createQuery(query).setMaxResults(1).uniqueResult()
        }
    }

    override fun saveNewParametersUpdate(networkParameters: NetworkParameters, description: String, updateDeadline: Instant) {
        database.transaction {
            val existingUpdate = getCurrentParametersUpdate()
            if (existingUpdate != null) {
                logger.info("Cancelling existing update: $existingUpdate")
                session.merge(existingUpdate.copy(status = UpdateStatus.CANCELLED))
            }
            val netParamsEntity = saveNetworkParameters(networkParameters, null)
            session.save(ParametersUpdateEntity(
                    networkParameters = netParamsEntity,
                    description = description,
                    updateDeadline = updateDeadline
            ))
        }
    }

    override fun getCurrentParametersUpdate(): ParametersUpdateEntity? {
        return database.transaction {
            val newParamsUpdates = session.fromQuery<ParametersUpdateEntity>("u where u.status in :statuses")
                    .setParameterList("statuses", listOf(UpdateStatus.NEW, UpdateStatus.FLAG_DAY))
                    .resultList
            when (newParamsUpdates.size) {
                0 -> null
                1 -> newParamsUpdates[0]
                else -> throw IllegalStateException("More than one update found: $newParamsUpdates")
            }
        }
    }

    override fun setParametersUpdateStatus(update: ParametersUpdateEntity, newStatus: UpdateStatus) {
        require(newStatus != UpdateStatus.NEW)
        database.transaction {
            session.merge(update.copy(status = newStatus))
        }
    }

    override fun switchFlagDay(update: ParametersUpdateEntity) {
        database.transaction {
            setParametersUpdateStatus(update, UpdateStatus.APPLIED)
            session.createQuery("update ${NodeInfoEntity::class.java.name} n set n.isCurrent = false " +
                    "where (n.acceptedParametersUpdate != :acceptedParamUp or n.acceptedParametersUpdate is null) and n.isCurrent = true")
                    .setParameter("acceptedParamUp", update).executeUpdate()
        }
    }
}

internal fun DatabaseTransaction.getNetworkParametersEntity(hash: SecureHash): NetworkParametersEntity? {
    return uniqueEntityWhere { builder, path ->
        builder.equal(path.get<String>(NetworkParametersEntity::hash.name), hash.toString())
    }
}
