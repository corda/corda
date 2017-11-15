package com.r3.corda.networkmanage.doorman

import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.r3.corda.networkmanage.common.persistence.*
import com.r3.corda.networkmanage.common.persistence.CertificationRequestStorage.Companion.DOORMAN_SIGNATURE
import com.r3.corda.networkmanage.common.signer.NetworkMapSigner
import com.r3.corda.networkmanage.common.utils.ShowHelpException
import com.r3.corda.networkmanage.doorman.DoormanServer.Companion.logger
import com.r3.corda.networkmanage.doorman.signer.DefaultCsrHandler
import com.r3.corda.networkmanage.doorman.signer.JiraCsrHandler
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import com.r3.corda.networkmanage.doorman.webservice.NodeInfoWebService
import com.r3.corda.networkmanage.doorman.webservice.RegistrationWebService
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.createDirectories
import net.corda.core.node.NetworkParameters
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.node.utilities.*
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 *  DoormanServer runs on Jetty server and provides certificate signing service via http.
 *  The server will require keystorePath, keystore password and key password via command line input.
 *  The Intermediate CA certificate,Intermediate CA private key and Root CA Certificate should use alias name specified in [X509Utilities]
 */
// TODO: Move this class to its own file.
class DoormanServer(hostAndPort: NetworkHostAndPort, private vararg val webServices: Any) : Closeable {
    companion object {
        val logger = loggerFor<DoormanServer>()
        val serverStatus = DoormanServerStatus()
    }

    private val server: Server = Server(InetSocketAddress(hostAndPort.host, hostAndPort.port)).apply {
        handler = HandlerCollection().apply {
            addHandler(buildServletContextHandler())
        }
    }

    val hostAndPort: NetworkHostAndPort
        get() = server.connectors.mapNotNull { it as? ServerConnector }
                .map { NetworkHostAndPort(it.host, it.localPort) }
                .first()

    override fun close() {
        logger.info("Shutting down Doorman Web Services...")
        server.stop()
        server.join()
    }

    fun start() {
        logger.info("Starting Doorman Web Services...")
        server.start()
        logger.info("Doorman Web Services started on $hostAndPort")
    }

    private fun buildServletContextHandler(): ServletContextHandler {
        return ServletContextHandler().apply {
            contextPath = "/"
            val resourceConfig = ResourceConfig().apply {
                // Add your API provider classes (annotated for JAX-RS) here
                webServices.forEach { register(it) }
            }
            val jerseyServlet = ServletHolder(ServletContainer(resourceConfig)).apply { initOrder = 0 }// Initialise at server start
            addServlet(jerseyServlet, "/api/*")
        }
    }
}

data class DoormanServerStatus(var serverStartTime: Instant = Instant.now(), var lastRequestCheckTime: Instant? = null)

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
// TODO: Move keygen methods to Utilities.kt
fun generateRootKeyPair(rootStorePath: Path, rootKeystorePass: String?, rootPrivateKeyPass: String?) {
    println("Generating Root CA keypair and certificate.")
    // Get password from console if not in config.
    val rootKeystorePassword = rootKeystorePass ?: readPassword("Root Keystore Password: ")
    // Ensure folder exists.
    rootStorePath.parent.createDirectories()
    val rootStore = loadOrCreateKeyStore(rootStorePath, rootKeystorePassword)
    val rootPrivateKeyPassword = rootPrivateKeyPass ?: readPassword("Root Private Key Password: ")

    if (rootStore.containsAlias(X509Utilities.CORDA_ROOT_CA)) {
        val oldKey = loadOrCreateKeyStore(rootStorePath, rootKeystorePassword).getCertificate(X509Utilities.CORDA_ROOT_CA).publicKey
        println("Key ${X509Utilities.CORDA_ROOT_CA} already exists in keystore, process will now terminate.")
        println(oldKey)
        exitProcess(1)
    }

    val selfSignKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val selfSignCert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Corda Root CA", organisation = "R3 Ltd", locality = "London", country = "GB", organisationUnit = "Corda", state = null), selfSignKey)
    rootStore.addOrReplaceKey(X509Utilities.CORDA_ROOT_CA, selfSignKey.private, rootPrivateKeyPassword.toCharArray(), arrayOf(selfSignCert))
    rootStore.save(rootStorePath, rootKeystorePassword)

    println("Root CA keypair and certificate stored in $rootStorePath.")
    println(loadKeyStore(rootStorePath, rootKeystorePassword).getCertificate(X509Utilities.CORDA_ROOT_CA).publicKey)
}

