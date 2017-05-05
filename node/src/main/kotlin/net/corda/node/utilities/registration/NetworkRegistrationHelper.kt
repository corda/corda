package net.corda.node.utilities.registration

import net.corda.core.*
import net.corda.core.crypto.X509Utilities
import net.corda.core.crypto.X509Utilities.CORDA_CLIENT_CA
import net.corda.core.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.core.crypto.X509Utilities.addOrReplaceCertificate
import net.corda.core.crypto.X509Utilities.addOrReplaceKey
import net.corda.node.services.config.NodeConfiguration
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.util.io.pem.PemObject
import java.io.StringWriter
import java.security.KeyPair
import java.security.cert.Certificate
import kotlin.system.exitProcess

/**
 * This checks the config.certificatesDirectory field for certificates required to connect to a Corda network.
 * If the certificates are not found, a [org.bouncycastle.pkcs.PKCS10CertificationRequest] will be submitted to
 * Corda network permissioning server using [NetworkRegistrationService]. This process will enter a polling loop until the request has been approved, and then
 * the certificate chain will be downloaded and stored in [Keystore] reside in the certificates directory.
 */
class NetworkRegistrationHelper(val config: NodeConfiguration, val certService: NetworkRegistrationService) {
    companion object {
        val pollInterval = 10.seconds
        val SELF_SIGNED_PRIVATE_KEY = "Self Signed Private Key"
    }

    private val requestIdStore = config.certificatesDirectory / "certificate-request-id.txt"
    private val keystorePassword = config.keyStorePassword
    // TODO: Use different password for private key.
    private val privateKeyPassword = config.keyStorePassword

    fun buildKeystore() {
        config.certificatesDirectory.createDirectories()
        val caKeyStore = X509Utilities.loadOrCreateKeyStore(config.keyStoreFile, keystorePassword)
        if (!caKeyStore.containsAlias(CORDA_CLIENT_CA)) {
            // Create or load self signed keypair from the key store.
            // We use the self sign certificate to store the key temporarily in the keystore while waiting for the request approval.
            if (!caKeyStore.containsAlias(SELF_SIGNED_PRIVATE_KEY)) {
                val selfSignCert = X509Utilities.createSelfSignedCACert(config.myLegalName)
                // Save to the key store.
                caKeyStore.addOrReplaceKey(SELF_SIGNED_PRIVATE_KEY, selfSignCert.keyPair.private, privateKeyPassword.toCharArray(), arrayOf(selfSignCert.certificate))
                X509Utilities.saveKeyStore(caKeyStore, config.keyStoreFile, keystorePassword)
            }
            val keyPair = X509Utilities.loadKeyPairFromKeyStore(config.keyStoreFile, keystorePassword, privateKeyPassword, SELF_SIGNED_PRIVATE_KEY)
            val requestId = submitOrResumeCertificateSigningRequest(keyPair)

            val certificates = try {
                pollServerForCertificates(requestId)
            } catch (e: CertificateRequestException) {
                System.err.println(e.message)
                println("Please make sure the details in configuration file are correct and try again.")
                println("Corda node will now terminate.")
                requestIdStore.deleteIfExists()
                exitProcess(1)
            }

            println("Certificate signing request approved, storing private key with the certificate chain.")
            // Save private key and certificate chain to the key store.
            caKeyStore.addOrReplaceKey(CORDA_CLIENT_CA, keyPair.private, privateKeyPassword.toCharArray(), certificates)
            caKeyStore.deleteEntry(SELF_SIGNED_PRIVATE_KEY)
            X509Utilities.saveKeyStore(caKeyStore, config.keyStoreFile, keystorePassword)
            // Save root certificates to trust store.
            val trustStore = X509Utilities.loadOrCreateKeyStore(config.trustStoreFile, config.trustStorePassword)
            // Assumes certificate chain always starts with client certificate and end with root certificate.
            trustStore.addOrReplaceCertificate(CORDA_ROOT_CA, certificates.last())
            X509Utilities.saveKeyStore(trustStore, config.trustStoreFile, config.trustStorePassword)
            println("Certificate and private key stored in ${config.keyStoreFile}.")
            // All done, clean up temp files.
            requestIdStore.deleteIfExists()
        } else {
            println("Certificate already exists, Corda node will now terminate...")
        }
    }

    /**
     * Poll Certificate Signing Server for approved certificate,
     * enter a slow polling loop if server return null.
     * @param requestId Certificate signing request ID.
     * @return Map of certificate chain.
     */
    private fun pollServerForCertificates(requestId: String): Array<Certificate> {
        println("Start polling server for certificate signing approval.")
        // Poll server to download the signed certificate once request has been approved.
        var certificates = certService.retrieveCertificates(requestId)
        while (certificates == null) {
            Thread.sleep(pollInterval.toMillis())
            certificates = certService.retrieveCertificates(requestId)
        }
        return certificates
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
            val request = X509Utilities.createCertificateSigningRequest(config.myLegalName, keyPair)
            val writer = StringWriter()
            JcaPEMWriter(writer).use {
                it.writeObject(PemObject("CERTIFICATE REQUEST", request.encoded))
            }
            println("Certificate signing request with the following information will be submitted to the Corda certificate signing server.")
            println()
            println("Legal Name: ${config.myLegalName}")
            println("Nearest City: ${config.nearestCity}")
            println("Email: ${config.emailAddress}")
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
