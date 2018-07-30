package net.corda.nodeapi.internal.revocation

import net.corda.core.internal.openHttpConnection
import net.corda.core.utilities.hours
import net.corda.core.utilities.seconds
import org.apache.commons.io.FileUtils
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.asn1.x509.Extension
import sun.security.util.Cache
import sun.security.util.Debug
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import java.security.Provider
import java.security.cert.*
import java.security.cert.Certificate
import java.time.Duration
import java.time.Instant
import net.corda.core.internal.div

class CordaCrlStore(params: CordaCertStoreParameters) : CertStoreSpi(params) {

    companion object {
        private val debug = Debug.getInstance("certpath")
        private val CACHE_SIZE = 185

        /**
         * Returns a URI CertStore. This method consults a cache of
         * CertStores (shared per JVM) using the URL as a key.
         */
        private val certStoreCache = Cache.newSoftMemoryCache<CordaCertStoreParameters, CertStore>(CACHE_SIZE)

        @Synchronized
        fun getInstance(params: CordaCertStoreParameters): CertStore {
            debug?.println("CertStore URI: ${params.url}")
            var cs = certStoreCache.get(params)
            if (cs == null) {
                cs = CordaCertStoreInternal(CordaCrlStore(params), null, "URL", params)
                certStoreCache.put(params, cs)
            } else {
                debug?.println("CordaCertStore.getInstance: cache hit")
            }
            return cs
        }

        @Synchronized
        fun getInstanceFromCertificate(certificate: X509Certificate, crlDirectory: Path): CertStore? {
            val crlURIs = getCrlDistributionPoints(certificate)
            if (crlURIs.isEmpty()) {
                return null
            }
            return getInstance(CordaCertStoreParameters(url = URL(crlURIs.first()), crlDirectory = crlDirectory))
        }

        /**
         * Extracts all CRL distribution point URLs from the
         * "CRL Distribution Point" extension in a X.509 certificate. If CRL
         * distribution point extension is unavailable, returns an empty list.
         */
        private fun getCrlDistributionPoints(certificate: X509Certificate): List<String> {
            val crlDpExt = certificate.getExtensionValue(Extension.cRLDistributionPoints.id)
            crlDpExt ?: return emptyList()
            val dosCrlDP = ASN1InputStream(crlDpExt.inputStream()).readObject() as DEROctetString
            val oAsnInStream = ASN1InputStream(dosCrlDP.octets.inputStream())
            val distPoint = CRLDistPoint.getInstance(oAsnInStream.readObject())
            val crlUrls = arrayListOf<String>()
            distPoint.distributionPoints.forEach {
                it.distributionPoint?.let {
                    if (it.type == DistributionPointName.FULL_NAME) {
                        GeneralNames.getInstance(it.name).names.forEach {
                            if (it.tagNo == GeneralName.uniformResourceIdentifier) {
                                crlUrls.add(DERIA5String.getInstance(it.name).string)
                            }
                        }
                    }
                }
            }
            return crlUrls
        }
    }

    private var lastChecked = 0L
    private var lastModified = 0L
    private val connectionTimeout = params.connectionTimeout.toMillis().toInt()
    private val cacheTimeout = params.cacheTimeout.toMillis()
    private val crlFile = params.crlDirectory?.let { it / extractFileNameFromUrl(params.url) }
    private val factory = CertificateFactory.getInstance("X.509")

    private var crl: X509CRL? = null
    private val url = params.url

    init {
        crlFile?.retrieveCrlFromFile()
    }

    override fun engineGetCRLs(selector: CRLSelector?): MutableCollection<out CRL> {
        ensureUpToDateCrl()
        return getMatchingCrls(selector)
    }

    private fun ensureUpToDateCrl() {
        val time = Instant.now().toEpochMilli()
        if (time - lastChecked < cacheTimeout) {
            debug?.println("Returning CRL from cache")
            return
        }
        lastChecked = time
        try {
            val connection = url.openHttpConnection()
            if (lastModified != 0L) {
                connection.ifModifiedSince = lastModified
            }
            connection.connectTimeout = connectionTimeout
            val oldLastModified = lastModified
            connection.inputStream.use {
                lastModified = connection.lastModified
                if (oldLastModified != 0L) {
                    if (oldLastModified == lastModified) {
                        debug?.println("Not modified, using cached copy")
                        return
                    } else {
                        // some proxy servers omit last modified
                        if (connection.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                            debug?.println("Not modified, using cached copy")
                            return
                        }
                    }
                }
                debug?.println("Downloading new CRL...")
                crl = factory.generateCRL(it) as X509CRL
                crlFile?.persistCrlToFile()
            }
        } catch (e: Exception) {
            debug?.let {
                it.println("Exception fetching CRL from the URL ($url):")
                e.printStackTrace()
            }
            //This will cause the fetching to occur every time the CRL is queried by the caller.
            lastChecked = 0L
            crlFile ?: throw CertStoreException(e)
        }
    }

    /**
     * Retrieves the CRL from file
     */
    private fun Path.retrieveCrlFromFile() {
        try {
            debug?.println("Loading the CRL from file $this...")
            crl = factory.generateCRL(FileUtils.readFileToByteArray(toFile()).inputStream()) as X509CRL
        } catch (e: Exception) {
            debug?.println("Unable to load the CRL from file $this")
        }
    }

    /**
     * Persists the CRL to file
     */
    private fun Path.persistCrlToFile() {
        try {
            crl?.let {
                debug?.println("Persisting the CRL to file $this...")
                FileUtils.writeByteArrayToFile(toFile(), it.encoded)
            }
        } catch (e: Exception) {
            debug?.println("Unable to persist the CRL to file $this")
        }
    }

    /**
     * Checks if the specified X509CRL matches the criteria specified in the
     * CRLSelector.
     */
    private fun getMatchingCrls(selector: CRLSelector?): MutableCollection<out CRL> {
        crl?.let {
            if (selector != null && selector.match(it)) {
                return hashSetOf(it)
            }
        }
        return hashSetOf()
    }

    private fun extractFileNameFromUrl(url: URL): String {
        val result = url.toString().substringAfterLast("/")
        return if (result.isEmpty()) {
            // in case the URL ends with '/'
            url.toString().substringBeforeLast("/").substringAfterLast("/")
        } else {
            result
        }
    }

    /**
     * This store is purely for the CRL, therefore an empty collection is returned in case of certificates.
     */
    override fun engineGetCertificates(selector: CertSelector?): MutableCollection<out Certificate> = hashSetOf()

    data class CordaCertStoreParameters(val cacheTimeout: Duration = 24.hours,
                                        val connectionTimeout: Duration = 5.seconds,
                                        val url: URL,
                                        val crlDirectory: Path?) : CertStoreParameters {

        override fun clone(): Any = copy()
    }

    /**
     * This class allows the CordaCrlStore to be accessed as a CertStore.
     */
    private class CordaCertStoreInternal(spi: CertStoreSpi, p: Provider?, type: String, params: CertStoreParameters): CertStore(spi, p, type, params)
}