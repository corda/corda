package net.corda.node.services.identity

import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.CertRole
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.x509Certificates
import java.security.InvalidAlgorithmParameterException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.TrustAnchor

/**
 * Contains utility methods for use in IdentityService classes
 */
class IdentityServiceUtil {

    companion object {
        private val log = contextLogger()


        fun partiesFromName(query: String, exactMatch: Boolean, x500name: CordaX500Name, results: LinkedHashSet<Party>, party: Party) {

            val components = listOfNotNull(x500name.commonName, x500name.organisationUnit, x500name.organisation, x500name.locality, x500name.state, x500name.country)
            components.forEach { component ->
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

        /**
         * Verifies that an identity is valid.
         *
         * @param trustAnchor The trust anchor that will verify the identity's validity
         * @param identity The identity to verify
         */
        @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
        fun verifyIdentity(trustAnchor: TrustAnchor, identity: PartyAndCertificate) {
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
                verifyIdentity(trustAnchor, PartyAndCertificate(firstPath))
            }
        }

        /**
         * Verifies that a given party owns an anonymous identity
         * @param party The party that owns the anonymous identity
         * @param anonymousIdentity The identity that needs to be verified for ownership
         */
        fun verifyPartyOwnsAnonymousIdentity(party: Party, anonymousIdentity: PartyAndCertificate) {
            val issuingCert = anonymousIdentity.certPath.certificates[1]
            require(issuingCert.publicKey == party.owningKey) {
                "Issuing certificate's public key must match the party key ${party.owningKey.toStringShort()}."
            }
        }

        /**
         * Resolves a (optionally) confidential identity to the corresponding well known identity [Party].
         * It transparently handles returning the well known identity back if a well known identity is passed in.
         *
         * @param identityService The IdentityService to retrieve the party from
         * @param party identity to determine well known identity for.
         * @return well known identity, if found.
         */
        fun wellKnowPartyFromAnonymous(identityService: IdentityService, party: AbstractParty): Party? {
            // The original version of this would return the party as-is if it was a Party (rather than AnonymousParty),
            // however that means that we don't verify that we know who owns the key. As such as now enforce turning the key
            // into a party, and from there figure out the well known party.
            val candidate = identityService.partyFromKey(party.owningKey)
            // TODO: This should be done via the network map cache, which is the authoritative source of well known identities
            return if (candidate != null) {
                require(party.nameOrNull() == null || party.nameOrNull() == candidate.name) {
                    "Candidate party $candidate does not match expected $party"
                }
                identityService.wellKnownPartyFromX500Name(candidate.name)
            } else {
                null
            }
        }

        /**
         * Resolve the well known identity of a party. Throws an exception if the party cannot be identified.
         * If the party passed in is already a well known identity (i.e. a [Party]) this returns it as-is.
         *
         * @param identityService The IdentityService to retrieve the party from
         * @return the well known identity.
         */
        @Throws(IllegalStateException::class)
        fun requireWellKnownPartyFromAnonymous(identityService: IdentityService, party: AbstractParty) =
                identityService.wellKnownPartyFromAnonymous(party)
                        ?: throw IllegalStateException("Could not deanonymise party ${party.owningKey.toStringShort()}")
    }
}