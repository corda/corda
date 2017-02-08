package com.r3.corda.doorman

import com.google.common.net.HostAndPort
import com.r3.corda.doorman.OptionParserHelper.toConfigWithOptions
import com.r3.corda.doorman.persistence.CertificationRequestStorage
import com.r3.corda.doorman.persistence.DBCertificateRequestStorage
import net.corda.core.crypto.X509Utilities
import net.corda.core.crypto.X509Utilities.CACertAndKey
import net.corda.core.crypto.X509Utilities.CORDA_INTERMEDIATE_CA
import net.corda.core.crypto.X509Utilities.CORDA_INTERMEDIATE_CA_PRIVATE_KEY
import net.corda.core.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.core.crypto.X509Utilities.CORDA_ROOT_CA_PRIVATE_KEY
import net.corda.core.crypto.X509Utilities.addOrReplaceKey
import net.corda.core.crypto.X509Utilities.createIntermediateCert
import net.corda.core.crypto.X509Utilities.loadCertificateAndKey
import net.corda.core.crypto.X509Utilities.loadKeyStore
import net.corda.core.crypto.X509Utilities.loadOrCreateKeyStore
import net.corda.core.crypto.X509Utilities.saveKeyStore
import net.corda.core.div
import net.corda.core.seconds
import net.corda.core.utilities.loggerFor
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.getOrElse
import net.corda.node.services.config.getValue
import net.corda.node.utilities.configureDatabase
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
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
import java.nio.file.Path
import java.nio.file.Paths
import java.security.cert.Certificate
import java.util.*
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 *  CertificateSigningServer runs on Jetty server and provide certificate signing service via http.
 *  The server will require keystorePath, keystore password and key password via command line input.
 *  The Intermediate CA certificate,Intermediate CA private key and Root CA Certificate should use alias name specified in [X509Utilities]
 */
class DoormanServer(webServerAddr: HostAndPort, val caCertAndKey: CACertAndKey, val rootCACert: Certificate, val storage: CertificationRequestStorage) : Closeable {
    companion object {
        val log = loggerFor<DoormanServer>()
    }

    val server: Server = Server(InetSocketAddress(webServerAddr.hostText, webServerAddr.port)).apply {
        server.handler = HandlerCollection().apply {
            addHandler(buildServletContextHandler())
        }
    }

    val hostAndPort: HostAndPort get() = server.connectors
            .map { it as? ServerConnector }
            .filterNotNull()
            .map { HostAndPort.fromParts(it.host, it.localPort) }
            .first()

    override fun close() {
        log.info("Shutting down Doorman Web Services...")
        server.stop()
        server.join()
    }

    fun start() {
        log.info("Starting Doorman Web Services...")
        server.start()
        log.info("Doorman Web Services started on $hostAndPort")
    }

    private fun buildServletContextHandler(): ServletContextHandler {
        return ServletContextHandler().apply {
            contextPath = "/"
            val resourceConfig = ResourceConfig().apply {
                // Add your API provider classes (annotated for JAX-RS) here
                register(DoormanWebService(caCertAndKey, rootCACert, storage))
            }
            val jerseyServlet = ServletHolder(ServletContainer(resourceConfig)).apply {
                initOrder = 0  // Initialise at server start
            }
            addServlet(jerseyServlet, "/api/*")
        }
    }
}

