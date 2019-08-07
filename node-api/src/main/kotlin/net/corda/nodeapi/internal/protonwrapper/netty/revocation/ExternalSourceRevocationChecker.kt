package net.corda.nodeapi.internal.protonwrapper.netty.revocation

import net.corda.nodeapi.internal.protonwrapper.netty.ExternalCrlSource
import sun.security.util.Debug
import sun.security.x509.PKIXExtensions.CertificateIssuer_Id
import sun.security.x509.PKIXExtensions.ReasonCode_Id
import sun.security.x509.X509CRLEntryImpl
import java.security.cert.*
import java.util.*

/**
 * Implementation of [PKIXRevocationChecker] which determines whether certificate is revoked using [externalCrlSource] which knows how to
 * obtain a set of CRLs for a given certificate from an external source
 */
class ExternalSourceRevocationChecker(private val externalCrlSource: ExternalCrlSource, private val dateSource: () -> Date) : PKIXRevocationChecker() {

    companion object {
        private val debug: Debug? = Debug.getInstance("certpath")
    }

    override fun check(cert: Certificate, unresolvedCritExts: MutableCollection<String>?) {
        val x509Certificate = cert as X509Certificate
        checkApprovedCRLs(x509Certificate, externalCrlSource.fetch(x509Certificate))
    }

    /**
     * Borrowed from `RevocationChecker.checkApprovedCRLs()`
     */
    @Throws(CertPathValidatorException::class)
    private fun checkApprovedCRLs(cert: X509Certificate, approvedCRLs: Set<X509CRL>) {
        // See if the cert is in the set of approved crls.
        if (debug != null) {
            val sn = cert.serialNumber
            debug.println("ExternalSourceRevocationChecker.checkApprovedCRLs() cert SN: $sn")
        }

        for (crl in approvedCRLs) {
            val e = crl.getRevokedCertificate(cert)
            if (e != null) {
                val entry = try {
                    X509CRLEntryImpl.toImpl(e)
                } catch (ce: CRLException) {
                    throw CertPathValidatorException(ce)
                }

                if (debug != null) {
                    debug.println("ExternalSourceRevocationChecker.checkApprovedCRLs() CRL entry: $entry")
                }

                /*
                 * Abort CRL validation and throw exception if there are any
                 * unrecognized critical CRL entry extensions (see section
                 * 5.3 of RFC 5280).
                 */
                val unresCritExts = entry.criticalExtensionOIDs
                if (unresCritExts != null && !unresCritExts.isEmpty()) {
                    /* remove any that we will process */
                    unresCritExts.remove(ReasonCode_Id.toString())
                    unresCritExts.remove(CertificateIssuer_Id.toString())
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
                            crl.issuerX500Principal, entry.extensions)
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