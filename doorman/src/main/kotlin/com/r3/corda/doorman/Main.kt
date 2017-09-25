package com.r3.corda.doorman

import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.google.common.net.HostAndPort
import com.r3.corda.doorman.DoormanServer.Companion.logger
import com.r3.corda.doorman.persistence.ApprovingAllCertificateRequestStorage
import com.r3.corda.doorman.persistence.CertificationRequestStorage
import com.r3.corda.doorman.persistence.DBCertificateRequestStorage
import com.r3.corda.doorman.persistence.DoormanSchemaService
import com.r3.corda.doorman.signer.*
import net.corda.core.crypto.Crypto
import net.corda.core.internal.createDirectories
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.seconds
import net.corda.node.utilities.*
import net.corda.node.utilities.X509Utilities.CORDA_INTERMEDIATE_CA
import net.corda.node.utilities.X509Utilities.CORDA_ROOT_CA
import net.corda.node.utilities.X509Utilities.createCertificate
import org.bouncycastle.asn1.x500.X500Name
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import java.io.Closeable
import java.lang.Thread.sleep
import java.net.InetSocketAddress
import java.net.URI
import java.time.Instant
import kotlin.concurrent.thread
import kotlin.system.exitProcess


/**
 *  DoormanServer runs on Jetty server and provide certificate signing service via http.
 *  The server will require keystorePath, keystore password and key password via command line input.
 *  The Intermediate CA certificate,Intermediate CA private key and Root CA Certificate should use alias name specified in [X509Utilities]
 */
class DoormanServer(webServerAddr: HostAndPort, val csrHandler: DefaultCsrHandler) : Closeable {
    val serverStatus = DoormanServerStatus()

    companion object {
        val logger = loggerFor<DoormanServer>()
    }

    private val server: Server = Server(InetSocketAddress(webServerAddr.host, webServerAddr.port)).apply {
        handler = HandlerCollection().apply {
            addHandler(buildServletContextHandler())
        }
    }

    val hostAndPort: HostAndPort
        get() = server.connectors
                .map { it as? ServerConnector }
                .filterNotNull()
                .map { HostAndPort.fromParts(it.host, it.localPort) }
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
        serverStatus.serverStartTime = Instant.now()

        // Thread approving request periodically.
        thread(name = "Request Approval Thread") {
            while (true) {
                try {
                    sleep(10.seconds.toMillis())
                    // TODO: Handle rejected request?
                    serverStatus.lastRequestCheckTime = Instant.now()
                    csrHandler.sign()
                } catch (e: Exception) {
                    // Log the error and carry on.
                    logger.error("Error encountered when approving request.", e)
                }
            }
        }
    }

    private fun buildServletContextHandler(): ServletContextHandler {
        return ServletContextHandler().apply {
            contextPath = "/"
            val resourceConfig = ResourceConfig().apply {
                // Add your API provider classes (annotated for JAX-RS) here
                register(DoormanWebService(csrHandler, serverStatus))
            }
            val jerseyServlet = ServletHolder(ServletContainer(resourceConfig)).apply {
                initOrder = 0  // Initialise at server start
            }
            addServlet(jerseyServlet, "/api/*")
        }
    }
}

data class DoormanServerStatus(var serverStartTime: Instant? = null,
                               var lastRequestCheckTime: Instant? = null)

/** Read password from console, do a readLine instead if console is null (e.g. when debugging in IDE). */
internal fun readPassword(fmt: String): String {
    return if (System.console() != null) {
        String(System.console().readPassword(fmt))
    } else {
        print(fmt)
        readLine()!!
    }
}

private fun DoormanParameters.generateRootKeyPair() {
    if (rootStorePath == null) {
        throw IllegalArgumentException("The 'rootStorePath' parameter must be specified when generating keys!")
    }
    println("Generating Root CA keypair and certificate.")
    // Get password from console if not in config.
    val rootKeystorePassword = rootKeystorePassword ?: readPassword("Root Keystore Password: ")
    // Ensure folder exists.
    rootStorePath.parent.createDirectories()
    val rootStore = loadOrCreateKeyStore(rootStorePath, rootKeystorePassword)
    val rootPrivateKeyPassword = rootPrivateKeyPassword ?: readPassword("Root Private Key Password: ")

    if (rootStore.containsAlias(CORDA_ROOT_CA)) {
        val oldKey = loadOrCreateKeyStore(rootStorePath, rootKeystorePassword).getCertificate(CORDA_ROOT_CA).publicKey
        println("Key $CORDA_ROOT_CA already exists in keystore, process will now terminate.")
        println(oldKey)
        exitProcess(1)
    }

    val selfSignKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val selfSignCert = X509Utilities.createSelfSignedCACertificate(X500Name("CN=Corda Root CA, O=R3, OU=Corda, L=London, C=GB"), selfSignKey)
    rootStore.addOrReplaceKey(CORDA_ROOT_CA, selfSignKey.private, rootPrivateKeyPassword.toCharArray(), arrayOf(selfSignCert))
    rootStore.save(rootStorePath, rootKeystorePassword)

    println("Root CA keypair and certificate stored in $rootStorePath.")
    println(loadKeyStore(rootStorePath, rootKeystorePassword).getCertificate(CORDA_ROOT_CA).publicKey)
}

