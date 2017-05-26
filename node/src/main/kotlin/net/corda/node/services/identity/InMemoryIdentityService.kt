package net.corda.node.services.identity

import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import org.bouncycastle.asn1.x500.X500Name
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
class InMemoryIdentityService(identities: Iterable<Party> = emptySet(),
                              certPaths: Map<AnonymousParty, CertPath> = emptyMap()) : SingletonSerializeAsToken(), IdentityService {
    companion object {
        private val log = loggerFor<InMemoryIdentityService>()
    }

    private val keyToParties = ConcurrentHashMap<PublicKey, Party>()
    private val principalToParties = ConcurrentHashMap<X500Name, Party>()
    private val partyToPath = ConcurrentHashMap<AnonymousParty, CertPath>()

    init {
        keyToParties.putAll(identities.associateBy { it.owningKey } )
        principalToParties.putAll(identities.associateBy { it.name })
        partyToPath.putAll(certPaths)
    }

    override fun registerIdentity(party: Party) {
        log.trace { "Registering identity $party" }
        keyToParties[party.owningKey] = party
        principalToParties[party.name] = party
    }

    // We give the caller a copy of the data set to avoid any locking problems
    override fun getAllIdentities(): Iterable<Party> = ArrayList(keyToParties.values)

    override fun partyFromKey(key: PublicKey): Party? = keyToParties[key]
    @Deprecated("Use partyFromX500Name")
    override fun partyFromName(name: String): Party? = principalToParties[X500Name(name)]
    override fun partyFromX500Name(principal: X500Name): Party? = principalToParties[principal]
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
    override fun registerPath(trustedRoot: X509Certificate, anonymousParty: AnonymousParty, path: CertPath) {
        val expectedTrustAnchor = TrustAnchor(trustedRoot, null)
        require(path.certificates.isNotEmpty()) { "Certificate path must contain at least one certificate" }
        val target = path.certificates.last() as X509Certificate
        require(target.publicKey == anonymousParty.owningKey) { "Certificate path must end with anonymous party's public key" }
        val validator = CertPathValidator.getInstance("PKIX")
        val validatorParameters = PKIXParameters(setOf(expectedTrustAnchor)).apply {
            isRevocationEnabled = false
        }
        val result = validator.validate(path, validatorParameters) as PKIXCertPathValidatorResult
        require(result.trustAnchor == expectedTrustAnchor)
        require(result.publicKey == anonymousParty.owningKey)

        partyToPath[anonymousParty] = path
    }
}
