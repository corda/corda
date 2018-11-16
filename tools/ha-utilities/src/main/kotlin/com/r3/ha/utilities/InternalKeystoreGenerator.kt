package com.r3.ha.utilities

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.ExitCodes
import net.corda.core.crypto.Crypto
import net.corda.core.internal.div
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import picocli.CommandLine.Option
import sun.security.x509.X500Name
import java.nio.file.Path
import java.nio.file.Paths
import javax.security.auth.x500.X500Principal

class InternalKeystoreGenerator : CliWrapperBase("generate-internal-ssl-keystores", "Generate self-signed root and SSL certificates for bridge, external artemis broker and float, for internal communication between the services.") {
    companion object {
        private const val DEFAULT_PASSWORD = "changeit"
    }

    @Option(names = ["-b", "--base-directory"], description = ["The node working directory where all the files are kept."])
    var baseDirectory: Path = Paths.get(".").toAbsolutePath().normalize()

    // TODO: options to generate keys for different HA deployment mode?
    @Option(names = ["-p", "--password"], description = ["Default password for all generated keystore and private keys."], defaultValue = DEFAULT_PASSWORD)
    lateinit var password: String
    @Option(names = ["-o", "--organization"], description = ["X500Name's organization attribute."], defaultValue = "Corda")
    lateinit var organization: String
    @Option(names = ["-u", "--organization-unit"], description = ["X500Name's organization unit attribute."], required = false)
    var organizationUnit: String? = null
    @Option(names = ["-l", "--locality"], description = ["X500Name's locality attribute."], defaultValue = "London")
    lateinit var locality: String
    @Option(names = ["-c", "--county"], description = ["X500Name's country attribute."], defaultValue = "GB")
    lateinit var country: String

    override fun runProgram(): Int {
        // Create tunnel certs
        val tunnelCertDir = baseDirectory / "tunnel"
        val tunnelRoot = createRootKeystore("Internal Tunnel Root", tunnelCertDir / "tunnel-root.jks", tunnelCertDir / "tunnel-truststore.jks").getCertificateAndKeyPair(X509Utilities.CORDA_ROOT_CA, password)
        createTLSKeystore("float", tunnelRoot, tunnelCertDir / "float.jks")

        // Create artemis certs
        val artemisCertDir = baseDirectory / "artemis"
        val root = createRootKeystore("Internal Artemis Root", artemisCertDir / "artemis-root.jks", artemisCertDir / "artemis-truststore.jks").getCertificateAndKeyPair(X509Utilities.CORDA_ROOT_CA, password)
        createTLSKeystore("bridge", root, artemisCertDir / "bridge.jks")
        createTLSKeystore("artemis", root, artemisCertDir / "artemis.jks")
        createTLSKeystore("artemis-client", root, artemisCertDir / "artemis-client.jks")

        if (password == DEFAULT_PASSWORD) {
            println("Password is defaulted to $DEFAULT_PASSWORD, please change the keystores password using java keytool.")
        }
        return ExitCodes.SUCCESS
    }

    private fun createRootKeystore(commonName: String, keystorePath: Path, trustStorePath: Path): X509KeyStore {
        val key = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val rootCert = X509Utilities.createSelfSignedCACertificate(getX500Principal(commonName), key)
        val keystore = X509KeyStore.fromFile(keystorePath, password, createNew = true)
        keystore.update {
            setPrivateKey(X509Utilities.CORDA_ROOT_CA, key.private, listOf(rootCert), password)
        }
        println("$commonName keystore created in $keystorePath.")

        X509KeyStore.fromFile(trustStorePath, password, createNew = true).setCertificate(X509Utilities.CORDA_ROOT_CA, rootCert)
        println("$commonName truststore created in $trustStorePath.")

        return keystore
    }

    private fun createTLSKeystore(serviceName: String, root: CertificateAndKeyPair, keystorePath: Path) {
        val key = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val cert = X509Utilities.createCertificate(CertificateType.TLS, root.certificate, root.keyPair, getX500Principal(serviceName), key.public)
        X509KeyStore.fromFile(keystorePath, password, createNew = true).update {
            setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, key.private, listOf(cert, root.certificate), password)
        }
        println("Internal TLS keystore for '$serviceName' created in $keystorePath.")
    }

    private fun getX500Principal(commonName: String): X500Principal {
        return if (organizationUnit == null) {
            "CN=$commonName, O=$organization, L=$locality, C=$country"
        } else {
            "CN=$commonName, OU=$organizationUnit, O=$organization, L=$locality, C=$country"
        }.let { X500Name(it).asX500Principal() }
    }
}