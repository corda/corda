package net.corda.nodeapi.internal.protonwrapper.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.concurrent.DefaultThreadFactory
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.Slf4JLoggerFactory
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.messages.SendableMessage
import net.corda.nodeapi.internal.protonwrapper.messages.impl.SendableMessageImpl
import net.corda.nodeapi.internal.requireMessageSize
import net.corda.nodeapi.internal.revocation.CertDistPointCrlSource
import org.apache.qpid.proton.engine.Delivery
import rx.Observable
import rx.subjects.PublishSubject
import java.net.BindException
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This create a socket acceptor instance that can receive possibly multiple AMQP connections.
 */
class AMQPServer(val hostName: String,
                 val port: Int,
                 private val configuration: AMQPConfiguration,
                 private val threadPoolName: String = "AMQPServer",
                 private val distPointCrlSource: CertDistPointCrlSource = CertDistPointCrlSource.SINGLETON,
                 private val remotingThreads: Int? = null) : AutoCloseable {
    companion object {
        init {
            InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE)
        }

        private val log = contextLogger()
        private val DEFAULT_REMOTING_THREADS = Integer.getInteger("net.corda.nodeapi.amqpserver.NumServerThreads", 4)
    }

    private val lock = ReentrantLock()
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var serverChannel: Channel? = null
    private var sslDelegatedTaskExecutor: ExecutorService? = null
    private val clientChannels = ConcurrentHashMap<InetSocketAddress, SocketChannel>()

    private class ServerChannelInitializer(val parent: AMQPServer) : ChannelInitializer<SocketChannel>() {
        private val keyManagerFactory = keyManagerFactory(parent.configuration.keyStore)
        private val trustManagerFactory = trustManagerFactoryWithRevocation(
                parent.configuration.trustStore,
                parent.configuration.revocationConfig,
                parent.distPointCrlSource
        )
        private val conf = parent.configuration

        override fun initChannel(ch: SocketChannel) {
            val amqpConfiguration = parent.configuration
            val pipeline = ch.pipeline()
            amqpConfiguration.healthCheckPhrase?.let { pipeline.addLast(ModeSelectingChannel.NAME, ModeSelectingChannel(it)) }
            val (sslHandler, keyManagerFactoriesMap) = createSSLHandler(amqpConfiguration, ch)
            pipeline.addLast("sslHandler", sslHandler)
            if (conf.trace) pipeline.addLast("logger", LoggingHandler(LogLevel.INFO))
            val suppressLogs = ch.remoteAddress()?.hostString in amqpConfiguration.silencedIPs
            pipeline.addLast(AMQPChannelHandler(
                    true,
                    null,
                    // Passing a mapping of legal names to key managers to be able to pick the correct one after
                    // SNI completion event is fired up.
                    keyManagerFactoriesMap,
                    conf.userName,
                    conf.password,
                    conf.trace,
                    suppressLogs,
                    onOpen = ::onChannelOpen,
                    onClose = ::onChannelClose,
                    onReceive = parent._onReceive::onNext
            ))
        }

        private fun onChannelOpen(channel: SocketChannel, change: ConnectionChange) {
            parent.run {
                clientChannels[channel.remoteAddress()] = channel
                _onConnection.onNext(change)
            }
        }

        private fun onChannelClose(channel: SocketChannel, change: ConnectionChange) {
            parent.run {
                val remoteAddress = channel.remoteAddress()
                clientChannels.remove(remoteAddress)
                _onConnection.onNext(change)
            }
        }

        private fun createSSLHandler(amqpConfig: AMQPConfiguration, ch: SocketChannel): Pair<ChannelHandler, Map<String, CertHoldingKeyManagerFactoryWrapper>> {
            return if (amqpConfig.useOpenSsl && amqpConfig.enableSNI && amqpConfig.keyStore.aliases().size > 1) {
                val keyManagerFactoriesMap = splitKeystore(amqpConfig)
                // SNI matching needed only when multiple nodes exist behind the server.
                Pair(createServerSNIOpenSniHandler(keyManagerFactoriesMap, trustManagerFactory), keyManagerFactoriesMap)
            } else {
                val keyManagerFactory = CertHoldingKeyManagerFactoryWrapper(keyManagerFactory, amqpConfig)
                val delegatedTaskExecutor = checkNotNull(parent.sslDelegatedTaskExecutor)
                val handler = if (amqpConfig.useOpenSsl) {
                    createServerOpenSslHandler(keyManagerFactory, trustManagerFactory, ch.alloc(), delegatedTaskExecutor)
                } else {
                    // For javaSSL, SNI matching is handled at key manager level.
                    createServerSslHandler(amqpConfig.keyStore, keyManagerFactory, trustManagerFactory, delegatedTaskExecutor)
                }
                handler.handshakeTimeoutMillis = amqpConfig.sslHandshakeTimeout.toMillis()
                Pair(handler, mapOf(DEFAULT to keyManagerFactory))
            }
        }
    }

    fun start() {
        lock.withLock {
            stop()

            sslDelegatedTaskExecutor = sslDelegatedTaskExecutor(threadPoolName)

            bossGroup = NioEventLoopGroup(1, DefaultThreadFactory("$threadPoolName-boss", Thread.MAX_PRIORITY))
            workerGroup = NioEventLoopGroup(
                    remotingThreads ?: DEFAULT_REMOTING_THREADS,
                    DefaultThreadFactory("$threadPoolName-worker", Thread.MAX_PRIORITY)
            )

            val server = ServerBootstrap()
            // TODO Needs more configuration control when we profile. e.g. to use EPOLL on Linux
            server.group(bossGroup, workerGroup).channel(NioServerSocketChannel::class.java)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .handler(NettyServerEventLogger(LogLevel.INFO, configuration.silencedIPs))
                    .childHandler(ServerChannelInitializer(this))

            log.info("Try to bind $port")
            val channelFuture = server.bind(hostName, port).sync() // block/throw here as better to know we failed to claim port than carry on
            if (!channelFuture.isDone || !channelFuture.isSuccess) {
                throw BindException("Failed to bind port $port")
            }
            log.info("Listening on port $port")
            serverChannel = channelFuture.channel()
        }
    }

    fun stop() {
        lock.withLock {
            serverChannel?.close()
            serverChannel = null

            workerGroup?.shutdownGracefully()
            workerGroup?.terminationFuture()?.sync()
            workerGroup = null

            bossGroup?.shutdownGracefully()
            bossGroup?.terminationFuture()?.sync()
            bossGroup = null

            sslDelegatedTaskExecutor?.shutdown()
            sslDelegatedTaskExecutor = null
        }
    }

    override fun close() = stop()

    val listening: Boolean
        get() {
            val channel = lock.withLock { serverChannel }
            return channel?.isActive ?: false
        }

    fun createMessage(payload: ByteArray,
                      topic: String,
                      destinationLegalName: String,
                      destinationLink: NetworkHostAndPort,
                      properties: Map<String, Any?>): SendableMessage {
        requireMessageSize(payload.size, configuration.maxMessageSize)
        val dest = InetSocketAddress(destinationLink.host, destinationLink.port)
        require(dest in clientChannels.keys) {
            "Destination $dest is not available"
        }
        return SendableMessageImpl(payload, topic, destinationLegalName, destinationLink, properties)
    }

    fun write(msg: SendableMessage) {
        val dest = InetSocketAddress(msg.destinationLink.host, msg.destinationLink.port)
        val channel = clientChannels[dest]
        if (channel == null) {
            throw IllegalStateException("Connection to ${msg.destinationLink} not active")
        } else {
            log.debug { "Writing message with payload of size ${msg.payload.size} into channel $channel" }
            channel.writeAndFlush(msg)
            log.debug { "Done writing message with payload of size ${msg.payload.size} into channel $channel" }
        }
    }

    fun dropConnection(connectionRemoteHost: InetSocketAddress) {
        clientChannels[connectionRemoteHost]?.close()
    }

    fun complete(delivery: Delivery, target: InetSocketAddress) {
        val channel = clientChannels[target]
        channel?.apply {
            log.debug { "Writing delivery $delivery into channel $channel" }
            writeAndFlush(delivery)
            log.debug { "Done writing delivery $delivery into channel $channel" }
        }
    }

    private val _onReceive = PublishSubject.create<ReceivedMessage>().toSerialized()
    val onReceive: Observable<ReceivedMessage>
        get() = _onReceive

    private val _onConnection = PublishSubject.create<ConnectionChange>().toSerialized()
    val onConnection: Observable<ConnectionChange>
        get() = _onConnection
}