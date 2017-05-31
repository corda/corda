package net.corda.core.node

import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

/**
 * Information for an advertised service including the service specific identity information.
 * The identity can be used in flows and is distinct from the Node's legalIdentity
 */
@CordaSerializable
data class ServiceEntry(val info: ServiceInfo, val identity: PartyAndCertificate)

/**
 * Info about a network node that acts on behalf of some form of contract party.
 */
@CordaSerializable
data class NodeInfo(val address: SingleMessageRecipient,
                    val legalIdentity: PartyAndCertificate,
                    val platformVersion: Int,
                    var advertisedServices: List<ServiceEntry> = emptyList(),
                    val physicalLocation: PhysicalLocation? = null) {
    init {
        require(advertisedServices.none { it.identity == legalIdentity }) { "Service identities must be different from node legal identity" }
    }

    val notaryIdentity: PartyAndCertificate get() = advertisedServices.single { it.info.type.isNotary() }.identity
    fun serviceIdentities(type: ServiceType): List<Party> = advertisedServices.filter { it.info.type.isSubTypeOf(type) }.map { it.identity }
}
