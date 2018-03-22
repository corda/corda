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
    override fun getActiveNetworkMap(): NetworkMapEntity? {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(NetworkMapEntity::class.java).run {
                from(NetworkMapEntity::class.java).run {
                    orderBy(builder.desc(get<Long>(NetworkMapEntity::version.name)))
                }
            }
            // We want the latest signed entry
            session.createQuery(query).setMaxResults(1).uniqueResult()
        }
    }

    override fun saveNewActiveNetworkMap(networkMapAndSigned: NetworkMapAndSigned) {
        val (networkMap, signedNetworkMap) = networkMapAndSigned
        database.transaction {
            val networkParametersEntity = checkNotNull(getNetworkParametersEntity(networkMap.networkParameterHash)) {
                "Network parameters ${networkMap.networkParameterHash} must be first persisted"
            }
            check(networkParametersEntity.isSigned) {
                "Network parameters ${networkMap.networkParameterHash} are not signed"
            }
            session.save(NetworkMapEntity(
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

    override fun getActiveNodeInfoHashes(): List<SecureHash> {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(String::class.java).run {
                from(NodeInfoEntity::class.java).run {
                    val certStatusExpression = get<CertificateSigningRequestEntity>(NodeInfoEntity::certificateSigningRequest.name)
                            .get<CertificateDataEntity>(CertificateSigningRequestEntity::certificateData.name)
                            .get<CertificateStatus>(CertificateDataEntity::certificateStatus.name)
                    // TODO When revoking certs, all node-infos that point to it must be made non-current. Then this check
                    // isn't needed.
                    val certStatusEq = builder.equal(certStatusExpression, CertificateStatus.VALID)
                    val isCurrentNodeInfo = builder.isTrue(get<Boolean>(NodeInfoEntity::isCurrent.name))
                    select(get<String>(NodeInfoEntity::nodeInfoHash.name)).where(builder.and(certStatusEq, isCurrentNodeInfo))
                }
            }
            session.createQuery(query).resultList.map { SecureHash.parse(it) }
        }
    }

    override fun saveNetworkParameters(networkParameters: NetworkParameters, signature: DigitalSignatureWithCert?): SecureHash {
        val serialized = networkParameters.serialize()
        signature?.verify(serialized)
        val hash = serialized.hash
        database.transaction {
            val entity = getNetworkParametersEntity(hash)
            val newNetworkParamsEntity = if (entity != null) {
                entity.copy(
                        signature = signature?.bytes,
                        certificate = signature?.by
                )
            } else {
                NetworkParametersEntity(
                        networkParameters = networkParameters,
                        hash = hash.toString(),
                        signature = signature?.bytes,
                        certificate = signature?.by
                )
            }
            session.merge(newNetworkParamsEntity)
        }
        return hash
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
            val hash = saveNetworkParameters(networkParameters, null)
            val netParamsEntity = getNetworkParametersEntity(hash)!!
            clearParametersUpdates()
            session.save(ParametersUpdateEntity(0, netParamsEntity, description, updateDeadline))
        }
    }

    override fun clearParametersUpdates() {
        database.transaction {
            val delete = "delete from ${ParametersUpdateEntity::class.java.name}"
            session.createQuery(delete).executeUpdate()
        }
    }

    override fun getParametersUpdate(): ParametersUpdateEntity? {
        return database.transaction {
            val currentParametersHash = getActiveNetworkMap()?.networkParameters?.hash
            val latestParameters = getLatestNetworkParameters()
            val criteria = session.criteriaBuilder.createQuery(ParametersUpdateEntity::class.java)
            val root = criteria.from(ParametersUpdateEntity::class.java)
            val query = criteria.select(root)
            // We just want the last entry
            val parametersUpdate = session.createQuery(query).setMaxResults(1).uniqueResult()
            check(parametersUpdate == null || latestParameters == parametersUpdate.networkParameters) {
                "ParametersUpdate doesn't correspond to latest network parameters"
            }
            // Highly unlikely, but...
            check(parametersUpdate == null || latestParameters?.hash != currentParametersHash) {
                "Having update for parameters that are already in network map"
            }
            parametersUpdate
        }
    }

    override fun setFlagDay(parametersHash: SecureHash) {
        database.transaction {
            val parametersUpdateEntity = getParametersUpdate() ?: throw IllegalArgumentException("Setting flag day but no parameters update to switch to")
            if (parametersHash.toString() != parametersUpdateEntity.networkParameters.hash) {
                throw IllegalArgumentException("Setting flag day for parameters: $parametersHash, but in database we have update for: ${parametersUpdateEntity.networkParameters.hash}")
            }
            session.merge(parametersUpdateEntity.copy(flagDay = true))
        }
    }
}

internal fun DatabaseTransaction.getNetworkParametersEntity(hash: SecureHash): NetworkParametersEntity? {
    return uniqueEntityWhere { builder, path ->
        builder.equal(path.get<String>(NetworkParametersEntity::hash.name), hash.toString())
    }
}
