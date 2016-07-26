package com.r3corda.core.crypto

import sun.security.util.HostnameChecker
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import java.security.KeyStore
import java.security.Provider
import java.security.Security
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * Call this to change the default verification algorithm and this use the WhitelistTrustManager
 * implementation. This is a work around to the fact that ArtemisMQ and probably many other libraries
 * don't correctly configure the SSLParameters with setEndpointIdentificationAlgorithm and thus don't check
 * that the certificate matches with the DNS entry requested. This exposes us to man in the middle attacks.
 */
fun registerWhitelistTrustManager() {
    if (Security.getProvider("WhitelistTrustManager") == null) {
        Security.addProvider(WhitelistTrustManagerProvider)
    }
}

/**
 * Custom Security Provider that forces the TrustManagerFactory to be our custom one.
 * Also holds the identity of the original TrustManager algorithm so
 * that we can delegate most of the checking to the proper Java code. We simply add some more checks.
 *
 * The whitelist automatically includes the local server DNS name and IP address
 *
 */
object WhitelistTrustManagerProvider : Provider("WhitelistTrustManager",
        1.0,
        "Provider for  custom trust manager that always validates certificate names") {

    val originalTrustProviderAlgorithm = Security.getProperty("ssl.TrustManagerFactory.algorithm")

    private val _whitelist = mutableSetOf<String>()
    val whitelist: Set<String> get() = _whitelist.toSet() // The acceptable IP and DNS names for clients and servers.

    init {
        // Add ourselves to whitelist
        val host = InetAddress.getLocalHost()
        addWhitelistEntry(host.hostName)

        // Register our custom TrustManagerFactorySpi
        put("TrustManagerFactory.whitelistTrustManager", "com.r3corda.core.crypto.WhitelistTrustManagerSpi")

        // Forcibly change the TrustManagerFactory defaultAlgorithm to be us
        // This will apply to all code using TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        // Which includes the standard HTTPS implementation and most other SSL code
        // TrustManagerFactory.getInstance(WhitelistTrustManagerProvider.originalTrustProviderAlgorithm)) will
        // Allow access to the original implementation which is normally "PKIX"
        Security.setProperty("ssl.TrustManagerFactory.algorithm", "whitelistTrustManager")
    }

    /**
     * Adds an extra name to the whitelist if not already present
     * If this is a new entry it will internally request a DNS lookup which may block the calling thread.
     */
    fun addWhitelistEntry(serverName: String) {
        synchronized(WhitelistTrustManagerProvider) {
            if (!_whitelist.contains(serverName)) {
                addWhitelistEntries(listOf(serverName))
            }
        }
    }

    /**
     * Adds a list of servers to the whitelist and also adds their fully resolved name after DNS lookup
     * If the server name is not an actual DNS name this is silently ignored
     * The DNS request may block the calling thread.
     */
    fun addWhitelistEntries(serverNames: List<String>) {
        synchronized(WhitelistTrustManagerProvider) {
            _whitelist.addAll(serverNames)
            for (name in serverNames) {
                try {
                    val addresses = InetAddress.getAllByName(name).toList()
                    _whitelist.addAll(addresses.map { y -> y.canonicalHostName })
                    _whitelist.addAll(addresses.map { y -> y.hostAddress })
                } catch (ex: UnknownHostException) {
                    // Ignore if the server name is not resolvable e.g. for wildcard addresses, or addresses that can only be resolved externally
                }
            }
        }
    }
}

/**
 * Registered TrustManagerFactorySpi
 */
class WhitelistTrustManagerSpi : TrustManagerFactorySpi() {
    // Get the original implementation to delegate to (can't use Kotlin delegation on abstract classes unfortunately).
    val originalProvider = TrustManagerFactory.getInstance(WhitelistTrustManagerProvider.originalTrustProviderAlgorithm)

    override fun engineInit(keyStore: KeyStore?) {
        originalProvider.init(keyStore)
    }

    override fun engineInit(managerFactoryParameters: ManagerFactoryParameters?) {
        originalProvider.init(managerFactoryParameters)
    }

    override fun engineGetTrustManagers(): Array<out TrustManager> {
        val parent = originalProvider.trustManagers.first() as X509ExtendedTrustManager
        // Wrap original provider in ours and return
        return arrayOf(WhitelistTrustManager(parent))
    }
}

/**
 * Our TrustManager extension takes the standard certificate checker and first delegates all the
 * chain checking to that. If everything is well formed we then simply add a check against our whitelist
 */
class WhitelistTrustManager(val originalProvider: X509ExtendedTrustManager) : X509ExtendedTrustManager() {
    // Use same Helper class as standard HTTPS library validator
    val checker = HostnameChecker.getInstance(HostnameChecker.TYPE_TLS)

    private fun checkIdentity(hostname: String?, cert: X509Certificate) {
        // Based on standard code in sun.security.ssl.X509TrustManagerImpl.checkIdentity
        // if IPv6 strip off the "[]"
        if ((hostname != null) && hostname.startsWith("[") && hostname.endsWith("]")) {
            checker.match(hostname.substring(1, hostname.length - 1), cert)
        } else {
            checker.match(hostname, cert)
        }
    }

    /**
     * scan whitelist and confirm the certificate matches at least one entry
     */
    private fun checkWhitelist(cert: X509Certificate) {
        for (whiteListEntry in WhitelistTrustManagerProvider.whitelist) {
            try {
                checkIdentity(whiteListEntry, cert)
                return // if we get here without throwing we had a match
            } catch(ex: CertificateException) {
                // Ignore and check the next entry until we find a match, or exhaust the whitelist
            }
        }
        throw CertificateException("Certificate not on whitelist ${cert.subjectDN}")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) {
        originalProvider.checkClientTrusted(chain, authType, socket)
        checkWhitelist(chain[0])
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) {
        originalProvider.checkClientTrusted(chain, authType, engine)
        checkWhitelist(chain[0])
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
        originalProvider.checkClientTrusted(chain, authType)
        checkWhitelist(chain[0])
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) {
        originalProvider.checkServerTrusted(chain, authType, socket)
        checkWhitelist(chain[0])
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) {
        originalProvider.checkServerTrusted(chain, authType, engine)
        checkWhitelist(chain[0])
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        originalProvider.checkServerTrusted(chain, authType)
        checkWhitelist(chain[0])
    }

    override fun getAcceptedIssuers(): Array<out X509Certificate> {
        return originalProvider.acceptedIssuers
    }
}
