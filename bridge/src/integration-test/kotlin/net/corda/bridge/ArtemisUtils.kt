package net.corda.bridge

import com.r3.ha.utilities.InternalArtemisKeystoreGenerator
import net.corda.cliutils.CommonCliConstants
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.internal.artemis.BrokerJaasLoginModule
import net.corda.node.services.config.configureDevKeyAndTrustStores
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.ArtemisTcpTransport
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.DEV_CA_TRUST_STORE_PASS
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.ManagedCryptoService
import net.corda.testing.core.MAX_MESSAGE_SIZE
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.core.config.Configuration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.TextFileCertificateLoginModule
import picocli.CommandLine
import java.nio.file.Path
import javax.security.auth.login.AppConfigurationEntry

const val ARTEMIS_KEYSTORE = "artemis.jks"
const val ARTEMIS_TRUSTSTORE = "artemis-truststore.jks"

fun createArtemis(baseDir: Path, artemisPort: Int, keyStorePassword: String = DEV_CA_KEY_STORE_PASS, truststorePassword: String = DEV_CA_TRUST_STORE_PASS): ActiveMQServer {
    // Generate self signed artemis ssl certificate
    val generator = InternalArtemisKeystoreGenerator()
    CommandLine.populateCommand(generator,
            CommonCliConstants.BASE_DIR, baseDir.toString(),
            "--keyStorePassword", keyStorePassword,
            "--trustStorePassword", truststorePassword)
    generator.runProgram()
    val artemisCertDir = baseDir / "artemis"
    // Start broker
    val artemisSSLConfig = object : MutualSslConfiguration {
        override val useOpenSsl: Boolean = false
        override val keyStore = FileBasedCertificateStoreSupplier(artemisCertDir / ARTEMIS_KEYSTORE, keyStorePassword, keyStorePassword)
        override val trustStore = FileBasedCertificateStoreSupplier(artemisCertDir / ARTEMIS_TRUSTSTORE, truststorePassword, truststorePassword)
    }
    return createArtemisTextCertsLogin(artemisCertDir, artemisPort, artemisSSLConfig)
}

private fun createArtemisTextCertsLogin(artemisDir: Path, p2pPort: Int, p2pSslOptions: MutualSslConfiguration): ActiveMQServer {
    val config = ConfigurationImpl().apply {
        bindingsDirectory = (artemisDir / "bindings").toString()
        journalDirectory = (artemisDir / "journal").toString()
        largeMessagesDirectory = (artemisDir / "large-messages").toString()
        acceptorConfigurations = mutableSetOf(ArtemisTcpTransport.p2pAcceptorTcpTransport(NetworkHostAndPort("0.0.0.0", p2pPort), p2pSslOptions))
        idCacheSize = 2000 // Artemis Default duplicate cache size i.e. a guess
        isPersistIDCache = true
        isPopulateValidatedUser = true
        journalBufferSize_NIO = MAX_MESSAGE_SIZE + ArtemisMessagingComponent.JOURNAL_HEADER_SIZE // Artemis default is 490KiB - required to address IllegalArgumentException (when Artemis uses Java NIO): Record is too large to store.
        journalBufferSize_AIO = MAX_MESSAGE_SIZE + ArtemisMessagingComponent.JOURNAL_HEADER_SIZE // Required to address IllegalArgumentException (when Artemis uses Linux Async IO): Record is too large to store.
        journalFileSize = MAX_MESSAGE_SIZE + ArtemisMessagingComponent.JOURNAL_HEADER_SIZE// The size of each journal file in bytes. Artemis default is 10MiB.
        managementNotificationAddress = SimpleString(ArtemisMessagingComponent.NOTIFICATIONS_ADDRESS)
    }.configureAddressSecurity()

    val usersPropertiesFilePath = ConfigTest::class.java.getResource("/net/corda/bridge/artemis/artemis-users.properties").path
    val rolesPropertiesFilePath = ConfigTest::class.java.getResource("/net/corda/bridge/artemis/artemis-roles.properties").path
    val securityConfiguration = object : SecurityConfiguration() {
        override fun getAppConfigurationEntry(name: String): Array<AppConfigurationEntry> {
            val options = mapOf(
                    "org.apache.activemq.jaas.textfiledn.user" to usersPropertiesFilePath,
                    "org.apache.activemq.jaas.textfiledn.role" to rolesPropertiesFilePath
            )

            return arrayOf(AppConfigurationEntry(name, AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options))
        }
    }
    val securityManager = ActiveMQJAASSecurityManager(TextFileCertificateLoginModule::class.java.name, securityConfiguration)
    return ActiveMQServerImpl(config, securityManager)
}

private fun ConfigurationImpl.configureAddressSecurity(): Configuration {
    val nodeInternalRole = Role("Node", true, true, true, true, true, true, true, true, true, true)
    securityRoles["${ArtemisMessagingComponent.INTERNAL_PREFIX}#"] = setOf(nodeInternalRole)  // Do not add any other roles here as it's only for the node
    securityRoles["${ArtemisMessagingComponent.P2P_PREFIX}#"] = setOf(nodeInternalRole, restrictedRole(BrokerJaasLoginModule.PEER_ROLE, send = true))
    securityRoles["*"] = setOf(Role("guest", true, true, true, true, true, true, true, true, true, true))
    return this
}

private fun restrictedRole(name: String, send: Boolean = false, consume: Boolean = false, createDurableQueue: Boolean = false,
                           deleteDurableQueue: Boolean = false, createNonDurableQueue: Boolean = false,
                           deleteNonDurableQueue: Boolean = false, manage: Boolean = false, browse: Boolean = false): Role {
    return Role(name, send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue,
            deleteNonDurableQueue, manage, browse, createDurableQueue || createNonDurableQueue, deleteDurableQueue || deleteNonDurableQueue)
}

fun createNodeDevCertificates(x500Name: CordaX500Name, nodePath: Path, keyStorePassword: String = DEV_CA_KEY_STORE_PASS, truststorePass: String = DEV_CA_TRUST_STORE_PASS, cryptoService: CryptoService? = null) {
    val certificateDir = nodePath / "certificates"
    val nodeKeystore = FileBasedCertificateStoreSupplier(certificateDir / "nodekeystore.jks", keyStorePassword, keyStorePassword)
    val config = object : MutualSslConfiguration {
        override val useOpenSsl = false
        override val keyStore = FileBasedCertificateStoreSupplier(certificateDir / "sslkeystore.jks", keyStorePassword, keyStorePassword)
        override val trustStore = FileBasedCertificateStoreSupplier(certificateDir / "truststore.jks", truststorePass, truststorePass)
    }
    config.configureDevKeyAndTrustStores(x500Name, nodeKeystore, certificateDir, cryptoService?.let { ManagedCryptoService(it) })
}