package net.corda.nodeapi.internal.revocation

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.internal.readFully
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.toSimpleString
import net.corda.nodeapi.internal.protonwrapper.netty.CrlSource
import net.corda.nodeapi.internal.protonwrapper.netty.distributionPoints
import java.net.URI
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.time.Duration
import javax.security.auth.x500.X500Principal

/**
 * [CrlSource] which downloads CRLs from the distribution points in the X509 certificate and caches them.
 */
class CertDistPointCrlSource(cacheSize: Long = DEFAULT_CACHE_SIZE,
                             cacheExpiry: Duration = DEFAULT_CACHE_EXPIRY,
                             private val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
                             private val readTimeout: Duration = DEFAULT_READ_TIMEOUT) : CrlSource {
    companion object {
        private val logger = contextLogger()

        // The default SSL handshake timeout is 60s (DEFAULT_SSL_HANDSHAKE_TIMEOUT). Considering there are 3 CRLs endpoints to check in a
        // node handshake, we want to keep the total timeout within that.
        private val DEFAULT_CONNECT_TIMEOUT = 9.seconds
        private val DEFAULT_READ_TIMEOUT = 9.seconds
        private const val DEFAULT_CACHE_SIZE = 185L  // Same default as the JDK (URICertStore)
        private val DEFAULT_CACHE_EXPIRY = 5.minutes

        val SINGLETON = CertDistPointCrlSource(
                cacheSize = java.lang.Long.getLong("net.corda.dpcrl.cache.size", DEFAULT_CACHE_SIZE),
                cacheExpiry = java.lang.Long.getLong("net.corda.dpcrl.cache.expiry")?.let(Duration::ofMillis) ?: DEFAULT_CACHE_EXPIRY,
                connectTimeout = java.lang.Long.getLong("net.corda.dpcrl.connect.timeout")?.let(Duration::ofMillis) ?: DEFAULT_CONNECT_TIMEOUT,
                readTimeout = java.lang.Long.getLong("net.corda.dpcrl.read.timeout")?.let(Duration::ofMillis) ?: DEFAULT_READ_TIMEOUT
        )
    }

    private val cache: LoadingCache<URI, X509CRL> = Caffeine.newBuilder()
            .maximumSize(cacheSize)
            .expireAfterWrite(cacheExpiry)
            .build(::retrieveCRL)

    private fun retrieveCRL(uri: URI): X509CRL {
        val start = System.currentTimeMillis()
        val bytes = try {
            val conn = uri.toURL().openConnection()
            conn.connectTimeout = connectTimeout.toMillis().toInt()
            conn.readTimeout = readTimeout.toMillis().toInt()
            // Read all bytes first and then pass them into the CertificateFactory. This may seem unnecessary when generateCRL already takes
            // in an InputStream, but the JDK implementation (sun.security.provider.X509Factory.engineGenerateCRL) converts any IOException
            // into CRLException and drops the cause chain.
            conn.getInputStream().readFully()
        } catch (e: Exception) {
            if (logger.isDebugEnabled) {
                logger.debug("Unable to download CRL from $uri (${System.currentTimeMillis() - start}ms)", e)
            }
            throw e
        }
        val duration = System.currentTimeMillis() - start
        val crl = try {
            X509CertificateFactory().generateCRL(bytes.inputStream())
        } catch (e: Exception) {
            if (logger.isDebugEnabled) {
                logger.debug("Invalid CRL from $uri (${duration}ms)", e)
            }
            throw e
        }
        logger.debug { "CRL from $uri (${duration}ms): ${crl.toSimpleString()}" }
        return crl
    }

    fun clearCache() {
        cache.invalidateAll()
    }

    override fun fetch(certificate: X509Certificate): Set<X509CRL> {
        val approvedCRLs = HashSet<X509CRL>()
        var exception: Exception? = null
        for ((distPointUri, issuerNames) in certificate.distributionPoints()) {
            try {
                val possibleCRL = getPossibleCRL(distPointUri)
                if (verifyCRL(possibleCRL, certificate, issuerNames)) {
                    approvedCRLs += possibleCRL
                }
            } catch (e: Exception) {
                if (exception == null) {
                    exception = e
                } else {
                    exception.addSuppressed(e)
                }
            }
        }
        // Only throw if no CRLs are retrieved
        if (exception != null && approvedCRLs.isEmpty()) {
            throw exception
        } else {
            return approvedCRLs
        }
    }

    private fun getPossibleCRL(uri: URI): X509CRL {
        return cache[uri]!!
    }

    // DistributionPointFetcher.verifyCRL
    private fun verifyCRL(crl: X509CRL, certificate: X509Certificate, distPointIssuerNames: List<X500Principal>?): Boolean {
        val crlIssuer = crl.issuerX500Principal
        return distPointIssuerNames?.any { it == crlIssuer } ?: (certificate.issuerX500Principal == crlIssuer)
    }
}
