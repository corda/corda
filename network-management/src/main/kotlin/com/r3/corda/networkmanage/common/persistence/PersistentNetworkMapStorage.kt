package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.persistence.entity.NetworkMapEntity
import com.r3.corda.networkmanage.common.persistence.entity.NetworkParametersEntity
import com.r3.corda.networkmanage.common.persistence.entity.NodeInfoEntity
import com.r3.corda.networkmanage.common.signer.NetworkMap
import com.r3.corda.networkmanage.common.signer.SignedNetworkMap
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.serialize
import net.corda.node.utilities.CordaPersistence
import org.hibernate.Session
import org.hibernate.jpa.QueryHints

/**
 * Database implementation of the [NetworkMapStorage] interface
 */
class PersistentNetworkMapStorage(private val database: CordaPersistence) : NetworkMapStorage {
    override fun getCurrentNetworkMap(): SignedNetworkMap = database.transaction {
        val networkMapEntity = getCurrentNetworkMapEntity(getNetworkMapWithNodeInfoAndParametersHint(session))
        networkMapEntity ?: throw NoSuchElementException("Current Network Map does not exist.")
        val nodeInfoHashes = networkMapEntity.nodeInfoList.map { it.nodeInfoHash }
        val networkParameterHash = networkMapEntity.parameters.parametersHash
        val signatureAndCertPath = networkMapEntity.signatureAndCertificate()
        SignedNetworkMap(NetworkMap(nodeInfoHashes, networkParameterHash), signatureAndCertPath!!)
    }

    override fun getCurrentNetworkParameters(): NetworkParameters = database.transaction {
        val networkMapEntity = getCurrentNetworkMapEntity(getNetworkMapWithParametersHint(session))
        if (networkMapEntity != null) {
            networkMapEntity.parameters.networkParameters()
        } else {
            throw NoSuchElementException("Current Network Parameters do not exist.")
        }
    }

    override fun saveNetworkMap(signedNetworkMap: SignedNetworkMap) {
        database.transaction {
            val networkMap = signedNetworkMap.networkMap
            val signatureAndCertPath = signedNetworkMap.signatureData
            val signature = signatureAndCertPath.signature
            val networkParametersEntity = getNetworkParametersEntity(networkMap.parametersHash.toString())
            networkParametersEntity ?: throw IllegalArgumentException("Error when retrieving network parameters entity for network map signing! - Entity does not exist")
            val networkMapEntity = NetworkMapEntity(
                    parameters = networkParametersEntity,
                    signatureBytes = signature.bytes,
                    certificatePathBytes = signatureAndCertPath.certPath.serialize().bytes
            )
            session.save(networkMapEntity)
            networkMap.nodeInfoHashes.forEach {
                val nodeInfoEntity = session.find(NodeInfoEntity::class.java, it)
                session.merge(nodeInfoEntity.copy(networkMap = networkMapEntity))
            }
        }
    }

    override fun getNetworkParameters(parameterHash: SecureHash): NetworkParameters {
        val entity = getNetworkParametersEntity(parameterHash.toString())
        if (entity != null) {
            return entity.networkParameters()
        } else {
            throw NoSuchElementException("Network parameters with $parameterHash do not exist")
        }
    }

    override fun getCurrentNetworkMapNodeInfoHashes(certificateStatuses: List<CertificateStatus>): List<SecureHash> = database.transaction {
        val networkMapEntity = getCurrentNetworkMapEntity(getNetworkMapWithNodeInfoAndCsrHint(session))
        if (networkMapEntity != null) {
            networkMapEntity.nodeInfoList.filter({
                certificateStatuses.isEmpty() || certificateStatuses.contains(it.certificateSigningRequest?.certificateData?.certificateStatus)
            }).map { SecureHash.parse(it.nodeInfoHash) }
        } else {
            emptyList()
        }
    }

    override fun putNetworkParameters(networkParameters: NetworkParameters): SecureHash = database.transaction {
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

    override fun getDetachedAndValidNodeInfoHashes(): List<SecureHash> = database.transaction {
        val builder = session.criteriaBuilder
        // Get signed NodeInfoEntities
        val query = builder.createQuery(NodeInfoEntity::class.java).run {
            from(NodeInfoEntity::class.java).run {
                where(builder.and(
                        builder.isNull(get<ByteArray>(NodeInfoEntity::networkMap.name)),
                        builder.isNotNull(get<ByteArray>(NodeInfoEntity::signatureBytes.name))))
            }
        }
        session.createQuery(query).resultList.map { SecureHash.parse(it.nodeInfoHash) }
    }

    private fun getCurrentNetworkMapEntity(hint: Pair<String, Any>): NetworkMapEntity? = database.transaction {
        val builder = session.criteriaBuilder
        val query = builder.createQuery(NetworkMapEntity::class.java).run {
            from(NetworkMapEntity::class.java).run {
                where(builder.isNotNull(get<ByteArray?>(NetworkMapEntity::signatureBytes.name)))
                orderBy(builder.desc(get<String>(NetworkMapEntity::version.name)))
            }
        }
        // We just want the last signed entry
        session.createQuery(query).setHint(hint.first, hint.second).resultList.firstOrNull()
    }

    private fun getNetworkParametersEntity(parameterHash: String): NetworkParametersEntity? = database.transaction {
        singleRequestWhere(NetworkParametersEntity::class.java) { builder, path ->
            builder.equal(path.get<String>(NetworkParametersEntity::parametersHash.name), parameterHash)
        }
    }

    /**
     * Creates Hibernate query hint for pulling [NetworkParametersEntity] when querying for [NetworkMapEntity]
     */
    private fun getNetworkMapWithParametersHint(session: Session): Pair<String, Any> {
        val graph = session.createEntityGraph(NetworkMapEntity::class.java)
        graph.addAttributeNodes(NetworkMapEntity::parameters.name)
        return QueryHints.HINT_LOADGRAPH to graph
    }

    /**
     * Creates Hibernate query hint for pulling [NodeInfoEntity] and [CertificateSigningRequestEntity] when querying for [NetworkMapEntity]
     */
    private fun getNetworkMapWithNodeInfoAndCsrHint(session: Session): Pair<String, Any> {
        val graph = session.createEntityGraph(NetworkMapEntity::class.java)
        val subGraph = graph.addSubgraph(NetworkMapEntity::nodeInfoList.name, NodeInfoEntity::class.java)
        subGraph.addAttributeNodes(NodeInfoEntity::certificateSigningRequest.name)
        return QueryHints.HINT_LOADGRAPH to graph
    }

    /**
     * Creates Hibernate query hint for pulling [NodeInfoEntity] and [NetworkParametersEntity] when querying for [NetworkMapEntity]
     */
    private fun getNetworkMapWithNodeInfoAndParametersHint(session: Session): Pair<String, Any> {
        val graph = session.createEntityGraph(NetworkMapEntity::class.java)
        graph.addAttributeNodes(NetworkMapEntity::nodeInfoList.name)
        graph.addAttributeNodes(NetworkMapEntity::parameters.name)
        return QueryHints.HINT_LOADGRAPH to graph
    }
}