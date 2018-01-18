package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.persistence.CertificationRequestStorage
import com.r3.corda.networkmanage.common.persistence.CertificationRequestStorage.Companion.DOORMAN_SIGNATURE
import com.r3.corda.networkmanage.common.persistence.configureDatabase
import com.r3.corda.networkmanage.common.utils.CertPathAndKey
import com.r3.corda.networkmanage.common.utils.ShowHelpException
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import com.r3.corda.networkmanage.hsm.configuration.Parameters.Companion.DEFAULT_CSR_CERTIFICATE_NAME
import com.r3.corda.networkmanage.hsm.configuration.Parameters.Companion.DEFAULT_NETWORK_MAP_CERTIFICATE_NAME
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.crypto.*
import net.corda.nodeapi.internal.network.NetworkParameters
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPClientSerializationScheme
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.nio.file.Path
import java.security.cert.X509Certificate
import java.time.Instant
import javax.security.auth.x500.X500Principal
import kotlin.concurrent.thread
import kotlin.system.exitProcess

data class NetworkMapStartParams(val signer: LocalSigner?, val updateNetworkParameters: NetworkParameters?, val config: NetworkMapConfig)

data class NetworkManagementServerStatus(var serverStartTime: Instant = Instant.now(), var lastRequestCheckTime: Instant? = null)

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
fun generateRootKeyPair(rootStoreFile: Path, rootKeystorePass: String?, rootPrivateKeyPass: String?) {
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
            CordaX500Name(commonName = "Corda Root CA", organisation = "R3 Ltd", locality = "London", country = "GB", organisationUnit = "Corda", state = null).x500Principal,
            selfSignKey)
    rootStore.addOrReplaceKey(X509Utilities.CORDA_ROOT_CA, selfSignKey.private, rootPrivateKeyPassword.toCharArray(), arrayOf(selfSignCert))
    rootStore.save(rootStoreFile, rootKeystorePassword)

    val nodeTrustStoreFile = (rootStoreFile.parent / "distribute-nodes").createDirectories() / "truststore.jks"
    // TODO The password for trust store must be a config option
    val nodeTrustStore = loadOrCreateKeyStore(nodeTrustStoreFile, "trustpass")
    nodeTrustStore.addOrReplaceCertificate(X509Utilities.CORDA_ROOT_CA, selfSignCert)
    nodeTrustStore.save(nodeTrustStoreFile, "trustpass")
    println("Trust store for distribution to nodes created in $nodeTrustStore")

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
            X500Principal("CN=Corda Intermediate CA,OU=Corda,O=R3 Ltd,L=London,C=GB"),
            X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)

    storeCertIfAbsent(
            DEFAULT_NETWORK_MAP_CERTIFICATE_NAME,
            CertificateType.NETWORK_MAP,
            X500Principal("CN=Corda Network Map,OU=Corda,O=R3 Ltd,L=London,C=GB"),
            Crypto.EDDSA_ED25519_SHA512)
}


private fun processKeyStore(parameters: NetworkManagementServerParameters): Pair<CertPathAndKey, LocalSigner>? {
    if (parameters.keystorePath == null) return null

    // Get password from console if not in config.
    val keyStorePassword = parameters.keystorePassword ?: readPassword("Key store password: ")
    val privateKeyPassword = parameters.caPrivateKeyPassword ?: readPassword("Private key password: ")
    val keyStore = loadOrCreateKeyStore(parameters.keystorePath, keyStorePassword)

    val csrCertPathAndKey = keyStore.run {
        CertPathAndKey(
                keyStore.getCertificateChain(DEFAULT_CSR_CERTIFICATE_NAME).map { it as X509Certificate },
                keyStore.getSupportedKey(DEFAULT_CSR_CERTIFICATE_NAME, privateKeyPassword)
        )
    }

    val networkMapSigner = LocalSigner(keyStore.getCertificateAndKeyPair(DEFAULT_NETWORK_MAP_CERTIFICATE_NAME, privateKeyPassword))

    return Pair(csrCertPathAndKey, networkMapSigner)
}

/**
 * This storage automatically approves all created requests.
 */
class ApproveAllCertificateRequestStorage(private val delegate: CertificationRequestStorage) : CertificationRequestStorage by delegate {
    override fun saveRequest(request: PKCS10CertificationRequest): String {
        val requestId = delegate.saveRequest(request)
        delegate.markRequestTicketCreated(requestId)
        approveRequest(requestId, DOORMAN_SIGNATURE)
        return requestId
    }
}

fun main(args: Array<String>) {
    try {
        parseParameters(*args).run {
            println("Starting in $mode mode")
            when (mode) {
                Mode.ROOT_KEYGEN -> generateRootKeyPair(
                        rootStorePath ?: throw IllegalArgumentException("The 'rootStorePath' parameter must be specified when generating keys!"),
                        rootKeystorePassword,
                        rootPrivateKeyPassword)
                Mode.CA_KEYGEN -> generateSigningKeyPairs(
                        keystorePath ?: throw IllegalArgumentException("The 'keystorePath' parameter must be specified when generating keys!"),
                        rootStorePath ?: throw IllegalArgumentException("The 'rootStorePath' parameter must be specified when generating keys!"),
                        rootKeystorePassword,
                        rootPrivateKeyPassword,
                        keystorePassword,
                        caPrivateKeyPassword)
                Mode.DOORMAN -> {
                    initialiseSerialization()
                    val database = configureDatabase(dataSourceProperties)
                    // TODO: move signing to signing server.
                    val csrAndNetworkMap = processKeyStore(this)

                    if (csrAndNetworkMap != null) {
                        println("Starting network management services with local signing")
                    }

                    val networkManagementServer = NetworkManagementServer()
                    val networkParameters = updateNetworkParameters?.let {
                        // TODO This check shouldn't be needed. Fix up the config design.
                        requireNotNull(networkMapConfig) { "'networkMapConfig' config is required for applying network parameters" }
                        println("Parsing network parameters from '${it.toAbsolutePath()}'...")
                        parseNetworkParametersFrom(it)
                    }
                    val networkMapStartParams = networkMapConfig?.let {
                        NetworkMapStartParams(csrAndNetworkMap?.second, networkParameters, it)
                    }

                    networkManagementServer.start(NetworkHostAndPort(host, port), database, csrAndNetworkMap?.first, doormanConfig, networkMapStartParams)

                    Runtime.getRuntime().addShutdownHook(thread(start = false) {
                        networkManagementServer.close()
                    })
                }
            }
        }
    } catch (e: ShowHelpException) {
        e.errorMessage?.let(::println)
        e.parser.printHelpOn(System.out)
    }
}

private fun initialiseSerialization() {
    val context = AMQP_P2P_CONTEXT
    nodeSerializationEnv = SerializationEnvironmentImpl(
            SerializationFactoryImpl().apply {
                registerScheme(AMQPClientSerializationScheme())
            },
            context)
}
