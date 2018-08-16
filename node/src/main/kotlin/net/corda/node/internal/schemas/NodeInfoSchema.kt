package net.corda.node.internal.schemas

import net.corda.core.crypto.toStringShort
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.NodeInfo
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.persistence.NodePropertiesPersistentStore
import javax.persistence.*

object NodeInfoSchema

object NodeInfoSchemaV1 : MappedSchema(
        schemaFamily = NodeInfoSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentNodeInfo::class.java, DBPartyAndCertificate::class.java, DBHostAndPort::class.java, NodePropertiesPersistentStore.DBNodeProperty::class.java)
) {
    override val migrationResource = "node-info.changelog-master"

    @Entity
    @Table(name = "node_infos")
    class PersistentNodeInfo(
            @Id
            @GeneratedValue
            @Column(name = "node_info_id", nullable = false)
            var id: Int,

            @Column(name = "node_info_hash", length = 64, nullable = false)
            val hash: String,

            @Column(name = "addresses", nullable = false)
            @OneToMany(cascade = [(CascadeType.ALL)], orphanRemoval = true)
            @JoinColumn(name = "node_info_id", foreignKey = ForeignKey(name = "FK__info_hosts__infos"))
            val addresses: List<DBHostAndPort>,

            @Column(name = "legal_identities_certs", nullable = false)
            @ManyToMany(cascade = [(CascadeType.ALL)])
            @JoinTable(name = "node_link_nodeinfo_party",
                    joinColumns = [(JoinColumn(name = "node_info_id", foreignKey = ForeignKey(name = "FK__link_nodeinfo_party__infos")))],
                    inverseJoinColumns = [(JoinColumn(name = "party_name", foreignKey = ForeignKey(name = "FK__link_ni_p__info_p_cert")))])
            val legalIdentitiesAndCerts: List<DBPartyAndCertificate>,

            @Column(name = "platform_version", nullable = false)
            val platformVersion: Int,

            /**
             * serial is an increasing value which represents the version of [NodeInfo].
             * Not expected to be sequential, but later versions of the registration must have higher values
             * Similar to the serial number on DNS records.
             */
            @Column(name = "serial", nullable = false)
            val serial: Long
    ) {
        fun toNodeInfo(): NodeInfo {
            return NodeInfo(
                    this.addresses.map { it.toHostAndPort() },
                    (this.legalIdentitiesAndCerts.filter { it.isMain } + this.legalIdentitiesAndCerts.filter { !it.isMain }).map { it.toLegalIdentityAndCert() },
                    this.platformVersion,
                    this.serial
            )
        }
    }

    @Entity
    @Table(name = "node_info_hosts")
    data class DBHostAndPort(
            @Id
            @GeneratedValue
            @Column(name = "hosts_id", nullable = false)
            var id: Int,
            val host: String? = null,
            val port: Int? = null
    ) {
        companion object {
            fun fromHostAndPort(hostAndPort: NetworkHostAndPort) = DBHostAndPort(
                    0, hostAndPort.host, hostAndPort.port
            )
        }

        fun toHostAndPort(): NetworkHostAndPort {
            return NetworkHostAndPort(host!!, port!!)
        }
    }

    /**
     *  PartyAndCertificate entity (to be replaced by referencing final Identity Schema).
     */
    @Entity
    @Table(name = "node_info_party_cert")
    data class DBPartyAndCertificate(
            @Id
            @Column(name = "party_name", nullable = false)
            val name: String,

            @Column(name = "owning_key_hash", length = MAX_HASH_HEX_SIZE, nullable = false)
            val owningKeyHash: String,

            @Lob
            @Column(name = "party_cert_binary", nullable = false)
            val partyCertBinary: ByteArray,

            val isMain: Boolean,

            @ManyToMany(mappedBy = "legalIdentitiesAndCerts", cascade = [(CascadeType.ALL)]) // ManyToMany because of distributed services.
            private val persistentNodeInfos: Set<PersistentNodeInfo> = emptySet()
    ) {
        constructor(partyAndCert: PartyAndCertificate, isMain: Boolean = false)
                : this(partyAndCert.name.toString(),
                partyAndCert.party.owningKey.toStringShort(),
                partyAndCert.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes, isMain)

        fun toLegalIdentityAndCert(): PartyAndCertificate {
            return partyCertBinary.deserialize()
        }
    }
}
