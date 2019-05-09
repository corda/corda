package net.corda.node.services.identity

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.identity.SignedKeyToPartyMapping
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.x500Matches
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.nodeapi.internal.crypto.x509Certificates
import java.security.PublicKey
import java.security.cert.CertStore
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.ThreadSafe

/**
 * Simple identity service which caches parties and provides functionality for efficient lookup.
 *
 * @param identities initial set of identities for the service, typically only used for unit tests.
 */
@ThreadSafe
class InMemoryIdentityService(identities: List<PartyAndCertificate> = emptyList(),
                              override val trustRoot: X509Certificate,
                              private val keyToParty: Map<PublicKey, Party>) : SingletonSerializeAsToken(), IdentityService {

    @JvmOverloads
    constructor(identities: List<PartyAndCertificate>, trustRoot: X509Certificate) : this(identities, trustRoot, emptyMap())

    companion object {
        private val log = contextLogger()
    }

    /**
     * Certificate store for certificate authority and intermediary certificates.
     */
    override val caCertStore: CertStore = CertStore.getInstance("Collection", CollectionCertStoreParameters(setOf(trustRoot)))
    override val trustAnchor: TrustAnchor = TrustAnchor(trustRoot, null)
    private val keysToPartyAndCert = ConcurrentHashMap<PublicKey, PartyAndCertificate>()
    private val principalToParties = ConcurrentHashMap<CordaX500Name, PartyAndCertificate>()
    private val keyToParties = ConcurrentHashMap<PublicKey, Party>()
    private val partyToKeys = ConcurrentHashMap<Party, ArrayList<PublicKey>>()

    init {
        keysToPartyAndCert.putAll(identities.associateBy { it.owningKey })
        principalToParties.putAll(identities.associateBy { it.name })
        keyToParties.putAll(keyToParty)
        val swapped = keyToParty.entries.groupBy { it.value }.mapValues { ArrayList(it.value.map { entries -> entries.key }) }
        partyToKeys.putAll(swapped)
    }

//    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
//    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate): PartyAndCertificate? = verifyAndRegisterIdentity(trustAnchor, identity)
//
//    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
//    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate, isNewRandomIdentity: Boolean): PartyAndCertificate? = verifyAndRegisterIdentity(trustAnchor, identity)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate): PartyAndCertificate? = verifyAndRegisterIdentity(trustAnchor, identity, false)

    fun verifyAndRegisterIdentity(trustAnchor: TrustAnchor, identity: PartyAndCertificate, bool: Boolean) : PartyAndCertificate {
        //FIXME
        return identity
    }


    override fun registerIdentity(identity: PartyAndCertificate, isNewRandomIdentity: Boolean): PartyAndCertificate? {
        val identityCertChain = identity.certPath.x509Certificates
        log.trace { "Registering identity $identity" }
        keysToPartyAndCert[identity.owningKey] = identity
        // Always keep the first party we registered, as that's the well known identity
        principalToParties.computeIfAbsent(identity.name) { identity }
        return keysToPartyAndCert[identityCertChain[1].publicKey]
    }

    override fun certificateFromKey(owningKey: PublicKey): PartyAndCertificate? = keysToPartyAndCert[owningKey]

    // We give the caller a copy of the data set to avoid any locking problems
    override fun getAllIdentities(): Iterable<PartyAndCertificate> = ArrayList(keysToPartyAndCert.values)

    override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? = principalToParties[name]?.party

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        val results = LinkedHashSet<Party>()
        principalToParties.forEach { (x500name, partyAndCertificate) ->
            if (x500Matches(query, exactMatch, x500name)) {
                results += partyAndCertificate.party
            }
        }
        return results
    }

    override fun registerConfidentialIdentity(keyMapping: SignedKeyToPartyMapping, nodeParty: Party): Boolean {
        val k = keyMapping.mapping.key
        val p = keyMapping.mapping.party
        val sig = keyMapping.signature

        if (p != nodeParty) {
            throw IllegalArgumentException("Something something something")
        }

        if (sig.by != nodeParty.owningKey) {
            throw IllegalArgumentException("Something somethign seomthien ghrijei")
        }

        var willRegisterNewMapping = true

        when (keyToParties.get(k)) {
            null -> {
                keyToParties.putIfAbsent(k, p)
            }
            else -> {
                if (p != keyToParties.get(k)) {
                    return false
                }
                willRegisterNewMapping = false
            }
        }
        return willRegisterNewMapping
    }
}