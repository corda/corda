package net.corda.node.services.identity

import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.cert
import net.corda.core.crypto.subject
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.*
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

/**
 * Simple identity service which caches parties and provides functionality for efficient lookup.
 *
 * @param identities initial set of identities for the service, typically only used for unit tests.
 * @param certPaths initial set of certificate paths for the service, typically only used for unit tests.
 */
@ThreadSafe
class InMemoryIdentityService(identities: Iterable<PartyAndCertificate> = emptySet(),
                              certPaths: Map<AnonymousParty, CertPath> = emptyMap(),
                              override val trustRoot: X509Certificate,
                              vararg caCertificates: X509Certificate) : SingletonSerializeAsToken(), IdentityService {
    constructor(identities: Iterable<PartyAndCertificate> = emptySet(),
                certPaths: Map<AnonymousParty, CertPath> = emptyMap(),
                trustRoot: X509CertificateHolder) : this(identities, certPaths, trustRoot.cert)
    companion object {
        private val log = loggerFor<InMemoryIdentityService>()
    }

    /**
     * Certificate store for certificate authority and intermediary certificates.
     */
    override val caCertStore: CertStore
    override val trustRootHolder = X509CertificateHolder(trustRoot.encoded)
    private val trustAnchor: TrustAnchor = TrustAnchor(trustRoot, null)
    private val keyToParties = ConcurrentHashMap<PublicKey, PartyAndCertificate>()
    private val principalToParties = ConcurrentHashMap<X500Name, PartyAndCertificate>()
    private val partyToPath = ConcurrentHashMap<AbstractParty, Pair<CertPath, X509CertificateHolder>>()

    init {
        val caCertificatesWithRoot: Set<X509Certificate> = caCertificates.toSet() + trustRoot
        caCertStore = CertStore.getInstance("Collection", CollectionCertStoreParameters(caCertificatesWithRoot))
        keyToParties.putAll(identities.associateBy { it.owningKey } )
        principalToParties.putAll(identities.associateBy { it.name })
        certPaths.forEach { (party, path) ->
            partyToPath.put(party, Pair(path, X509CertificateHolder(path.certificates.first().encoded)))
        }
    }

    // TODO: Check the certificate validation logic
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun registerIdentity(party: PartyAndCertificate) {
        require(party.certPath.certificates.isNotEmpty()) { "Certificate path must contain at least one certificate" }
        // Validate the chain first, before we do anything clever with it
        validateCertificatePath(party.party, party.certPath)

        log.trace { "Registering identity $party" }
        require(Arrays.equals(party.certificate.subjectPublicKeyInfo.encoded, party.owningKey.encoded)) { "Party certificate must end with party's public key" }

        partyToPath[party.party] = Pair(party.certPath, party.certificate)
        keyToParties[party.owningKey] = party
        principalToParties[party.name] = party
    }

    override fun anonymousFromKey(owningKey: PublicKey): AnonymousPartyAndPath? {
        val anonymousParty = AnonymousParty(owningKey)
        val path = partyToPath[anonymousParty]
        return path?.let { it ->
            AnonymousPartyAndPath(anonymousParty, it.first)
        }
    }
    override fun certificateFromKey(owningKey: PublicKey): PartyAndCertificate? = keyToParties[owningKey]
    override fun certificateFromParty(party: Party): PartyAndCertificate? = principalToParties[party.name]

    // We give the caller a copy of the data set to avoid any locking problems
    override fun getAllIdentities(): Iterable<PartyAndCertificate> = java.util.ArrayList(keyToParties.values)

    override fun partyFromKey(key: PublicKey): Party? = keyToParties[key]?.party
    @Deprecated("Use partyFromX500Name")
    override fun partyFromName(name: String): Party? = principalToParties[X500Name(name)]?.party
    override fun partyFromX500Name(principal: X500Name): Party? = principalToParties[principal]?.party
    override fun partyFromAnonymous(party: AbstractParty) = party as? Party ?: partyFromKey(party.owningKey)
    override fun partyFromAnonymous(partyRef: PartyAndReference) = partyFromAnonymous(partyRef.party)
    override fun requirePartyFromAnonymous(party: AbstractParty): Party {
        return partyFromAnonymous(party) ?: throw IllegalStateException("Could not deanonymise party ${party.owningKey.toStringShort()}")
    }

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        val results = HashSet<Party>()
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
        val path = partyToPath[anonymousParty]?.first ?: throw IdentityService.UnknownAnonymousPartyException("Unknown anonymous party ${anonymousParty.owningKey.toStringShort()}")
        require(path.certificates.size > 1) { "Certificate path must contain at least two certificates" }
        val actual = path.certificates[1]
        require(actual is X509Certificate && actual.publicKey == party.owningKey) { "Next certificate in the path must match the party key ${party.owningKey.toStringShort()}." }
        val target = path.certificates.first()
        require(target is X509Certificate && target.publicKey == anonymousParty.owningKey) { "Certificate path starts with a certificate for the anonymous party" }
    }

    override fun pathForAnonymous(anonymousParty: AnonymousParty): CertPath? = partyToPath[anonymousParty]?.first

    override fun registerAnonymousIdentity(anonymousIdentity: AnonymousPartyAndPath, party: Party): PartyAndCertificate = verifyAndRegisterAnonymousIdentity(anonymousIdentity,  party)

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterAnonymousIdentity(anonymousIdentity: AnonymousPartyAndPath, wellKnownIdentity: Party): PartyAndCertificate {
        val fullParty = verifyAnonymousIdentity(anonymousIdentity, wellKnownIdentity)
        val certificate = X509CertificateHolder(anonymousIdentity.certPath.certificates.first().encoded)
        log.trace { "Registering identity $fullParty" }

        partyToPath[anonymousIdentity.party] = Pair(anonymousIdentity.certPath, certificate)
        keyToParties[anonymousIdentity.party.owningKey] = fullParty
        return fullParty
    }

    override fun verifyAnonymousIdentity(anonymousIdentity: AnonymousPartyAndPath, party: Party): PartyAndCertificate {
        val (anonymousParty, path) = anonymousIdentity
        val fullParty = certificateFromParty(party) ?: throw IllegalArgumentException("Unknown identity ${party.name}")
        require(path.certificates.isNotEmpty()) { "Certificate path must contain at least one certificate" }
        // Validate the chain first, before we do anything clever with it
        validateCertificatePath(anonymousParty, path)
        val subjectCertificate = path.certificates.first()
        require(subjectCertificate is X509Certificate && subjectCertificate.subject == fullParty.name) { "Subject of the transaction certificate must match the well known identity" }
        return fullParty
    }

    /**
     * Verify that the given certificate path is valid and leads to the owning key of the party.
     */
    private fun validateCertificatePath(party: AbstractParty, path: CertPath): PKIXCertPathValidatorResult {
        // Check that the path ends with a certificate for the correct party.
        val endCertificate = path.certificates.first()
        // Ensure the key is in the correct format for comparison.
        // TODO: Replace with a Bouncy Castle cert path so we can avoid Sun internal classes appearing unexpectedly.
        //       For now we have to deal with this potentially being an [X509Key] which is Sun's equivalent to
        //       [SubjectPublicKeyInfo] but doesn't compare properly with [PublicKey].
        val endKey = Crypto.decodePublicKey(endCertificate.publicKey.encoded)
        require(endKey == party.owningKey) { "Certificate path validation must end at owning key ${party.owningKey.toStringShort()}, found ${endKey.toStringShort()}" }

        val validatorParameters = PKIXParameters(setOf(trustAnchor))
        val validator = CertPathValidator.getInstance("PKIX")
        validatorParameters.isRevocationEnabled = false
        return validator.validate(path, validatorParameters) as PKIXCertPathValidatorResult
    }
}
