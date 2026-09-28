package net.corda.nodeapi.internal.revocation

import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.crypto.toSimpleString
import net.corda.nodeapi.internal.protonwrapper.netty.CrlSource
import org.bouncycastle.asn1.x509.Extension
import java.security.cert.CRLReason
import java.security.cert.CertPathValidatorException
import java.security.cert.CertPathValidatorException.BasicReason
import java.security.cert.Certificate
import java.security.cert.CertificateRevokedException
import java.security.cert.PKIXRevocationChecker
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.*
import kotlin.collections.ArrayList

/**
 * Custom [PKIXRevocationChecker] which delegates to a plugable [CrlSource] to retrieve the CRLs for certificate revocation checks.
 */
class CordaRevocationChecker(private val crlSource: CrlSource,
                             private val softFail: Boolean,
                             private val dateSource: () -> Date = ::Date) : PKIXRevocationChecker() {
    companion object {
        private val logger = contextLogger()
    }

    private val softFailExceptions = ArrayList<CertPathValidatorException>()

    override fun check(cert: Certificate, unresolvedCritExts: MutableCollection<String>?) {
        cert as X509Certificate
        checkApprovedCRLs(cert, getCRLs(cert))
    }

    private fun getCRLs(cert: X509Certificate): Set<X509CRL> {
        val crls = try {
            crlSource.fetch(cert)
        } catch (e: Exception) {
            if (softFail) {
                addSoftFailException(e)
                return emptySet()
            } else {
                throw undeterminedRevocationException("Unable to retrieve CRLs for cert ${cert.serialNumber}", e)
            }
        }
        if (crls.isNotEmpty() || softFail) {
            return crls
        }
        // Note, the JDK tries to find a valid CRL from a different signing key before giving up (RevocationChecker.verifyWithSeparateSigningKey)
        throw undeterminedRevocationException("Could not find any valid CRLs for cert ${cert.serialNumber}", null)
    }

    /**
     * Borrowed from `RevocationChecker.checkApprovedCRLs()`
     */
    @Suppress("NestedBlockDepth")
    private fun checkApprovedCRLs(cert: X509Certificate, approvedCRLs: Set<X509CRL>) {
        // See if the cert is in the set of approved crls.
        logger.debug { "Check cert ${cert.serialNumber} against CRLs ${approvedCRLs.map { it.toSimpleString() }}" }

        for (crl in approvedCRLs) {
            val entry = crl.getRevokedCertificate(cert)
            if (entry != null) {
                /*
                 * Abort CRL validation and throw exception if there are any
                 * unrecognized critical CRL entry extensions (see section
                 * 5.3 of RFC 5280).
                 */
                val unresCritExts = entry.criticalExtensionOIDs
                if (unresCritExts != null && unresCritExts.isNotEmpty()) {
                    /* remove any that we will process */
                    unresCritExts.remove(Extension.cRLDistributionPoints.id)
                    unresCritExts.remove(Extension.certificateIssuer.id)
                    if (unresCritExts.isNotEmpty()) {
                        throw CertPathValidatorException("Unrecognized critical extension(s) in revoked CRL entry: $unresCritExts")
                    }
                }

                val reasonCode = entry.revocationReason ?: CRLReason.UNSPECIFIED
                val revocationDate = entry.revocationDate
                if (revocationDate.before(dateSource())) {
                    val t = CertificateRevokedException(revocationDate, reasonCode, crl.issuerX500Principal, emptyMap())
                    throw CertPathValidatorException(t.message, t, null, -1, BasicReason.REVOKED)
                }
            }
        }
    }

    /**
     * This is set to false intentionally for security reasons.
     * It ensures that certificates are provided in reverse direction (from most-trusted CA to target certificate)
     * after the necessary validation checks have already been performed.
     *
     * If that wasn't the case, we could be reaching out to CRL endpoints for invalid certificates, which would open security holes
     * e.g. systems that are not part of a Corda network could force a Corda firewall to initiate outbound requests to systems under their control.
     */
    override fun isForwardCheckingSupported(): Boolean {
        return false
    }

    override fun getSupportedExtensions(): Set<String>? {
        return null
    }

    override fun init(forward: Boolean) {
        if (forward) {
            throw CertPathValidatorException("Forward checking not allowed")
        }
        softFailExceptions.clear()
    }

    override fun getSoftFailExceptions(): List<CertPathValidatorException> {
        return Collections.unmodifiableList(softFailExceptions)
    }

    private fun addSoftFailException(e: Exception) {
        logger.debug("Soft fail exception", e)
        softFailExceptions += undeterminedRevocationException(e.message, e)
    }

    private fun undeterminedRevocationException(message: String?, cause: Throwable?): CertPathValidatorException {
        return CertPathValidatorException(message, cause, null, -1, BasicReason.UNDETERMINED_REVOCATION_STATUS)
    }
}