fun generateCAKeyPair(keystorePath: Path, rootStorePath: Path, rootKeystorePass: String?, rootPrivateKeyPass: String?, keystorePass: String?, caPrivateKeyPass: String?) {
    println("Generating Intermediate CA keypair and certificate using root keystore $rootStorePath.")
    // Get password from console if not in config.
    val rootKeystorePassword = rootKeystorePass ?: readPassword("Root Keystore Password: ")
    val rootPrivateKeyPassword = rootPrivateKeyPass ?: readPassword("Root Private Key Password: ")
    val rootKeyStore = loadKeyStore(rootStorePath, rootKeystorePassword)

    val rootKeyAndCert = rootKeyStore.getCertificateAndKeyPair(X509Utilities.CORDA_ROOT_CA, rootPrivateKeyPassword)

    val keystorePassword = keystorePass ?: readPassword("Keystore Password: ")
    val caPrivateKeyPassword = caPrivateKeyPass ?: readPassword("CA Private Key Password: ")
    // Ensure folder exists.
    keystorePath.parent.createDirectories()
    val keyStore = loadOrCreateKeyStore(keystorePath, keystorePassword)

    if (keyStore.containsAlias(X509Utilities.CORDA_INTERMEDIATE_CA)) {
        val oldKey = loadOrCreateKeyStore(keystorePath, rootKeystorePassword).getCertificate(X509Utilities.CORDA_INTERMEDIATE_CA).publicKey
        println("Key ${X509Utilities.CORDA_INTERMEDIATE_CA} already exists in keystore, process will now terminate.")
        println(oldKey)
        exitProcess(1)
    }

    val intermediateKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val intermediateCert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootKeyAndCert.certificate, rootKeyAndCert.keyPair,
            CordaX500Name(commonName = "Corda Intermediate CA", organisation = "R3 Ltd", organisationUnit = "Corda", locality = "London", country = "GB", state = null), intermediateKey.public)
    keyStore.addOrReplaceKey(X509Utilities.CORDA_INTERMEDIATE_CA, intermediateKey.private,
            caPrivateKeyPassword.toCharArray(), arrayOf(intermediateCert, rootKeyAndCert.certificate))
    keyStore.save(keystorePath, keystorePassword)
    println("Intermediate CA keypair and certificate stored in $keystorePath.")
    println(loadKeyStore(keystorePath, keystorePassword).getCertificate(X509Utilities.CORDA_INTERMEDIATE_CA).publicKey)
}

