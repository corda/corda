package net.corda.node.utilities.registration

import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.*
import net.corda.node.NodeRegistrationOption
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_TLS
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.util.io.pem.PemObject
import java.io.IOException
import java.io.StringWriter
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.time.Duration
import javax.naming.ServiceUnavailableException

/**
 * Helper for managing the node registration process, which checks for any existing certificates and requests them if
 * needed.
 */
class NetworkRegistrationHelper(private val config: SSLConfiguration,
                                private val myLegalName: CordaX500Name,
                                private val emailAddress: String,
                                private val certService: NetworkRegistrationService,
                                private val networkRootTrustStorePath: Path,
                                networkRootTrustStorePassword: String,
                                private val certRole: CertRole,
                                private val nextIdleDuration: (Duration?) -> Duration? = FixedPeriodLimitedRetrialStrategy(10, Duration.ofMinutes(1))) {

    // Constructor for corda node, cert role is restricted to [CertRole.NODE_CA].
    constructor(config: NodeConfiguration, certService: NetworkRegistrationService, regConfig: NodeRegistrationOption) :
            this(config, config.myLegalName, config.emailAddress, certService, regConfig.networkRootTrustStorePath, regConfig.networkRootTrustStorePassword, CertRole.NODE_CA)

    private companion object {
        const val SELF_SIGNED_PRIVATE_KEY = "Self Signed Private Key"
    }

    private val requestIdStore = config.certificatesDirectory / "certificate-request-id.txt"
    // TODO: Use different password for private key.
    private val privateKeyPassword = config.keyStorePassword
    private val rootTrustStore: X509KeyStore
    private val rootCert: X509Certificate

    init {
        require(networkRootTrustStorePath.exists()) {
            "$networkRootTrustStorePath does not exist. This file must contain the root CA cert of your compatibility zone. " +
                    "Please contact your CZ operator."
        }
        rootTrustStore = X509KeyStore.fromFile(networkRootTrustStorePath, networkRootTrustStorePassword)
        rootCert = rootTrustStore.getCertificate(CORDA_ROOT_CA)
    }

    /**
     * Ensure the initial keystore for a node is set up.
     *
     * This checks the "config.certificatesDirectory" field for certificates required to connect to a Corda network.
     * If the certificates are not found, a PKCS #10 certification request will be submitted to the
     * Corda network permissioning server via [NetworkRegistrationService]. This process will enter a polling loop until
     * the request has been approved, and then the certificate chain will be downloaded and stored in [KeyStore] reside in
     * the certificates directory.
     *
     * @throws CertificateRequestException if the certificate retrieved by doorman is invalid.
     */
    fun buildKeystore() {
        config.certificatesDirectory.createDirectories()
        val nodeKeyStore = config.loadNodeKeyStore(createNew = true)
        if (CORDA_CLIENT_CA in nodeKeyStore) {
            println("Certificate already exists, Corda node will now terminate...")
            return
        }

        // Create or load self signed keypair from the key store.
        // We use the self sign certificate to store the key temporarily in the keystore while waiting for the request approval.
        if (SELF_SIGNED_PRIVATE_KEY !in nodeKeyStore) {
            val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
            val selfSignCert = X509Utilities.createSelfSignedCACertificate(myLegalName.x500Principal, keyPair)
            // Save to the key store.
            nodeKeyStore.setPrivateKey(SELF_SIGNED_PRIVATE_KEY, keyPair.private, listOf(selfSignCert), keyPassword = privateKeyPassword)
            nodeKeyStore.save()
        }

        val keyPair = nodeKeyStore.getCertificateAndKeyPair(SELF_SIGNED_PRIVATE_KEY, privateKeyPassword).keyPair
        val requestId = submitOrResumeCertificateSigningRequest(keyPair)

        val certificates = try {
            pollServerForCertificates(requestId)
        } catch (certificateRequestException: CertificateRequestException) {
            System.err.println(certificateRequestException.message)
            System.err.println("Please make sure the details in configuration file are correct and try again.")
            System.err.println("Corda node will now terminate.")
            requestIdStore.deleteIfExists()
            throw certificateRequestException
        }

        val certificate = certificates.first()

        val nodeCaSubject = try {
            CordaX500Name.build(certificate.subjectX500Principal)
        } catch (e: IllegalArgumentException) {
            throw CertificateRequestException("Received node CA cert has invalid subject name: ${e.message}")
        }
        if (nodeCaSubject != myLegalName) {
            throw CertificateRequestException("Subject of received node CA cert doesn't match with node legal name: $nodeCaSubject")
        }

        val nodeCaCertRole = try {
            CertRole.extract(certificate)
        } catch (e: IllegalArgumentException) {
            throw CertificateRequestException("Unable to extract cert role from received node CA cert: ${e.message}")
        }

        // Validate certificate chain returned from the doorman with the root cert obtained via out-of-band process, to prevent MITM attack on doorman server.
        X509Utilities.validateCertificateChain(rootCert, certificates)

        println("Certificate signing request approved, storing private key with the certificate chain.")

        when (nodeCaCertRole) {
            CertRole.NODE_CA -> {
                // Save private key and certificate chain to the key store.
                nodeKeyStore.setPrivateKey(CORDA_CLIENT_CA, keyPair.private, certificates, keyPassword = privateKeyPassword)
                nodeKeyStore.internal.deleteEntry(SELF_SIGNED_PRIVATE_KEY)
                nodeKeyStore.save()
                println("Node private key and certificate stored in ${config.nodeKeystore}.")

                config.loadSslKeyStore(createNew = true).update {
                    println("Generating SSL certificate for node messaging service.")
                    val sslKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
                    val sslCert = X509Utilities.createCertificate(
                            CertificateType.TLS,
                            certificate,
                            keyPair,
                            myLegalName.x500Principal,
                            sslKeyPair.public)
                    setPrivateKey(CORDA_CLIENT_TLS, sslKeyPair.private, listOf(sslCert) + certificates)
                }
                println("SSL private key and certificate stored in ${config.sslKeystore}.")
            }
            // TODO: Fix this, this is not needed in corda node.
            CertRole.SERVICE_IDENTITY -> {
                // Only create keystore containing notary's key for service identity role.
                nodeKeyStore.setPrivateKey("${DevIdentityGenerator.DISTRIBUTED_NOTARY_ALIAS_PREFIX}-private-key", keyPair.private, certificates, keyPassword = privateKeyPassword)
                nodeKeyStore.internal.deleteEntry(SELF_SIGNED_PRIVATE_KEY)
                nodeKeyStore.save()
                println("Service identity private key and certificate stored in ${config.nodeKeystore}.")
            }
            else -> throw CertificateRequestException("Received node CA cert has invalid role: $nodeCaCertRole")
        }
        // Save root certificates to trust store.
        config.loadTrustStore(createNew = true).update {
            println("Generating trust store for corda node.")
            // Assumes certificate chain always starts with client certificate and end with root certificate.
            setCertificate(CORDA_ROOT_CA, certificates.last())
        }
        println("Node trust store stored in ${config.trustStoreFile}.")
        // All done, clean up temp files.
        requestIdStore.deleteIfExists()
    }

    /**
     * Poll Certificate Signing Server for approved certificate,
     * enter a slow polling loop if server return null.
     * @param requestId Certificate signing request ID.
     * @return List of certificate chain.
     */
    private fun pollServerForCertificates(requestId: String): List<X509Certificate> {
        println("Start polling server for certificate signing approval.")
        // Poll server to download the signed certificate once request has been approved.
        var idlePeriodDuration: Duration? = null
        while (true) {
            try {
                val (pollInterval, certificates) = certService.retrieveCertificates(requestId)
                if (certificates != null) {
                    return certificates
                }
                Thread.sleep(pollInterval.toMillis())
            } catch (e: ServiceUnavailableException) {
                idlePeriodDuration = nextIdleDuration(idlePeriodDuration)
                if (idlePeriodDuration != null) {
                    Thread.sleep(idlePeriodDuration.toMillis())
                } else {
                    throw UnableToRegisterNodeWithDoormanException()
                }
            }
        }
    }

    /**
     * Submit Certificate Signing Request to Certificate signing service if request ID not found in file system
     * New request ID will be stored in requestId.txt
     * @param keyPair Public Private key pair generated for SSL certification.
     * @return Request ID return from the server.
     */
    private fun submitOrResumeCertificateSigningRequest(keyPair: KeyPair): String {
        // Retrieve request id from file if exists, else post a request to server.
        return if (!requestIdStore.exists()) {
            val request = X509Utilities.createCertificateSigningRequest(myLegalName.x500Principal, emailAddress, keyPair, certRole)
            val writer = StringWriter()
            JcaPEMWriter(writer).use {
                it.writeObject(PemObject("CERTIFICATE REQUEST", request.encoded))
            }
            println("Certificate signing request with the following information will be submitted to the Corda certificate signing server.")
            println()
            println("Legal Name: $myLegalName")
            println("Email: $emailAddress")
            println()
            println("Public Key: ${keyPair.public}")
            println()
            println("$writer")
            // Post request to signing server via http.
            println("Submitting certificate signing request to Corda certificate signing server.")
            val requestId = certService.submitRequest(request)
            // Persists request ID to file in case of node shutdown.
            requestIdStore.writeLines(listOf(requestId))
            println("Successfully submitted request to Corda certificate signing server, request ID: $requestId.")
            requestId
        } else {
            val requestId = requestIdStore.readLines { it.findFirst().get() }
            println("Resuming from previous certificate signing request, request ID: $requestId.")
            requestId
        }
    }
}

class UnableToRegisterNodeWithDoormanException : IOException()

private class FixedPeriodLimitedRetrialStrategy(times: Int, private val period: Duration) : (Duration?) -> Duration? {
    init {
        require(times > 0)
    }
    private var counter = times
    override fun invoke(@Suppress("UNUSED_PARAMETER") previousPeriod: Duration?): Duration? {
        synchronized(this) {
            return if (counter-- > 0) period else null
        }
    }
}