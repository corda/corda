package net.corda.core.schemas

import com.google.common.net.HostAndPort
import io.requery.Persistable
import net.corda.core.crypto.toBase58String
import net.corda.core.node.ServiceEntry
import net.corda.core.node.WorldMapLocation
import javax.persistence.Column
import javax.persistence.ElementCollection
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

object NodeInfoSchema
/**
 * First version of a cash contract ORM schema that maps all fields of the [Cash] contract state as it stood
 * at the time of writing.
 */
object NodeInfoSchemaV1 : MappedSchema(schemaFamily = NodeInfoSchema.javaClass, version = 1, mappedTypes = listOf(PersistentNodeInfo::class.java)) {
    @Entity
    @Table(name = "node_infos"
            // TODO just for record how the syntax looks like
//            indexes = arrayOf(
//                    Index(name = "addresses_index", columnList = "addresses"),
//                    Index(name = "legal_identity_cert_index", columnList = "legal_identity_cert"))
    )
    class PersistentNodeInfo(
            @Column(name = "addresses")
            @ElementCollection
            val addresses: List<NodeInfoSchemaV1.DBHostAndPort>,

            @Id //TODO it is stupid, id shouldn't be on that, need schema for PartyAndCertificate
            @Column(name = "legal_identity_cert")
//            val legalIdentityAndCert: PartyAndCertificate, //TODO Specify schema for PartyAndCertificate - blocked on identity work
            val legalIdentityAndCert: String,

            @Column(name = "legal_identities_certs")
            @ElementCollection // TODO ElementCollection is not good as there are problems with reconstructing the whole collection when removing elements. @OrderColumn annotation
//            val legalIdentitiesAndCerts: Set<PartyAndCertificate>,
            val legalIdentitiesAndCerts: Set<String>,

            @Column(name = "platform_version")
            val platformVersion: Int,

            @Column(name = "advertised_services")
            @ElementCollection
            var advertisedServices: List<DBServiceEntry> = emptyList(),

            @Column(name = "world_map_location", nullable = true)
            val worldMapLocation: DBWorldMapLocation?
    ) : Persistable

    @Embeddable
    class DBHostAndPort(
            val host: String,
            val port: Int
    ) {
        constructor(hostAndPort: HostAndPort): this(hostAndPort.host, hostAndPort.port)
    }

    @Embeddable
    class DBWorldMapLocation(
            val latitude: Double,
            val longitude: Double,
            val description: String,
            val countryCode: String
    ) {
        constructor(location: WorldMapLocation): this(
                location.coordinate.latitude,
                location.coordinate.longitude,
                location.description,
                location.countryCode
        )
    }

    // TODO after removing services it won't be a problem
    @Embeddable
    class DBServiceEntry(
            val type: String,
            val name: String? = null, // TODO X500Name
            val identity: String // TODO it should be PartyAndCertificate but I don't bother for now
    ) {
        constructor(serviceEntry: ServiceEntry): this(
                serviceEntry.info.type.toString(),
                serviceEntry.identity.name.toString(),
                serviceEntry.identity.owningKey.toBase58String()
        )
    }
}
