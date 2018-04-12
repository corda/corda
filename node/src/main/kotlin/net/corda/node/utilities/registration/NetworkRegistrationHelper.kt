package net.corda.node.utilities.registration

import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.*
import net.corda.node.NodeRegistrationOption
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_TLS
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.util.io.pem.PemObject
import java.io.StringWriter
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyStore
import java.security.PublicKey
import java.security.cert.X509Certificate

/**
 * Helper for managing the node registration process, which checks for any existing certificates and requests them if
 * needed.
 */
// TODO: Use content signer instead of keypairs.
open class NetworkRegistrationHelper(private val config: SSLConfiguration,
                                     private val myLegalName: CordaX500Name,
                                     private val emailAddress: String,
                                     private val certService: NetworkRegistrationService,
                                     private val networkRootTrustStorePath: Path,
                                     networkRootTrustStorePassword: String,
                                     private val keyAlias: String,
                                     private val certRole: CertRole) {

    companion object {
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
        if (keyAlias in nodeKeyStore) {
            println("Certificate already exists, Corda node will now terminate...")
            return
        }

        val keyPair = nodeKeyStore.loadOrCreateKeyPair(SELF_SIGNED_PRIVATE_KEY)

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
        validateCertificates(keyPair.public, certificates)
        storePrivateKeyWithCertificates(nodeKeyStore, keyPair, certificates, keyAlias)
        onSuccess(keyPair, certificates)
        // All done, clean up temp files.
        requestIdStore.deleteIfExists()

        println("Successfully registered Corda node with compatibility zone, node identity certificates are stored in '${config.certificatesDirectory}', it is advised to backup the certificates folder.")
        println("Corda node will now terminate.")
    }

    private fun validateCertificates(registeringPublicKey: PublicKey, certificates: List<X509Certificate>) {
        val nodeCACertificate = certificates.first()

        val nodeCaSubject = try {
            CordaX500Name.build(nodeCACertificate.subjectX500Principal)
        } catch (e: IllegalArgumentException) {
            throw CertificateRequestException("Received node CA cert has invalid subject name: ${e.message}")
        }
        if (nodeCaSubject != myLegalName) {
            throw CertificateRequestException("Subject of received node CA cert doesn't match with node legal name: $nodeCaSubject")
        }

        val nodeCaCertRole = try {
            CertRole.extract(nodeCACertificate)
        } catch (e: IllegalArgumentException) {
            throw CertificateRequestException("Unable to extract cert role from received node CA cert: ${e.message}")
        }

        if (certRole != nodeCaCertRole) {
            throw CertificateRequestException("Received certificate contains invalid cert role, expected '$certRole', got '$nodeCaCertRole'.")
        }

        // Validate returned certificate is for the correct public key.
        if (Crypto.toSupportedPublicKey(certificates.first().publicKey) != Crypto.toSupportedPublicKey(registeringPublicKey)) {
            throw CertificateRequestException("Received certificate contains incorrect public key, expected '$registeringPublicKey', got '${certificates.first().publicKey}'.")
        }

        // Validate certificate chain returned from the doorman with the root cert obtained via out-of-band process, to prevent MITM attack on doorman server.
        X509Utilities.validateCertificateChain(rootCert, certificates)
        println("Certificate signing request approved, storing private key with the certificate chain.")
    }

    private fun storePrivateKeyWithCertificates(nodeKeystore: X509KeyStore, keyPair: KeyPair, certificates: List<X509Certificate>, keyAlias: String) {
        // Save private key and certificate chain to the key store.
        nodeKeystore.setPrivateKey(keyAlias, keyPair.private, certificates, keyPassword = config.keyStorePassword)
        nodeKeystore.internal.deleteEntry(SELF_SIGNED_PRIVATE_KEY)
        nodeKeystore.save()
        println("Private key '$keyAlias' and certificate stored in ${config.nodeKeystore}.")
    }

    private fun X509KeyStore.loadOrCreateKeyPair(alias: String): KeyPair {
        // Create or load self signed keypair from the key store.
        // We use the self sign certificate to store the key temporarily in the keystore while waiting for the request approval.
        if (alias !in this) {
            val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
            val selfSignCert = X509Utilities.createSelfSignedCACertificate(myLegalName.x500Principal, keyPair)
            // Save to the key store.
            setPrivateKey(alias, keyPair.private, listOf(selfSignCert), keyPassword = privateKeyPassword)
            save()
        }
        return getCertificateAndKeyPair(alias, privateKeyPassword).keyPair
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
        while (true) {
            val (pollInterval, certificates) = certService.retrieveCertificates(requestId)
            if (certificates != null) {
                return certificates
            }
            Thread.sleep(pollInterval.toMillis())
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

    protected open fun onSuccess(nodeCAKeyPair: KeyPair, certificates: List<X509Certificate>) {}
}

class NodeRegistrationHelper(private val config: NodeConfiguration, certService: NetworkRegistrationService, regConfig: NodeRegistrationOption) :
        NetworkRegistrationHelper(config,
                config.myLegalName,
                config.emailAddress,
                certService,
                regConfig.networkRootTrustStorePath,
                regConfig.networkRootTrustStorePassword,
                CORDA_CLIENT_CA,
                CertRole.NODE_CA) {

    override fun onSuccess(nodeCAKeyPair: KeyPair, certificates: List<X509Certificate>) {
        createSSLKeystore(nodeCAKeyPair, certificates)
        createTruststore(certificates.last())
    }

    private fun createSSLKeystore(nodeCAKeyPair: KeyPair, certificates: List<X509Certificate>) {
        config.loadSslKeyStore(createNew = true).update {
            println("Generating SSL certificate for node messaging service.")
            val sslKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
            val sslCert = X509Utilities.createCertificate(
                    CertificateType.TLS,
                    certificates.first(),
                    nodeCAKeyPair,
                    config.myLegalName.x500Principal,
                    sslKeyPair.public)
            setPrivateKey(CORDA_CLIENT_TLS, sslKeyPair.private, listOf(sslCert) + certificates)
        }
        println("SSL private key and certificate stored in ${config.sslKeystore}.")
    }

    private fun createTruststore(rootCertificate: X509Certificate) {
        // Save root certificates to trust store.
        config.loadTrustStore(createNew = true).update {
            println("Generating trust store for corda node.")
            // Assumes certificate chain always starts with client certificate and end with root certificate.
            setCertificate(CORDA_ROOT_CA, rootCertificate)
        }
        println("Node trust store stored in ${config.trustStoreFile}.")
    }
}