// TODO: Move this method to DoormanServer.
fun startDoorman(hostAndPort: NetworkHostAndPort,
                 database: CordaPersistence,
                 approveAll: Boolean,
                 initialNetworkMapParameters: NetworkParameters,
                 signer: LocalSigner? = null,
                 approveInterval: Long,
                 signInterval: Long,
                 jiraConfig: DoormanParameters.JiraConfig? = null): DoormanServer {

    logger.info("Starting Doorman server.")

    val requestService = if (approveAll) {
        logger.warn("Doorman server is in 'Approve All' mode, this will approve all incoming certificate signing requests.")
        ApproveAllCertificateRequestStorage(PersistentCertificateRequestStorage(database))
    } else {
        PersistentCertificateRequestStorage(database)
    }

    val requestProcessor = if (jiraConfig != null) {
        val jiraWebAPI = AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(URI(jiraConfig.address), jiraConfig.username, jiraConfig.password)
        val jiraClient = JiraClient(jiraWebAPI, jiraConfig.projectCode, jiraConfig.doneTransitionCode)
        JiraCsrHandler(jiraClient, requestService, DefaultCsrHandler(requestService, signer))
    } else {
        DefaultCsrHandler(requestService, signer)
    }
    val networkMapStorage = PersistentNetworkMapStorage(database)
    val nodeInfoStorage = PersistentNodeInfoStorage(database)

    val doorman = DoormanServer(hostAndPort, RegistrationWebService(requestProcessor, DoormanServer.serverStatus), NodeInfoWebService(nodeInfoStorage, networkMapStorage, signer))
    doorman.start()

    val networkMapSigner = if (signer != null) NetworkMapSigner(networkMapStorage, signer) else null

    // Thread process approved request periodically.
    val scheduledExecutor = Executors.newScheduledThreadPool(2)
    val approvalThread = Runnable {
        try {
            DoormanServer.serverStatus.lastRequestCheckTime = Instant.now()
            requestProcessor.processApprovedRequests()
        } catch (e: Exception) {
            // Log the error and carry on.
            DoormanServer.logger.error("Error encountered when approving request.", e)
        }
    }
    scheduledExecutor.scheduleAtFixedRate(approvalThread, approveInterval, approveInterval, TimeUnit.SECONDS)
    // Thread sign network map in case of change (i.e. a new node info has been added or a node info has been removed).
    if (networkMapSigner != null) {
        val signingThread = Runnable {
            try {
                networkMapSigner.signNetworkMap()
            } catch (e: Exception) {
                // Log the error and carry on.
                DoormanServer.logger.error("Error encountered when processing node info changes.", e)
            }
        }
        scheduledExecutor.scheduleAtFixedRate(signingThread, signInterval, signInterval, TimeUnit.SECONDS)
    }
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        scheduledExecutor.shutdown()
        doorman.close()
    })
    return doorman
}

private fun buildLocalSigner(parameters: DoormanParameters): LocalSigner? {
    return parameters.keystorePath?.let {
        // Get password from console if not in config.
        val keystorePassword = parameters.keystorePassword ?: readPassword("Keystore Password: ")
        val caPrivateKeyPassword = parameters.caPrivateKeyPassword ?: readPassword("CA Private Key Password: ")
        val keystore = loadOrCreateKeyStore(parameters.keystorePath, keystorePassword)
        val caKeyPair = keystore.getKeyPair(X509Utilities.CORDA_INTERMEDIATE_CA, caPrivateKeyPassword)
        val caCertPath = keystore.getCertificateChain(X509Utilities.CORDA_INTERMEDIATE_CA)
        LocalSigner(caKeyPair, caCertPath)
    }
}

/**
 * This storage automatically approves all created requests.
 */
private class ApproveAllCertificateRequestStorage(private val delegate: CertificationRequestStorage) : CertificationRequestStorage by delegate {
    override fun saveRequest(request: PKCS10CertificationRequest): String {
        val requestId = delegate.saveRequest(request)
        approveRequest(requestId, DOORMAN_SIGNATURE)
        return requestId
    }
}

fun main(args: Array<String>) {
    try {
        val commandLineOptions = parseCommandLine(*args)
        parseParameters(commandLineOptions.configFile).run {
            when (mode) {
                DoormanParameters.Mode.ROOT_KEYGEN -> generateRootKeyPair(
                        rootStorePath ?: throw IllegalArgumentException("The 'rootStorePath' parameter must be specified when generating keys!"),
                        rootKeystorePassword,
                        rootPrivateKeyPassword)
                DoormanParameters.Mode.CA_KEYGEN -> generateCAKeyPair(
                        keystorePath ?: throw IllegalArgumentException("The 'keystorePath' parameter must be specified when generating keys!"),
                        rootStorePath ?: throw IllegalArgumentException("The 'rootStorePath' parameter must be specified when generating keys!"),
                        rootKeystorePassword,
                        rootPrivateKeyPassword,
                        keystorePassword,
                        caPrivateKeyPassword)
                DoormanParameters.Mode.DOORMAN -> {
                    val database = configureDatabase(dataSourceProperties, databaseProperties, { throw UnsupportedOperationException() }, SchemaService())
                    val signer = buildLocalSigner(this)

                    val networkParameters = parseNetworkParametersFrom(commandLineOptions.initialNetworkParameters)
                    startDoorman(NetworkHostAndPort(host, port), database, approveAll, networkParameters, signer, approveInterval, signInterval, jiraConfig)
                }
            }
        }
    } catch (e: ShowHelpException) {
        e.parser.printHelpOn(System.out)
    }
}
