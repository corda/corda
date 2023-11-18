package net.corda.nodeapi.internal.revocation

import net.corda.core.utilities.Try
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.DEV_CA_PRIVATE_KEY_PASS
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.internal.fixedCrlSource
import org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory
import org.junit.Test
import java.math.BigInteger
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CordaRevocationCheckerTest {

    @Test(timeout=300_000)
	fun checkRevoked() {
        val checkResult = performCheckOnDate(LocalDate.of(2019, 9, 27))
        val failedChecks = checkResult.filterNot { it.second.isSuccess }
        assertEquals(1, failedChecks.size)
        assertEquals(BigInteger.valueOf(8310484079152632582), failedChecks.first().first.serialNumber)
    }

    @Test(timeout=300_000)
	fun checkTooEarly() {
        val checkResult = performCheckOnDate(LocalDate.of(2019, 8, 27))
        assertTrue(checkResult.all { it.second.isSuccess })
    }

    private fun performCheckOnDate(date: LocalDate): List<Pair<X509Certificate, Try<Unit>>> {
        val certStore = CertificateStore.fromResource(
                "net/corda/nodeapi/internal/protonwrapper/netty/sslkeystore_Revoked.jks",
                DEV_CA_KEY_STORE_PASS, DEV_CA_PRIVATE_KEY_PASS)

        val resourceAsStream = javaClass.getResourceAsStream("/net/corda/nodeapi/internal/protonwrapper/netty/doorman.crl")
        val crl = CertificateFactory().engineGenerateCRL(resourceAsStream) as X509CRL

        val checker = CordaRevocationChecker(
                crlSource = fixedCrlSource(setOf(crl)),
                softFail = true,
                dateSource = { Date.from(date.atStartOfDay().toInstant(ZoneOffset.UTC)) }
        )

        return certStore.query {
            getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).map {
                Pair(it, Try.on { checker.check(it, mutableListOf()) })
            }
        }
    }
}