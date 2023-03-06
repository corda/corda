package net.corda.nodeapi.internal.protonwrapper.netty

import io.netty.buffer.ByteBufAllocator
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SniHandler
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.SslProvider
import io.netty.util.DomainWildcardMappingBuilder
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.VisibleForTesting
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.toHex
import net.corda.nodeapi.internal.ArtemisTcpTransport
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.toBc
import net.corda.nodeapi.internal.crypto.x509
import net.corda.nodeapi.internal.protonwrapper.netty.revocation.ExternalSourceRevocationChecker
import org.bouncycastle.asn1.ASN1IA5String
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.Socket
import java.security.KeyStore
import java.security.cert.*
import java.util.*
import java.util.concurrent.Executor
import javax.net.ssl.*
import kotlin.system.measureTimeMillis

private const val HOSTNAME_FORMAT = "%s.corda.net"
internal const val DEFAULT = "default"

internal const val DP_DEFAULT_ANSWER = "NO CRLDP ext"

internal val logger = LoggerFactory.getLogger("net.corda.nodeapi.internal.protonwrapper.netty.SSLHelper")

fun X509Certificate.distributionPoints() : Set<String>? {
    logger.debug("Checking CRLDPs for $subjectX500Principal")

    val crldpExtBytes = getExtensionValue(Extension.cRLDistributionPoints.id)
    if (crldpExtBytes == null) {
        logger.debug(DP_DEFAULT_ANSWER)
        return emptySet()
    }

    val derObjCrlDP = ASN1InputStream(ByteArrayInputStream(crldpExtBytes)).readObject()
    val dosCrlDP = derObjCrlDP as? DEROctetString
    if (dosCrlDP == null) {
        logger.error("Expected to have DEROctetString, actual type: ${derObjCrlDP.javaClass}")
        return emptySet()
    }
    val crldpExtOctetsBytes = dosCrlDP.octets
    val dpObj = ASN1InputStream(ByteArrayInputStream(crldpExtOctetsBytes)).readObject()
    val distPoint = CRLDistPoint.getInstance(dpObj)
    if (distPoint == null) {
        logger.error("Could not instantiate CRLDistPoint, from: $dpObj")
        return emptySet()
    }

    val dpNames = distPoint.distributionPoints.mapNotNull { it.distributionPoint }.filter { it.type == DistributionPointName.FULL_NAME }
    val generalNames = dpNames.flatMap { GeneralNames.getInstance(it.name).names.asList() }
    return generalNames.filter { it.tagNo == GeneralName.uniformResourceIdentifier}.map { ASN1IA5String.getInstance(it.name).string }.toSet()
}

fun X509Certificate.distributionPointsToString() : String {
    return with(distributionPoints()) {
        if(this == null || isEmpty()) {
            DP_DEFAULT_ANSWER
        } else {
            sorted().joinToString()
        }
    }
}

fun certPathToString(certPath: Array<out X509Certificate>?): String {
    if (certPath == null) {
        return "<empty certpath>"
    }
    val certs = certPath.map {
        val bcCert = it.toBc()
        val subject = bcCert.subject.toString()
        val issuer = bcCert.issuer.toString()
        val keyIdentifier = try {
            SubjectKeyIdentifier.getInstance(bcCert.getExtension(Extension.subjectKeyIdentifier).parsedValue).keyIdentifier.toHex()
        } catch (ex: Exception) {
            "null"
        }
        val authorityKeyIdentifier = try {
            AuthorityKeyIdentifier.getInstance(bcCert.getExtension(Extension.authorityKeyIdentifier).parsedValue).keyIdentifier.toHex()
        } catch (ex: Exception) {
            "null"
        }
        "  $subject[$keyIdentifier] issued by $issuer[$authorityKeyIdentifier] [${it.distributionPointsToString()}]"
    }
    return certs.joinToString("\r\n")
}

