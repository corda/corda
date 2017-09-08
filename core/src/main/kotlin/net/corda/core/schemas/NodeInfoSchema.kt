package net.corda.core.schemas

import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceEntry
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.toBase58String
import java.io.Serializable
import javax.persistence.*

object NodeInfoSchema

object NodeInfoSchemaV1 : MappedSchema(
        schemaFamily = NodeInfoSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentNodeInfo::class.java, DBPartyAndCertificate::class.java, DBHostAndPort::class.java)
) {
    @Entity
    @Table(name = "node_infos")
    class PersistentNodeInfo(
            @Id
            @GeneratedValue
            @Column(name = "node_info_id")
            var id: Int,

            @Column(name = "addresses")
            @OneToMany(cascade = arrayOf(CascadeType.ALL), orphanRemoval = true)
            val addresses: List<NodeInfoSchemaV1.DBHostAndPort>,

            @Column(name = "legal_identities_certs")
            @ManyToMany(cascade = arrayOf(CascadeType.ALL))
            @JoinTable(name = "link_nodeinfo_party",
                    joinColumns = arrayOf(JoinColumn(name="node_info_id")),
                    inverseJoinColumns = arrayOf(JoinColumn(name="party_name")))
            val legalIdentitiesAndCerts: Set<NodeInfoSchemaV1.DBPartyAndCertificate>,

            @Column(name = "platform_version")
            val platformVersion: Int,

            @Column(name = "advertised_services")
            @ElementCollection
            var advertisedServices: List<DBServiceEntry> = emptyList(),

            /**
             * serial is an increasing value which represents the version of [NodeInfo].
             * Not expected to be sequential, but later versions of the registration must have higher values
             * Similar to the serial number on DNS records.
             */
            @Column(name = "serial")
            val serial: Long
    ) {
        fun toNodeInfo(): NodeInfo {
            return NodeInfo(
                    this.addresses.map { it.toHostAndPort() },
                    this.legalIdentitiesAndCerts.filter { it.isMain }.single().toLegalIdentityAndCert(), // TODO Workaround, it will be changed after PR with services removal.
                    this.legalIdentitiesAndCerts.filter { !it.isMain }.map { it.toLegalIdentityAndCert() }.toSet(),
                    this.platformVersion,
                    this.advertisedServices.map {
                        it.serviceEntry?.deserialize<ServiceEntry>() ?: throw IllegalStateException("Service entry shouldn't be null")
                    },
                    this.serial
            )
        }
    }

    @Embeddable
    data class PKHostAndPort(
            val host: String? = null,
            val port: Int? = null
    ) : Serializable

    @Entity
    data class DBHostAndPort(
                @EmbeddedId
                private val pk: PKHostAndPort
    ) {
        companion object {
            fun fromHostAndPort(hostAndPort: NetworkHostAndPort) = DBHostAndPort(
                    PKHostAndPort(hostAndPort.host, hostAndPort.port)
            )
        }
        fun toHostAndPort(): NetworkHostAndPort {
            return NetworkHostAndPort(this.pk.host!!, this.pk.port!!)
        }
    }

    @Embeddable // TODO To be removed with services.
    data class DBServiceEntry(
            @Column(length = 65535)
            val serviceEntry: ByteArray? = null
    )

    /**
     *  PartyAndCertificate entity (to be replaced by referencing final Identity Schema).
     */
    @Entity
    @Table(name = "node_info_party_cert")
    data class DBPartyAndCertificate(
            @Id
            @Column(name = "owning_key", length = 8000, nullable = false)
            val owningKey: String,

            //@Id // TODO Do we assume that names are unique? Note: We can't have it as Id, because our toString on X500 is inconsistent.
            @Column(name = "party_name", nullable = false)
            val name: String,

            @Column(name = "party_cert_binary")
            @Lob
            val partyCertBinary: ByteArray,

            val isMain: Boolean,

            @ManyToMany(mappedBy = "legalIdentitiesAndCerts", cascade = arrayOf(CascadeType.ALL)) // ManyToMany because of distributed services.
            private val persistentNodeInfos: Set<PersistentNodeInfo> = emptySet()
    ) {
        constructor(partyAndCert: PartyAndCertificate, isMain: Boolean = false)
                : this(partyAndCert.party.owningKey.toBase58String(), partyAndCert.party.name.toString(), partyAndCert.serialize().bytes, isMain)

        fun toLegalIdentityAndCert(): PartyAndCertificate {
            return partyCertBinary.deserialize<PartyAndCertificate>()
        }
    }
}