class DoormanParameters(args: Array<String>) {
    private val argConfig = args.toConfigWithOptions {
        accepts("basedir", "Overriding configuration filepath, default to current directory.").withRequiredArg().describedAs("filepath")
        accepts("keygen", "Generate CA keypair and certificate using provide Root CA key.").withOptionalArg()
        accepts("rootKeygen", "Generate Root CA keypair and certificate.").withOptionalArg()
        accepts("approveAll", "Approve all certificate signing request.").withOptionalArg()
        accepts("keystorePath", "CA keystore filepath, default to [basedir]/certificates/caKeystore.jks.").withRequiredArg().describedAs("filepath")
        accepts("rootStorePath", "Root CA keystore filepath, default to [basedir]/certificates/rootCAKeystore.jks.").withRequiredArg().describedAs("filepath")
        accepts("keystorePassword", "CA keystore password.").withRequiredArg().describedAs("password")
        accepts("caPrivateKeyPassword", "CA private key password.").withRequiredArg().describedAs("password")
        accepts("rootKeystorePassword", "Root CA keystore password.").withRequiredArg().describedAs("password")
        accepts("rootPrivateKeyPassword", "Root private key password.").withRequiredArg().describedAs("password")
        accepts("host", "Doorman web service host override").withRequiredArg().describedAs("hostname")
        accepts("port", "Doorman web service port override").withRequiredArg().ofType(Int::class.java).describedAs("port number")
    }
    private val basedir by argConfig.getOrElse { Paths.get(".") }
    private val config = argConfig.withFallback(ConfigHelper.loadConfig(basedir, allowMissingConfig = true))
    val keystorePath: Path by config.getOrElse { basedir / "certificates" / "caKeystore.jks" }
    val rootStorePath: Path by config.getOrElse { basedir / "certificates" / "rootCAKeystore.jks" }
    val keystorePassword: String? by config.getOrElse { null }
    val caPrivateKeyPassword: String? by config.getOrElse { null }
    val rootKeystorePassword: String? by config.getOrElse { null }
    val rootPrivateKeyPassword: String? by config.getOrElse { null }
    val approveAll: Boolean by config
    val host: String by config
    val port: Int by config
    val dataSourceProperties: Properties by  config
    private val keygen: Boolean by config.getOrElse { false }
    private val rootKeygen: Boolean by config.getOrElse { false }

    val mode = if (rootKeygen) Mode.ROOT_KEYGEN else if (keygen) Mode.CA_KEYGEN else Mode.DOORMAN

    enum class Mode {
        DOORMAN, CA_KEYGEN, ROOT_KEYGEN
    }
}

