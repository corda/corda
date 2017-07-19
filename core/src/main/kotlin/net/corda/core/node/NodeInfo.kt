package net.corda.core.node

import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.NodeInfoSchemaV1
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.NonEmptySet
import net.corda.core.serialization.serialize

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
                    // TODO After removing of services these two fields will be merged together and made NonEmptySet.
                    val legalIdentityAndCert: PartyAndCertificate,
                    val legalIdentitiesAndCerts: Set<PartyAndCertificate>,
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
                    id = 0,
                    addresses = this.addresses.map { NodeInfoSchemaV1.DBHostAndPort.fromHostAndPort(it) },
                    legalIdentitiesAndCerts = this.legalIdentitiesAndCerts.map { NodeInfoSchemaV1.DBPartyAndCertificate(it) }.toSet()
                            // TODO It's workaround to keep the main identity, will be removed in future PR getting rid of services.
                            + NodeInfoSchemaV1.DBPartyAndCertificate(this.legalIdentityAndCert, isMain = true),
                    platformVersion = this.platformVersion,
                    advertisedServices = this.advertisedServices.map { NodeInfoSchemaV1.DBServiceEntry(it.serialize().bytes) },
                    worldMapLocation = this.worldMapLocation?.let { NodeInfoSchemaV1.DBWorldMapLocation(it) }
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }
}
