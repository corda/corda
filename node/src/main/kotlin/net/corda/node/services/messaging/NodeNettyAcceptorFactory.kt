package net.corda.node.services.messaging

import io.netty.buffer.ByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.group.ChannelGroup
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.SslHandshakeTimeoutException
import io.netty.handler.ssl.SslProvider
import net.corda.core.internal.declaredField
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.nodeapi.internal.ArtemisTcpTransport
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.protonwrapper.netty.createAndInitSslContext
import net.corda.nodeapi.internal.protonwrapper.netty.keyManagerFactory
import net.corda.nodeapi.internal.protonwrapper.netty.sslDelegatedTaskExecutor
import net.corda.nodeapi.internal.setThreadPoolName
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration
import org.apache.activemq.artemis.api.core.BaseInterceptor
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants
import org.apache.activemq.artemis.core.remoting.impl.ssl.SSLSupport
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager
import org.apache.activemq.artemis.spi.core.remoting.Acceptor
import org.apache.activemq.artemis.spi.core.remoting.AcceptorFactory
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler
import org.apache.activemq.artemis.spi.core.remoting.ServerConnectionLifeCycleListener
import org.apache.activemq.artemis.utils.ConfigurationHelper
import org.apache.activemq.artemis.utils.actors.OrderedExecutor
import java.net.SocketAddress
import java.nio.channels.ClosedChannelException
import java.nio.file.Paths
import java.security.PrivilegedExceptionAction
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService
import java.util.regex.Pattern
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManagerFactory
import javax.security.auth.Subject

@Suppress("unused", "TooGenericExceptionCaught", "ComplexMethod", "MagicNumber", "TooManyFunctions")
class NodeNettyAcceptorFactory : AcceptorFactory {
    override fun createAcceptor(name: String?,
                                clusterConnection: ClusterConnection?,
                                configuration: Map<String, Any>,
                                handler: BufferHandler?,
                                listener: ServerConnectionLifeCycleListener?,
                                threadPool: Executor,
                                scheduledThreadPool: ScheduledExecutorService,
                                protocolMap: Map<String, ProtocolManager<BaseInterceptor<*>>>?): Acceptor {
        val threadPoolName = ConfigurationHelper.getStringProperty(ArtemisTcpTransport.THREAD_POOL_NAME_NAME, "Acceptor", configuration)
        threadPool.setThreadPoolName("$threadPoolName-artemis")
        scheduledThreadPool.setThreadPoolName("$threadPoolName-artemis-scheduler")
        val failureExecutor = OrderedExecutor(threadPool)
        return NodeNettyAcceptor(
                name,
                clusterConnection,
                configuration,
                handler,
                listener,
                scheduledThreadPool,
                failureExecutor,
                protocolMap,
                "$threadPoolName-netty"
        )
    }


