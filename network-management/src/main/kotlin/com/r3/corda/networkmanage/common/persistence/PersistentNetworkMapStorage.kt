package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.persistence.entity.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.NetworkMap
import net.corda.nodeapi.internal.NetworkParameters
import net.corda.nodeapi.internal.SignedNetworkMap
import net.corda.nodeapi.internal.persistence.CordaPersistence

/**
 * Database implementation of the [NetworkMapStorage] interface
 */
class PersistentNetworkMapStorage(private val database: CordaPersistence) : NetworkMapStorage {
    override fun getCurrentNetworkMap(): SignedNetworkMap? = database.transaction {
        val networkMapEntity = getCurrentNetworkMapEntity()
        networkMapEntity?.let {
            val signatureAndCertPath = it.signatureAndCertificate()
            SignedNetworkMap(SerializedBytes(it.networkMap), signatureAndCertPath)
        }
    }

    override fun getCurrentNetworkParameters(): NetworkParameters? = database.transaction {
        getCurrentNetworkMapEntity()?.let {
            val parameterHash = it.networkMap.deserialize<NetworkMap>().networkParameterHash
            getNetworkParameters(parameterHash)
        }
    }

    override fun saveNetworkMap(signedNetworkMap: SignedNetworkMap) {
        database.transaction {
            val networkMapEntity = NetworkMapEntity(
                    networkMap = signedNetworkMap.raw.bytes,
                    signature = signedNetworkMap.sig.signatureBytes,
                    certificate = signedNetworkMap.sig.by.encoded
            )
            session.save(networkMapEntity)
        }
    }

    override fun getNetworkParameters(parameterHash: SecureHash): NetworkParameters? {
        return getNetworkParametersEntity(parameterHash.toString())?.networkParameters()
    }

    override fun getNodeInfoHashes(certificateStatus: CertificateStatus): List<SecureHash> = database.transaction {
        val builder = session.criteriaBuilder
        val query = builder.createQuery(String::class.java).run {
            from(NodeInfoEntity::class.java).run {
                select(get<String>(NodeInfoEntity::nodeInfoHash.name))
                        .where(builder.equal(get<CertificateSigningRequestEntity>(NodeInfoEntity::certificateSigningRequest.name)
                                .get<CertificateDataEntity>(CertificateSigningRequestEntity::certificateData.name)
                                .get<CertificateStatus>(CertificateDataEntity::certificateStatus.name), certificateStatus))
            }
        }
        session.createQuery(query).resultList.map { SecureHash.parse(it) }
    }

    override fun saveNetworkParameters(networkParameters: NetworkParameters): SecureHash = database.transaction {
        val bytes = networkParameters.serialize().bytes
        val hash = bytes.sha256()
        session.save(NetworkParametersEntity(
                parametersBytes = bytes,
                parametersHash = hash.toString()
        ))
        hash
    }

    override fun getLatestNetworkParameters(): NetworkParameters = getLatestNetworkParametersEntity().networkParameters()

    private fun getLatestNetworkParametersEntity(): NetworkParametersEntity = database.transaction {
        val builder = session.criteriaBuilder
        val query = builder.createQuery(NetworkParametersEntity::class.java).run {
            from(NetworkParametersEntity::class.java).run {
                orderBy(builder.desc(get<String>(NetworkParametersEntity::version.name)))
            }
        }
        // We just want the last signed entry
        session.createQuery(query).resultList.first()
    }

    private fun getCurrentNetworkMapEntity(): NetworkMapEntity? = database.transaction {
        val builder = session.criteriaBuilder
        val query = builder.createQuery(NetworkMapEntity::class.java).run {
            from(NetworkMapEntity::class.java).run {
                where(builder.isNotNull(get<ByteArray?>(NetworkMapEntity::signature.name)))
                orderBy(builder.desc(get<String>(NetworkMapEntity::version.name)))
            }
        }
        // We just want the last signed entry
        session.createQuery(query).resultList.firstOrNull()
    }

    private fun getNetworkParametersEntity(parameterHash: String): NetworkParametersEntity? = database.transaction {
        singleRequestWhere(NetworkParametersEntity::class.java) { builder, path ->
            builder.equal(path.get<String>(NetworkParametersEntity::parametersHash.name), parameterHash)
        }
    }
}
