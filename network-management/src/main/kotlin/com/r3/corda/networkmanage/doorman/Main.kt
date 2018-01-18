package com.r3.corda.networkmanage.doorman

import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.r3.corda.networkmanage.common.persistence.*
import com.r3.corda.networkmanage.common.persistence.CertificationRequestStorage.Companion.DOORMAN_SIGNATURE
import com.r3.corda.networkmanage.common.signer.NetworkMapSigner
import com.r3.corda.networkmanage.common.utils.ShowHelpException
import com.r3.corda.networkmanage.doorman.signer.DefaultCsrHandler
import com.r3.corda.networkmanage.doorman.signer.JiraCsrHandler
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import com.r3.corda.networkmanage.doorman.webservice.MonitoringWebService
import com.r3.corda.networkmanage.doorman.webservice.NodeInfoWebService
import com.r3.corda.networkmanage.doorman.webservice.RegistrationWebService
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
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.crypto.*
import net.corda.nodeapi.internal.network.NetworkParameters
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPClientSerializationScheme
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.Closeable
import java.net.URI
import java.nio.file.Path
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.security.auth.x500.X500Principal
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class NetworkManagementServer : Closeable {
    private val doOnClose = mutableListOf<() -> Unit>()
    lateinit var hostAndPort: NetworkHostAndPort

    override fun close() = doOnClose.forEach { it() }

    companion object {
        private val logger = loggerFor<NetworkManagementServer>()
    }

    private fun getNetworkMapService(config: NetworkMapConfig, database: CordaPersistence, signer: LocalSigner?, updateNetworkParameters: NetworkParameters?): NodeInfoWebService {
        val networkMapStorage = PersistentNetworkMapStorage(database)
        val nodeInfoStorage = PersistentNodeInfoStorage(database)
        val localNetworkMapSigner = if (signer != null) NetworkMapSigner(networkMapStorage, signer) else null

        updateNetworkParameters?.let {
            // Persisting new network parameters
            val currentNetworkParameters = networkMapStorage.getCurrentSignedNetworkParameters()
            if (currentNetworkParameters == null) {
                localNetworkMapSigner?.signNetworkParameters(it) ?: networkMapStorage.saveNetworkParameters(it, null)
            } else {
                throw UnsupportedOperationException("Network parameters already exist. Updating them via the file config is not supported yet.")
            }
        }

        // This call will fail if parameter is null in DB.
        try {
            val latestParameter = networkMapStorage.getLatestUnsignedNetworkParameters()
            logger.info("Starting network map service with network parameters : $latestParameter")
        } catch (e: NoSuchElementException) {
            logger.error("No network parameter found, please upload new network parameter before starting network map service. The server will now exit.")
            exitProcess(-1)
        }

        // Thread sign network map in case of change (i.e. a new node info has been added or a node info has been removed).
        if (localNetworkMapSigner != null) {
            val scheduledExecutor = Executors.newScheduledThreadPool(1)
            val signingThread = Runnable {
                try {
                    localNetworkMapSigner.signNetworkMap()
                } catch (e: Exception) {
                    // Log the error and carry on.
                    logger.error("Error encountered when processing node info changes.", e)
                }
            }
            scheduledExecutor.scheduleAtFixedRate(signingThread, config.signInterval, config.signInterval, TimeUnit.MILLISECONDS)
            doOnClose += { scheduledExecutor.shutdown() }
        }

        return NodeInfoWebService(nodeInfoStorage, networkMapStorage, config)
    }


    private fun getDoormanService(config: DoormanConfig, database: CordaPersistence, signer: LocalSigner?, serverStatus: NetworkManagementServerStatus): RegistrationWebService {
        logger.info("Starting Doorman server.")
        val requestService = if (config.approveAll) {
            logger.warn("Doorman server is in 'Approve All' mode, this will approve all incoming certificate signing requests.")
            ApproveAllCertificateRequestStorage(PersistentCertificateRequestStorage(database))
        } else {
            PersistentCertificateRequestStorage(database)
        }

        val jiraConfig = config.jiraConfig
        val requestProcessor = if (jiraConfig != null) {
            val jiraWebAPI = AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(URI(jiraConfig.address), jiraConfig.username, jiraConfig.password)
            val jiraClient = JiraClient(jiraWebAPI, jiraConfig.projectCode, jiraConfig.doneTransitionCode)
            JiraCsrHandler(jiraClient, requestService, DefaultCsrHandler(requestService, signer))
        } else {
            DefaultCsrHandler(requestService, signer)
        }

        val scheduledExecutor = Executors.newScheduledThreadPool(1)
        val approvalThread = Runnable {
            try {
                serverStatus.lastRequestCheckTime = Instant.now()
                // Create tickets for requests which don't have one yet.
                requestProcessor.createTickets()
                // Process Jira approved tickets.
                requestProcessor.processApprovedRequests()
            } catch (e: Exception) {
                // Log the error and carry on.
                logger.error("Error encountered when approving request.", e)
            }
        }
        scheduledExecutor.scheduleAtFixedRate(approvalThread, config.approveInterval, config.approveInterval, TimeUnit.MILLISECONDS)
        doOnClose += { scheduledExecutor.shutdown() }

        return RegistrationWebService(requestProcessor)
    }

    fun start(hostAndPort: NetworkHostAndPort,
              database: CordaPersistence,
              doormanSigner: LocalSigner? = null,
              doormanServiceParameter: DoormanConfig?,  // TODO Doorman config shouldn't be optional as the doorman is always required to run
              startNetworkMap: NetworkMapStartParams?
    ) {
        val services = mutableListOf<Any>()
        val serverStatus = NetworkManagementServerStatus()

        startNetworkMap?.let { services += getNetworkMapService(it.config, database, it.signer, it.updateNetworkParameters) }
        doormanServiceParameter?.let { services += getDoormanService(it, database, doormanSigner, serverStatus) }

        require(services.isNotEmpty()) { "No service created, please provide at least one service config." }

        // TODO: use mbean to expose audit data?
        services += MonitoringWebService(serverStatus)

        val webServer = NetworkManagementWebServer(hostAndPort, *services.toTypedArray())
        webServer.start()

        doOnClose += webServer::close
        this.hostAndPort = webServer.hostAndPort
    }
}

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
        val oldKey = loadOrCreateKeyStore(rootStoreFile, rootKeystorePassword).getCertificate(X509Utilities.CORDA_ROOT_CA).publicKey
        println("Key ${X509Utilities.CORDA_ROOT_CA} already exists in keystore, process will now terminate.")
        println(oldKey)
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
    println(loadKeyStore(rootStoreFile, rootKeystorePassword).getCertificate(X509Utilities.CORDA_ROOT_CA).publicKey)
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


