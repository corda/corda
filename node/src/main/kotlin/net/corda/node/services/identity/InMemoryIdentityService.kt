package net.corda.node.services.identity

import net.corda.core.contracts.PartyAndReference
import net.corda.core.identity.*
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.UnknownAnonymousPartyException
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.node.services.identity.IdentityServiceUtil.Companion.partiesFromName
import net.corda.node.services.identity.IdentityServiceUtil.Companion.requireWellKnownPartyFromAnonymous
import net.corda.node.services.identity.IdentityServiceUtil.Companion.verifyIdentity
import net.corda.node.services.identity.IdentityServiceUtil.Companion.verifyPartyOwnsAnonymousIdentity
import net.corda.node.services.identity.IdentityServiceUtil.Companion.wellKnowPartyFromAnonymous
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
// TODO There is duplicated logic between this and PersistentIdentityService
@ThreadSafe
class InMemoryIdentityService(identities: List<out PartyAndCertificate> = emptyList(),
                              override val trustRoot: X509Certificate) : SingletonSerializeAsToken(), IdentityService {
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
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate): PartyAndCertificate? {
        // Validate the chain first, before we do anything clever with it
        verifyIdentity(trustAnchor, identity)
        return registerIdentity(identity)
    }

    private fun registerIdentity(identity: PartyAndCertificate): PartyAndCertificate? {
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

    override fun partyFromKey(key: PublicKey): Party? = keyToParties[key]?.party
    override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? = principalToParties[name]?.party
    override fun wellKnownPartyFromAnonymous(party: AbstractParty) = wellKnowPartyFromAnonymous(this, party)

    override fun wellKnownPartyFromAnonymous(partyRef: PartyAndReference) = wellKnownPartyFromAnonymous(partyRef.party)
    override fun requireWellKnownPartyFromAnonymous(party: AbstractParty) = requireWellKnownPartyFromAnonymous(this, party)

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        val results = LinkedHashSet<Party>()
        for ((x500name, partyAndCertificate) in principalToParties) {
            partiesFromName(query, exactMatch, x500name, results, partyAndCertificate.party)
        }
        return results
    }

    @Throws(UnknownAnonymousPartyException::class)
    override fun assertOwnership(party: Party, anonymousParty: AnonymousParty) {
        val anonymousIdentity = keyToParties[anonymousParty.owningKey]
                ?: throw UnknownAnonymousPartyException("Unknown $anonymousParty")
        verifyPartyOwnsAnonymousIdentity(party, anonymousIdentity)
    }
}
