package net.corda.node.services.network

import net.corda.core.crypto.parsePublicKeyBase58
import net.corda.core.crypto.toBase58String
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.ThreadBox
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.utilities.*
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import javax.persistence.*
import java.io.Serializable
import java.util.*

/**
 * A network map service backed by a database to survive restarts of the node hosting it.
 *
 * Majority of the logic is inherited from [AbstractNetworkMapService].
 *
 * This class needs database transactions to be in-flight during method calls and init, otherwise it will throw
 * exceptions.
 */
class PersistentNetworkMapService(services: ServiceHubInternal, minimumPlatformVersion: Int)
    : AbstractNetworkMapService(services, minimumPlatformVersion) {

    // Only the node_party_path column is needed to reconstruct a PartyAndCertificate but we have the others for human readability
    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}network_map_nodes")
    class NetworkNode(
            @EmbeddedId
            @Column
            var nodeParty: NodeParty = NodeParty(),

            @Lob
            @Column
            var registrationInfo: ByteArray = ByteArray(0)
    )

    @Embeddable
    data class NodeParty(
            @Column(name = "node_party_name")
            var name: String = "",

            @Column(name = "node_party_key", length = 4096)
            var owningKey: String = "", // PublicKey

            @Column(name = "node_party_certificate", length = 4096)
            var certificate: ByteArray = ByteArray(0),

            @Column(name = "node_party_path", length = 4096)
            var certPath: ByteArray = ByteArray(0)
    ): Serializable

    private companion object {
        private val factory = CertificateFactory.getInstance("X.509")

        fun createNetworkNodesMap(): PersistentMap<PartyAndCertificate, NodeRegistrationInfo, NetworkNode, NodeParty> {
            return PersistentMap(
                    toPersistentEntityKey = {  NodeParty(
                            it.name.toString(),
                            it.owningKey.toBase58String(),
                            it.certificate.encoded,
                            it.certPath.encoded
                    ) },
                    fromPersistentEntity = {
                        // TODO: We should understand an X500Name database field type, rather than manually doing the conversion ourselves
                        Pair(PartyAndCertificate(X500Name(it.nodeParty.name),
                                parsePublicKeyBase58(it.nodeParty.owningKey),
                                X509CertificateHolder(it.nodeParty.certificate),
                                factory.generateCertPath(ByteArrayInputStream(it.nodeParty.certPath))),
                                it.registrationInfo.deserialize(context = SerializationDefaults.STORAGE_CONTEXT))
                    },
                    toPersistentEntity = { key: PartyAndCertificate, value: NodeRegistrationInfo ->
                        NetworkNode().apply {
                            nodeParty = NodeParty(
                                    key.name.toString(),
                                    key.owningKey.toBase58String(),
                                    key.certificate.encoded,
                                    key.certPath.encoded
                            )
                            registrationInfo = value.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes
                        }
                    },
                    persistentEntityClass = NetworkNode::class.java
            )
        }

        fun createNetworkSubscribersMap(): PersistentMap<SingleMessageRecipient, LastAcknowledgeInfo, NetworkSubscriber, ByteArray> {
            return PersistentMap(
                    toPersistentEntityKey = { it.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes},
                    fromPersistentEntity = {
                        Pair(it.key.deserialize(context = SerializationDefaults.STORAGE_CONTEXT),
                                it.value.deserialize(context = SerializationDefaults.STORAGE_CONTEXT))
                    },
                    toPersistentEntity = { _key: SingleMessageRecipient, _value: LastAcknowledgeInfo ->
                        NetworkSubscriber().apply {
                            key = _key.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes
                            value = _value.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes
                        }
                    },
                    persistentEntityClass = NetworkSubscriber::class.java
            )
        }
    }

    override val nodeRegistrations: MutableMap<PartyAndCertificate, NodeRegistrationInfo> =
            Collections.synchronizedMap(createNetworkNodesMap())

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}network_map_subscribers")
    class NetworkSubscriber(
            @Id
            @Column(length = 4096)
            var key: ByteArray = ByteArray(0),

            @Column(length = 4096)
            var value: ByteArray = ByteArray(0)
    )

    override val subscribers = ThreadBox(createNetworkSubscribersMap())

    init {
        // Initialise the network map version with the current highest persisted version, or zero if there are no entries.
        _mapVersion.set(nodeRegistrations.values.map { it.mapVersion }.max() ?: 0)
        setup()
    }
}
