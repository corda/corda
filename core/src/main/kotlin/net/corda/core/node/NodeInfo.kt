package net.corda.core.node

import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.NodeInfoSchema
import net.corda.core.schemas.NodeInfoSchemaV1
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.NonEmptySet

/**
 * Information for an advertised service including the service specific identity information.
 * The identity can be used in flows and is distinct from the Node's legalIdentity
 */
@CordaSerializable
data class ServiceEntry(val info: ServiceInfo, val identity: PartyAndCertificate)

/**
 * Info about a network node that acts on behalf of some form of contract party.
 */
// TODO We currently don't support multi-IP/multi-identity nodes, we only left slots in the data structures.
@CordaSerializable
data class NodeInfo(val addresses: List<NetworkHostAndPort>,
                    val legalIdentityAndCert: PartyAndCertificate, //TODO This field will be removed in future PR which gets rid of services.
                    val legalIdentitiesAndCerts: NonEmptySet<PartyAndCertificate>,
                    val platformVersion: Int,
                    val advertisedServices: List<ServiceEntry> = emptyList(),
                    val worldMapLocation: WorldMapLocation? = null) {
    init {
        require(advertisedServices.none { it.identity == legalIdentityAndCert }) {
            "Service identities must be different from node legal identity"
        }
    }

    val legalIdentity: Party get() = legalIdentityAndCert.party
    val notaryIdentity: Party get() = advertisedServices.single { it.info.type.isNotary() }.identity.party
    fun serviceIdentities(type: ServiceType): List<Party> {
        return advertisedServices.mapNotNull { if (it.info.type.isSubTypeOf(type)) it.identity.party else null }
    }

    /** Object Relational Mapping support. */
    fun generateMappedObject(schema: MappedSchema): NodeInfoSchemaV1.PersistentNodeInfo {
        return when (schema) {
            is NodeInfoSchemaV1 -> NodeInfoSchemaV1.PersistentNodeInfo(
                    addresses = this.addresses.map { NodeInfoSchemaV1.DBHostAndPort(it) },
                    legalIdentityAndCert = this.legalIdentityAndCert.toString(),
                    legalIdentitiesAndCerts = this.legalIdentitiesAndCerts.map { it.toString() }.toSet(),
                    platformVersion = this.platformVersion,
                    advertisedServices = this.advertisedServices.map { NodeInfoSchemaV1.DBServiceEntry(it) },
                    worldMapLocation = this.worldMapLocation?.let { NodeInfoSchemaV1.DBWorldMapLocation(it) }
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }
}