    private class NodeNettyAcceptor(name: String?,
                                    clusterConnection: ClusterConnection?,
                                    configuration: Map<String, Any>,
                                    handler: BufferHandler?,
                                    listener: ServerConnectionLifeCycleListener?,
                                    scheduledThreadPool: ScheduledExecutorService?,
                                    failureExecutor: Executor,
                                    protocolMap: Map<String, ProtocolManager<BaseInterceptor<*>>>?,
                                    private val threadPoolName: String) :
            NettyAcceptor(name, clusterConnection, configuration, handler, listener, scheduledThreadPool, failureExecutor, protocolMap)
    {
        companion object {
            private val defaultThreadPoolNamePattern = Pattern.compile("""Thread-(\d+) \(activemq-netty-threads\)""")
        }

        private val sslDelegatedTaskExecutor = sslDelegatedTaskExecutor(threadPoolName)
        private val trace = ConfigurationHelper.getBooleanProperty(ArtemisTcpTransport.TRACE_NAME, false, configuration)

        @Synchronized
        override fun start() {
            super.start()
            if (trace) {
                // Unfortunately we have to resort to reflection to be able to get access to the server channel(s)
                declaredField<ChannelGroup>("serverChannelGroup").value.forEach { channel ->
                    channel.pipeline().addLast("logger", LoggingHandler(LogLevel.INFO))
                }
            }
        }

        @Synchronized
        override fun stop() {
            super.stop()
            sslDelegatedTaskExecutor.shutdown()
        }

        @Synchronized
        override fun getSslHandler(alloc: ByteBufAllocator?): SslHandler {
            applyThreadPoolName()
            val engine = getSSLEngine(alloc)
            val sslHandler = NodeAcceptorSslHandler(engine, sslDelegatedTaskExecutor, trace)
            val handshakeTimeout = configuration[ArtemisTcpTransport.SSL_HANDSHAKE_TIMEOUT_NAME] as Duration?
            if (handshakeTimeout != null) {
                sslHandler.handshakeTimeoutMillis = handshakeTimeout.toMillis()
            }
            return sslHandler
        }

        /**
         * [NettyAcceptor.start] has hardcoded the thread pool name and does not provide a way to configure it. This is a workaround.
         */
        private fun applyThreadPoolName() {
            val matcher = defaultThreadPoolNamePattern.matcher(Thread.currentThread().name)
            if (matcher.matches()) {
                Thread.currentThread().name = "$threadPoolName-${matcher.group(1)}" // Preserve the pool thread number
            }
        }

        /**
         * This is a copy of [NettyAcceptor.getSslHandler] so that we can provide different implementations for [loadOpenSslEngine] and
         * [loadJdkSslEngine]. [NodeNettyAcceptor], instead of creating a default [TrustManagerFactory], will simply use the provided one in
         * the [ArtemisTcpTransport.TRUST_MANAGER_FACTORY_NAME] configuration.
         */
        private fun getSSLEngine(alloc: ByteBufAllocator?): SSLEngine {
            val engine = if (sslProvider == TransportConstants.OPENSSL_PROVIDER) {
                loadOpenSslEngine(alloc)
            } else {
                loadJdkSslEngine()
            }
            engine.useClientMode = false
            if (needClientAuth) {
                engine.needClientAuth = true
            }

            // setting the enabled cipher suites resets the enabled protocols so we need
            // to save the enabled protocols so that after the customer cipher suite is enabled
            // we can reset the enabled protocols if a customer protocol isn't specified
            val originalProtocols = engine.enabledProtocols
            if (enabledCipherSuites != null) {
                try {
                    engine.enabledCipherSuites = SSLSupport.parseCommaSeparatedListIntoArray(enabledCipherSuites)
                } catch (e: IllegalArgumentException) {
                    ActiveMQServerLogger.LOGGER.invalidCipherSuite(SSLSupport.parseArrayIntoCommandSeparatedList(engine.supportedCipherSuites))
                    throw e
                }
            }
            if (enabledProtocols != null) {
                try {
                    engine.enabledProtocols = SSLSupport.parseCommaSeparatedListIntoArray(enabledProtocols)
                } catch (e: IllegalArgumentException) {
                    ActiveMQServerLogger.LOGGER.invalidProtocol(SSLSupport.parseArrayIntoCommandSeparatedList(engine.supportedProtocols))
                    throw e
                }
            } else {
                engine.enabledProtocols = originalProtocols
            }
            return engine
        }

        /**
         * Copy of [NettyAcceptor.loadOpenSslEngine] which invokes our custom [createOpenSslContext].
         */
        private fun loadOpenSslEngine(alloc: ByteBufAllocator?): SSLEngine {
            val context = try {
                // We copied all this code just so we could replace the SSLSupport.createNettyContext method call with our own one.
                createOpenSslContext()
            } catch (e: Exception) {
                throw IllegalStateException("Unable to create NodeNettyAcceptor", e)
            }
            return Subject.doAs<SSLEngine>(null, PrivilegedExceptionAction {
                context.newEngine(alloc)
            })
        }

        /**
         * Copy of [NettyAcceptor.loadJdkSslEngine] which invokes our custom [createJdkSSLContext].
         */
        private fun loadJdkSslEngine(): SSLEngine {
            val context = try {
                // We copied all this code just so we could replace the SSLHelper.createContext method call with our own one.
                createJdkSSLContext()
            } catch (e: Exception) {
                throw IllegalStateException("Unable to create NodeNettyAcceptor", e)
            }
            return Subject.doAs<SSLEngine>(null, PrivilegedExceptionAction {
                context.createSSLEngine()
            })
        }

        /**
         * Create an [SSLContext] using the [TrustManagerFactory] provided on the [ArtemisTcpTransport.TRUST_MANAGER_FACTORY_NAME] config.
         */
        private fun createJdkSSLContext(): SSLContext {
            return createAndInitSslContext(
                    createKeyManagerFactory(),
                    configuration[ArtemisTcpTransport.TRUST_MANAGER_FACTORY_NAME] as TrustManagerFactory?
            )
        }

        /**
         * Create an [SslContext] using the the [TrustManagerFactory] provided on the [ArtemisTcpTransport.TRUST_MANAGER_FACTORY_NAME] config.
         */
        private fun createOpenSslContext(): SslContext {
            return SslContextBuilder
                    .forServer(createKeyManagerFactory())
                    .sslProvider(SslProvider.OPENSSL)
                    .trustManager(configuration[ArtemisTcpTransport.TRUST_MANAGER_FACTORY_NAME] as TrustManagerFactory?)
                    .build()
        }

        private fun createKeyManagerFactory(): KeyManagerFactory {
            return keyManagerFactory(CertificateStore.fromFile(Paths.get(keyStorePath), keyStorePassword, keyStorePassword, false))
        }

        // Replicate the fields which are private in NettyAcceptor
        private val sslProvider = ConfigurationHelper.getStringProperty(TransportConstants.SSL_PROVIDER, TransportConstants.DEFAULT_SSL_PROVIDER, configuration)
        private val needClientAuth = ConfigurationHelper.getBooleanProperty(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, TransportConstants.DEFAULT_NEED_CLIENT_AUTH, configuration)
        private val enabledCipherSuites = ConfigurationHelper.getStringProperty(TransportConstants.ENABLED_CIPHER_SUITES_PROP_NAME, TransportConstants.DEFAULT_ENABLED_CIPHER_SUITES, configuration)
        private val enabledProtocols = ConfigurationHelper.getStringProperty(TransportConstants.ENABLED_PROTOCOLS_PROP_NAME, TransportConstants.DEFAULT_ENABLED_PROTOCOLS, configuration)
        private val keyStorePath = ConfigurationHelper.getStringProperty(TransportConstants.KEYSTORE_PATH_PROP_NAME, TransportConstants.DEFAULT_KEYSTORE_PATH, configuration)
        private val keyStoreProvider = ConfigurationHelper.getStringProperty(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, TransportConstants.DEFAULT_KEYSTORE_PROVIDER, configuration)
        private val keyStorePassword = ConfigurationHelper.getPasswordProperty(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, TransportConstants.DEFAULT_KEYSTORE_PASSWORD, configuration, ActiveMQDefaultConfiguration.getPropMaskPassword(), ActiveMQDefaultConfiguration.getPropPasswordCodec())
    }


