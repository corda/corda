/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.rpc

import net.corda.core.internal.noneOrSingle
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.artemis.ArtemisBroker
import net.corda.node.internal.artemis.BrokerAddresses
import net.corda.node.internal.security.RPCSecurityManager
import net.corda.node.services.config.CertChainPolicyConfig
import net.corda.node.internal.artemis.CertificateChainCheckPolicy
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.crypto.loadKeyStore
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import rx.Observable
import java.io.IOException
import java.nio.file.Path
import java.security.KeyStoreException
import java.util.concurrent.CompletableFuture
import javax.security.auth.login.AppConfigurationEntry

internal class ArtemisRpcBroker internal constructor(
        address: NetworkHostAndPort,
        private val adminAddressOptional: NetworkHostAndPort?,
        private val sslOptions: SSLConfiguration,
        private val useSsl: Boolean,
        private val securityManager: RPCSecurityManager,
        private val certificateChainCheckPolicies: List<CertChainPolicyConfig>,
        private val maxMessageSize: Int,
        private val jmxEnabled: Boolean = false,
        private val baseDirectory: Path) : ArtemisBroker {

    companion object {
        private val logger = loggerFor<ArtemisRpcBroker>()

        fun withSsl(address: NetworkHostAndPort, sslOptions: SSLConfiguration, securityManager: RPCSecurityManager, certificateChainCheckPolicies: List<CertChainPolicyConfig>, maxMessageSize: Int, jmxEnabled: Boolean, baseDirectory: Path): ArtemisBroker {
            return ArtemisRpcBroker(address, null, sslOptions, true, securityManager, certificateChainCheckPolicies, maxMessageSize, jmxEnabled, baseDirectory)
        }

        fun withoutSsl(address: NetworkHostAndPort, adminAddress: NetworkHostAndPort, sslOptions: SSLConfiguration, securityManager: RPCSecurityManager, certificateChainCheckPolicies: List<CertChainPolicyConfig>, maxMessageSize: Int, jmxEnabled: Boolean, baseDirectory: Path): ArtemisBroker {
            return ArtemisRpcBroker(address, adminAddress, sslOptions, false, securityManager, certificateChainCheckPolicies, maxMessageSize, jmxEnabled, baseDirectory)
        }
    }

    override fun start() {
        logger.debug("Artemis RPC broker is starting.")
        server.start()
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
        val serverConfiguration = RpcBrokerConfiguration(baseDirectory, maxMessageSize, jmxEnabled, addresses.primary, adminAddressOptional, sslOptions, useSsl)
        val serverSecurityManager = createArtemisSecurityManager(serverConfiguration.loginListener, sslOptions)

        return ActiveMQServerImpl(serverConfiguration, serverSecurityManager).apply {
            registerActivationFailureListener { exception -> throw exception }
            registerPostQueueDeletionCallback { address, qName -> logger.debug("Queue deleted: $qName for $address") }
        }
    }

    @Throws(IOException::class, KeyStoreException::class)
    private fun createArtemisSecurityManager(loginListener: LoginListener, sslOptions: SSLConfiguration): ActiveMQJAASSecurityManager {
        val keyStore = loadKeyStore(sslOptions.sslKeystore, sslOptions.keyStorePassword)
        val trustStore = loadKeyStore(sslOptions.trustStoreFile, sslOptions.trustStorePassword)

        val defaultCertPolicies = mapOf(
                NodeLoginModule.NODE_ROLE to CertificateChainCheckPolicy.LeafMustMatch,
                NodeLoginModule.RPC_ROLE to CertificateChainCheckPolicy.Any
        )
        val certChecks = defaultCertPolicies.mapValues { (role, defaultPolicy) ->
            val policy = certificateChainCheckPolicies.noneOrSingle { it.role == role }?.certificateChainCheckPolicy ?: defaultPolicy
            policy.createCheck(keyStore, trustStore)
        }

        val securityConfig = object : SecurityConfiguration() {
            override fun getAppConfigurationEntry(name: String): Array<AppConfigurationEntry> {
                val options = mapOf(
                        NodeLoginModule.LOGIN_LISTENER_ARG to loginListener,
                        NodeLoginModule.SECURITY_MANAGER_ARG to securityManager,
                        NodeLoginModule.USE_SSL_ARG to useSsl,
                        NodeLoginModule.CERT_CHAIN_CHECKS_ARG to certChecks)
                return arrayOf(AppConfigurationEntry(name, AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options))
            }
        }
        return ActiveMQJAASSecurityManager(NodeLoginModule::class.java.name, securityConfig)
    }
}

typealias LoginListener = (String) -> Unit

private fun <RESULT> CompletableFuture<RESULT>.toObservable() = Observable.from(this)