private fun DoormanParameters.generateCAKeyPair() {
    if (keystorePath == null) {
        throw IllegalArgumentException("The 'keystorePath' parameter must be specified when generating keys!")
    }

    if (rootStorePath == null) {
        throw IllegalArgumentException("The 'rootStorePath' parameter must be specified when generating keys!")
    }
    println("Generating Intermediate CA keypair and certificate using root keystore $rootStorePath.")
    // Get password from console if not in config.
    val rootKeystorePassword = rootKeystorePassword ?: readPassword("Root Keystore Password: ")
    val rootPrivateKeyPassword = rootPrivateKeyPassword ?: readPassword("Root Private Key Password: ")
    val rootKeyStore = loadKeyStore(rootStorePath, rootKeystorePassword)

    val rootKeyAndCert = rootKeyStore.getCertificateAndKeyPair(CORDA_ROOT_CA, rootPrivateKeyPassword)

    val keystorePassword = keystorePassword ?: readPassword("Keystore Password: ")
    val caPrivateKeyPassword = caPrivateKeyPassword ?: readPassword("CA Private Key Password: ")
    // Ensure folder exists.
    keystorePath.parent.createDirectories()
    val keyStore = loadOrCreateKeyStore(keystorePath, keystorePassword)

    if (keyStore.containsAlias(CORDA_INTERMEDIATE_CA)) {
        val oldKey = loadOrCreateKeyStore(keystorePath, rootKeystorePassword).getCertificate(CORDA_INTERMEDIATE_CA).publicKey
        println("Key $CORDA_INTERMEDIATE_CA already exists in keystore, process will now terminate.")
        println(oldKey)
        exitProcess(1)
    }

    val intermediateKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val intermediateCert = createCertificate(CertificateType.INTERMEDIATE_CA, rootKeyAndCert.certificate, rootKeyAndCert.keyPair, X500Name("CN=Corda Intermediate CA, O=R3, OU=Corda, L=London, C=GB"), intermediateKey.public)
    keyStore.addOrReplaceKey(CORDA_INTERMEDIATE_CA, intermediateKey.private,
            caPrivateKeyPassword.toCharArray(), arrayOf(intermediateCert, rootKeyAndCert.certificate))
    keyStore.save(keystorePath, keystorePassword)
    println("Intermediate CA keypair and certificate stored in $keystorePath.")
    println(loadKeyStore(keystorePath, keystorePassword).getCertificate(CORDA_INTERMEDIATE_CA).publicKey)
}

private fun DoormanParameters.startDoorman(isLocalSigning: Boolean = false) {
    logger.info("Starting Doorman server.")
    // Create DB connection.
    val database = configureDatabase(dataSourceProperties, databaseProperties, { DoormanSchemaService() }, createIdentityService = {
        // Identity service not needed doorman, corda persistence is not very generic.
        throw UnsupportedOperationException()
    })
    val csrHandler = if (jiraConfig == null) {
        logger.warn("Doorman server is in 'Approve All' mode, this will approve all incoming certificate signing request.")
        val storage = ApprovingAllCertificateRequestStorage(database)
        DefaultCsrHandler(storage, buildLocalSigner(storage, this))
    } else {
        val storage = DBCertificateRequestStorage(database)
        val signer = if (isLocalSigning) {
            buildLocalSigner(storage, this)
        } else {
            ExternalSigner()
        }
        val jiraClient = AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(URI(jiraConfig.address), jiraConfig.username, jiraConfig.password)
        JiraCsrHandler(jiraClient, jiraConfig.projectCode, jiraConfig.doneTransitionCode, storage, signer)
    }
    val doorman = DoormanServer(HostAndPort.fromParts(host, port), csrHandler)
    doorman.start()
    Runtime.getRuntime().addShutdownHook(thread(start = false) { doorman.close() })
}

private fun buildLocalSigner(storage: CertificationRequestStorage, parameters: DoormanParameters): Signer {
    checkNotNull(parameters.keystorePath) {"The keystorePath parameter must be specified when using local signing!"}
    // Get password from console if not in config.
    val keystorePassword = parameters.keystorePassword ?: readPassword("Keystore Password: ")
    val caPrivateKeyPassword = parameters.caPrivateKeyPassword ?: readPassword("CA Private Key Password: ")
    val keystore = loadOrCreateKeyStore(parameters.keystorePath!!, keystorePassword)
    val rootCACert = keystore.getCertificateChain(X509Utilities.CORDA_INTERMEDIATE_CA).last()
    val caCertAndKey = keystore.getCertificateAndKeyPair(X509Utilities.CORDA_INTERMEDIATE_CA, caPrivateKeyPassword)
    return LocalSigner(storage, caCertAndKey, rootCACert)
}

fun main(args: Array<String>) {
    try {
        // TODO : Remove config overrides and solely use config file after testnet is finalized.
        parseParameters(*args).run {
            when (mode) {
                DoormanParameters.Mode.ROOT_KEYGEN -> generateRootKeyPair()
                DoormanParameters.Mode.CA_KEYGEN -> generateCAKeyPair()
                DoormanParameters.Mode.DOORMAN -> startDoorman(keystorePath != null)
            }
        }
    } catch (e: ShowHelpException) {
        e.parser.printHelpOn(System.out)
    }
}
