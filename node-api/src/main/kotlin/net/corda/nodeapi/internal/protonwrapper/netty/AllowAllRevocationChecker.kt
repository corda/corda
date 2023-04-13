package net.corda.nodeapi.internal.protonwrapper.netty

import net.corda.core.utilities.debug
import org.slf4j.LoggerFactory
import java.security.cert.CertPathValidatorException
import java.security.cert.Certificate
import java.security.cert.PKIXRevocationChecker
import java.util.*

object AllowAllRevocationChecker : PKIXRevocationChecker() {

    private val logger = LoggerFactory.getLogger(AllowAllRevocationChecker::class.java)

    override fun check(cert: Certificate, unresolvedCritExts: Collection<String>) {
        logger.debug {"Passing certificate check for: $cert"}
        // Nothing to do
    }

    override fun isForwardCheckingSupported(): Boolean {
        return true
    }

    override fun getSupportedExtensions(): Set<String>? {
        return null
    }

    override fun init(forward: Boolean) {
        // Nothing to do
    }

    override fun getSoftFailExceptions(): List<CertPathValidatorException> {
        return Collections.emptyList()
    }
}