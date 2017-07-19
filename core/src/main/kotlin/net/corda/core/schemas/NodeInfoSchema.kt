package net.corda.core.schemas

import net.corda.core.crypto.parsePublicKeyBase58
import net.corda.core.crypto.toBase58String
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceEntry
import net.corda.core.node.WorldCoordinate
import net.corda.core.node.WorldMapLocation
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import java.io.Serializable
import java.security.cert.CertPath
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.ElementCollection
import javax.persistence.Embeddable
import javax.persistence.Embedded
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.Lob
import javax.persistence.ManyToMany
import javax.persistence.Table

object NodeInfoSchema

object NodeInfoSchemaV1 : MappedSchema(
        schemaFamily = NodeInfoSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentNodeInfo::class.java, DBPartyAndCertificate::class.java)
) {
    @Entity
    @Table(name = "node_infos")
    class PersistentNodeInfo(
            @Id
            @GeneratedValue
            @Column(name = "node_info_id")
            var id: Int,

            // TODO bidirectional OneToMany, or just composite id
            @Column(name = "addresses")
            @ElementCollection
            @Embedded
            val addresses: List<NodeInfoSchemaV1.DBHostAndPort>,

            @Column(name = "legal_identities_certs")
            @ManyToMany(cascade = arrayOf(CascadeType.ALL))
            val legalIdentitiesAndCerts: Set<DBPartyAndCertificate>,

            @Column(name = "platform_version")
            val platformVersion: Int,

            @Column(name = "advertised_services")
            @ElementCollection
            var advertisedServices: List<DBServiceEntry> = emptyList(),

            @Column(name = "world_map_location", nullable = true)
            val worldMapLocation: DBWorldMapLocation?
    ) : Serializable {
        fun toNodeInfo(): NodeInfo {
            return NodeInfo(
                    this.addresses.map { it.toHostAndPort() },
                    this.legalIdentitiesAndCerts.filter { it.isMain }.single().toLegalIdentityAndCert(), // TODO Workaround, it will be changed after PR with services removal.
                    this.legalIdentitiesAndCerts.filter { !it.isMain }.map { it.toLegalIdentityAndCert() }.toSet(),
                    this.platformVersion,
                    this.advertisedServices.map {
                        it.serviceEntry?.deserialize<ServiceEntry>() ?: throw IllegalStateException("Service entry shouldn't be null") },
                    this.worldMapLocation?.toWorldMapLocation())
        }
    }

    @Embeddable
    class DBHostAndPort(
            val host: String?,
            val port: Int?
    ) {
        constructor(): this(null, null)
        companion object {
            fun fromHostAndPort(hostAndPort: NetworkHostAndPort) = DBHostAndPort(
                    hostAndPort.host,
                    hostAndPort.port
            )
        }
        fun toHostAndPort(): NetworkHostAndPort {
            return NetworkHostAndPort(this.host!!, this.port!!)
        }
    }

    @Embeddable
    class DBServiceEntry(
            @Column(length = 65535)
            val serviceEntry: ByteArray?
    ) {
        constructor(): this(null)
    }

    // TODO It's probably not worth storing this way.
    @Embeddable
    class DBWorldMapLocation(
            val latitude: Double?,
            val longitude: Double?,
            val description: String?,
            val countryCode: String?
    ) {
        constructor(): this(null, null, null, null)
        constructor(location: WorldMapLocation): this(
                location.coordinate.latitude,
                location.coordinate.longitude,
                location.description,
                location.countryCode
        )
        fun toWorldMapLocation(): WorldMapLocation {
            if (latitude == null || longitude == null || description == null || countryCode == null)
                throw IllegalStateException("Any entry in WorldMapLocation shouldn't be null")
            else
                return WorldMapLocation(WorldCoordinate(latitude, longitude), description, countryCode)
        }
    }

    /**
     *  PartyAndCertificate entity (to be replaced by referencing final Identity Schema).
     */
    @Entity
    @Table(name = "node_info_party_cert")
            //indexes = arrayOf(Index(name = "party_cert_idx", columnList = "party_name")))
    class DBPartyAndCertificate(
            @Column(name = "owning_key", length = 65535, nullable = false)
            val owningKey: String,

            @Id // TODO Do we assume that names are unique?
            @Column(name = "party_name", nullable = false)
            val name: String,

            @Column(name = "certificate")
            @Lob
            val certificate: ByteArray,

            @Column(name = "certificate_path")
            @Lob
            val certPath: ByteArray,

            val isMain: Boolean,

            @ManyToMany(mappedBy = "legalIdentitiesAndCerts") // ManyToMany because of distributed services.
            private val persistentNodeInfos: Set<PersistentNodeInfo> = emptySet()
    ) {
        constructor(partyAndCert: PartyAndCertificate, isMain: Boolean = false)
                : this(partyAndCert.party.owningKey.toBase58String(), partyAndCert.party.name.toString(), partyAndCert.certificate.serialize().bytes, partyAndCert.certPath.serialize().bytes, isMain)
        fun toLegalIdentityAndCert(): PartyAndCertificate {
            return PartyAndCertificate(
                    Party(X500Name(name),
                            parsePublicKeyBase58(owningKey)),
                    certificate.deserialize<X509CertificateHolder>(),
                    certPath.deserialize<CertPath>()
            )
        }
    }
}
