package net.corda.node.services.rpc

import net.corda.core.internal.errors.AddressBindingException
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.artemis.*
import net.corda.node.internal.artemis.BrokerJaasLoginModule.Companion.NODE_SECURITY_CONFIG
import net.corda.node.internal.artemis.BrokerJaasLoginModule.Companion.RPC_SECURITY_CONFIG
import net.corda.node.internal.security.RPCSecurityManager
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import java.io.IOException
import java.nio.file.Path
import java.security.KeyStoreException
import javax.security.auth.login.AppConfigurationEntry

class ArtemisRpcBroker internal constructor(
        address: NetworkHostAndPort,
        private val adminAddressOptional: NetworkHostAndPort?,
        private val sslOptions: BrokerRpcSslOptions?,
        private val useSsl: Boolean,
        private val securityManager: RPCSecurityManager,
        private val maxMessageSize: Int,
        private val jmxEnabled: Boolean = false,
        private val baseDirectory: Path,
        private val nodeConfiguration: MutualSslConfiguration,
        private val shouldStartLocalShell: Boolean) : ArtemisBroker {

    companion object {
        private val logger = loggerFor<ArtemisRpcBroker>()

        fun withSsl(configuration: MutualSslConfiguration, address: NetworkHostAndPort, adminAddress: NetworkHostAndPort, sslOptions: BrokerRpcSslOptions, securityManager: RPCSecurityManager, maxMessageSize: Int, jmxEnabled: Boolean, baseDirectory: Path, shouldStartLocalShell: Boolean): ArtemisBroker {
            return ArtemisRpcBroker(address, adminAddress, sslOptions, true, securityManager, maxMessageSize, jmxEnabled, baseDirectory, configuration, shouldStartLocalShell)
        }

        fun withoutSsl(configuration: MutualSslConfiguration, address: NetworkHostAndPort, adminAddress: NetworkHostAndPort, securityManager: RPCSecurityManager, maxMessageSize: Int, jmxEnabled: Boolean, baseDirectory: Path, shouldStartLocalShell: Boolean): ArtemisBroker {
            return ArtemisRpcBroker(address, adminAddress, null, false, securityManager, maxMessageSize, jmxEnabled, baseDirectory, configuration, shouldStartLocalShell)
        }
    }

    override fun start() {
        logger.debug("Artemis RPC broker is starting.")
        try {
            server.start()
        } catch (e: java.io.IOException) {
            if (e.isBindingError()) {
                throw AddressBindingException(adminAddressOptional?.let { setOf(it, addresses.primary) } ?: setOf(addresses.primary))
            } else {
                throw e
            }
        }
        logger.debug("Artemis RPC broker is started.")
    }

    override fun stop() {
        logger.debug("Artemis RPC broker is stopping.")
        server.stop(true)
        logger.debug("Artemis RPC broker is stopped.")
    }

    override val started get() = server.isStarted

    override val serverControl: ActiveMQServerControl get() = server.activeMQServerControl

    override val addresses = BrokerAddresses(address, adminAddressOptional ?: address)

    private val server = initialiseServer()

    private fun initialiseServer(): ActiveMQServer {
        val serverConfiguration = RpcBrokerConfiguration(baseDirectory, maxMessageSize, jmxEnabled, addresses.primary, adminAddressOptional, sslOptions, useSsl, nodeConfiguration, shouldStartLocalShell)
        val serverSecurityManager = createArtemisSecurityManager(serverConfiguration.loginListener)

        return ActiveMQServerImpl(serverConfiguration, serverSecurityManager).apply {
            registerActivationFailureListener { exception -> throw exception }
            registerPostQueueDeletionCallback { address, qName -> logger.debug("Queue deleted: $qName for $address") }
        }
    }

    @Throws(IOException::class, KeyStoreException::class)
    private fun createArtemisSecurityManager(loginListener: LoginListener): ActiveMQJAASSecurityManager {
        val keyStore = nodeConfiguration.keyStore.get()
        val trustStore = nodeConfiguration.trustStore.get()

        val securityConfig = object : SecurityConfiguration() {
            override fun getAppConfigurationEntry(name: String): Array<AppConfigurationEntry> {
                val options = mapOf(
                        RPC_SECURITY_CONFIG to RPCJaasConfig(securityManager, loginListener, useSsl),
                        NODE_SECURITY_CONFIG to NodeJaasConfig(keyStore.value.internal, trustStore.value.internal)
                )
                return arrayOf(AppConfigurationEntry(name, AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options))
            }
        }
        return ActiveMQJAASSecurityManager(BrokerJaasLoginModule::class.java.name, securityConfig)
    }
}

typealias LoginListener = (String) -> Unit
