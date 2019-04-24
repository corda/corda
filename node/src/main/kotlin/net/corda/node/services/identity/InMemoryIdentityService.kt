package net.corda.node.services.identity

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.hash
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.nodeapi.internal.crypto.x509Certificates
import java.security.InvalidAlgorithmParameterException
import java.security.PublicKey
import java.security.cert.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import javax.annotation.concurrent.ThreadSafe

/**
 * Simple identity service which caches parties and provides functionality for efficient lookup.
 *
 * @param identities initial set of identities for the service, typically only used for unit tests.
 */
@ThreadSafe
class InMemoryIdentityService(identities: List<PartyAndCertificate> = emptyList(),
                              override val trustRoot: X509Certificate,
                              private val keyToParty: Map<PublicKey, Party>) : SingletonSerializeAsToken(), IdentityServiceInternal {

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

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate): PartyAndCertificate? = verifyAndRegisterIdentity(trustAnchor, identity)

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate, isNewRandomIdentity: Boolean): PartyAndCertificate? = verifyAndRegisterIdentity(trustAnchor, identity)

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
                results +=  partyAndCertificate.party
            }
        }
        return results
    }

    override fun registerIdentityMapping(identity: Party, key: PublicKey): Boolean {
        // Check by key
        when (keyToParties[key]) {
            null -> {
                log.info("Linking: ${key.hash} to ${identity.name}")
                keyToParties[key] = identity
            }
            else -> {
                log.info("An existing entry for ${key.hash} already exists.")
                if (identity != keyToParties[key]) {
                    log.error("The public key ${key.hash} is already assigned to a different party.")
                    return false
                }
                return false
            }
        }

        // Check by party
        when(partyToKeys[identity]) {
            null -> {
                log.info("Linking: ${identity.name} to ${key.hash}")
                partyToKeys.putIfAbsent(identity, arrayListOf(key))
            }
            else -> {
                val keys = partyToKeys[identity]
                if (!keys!!.contains(key)) {
                    keys.add(key)


                }
            }
        }
//        if (existingEntryForParty.isEmpty()) {
//            log.info("Linking: ${identity.name} to ${key.hash}")
//            partyToKeys[identity] = key
//        } else {
//            partyToKeys.computeIfPresent( existingEntryForParty, k ->  )
//            //existingEntryForParty does not equal one of the values for this party then add to the map
//            ){
//                log.info("An existing entry for ${identity.name} already exists.")
//                if (key != partyToKeys[identity]) {
//                    log.error("The public key ${key.hash} is already assigned to a different party.")
//                }
//                return false
//            }
//        }
    return true
    }
}
