package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.utils.CORDA_NETWORK_MAP
import com.r3.corda.networkmanage.hsm.configuration.Parameters.Companion.DEFAULT_CSR_CERTIFICATE_NAME
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.nodeapi.internal.crypto.*
import java.nio.file.Path
import javax.security.auth.x500.X500Principal
import kotlin.system.exitProcess

private val cordaX500Name = "OU=Corda,O=R3 Ltd,L=London,C=GB"

const val NETWORK_ROOT_TRUSTSTORE_FILENAME = "network-root-truststore.jks"

/** Read password from console, do a readLine instead if console is null (e.g. when debugging in IDE). */
internal fun readPassword(fmt: String): String {
    return if (System.console() != null) {
        String(System.console().readPassword(fmt))
    } else {
        print(fmt)
        readLine() ?: ""
    }
}

// Keygen utilities.
fun generateRootKeyPair(rootStoreFile: Path, rootKeystorePass: String?, rootPrivateKeyPass: String?, networkRootTrustPass: String?) {
    println("Generating Root CA keypair and certificate.")
    // Get password from console if not in config.
    val rootKeystorePassword = rootKeystorePass ?: readPassword("Root Keystore Password: ")
    // Ensure folder exists.
    rootStoreFile.parent.createDirectories()
    val rootStore = loadOrCreateKeyStore(rootStoreFile, rootKeystorePassword)
    val rootPrivateKeyPassword = rootPrivateKeyPass ?: readPassword("Root Private Key Password: ")

    if (rootStore.containsAlias(X509Utilities.CORDA_ROOT_CA)) {
        println("${X509Utilities.CORDA_ROOT_CA} already exists in keystore, process will now terminate.")
        println(rootStore.getCertificate(X509Utilities.CORDA_ROOT_CA))
        exitProcess(1)
    }

    val selfSignKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    // TODO Make the cert subject configurable
    val selfSignCert = X509Utilities.createSelfSignedCACertificate(
            X500Principal("CN=Corda Root CA,$cordaX500Name"),
            selfSignKey)
    rootStore.addOrReplaceKey(X509Utilities.CORDA_ROOT_CA, selfSignKey.private, rootPrivateKeyPassword.toCharArray(), arrayOf(selfSignCert))
    rootStore.save(rootStoreFile, rootKeystorePassword)

    val trustStorePath = (rootStoreFile.parent / "distribute-nodes").createDirectories() / NETWORK_ROOT_TRUSTSTORE_FILENAME

    val networkRootTrustPassword = networkRootTrustPass ?: readPassword("Network Root Trust Store Password: ")
    val networkRootTrustStore = loadOrCreateKeyStore(trustStorePath, networkRootTrustPassword)
    networkRootTrustStore.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, selfSignCert)
    networkRootTrustStore.save(trustStorePath, networkRootTrustPassword)
    println("Trust store for distribution to nodes created in $networkRootTrustStore")

    println("Root CA keypair and certificate stored in ${rootStoreFile.toAbsolutePath()}.")
    println(selfSignCert)
}

fun generateSigningKeyPairs(keystoreFile: Path, rootStoreFile: Path, rootKeystorePass: String?, rootPrivateKeyPass: String?, keystorePass: String?, caPrivateKeyPass: String?) {
    println("Generating intermediate and network map key pairs and certificates using root key store $rootStoreFile.")
    // Get password from console if not in config.
    val rootKeystorePassword = rootKeystorePass ?: readPassword("Root key store password: ")
    val rootPrivateKeyPassword = rootPrivateKeyPass ?: readPassword("Root private key password: ")
    val rootKeyStore = loadKeyStore(rootStoreFile, rootKeystorePassword)

    val rootKeyPairAndCert = rootKeyStore.getCertificateAndKeyPair(X509Utilities.CORDA_ROOT_CA, rootPrivateKeyPassword)

    val keyStorePassword = keystorePass ?: readPassword("Key store Password: ")
    val privateKeyPassword = caPrivateKeyPass ?: readPassword("Private key Password: ")
    // Ensure folder exists.
    keystoreFile.parent.createDirectories()
    val keyStore = loadOrCreateKeyStore(keystoreFile, keyStorePassword)

    fun storeCertIfAbsent(alias: String, certificateType: CertificateType, subject: X500Principal, signatureScheme: SignatureScheme) {
        if (keyStore.containsAlias(alias)) {
            println("$alias already exists in keystore:")
            println(keyStore.getCertificate(alias))
            return
        }

        val keyPair = Crypto.generateKeyPair(signatureScheme)
        val cert = X509Utilities.createCertificate(
                certificateType,
                rootKeyPairAndCert.certificate,
                rootKeyPairAndCert.keyPair,
                subject,
                keyPair.public
        )
        keyStore.addOrReplaceKey(
                alias,
                keyPair.private,
                privateKeyPassword.toCharArray(),
                arrayOf(cert, rootKeyPairAndCert.certificate)
        )
        keyStore.save(keystoreFile, keyStorePassword)

        println("$certificateType key pair and certificate stored in $keystoreFile.")
        println(cert)
    }

    storeCertIfAbsent(
            DEFAULT_CSR_CERTIFICATE_NAME,
            CertificateType.INTERMEDIATE_CA,
            X500Principal("CN=Corda Intermediate CA,$cordaX500Name"),
            X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)

    storeCertIfAbsent(
            CORDA_NETWORK_MAP,
            CertificateType.NETWORK_MAP,
            X500Principal("CN=Corda Network Map,$cordaX500Name"),
            Crypto.EDDSA_ED25519_SHA512)
}
