package net.corda.node.services.identity

import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.cert
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import java.security.InvalidAlgorithmParameterException
import java.security.PublicKey
import java.security.cert.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.ThreadSafe
import kotlin.collections.LinkedHashSet

/**
 * Simple identity service which caches parties and provides functionality for efficient lookup.
 *
 * @param identities initial set of identities for the service, typically only used for unit tests.
 * @param certPaths initial set of certificate paths for the service, typically only used for unit tests.
 */
@ThreadSafe
class InMemoryIdentityService(identities: Iterable<PartyAndCertificate> = emptySet(),
                              confidentialIdentities: Iterable<PartyAndCertificate> = emptySet(),
                              override val trustRoot: X509Certificate,
                              vararg caCertificates: X509Certificate) : SingletonSerializeAsToken(), IdentityService {
    constructor(wellKnownIdentities: Iterable<PartyAndCertificate> = emptySet(),
                confidentialIdentities: Iterable<PartyAndCertificate> = emptySet(),
                trustRoot: X509CertificateHolder) : this(wellKnownIdentities, confidentialIdentities, trustRoot.cert)
    companion object {
        private val log = loggerFor<InMemoryIdentityService>()
    }

    /**
     * Certificate store for certificate authority and intermediary certificates.
     */
    override val caCertStore: CertStore
    override val trustRootHolder = X509CertificateHolder(trustRoot.encoded)
    override val trustAnchor: TrustAnchor = TrustAnchor(trustRoot, null)
    private val keyToParties = ConcurrentHashMap<PublicKey, PartyAndCertificate>()
    private val principalToParties = ConcurrentHashMap<X500Name, PartyAndCertificate>()

    init {
        val caCertificatesWithRoot: Set<X509Certificate> = caCertificates.toSet() + trustRoot
        caCertStore = CertStore.getInstance("Collection", CollectionCertStoreParameters(caCertificatesWithRoot))
        keyToParties.putAll(identities.associateBy { it.owningKey } )
        principalToParties.putAll(identities.associateBy { it.name })
        confidentialIdentities.forEach { identity ->
            require(identity.certPath.certificates.size >= 2) { "Certificate path must at least include subject and issuing certificates" }
            principalToParties.computeIfAbsent(identity.name) { identity }
        }
    }

    override fun registerIdentity(party: PartyAndCertificate) {
        verifyAndRegisterIdentity(party)
    }

    // TODO: Check the certificate validation logic
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate): PartyAndCertificate? {
        require(identity.certPath.certificates.size >= 2) { "Certificate path must at least include subject and issuing certificates" }
        // Validate the chain first, before we do anything clever with it
        identity.verify(trustAnchor)

        log.trace { "Registering identity $identity" }
        require(Arrays.equals(identity.certificate.subjectPublicKeyInfo.encoded, identity.owningKey.encoded)) { "Party certificate must end with party's public key" }

        keyToParties[identity.owningKey] = identity
        // Always keep the first party we registered, as that's the well known identity
        principalToParties.computeIfAbsent(identity.name) { identity }
        return keyToParties[identity.certPath.certificates[1].publicKey]
    }

    override fun certificateFromKey(owningKey: PublicKey): PartyAndCertificate? = keyToParties[owningKey]
    override fun certificateFromParty(party: Party): PartyAndCertificate = principalToParties[party.name] ?: throw IllegalArgumentException("Unknown identity ${party.name}")

    // We give the caller a copy of the data set to avoid any locking problems
    override fun getAllIdentities(): Iterable<PartyAndCertificate> = java.util.ArrayList(keyToParties.values)

    override fun partyFromKey(key: PublicKey): Party? = keyToParties[key]?.party
    override fun partyFromX500Name(principal: X500Name): Party? = principalToParties[principal]?.party
    override fun partyFromAnonymous(party: AbstractParty): Party? {
        // Expand the anonymous party to a full party (i.e. has a name) if possible
        val candidate = party as? Party ?: keyToParties[party.owningKey]?.party
        // TODO: This should be done via the network map cache, which is the authoritative source of well known identities
        // Look up the well known identity for that name
        return if (candidate != null) {
            // If we have a well known identity by that name, use it in preference to the candidate. Otherwise default
            // back to the candidate.
            principalToParties[candidate.name]?.party ?: candidate
        } else {
            null
        }
    }
    override fun partyFromAnonymous(partyRef: PartyAndReference) = partyFromAnonymous(partyRef.party)
    override fun requirePartyFromAnonymous(party: AbstractParty): Party {
        return partyFromAnonymous(party) ?: throw IllegalStateException("Could not deanonymise party ${party.owningKey.toStringShort()}")
    }

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        val results = LinkedHashSet<Party>()
        for ((x500name, partyAndCertificate) in principalToParties) {
            val party = partyAndCertificate.party
            for (rdn in x500name.rdNs) {
                val component = rdn.first.value.toString()
                if (exactMatch && component == query) {
                    results += party
                } else if (!exactMatch) {
                    // We can imagine this being a query over a lucene index in future.
                    //
                    // Kostas says: We can easily use the Jaro-Winkler distance metric as it is best suited for short
                    // strings such as entity/company names, and to detect small typos. We can also apply it for city
                    // or any keyword related search in lists of records (not raw text - for raw text we need indexing)
                    // and we can return results in hierarchical order (based on normalised String similarity 0.0-1.0).
                    if (component.contains(query, ignoreCase = true))
                        results += party
                }
            }
        }
        return results
    }

    @Throws(IdentityService.UnknownAnonymousPartyException::class)
    override fun assertOwnership(party: Party, anonymousParty: AnonymousParty) {
        val path = keyToParties[anonymousParty.owningKey]?.certPath ?: throw IdentityService.UnknownAnonymousPartyException("Unknown anonymous party ${anonymousParty.owningKey.toStringShort()}")
        require(path.certificates.size > 1) { "Certificate path must contain at least two certificates" }
        val actual = path.certificates[1]
        require(actual is X509Certificate && actual.publicKey == party.owningKey) { "Next certificate in the path must match the party key ${party.owningKey.toStringShort()}." }
        val target = path.certificates.first()
        require(target is X509Certificate && target.publicKey == anonymousParty.owningKey) { "Certificate path starts with a certificate for the anonymous party" }
    }
}