fun main(args: Array<String>) {
    fun readPassword(fmt: String): String {
        return if (System.console() != null) {
            String(System.console().readPassword(fmt))
        } else {
            print(fmt)
            readLine()!!
        }
    }
    DoormanParameters(args).run {
        val log = DoormanServer.log
        when (mode) {
            DoormanParameters.Mode.ROOT_KEYGEN -> {
                println("Generating Root CA keypair and certificate.")
                val rootKeystorePassword = rootKeystorePassword ?: readPassword("Root Keystore Password : ")
                val rootStore = loadOrCreateKeyStore(rootStorePath, rootKeystorePassword)
                val rootPrivateKeyPassword = rootPrivateKeyPassword ?: readPassword("Root Private Key Password : ")

                if (rootStore.containsAlias(CORDA_ROOT_CA_PRIVATE_KEY)) {
                    val oldKey = loadOrCreateKeyStore(rootStorePath, rootKeystorePassword).getCertificate(CORDA_ROOT_CA_PRIVATE_KEY).publicKey
                    println("Key $CORDA_ROOT_CA_PRIVATE_KEY already exists in keystore, process will now terminate.")
                    println(oldKey)
                    exitProcess(1)
                }

                val selfSignCert = X509Utilities.createSelfSignedCACert(CORDA_ROOT_CA)
                rootStore.addOrReplaceKey(CORDA_ROOT_CA_PRIVATE_KEY, selfSignCert.keyPair.private, rootPrivateKeyPassword.toCharArray(), arrayOf(selfSignCert.certificate))
                saveKeyStore(rootStore, rootStorePath, rootKeystorePassword)

                println("Root CA keypair and certificate stored in $rootStorePath.")
                println(loadKeyStore(rootStorePath, rootKeystorePassword).getCertificate(CORDA_ROOT_CA_PRIVATE_KEY).publicKey)
            }
            DoormanParameters.Mode.CA_KEYGEN -> {
                println("Generating Intermediate CA keypair and certificate.")
                val rootKeystorePassword = rootKeystorePassword ?: readPassword("Root Keystore Password : ")
                val rootPrivateKeyPassword = rootPrivateKeyPassword ?: readPassword("Root Private Key Password : ")
                val rootKeyStore = loadKeyStore(rootStorePath, rootKeystorePassword)

                val rootKeyAndCert = loadCertificateAndKey(rootKeyStore, rootPrivateKeyPassword, CORDA_ROOT_CA_PRIVATE_KEY)

                val keystorePassword = keystorePassword ?: readPassword("Keystore Password : ")
                val caPrivateKeyPassword = caPrivateKeyPassword ?: readPassword("CA Private Key Password : ")
                val keyStore = loadOrCreateKeyStore(keystorePath, keystorePassword)

                if (keyStore.containsAlias(CORDA_INTERMEDIATE_CA_PRIVATE_KEY)) {
                    val oldKey = loadOrCreateKeyStore(keystorePath, rootKeystorePassword).getCertificate(CORDA_INTERMEDIATE_CA_PRIVATE_KEY).publicKey
                    println("Key $CORDA_INTERMEDIATE_CA_PRIVATE_KEY already exists in keystore, process will now terminate.")
                    println(oldKey)
                    exitProcess(1)
                }

                val intermediateKeyAndCert = createIntermediateCert(CORDA_INTERMEDIATE_CA, rootKeyAndCert)
                keyStore.addOrReplaceKey(CORDA_INTERMEDIATE_CA_PRIVATE_KEY, intermediateKeyAndCert.keyPair.private,
                        caPrivateKeyPassword.toCharArray(), arrayOf(intermediateKeyAndCert.certificate, rootKeyAndCert.certificate))
                saveKeyStore(keyStore, keystorePath, keystorePassword)
                println("Intermediate CA keypair and certificate stored in $keystorePath.")
                println(loadKeyStore(keystorePath, keystorePassword).getCertificate(CORDA_INTERMEDIATE_CA_PRIVATE_KEY).publicKey)
            }
            DoormanParameters.Mode.DOORMAN -> {
                log.info("Starting certificate signing server.")

                val keystorePassword = keystorePassword ?: readPassword("Keystore Password : ")
                val caPrivateKeyPassword = caPrivateKeyPassword ?: readPassword("CA Private Key Password : ")

                val keystore = X509Utilities.loadKeyStore(keystorePath, keystorePassword)
                val rootCACert = keystore.getCertificateChain(CORDA_INTERMEDIATE_CA_PRIVATE_KEY).last()
                val caCertAndKey = X509Utilities.loadCertificateAndKey(keystore, caPrivateKeyPassword, CORDA_INTERMEDIATE_CA_PRIVATE_KEY)

                // Create DB connection.
                val (datasource, database) = configureDatabase(dataSourceProperties)
                val storage = DBCertificateRequestStorage(database)
                // Daemon thread approving all request periodically.
                val approvalThread = if (approveAll) {
                    thread(name = "Request Approval Daemon") {
                        while (true) {
                            sleep(10.seconds.toMillis())
                            for (id in storage.getPendingRequestIds()) {
                                storage.approveRequest(id, {
                                    JcaPKCS10CertificationRequest(it.request).run {
                                        X509Utilities.createServerCert(subject, publicKey, caCertAndKey,
                                                if (it.ipAddress == it.hostName) listOf() else listOf(it.hostName), listOf(it.ipAddress))
                                    }
                                })
                                log.info("Approved $id")
                            }
                        }
                    }
                } else null
                DoormanServer(HostAndPort.fromParts(host, port), caCertAndKey, rootCACert, storage).use {
                    it.start()
                    it.server.join()
                    approvalThread?.interrupt()
                    approvalThread?.join()
                }
            }
        }
    }
}