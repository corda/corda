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
import com.r3.corda.networkmanage.common.utils.SignedNetworkMap
import com.r3.corda.networkmanage.common.utils.SignedNetworkParameters
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.persistence.CordaPersistence

/**
 * Database implementation of the [NetworkMapStorage] interface
 */
class PersistentNetworkMapStorage(private val database: CordaPersistence) : NetworkMapStorage {
    override fun getCurrentNetworkMap(): SignedNetworkMap? {
        return database.transaction {
            getCurrentNetworkMapEntity()?.toSignedNetworkMap()
        }
    }

    override fun getNetworkParametersOfNetworkMap(): SignedNetworkParameters? {
        return database.transaction {
            getCurrentNetworkMapEntity()?.let {
                val netParamsHash = it.toNetworkMap().networkParameterHash
                getSignedNetworkParameters(netParamsHash) ?:
                        throw IllegalStateException("Current network map is pointing to network parameters that do not exist: $netParamsHash")
            }
        }
    }

    override fun saveNetworkMap(signedNetworkMap: SignedNetworkMap) {
        database.transaction {
            val networkMapEntity = NetworkMapEntity(
                    networkMap = signedNetworkMap.raw.bytes,
                    signature = signedNetworkMap.sig.bytes,
                    certificate = signedNetworkMap.sig.by.encoded
            )
            session.save(networkMapEntity)
        }
    }

    override fun getSignedNetworkParameters(hash: SecureHash): SignedNetworkParameters? {
        return getNetworkParametersEntity(hash.toString())?.let {
            if (it.isSigned) it.toSignedNetworkParameters() else null
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
                    val certStatusEq = builder.equal(certStatusExpression, CertificateStatus.VALID)
                    val isCurrentNodeInfo = builder.isTrue(get<Boolean>(NodeInfoEntity::isCurrent.name))
                    select(get<String>(NodeInfoEntity::nodeInfoHash.name)).where(builder.and(certStatusEq, isCurrentNodeInfo))
                }
            }
            session.createQuery(query).resultList.map { SecureHash.parse(it) }
        }
    }

    override fun saveNetworkParameters(networkParameters: NetworkParameters, sig: DigitalSignatureWithCert?): SecureHash {
        return database.transaction {
            val bytes = networkParameters.serialize().bytes
            val hash = bytes.sha256()
            session.saveOrUpdate(NetworkParametersEntity(
                    parametersBytes = bytes,
                    parametersHash = hash.toString(),
                    signature = sig?.bytes,
                    certificate = sig?.by?.encoded
            ))
            hash
        }
    }

    override fun getLatestNetworkParameters(): NetworkParameters? {
        return database.transaction {
            val query = session.criteriaBuilder.run {
                createQuery(NetworkParametersEntity::class.java).run {
                    from(NetworkParametersEntity::class.java).run {
                        orderBy(desc(get<String>(NetworkParametersEntity::created.name)))
                    }
                }
            }
            // We just want the last entry
            session.createQuery(query).setMaxResults(1).uniqueResult()?.toNetworkParameters()
        }
    }

    private fun getCurrentNetworkMapEntity(): NetworkMapEntity? {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(NetworkMapEntity::class.java).run {
                from(NetworkMapEntity::class.java).run {
                    // TODO a limit of 1 since we only need the first result
                    where(builder.isNotNull(get<ByteArray?>(NetworkMapEntity::signature.name)))
                    orderBy(builder.desc(get<String>(NetworkMapEntity::version.name)))
                }
            }
            // We just want the last signed entry
            session.createQuery(query).resultList.firstOrNull()
        }
    }

    private fun getNetworkParametersEntity(parameterHash: String): NetworkParametersEntity? {
        return database.transaction {
            singleEntityWhere { builder, path ->
                builder.equal(path.get<String>(NetworkParametersEntity::parametersHash.name), parameterHash)
            }
        }
    }
}
