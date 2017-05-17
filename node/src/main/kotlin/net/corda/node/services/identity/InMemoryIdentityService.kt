package net.corda.node.services.identity

import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.subject
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
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import java.security.InvalidAlgorithmParameterException
import java.security.PublicKey
import java.security.cert.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.ThreadSafe
import kotlin.collections.ArrayList

/**
 * Simple identity service which caches parties and provides functionality for efficient lookup.
 *
 * @param identities initial set of identities for the service, typically only used for unit tests.
 * @param certPaths initial set of certificate paths for the service, typically only used for unit tests.
 */
@ThreadSafe
class InMemoryIdentityService(identities: Iterable<PartyAndCertificate>,
                              certPaths: Map<AnonymousParty, CertPath> = emptyMap(),
                              val trustRoot: X509Certificate?) : SingletonSerializeAsToken(), IdentityService {
    constructor(identities: Iterable<PartyAndCertificate> = emptySet(),
                certPaths: Map<AnonymousParty, CertPath> = emptyMap(),
                trustRoot: X509CertificateHolder?) : this(identities, certPaths, trustRoot?.let { JcaX509CertificateConverter().getCertificate(it) })
    companion object {
        private val log = loggerFor<InMemoryIdentityService>()
    }

    private val trustAnchor: TrustAnchor? = trustRoot?.let { cert -> TrustAnchor(cert, null) }
    private val keyToParties = ConcurrentHashMap<PublicKey, PartyAndCertificate>()
    private val principalToParties = ConcurrentHashMap<X500Name, PartyAndCertificate>()
    private val partyToPath = ConcurrentHashMap<AbstractParty, CertPath>()

    init {
        keyToParties.putAll(identities.associateBy { it.owningKey } )
        principalToParties.putAll(identities.associateBy { it.name })
        partyToPath.putAll(certPaths)
    }

    // TODO: Check the certificate validation logic
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun registerIdentity(party: PartyAndCertificate) {
        require(party.certPath.certificates.isNotEmpty()) { "Certificate path must contain at least one certificate" }
        // Validate the chain first, before we do anything clever with it
        val validatorParameters = if (trustAnchor != null) {
            PKIXParameters(setOf(trustAnchor))
        } else {
            // TODO: We should always require a full chain back to a trust anchor, but until we have a network
            // trust anchor everywhere, this will have to do.
            val converter = JcaX509CertificateConverter()
            PKIXParameters(setOf(TrustAnchor(converter.getCertificate(party.certificate), null)))
        }
        val validator = CertPathValidator.getInstance("PKIX")
        validatorParameters.isRevocationEnabled = false
        // TODO: val result = validator.validate(party.certPath, validatorParameters) as PKIXCertPathValidatorResult
        // require(trustAnchor == null || result.trustAnchor == trustAnchor)
        // require(result.publicKey == party.owningKey) { "Certificate path validation must end at transaction key ${anonymousParty.owningKey.toStringShort()}, found ${result.publicKey.toStringShort()}" }

        log.trace { "Registering identity $party" }
        require(Arrays.equals(party.certificate.subjectPublicKeyInfo.encoded, party.owningKey.encoded)) { "Party certificate must end with party's public key" }

        partyToPath[party.party] = party.certPath
        keyToParties[party.owningKey] = party
        principalToParties[party.name] = party
    }

    override fun certificateFromParty(party: Party): PartyAndCertificate? = principalToParties[party.name]

    // We give the caller a copy of the data set to avoid any locking problems
    override fun getAllIdentities(): Iterable<PartyAndCertificate> = ArrayList(keyToParties.values)

    override fun partyFromKey(key: PublicKey): Party? = keyToParties[key]?.party
    @Deprecated("Use partyFromX500Name")
    override fun partyFromName(name: String): Party? = principalToParties[X500Name(name)]?.party
    override fun partyFromX500Name(principal: X500Name): Party? = principalToParties[principal]?.party
    override fun partyFromAnonymous(party: AbstractParty): Party? {
        return if (party is Party) {
            party
        } else {
            partyFromKey(party.owningKey)
        }
    }
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
                    if (component.contains(query, ignoreCase = true))
                        results += party
                }
            }
        }
        return results
    }

    @Throws(IdentityService.UnknownAnonymousPartyException::class)
    override fun assertOwnership(party: Party, anonymousParty: AnonymousParty) {
        val path = partyToPath[anonymousParty] ?: throw IdentityService.UnknownAnonymousPartyException("Unknown anonymous party ${anonymousParty.owningKey.toStringShort()}")
        val target = path.certificates.last() as X509Certificate
        requireThat {
            "Certificate path ends with \"${target.issuerX500Principal}\" expected \"${party.name}\"" using (X500Name(target.subjectX500Principal.name) == party.name)
            "Certificate path ends with correct public key" using (target.publicKey == anonymousParty.owningKey)
        }
        // Verify there's a previous certificate in the path, which matches
        val root = path.certificates.first() as X509Certificate
        require(X500Name(root.issuerX500Principal.name) == party.name) { "Certificate path starts with \"${root.issuerX500Principal}\" expected \"${party.name}\"" }
    }

    override fun pathForAnonymous(anonymousParty: AnonymousParty): CertPath? = partyToPath[anonymousParty]

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun registerAnonymousIdentity(anonymousParty: AnonymousParty, party: Party, path: CertPath) {
        val fullParty = certificateFromParty(party) ?: throw IllegalArgumentException("Unknown identity ${party.name}")
        require(path.certificates.isNotEmpty()) { "Certificate path must contain at least one certificate" }
        // Validate the chain first, before we do anything clever with it
        val validator = CertPathValidator.getInstance("PKIX")
        val validatorParameters = if (trustAnchor != null) {
            PKIXParameters(setOf(trustAnchor))
        } else {
            // TODO: We should always require a full chain back to a trust anchor, but until we have a network
            // trust anchor everywhere, this will have to do.
            val converter = JcaX509CertificateConverter()
            PKIXParameters(setOf(TrustAnchor(converter.getCertificate(fullParty.certificate), null)))
        }
        validatorParameters.isRevocationEnabled = false
        val result = validator.validate(path, validatorParameters) as PKIXCertPathValidatorResult
        val subjectCertificate = path.certificates.first()
        require(trustAnchor == null || result.trustAnchor == trustAnchor)
        require(result.publicKey == anonymousParty.owningKey) { "Certificate path validation must end at transaction key ${anonymousParty.owningKey.toStringShort()}, found ${result.publicKey.toStringShort()}" }
        require(subjectCertificate is X509Certificate && subjectCertificate.subject == fullParty.name) { "Subject of the transaction certificate must match the well known identity" }

        log.trace { "Registering identity $fullParty" }

        partyToPath[anonymousParty] = path
        keyToParties[anonymousParty.owningKey] = fullParty
        principalToParties[fullParty.name] = fullParty
    }
}