    private class NodeAcceptorSslHandler(engine: SSLEngine,
                                         delegatedTaskExecutor: Executor,
                                         private val trace: Boolean) : SslHandler(engine, delegatedTaskExecutor) {
        companion object {
            private val nettyLogHandshake = System.getProperty("net.corda.node.services.messaging.nettyLogHandshake")?.toBoolean() ?: false
            private val logger = contextLogger()
        }

        override fun handlerAdded(ctx: ChannelHandlerContext) {
            logHandshake(ctx.channel().remoteAddress())
            super.handlerAdded(ctx)
            // Unfortunately NettyAcceptor does not let us add extra child handlers, so we have to add our logger this way.
            if (trace) {
                ctx.pipeline().addLast("logger", LoggingHandler(LogLevel.INFO))
            }
        }

        private fun logHandshake(remoteAddress: SocketAddress) {
            val start = System.currentTimeMillis()
            handshakeFuture().addListener {
                val duration = System.currentTimeMillis() - start
                val peer = try {
                    engine().session.peerPrincipal
                } catch (e: SSLPeerUnverifiedException) {
                    remoteAddress
                }
                when {
                    it.isSuccess -> loggerInfo { "SSL handshake completed in ${duration}ms with $peer" }
                    it.isCancelled -> loggerWarn { "SSL handshake cancelled after ${duration}ms with $peer" }
                    else -> when (it.cause()) {
                        is ClosedChannelException -> loggerWarn { "SSL handshake closed early after ${duration}ms with $peer" }
                        is SslHandshakeTimeoutException -> loggerWarn { "SSL handshake timed out after ${duration}ms with $peer" }
                        else -> loggerWarn(it.cause()) {"SSL handshake failed after ${duration}ms with $peer" }
                    }
                }
            }
        }
        private fun loggerInfo(msgFn: () -> String) {
            if (nettyLogHandshake && logger.isInfoEnabled) {
                logger.info(msgFn())
            }
            else {
                logger.trace { msgFn() }
            }
        }
        private fun loggerWarn(t: Throwable? = null, msgFn: () -> String) {
            if (nettyLogHandshake && logger.isWarnEnabled) {
                logger.warn(msgFn(), t)
            }
            else if (logger.isTraceEnabled) {
                logger.trace(msgFn(), t)
            }
        }
    }
}
