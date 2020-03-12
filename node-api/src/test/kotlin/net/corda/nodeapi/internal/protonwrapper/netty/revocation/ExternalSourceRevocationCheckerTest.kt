package net.corda.nodeapi.internal.protonwrapper.netty.revocation

import net.corda.core.utilities.Try
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.DEV_CA_PRIVATE_KEY_PASS
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.protonwrapper.netty.ExternalCrlSource
import org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory
import org.junit.Test
import java.math.BigInteger

import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.sql.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalSourceRevocationCheckerTest {

    @Test(timeout=300_000)
	fun checkRevoked() {
        val checkResult = performCheckOnDate(Date.valueOf("2019-09-27"))
        val failedChecks = checkResult.filterNot { it.second.isSuccess }
        assertEquals(1, failedChecks.size)
        assertEquals(BigInteger.valueOf(8310484079152632582), failedChecks.first().first.serialNumber)
    }

    @Test(timeout=300_000)
	fun checkTooEarly() {
        val checkResult = performCheckOnDate(Date.valueOf("2019-08-27"))
        assertTrue(checkResult.all { it.second.isSuccess })
    }

    private fun performCheckOnDate(date: Date): List<Pair<X509Certificate, Try<Unit>>> {
        val certStore = CertificateStore.fromResource(
                "net/corda/nodeapi/internal/protonwrapper/netty/sslkeystore_Revoked.jks",
                DEV_CA_KEY_STORE_PASS, DEV_CA_PRIVATE_KEY_PASS)

        val resourceAsStream = javaClass.getResourceAsStream("/net/corda/nodeapi/internal/protonwrapper/netty/doorman.crl")
        val crl = CertificateFactory().engineGenerateCRL(resourceAsStream) as X509CRL

        //val crlHolder = X509CRLHolder(resourceAsStream)
        //crlHolder.revokedCertificates as X509CRLEntryHolder

        val instance = ExternalSourceRevocationChecker(object : ExternalCrlSource {
            override fun fetch(certificate: X509Certificate): Set<X509CRL> = setOf(crl)
        }) { date }

        return certStore.query {
            getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).map {
                Pair(it, Try.on { instance.check(it, mutableListOf()) })
            }
        }
    }
}