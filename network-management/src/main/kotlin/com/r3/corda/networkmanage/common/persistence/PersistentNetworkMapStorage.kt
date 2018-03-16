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
                    networkMapBytes = signedNetworkMap.raw.bytes,
                    signature = signedNetworkMap.sig.bytes,
                    certificate = signedNetworkMap.sig.by.encoded,
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
        val serialised = networkParameters.serialize()
        val hash = serialised.hash
        database.transaction {
            session.saveOrUpdate(NetworkParametersEntity(
                    parametersBytes = serialised.bytes,
                    parametersHash = hash.toString(),
                    signature = signature?.bytes,
                    certificate = signature?.by?.encoded
            ))
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

    private fun DatabaseTransaction.getNetworkParametersEntity(hash: SecureHash): NetworkParametersEntity? {
        return uniqueEntityWhere { builder, path ->
            builder.equal(path.get<String>(NetworkParametersEntity::parametersHash.name), hash.toString())
        }
    }
}