private fun buildLocalSigners(parameters: NetworkManagementServerParameters): Pair<LocalSigner, LocalSigner>? {
    if (parameters.keystorePath == null) return null

    // Get password from console if not in config.
    val keyStorePassword = parameters.keystorePassword ?: readPassword("Key store password: ")
    val privateKeyPassword = parameters.caPrivateKeyPassword ?: readPassword("Private key password: ")
    val keyStore = loadOrCreateKeyStore(parameters.keystorePath, keyStorePassword)

    val (doormanSigner, networkMapSigner) = listOf(DEFAULT_CSR_CERTIFICATE_NAME, DEFAULT_NETWORK_MAP_CERTIFICATE_NAME).map {
        val keyPair = keyStore.getKeyPair(it, privateKeyPassword)
        val certPath = keyStore.getCertificateChain(it).map { it as X509Certificate }
        LocalSigner(keyPair, certPath.toTypedArray())
    }

    return Pair(doormanSigner, networkMapSigner)
}

/**
 * This storage automatically approves all created requests.
 */
private class ApproveAllCertificateRequestStorage(private val delegate: CertificationRequestStorage) : CertificationRequestStorage by delegate {
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
                    val localSigners = buildLocalSigners(this)

                    if (localSigners != null) {
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
                        NetworkMapStartParams(localSigners?.second, networkParameters, it)
                    }

                    networkManagementServer.start(NetworkHostAndPort(host, port), database, localSigners?.first, doormanConfig, networkMapStartParams)

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
