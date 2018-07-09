package net.corda.node.services.api

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

interface IdentityServiceInternal : IdentityService {

    private companion object {
        val log = contextLogger()
    }

    /** This method exists so it can be mocked with doNothing, rather than having to make up a possibly invalid return value. */
    fun justVerifyAndRegisterIdentity(identity: PartyAndCertificate) {
        verifyAndRegisterIdentity(identity)
    }

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
    fun verifyAndRegisterIdentity(trustAnchor: TrustAnchor, identity: PartyAndCertificate): PartyAndCertificate? {
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

    fun registerIdentity(identity: PartyAndCertificate): PartyAndCertificate?
}
