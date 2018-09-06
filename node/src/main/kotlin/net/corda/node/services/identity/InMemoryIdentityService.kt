package net.corda.node.services.identity

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.nodeapi.internal.crypto.x509Certificates
import java.security.InvalidAlgorithmParameterException
import java.security.PublicKey
import java.security.cert.*
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.ThreadSafe

/**
 * Simple identity service which caches parties and provides functionality for efficient lookup.
 *
 * @param identities initial set of identities for the service, typically only used for unit tests.
 */
@ThreadSafe
class InMemoryIdentityService(identities: List<PartyAndCertificate> = emptyList(),
                              override val trustRoot: X509Certificate) : SingletonSerializeAsToken(), IdentityServiceInternal {
    companion object {
        private val log = contextLogger()
    }

    /**
     * Certificate store for certificate authority and intermediary certificates.
     */
    override val caCertStore: CertStore = CertStore.getInstance("Collection", CollectionCertStoreParameters(setOf(trustRoot)))
    override val trustAnchor: TrustAnchor = TrustAnchor(trustRoot, null)
    private val keyToParties = ConcurrentHashMap<PublicKey, PartyAndCertificate>()
    private val principalToParties = ConcurrentHashMap<CordaX500Name, PartyAndCertificate>()

    init {
        keyToParties.putAll(identities.associateBy { it.owningKey })
        principalToParties.putAll(identities.associateBy { it.name })
    }

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate): PartyAndCertificate? = verifyAndRegisterIdentity(trustAnchor, identity)

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate, isNewRandomIdentity: Boolean): PartyAndCertificate? = verifyAndRegisterIdentity(trustAnchor, identity)

    override fun registerIdentity(identity: PartyAndCertificate, isNewRandomIdentity: Boolean): PartyAndCertificate? {
        val identityCertChain = identity.certPath.x509Certificates
        log.trace { "Registering identity $identity" }
        keyToParties[identity.owningKey] = identity
        // Always keep the first party we registered, as that's the well known identity
        principalToParties.computeIfAbsent(identity.name) { identity }
        return keyToParties[identityCertChain[1].publicKey]
    }

    override fun certificateFromKey(owningKey: PublicKey): PartyAndCertificate? = keyToParties[owningKey]

    // We give the caller a copy of the data set to avoid any locking problems
    override fun getAllIdentities(): Iterable<PartyAndCertificate> = ArrayList(keyToParties.values)

    override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? = principalToParties[name]?.party

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        val results = LinkedHashSet<Party>()
        for ((x500name, partyAndCertificate) in principalToParties) {
            partiesFromName(query, exactMatch, x500name, results, partyAndCertificate.party)
        }
        return results
    }
}
