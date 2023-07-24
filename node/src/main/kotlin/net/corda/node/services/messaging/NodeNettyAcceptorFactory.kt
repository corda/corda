package net.corda.node.services.messaging

import io.netty.buffer.ByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.group.ChannelGroup
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.SslHandshakeTimeoutException
import net.corda.core.internal.declaredField
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.ArtemisTcpTransport
import net.corda.nodeapi.internal.protonwrapper.netty.sslDelegatedTaskExecutor
import net.corda.nodeapi.internal.setThreadPoolName
import org.apache.activemq.artemis.api.core.BaseInterceptor
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection
import org.apache.activemq.artemis.core.server.routing.RoutingHandler
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager
import org.apache.activemq.artemis.spi.core.remoting.Acceptor
import org.apache.activemq.artemis.spi.core.remoting.AcceptorFactory
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler
import org.apache.activemq.artemis.spi.core.remoting.ServerConnectionLifeCycleListener
import org.apache.activemq.artemis.spi.core.remoting.ssl.OpenSSLContextFactoryProvider
import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextFactoryProvider
import org.apache.activemq.artemis.utils.ConfigurationHelper
import org.apache.activemq.artemis.utils.actors.OrderedExecutor
import java.net.SocketAddress
import java.nio.channels.ClosedChannelException
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService
import java.util.regex.Pattern
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLPeerUnverifiedException

@Suppress("unused")  // Used via reflection in ArtemisTcpTransport
class NodeNettyAcceptorFactory : AcceptorFactory {
    override fun createAcceptor(name: String?,
                                clusterConnection: ClusterConnection?,
                                configuration: Map<String, Any>,
                                handler: BufferHandler?,
                                listener: ServerConnectionLifeCycleListener?,
                                threadPool: Executor,
                                scheduledThreadPool: ScheduledExecutorService,
                                protocolMap: MutableMap<String, ProtocolManager<BaseInterceptor<*>, RoutingHandler<*>>>?): Acceptor {
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
                                    protocolMap: MutableMap<String, ProtocolManager<BaseInterceptor<*>, RoutingHandler<*>>>?,
                                    private val threadPoolName: String) :
            NettyAcceptor(name, clusterConnection, configuration, handler, listener, scheduledThreadPool, failureExecutor, protocolMap)
    {
        companion object {
            private val defaultThreadPoolNamePattern = Pattern.compile("""Thread-(\d+) \(activemq-netty-threads\)""")

            init {
                // Make sure Artemis isn't using another (Open)SSLContextFactory
                check(SSLContextFactoryProvider.getSSLContextFactory() is NodeSSLContextFactory)
                check(OpenSSLContextFactoryProvider.getOpenSSLContextFactory() is NodeOpenSSLContextFactory)
            }
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
        override fun getSslHandler(alloc: ByteBufAllocator?, peerHost: String?, peerPort: Int): SslHandler {
            applyThreadPoolName()
            val engine = super.getSslHandler(alloc, peerHost, peerPort).engine()
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
    }


    private class NodeAcceptorSslHandler(engine: SSLEngine,
                                         delegatedTaskExecutor: Executor,
                                         private val trace: Boolean) : SslHandler(engine, delegatedTaskExecutor) {
        companion object {
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
                    it.isSuccess -> logger.info("SSL handshake completed in ${duration}ms with $peer")
                    it.isCancelled -> logger.warn("SSL handshake cancelled after ${duration}ms with $peer")
                    else -> when (it.cause()) {
                        is ClosedChannelException -> logger.warn("SSL handshake closed early after ${duration}ms with $peer")
                        is SslHandshakeTimeoutException -> logger.warn("SSL handshake timed out after ${duration}ms with $peer")
                        else -> logger.warn("SSL handshake failed after ${duration}ms with $peer", it.cause())
                    }
                }
            }
        }
    }
}
