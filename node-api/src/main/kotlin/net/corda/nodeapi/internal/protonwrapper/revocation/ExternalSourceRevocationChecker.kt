package net.corda.nodeapi.internal.protonwrapper.netty.revocation

import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.protonwrapper.netty.ExternalCrlSource
import org.bouncycastle.asn1.x509.Extension
import java.security.cert.CRLReason
import java.security.cert.CertPathValidatorException
import java.security.cert.Certificate
import java.security.cert.CertificateRevokedException
import java.security.cert.PKIXRevocationChecker
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.*

/**
 * Implementation of [PKIXRevocationChecker] which determines whether certificate is revoked using [externalCrlSource] which knows how to
 * obtain a set of CRLs for a given certificate from an external source
 */
class ExternalSourceRevocationChecker(private val externalCrlSource: ExternalCrlSource, private val dateSource: () -> Date) : PKIXRevocationChecker() {

    companion object {
        private val logger = contextLogger()
    }

    override fun check(cert: Certificate, unresolvedCritExts: MutableCollection<String>?) {
        val x509Certificate = cert as X509Certificate
        checkApprovedCRLs(x509Certificate, externalCrlSource.fetch(x509Certificate))
    }

    /**
     * Borrowed from `RevocationChecker.checkApprovedCRLs()`
     */
    @Suppress("NestedBlockDepth")
    @Throws(CertPathValidatorException::class)
    private fun checkApprovedCRLs(cert: X509Certificate, approvedCRLs: Set<X509CRL>) {
        // See if the cert is in the set of approved crls.
        logger.debug("ExternalSourceRevocationChecker.checkApprovedCRLs() cert SN: ${cert.serialNumber}")

        for (crl in approvedCRLs) {
            val entry = crl.getRevokedCertificate(cert)
            if (entry != null) {
                logger.debug("ExternalSourceRevocationChecker.checkApprovedCRLs() CRL entry: $entry")

                /*
                 * Abort CRL validation and throw exception if there are any
                 * unrecognized critical CRL entry extensions (see section
                 * 5.3 of RFC 5280).
                 */
                val unresCritExts = entry.criticalExtensionOIDs
                if (unresCritExts != null && !unresCritExts.isEmpty()) {
                    /* remove any that we will process */
                    unresCritExts.remove(Extension.cRLDistributionPoints.id)
                    unresCritExts.remove(Extension.certificateIssuer.id)
                    if (!unresCritExts.isEmpty()) {
                        throw CertPathValidatorException(
                                "Unrecognized critical extension(s) in revoked CRL entry: $unresCritExts")
                    }
                }

                val reasonCode = entry.revocationReason ?: CRLReason.UNSPECIFIED
                val revocationDate = entry.revocationDate
                if (revocationDate.before(dateSource())) {
                    val t = CertificateRevokedException(
                            revocationDate, reasonCode,
                            crl.issuerX500Principal, mutableMapOf())
                    throw CertPathValidatorException(
                            t.message, t, null, -1, CertPathValidatorException.BasicReason.REVOKED)
                }
            }
        }
    }

    override fun isForwardCheckingSupported(): Boolean {
        return true
    }

    override fun getSupportedExtensions(): MutableSet<String>? {
        return null
    }

    override fun init(forward: Boolean) {
        // Nothing to do
    }

    override fun getSoftFailExceptions(): MutableList<CertPathValidatorException> {
        return LinkedList()
    }
}