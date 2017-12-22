package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.persistence.entity.*
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.sha256
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.NetworkParameters
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.persistence.CordaPersistence

/**
 * Database implementation of the [NetworkMapStorage] interface
 */
class PersistentNetworkMapStorage(private val database: CordaPersistence, private val localSigner: LocalSigner?) : NetworkMapStorage {
    override fun getCurrentNetworkMap(): SignedNetworkMap? {
        return database.transaction {
            getCurrentNetworkMapEntity()?.let {
                val signatureAndCertPath = it.signatureAndCertificate()
                SignedNetworkMap(SerializedBytes(it.networkMap), signatureAndCertPath)
            }
        }
    }

    override fun getCurrentNetworkParameters(): NetworkParameters? {
        return database.transaction {
            getCurrentNetworkMapEntity()?.let {
                val netParamsHash = it.networkMap.deserialize<NetworkMap>().networkParameterHash
                getNetworkParametersEntity(netParamsHash.toString())?.networkParameters()
            }
        }
    }

    override fun saveNetworkMap(signedNetworkMap: SignedNetworkMap) {
        database.transaction {
            val networkMapEntity = NetworkMapEntity(
                    networkMap = signedNetworkMap.raw.bytes,
                    signature = signedNetworkMap.signature.signatureBytes,
                    certificate = signedNetworkMap.signature.by.encoded
            )
            session.save(networkMapEntity)
        }
    }

    // TODO The signing cannot occur here as it won't work with an HSM. The signed network parameters needs to be persisted
    // into the database.
    override fun getSignedNetworkParameters(hash: SecureHash): SignedData<NetworkParameters>? {
        val netParamsBytes = getNetworkParametersEntity(hash.toString())?.parametersBytes ?: return null
        val sigWithCert = localSigner!!.sign(netParamsBytes)
        return SignedData(SerializedBytes(netParamsBytes), DigitalSignature.WithKey(sigWithCert.by.publicKey, sigWithCert.signatureBytes))
    }

    override fun getNodeInfoHashes(certificateStatus: CertificateStatus): List<SecureHash> {
        return database.transaction {
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
    }

    override fun saveNetworkParameters(networkParameters: NetworkParameters): SecureHash {
        return database.transaction {
            val bytes = networkParameters.serialize().bytes
            val hash = bytes.sha256()
            session.save(NetworkParametersEntity(
                    parametersBytes = bytes,
                    parametersHash = hash.toString()
            ))
            hash
        }
    }

    override fun getLatestNetworkParameters(): NetworkParameters = getLatestNetworkParametersEntity().networkParameters()

    private fun getLatestNetworkParametersEntity(): NetworkParametersEntity {
        return database.transaction {
            val builder = session.criteriaBuilder
            val query = builder.createQuery(NetworkParametersEntity::class.java).run {
                from(NetworkParametersEntity::class.java).run {
                    orderBy(builder.desc(get<String>(NetworkParametersEntity::version.name)))
                }
            }
            // We just want the last signed entry
            session.createQuery(query).resultList.first()
        }
    }

    private fun getCurrentNetworkMapEntity(): NetworkMapEntity? {
        return database.transaction {
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
    }

    private fun getNetworkParametersEntity(parameterHash: String): NetworkParametersEntity? {
        return database.transaction {
            singleRequestWhere(NetworkParametersEntity::class.java) { builder, path ->
                builder.equal(path.get<String>(NetworkParametersEntity::parametersHash.name), parameterHash)
            }
        }
    }
}
