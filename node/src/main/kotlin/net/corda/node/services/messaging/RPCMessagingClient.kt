/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.messaging

import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.RPCOps
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.internal.security.RPCSecurityManager
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_USER
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl

class RPCMessagingClient(
        private val config: SSLConfiguration,
        serverAddress: NetworkHostAndPort,
        maxMessageSize: Int,
        private val rpcServerConfiguration: RPCServerConfiguration = RPCServerConfiguration.default
) : SingletonSerializeAsToken(), AutoCloseable {
    private val artemis = ArtemisMessagingClient(config, serverAddress, maxMessageSize)
    private var rpcServer: RPCServer? = null

    fun start(rpcOps: RPCOps, securityManager: RPCSecurityManager) = synchronized(this) {
        val locator = artemis.start().sessionFactory.serverLocator
        val myCert = config.loadSslKeyStore().getCertificate(X509Utilities.CORDA_CLIENT_TLS)
        rpcServer = RPCServer(rpcOps, NODE_USER, NODE_USER, locator, securityManager, CordaX500Name.build(myCert.subjectX500Principal), rpcServerConfiguration)
    }

    fun start2(serverControl: ActiveMQServerControl) = synchronized(this) {
        rpcServer!!.start(serverControl)
    }

    fun stop() = synchronized(this) {
        rpcServer?.close()
        artemis.stop()
    }

    override fun close() = stop()
}
