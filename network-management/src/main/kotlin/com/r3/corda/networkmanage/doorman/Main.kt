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
import net.corda.core.crypto.Crypto
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
        val networkMapStorage = PersistentNetworkMapStorage(database, signer)
        val nodeInfoStorage = PersistentNodeInfoStorage(database)

        updateNetworkParameters?.let {
            // Persisting new network parameters
            val currentNetworkParameters = networkMapStorage.getCurrentNetworkParameters()
            if (currentNetworkParameters == null) {
                networkMapStorage.saveNetworkParameters(it)
            } else {
                throw UnsupportedOperationException("Network parameters already exist. Updating them via the file config is not supported yet.")
            }
        }

        // This call will fail if parameter is null in DB.
        try {
            val latestParameter = networkMapStorage.getLatestNetworkParameters()
            logger.info("Starting network map service with network parameters : $latestParameter")
        } catch (e: NoSuchElementException) {
            logger.error("No network parameter found, please upload new network parameter before starting network map service. The server will now exit.")
            exitProcess(-1)
        }

        val networkMapSigner = if (signer != null) NetworkMapSigner(networkMapStorage, signer) else null

        // Thread sign network map in case of change (i.e. a new node info has been added or a node info has been removed).
        if (networkMapSigner != null) {
            val scheduledExecutor = Executors.newScheduledThreadPool(1)
            val signingThread = Runnable {
                try {
                    networkMapSigner.signNetworkMap()
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
              signer: LocalSigner? = null,
              updateNetworkParameters: NetworkParameters?,
              networkMapServiceParameter: NetworkMapConfig?,
              doormanServiceParameter: DoormanConfig?) {

        val services = mutableListOf<Any>()
        val serverStatus = NetworkManagementServerStatus()

        // TODO: move signing to signing server.
        networkMapServiceParameter?.let { services += getNetworkMapService(it, database, signer, updateNetworkParameters) }
        doormanServiceParameter?.let { services += getDoormanService(it, database, signer, serverStatus) }

        require(services.isNotEmpty()) { "No service created, please provide at least one service config." }

        // TODO: use mbean to expose audit data?
        services += MonitoringWebService(serverStatus)

        val webServer = NetworkManagementWebServer(hostAndPort, *services.toTypedArray())
        webServer.start()

        doOnClose += { webServer.close() }
        this.hostAndPort = webServer.hostAndPort
    }
}

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

fun generateCAKeyPair(keystoreFile: Path, rootStoreFile: Path, rootKeystorePass: String?, rootPrivateKeyPass: String?, keystorePass: String?, caPrivateKeyPass: String?) {
    println("Generating Intermediate CA keypair and certificate using root keystore $rootStoreFile.")
    // Get password from console if not in config.
    val rootKeystorePassword = rootKeystorePass ?: readPassword("Root Keystore Password: ")
    val rootPrivateKeyPassword = rootPrivateKeyPass ?: readPassword("Root Private Key Password: ")
    val rootKeyStore = loadKeyStore(rootStoreFile, rootKeystorePassword)

    val rootKeyAndCert = rootKeyStore.getCertificateAndKeyPair(X509Utilities.CORDA_ROOT_CA, rootPrivateKeyPassword)

    val keystorePassword = keystorePass ?: readPassword("Keystore Password: ")
    val caPrivateKeyPassword = caPrivateKeyPass ?: readPassword("CA Private Key Password: ")
    // Ensure folder exists.
    keystoreFile.parent.createDirectories()
    val keyStore = loadOrCreateKeyStore(keystoreFile, keystorePassword)

    if (keyStore.containsAlias(X509Utilities.CORDA_INTERMEDIATE_CA)) {
        val oldKey = loadOrCreateKeyStore(keystoreFile, rootKeystorePassword).getCertificate(X509Utilities.CORDA_INTERMEDIATE_CA).publicKey
        println("Key ${X509Utilities.CORDA_INTERMEDIATE_CA} already exists in keystore, process will now terminate.")
        println(oldKey)
        exitProcess(1)
    }

    val intermediateKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val intermediateCert = X509Utilities.createCertificate(
            CertificateType.INTERMEDIATE_CA,
            rootKeyAndCert.certificate,
            rootKeyAndCert.keyPair,
            CordaX500Name(commonName = "Corda Intermediate CA", organisation = "R3 Ltd", organisationUnit = "Corda", locality = "London", country = "GB", state = null).x500Principal,
            intermediateKeyPair.public
    )
    keyStore.addOrReplaceKey(
            X509Utilities.CORDA_INTERMEDIATE_CA,
            intermediateKeyPair.private,
            caPrivateKeyPassword.toCharArray(),
            arrayOf(intermediateCert, rootKeyAndCert.certificate)
    )
    keyStore.save(keystoreFile, keystorePassword)
    println("Intermediate CA keypair and certificate stored in $keystoreFile.")
    println(loadKeyStore(keystoreFile, keystorePassword).getCertificate(X509Utilities.CORDA_INTERMEDIATE_CA).publicKey)
}


private fun buildLocalSigner(parameters: NetworkManagementServerParameters): LocalSigner? {
    return parameters.keystorePath?.let {
        // Get password from console if not in config.
        val keystorePassword = parameters.keystorePassword ?: readPassword("Keystore Password: ")
        val caPrivateKeyPassword = parameters.caPrivateKeyPassword ?: readPassword("CA Private Key Password: ")
        val keystore = loadOrCreateKeyStore(parameters.keystorePath, keystorePassword)
        val caKeyPair = keystore.getKeyPair(X509Utilities.CORDA_INTERMEDIATE_CA, caPrivateKeyPassword)
        val caCertPath = keystore.getCertificateChain(X509Utilities.CORDA_INTERMEDIATE_CA).map { it as X509Certificate }
        LocalSigner(caKeyPair, caCertPath.toTypedArray())
    }
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
                Mode.CA_KEYGEN -> generateCAKeyPair(
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
                    val signer = buildLocalSigner(this)

                    if (signer != null) {
                        println("Starting network management services with local signer.")
                    }

                    val networkManagementServer = NetworkManagementServer()
                    val networkParameter = updateNetworkParameters?.let {
                        println("Parsing network parameter from '${it.fileName}'...")
                        parseNetworkParametersFrom(it)
                    }
                    networkManagementServer.start(NetworkHostAndPort(host, port), database, signer, networkParameter, networkMapConfig, doormanConfig)

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