@VisibleForTesting
class LoggingTrustManagerWrapper(val wrapped: X509ExtendedTrustManager) : X509ExtendedTrustManager() {
    companion object {
        val log = contextLogger()
    }

    private fun certPathToStringFull(chain: Array<out X509Certificate>?): String {
        if (chain == null) {
            return "<empty certpath>"
        }
        return chain.map { it.toString() }.joinToString(", ")
    }

    private fun logErrors(chain: Array<out X509Certificate>?, block: () -> Unit) {
        try {
            block()
        } catch (ex: CertificateException) {
            log.error("Bad certificate path ${ex.message}:\r\n${certPathToStringFull(chain)}")
            throw ex
        }
    }

    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        log.info("Check Client Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkClientTrusted(chain, authType, socket) }
    }

    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        log.info("Check Client Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkClientTrusted(chain, authType, engine) }
    }

    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        log.info("Check Client Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkClientTrusted(chain, authType) }
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        log.info("Check Server Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkServerTrusted(chain, authType, socket) }
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        log.info("Check Server Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkServerTrusted(chain, authType, engine) }
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        log.info("Check Server Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkServerTrusted(chain, authType) }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = wrapped.acceptedIssuers

}

private object LoggingImmediateExecutor : Executor {

    override fun execute(command: Runnable?) {
        val log = LoggerFactory.getLogger(javaClass)

        if (command == null) {
            log.error("SSL handler executor called with a null command")
            throw NullPointerException("command")
        }

        @Suppress("TooGenericExceptionCaught", "MagicNumber") // log and rethrow all exceptions
        try {
            val commandName = command::class.qualifiedName?.let { "[$it]" } ?: ""
            log.debug("Entering SSL command $commandName")
            val elapsedTime = measureTimeMillis { command.run() }
            log.debug("Exiting SSL command $elapsedTime millis")
            if (elapsedTime > 100) {
                log.info("Command: $commandName took $elapsedTime millis to execute")
            }
        }
        catch (ex: Exception) {
            log.error("Caught exception in SSL handler executor", ex)
            throw ex
        }
    }
}

internal fun createClientSslHelper(target: NetworkHostAndPort,
                                   expectedRemoteLegalNames: Set<CordaX500Name>,
                                   keyManagerFactory: KeyManagerFactory,
                                   trustManagerFactory: TrustManagerFactory): SslHandler {
    val sslContext = createAndInitSslContext(keyManagerFactory, trustManagerFactory)
    val sslEngine = sslContext.createSSLEngine(target.host, target.port)
    sslEngine.useClientMode = true
    sslEngine.enabledProtocols = ArtemisTcpTransport.TLS_VERSIONS.toTypedArray()
    sslEngine.enabledCipherSuites = ArtemisTcpTransport.CIPHER_SUITES.toTypedArray()
    sslEngine.enableSessionCreation = true
    if (expectedRemoteLegalNames.size == 1) {
        val sslParameters = sslEngine.sslParameters
        sslParameters.serverNames = listOf(SNIHostName(x500toHostName(expectedRemoteLegalNames.single())))
        sslEngine.sslParameters = sslParameters
    }
    @Suppress("DEPRECATION")
    return SslHandler(sslEngine, false, LoggingImmediateExecutor)
}

internal fun createClientOpenSslHandler(target: NetworkHostAndPort,
                                        expectedRemoteLegalNames: Set<CordaX500Name>,
                                        keyManagerFactory: KeyManagerFactory,
                                        trustManagerFactory: TrustManagerFactory,
                                        alloc: ByteBufAllocator): SslHandler {
    val sslContext = SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL).keyManager(keyManagerFactory).trustManager(LoggingTrustManagerFactoryWrapper(trustManagerFactory)).build()
    val sslEngine = sslContext.newEngine(alloc, target.host, target.port)
    sslEngine.enabledProtocols = ArtemisTcpTransport.TLS_VERSIONS.toTypedArray()
    sslEngine.enabledCipherSuites = ArtemisTcpTransport.CIPHER_SUITES.toTypedArray()
    if (expectedRemoteLegalNames.size == 1) {
        val sslParameters = sslEngine.sslParameters
        sslParameters.serverNames = listOf(SNIHostName(x500toHostName(expectedRemoteLegalNames.single())))
        sslEngine.sslParameters = sslParameters
    }
    @Suppress("DEPRECATION")
    return SslHandler(sslEngine, false, LoggingImmediateExecutor)
}

internal fun createServerSslHandler(keyStore: CertificateStore,
                                    keyManagerFactory: KeyManagerFactory,
                                    trustManagerFactory: TrustManagerFactory): SslHandler {
    val sslContext = createAndInitSslContext(keyManagerFactory, trustManagerFactory)
    val sslEngine = sslContext.createSSLEngine()
    sslEngine.useClientMode = false
    sslEngine.needClientAuth = true
    sslEngine.enabledProtocols = ArtemisTcpTransport.TLS_VERSIONS.toTypedArray()
    sslEngine.enabledCipherSuites = ArtemisTcpTransport.CIPHER_SUITES.toTypedArray()
    sslEngine.enableSessionCreation = true
    val sslParameters = sslEngine.sslParameters
    sslParameters.sniMatchers = listOf(ServerSNIMatcher(keyStore))
    sslEngine.sslParameters = sslParameters
    @Suppress("DEPRECATION")
    return SslHandler(sslEngine, false, LoggingImmediateExecutor)
}

fun createAndInitSslContext(keyManagerFactory: KeyManagerFactory, trustManagerFactory: TrustManagerFactory): SSLContext {
    val sslContext = SSLContext.getInstance("TLS")
    val keyManagers = keyManagerFactory.keyManagers
    val trustManagers = trustManagerFactory.trustManagers.filterIsInstance(X509ExtendedTrustManager::class.java)
            .map { LoggingTrustManagerWrapper(it) }.toTypedArray()
    sslContext.init(keyManagers, trustManagers, newSecureRandom())
    return sslContext
}

@VisibleForTesting
fun initialiseTrustStoreAndEnableCrlChecking(trustStore: CertificateStore, revocationConfig: RevocationConfig): ManagerFactoryParameters {
    val pkixParams = PKIXBuilderParameters(trustStore.value.internal, X509CertSelector())
    val revocationChecker = when (revocationConfig.mode) {
        RevocationConfig.Mode.OFF -> AllowAllRevocationChecker  // Custom PKIXRevocationChecker skipping CRL check
        RevocationConfig.Mode.EXTERNAL_SOURCE -> {
            require(revocationConfig.externalCrlSource != null) { "externalCrlSource must not be null" }
            ExternalSourceRevocationChecker(revocationConfig.externalCrlSource!!) { Date() } // Custom PKIXRevocationChecker which uses `externalCrlSource`
        }
        else -> {
            val certPathBuilder = CertPathBuilder.getInstance("PKIX")
            val pkixRevocationChecker = certPathBuilder.revocationChecker as PKIXRevocationChecker
            pkixRevocationChecker.options = EnumSet.of(
                    // Prefer CRL over OCSP
                    PKIXRevocationChecker.Option.PREFER_CRLS,
                    // Don't fall back to OCSP checking
                    PKIXRevocationChecker.Option.NO_FALLBACK)
            if (revocationConfig.mode == RevocationConfig.Mode.SOFT_FAIL) {
                // Allow revocation check to succeed if the revocation status cannot be determined for one of
                // the following reasons: The CRL or OCSP response cannot be obtained because of a network error.
                pkixRevocationChecker.options = pkixRevocationChecker.options + PKIXRevocationChecker.Option.SOFT_FAIL
            }
            pkixRevocationChecker
        }
    }
    pkixParams.addCertPathChecker(revocationChecker)
    return CertPathTrustManagerParameters(pkixParams)
}

internal fun createServerOpenSslHandler(keyManagerFactory: KeyManagerFactory,
                                        trustManagerFactory: TrustManagerFactory,
                                        alloc: ByteBufAllocator): SslHandler {

    val sslContext = getServerSslContextBuilder(keyManagerFactory, trustManagerFactory).build()
    val sslEngine = sslContext.newEngine(alloc)
    sslEngine.useClientMode = false
    @Suppress("DEPRECATION")
    return SslHandler(sslEngine, false, LoggingImmediateExecutor)
}

/**
 * Creates a special SNI handler used only when openSSL is used for AMQPServer
 */
internal fun createServerSNIOpenSslHandler(keyManagerFactoriesMap: Map<String, KeyManagerFactory>,
                                           trustManagerFactory: TrustManagerFactory): SniHandler {

    // Default value can be any in the map.
    val sslCtxBuilder = getServerSslContextBuilder(keyManagerFactoriesMap.values.first(), trustManagerFactory)
    val mapping = DomainWildcardMappingBuilder(sslCtxBuilder.build())
    keyManagerFactoriesMap.forEach {
        mapping.add(it.key, sslCtxBuilder.keyManager(it.value).build())
    }
    return SniHandler(mapping.build())
}

@Suppress("SpreadOperator")
private fun getServerSslContextBuilder(keyManagerFactory: KeyManagerFactory, trustManagerFactory: TrustManagerFactory): SslContextBuilder {
    return SslContextBuilder.forServer(keyManagerFactory)
            .sslProvider(SslProvider.OPENSSL)
            .trustManager(LoggingTrustManagerFactoryWrapper(trustManagerFactory))
            .clientAuth(ClientAuth.REQUIRE)
            .ciphers(ArtemisTcpTransport.CIPHER_SUITES)
            .protocols(*ArtemisTcpTransport.TLS_VERSIONS.toTypedArray())
}

internal fun splitKeystore(config: AMQPConfiguration): Map<String, CertHoldingKeyManagerFactoryWrapper> {
    val keyStore = config.keyStore.value.internal
    val password = config.keyStore.entryPassword.toCharArray()
    return keyStore.aliases().toList().map { alias ->
        val key = keyStore.getKey(alias, password)
        val certs = keyStore.getCertificateChain(alias)
        val x500Name = keyStore.getCertificate(alias).x509.subjectX500Principal
        val cordaX500Name = CordaX500Name.build(x500Name)
        val newKeyStore = KeyStore.getInstance("JKS")
        newKeyStore.load(null)
        newKeyStore.setKeyEntry(alias, key, password, certs)
        val newKeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        newKeyManagerFactory.init(newKeyStore, password)
        x500toHostName(cordaX500Name) to CertHoldingKeyManagerFactoryWrapper(newKeyManagerFactory, config)
    }.toMap()
}

// As per Javadoc in: https://docs.oracle.com/javase/8/docs/api/javax/net/ssl/KeyManagerFactory.html `init` method
// 2nd parameter `password` - the password for recovering keys in the KeyStore
fun KeyManagerFactory.init(keyStore: CertificateStore) = init(keyStore.value.internal, keyStore.entryPassword.toCharArray())

fun TrustManagerFactory.init(trustStore: CertificateStore) = init(trustStore.value.internal)

/**
 * Method that converts a [CordaX500Name] to a a valid hostname (RFC-1035). It's used for SNI to indicate the target
 * when trying to communicate with nodes that reside behind the same firewall. This is a solution to TLS's extension not
 * yet supporting x500 names as server names
 */
internal fun x500toHostName(x500Name: CordaX500Name): String {
    val secureHash = SecureHash.sha256(x500Name.toString())
    // RFC 1035 specifies a limit 255 bytes for hostnames with each label being 63 bytes or less. Due to this, the string
    // representation of the SHA256 hash is truncated to 32 characters.
    return String.format(HOSTNAME_FORMAT, secureHash.toString().take(32).toLowerCase())
}
