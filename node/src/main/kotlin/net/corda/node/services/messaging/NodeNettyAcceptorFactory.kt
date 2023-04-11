package net.corda.node.services.messaging

import io.netty.buffer.ByteBufAllocator
import io.netty.channel.group.ChannelGroup
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslHandler
import net.corda.core.internal.declaredField
import net.corda.nodeapi.internal.ArtemisTcpTransport
import org.apache.activemq.artemis.api.core.BaseInterceptor
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager
import org.apache.activemq.artemis.spi.core.remoting.Acceptor
import org.apache.activemq.artemis.spi.core.remoting.AcceptorFactory
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler
import org.apache.activemq.artemis.spi.core.remoting.ServerConnectionLifeCycleListener
import org.apache.activemq.artemis.utils.actors.OrderedExecutor
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService

@Suppress("unused")  // Used via reflection in ArtemisTcpTransport
class NodeNettyAcceptorFactory : AcceptorFactory {
    override fun createAcceptor(name: String?,
                                clusterConnection: ClusterConnection?,
                                configuration: Map<String, Any>,
                                handler: BufferHandler?,
                                listener: ServerConnectionLifeCycleListener?,
                                threadPool: Executor,
                                scheduledThreadPool: ScheduledExecutorService?,
                                protocolMap: Map<String, ProtocolManager<BaseInterceptor<*>>>?): Acceptor {
        val failureExecutor = OrderedExecutor(threadPool)
        return NodeNettyAcceptor(name, clusterConnection, configuration, handler, listener, scheduledThreadPool, failureExecutor, protocolMap)
    }

    private class NodeNettyAcceptor(name: String?,
                                    clusterConnection: ClusterConnection?,
                                    configuration: Map<String, Any>,
                                    handler: BufferHandler?,
                                    listener: ServerConnectionLifeCycleListener?,
                                    scheduledThreadPool: ScheduledExecutorService?,
                                    failureExecutor: Executor,
                                    protocolMap: Map<String, ProtocolManager<BaseInterceptor<*>>>?) :
            NettyAcceptor(name, clusterConnection, configuration, handler, listener, scheduledThreadPool, failureExecutor, protocolMap)
    {
        override fun start() {
            super.start()
            if (configuration[ArtemisTcpTransport.TRACE_NAME] == true) {
                // Artemis does not seem to allow access to the underlying channel so we resort to reflection and get it via the
                // serverChannelGroup field. This field is only available after start(), hence why we add the logger here.
                declaredField<ChannelGroup>("serverChannelGroup").value.forEach { channel ->
                    channel.pipeline().addLast("logger", LoggingHandler(LogLevel.INFO))
                }
            }
        }

        override fun getSslHandler(alloc: ByteBufAllocator?): SslHandler {
            val sslHandler = super.getSslHandler(alloc)
            val handshakeTimeout = configuration[ArtemisTcpTransport.SSL_HANDSHAKE_TIMEOUT_NAME] as Duration?
            if (handshakeTimeout != null) {
                sslHandler.handshakeTimeoutMillis = handshakeTimeout.toMillis()
            }
            return sslHandler
        }
    }
}
