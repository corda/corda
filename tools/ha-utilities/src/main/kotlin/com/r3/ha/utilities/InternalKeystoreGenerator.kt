package com.r3.ha.utilities

import net.corda.cliutils.CliWrapperBase
import net.corda.cliutils.CommonCliConstants.BASE_DIR
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

private const val DEFAULT_PASSWORD = "changeit"

class InternalArtemisKeystoreGenerator : AbstractInternalKeystoreGenerator("generate-internal-artemis-ssl-keystores", "Generate self-signed root and SSL certificates for internal communication between the services and external Artemis broker.") {

    override fun createKeyStores() {
        println("Generating Artemis keystores")
        val artemisCertDir = baseDirectory / "artemis"
        val root = createRootKeystore("Internal Artemis Root", artemisCertDir / "artemis-root.jks", artemisCertDir / "artemis-truststore.jks",
                keyStorePassword, keyStorePassword, trustStorePassword).getCertificateAndKeyPair(X509Utilities.CORDA_ROOT_CA, keyStorePassword)
        createTLSKeystore("artemis", root, artemisCertDir / "artemis.jks", keyStorePassword, keyStorePassword)
    }
}

class InternalTunnelKeystoreGenerator : AbstractInternalKeystoreGenerator("generate-internal-tunnel-ssl-keystores", "Generate self-signed root and SSL certificates for internal communication between Bridge and Float.") {

    @Option(names = ["-e", "--entryPassword"], description = ["Password for all the keystores private keys."], defaultValue = DEFAULT_PASSWORD)
    lateinit var entryPassword: String

    override fun createKeyStores() {
        println("Generating Tunnel keystores")
        val tunnelCertDir = baseDirectory / "tunnel"
        val tunnelRoot = createRootKeystore("Internal Tunnel Root", tunnelCertDir / "tunnel-root.jks", tunnelCertDir / "tunnel-truststore.jks",
                keyStorePassword, entryPassword, trustStorePassword).getCertificateAndKeyPair(X509Utilities.CORDA_ROOT_CA, entryPassword)
        createTLSKeystore("float", tunnelRoot, tunnelCertDir / "float.jks", keyStorePassword, entryPassword)
        createTLSKeystore("bridge", tunnelRoot, tunnelCertDir / "bridge.jks", keyStorePassword, entryPassword)

        warnOnDefaultPassword(entryPassword, "entryPassword")
    }
}

abstract class AbstractInternalKeystoreGenerator(alias: String, description: String) : CliWrapperBase(alias, description) {

    @Option(names = ["-b", BASE_DIR], description = ["The node working directory where all the files are kept."])
    var baseDirectory: Path = Paths.get(".").toAbsolutePath().normalize()
    @Option(names = ["-p", "--keyStorePassword"], description = ["Password for all generated keystores."], defaultValue = DEFAULT_PASSWORD)
    lateinit var keyStorePassword: String
    @Option(names = ["-t", "--trustStorePassword"], description = ["Password for the trust store."], defaultValue = DEFAULT_PASSWORD)
    lateinit var trustStorePassword: String
    @Option(names = ["-o", "--organization"], description = ["X500Name's organization attribute."], defaultValue = "Corda")
    lateinit var organization: String
    @Option(names = ["-u", "--organization-unit"], description = ["X500Name's organization unit attribute."], required = false)
    var organizationUnit: String? = null
    @Option(names = ["-l", "--locality"], description = ["X500Name's locality attribute."], defaultValue = "London")
    lateinit var locality: String
    @Option(names = ["-c", "--country"], description = ["X500Name's country attribute."], defaultValue = "GB")
    lateinit var country: String

    protected abstract fun createKeyStores()

    override fun runProgram(): Int {

        createKeyStores()

        listOf(keyStorePassword to "keyStorePassword", trustStorePassword to "trustStorePassword").forEach {
            warnOnDefaultPassword(it.first, it.second)
        }

        return ExitCodes.SUCCESS
    }

    protected fun warnOnDefaultPassword(password: String, paramName: String) {
        if (password == DEFAULT_PASSWORD) {
            println("WARN: Password for '$paramName' is defaulted to '$DEFAULT_PASSWORD'. Please consider changing the password using java keytool.")
        }
    }

    protected fun createRootKeystore(commonName: String, keystorePath: Path, trustStorePath: Path, storePassword: String, entryPassword: String, trustStorePassword: String): X509KeyStore {
        val key = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val rootCert = X509Utilities.createSelfSignedCACertificate(getX500Principal(commonName), key)
        val keystore = X509KeyStore.fromFile(keystorePath, storePassword, createNew = true)
        keystore.update {
            setPrivateKey(X509Utilities.CORDA_ROOT_CA, key.private, listOf(rootCert), entryPassword)
        }
        println("$commonName keystore created in $keystorePath.")

        X509KeyStore.fromFile(trustStorePath, trustStorePassword, createNew = true).setCertificate(X509Utilities.CORDA_ROOT_CA, rootCert)
        println("$commonName truststore created in $trustStorePath.")

        return keystore
    }

    protected fun createTLSKeystore(serviceName: String, root: CertificateAndKeyPair, keystorePath: Path, storePassword: String, entryPassword: String) {
        val key = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val cert = X509Utilities.createCertificate(CertificateType.TLS, root.certificate, root.keyPair, getX500Principal(serviceName), key.public)
        X509KeyStore.fromFile(keystorePath, storePassword, createNew = true).update {
            setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, key.private, listOf(cert, root.certificate), entryPassword)
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