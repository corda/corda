package net.corda.node.services.identity

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.identity.x500Matches
import net.corda.core.internal.CertRole
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.nodeapi.internal.crypto.X509Utilities
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
                              override val trustRoot: X509Certificate) : SingletonSerializeAsToken(), IdentityService {
    companion object {
        private val log = contextLogger()
    }

    /**
     * Certificate store for certificate authority and intermediary certificates.
     */
    override val caCertStore: CertStore = CertStore.getInstance("Collection", CollectionCertStoreParameters(setOf(trustRoot)))
    override val trustAnchor: TrustAnchor = TrustAnchor(trustRoot, null)
    private val keyToPartyAndCerts = ConcurrentHashMap<PublicKey, PartyAndCertificate>()
    private val nameToKey = ConcurrentHashMap<CordaX500Name, PublicKey>()
    private val keyToName = ConcurrentHashMap<PublicKey, CordaX500Name>()

    init {
        keyToPartyAndCerts.putAll(identities.associateBy { it.owningKey })
        nameToKey.putAll(identities.associateBy { it.name }.mapValues { it.value.owningKey })
        keyToName.putAll(identities.associateBy{ it.owningKey }.mapValues { it.value.party.name })
    }


    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate): PartyAndCertificate? {
        return verifyAndRegisterIdentity(trustAnchor, identity)
    }

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    private fun verifyAndRegisterIdentity(trustAnchor: TrustAnchor, identity: PartyAndCertificate): PartyAndCertificate? {
        // Validate the chain first, before we do anything clever with it
        val identityCertChain = identity.certPath.x509Certificates
        try {
            identity.verify(trustAnchor)
        } catch (e: CertPathValidatorException) {
            log.warn("Certificate validation failed for ${identity.name} against trusted root ${trustAnchor.trustedCert.subjectX500Principal}.")
            log.warn("Certificate path :")
            identityCertChain.reversed().forEachIndexed { index, certificate ->
                val space = (0 until index).joinToString("") { "   " }
                log.warn("$space${certificate.subjectX500Principal}")
            }
            throw e
        }
        // Ensure we record the first identity of the same name, first
        val wellKnownCert = identityCertChain.single { CertRole.extract(it)?.isWellKnown ?: false }
        if (wellKnownCert != identity.certificate) {
            val idx = identityCertChain.lastIndexOf(wellKnownCert)
            val firstPath = X509Utilities.buildCertPath(identityCertChain.slice(idx until identityCertChain.size))
            verifyAndRegisterIdentity(trustAnchor, PartyAndCertificate(firstPath))
        }
        return registerIdentity(identity)
    }

    private fun registerIdentity(identity: PartyAndCertificate): PartyAndCertificate? {
        val identityCertChain = identity.certPath.x509Certificates
        log.trace { "Registering identity $identity" }
        keyToPartyAndCerts[identity.owningKey] = identity
        // Always keep the first party we registered, as that's the well known identity
        nameToKey.putIfAbsent(identity.name, identity.owningKey)
        keyToName.putIfAbsent(identity.owningKey, identity.name)
        return keyToPartyAndCerts[identityCertChain[1].publicKey]
    }

    override fun certificateFromKey(owningKey: PublicKey): PartyAndCertificate? = keyToPartyAndCerts[owningKey]

    // We give the caller a copy of the data set to avoid any locking problems
    override fun getAllIdentities(): Iterable<PartyAndCertificate> = ArrayList(keyToPartyAndCerts.values)

    override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? {
        val key = nameToKey[name]
        return if (key != null) {
            keyToPartyAndCerts[key]?.party
        } else
            null
    }

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        val results = LinkedHashSet<Party>()
        nameToKey.forEach { (x500name, key) ->
            if (x500Matches(query, exactMatch, x500name)) {
                results += keyToPartyAndCerts[key]!!.party
            }
        }
        return results
    }

    override fun registerKeyToParty(key: PublicKey, party: Party): Boolean {
        if (keyToName[key] == null) {
            keyToName.putIfAbsent(key, party.name)
            return true
        }
        return false
    }
}
